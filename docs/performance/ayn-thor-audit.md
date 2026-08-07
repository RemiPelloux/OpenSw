# Audit performance OpenSw sur AYN Thor

## Statut

Révision de base auditée: `5e1d5e82dcc98627c769b7ca32384ded16bc5e1d`. L'état final est le
commit qui contient ce document.

Ce document distingue strictement les changements vérifiés sur l'hôte, les expériences qui
nécessitent encore un AYN Thor et les idées explicitement non promues. Aucun résultat appareil
n'est inféré à partir d'un build ou d'une moyenne UI.

Le Thor connecté est sous Android 13 avec une taille physique déclarée de 1080x1920. Le variant
Release `com.remipelloux.opensw` a été installé en mise à jour, sans effacer les données OpenSw.
La bibliothèque réelle, les insets paysage et la navigation D-pad ont été contrôlés sur l'appareil.

| Domaine | État | Preuve disponible |
| --- | --- | --- |
| Build natif OpenSw Debug | vérifié | `externalNativeBuildOpenSwDebug` réussi |
| Build natif OpenSw Profile | vérifié | `externalNativeBuildOpenSwProfile` réussi avec `OPENSW_PROFILE` |
| Tests métriques Kotlin | vérifié | `testOpenSwDebugUnitTest` réussi |
| Tests `opensw-performance-v2` | vérifié | 8 tests Python réussis |
| APK Release/Profile | vérifié sur l'hôte | chemins, identifiants et SHA-256 dans la section Livrables |
| UI paysage Thor | vérifié sur appareil | bibliothèque réelle, barre compacte, focus D-pad et changement de sélection |
| UI secondaire Thor | vérifié en Release | vues Performance/Session 1240x1080, pause/reprise, capture et partage sans collision |
| 6 cycles courts Foretales | validation partielle | 6 launch/pause/resume/capture/stop/restart réussis; rotation physique non exécutée |
| Captures UI multi-format | validation partielle | paysage Thor et écran secondaire vérifiés; portrait, thèmes et tailles de police restent à couvrir |
| Runs A/B courts sur Thor | validation utilisateur en attente | cinq runs par variante requis |
| Session longue Arceus 1.1.1 | **validation utilisateur en attente** | logs, trace et manifeste requis |
| Monster Train 2 | non exécuté | uniquement s'il est déjà légalement disponible dans OpenSw |

## Travaux vérifiés

### Runtime natif

- `PerfStats` retourne `0.0` lorsqu'aucune frame n'existe et expose séparément le p95 roulant.
- `DeviceMemoryManager` valide ASID et plages, retire les reverse mappings physiques lors d'un
  remap, supprime le backing CPU obsolète et nettoie les mappings possédés avant réutilisation
  d'un ASID.
- Le verrou de session JNI couvre les changements/destructions de surface et la capture GPU.
- Chaque démarrage natif reçoit une génération monotone transmise aux callbacks Kotlin. Les callbacks
  de démarrage ou d'arrêt qui ne correspondent plus à la session active sont ignorés après un
  stop/restart.
- L'arrêt Vulkan draine le scheduler et la présentation, arrête et joint le thread de présentation,
  puis exécute `vkDeviceWaitIdle` dans la séquence sérialisée.
- L'arène HostMemory ARM64 non-NCE peut utiliser l'espace VA 47-bit pour la réservation 39-bit.
  La sélection historique sous 39-bit du chemin NCE 38-bit n'est pas modifiée.
- Les attentes audio acceptent un `stop_token` et cessent de bloquer lors de l'annulation.
- Les fibers utilisent des stacks `mmap`/`VirtualAlloc` entourées de pages `PROT_NONE`/
  `PAGE_NOACCESS` sur les plateformes prises en charge.
- L'affinité aveugle entre coeur émulé et CPU hôte 0-3 a été retirée.
- Le registre global des objets kernel reste non-owning et diagnostique uniquement un nombre
  agrégé d'objets pendants au shutdown.

### Vulkan et shaders

- Les entrées sérialisées bornent le code shader à 16 MiB, chaque map à 65 536 entrées et le
  cache pipeline à 512 MiB.
- Un cache tronqué est ramené au dernier enregistrement complet via fichier temporaire et rename.
  En cas d'échec, le cache d'origine reste intact et le temporaire est supprimé.
- Le cache pilote Vulkan est borné à 256 MiB. Les caches invalides ou rejetés sont renommés en
  `.rejected`; un cache vide est ensuite créé.
- Le variant Profile compte, sans modifier les heuristiques: hits/misses, compilations, profondeur
  maximale de queue, demandes d'attente des petits draws, attentes réelles et durées cumulées de
  traduction Maxwell, émission SPIR-V, module shader et pipeline Vulkan. Les compteurs sont remis
  à zéro au chargement d'un Title ID et le log inclut cet identifiant.
- Le snapshot JNI Profile conserve ses 12 champs historiques puis ajoute, dans cet ordre, la
  profondeur maximale de queue de présentation, l'attente d'une frame libre, l'attente scheduler,
  l'acquisition swapchain et la durée de présentation. Les durées sont cumulatives et les résumés
  calculent des deltas début/fin; les profondeurs utilisent le maximum de la fenêtre.
- Le nombre de workers, la présentation asynchrone, les shaders asynchrones et l'heuristique
  `<= 6` sont inchangés.

### Android et métriques

- Le variant Debug utilise `com.remipelloux.opensw.debug`. Le variant Profile destiné aux mesures
  utilise `com.remipelloux.opensw.profile` et autorise le profilage shell. Le variant Release met
  à jour `com.remipelloux.opensw` en conservant ses données.
- Le contrôleur Kotlin testé isole les Title IDs et sérialise annulation et publication. L'outil
  opérationnel vérifie le marqueur du Title ID actif au début, pendant et à la fin de la capture;
  un changement de jeu ou de PID invalide le run.
- Les captures du panneau sont des diagnostics `opensw-native-diagnostic-v1`, explicitement marqués
  `measurement_source=native_render_frame` et `promotion_eligible=false`. Elles portent un UUID,
  une génération, des timestamps monotones, un état `ACTIVE/INVALIDATED/FINISHED` et un motif
  d'invalidation. Elles ne publient aucun percentile A/B issu des échantillons à 250 ms.
- La rotation détruit uniquement les consommateurs UI du sampler; une capture application-scoped
  reste active. La fin de l'émulation ou un changement de Title ID invalide la capture avant le
  teardown, et une publication dont la génération ou la configuration a changé est rejetée.
- `opensw-performance-v2` échantillonne le tampon borné de SurfaceFlinger chaque seconde,
  conserve toutes les réponses brutes, déduplique leurs timestamps `actualPresentTime` et calcule
  p50/p95/p99 nearest-rank, FPS médian, nombre de frames, RSS max et température max sur la fenêtre.
- Chaque manifeste contient HEAD, empreinte de l'arbre source incluant les fichiers non suivis,
  APK local/installé, build Android, firmware Switch, pilote, résolution, profil et hash du scénario.
- Une synthèse refuse moins de cinq runs, une configuration incomplète ou un mélange de Title ID,
  scénario, variante, APK, sources, firmware, profil, résolution, pilote ou appareil.
- La comparaison refuse une promotion si une métrique régresse de plus de 2 %. Elle exige soit
  3 % de gain de FPS médian, soit une amélioration simultanée des p95 et p99.

### Menu principal

- La refonte conserve Views/XML, RecyclerView, Navigation et Coil.
- Une barre de sélection paysage de 64 dp affiche le titre, la version, le temps de jeu et l'action
  Lancer. La jaquette reste uniquement dans la bibliothèque, sans doublon ni hero occupant l'écran.
- Les favoris sont persistés par chemin dans un unique `StringSet`. Ils restent en tête dans les
  tris par défaut, alphabétique et récents, ainsi que dans les résultats de recherche. Le bouton
  étoile de la barre de sélection reclasse la liste par DiffUtil sans rafraîchissement global.
- La recherche est repliée derrière une action; tri, mode et réglages restent compacts.
- Pendant la saisie, les actions secondaires de la barre supérieure sont repliées afin que le champ
  utilise la largeur disponible, notamment en portrait.
- Grille, liste et carousel restent disponibles. Aucun `notifyDataSetChanged()` global n'est émis
  lors d'un changement de mode.
- La sélection est restaurée par chemin, le focus D-pad est explicite, l'appui long conserve les
  propriétés et l'animation de focus dure 140 ms sans déplacement de layout.
- Coil décode à la taille demandée, borne son cache à 64 MiB, annule les requêtes recyclées et
  précharge au plus deux voisins de chaque côté.
- Les états chargement, vide, erreur/retry et jaquette absente sont présents.

### Cockpit écran secondaire

- L'écran secondaire n'affiche ni hero ni jaquette. Une barre compacte conserve le titre, le Title
  ID et l'état `En cours`/`En pause` au-dessus des vues Performance, Cheats et Session.
- Les en-têtes jeu/performance dupliqués sont masqués dans ce contexte compact.
- Performance garde FPS, p95 roulant, vitesse, température et un graphe de 80 dp dans la vue
  `Direct`. Le variant Profile ajoute le contrôle `Direct/Détails` et expose cache, compilations,
  queues, attentes et durées; une Release masque complètement ce contrôle et ces compteurs.
- Les actions Capture et Partager restent fixées en bas. Capture conserve texte et icône; Partager
  est une icône avec description accessible. Une capture Release a été terminée, enregistrée et
  présentée correctement à la feuille de partage Android, avec `ClipData` et permission de lecture
  propagée au chooser sans refus du `FileProvider`.
- Session expose Pause/Reprise, overlay, réglages rapides et arrêt de l'émulation. L'ordre de focus
  vertical est explicite pour la manette et le clavier.
- Le panneau a été contrôlé sur le second écran physique 1080x1240 tourné en 1240x1080, en français
  et thème sombre. Les autres orientations, le thème clair, l'anglais et la taille de police
  maximale restent à valider sur appareil.

## Synchronisation Eden du 6 août 2026

Le fetch de `upstream/master` ne laisse aucun patch officiel unique non porté selon
`git log --cherry-pick`. Les commits suivants sont conservés comme commits séparés:

- `3efd58df8a`, port de `49a0ca6d5d`: bindless buffers/descriptors Vulkan;
- `f01fa3dee7`, port de `a0f1cd1baf`: garde null NPad;
- `fbca988241`, port de `a43664c0fd`: bornes audio DSP et correction Jamboree.

Le bindless Vulkan compile avec les compteurs OpenSw existants, mais reste une optimisation non
mesurée jusqu'aux cinq runs A/B et contrôles visuels sur Thor.

## Trois optimisations prioritaires

1. Le chemin bindless Vulkan officiel réduit le coût de gestion des buffers et des descripteurs.
   Statut: compilé Release/Profile, validation Thor A/B en attente.
2. Le tri bibliothèque prend désormais un snapshot O(n) des favoris et timestamps avant le tri;
   il ne relit plus `SharedPreferences` dans le comparateur O(n log n).
3. Le bootstrap du cache bibliothèque ne fait plus un appel SAF `DocumentFile.exists()` par jeu
   avant le scan complet qui réconcilie déjà la liste. Cela supprime le N+1 d'I/O au cold start.

Le nettoyage des retours/tabulations des titres réutilise aussi une expression régulière compilée
une fois par processus au lieu de la reconstruire à chaque rebind.

## Protocole reproductible

### Préparation

1. Copier `tools/performance/scenarios/ayn-thor-arceus-1.1.1.example.json` hors du nom `example` et
   remplacer tous les champs `RECORD_ME`.
2. Relever APK, firmware Switch, pilote GPU, résolution, profil, mode ventilateur, température
   ambiante et température initiale.
3. Conserver exactement le même save, trajet, caméra et séquence d'inputs.
4. Faire dix minutes de warmup, puis attendre la même plage thermique avant chaque run.
5. Capturer l'APK déjà installé avant tout remplacement. L'outil ne lance et n'installe rien.

Exemple de run Profile de 60 secondes:

```sh
tools/performance/opensw-performance-v2 capture \
  --output captures/arceus-baseline-01 \
  --scenario tools/performance/scenarios/ayn-thor-arceus-1.1.1.json \
  --title-id 01001f5010dfa000 \
  --run-id baseline-01 \
  --variant baseline \
  --switch-firmware RECORD_EXACT_VERSION_AND_HASH \
  --profile RECORD_EXACT_PROFILE \
  --apk src/android/app/build/outputs/apk/openSw/profile/app-openSw-profile.apk \
  --duration 60
```

Lancer cinq runs chauds par variante, puis:

```sh
tools/performance/opensw-performance-v2 summarize \
  --output captures/baseline.json captures/arceus-baseline-*/manifest.json

tools/performance/opensw-performance-v2 summarize \
  --output captures/candidate.json captures/arceus-candidate-*/manifest.json

tools/performance/opensw-performance-v2 compare \
  --baseline captures/baseline.json \
  --candidate captures/candidate.json \
  --output captures/comparison.json
```

### Caches froids et chauds

- Run froid: utiliser exclusivement l'action OpenSw de suppression du cache du jeu, confirmer le
  Title ID affiché, fermer proprement la session, puis relancer le scénario.
- Run chaud: ne supprimer aucun cache entre warmup et mesure.
- Comparer 2, 4 et 6 workers comme expériences distinctes. Ne modifier aucune valeur par défaut
  sans cinq runs froids et cinq runs chauds par configuration.
- Exploiter le log `OpenSw pipeline profile` et Perfetto pour séparer traduction Maxwell, SPIR-V,
  modules, pipelines Vulkan, profondeur de queue et attentes.

### Cycles de vie

Exécuter d'abord 6 cycles, puis 30 cycles:

1. Lancer OpenSw et reprendre le même jeu.
2. Pause, retour menu, reprise.
3. Rotation portrait/paysage pendant une capture active.
4. Stop de l'émulation, retour bibliothèque, relance.
5. Vérifier Title ID, nombre de frames, absence de publication tardive et RSS après chaque cycle.

La rotation conserve la capture; la fin de session doit l'annuler. Un manifeste contenant deux
Title IDs est invalide.

Le 7 août 2026, six cycles courts ont été exécutés sur Foretales `010026801939E000` avec la Release:
capture, passage à Session, pause, reprise, arrêt avec capture active, retour bibliothèque et
relance. Aucun crash, ANR, callback tardif ni mélange de Title ID n'a été observé. Le RSS bibliothèque
mesuré après chaque teardown était 343, 353, 360, 363, 368 et 374 MiB, puis 355 MiB après 30 secondes
au repos; cette série courte ne montre pas une croissance RSS continue. La rotation physique n'a pas
été exécutée: la simuler aurait nécessité de modifier l'état système du Thor. Elle reste donc à
couvrir manuellement pendant les 30 cycles utilisateur.

### UI

Mesurer avec des bibliothèques synthétiques ou légales de 10, 100 et 500 jeux:

- cold start et première frame;
- fin du chargement bibliothèque;
- jank de scroll et coût des rebinds;
- RSS et mémoire bitmap;
- recherche, rotation, grille/liste/carousel, focus, retour, appui long et lancement.

Capturer 1080x1920, 1920x1080 et l'écran secondaire 1080x1240, en clair/sombre, français/anglais
et aux tailles de police Android usuelles et maximales. Vérifier aussi titre très long et jaquette
absente. Ces validations sont encore en attente.

### Session longue

Arceus 1.1.1 est la cible principale. Utiliser le build Profile, conserver la trace initiale et
finale, les logs OpenSw, les températures, le RSS, les erreurs Vulkan et le manifeste de scénario.
Ne conclure qu'après retour des artefacts utilisateur. Statut actuel: **validation utilisateur en
attente**.

## Expériences non promues

- 2/4/6 workers shaders.
- Déplacement de la traduction shader hors du thread critique.
- Modification de l'heuristique des petits draws `<= 6`.
- Présentation ou shaders asynchrones supplémentaires.

Ces expériences exigent une preuve Perfetto, cinq runs A/B, contrôles visuels et aucune régression
supérieure à 2 %.

## Idées rejetées

- Calculer p95/p99 depuis des moyennes échantillonnées.
- Mélanger plusieurs Title IDs dans une capture.
- Promouvoir sur un seul run, une moyenne FPS seule ou un gain inférieur à 3 %.
- Supprimer un cache hors de l'action OpenSw prévue pour le jeu.
- Migrer le menu vers Compose ou ajouter une dépendance UI.
- Présenter une compilation hôte comme une validation thermique, visuelle ou de stabilité Thor.

## Livrables

Build final effectué depuis le worktree basé sur `64d9a9cb8eda530ff64461771aaf3d1e9b922706`, Android SDK
`/Users/remipelloux/Library/Android/sdk` et les tâches suivantes:

```sh
cd src/android
./gradlew \
  :app:testOpenSwReleaseUnitTest \
  :app:testOpenSwProfileUnitTest \
  :app:assembleOpenSwRelease \
  :app:assembleOpenSwProfile
```

| Variant | Application ID | APK | SHA-256 |
| --- | --- | --- | --- |
| `openSwRelease` | `com.remipelloux.opensw` | `src/android/app/build/outputs/apk/openSw/release/app-openSw-release.apk` | `7dd06d284a900047fc4121d0705e24f0425ac8574e48ee190a1cde7e4747bb4a` |
| `openSwProfile` | `com.remipelloux.opensw.profile` | `src/android/app/build/outputs/apk/openSw/profile/app-openSw-profile.apk` | `1dc2c1acc2ea6f2afe793b22e5478147c3ba8248fceefb0d2882f4aa6dd25ae5` |

Le build Release final a été installé en mise à jour avec succès sur le Thor. Les captures
1920x1080 confirment l'absence de hero/jaquette dupliquée, la barre de sélection compacte, le bouton
favori et le dock sans collision. Le contrôle a aussi détecté puis corrigé l'éditeur de recherche
plein écran Android: le clavier paysage conserve maintenant la bibliothèque visible. Le variant
Profile est le livrable de mesure; chaque campagne doit l'accompagner de son SHA-256, du manifeste
exact du scénario et du protocole de session longue ci-dessus.

Le lint vital, R8 et les suites Release/Profile passent. `ktlintCheck` conserve des violations
préexistantes dans des fichiers historiques hors de cette modification; les fichiers performance
et leur test modifiés dans cette passe sont formatés. Aucune baseline n'a été ajoutée.

## Risques résiduels

- Six teardowns Vulkan complets ont été exercés indirectement avec Foretales, sans injection de
  panne; l'annulation audio et les pages gardes ne disposent toujours pas d'un test appareil isolé.
- Les tests natifs unitaires ajoutés ne sont pas exécutés par le build Android (`BUILD_TESTING=OFF`).
- La réparation par rename atomique est validée par compilation, pas par injection de panne réelle.
- Le rendu du Focus Shelf n'a pas encore été contrôlé par screenshots aux dimensions cibles.
- Les compteurs Profile sont remis à zéro au chargement d'un Title ID, mais leur attribution et les
  cinq nouvelles durées doivent encore être confirmées avec le variant Profile sur Arceus.
- Le JSON Release a été créé et transmis au sélecteur de partage, mais son contenu privé n'est pas
  lisible par `adb` sur un APK non débogable; son schéma et ses invalidations sont vérifiés par les
  tests Kotlin.
- Le Focus Shelf et sa logique restent dans le source set Android principal; les autres flavors
  héritent donc actuellement de la refonte. Leur non-régression n'a pas été validée.

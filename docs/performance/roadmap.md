# Roadmap performance OpenSw

## État de livraison

Les phases d'implémentation 1 à 4 sont intégrées dans les APK Release/Profile et documentées dans
l'audit Thor. Aucun gain de performance n'est revendiqué sans campagne A/B. Le statut global reste
**validation utilisateur en attente** pour les 30 cycles avec rotation physique et la session
Arceus 1.1.1 longue.

## Vérifié sur l'hôte

- Variants OpenSw Debug/Profile et métriques v2.
- Correctifs P1 runtime, mémoire, audio, fibers et teardown Vulkan.
- Durcissement des caches shaders/pilote et compteurs profile-only.
- Barre de sélection compacte en Views/XML, sans jaquette dupliquée, avec chargement Coil borné.
- Favoris persistants en tête, tri sans lectures de préférences dans le comparateur et bootstrap du
  cache sans N+1 SAF.
- Cockpit secondaire compact avec état de session, Performance, Cheats et actions de session.
- Snapshot JNI Profile compatible 12 -> 17 champs avec queue de présentation, attentes frame libre
  et scheduler, acquire swapchain et durée de présentation.
- Diagnostics panneau versionnés, application-scoped et non éligibles à la promotion A/B, avec
  génération de session, timestamps monotones et motifs d'invalidation explicites.
- Trois patches Eden officiels portés: bindless Vulkan, garde NPad et bornes audio DSP.
- UI paysage Thor, focus D-pad et mise à jour de sélection validés sur l'application Release.
- UI secondaire Release validée en 1240x1080: Direct, graphe, capture, partage, Session et arrêt.
- Six cycles courts Foretales launch/pause/resume/capture/stop/restart sans crash, ANR, callback
  tardif ni croissance RSS continue après stabilisation.
- Partage du diagnostic Release vérifié sans erreur JSON ni refus FileProvider; les valeurs non
  finies sont sérialisées comme `null` au lieu d'invalider le rapport.

## Validation appareil requise

- Produire cinq runs A/B Arceus 1.1.1 par variante.
- Exécuter les 30 cycles utilisateur launch/pause/resume/rotation/stop/restart; les 6 cycles courts
  hôte/appareil sont terminés, sauf la rotation physique qui reste à couvrir manuellement.
- Vérifier les écrans, thèmes, langues et tailles de police listés dans l'audit.
- Valider visuellement le cockpit secondaire 1080x1240 et l'arrêt depuis cet écran.
- Mesurer le bindless Vulkan officiel contre le build précédent; ne pas attribuer de gain avant les
  cinq runs A/B.
- Exécuter la session longue Arceus; statut: **validation utilisateur en attente**.
- Ajouter Monster Train 2 uniquement s'il est légalement disponible dans OpenSw.

## Expériences

- Matrice workers shaders 2/4/6, caches froids et chauds.
- Traduction hors thread critique uniquement après attribution Perfetto.
- Async présentation/shaders uniquement après contrôles visuels et seuils A/B.

## Règle de promotion

Cinq runs comparables, au moins 3 % de gain de FPS médian ou amélioration conjointe p95/p99, et
aucune régression de métrique supérieure à 2 %.

## Handoff

Statut actuel: **validation utilisateur en attente**. Les APK Release/Profile, leurs SHA-256, les
tests hôte, les contrôles UI secondaires et les six cycles courts sont terminés. Restent à la charge
de l'utilisateur les 30 cycles complets avec rotation physique, puis la session Arceus 1.1.1 de
45-60 minutes avec rapports, température, RSS, logs et captures. Aucun accès ou changement n'a été
effectué sur `dev.eden.eden_emulator.nightly` ni sur ses données.

Les screenshots, logs et dumps temporaires produits par la validation doivent être supprimés après
publication; les caches de jeu, sauvegardes, firmware et données Eden ne font jamais partie de ce
nettoyage.

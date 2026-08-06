# Roadmap performance OpenSw

## Vérifié sur l'hôte

- Variants OpenSw Debug/Profile et métriques v2.
- Correctifs P1 runtime, mémoire, audio, fibers et teardown Vulkan.
- Durcissement des caches shaders/pilote et compteurs profile-only.
- Barre de sélection compacte en Views/XML, sans jaquette dupliquée, avec chargement Coil borné.
- Favoris persistants en tête, tri sans lectures de préférences dans le comparateur et bootstrap du
  cache sans N+1 SAF.
- Cockpit secondaire compact avec état de session, Performance, Cheats et actions de session.
- Trois patches Eden officiels portés: bindless Vulkan, garde NPad et bornes audio DSP.
- UI paysage Thor, focus D-pad et mise à jour de sélection validés sur l'application Release.

## Validation appareil requise

- Produire cinq runs A/B Arceus 1.1.1 par variante.
- Exécuter 6 puis 30 cycles launch/pause/resume/rotation/stop/restart.
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

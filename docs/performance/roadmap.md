<!--
SPDX-FileCopyrightText: Copyright 2026 OpenSw Emulator Project
SPDX-License-Identifier: GPL-3.0-or-later
-->

# Roadmap performance OpenSw

Cette feuille de route transforme les observations Thor en expériences falsifiables. Aucun gain
n'est revendiqué par compilation, ressenti, capture UI ou run unique.

## État livré

- L'application normale est `com.remipelloux.opensw`; le variant Profile reste un outil isolé de
  mesure et ne met pas à jour l'application normale.
- `Standard`, `60 Opti` et `Max` utilisent respectivement 4, 6 et 8 workers Vulkan.
- Présentation asynchrone, vertex buffers optimisés, GPU/shaders asynchrones et placement hybride
  ADPF sont partagés par les trois profils. Leur présence n'est pas une revendication de gain.
- Six cycles Foretales launch/capture/pause/resume/stop/restart ont réussi sans crash, ANR, callback
  tardif ni mélange de Title ID. Les 30 cycles avec rotation physique restent à faire.
- Le changement de caméra Arceus reste GPU-bound dans la capture diagnostique existante: 39,51 FPS
  médians, p95 42,18 ms, p99 67,50 ms, jusqu'à 98 % GPU à 680 MHz observés.
- Les 4,22 millions de hits pipeline, sans miss, compilation ni attente, écartent la compilation de
  shaders comme cause principale de cette capture. Ils ne prouvent pas encore quelle charge per-draw
  domine.

## P0: rétablir une baseline valide

1. Produire cinq runs caméra Arceus 1.1.1 à `1x`, avec save, replay, cheats, filtre, pilote, cache
   chaud, mode ventilateur et paramètres graphiques identiques.
2. Lire l'utilisation, la fréquence, les deltas busy-time et la température nommée KGSL. Démarrer
   dans une bande de 2 C et exiger le statut thermique Android `0`.
3. Rejeter toute capture dont package, PID, Title ID, génération de session, surface, APK/source ou
   hash de scénario change pendant le run.
4. Rejeter une trace Perfetto sous 4 KiB ou sans processus/threads OpenSw. Sur ce firmware, publier
   `unavailable` si ftrace reste inaccessible; ne jamais présenter une trace vide comme valide.
5. Classer l'ancienne capture thermique à 92,7 C comme invalide: elle mélangeait des capteurs non
   nommés et dépassait la limite configurée de 80 C.

## P1: comparer les profils

Comparer 4, 6 et 8 workers comme trois candidats indépendants. Tous les autres réglages doivent être
strictement identiques. Effectuer cinq runs chauds par profil, puis confirmer le meilleur résultat
avec une seconde série dans la même bande thermique initiale. Les runs cache froid servent à mesurer
la compilation et ne doivent pas être mélangés aux résultats caméra stabilisés.

## P2: attribuer le coût per-draw Vulkan

1. Utiliser les compteurs descriptor déjà livrés pour A/B les caches d'offset/payload. Ajouter des
   chunks spill conservés seulement si le compteur d'épuisement prouve le fallback scheduler actuel.
2. Utiliser les compteurs bind/slots/synchronisation/upload déjà livrés pour A/B le binding sparse
   Android sans réduire la synchronisation et en gardant le réglage de compatibilité.
3. Utiliser les compteurs self/linear/hash/slow-path et profondeur de probes pour A/B le cache hybride
   de transitions pipeline contre son prédécesseur linéaire.
4. Exploiter les octets/temps d'upload, decode et unswizzle déjà exposés avant toute stratégie de lazy
   loading, préchargement ou prédiction.
5. Construire les candidats depuis leurs commits séparés et tester chacun seul. Supprimer un candidat
   sans gain répétable ou avec défaut visuel.

## P3: stabilité

1. Exécuter 30 cycles avec rotation physique et mesures mémoire à 10 et 30 secondes.
2. Exécuter Arceus 45-60 minutes avec caméra, pause/reprise et écran secondaire.
3. Attribuer la hausse mémoire native observée d'environ 5,0 à 5,8 GiB pendant le sweep; une hausse
   en jeu ne devient une fuite que si elle reste sans propriétaire et ne retombe pas après la scène.
4. Diagnostiquer `MemoryTracker: Out of bound ranges 3` et
   `DeviceMemoryManager: UpdatePagesCachedBatch basic` sur l'hôte.

## P4: validation et livraison

- Tester Android ARM64 générique et garder tout comportement Thor spécifique optionnel et réversible.
- Comparer des screenshots aux mêmes timestamps pour géométrie, textures, éclairage et corruption.
- Exécuter tests hôte, tests Android/Kotlin, build natif ARM64, contrôle des métadonnées et
  `git diff --check`.
- Construire depuis un commit, vérifier package/certificat/version/SHA-256, puis mettre à jour
  `com.remipelloux.opensw` avec `adb install -r`. Ne jamais modifier Eden officiel.

## Règle de promotion

Cinq runs baseline et cinq runs candidat. Exiger au moins 3 % de gain de FPS médian ou une
amélioration simultanée p95/p99, sans régression de métrique, RSS ou température supérieure à 2 %.
Combiner uniquement des candidats prouvés, puis répéter cinq runs de validation du build combiné.

Sont hors périmètre: `0.75x`, FSR, changements de clocks ou ventilateur, unsafe math, suppression du
cache shaders comme optimisation et modes `2D`/`3D` spéculatifs sans attribution préalable.

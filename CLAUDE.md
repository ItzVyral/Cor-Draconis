# Cor Draconis — mod Fabric pour Minecraft Java 26.3

## Stack
- Minecraft 26.3, Fabric Loader 0.20.1, Fabric API 0.166.2+26.3, Loom 1.19, Gradle 9.8.2, Java 25 (Temurin installé dans `C:\Program Files\Eclipse Adoptium\jdk-25.0.5.8-hotspot`).
- Minecraft 26.x n'est plus obfusqué : noms officiels Mojang (pas de Yarn, pas de `modImplementation`).
- GeckoLib 5.5.7 (`com.geckolib:geckolib-fabric-26.3`, package `com.geckolib`) pour les animations ; modèles Bedrock `.geo.json` ouvrables dans Blockbench.
- Source sets : `src/main` (commun), `src/client` (rendu, HUD, écrans), `src/gametest` (tests en jeu).

## Conventions
- Mod id `cordraconis`, package racine `com.cordraconis`.
- Une classe par fonctionnalité ; commentaires, noms de variables et de méthodes en français.
- L'utilisateur débute en Java : chaque nouvelle notion est expliquée par un commentaire `NOTION JAVA —` ou `NOTION —`.

## Architecture
- `registre/` : Mod{Items,Blocs,Entites,Menus,Composants,Attachements,OngletCreatif} — enregistrements.
- `rune/Rune` : enum des runes (composant de données `cordraconis:rune` + info-bulle).
- `epee/` : `EpeeDuVeilleurItem` (clic droit = sort), `SouffleDraconique` (cône de dégâts + effet de rune).
- `forge/` : `ForgeRuniqueBlock`, `ForgeRuniqueMenu` (hérite d'`ItemCombinerMenu`).
- `braise/SystemeBraise` : attachement joueur synchronisé, Embrasement (seuil 60), Surchauffe (100).
- `entite/cendreux/` : se reforme tant que son cœur n'est pas détruit (interception dans `actuallyHurt`).
- `entite/gardien/` : boss 2 phases + `ArenePremierGardien` (construite par `objet/SceauDuGardienItem`).
- `objet/` : Éclat du cœur, Fiole de cendre froide, Sceau du Gardien.
- Client : `client/hud/JaugeBraiseHud`, `client/ecran/ForgeRuniqueScreen`, `client/rendu/*Renderer`.

## Particularités de l'API 26.3 (vérifiées dans les sources)
- GUI : `GuiGraphicsExtractor` (méthodes `extract…`, `text`, `fill`, `blitSprite`), couleurs ARGB avec alpha.
- `EntityTypes.X` (et non `EntityType.X`), `entity.snapTo(...)`, `player.sendOverlayMessage(...)`.
- Sauvegarde d'entité : `ValueOutput`/`ValueInput` (`putInt`, `getIntOr`...).
- Loot tables : clés `modifier` / `condition` (au singulier) ; tags dans `tags/block/`.
- Modèles d'items : `assets/<ns>/items/*.json` (sélection par composant : `minecraft:select` + `minecraft:component`).
- Sources décompilées : `./gradlew genSources` puis jars dans `.gradle/loom-cache/minecraftMaven`.

## Ressources générées
- `outils/GenerateurRessources.java` crée textures, glowmasks, icônes, GUI et `.geo.json`.
  `java outils/GenerateurRessources.java` — ÉCRASE les fichiers : ne plus le lancer sur un fichier retouché à la main.
- Les animations (`geckolib/animations/entity/*.animation.json`) sont écrites à la main.
  Keyframes `{"vector": [...], "easing": "easeOutBack"}` (easing = arrivée sur ce keyframe) et Molang (`query.anim_time`, `math.sin` en degrés).
- Contrôleurs GeckoLib : `corps` (état), `actions` (triggerAnim, remplace `corps` sur ses os), `ambiance` (additif, toujours actif : cœur, orbites, cape).
  Dans un contrôleur non additif, animer un os réinitialise ses 3 transformations (rotation, position, échelle).
- Os `regard` (sans cube, parent de `tete`) : tourné vers la cible par `adjustModelBonesForRender` dans les renderers.
- Cendreux : l'os `tas` est mis à l'échelle 0 par repos/marche et n'apparaît qu'à l'effondrement.
- Gardien : agonie de 60 ticks (`tickDeath`), impact du Fracas 12 ticks après son déclenchement (synchro avec l'animation).
- Le test en jeu prend des gros plans (« galerie ») : la caméra tourne autour, les créatures sans IA ne pivotent pas leur corps.

## Commandes
- `./gradlew build` : compile le mod (jar dans `build/libs/`).
- `./gradlew runClient` : lance Minecraft avec le mod.
- `./gradlew runClientGameTest` : scénario de test automatique en jeu (captures dans `build/run/clientGameTest/screenshots`).

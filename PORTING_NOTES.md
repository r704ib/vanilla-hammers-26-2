# Notes de portage — Vanilla Hammers 1.19.2 → 26.2

## Différence fondamentale avec le portage d'Adabranium

Adabranium (1.21.11 → 26.2) était un **portage** : le code utilisait déjà les mappings officielles,
la structure changeait peu, l'essentiel du travail a été de suivre les renommages d'API récents.

Vanilla Hammers (1.19.2 → 26.2) est une **réécriture** : mappings différentes (Yarn), ~4 ans d'écart,
et surtout 3 dépendances externes du même auteur non maintenues au-delà de 1.18-1.20 (Magna,
StaticData, OmegaConfig) qui portaient l'essentiel de la logique. Plutôt que de tenter de porter ces
3 bibliothèques séparées, j'ai réimplémenté leurs fonctionnalités essentielles directement dans ce
dépôt, avec les API actuelles.

**Contrairement à Adabranium, ce code n'a jamais été compilé** (ni par moi, contrainte réseau du bac
à sable, ni par toi pour l'instant) — attends-toi à un premier retour de compilation avec beaucoup
plus d'erreurs, sur des points plus variés, que pour Adabranium.

## Ce qui remplace les bibliothèques externes

- **Magna** (mécanique de minage 3×3) → `HammerItem.java` : casse les blocs autour de celui visé,
  dans le plan perpendiculaire à la direction du regard du joueur (approximée via
  `Direction.getApproximateNearest`), sur `breakRadius` blocs de rayon. Logique écrite from-scratch,
  pas vue dans Magna (dont le code source n'a pas été consulté en détail) — la géométrie exacte
  (quel plan, quels cas limites) est une approximation raisonnable, pas une garantie de fidélité à
  l'original.
- **StaticData** (chargement de données inter-mods au démarrage) → `HammerData.loadAndRegisterAll()` :
  scanne `static_data/vanilla-hammers/hammers/*.json` dans **tous** les mods chargés (le nôtre et
  les autres, ex. Adabranium) via `FabricLoader.getInstance().getAllMods()` +
  `ModContainer.getRootPaths()`, à l'initialisation du mod (avant que les ressources/tags normaux ne
  soient disponibles) — c'est ce qui permet à Adabranium de fournir des marteaux vibranium/adamantium/
  nether sans dépendance directe entre les deux mods.
- **OmegaConfig** (écran de configuration) → **non porté**. `VanillaHammersConfig.java` garde les
  mêmes valeurs par défaut que l'original mais en dur, sans écran de configuration ni sauvegarde sur
  disque. Simplification assumée pour limiter la portée de ce premier jet.

## Simplifications / écarts assumés par rapport à l'original

- **Réparation à l'enclume** : l'original utilisait un ingrédient de réparation précis par matériau
  (`repairIngredient` dans le JSON). Comme `ToolMaterial` exige un **tag** (`TagKey<Item>`) et pas un
  item précis, et que les marteaux sont enregistrés dynamiquement (potentiellement depuis un mod
  qu'on ne connaît pas au moment de la compilation), tous les marteaux partagent **un seul tag**
  `vanilla-hammers:repairable` regroupant tous les matériaux de nos 14 marteaux natifs. Un marteau
  vibranium (Adabranium) serait réparable avec n'importe quel matériau de ce tag, pas seulement du
  lingot de vibranium — imprécis mais fonctionnel. Un mod tiers peut enrichir ce tag avec ses propres
  matériaux en fournissant son propre fichier `data/vanilla-hammers/tags/item/repairable.json` (les
  tags de plusieurs mods/datapacks pour le même ID fusionnent automatiquement).
- **Niveau de minage → tag "incorrect_for_x_tool"** : mapping approximatif (0→pierre, 1→fer,
  2→diamant, 3+→netherite) basé sur ma mémoire de la hiérarchie vanilla réelle, **non vérifié** sur
  un vrai build 26.2.
- **Groupe créatif personnalisé par marteau** (`group` dans le JSON d'origine, résolu via un mixin
  `ItemGroupAccessor`) : abandonné, tous les marteaux vont dans l'onglet créatif "Vanilla Hammers".
- **Silk Touch conditionnel** (les marteaux qui fondent les blocs ne devraient pas accepter Silk
  Touch, via un mixin sur `SilkTouchEnchantment`) : **non porté**. Depuis 1.21, les enchantements sont
  pilotés par des données JSON (tags `supported_items`), plus par des classes Java par enchantement
  — le mixin d'origine ne peut plus fonctionner tel quel. Pas de remplacement pour l'instant ; tous
  les marteaux peuvent recevoir Silk Touch, y compris ceux qui fondent (comportement légèrement
  incohérent mais pas bloquant).
- **Knockback bonus (marteau de slime)** : l'original interceptait le calcul vanilla du knockback via
  un mixin sur `EnchantmentHelper`. Remplacé par une poussée directe appliquée à l'entité touchée
  dans le callback d'attaque — pas équivalent au calcul d'enchantement d'origine, mais donne le même
  effet perçu (un coup plus repoussant).
- **Avancements** (14 fichiers dans l'original) : **non portés** dans ce premier jet, pour limiter la
  portée. Peut être ajouté ensuite si voulu.
- **`hurtAndBreak`** (usure de l'outil après avoir cassé un bloc supplémentaire) : signature exacte en
  26.2 non confirmée, un pari raisonnable a été fait (`hurtAndBreak(int, LivingEntity, EquipmentSlot)`).
- **`FuelRegistry`** (marteaux utilisables comme combustible de four, ex. marteau en bois) : classe
  Fabric API historiquement utilisée telle quelle, mais pas revérifiée pour 26.2 — la table de
  correspondance officielle des renommages 26.1 montre `FuelRegistryEvents` → `FuelValueEvents`
  (un système différent, à base d'événements), donc `FuelRegistry` (celui utilisé ici) a peut-être
  changé aussi.

## Ce qui est repris tel quel de l'original

- Toutes les stats des 14 marteaux natifs (`static_data/vanilla-hammers/hammers/*.json`) : durabilité,
  vitesse, dégâts, enchantabilité, etc. — inchangées.
- Textures et modèles d'objets (formats stables depuis longtemps).
- Recettes de craft (adaptées au format JSON actuel : ingrédients simplifiés en chaînes, plus
  d'enveloppe `{"item": "..."}` ; recette de forge du marteau en netherite convertie de l'ancien
  `minecraft:smithing` vers `minecraft:smithing_transform` avec un emplacement de gabarit — confirmé
  via une recette générée par Adabranium avec le même type).

## Journal des erreurs de compilation réelles

**1er retour (5 erreurs)** — bien moins que redouté vu l'ampleur de la réécriture :

- `FuelRegistry` n'existe plus — confirmé via `fabric-docs` : remplacé par un système à base
  d'événement, `FuelValueEvents.BUILD.register((builder, context) -> builder.add(item, ticks))`.
  Comme nos marteaux sont enregistrés dynamiquement (potentiellement en plusieurs fois, y compris
  depuis d'autres mods), les entrées "combustible" sont maintenant collectées dans
  `HammerData.FUEL_ENTRIES` pendant l'enregistrement, puis un seul callback `FuelValueEvents.BUILD`
  les consomme toutes d'un coup dans `VanillaHammers.onInitialize()`.
- `BuiltInRegistries.ITEM.get(Identifier)` retourne maintenant `Optional<Holder.Reference<Item>>`
  (avant : l'item directement) — corrigé avec `.map(Holder.Reference::value).orElse(...)`, déduit
  directement du message d'erreur du compilateur (pas une supposition).
- `Entity.setSecondsOnFire(int)` n'existe plus → remplacé par `Entity.igniteForSeconds(int)`
  (best-effort, pas confirmé ailleurs — repose sur le prochain retour de compilation).
- `Level.getRecipeManager()` n'existe plus sur `ServerLevel` → remplacé par
  `serverLevel.getServer().getRecipeManager()` (best-effort, pas confirmé ailleurs).

## Versions retenues

Mêmes versions que le portage Adabranium (déjà confirmées par une compilation + un lancement
réussis) : Fabric Loader 0.19.3, Fabric Loom 1.17, Fabric API 0.156.0+26.2, Java 25.

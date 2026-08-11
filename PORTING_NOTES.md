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
  `serverLevel.getServer().getRecipeManager()` (best-effort, pas confirmé ailleurs). **Confirmé bon**
  par le 2e retour (voir ci-dessous, plus d'erreur dessus).

**2e retour (3 erreurs, toutes dans la même méthode `smelt()`)** :

- `SmeltingRecipe.assemble(SingleRecipeInput, RegistryAccess)` n'existe plus avec 2 arguments →
  `assemble(SingleRecipeInput)` seul. Corrigé ; les 2 autres erreurs (`isEmpty()`/`copy()` introuvables
  sur `Object`) étaient une simple conséquence en cascade de celle-ci et se sont résolues avec.

**3e retour : `BUILD SUCCESSFUL`.** Ça compile. Reste à vérifier en jeu (`./gradlew runClient`) — voir
la liste des points "best-effort" ci-dessus, aucun ne casse la compilation mais certains pourraient
avoir un comportement différent de ce qui est documenté (mapping des niveaux de minage, Silk Touch non
filtré, knockback simplifié...).

## Bugs trouvés après le "BUILD SUCCESSFUL" (retours de test en jeu)

**Craft impossible sur les 13 marteaux natifs en crafting_shaped** (le netherite, en
`smithing_transform`, n'était pas touché) : chaque recette utilisait `"category": "tools"`, une
valeur qui n'existe pas dans l'énumération `CraftingBookCategory` de Minecraft (valeurs valides :
`building`, `redstone`, `equipment`, `misc`). Une catégorie invalide fait échouer le parsing JSON de
toute la recette au chargement du datapack — Minecraft l'ignore silencieusement, sans crash, donc le
bug ne se voyait qu'en essayant de crafter. Corrigé en `"equipment"` dans les 13 fichiers.

**Enchantement impossible sur tous les marteaux** : `HammerData.register()` construisait
`Item.Properties` avec seulement `.setId(key).stacksTo(1)` (+ `.fireResistant()` si applicable). Le
champ `enchantability` était bien lu depuis le JSON et stocké dans `ToolMaterial`, mais jamais
appliqué à l'item lui-même via `.enchantable(...)` — sans ce composant, un item n'est tout simplement
pas éligible à l'enchantement en table, quelle que soit sa valeur d'enchantabilité déclarée. Même
chose pour `.durability(...)` : jamais appelé, donc l'item n'était pas endommageable du tout
(`ItemStack.hurtAndBreak()` ne fait rien sur un item sans composant de durabilité — pas de crash, mais
durabilité infinie en pratique, invisible sans vérifier explicitement). Les deux sont maintenant
posés explicitement sur `Item.Properties` dans `HammerData.register()`.

**Enchantements visibles dans la table mais impossibles à valider** : poser `.enchantable(...)` sur
l'item (voir plus haut) ne suffit pas à lui-même. Depuis la refonte des enchantements en 1.21, chaque
enchantement (Efficacité, Fortune, Solidité...) déclare un tag `supported_items` qui liste les objets
éligibles - et ce tag est indépendant du composant `enchantable`. Vérifié directement dans les données
vanilla (`data/minecraft/tags/item/enchantable/mining.json`, `mining_loot.json`, `durability.json`) :
ces tags n'énumèrent jamais les objets un par un, ils référencent `#minecraft:pickaxes` (+ axes/pelles/
houes). Nos marteaux n'étant dans aucun tag vanilla, la table proposait un enchantement (le composant
`enchantable` suffit pour ça) mais le clic de confirmation échouait silencieusement car l'enchantement
n'est en réalité pas "supporté" pour cet objet. Corrigé en ajoutant tous les marteaux (les 14 natifs +
les 3 fournis par Adabranium : vibranium, adamantium, nether) au tag `#minecraft:pickaxes` via un
fichier `data/minecraft/tags/item/pickaxes.json` dans ce mod (un mod peut étendre un tag vanilla en
livrant un fichier au même chemin sous le namespace `minecraft` - Minecraft fusionne avec le tag
d'origine au lieu de le remplacer). Ça couvre transitivement Efficacité/Fortune/Sac de nœuds
(`enchantable/mining` + `mining_loot`), Solidité (`enchantable/durability`) et Malédiction de
disparition (`enchantable/vanishing`, qui référence `durability`). Volontairement pas ajouté à
`#minecraft:axes`/`#minecraft:swords` (Tranchant, Butin...) pour ne pas hériter d'un comportement
d'outil non désiré (ex. déshabillage des troncs par une hache) - à revoir si demandé.

Attention : cette liste doit être tenue à jour à la main si un futur mod tiers fournit encore d'autres
marteaux via `static_data/vanilla-hammers/hammers/*.json` sans être ajouté ici - il resterait craftable
et fonctionnel, juste pas enchantable, sans planter.

**Crash découvert par un test réel (isolation sans Adabranium) et corrigé** : contrairement à ce que
je pensais, une entrée de tag pointant vers un objet non enregistré n'est **pas** silencieusement
ignorée en 26.2 - elle fait planter tout le chargement du tag (`IllegalStateException: Missing tag`),
qui plante en cascade les tags `enchantable/*` qui en dépendent, qui empêchent même les enchantements
**vanilla** (Efficacité, Solidité, Mending...) de se lier, qui fait planter toute création de monde.
`pickaxes.json` listait en dur les 3 marteaux fournis par Adabranium (vibranium, nether, adamantium) -
sans Adabranium installé, ces objets n'existent pas et faisaient planter le jeu à la création de
n'importe quel monde. Corrigé en marquant ces 3 entrées `"required": false` (format objet
`{"id": "...", "required": false}` plutôt qu'une simple chaîne) - le tag se construit maintenant
correctement qu'Adabranium soit présent ou non. **Cette leçon s'applique à toute future entrée
cross-mod ajoutée à un tag partagé : toujours `required: false` sauf si l'objet est garanti d'exister
dans ce mod lui-même.**

**Confirmé par un test réel (pas de dégâts d'attaque affichés) et corrigé** : `attackDamage` et
`attackSpeed` lus depuis le JSON n'étaient jamais appliqués à l'item — il leur manquait le composant
`minecraft:attribute_modifiers` (`Item.Properties#attributes(ItemAttributeModifiers)`) que les
classes vanilla comme `DiggerItem` posent automatiquement dans leur constructeur, mais que
`HammerItem` (qui étend `Item` directement, pas `DiggerItem`/`PickaxeItem`, à cause de la logique de
minage custom) ne posait jamais. Corrigé dans `HammerData.register()` avec
`ItemAttributeModifiers.builder().add(Attributes.ATTACK_DAMAGE, ..., EquipmentSlotGroup.MAINHAND)` +
pareil pour `ATTACK_SPEED`. Les valeurs JSON sont déjà pensées comme des *bonus* additifs par rapport
à la valeur à mains nues (c'est littéralement le nom du paramètre correspondant dans le constructeur
`ToolMaterial` : `attackDamageBonus`, et les `attackSpeed` du JSON sont négatifs comme toutes les
valeurs vanilla, ex. `-2.4` pour l'épée en diamant) donc utilisées telles quelles avec
`AttributeModifier.Operation.ADD_VALUE`, sans transformation. **Best-effort / pas confirmé** : l'ID
de chaque `AttributeModifier` est un `Identifier` custom (`vanilla-hammers:hammer_attack_damage_<id>`)
plutôt que la constante vanilla `Item.BASE_ATTACK_DAMAGE_ID` (choix délibéré pour ne pas dépendre
d'une constante dont le nom exact en 26.2 n'est pas vérifié) ; `EquipmentSlotGroup.MAINHAND` est
confirmé exister (utilisé dans le code vanilla historique pour ce même usage) mais pas testé en
conditions réelles sur cette version précise.

**Piste la plus sérieuse pour le craft impossible, après une très longue session de diagnostic en
conditions réelles** (JSON revérifié plusieurs fois, item confirmé enregistré, jar confirmé à jour,
test en isolation sans les 20 autres mods du pack de l'utilisateur, craft vanilla confirmé fonctionnel
dans le même monde) : les 13 recettes `crafting_shaped` n'avaient **pas** de champ `count` explicite
dans leur `result` (juste `"id"`, sans `"count"`). Toutes les vraies recettes vanilla générées par le
jeu lui-même incluent systématiquement `"count": 1` explicitement, tout comme la recette générée par
Adabranium utilisée comme référence plus haut dans ce document - aucune n'omet ce champ. Hypothèse :
`count` manquant est peut-être interprété comme `0` plutôt que `1` par le codec de résultat, ce qui
ferait "matcher" la recette en interne (aucune erreur, aucun avertissement dans les logs - cohérent
avec ce qu'on observe) mais produirait un résultat de compte nul, donc une pile vide, donc une case de
résultat visuellement vide dans l'interface - exactement le symptôme. Corrigé en ajoutant `"count": 1`
explicitement aux 13 recettes `crafting_shaped`. **Pas touché sur `netherite_hammer.json`**
(`smithing_transform`) : la vraie recette vanilla équivalente (`netherite_pickaxe_smithing.json`)
n'a elle-même pas de `count` non plus, donc ce type de recette semble avoir un comportement différent
(ou un défaut à 1 assumé correct pour ce type précis) - pas de raison d'y toucher.

Si cette hypothèse est confirmée par le prochain test réel, ce sera la conclusion d'un chaînage
d'investigation particulièrement long : `/datapack list` (pack bien actif) → contenu du jar vérifié
octet pour octet (recette bien à jour) → aucune erreur dans les logs → test en isolation sans les
20 autres mods (reproductible, donc pas une interférence externe) → craft vanilla confirmé
fonctionnel dans le même monde (donc pas un bug d'installation/launcher) → comparaison ligne à ligne
avec une vraie recette vanilla, seule différence trouvée : le champ `count` absent.

## LA vraie cause du craft impossible, enfin trouvée

Toutes les hypothèses précédentes (categorie invalide, count manquant...) étaient de vrais bugs annexes
mais aucune n'était LA cause. La vraie cause, trouvée grâce à `/recipe give @s vanilla-hammers:iron_hammer`
qui a répondu **"Recette inconnue"** (donc le jeu ne connaissait même pas l'existence de la recette,
peu importe son contenu) : le dossier de données pour les recettes s'appelle **`recipe` (singulier)**
depuis un moment déjà côté vanilla, pas `recipes` (pluriel) comme on l'a utilisé partout dans ce
portage. Confirmé directement dans une vraie arborescence de données vanilla (`data/minecraft/recipe/`
existe, `data/minecraft/recipes/` n'existe pas - même chose pour `advancement` et `loot_table`,
également au singulier ; seul `tags` reste au pluriel). Comme Minecraft scanne un dossier au nom
exact, un dossier `recipes/` n'est simplement jamais visité - aucune erreur, aucun avertissement, les
14 fichiers étaient juste invisibles pour le jeu depuis le début. Corrigé en renommant
`data/vanilla-hammers/recipes/` en `data/vanilla-hammers/recipe/` (`git mv`, historique préservé).

Ce dossier existait aussi en double dans le dépôt Adabranium (`data/vanilla-hammers/recipes/`,
les 3 recettes vibranium/adamantium/nether contribuées à Vanilla Hammers) avec exactement le même bug -
également corrigé là-bas dans le même mouvement.

## Réparation à l'enclume impossible (confirmé par un test réel) et corrigée

Le tag `vanilla-hammers:repairable` et le `ToolMaterial` de chaque marteau étaient corrects, mais rien
ne les reliait jamais à un comportement réel en jeu.

**Première tentative (erreur de compilation, corrigée au retour suivant)** : j'ai d'abord supposé que
la réparation à l'enclume venait de `TieredItem.isValidRepairItem()`, hérité par tous les vrais outils
vanilla mais pas par `HammerItem` (qui étend `Item` directement, pas `TieredItem`, à cause de la
logique de minage custom) - et j'ai ajouté une surcharge manuelle de cette méthode. Erreur de
compilation réelle : `isValidRepairItem(ItemStack,ItemStack)` n'existe plus du tout sur `Item` en 26.2.
Cette méthode a été retirée quand la réparation est devenue un **composant de données** plutôt qu'un
comportement Java (introduit dès les snapshots 1.21, donc bien présent en 26.2) - confirmé par
recherche externe (le composant `minecraft:repairable`, exposé côté Fabric/vanilla via
`Item.Properties#repairable(...)`, exactement le même genre de builder que `.enchantable(...)` et
`.durability(...)` déjà utilisés dans ce fichier).

**Correction réelle** : suppression de la surcharge dans `HammerItem`, remplacée par
`.repairable(REPAIRABLE)` directement dans la chaîne `Item.Properties` de `HammerData.register()`
(même tag `HammerData.REPAIRABLE` qu'avant, juste posé au bon endroit). Beaucoup plus simple que la
première tentative, et cohérent avec le reste du fichier.

## Fortune (et tout autre enchantement) ignoré sur les blocs cassés en zone, confirmé par un test réel

Signalé par l'utilisateur : Fortune 3 sur un marteau ne donnait pas plus de butin, et certains blocs
cassés par l'effet de zone ne donnaient carrément rien. Cause trouvée dans `HammerItem.breakExtra()` :
pour tous les marteaux sauf celui en fiery (`canSmelt() == true`), les blocs "en plus" (autour du bloc
visé) étaient cassés via `level.destroyBlock(pos, true, player)` - une méthode qui ne prend **pas**
l'outil en paramètre. En interne, Minecraft calcule alors le butin comme si le bloc était cassé à
mains nues : les enchantements du marteau (Fortune, Sac de nœuds...) n'étaient jamais consultés, et les
blocs qui exigent un palier d'outil minimum pour lâcher leur butin (le diamant par exemple) ne
donnaient tout simplement rien. Seul le bloc central, cassé via le circuit de minage normal du joueur
(en dehors de notre code), profitait correctement des enchantements - d'où l'impression très
spécifique remontée par l'utilisateur.

Corrigé en unifiant les deux branches (fonte ou non) sur le même calcul de butin déjà utilisé pour le
marteau fiery, `Block.getDrops(state, level, pos, blockEntity, player, tool)` - qui prend bien l'outil
réel en compte pour les enchantements et le palier requis - suivi de la fonte optionnelle bloc par bloc
selon `data.canSmelt()`.

## La vraie cause du bug Fortune/butin manquant : composant `minecraft:tool` jamais posé

Le fix précédent (`Block.getDrops(..., tool)`) était nécessaire mais pas suffisant - confirmé par un
test réel : bloc de minerai de diamant cassé par la zone du marteau, disparu, mais **zéro** diamant
lâché, sans la moindre ligne dans les logs. Cause trouvée après avoir recoupé plusieurs sources
externes (recherche + vraie source Java compilable dans les tests de `fabric-item-api-v1`, voir
`ModifyComponentsInPropertiesTestSetup.java`) : depuis la refonte des outils en composants de données
(1.21+), qu'un outil soit "correct pour le butin" sur un bloc donné (ce qui gouverne si Fortune/Sac de
nœuds s'appliquent, et si les blocs à palier requis comme le minerai de diamant lâchent quoi que ce
soit) est déterminé par le composant `minecraft:tool` (`Tool` + liste de `Tool.Rule`), consulté en
interne par `Block.getDrops()`. Les classes vanilla comme `PickaxeItem`/`DiggerItem` posent ce
composant automatiquement dans leur constructeur - mais `HammerItem` étend `Item` directement (à cause
de la logique de minage custom), donc n'avait **aucun** composant `tool` du tout. Résultat : chaque
bloc était silencieusement traité comme "mauvais outil" par `Block.getDrops()`, indépendamment de
`isCorrectToolFor()` (notre propre vérification, qui ne fait que décider si on tente de casser le bloc
du tout, complètement séparée de cette histoire de composant).

Corrigé en construisant le composant `minecraft:tool` à la main dans `HammerData.register()`
(`buildToolComponent()`), avec une règle par tag `MINEABLE_WITH_{PICKAXE,SHOVEL,AXE,HOE}` marquée
`correctForDrops: true` - couvre tous les blocs que ces 4 outils vanilla peuvent normalement casser,
peu importe lequel le marteau touche. Construit via `Registry#getTagOrEmpty(TagKey)` plutôt que
`HolderLookup.Provider#getOrThrow` - le premier renvoie une référence "vide puis remplie plus tard",
utilisable avant que les tags soient chargés (contrairement au second qui exige que le tag existe déjà
au moment de l'appel) ; nécessaire ici puisque tout ce fichier tourne au moment de l'initialisation du
mod, avant les tags (voir la javadoc de la classe). La vitesse de minage définie dans chaque règle n'a
pas d'effet réel sur le jeu telle quelle car `HammerItem.getDestroySpeed()` la court-circuite déjà avec
une valeur uniforme - seul le `correctForDrops: true` compte vraiment ici, la vitesse est juste posée
par cohérence.

**1ère tentative (erreur de compilation, corrigée au retour suivant)** : `Registry#getOrCreateTag` -
n'existe pas du tout sous ce nom en 26.2 (mauvais souvenir de ma part, pas vérifié contre une vraie
source au moment de l'écrire). Vraie méthode trouvée en cherchant un usage réel et compilable dans le
dépôt `fabric-content-registries-v0` de Fabric API (`FlammableBlockRegistryImpl.java`) :
`Registry#getTagOrEmpty(TagKey)`. Signatures de `Tool`/`Tool.Rule` elles-mêmes confirmées dès le
premier essai via une autre source réelle et compilable (`fabric-item-api-v1`,
`ModifyComponentsInPropertiesTestSetup.java`) et n'ont pas eu besoin d'être changées.

**2e tentative (erreur de compilation, corrigée au retour suivant)** : `getTagOrEmpty` compile bien
mais renvoie un simple `Iterable<Holder<Block>>` côté interface, pas le `HolderSet<Block>` qu'exige
`Tool.Rule` - erreur de type à la compilation. Confirmé en lisant les internes de stockage des tags du
registre (le mixin `MappedRegistryMixin` de Fabric API "shadow" une méthode interne
`createTag(TagKey<T>)` qui renvoie bien un `HolderSet.Named<T>`) que l'objet réellement renvoyé à
l'exécution est bien un `HolderSet`, juste déclaré plus largement par l'interface. Corrigé avec un cast
explicite (`mineableTag()`), qui ne change rien à ce qui est renvoyé, juste au type vu par le
compilateur.

**3e tentative - plantage réel au chargement du monde cette fois (compilait, mais crash confirmé par
un vrai test), corrigée en changeant complètement d'approche** : le cast compilait, mais à l'exécution,
`getTagOrEmpty(BlockTags.MINEABLE_WITH_PICKAXE)` lançait
`IllegalStateException: Tags not bound, trying to access TagKey[minecraft:block / minecraft:mineable/pickaxe]`.
Confirmé noir sur blanc dans le log (`Failed to load hammer data from ...` pour les 17 marteaux, capturé
grâce à un test réel) : les tags de blocs ne sont **vraiment** pas liés à ce stade du chargement du mod,
comme le disait déjà la javadoc de cette classe depuis le début - mon hypothèse selon laquelle
`getTagOrEmpty` renverrait une référence "vide puis remplie plus tard" était fausse, elle exige que le
tag soit déjà résolu.

Plutôt que de continuer à deviner la bonne méthode d'accès bas niveau (déjà deux échecs sur ce point
précis), changement d'approche complet : **`HammerItem` étend maintenant `PickaxeItem` au lieu
d'`Item` directement**. `PickaxeItem`/`DiggerItem` construisent déjà correctement ce même composant
`minecraft:tool` (avec `BlockTags.MINEABLE_WITH_PICKAXE`) dans leur propre constructeur, exactement au
même moment du chargement du mod que nous - c'est le mécanisme que **toutes** les pioches vanilla
utilisent avec succès, donc garanti fonctionner à ce stade précis, sans qu'on ait besoin de deviner
quoi que ce soit. Confirmé via `AxeItem` dans le code source réel des exemples de `fabric-docs`
(`new AxeItem(material, attackDamage, attackSpeed, settings)`), `PickaxeItem` partageant la même
hiérarchie `DiggerItem` et donc très probablement le même patron de constructeur.

Bénéfice supplémentaire : `PickaxeItem`/`TieredItem` posent aussi automatiquement `durability`,
`enchantable`, `repairable` et les attributs d'attaque/vitesse à partir du même `ToolMaterial` et des
mêmes `attackDamage`/`attackSpeed` qu'on leur passe déjà - donc tous nos appels manuels
`.durability(...)`, `.enchantable(...)`, `.repairable(...)`, `.attributes(...)` sur `Item.Properties`
(ajoutés lors de sessions précédentes) sont devenus redondants et ont été retirés de
`HammerData.register()`, qui ne pose plus que `.setId(...)`, `.stacksTo(1)` et `.fireResistant()`
(propriétés propres au marteau, pas dérivées du matériau). Simplifie le code en plus de corriger le
bug - moins de surface pour deviner une API incertaine.

**4e tentative - `PickaxeItem` n'existe pas du tout en 26.2, corrigée au retour suivant** : la
supposition de la 3e tentative (signature déduite par analogie avec `AxeItem`) était en fait basée sur
une prémisse fausse - `import net.minecraft.world.item.PickaxeItem` a échoué avec `cannot find symbol:
class PickaxeItem`. Cette classe n'existe simplement plus en 26.2. Trouvé la vraie explication et le
vrai remplacement en cherchant un autre usage réel et compilable dans les tests `fabric-item-api-v1` :
`CustomDamageTest.WeirdPick`, qui étend `Item` **directement** (pas de classe dédiée) et pose tout via
`new Item.Properties().pickaxe(ToolMaterial.GOLD, 3f, 5f)`. Les pioches, comme les épées avant elles,
n'ont apparemment plus besoin d'une classe dédiée puisqu'elles n'ont pas de comportement de clic-droit
spécial (contrairement aux haches/houes/pelles, qui elles ont probablement encore des classes dédiées,
vu `AxeItem` confirmé plus haut) - tout passe par un raccourci sur `Item.Properties`.

Corrigé en repassant `HammerItem` à `extends Item` (comme dans les toutes premières versions de ce
fichier) et en ajoutant `.pickaxe(material, attackDamage, attackSpeed)` à la chaîne `Item.Properties`
dans `HammerData.register()` à la place de l'héritage `PickaxeItem`. Le constructeur `HammerItem`
redevient aussi plus simple (`material, settings, breakRadius, data)`, sans avoir besoin de
`attackDamage`/`attackSpeed` en paramètres séparés puisque `.pickaxe(...)` s'en charge en amont.

## Versions retenues

Mêmes versions que le portage Adabranium (déjà confirmées par une compilation + un lancement
réussis) : Fabric Loader 0.19.3, Fabric Loom 1.17, Fabric API 0.156.0+26.2, Java 25.

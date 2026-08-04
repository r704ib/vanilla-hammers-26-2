# Vanilla Hammers — portage Minecraft 26.2

Portage non-officiel du mod Fabric **Vanilla Hammers** (Draylar) vers Minecraft **26.2**. Le mod
original est disponible ici : https://github.com/Draylar/vanilla-hammers (branche `1.19.2`, version
de base de ce portage).

Contrairement au portage d'[Adabranium](https://github.com/r704ib/adabranium-26-2) (quelques mois
d'écart, même système de mappings), celui-ci part d'une version bien plus ancienne (1.19.2, ~4 ans
d'écart) et **réimplémente le mod plutôt que de le traduire ligne à ligne** :

- Les mappings d'origine sont **Yarn**, pas les mappings officielles Mojang qu'utilise 26.2 — quasi
  tous les noms de classes sont différents.
- Le mod dépendait de 3 bibliothèques externes du même auteur (**Magna** pour la mécanique de
  minage 3×3, **StaticData** pour le chargement de données inter-mods, **OmegaConfig** pour l'écran
  de configuration) — aucune n'a été mise à jour au-delà de 1.18-1.20. Elles ne sont **pas portées** ;
  leurs fonctionnalités essentielles sont réimplémentées directement dans ce dépôt (voir
  [PORTING_NOTES.md](PORTING_NOTES.md)).

## Le mod

Ajoute des marteaux qui minent une zone 3×3 (perpendiculaire à la face visée) au lieu d'un seul
bloc : bois, pierre, fer, or, diamant, netherite, plus des matériaux "extra" (slime, quartz,
prismarine, obsidienne, lapis, ender, émeraude, ardent).

Compatible avec les mods qui fournissent leurs propres marteaux via la même convention de données
(`static_data/vanilla-hammers/hammers/*.json`) — par exemple
[Adabranium](https://github.com/r704ib/adabranium-26-2) (vibranium, adamantium, nether).

## Licence

MIT, comme le mod d'origine (voir [LICENSE](LICENSE)).

# Implementation — Ship roster pass

Full sweep over every hull sprite in `graphics/ships/`, turning them into complete, balanced,
consistent ships. Context/decisions were captured live with the developer (names, systems, balance
all developer-approved).

## What was built

**19 hulls**, each with: canonical id, `.ship` (outline-traced bounds), `ship_data.csv` row, custom
Covenant shield wiring (combat hulls), `.variant` (originally vanilla-weapon placeholders, since
re-fitted — see [weapons-arsenal.md](weapons-arsenal.md)), and a codex description.

| Class | Hulls |
| --- | --- |
| Frigates (3) | Rasho Escort (support), Ceudar Gunboat, Ardo'nikapoora Landing (troop) |
| Destroyers (4) | SDV Heavy Corvette (PD screen), CPV Escort Carrier, CRS Strike, Ket Assault |
| Cruisers (4) | CCS Battlecruiser, DDS Escort Carrier, ORS Light, RCS Armored (tank) |
| Capitals (3) | Reverence Battleship (tank), ORS Heavy Cruiser, CPV Heavy Destroyer |
| Supercaps (2) | CAS Assault Carrier, CVS Supercarrier |
| Wings (3) | Seraph (2-config: fighter/bomber), Type-26 Banshee, Type-27 Banshee |

## Key facts / decisions

- **Custom shield wiring:** combat hulls set `shield type = PHASE` + `defense id =
  covenant_remnants_covenantshield` in `ship_data.csv`, plus a row in `data/hulls/shield_data.csv`
  (cols: `hullId, shield hp, shield regen, cooldown, shield regen delay, broken shield cooldown` —
  parser `utils/Data.kt` reads shield hp / shield regen / shield regen delay / broken shield
  cooldown by name; `shield regen rate` column deleted as redundant). Fighters get no shield.
- **Bounds** are **outline-traced placeholders** (alpha silhouette → simplified polygon; tool:
  `scratchpad/trace_bounds.py`). Weapon-slot **positions** are placeholder. Both need refinement in
  the in-game ship editor — see [TODO.md](../TODO.md).
- **Balance** is vanilla-anchored ("slightly above vanilla, rarer/pricier"); all numbers are
  placeholder and tunable.
- **IDs** migrated legacy `covenantremnants_` → canonical `covenant_remnants_` (this pass +
  weapons pass completed the migration — none remain).
- Deleted the `banshee`/`banshee2` stub `.ship` files (wings rebuilt from scratch); purged junk
  `shield_data.csv` rows.
- **Grey hulls** (cruiser1, supercap1) are just a paintjob per the developer — same Covenant
  lineage, not a separate faction.

## Systems in use

pd_overdrive (SDV, see [pd-overdrive-system.md](pd-overdrive-system.md)), slipspacedrive, plus
vanilla systems (highenergyfocus, microburn, displacer, damper, recalldevice, targetingfeed,
fortressshield). See [TODO.md](../TODO.md) for the fortressshield→custom-shield wiring that is still
a stub.

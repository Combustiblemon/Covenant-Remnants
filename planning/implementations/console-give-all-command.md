# Implementation — `CovenantGiveAll` console command

Dev cheat for the Console Commands mod. Grants all Covenant Remnants content in one call so a save
can be loaded up for testing without playing through the quest gates.

## What it does

Discovers content **dynamically by canonical id prefix** `covenant_remnants_` (scanned from the spec
registries at runtime), so new ships/weapons/fighters/hullmods are picked up automatically — no id
lists to maintain. Campaign-only (`WRONG_CONTEXT` otherwise).

- **Ships** — spawns one empty-hull fleet member of each Covenant hull into the player fleet. Fighter
  hulls (`HullSize.FIGHTER` — `wing1/2/3`) are excluded; they deploy as wings, not ships. Hulls spawn
  unarmed by design — weapons come from cargo.
- **Weapons** — 30 of each to player cargo (`cargo.addWeapons`).
- **Fighters** — 30 of each LPC to player cargo (`cargo.addFighters`).
- **Hullmods** — learned via `playerFaction.addKnownHullMod` (installable in refit). Hidden hullmods
  (`isHidden`/`isHiddenEverywhere`, e.g. `covenant_remnants_hullmodmanager`) are skipped.

Prints a summary line with per-category counts.

## Files

- `src/org/combustiblelemon/covenantremnants/commands/CovenantGiveAll.kt` — `BaseCommand`; modeled on
  `RegenerateSlipspace.kt`. Uses `SettingsAPI.getAll{ShipHull,Weapon,FighterWing,HullMod}Specs`,
  `createEmptyVariant`, `Factory.createFleetMember`, `CargoAPI.addWeapons/addFighters`,
  `FactionAPI.addKnownHullMod`.
- `data/console/commands.csv` — registered the `CovenantGiveAll` row.

## Decisions (confirmed with dev)

- Ships spawned into fleet (not blueprints); weapons/fighters cargo-only at qty 30; hullmods learned;
  dynamic prefix discovery over a hardcoded list.

## Notes / open items

- Decorative `*covercap` weapons carry no prefix, so the filter drops them automatically.
- Console Commands is a Gradle `compileOnly` reference, **not** a declared `mod_info.json` dependency.
  The command registers at runtime only when Console Commands is active. Developer ruling
  2026-10-01: Console Commands stays optional.
- Verified against starsector-knowledge (0.98a-RC8); builds clean via `buildMod`. Not yet run in-game.

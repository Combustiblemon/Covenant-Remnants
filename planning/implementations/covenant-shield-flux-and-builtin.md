# Implementation — Covenant Shield: max flux penalty + enforced built-in

Two changes to the Covenant Shield hullmod (`covenant_remnants_covenantshieldhullmod`), asked for
by the developer on 2026-10-17.

## What changed

1. **−20 % max flux.** The hullmod now applies `fluxCapacity.modifyMult(id, 0.8f)`
   (`CovenantShield.MAX_FLUX_MULT`). It applies on every ship carrying the hullmod: built-in on
   Covenant hulls, or installed on another hull.
2. **Built-in on every shield hull, enforced at load.** `CovenantShield.ensureBuiltInOnShieldHulls()`,
   called from `CovenantRemnantsPlugin.onApplicationLoad`, walks `allShipHullSpecs` and adds the
   hullmod as built-in to any spec whose `shipDefenseId` is `covenant_remnants_covenantshield` and
   lacks it (logged per hull). Before this change, all 16 combat hulls already had it in their
   `.ship` files. The check catches the ship editor dropping it again (it did twice) and
   other mods' hulls using the system.

## Files

- `src/.../hullmods/CovenantShield.kt` — `MAX_FLUX_MULT`, `ensureBuiltInOnShieldHulls()`, flux
  modifier in `applyEffectsBeforeShipCreation`, description param 8.
- `src/.../CovenantRemnantsPlugin.kt` — calls `ensureBuiltInOnShieldHulls()` after the
  incompatibility install.
- `data/hullmods/hull_mods.csv` — Covenant Shield description gains "Reduces maximum flux capacity
  by %s."

## Key decisions

- **Multiplier, not percent.** `modifyMult(0.8)`, the same style as the mod's other hullmods.
  (Plasma Capacitors' `fluxCapacity ×1.15` was removed in its 2026-10-17 rework, see
  [plasma-capacitors-rof.md](plasma-capacitors-rof.md).)
- **Flux modifier sits before the early return.** `applyEffectsBeforeShipCreation` returns early on
  hulls whose defense is already the Covenant shield. That covers the Covenant hulls, and also the
  cloned spec HullmodManager gives other hulls. The modifier is placed first so it applies in every
  case.
- **Spec-level enforcement.** `addBuiltInMod` changes the loaded hull spec in memory each launch.
  No data files are rewritten. Runtime clones (tagged `covenant_remnants_covenant_shield_temp`)
  are not in `allShipHullSpecs`, so they are untouched.

## Verify

`./gradlew.bat buildMod` compiles clean (2026-10-17). In game:

1. Covenant hull refit: max flux 20 % lower than the hull's base; tooltip shows "Reduces maximum
   flux capacity by 20%."
2. Non-Covenant hull with the hullmod installed: same −20 %.
3. `starsector.log` shows no "Added … as built-in" lines while the `.ship` files are correct. Remove the
   hullmod from one `.ship` `builtInMods` as a test: it still shows built-in and the log names that hull.

## Open items

- In-game check above (tracked in [../TODO.md](../TODO.md)).
- −20 % falls under the general "Tune hullmod numbers" pass.

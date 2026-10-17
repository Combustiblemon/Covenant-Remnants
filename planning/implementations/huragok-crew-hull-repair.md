# Implementation — Huragok Crew update (conditional CR, exact repair cancel, hull repair)

Huragok Crew (`covenant_remnants_huragok_crew`) still counteracts Covenant Systems, but its CR bonus
now needs Systems on the ship, its repair-rate bonus fully cancels the Systems penalty, and it adds
in-combat hull repair. All choices confirmed with the developer on 2026-10-17.

## What changed

| Effect | Before | Now |
| --- | --- | --- |
| Max CR | +20 % always | +20 % only if the ship has Covenant Systems (built-ins count) |
| Repair rate | ×1.25 always (net ×0.9375 with Systems) | ×1/0.75 ≈ ×1.333 always (net ×1.0 with Systems, +33 % without) |
| Hull repair in combat | none | elite Combat Endurance numbers: 0.5 % max hull/s while below 100 % hull, total per battle = max(2000 points, 50 % max hull) |

## Files

- `src/.../hullmods/CovenantSystems.kt` — `MAX_CR_PENALTY` and `REPAIR_PER_DAY_MULT` moved into the
  companion as `const val` (values unchanged) so Huragok derives its offsets from them.
- `src/.../hullmods/HuragokCrew.kt` — `MAX_CR_BONUS = CovenantSystems.MAX_CR_PENALTY`,
  `REPAIR_PER_DAY_MULT = 1f / CovenantSystems.REPAIR_PER_DAY_MULT`; CR gated on
  `variant.hasHullMod(CovenantSystems.ID)`; `applyEffectsAfterShipCreation` adds the nested
  `HullRegen` listener; description params reordered.
- `data/hullmods/hull_mods.csv` — Huragok Crew description rewritten for the new effects.

## Key decisions

- **Hull repair numbers come from vanilla's statics** (`CombatEndurance.REGEN_RATE`,
  `TOTAL_REGEN_MAX_POINTS`, `TOTAL_REGEN_MAX_HULL_FRACTION`, `MAX_REGEN_LEVEL`), read at use time, so
  the hullmod always matches the skill — including when another mod retunes it.
- **Stacks fully with the skill** (developer ruling). `HullRegen` is a port of
  `CombatEndurance.CombatEnduranceRegen` with its own repaired total under engine custom-data key
  `covenant_remnants_huragok_regen_<ship id>_repaired`; a ship with an elite-Combat-Endurance
  captain repairs from two pools (~1 %/s, double total).
- **No captain key.** Vanilla also tracks repairs per captain (half carries over when the player
  switches ships); the hullmod repair belongs to the hull, so only the per-ship key is kept. A
  retreat and redeploy in the same battle keeps the pool spent.
- **Hull repair has no Covenant Systems condition** — only the CR bonus is gated.
- Vanilla's empty `DamageTakenModifier` stub dropped; the listener is only an `AdvanceableListener`.

## Verify

`./gradlew.bat buildMod` compiles clean (2026-10-17). In game (with `CovenantGiveAll`):

1. Covenant hull + Huragok Crew: refit max CR back to baseline, repair rate net 100 %.
2. Non-Covenant hull + Huragok Crew: max CR unchanged, repair rate +33 %; tooltip reads
   33 % / 20 % / Covenant Systems / 0.5 % / 2000 / 50 %.
3. Combat: damaged Huragok ship regenerates ~0.5 % max hull per second until the cap; with an
   elite-Combat-Endurance captain the rate roughly doubles.
4. Retreat and redeploy in the same battle: the spent pool is not reset.

## Open items

- In-game check above (tracked in [../TODO.md](../TODO.md)).
- Numbers fall under the general "Tune hullmod numbers" pass.

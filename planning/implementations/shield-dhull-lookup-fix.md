# Implementation — Shield 3000-cap fix (D-hull lookup miss) + shield init bugs

Developer reported the supercap1 shield UI capping at 3000 instead of 20000 after the
[shield-flux-tune](shield-flux-tune.md) pass.

## Root cause

- `CovenantGiveAll` iterated `settings.allShipHullSpecs` filtered only by id prefix. That list
  also contains the auto-generated damaged-hull specs (`covenant_remnants_supercap1_default_D`),
  so the command granted **two copies of every ship**; the developer was piloting a `_default_D`
  copy.
- `CovenantShieldsStats` looked up `shieldData` by raw `ship.hullSpec.hullId`. The `_default_D`
  id misses the map → fallback `generateDefaultShieldData()` → shieldHealth exactly **3000**
  (`utils/Data.kt`). Game log confirmed the CSV itself loaded 20000 correctly.

## Fixes

`src/.../systems/CovenantShieldsStats.kt`:

- Lookup now uses `ship.hullSpec.baseHullId` (strips `_default_D`; vanilla-standard practice for
  hull-id matching).
- **`shieldInit` was never set `true`** — the init block re-ran every frame the system was
  active, resetting `shieldHealth` to full each frame (near-infinite shield while active; also
  why regen felt too strong pre-tune). Now one-shot.
- Init ordering fixed: `maxShieldHealth` is set **before** `shieldHealth` (the health setter
  clamps to the current max, which used to be the stale initial `1f`).
- Stale-state guard: the script stores the `CombatEngineAPI` it initialized against; when
  `apply()` sees a different engine instance, it nulls the render/UI plugins and listeners,
  clears `damageInstances`, resets `breakCooldownActive` + `shieldInit`, and re-initializes.
  Covers the case where the stats-script instance outlives one battle.

`src/.../commands/CovenantGiveAll.kt`:

- Ship loop now skips damaged-hull specs: `if (spec.isDefaultDHull) continue`.

## Build note

The deployable jar is produced by the custom Gradle task **`buildMod`** (writes
`jars/CovenantRemnants.jar`), NOT the standard `jar` task (which writes `build/libs/`). Jar
rebuilt with all fixes.

## Notes / open items

- Existing save: fleet still holds the duplicate D-hull ships from earlier GiveAll runs — prune
  manually or re-add ships. The D-hull CAS now reads correct shield data anyway (baseHullId).
- **Unverified suspicion:** Starsector may share one stats-script instance across all ships with
  the same system id. If so, all Covenant ships in one battle share one shield pool
  (`shieldHealth`, `ship`, plugins are instance fields on the script). Test with 2+ Covenant
  ships deployed at once; if confirmed, refactor state to per-ship (map keyed by ship). Tracked
  in [../TODO.md](../TODO.md).

# Implementation — Hullmod pass (fixes, OP costs, built-ins, two new mods)

A pass over the Covenant hullmod set: fixed two bugs, priced the optional mods, baked two mods into
the hulls, and added two new hullmods. All choices confirmed with the developer during planning.

## Bug fixes

1. **Covenant Systems penalty was inverted.** The −CR / repair penalty only fired when Huragok Crew
   was *also* installed, so pairing them was punished. Now the penalty is **standalone** (always on);
   Huragok Crew offsets it. `CovenantSystems.kt` — removed the `if (has huragok)` guard, penalty now
   `maxCombatReadiness.modifyFlat(ID, -0.20f)` + `repairRatePercentPerDay.modifyMult(ID, 0.75f)`.

2. **CR magnitude units bug.** `maxCombatReadiness` is a **0–1 fraction** (vanilla always uses
   `* 0.01f`). Both hullmods used `modifyFlat(20f)` — i.e. **+2000% CR**. Corrected to `0.20f`
   (= 20 percentage points) in `CovenantSystems.kt` (penalty, `MAX_CR_PENALTY`) and `HuragokCrew.kt`
   (`MAX_CR_BONUS`). Description params updated to render `20%` correctly.

## Balance / structure

3. **OP costs** added to optional mods in `data/hullmods/hull_mods.csv` (vanilla-anchored, placeholders):
   - Huragok Crew: 5 / 10 / 15 / 25 (frig/dest/cruiser/cap)
   - Covenant Shield: 10 / 20 / 30 / 45
   - Slipspace Tuning: 4 / 8 / 12 / 20
   - Plasma Capacitors: 5 / 10 / 15 / 25
   - Covenant Systems + Nanolaminate stay 0 (they are built-in, below).

4. **Covenant Systems + Nanolaminate Armor built-in** on all 16 Covenant **combat** hulls (fighter
   hulls wing1/2/3 excluded). Added `"builtInMods": ["covenant_remnants_covenantsystems",
   "covenant_remnants_nanolaminate_armor"],` as line 2 of each `data/hulls/covenant_remnants_*.ship`.
   Consequence (developer-confirmed): the standalone Systems penalty is now a **baseline** on every
   Covenant hull — Huragok Crew cancels it. Both mods remain in `hull_mods.csv`, so they're still
   installable on non-Covenant ships.

## New hullmods

5. **Slipspace Tuning** (`covenant_remnants_slipspace_tuning`) — `hullmods/SlipspaceTuning.kt`.
   - `systemCooldownBonus.modifyMult(id, 0.7f)` — −30% slipspace drive cooldown (engine-applied).
   - Loosens the drive's flux ceiling: `systems/SlipspaceDrive.kt` `isUsable` now reads
     `SlipspaceTuning.FLUX_GATE` (0.85) instead of the hardcoded 0.7 when the hullmod is installed.
   - Restricted to hulls whose `hullSpec.shipSystemId == covenant_remnants_slipspacedrive`
     (frigate1, frigate3, destroyer2, supercap1) via `isApplicableToShip` / `getUnapplicableReason`.

6. **Plasma Capacitors** (`covenant_remnants_plasma_capacitors`) — `hullmods/PlasmaCapacitors.kt`.
   Optional, any ship, OP 5/10/15/25.
   - `fluxCapacity ×1.15` (+15%), `ventRateMult ×1.25` (+25% venting).
   - **Downside:** `overloadTimeMod +30%` — overloads last longer, so getting flux-swamped punishes
     harder (reinforces "weak when overwhelmed"). All values placeholders (verified stats
     `combat.java:2800/3004/2987`).

7. **Slipspace Drive** (`covenant_remnants_slipspace_drive`) — `hullmods/SlipspaceDriveHullmod.kt`.
   Flavour/marker hullmod with **no mechanical effect**. Built into **all 16 combat hulls** (added to
   the `builtInMods` array alongside Systems + Nanolaminate). Visible, `isApplicableToShip` returns
   false so it can't be manually installed (built-in application bypasses that check). Exists for
   campaign scripts to detect a slipspace-capable ship via `variant.hasHullMod(ID)`. Distinct from the
   slipspace drive *ship system* (`covenant_remnants_slipspacedrive`).

## Key decisions
- Penalty standalone + Huragok offset; CR magnitude fixed to fraction scale.
- Systems + Nanolaminate built-in; penalty baseline accepted.
- Plasma Capacitors: sustain buff with an overload-duration downside.

## Verify
`./gradlew.bat buildMod` compiles clean. In-game (with `CovenantGiveAll`):
- Covenant hull refit: Systems + Nanolaminate show built-in (0 OP); CR/repair reflect baseline penalty.
- Install Huragok Crew: CR/repair return to ~baseline.
- Optional mods cost OP per the table.
- Slipspace Tuning: only on frigate1/frigate3/destroyer2/supercap1; faster cooldown, jumps at higher flux.
- Plasma Capacitors: any ship; more flux capacity + venting, longer overloads.

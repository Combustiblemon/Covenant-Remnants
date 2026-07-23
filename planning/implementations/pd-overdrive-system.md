# Implementation — PD Overdrive ship system

A ship system for the SDV-class point-defense screen (destroyer1). Per the developer: it does **not**
add weapons — it sharpens the ones already installed, so the ship's fixed battery of small mounts
becomes a far more effective PD grid while active.

## What it does

While active, scaled by effect level: boosts weapon turn rate (+300%), beam-weapon turn rate,
autofire aim accuracy (flat), and energy-weapon range (+25%), with a purple jitter VFX.

## Files

- `src/org/combustiblelemon/covenantremnants/systems/PDOverdrive.kt` — `BaseShipSystemScript`; uses
  `MutableShipStatsAPI` `weaponTurnRateBonus` / `beamWeaponTurnRateBonus` / `autofireAimAccuracy` /
  `energyWeaponRangeBonus`.
- `data/shipsystems/covenant_remnants_pd_overdrive.system` — `type STAT_MOD`, `statsScript` → the class.
- `data/shipsystems/ship_systems.csv` — `PD Overdrive` row (charge 0 / active 6 / cooldown 10).

Assigned to `covenant_remnants_destroyer1` via its `system id` in `ship_data.csv`.

## Notes

Vanilla `targetingfeed` is the closest stock analog (range + turn rate); this is the bespoke
Covenant version. Model/template for future stat-mod systems.

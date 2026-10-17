# Implementation — weapon hullmods: Capacitor Discharge, Target Lattice, Overcharged Emitters

Three new optional hullmods. They came from the ideas list given when Plasma Capacitors was reworked
(see [plasma-capacitors-rof.md](plasma-capacitors-rof.md)). The developer picked them and confirmed
all numbers and rules on 2026-10-17.

All three:

- Install on any ship. OP 5 / 10 / 15 / 25. Tags `offensive, no_drop_salvage`, refit filter
  "Weapons". They do **not** require a dock.
- Same change for Plasma Capacitors: its dock requirement is removed and it moves to the same
  "Weapons" category.
- Show a status line for the player ship in combat, with a vanilla ship-system icon as placeholder.
- Are learned by `CovenantGiveAll` and go into the tester cache through the existing id-prefix scan.

## Capacitor Discharge (`covenant_remnants_capacitor_discharge`)

`hullmods/CapacitorDischarge.kt`

- **Effect:** after an active vent finishes, energy weapon damage is up to +15 % for 5 s
  (`energyWeaponDamageMult`).
- **Scaling:** the bonus scales with the flux level when the vent began. A vent from full flux gives
  +15 %, one from 50 % gives +7.5 %. A vent tapped at near-zero flux gives next to nothing (developer
  ruling, closes the free-buff exploit).
- **Downside:** active vent rate ×0.8 (`ventRateMult`).
- **Wiring:** an `AdvanceableListener` watches `fluxTracker.isVenting`:
  - Vent start: records the higher of the current and the previous frame's flux level.
  - Vent end: (re)starts the buff with that level.
  - A new vent while a buff runs replaces it when that vent ends.
- **Status line:** "+N% energy weapon damage (T s)", icon `high_energy_focus.png`.

## Target Lattice (`covenant_remnants_target_lattice`)

`hullmods/TargetLattice.kt`

- **The lock:** the last enemy ship hit by this ship's own weapons. A module counts as its station.
  - Fighters, missiles, hulks and allies are ignored. Hits on them neither build nor reset the
    bonus.
  - Hitting a different enemy ship moves the lock and resets the bonus to 0.
- **Building:** for 1 s after each hit on the lock, the bonus grows by +5 % per second, up to +20 %.
  - From 1 s to 3 s after the last hit it holds.
  - After 3 s it drains by 10 % per second.
  - Shield hits count.
  - Slow guns build slower than steady fire. Developer ruling: "build 1 s, hold to 3 s".
- **Applying:** the bonus is added to every hit on the locked target as
  `damage.modifier.modifyPercent`. It covers all of the ship's weapons, torpedoes included. It works
  through a `DamageDealtModifier` + `AdvanceableListener` on the ship.
- **Status line:** "+N% damage vs locked target", icon `targeting_feed.png`.

## Overcharged Emitters (`covenant_remnants_overcharged_emitters`)

`hullmods/OverchargedEmitters.kt`

- **Effect:** energy weapon rate of fire gets no bonus below 50 % flux. Above that it rises linearly
  to +20 % at full flux (`energyRoFMult`, updated every frame in `advanceInCombat`).
- **Downside:** +15 % overload duration (`overloadTimeMod`).
- **Stacking:** allowed with Plasma Capacitors (developer ruling). Together they reach ×1.44 at full
  flux. Riding high flux also slows Covenant shield regen, which balances it.
- Ammo regen is not raised (only fire rate was asked for).
- **Status line:** "+N% energy weapon rate of fire", icon `ammo_feeder.png`.

## Files

- New: `src/.../hullmods/CapacitorDischarge.kt`, `TargetLattice.kt`, `OverchargedEmitters.kt`.
- `data/hullmods/hull_mods.csv`: three new rows, and the Plasma Capacitors row loses `req_spaceport`
  / "Requires Dock" and gains `offensive` / "Weapons".
- `src/.../campaign/TesterCache.kt`: one-time modspec top-up for existing caches (flag
  `$covenant_remnants_tester_cache_weapon_hullmods_added`). See [tester-cache.md](tester-cache.md).

## Verify

`./gradlew.bat buildMod` compiles clean (2026-10-17). `hull_mods.csv` parses with every row at full
width, and the `%s` counts match the description params. In game:

1. Refit: all three are listed under the "Weapons" filter, installable away from a dock, OP 5/10/15/25,
   and the tooltips read correctly. Plasma Capacitors no longer needs a dock.
2. **Capacitor Discharge:** vent from full flux. The status line shows +15 % for 5 s. A vent from
   about 50 % shows about +7 %. Venting is visibly slower.
3. **Target Lattice:** hold fire on one ship. The bonus climbs to +20 % in about 4 s of steady fire.
   Switching to another ship resets it. Shooting fighters leaves it alone. Ceasing fire for more than
   3 s drains it.
4. **Overcharged Emitters:** the status line appears above 50 % flux and reaches +20 % at full flux.
   Overloads last longer.
5. Existing tester save: storage gains the three modspecs once, and the log has the top-up line.

## Open items

- Bespoke status icons (vanilla ones are placeholders).
- Numbers fall under the general "Tune hullmod numbers" pass.
- The AI does not know about Capacitor Discharge, so AI ships vent on vanilla logic.

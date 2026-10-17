# Implementation — Plasma Capacitors rework (rate of fire for flux cost)

Plasma Capacitors (`covenant_remnants_plasma_capacitors`) used to give +15 % flux capacity,
+25 % vent rate and +30 % overload duration. That overlapped two vanilla hullmods (Flux Coil Adjunct,
Resistant Flux Conduits). It is now an energy weapon damage-per-second hullmod. The developer
proposed the idea and confirmed all numbers on 2026-10-17.

## What it does now

| Stat | Change |
| --- | --- |
| Energy weapon rate of fire (`energyRoFMult`) | ×1.2 |
| Energy weapon ammo regen (`energyAmmoRegenMult`) | ×1.2 (same as rate of fire, so the Autocannon keeps the gain past its magazine) |
| Energy weapon flux cost (`energyWeaponFluxCostMod`) | ×1.2 |
| Beam flux cost (`beamWeaponFluxCostMult`) | ×1/1.2 (cancels the cost for beams) |

Net on a projectile weapon: damage per second ×1.2, flux per second ×1.44. On a Covenant hull,
Covenant Systems' ×0.8 flux cost puts flux per shot at 0.96 of the weapon's listed cost.

How rate of fire hits each weapon kind (0.98a internals):

- **Projectile weapons:** fire faster.
- **Burst beams** (Energy Projector, Plasma Lance): shorter cooldown, no extra flux.
- **Continuous beams** (Plasma Beam Emitter, Suppressor Beam): no effect either way.
- **Torpedoes:** untouched (missile type).

## Files

- `src/.../hullmods/PlasmaCapacitors.kt` — rewritten. The old flux capacity, vent and overload
  effects are removed.
- `data/hullmods/hull_mods.csv` — description rewritten.

## Key decisions

- **Beams exempt from the flux cost** (developer ruling). The beam cost formula is the energy flux
  mod × the beam flux mult (`computeActualFluxCost`), so setting the beam mult to 1/1.2 cancels the
  extra cost exactly. A flat modifier on the energy flux mod from another source would be scaled by
  1/1.2 for beams; nothing in this mod or vanilla adds one.
- **Burst beams keep the faster cooldown for free.** The developer accepted this when choosing
  "Exempt all beams".
- **Old effects replaced entirely** (developer ruling).
- OP cost unchanged (5 / 10 / 15 / 25).
- **Later the same day:** the dock requirement was removed, and the hullmod moved to the "Weapons" refit
  filter (`offensive` tag), alongside the new weapon hullmods. See
  [weapon-hullmods.md](weapon-hullmods.md).

## Verify

`./gradlew.bat buildMod` compiles clean (2026-10-17). In game:

1. Refit tooltip reads "rate of fire and ammo regeneration by 20%" and "flux cost by 20%".
2. Weapon stats in refit: Plasma Cannon / Repeater show higher flux per second and higher DPS.
   Plasma Beam Emitter flux per second is unchanged.
3. Combat: Autocannon refills its magazine faster. Energy Projector cooldown is shorter.

## Open items

- Six stock variants carry Plasma Capacitors (frigate1, frigate2, destroyer4, cruiser5, capital1,
  capital2). They were fitted for flux sustain. The variant-fit doctrine may want a re-check now
  that it is a damage mod with a flux cost.
- Numbers fall under the general "Tune hullmod numbers" pass.

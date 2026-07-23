# Implementation — Energy Projector

The developer's priority weapon: a large energy lance that melts through a target once its shields
are down and its armor is stripped, then punches out the far side.

## Behaviour

- Fires like a **Tachyon Lance** — heavy **burst beam** (not continuous): chargeup 0.5s, ~1s burst,
  4s cooldown, 1500 burst DPS, range 1200, 32 OP. **No EMP.** Large energy, mountable anywhere.
- **Melt:** at a hit point that has **no shield** (vanilla shield off / out-of-arc, and the Covenant
  phase-shield down) **and depleted armor** (<5% at that cell), applies **ramping bonus HULL
  damage**, climbing 1×→4× over ~1s of continuous burn; resets when the beam leaves, the point
  re-shields, or armor recovers.
- **Pierce-through:** while exposed, the lance continues past the exit point (800 su) and deals
  energy damage to whatever is behind the target on the beam line.

## Files

- `src/.../weapons/EnergyProjectorBeamEffect.kt` — `BeamEffectPlugin`. Reads `beam.getDamageTarget()`
  armor grid (`getCellAtLocation`/`getArmorFraction`) + shield (`isWithinArc`/`isOff`) and
  `phaseCloak.effectLevel` (Covenant-shield caveat). Applies melt via `engine.applyDamage(HIGH_EXPLOSIVE)`
  and drives the pierce visual.
- `src/.../weapons/EnergyProjectorEffect.kt` — pre-existing `everyFrameEffect`; locks turret turn-rate
  while firing (kept).
- `src/.../plugins/EnergyProjectorPierceRenderPlugin.kt` — `CombatLayeredRenderingPlugin`; draws the
  additive GL beam-strip past the exit point (purple fringe + white core, taper + hit-glow), self-fades.
- `data/weapons/covenant_remnants_energyprojector.wpn` — `specClass beam`, `beamEffect` → the pierce
  class, `everyFrameEffect` → the turn-lock. **Placeholder art:** vanilla Tachyon Lance sprites.
- `data/weapons/weapon_data.csv` — `Energy Projector` row.

## Open / TODO

- Bespoke turret + beam art (currently vanilla Tachyon sprites). See [TODO.md](../TODO.md).
- Damage/ramp/pierce numbers are placeholder and tunable.

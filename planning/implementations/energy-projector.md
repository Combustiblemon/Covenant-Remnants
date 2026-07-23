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

## Fixes (2026-07-23)

- **Burst-beam fade gate.** Melt + pierce now gate on `beam.didDamageThisFrame()`. Burst beams keep
  existing/rendering while fading out but deal no weapon damage then; previously the effect kept
  applying full melt + pierce during the fade because it only checked `damageTarget != null`.
- **Base-damage source.** Switched from `beam.damage.damage * amount` to
  `beam.damage.computeDamageDealt(amount)` — honors the DPS flag and the damage `multiplier`
  (skills/hullmods), instead of assuming raw DPS and ignoring buffs.
- **Framerate independence.** Two effects, both fps-independent:
  - *Damage value:* beams batch damage into ~0.1s ticks (`didDamageThisFrame()` true only on tick
    frames); `computeDamageDealt` returns the tick's real damage via the beam's own `dpsDuration`,
    not the passed `amount`. 60 vs 120 fps integrate to the same DPS.
  - *Melt ramp:* `rampTime` now accumulates on **every** exposed frame (real seconds), **before** the
    damage-tick gate. Previously it was gated on `didDamageThisFrame()`, so it advanced only ~0.1s
    ticks by one frame's `amount` — ramp reached full ~2× slower at 120 fps than at 60 fps.

## Open / TODO

- Bespoke turret + beam art (currently vanilla Tachyon sprites). See [TODO.md](../TODO.md).
- Damage/ramp/pierce numbers are placeholder and tunable.

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
- `src/.../plugins/EnergyProjectorPierceRenderPlugin.kt` — `CombatLayeredRenderingPlugin`; **re-renders
  the real beam** along the pierce segment so it is pixel-identical to the lance (same texture/scroll/
  width/core/fringe/charge-fade). See "Pierce visuals" below.
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

## Pierce visuals (2026-07-23)

Goal: the pass-through segment must look exactly like the real lance, not a hand-drawn lookalike.

- **Rejected — extend the one real beam.** The engine's beam loop calls `beamEffect.advance` and then
  *immediately overwrites* `getTo()` (obf `BeamWeapon.advance`), and the collision pass re-shortens
  `to` after that. Anything we write into `beam.to` from the effect never reaches render.
- **Rejected — invisible second beam weapon.** Weapons are hull-slot-bound; a fired beam originates at
  its mount, and there's no API to plant/re-aim a firing beam at an arbitrary moving world point.
- **Rejected — reimplement the internal textured-line renderer** (`com.fs.starfarer.renderers.L`).
  Not public API; obfuscated class + method names; brittle across game versions.
- **Chosen — reuse the beam's own `render()`.** The live `BeamAPI` object is a `LayeredRenderable`
  that draws itself `from`→`to` with its own texture animator. In our layered-render plugin we save
  the beam's real endpoints, set them to the pierce segment via the **public** `getFrom()/getTo()`
  vectors, invoke `"render"` reflectively, then restore. Result is the actual beam, so identical by
  construction. **Update-independence:** modelled on the `HullmodManager` shield swap — no obfuscated
  class/field/method names; the only reflected name is the `LayeredRenderable` interface method
  `"render"`, wrapped in try/catch so a future rename degrades to "no pierce visual", not a crash.
- Visual refresh moved **before** the `didDamageThisFrame()` gate in `EnergyProjectorBeamEffect`, so
  the pierce tracks the lance every exposed frame (between damage ticks and during burst fade) instead
  of strobing per ~0.1s tick. Pierce **damage** stays gated on the damage tick.
- **Origin = far-side exit, not the entry hit.** The visible pierce segment starts where the lance
  *leaves* the target's back, so the through-hull portion is never drawn (the ship sprite covers it).
  `farSideExit()` casts `CollisionUtils.getCollisionPoint` from a point beyond the ship back toward the
  entry hit; the first bounds intersection is the exit surface. Fallback to the entry hit for entities
  without usable bounds.
- **Pierce behaves as a real beam re-fired from the exit.** `firstPierceHit()` raycasts from the exit
  point along the lance line (≤ `pierceLength`) and returns the **nearest** contact across all ships:
  a raised shield (ray-vs-shield-circle via `rayCircleEntry()`, clamped to the shield's arc with
  `isWithinArc`) or, failing that, the hull bounds (`CollisionUtils.getCollisionPoint`). Damage is
  applied **once**, at that exact contact point, `bypassShields = false` — so a raised shield eats it
  and the beam stops there instead of tunnelling to a point behind the target. The visual `end` is
  clamped to the same hit, recomputed every exposed frame so an interposing shield blocks it live.
  Excludes the pierced target, the firing source, and phased ships.

## Open / TODO

- Bespoke turret + beam art (currently vanilla Tachyon sprites). See [TODO.md](../TODO.md).
- Damage/ramp/pierce numbers are placeholder and tunable.

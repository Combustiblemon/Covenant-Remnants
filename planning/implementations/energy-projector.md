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
- `src/.../weapons/EnergyProjectorExitBurn.kt` — damage-then-restore: stamps a REAL vanilla scorch
  decal at the pierce exit point. See "Exit-point scorch" below.
- `src/.../plugins/EnergyProjectorScorchCleanupPlugin.kt` — `BaseEveryFrameCombatPlugin` singleton
  (via `engine.customData`); fades out and removes the synthetic decals ~3s after the last burn per
  ship.
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
- **Origin = far-side exit, not the entry hit.** The visible pierce-out segment starts where the lance
  *leaves* the target's back, so the through-hull portion is not drawn over the ship.
  `farSideExit()` casts `CollisionUtils.getCollisionPoint` from a point beyond the ship back toward the
  entry hit; the first bounds intersection is the exit surface. Fallback to the entry hit for entities
  without usable bounds.
- **Through-ship segment on `UNDER_SHIPS_LAYER` (2026-08-06).** Ships whose sprite has holes/gaps
  (e.g. ring hulls) exposed the missing through-hull segment — the lance visibly vanished over the
  gap. Fix: the render plugin now draws **two** segments and is active on two layers:
  entry→exit on `CombatEngineLayers.UNDER_SHIPS_LAYER` (below all ship sprites, so it only shows
  through transparent sprite pixels — the holes) and exit→end on `ABOVE_SHIPS_LAYER` (unchanged
  pierce-out). Both reuse the same endpoint-swap re-render helper; visual-only, shown only while
  piercing, no damage/geometry change. `refresh()` takes `(beam, entry, exit, end)`.
- **Pierce behaves as a real beam re-fired from the exit.** `firstPierceHit()` raycasts from the exit
  point along the lance line (≤ `pierceLength`) and returns the **nearest** contact across all ships:
  a raised shield (ray-vs-shield-circle via `rayCircleEntry()`, clamped to the shield's arc with
  `isWithinArc`) or, failing that, the hull bounds (`CollisionUtils.getCollisionPoint`). Damage is
  applied **once**, at that exact contact point, `bypassShields = false` — so a raised shield eats it
  and the beam stops there instead of tunnelling to a point behind the target. The visual `end` is
  clamped to the same hit, recomputed every exposed frame so an interposing shield blocks it live.
  Excludes the pierced target, the firing source, and phased ships.

## Exit-point scorch (2026-08-07)

Goal: where the lance punches out of the hull, the armor should look damaged **exactly** the way it
does when armor takes real damage — the developer rejected a custom-rendered imitation and asked for
the actual vanilla visual. Settled: real vanilla decals via **damage-then-restore**, removed on a
timer.

- **Rejected — custom imitation** (first attempt, `EnergyProjectorScorchDecalPlugin`, deleted):
  redrew the vanilla decal sheets in a mod render plugin. Looked wrong; scrapped.
- **Rejected — invoking the vanilla decal renderer directly.** `com.fs.starfarer.renderers.damage.*`
  is fully obfuscated (reflection there is barred by the update-independence rule) and decal opacity
  is hard-derived from real armor state.
- **Chosen — damage-then-restore (`EnergyProjectorExitBurn`).** Once per beam damage tick at the
  exit point: snapshot the 21-cell armor pattern + hull + all weapon/engine health, run a synthetic
  hit through the real `engine.applyDamage`, let the game's own renderer create the decal (exact
  vanilla scorch + ember + stencil clipping), restore everything in the same call stack. Works
  because `setArmorValue` is a raw write with **no listener notify** (vanilla precedent:
  `DisintegratorEffect`), and the decal rebuild path skips cells above 90% armor — so restored-full
  cells keep their decal untouched.
- **Death/disable safety** (both guards required, pure API):
  - `setNextHitHullDamageThresholdMult(0f, 0f)` → hull damage computes to 0 before the death check
    (auto-reset by the engine per hit) — but a `DamageTakenModifier` (e.g. vanilla elite Damage
    Control) can overwrite it mid-hit, hence:
  - a `HullDamageAboutToBeTakenListener` returning `true` (added before, removed after) — applied
    after all listeners, cannot be overridden.
  - Weapon/engine disables are **deferred** to the components' interval-gated advance, never fired
    synchronously inside `applyDamage` — same-stack health restore means a disable can never
    trigger; `repair()`-then-set-health restore kept as insurance.
- **Floaty suppression:** damage numbers gate on obf `Ship.setSuppressFloaties(boolean)` — public,
  **stable name**, vanilla's own switch around synthetic damage in `Ship.disable()`. Called via
  `ReflectionUtils.invoke`; if it ever throws, the feature sets `broken` and turns itself off
  (phantom numbers are worse than no decal).
- **Sizing:** desired = armor rating (≈ half-depth scorch on pristine armor after the ~0.5 armor
  reduction), capped `0.9·15·min(inner 3x3)` / `0.9·30·min(outer ring)` so no in-bounds cell
  overflows into hull, floored at `0.2·rating` so embers keep flaring on stripped cells (the floor's
  overflow is neutralized by the hull guards + restore). `applyDamage` args: ENERGY, emp 0,
  bypassShields (skips shields entirely — no flux/ripple), source **null** (API-blessed; every use
  in the damage path is `instanceof Ship`-guarded — no attribution, no NPE), playSound false.
- **Timed fade-out (`EnergyProjectorScorchCleanupPlugin`):** vanilla decals normally persist all
  combat; opacity is re-derived from the armor grid by `syncWithArmorGridState()` (only for cells
  below 90% armor), which makes a fade drivable through public API. The burn records each cell's
  **post-hit** armor value (the stamped darkness); 3s after the last burn the fade runs 2.5s, each
  frame writing `lerp(postHit → real value, t)` into the burned cells, syncing (renderer lightens
  the decal), then writing the real values back. Cells with genuine damage converge exactly to
  their true look (no pop); pristine cells stop updating at the 90% gate (~10% alpha) and a final
  `clearDamageDecals()` + sync purges the leftover. Ember wrinkle: sync freezes ember decay, so the
  fade *starts* with one clear+sync — every decal reborn from grid state, ours at current darkness
  with ember zeroed. A fresh burn mid-fade restarts the timer (decal re-darkened by the burn
  anyway). Singleton via `engine.customData` (key `covenant_remnants_ep_scorch_cleanup`) so two
  projectors share one fade per ship; customData + plugin list reset per combat.
- **Accepted side effects (documented, invisible or negligible):** at fade start, the ship's *other*
  decals re-roll frame/rotation and lose active embers; mods' damage listeners see the synthetic
  damage; can consume the target's elite Damage Control proc; target's received-damage stats
  accumulate it; `sinceLastDamageTaken` resets; component auto-repair delay resets; god-mode player
  ships show no decal; live fighters never render decal glow (vanilla behaviour).

## Open / TODO

- Bespoke turret + beam art (currently vanilla Tachyon sprites). See [TODO.md](../TODO.md).
- Damage/ramp/pierce numbers are placeholder and tunable.
- Exit scorch not yet verified in-game; constants tunable in `EnergyProjectorExitBurn`
  (damage sizing) and `EnergyProjectorScorchCleanupPlugin` (`FADE_DELAY`, `FADE_TIME`).

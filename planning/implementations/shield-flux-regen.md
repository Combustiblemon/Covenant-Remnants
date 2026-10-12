# Implementation — shield regen flux falloff (2026-10-12)

The developer asked for Covenant shield regen to fall as flux rises, so a ship that has built up
flux recovers its shield more slowly. Until now the Covenant shield ignored flux: it has its own HP
pool and makes no flux of its own.

## What it does

Each frame, regen is multiplied by a flux factor on top of the existing post-hit pause and slow
stage:

```text
cap  = maxFlux * (1 + FLUX_REGEN_BONUS)
mult = clamp(cap / currFlux - 1, 0, 1)      (1 at zero flux)
```

Regen stays at full rate up to half of `cap`, then drops along a curve to 0 at `cap`. With the
starting bonus of 0:

| Flux (of max) | 0-50% | 60% | 75% | 90% | 100% |
| --- | --- | --- | --- | --- | --- |
| Regen | 100% | 67% | 33% | 11% | 0% |

A bonus above 0 pushes `cap` past max flux. That moves the knee higher, and a ship at max flux
keeps some regen. For example, bonus 0.5 gives full rate up to 75% flux and 50% regen at max flux.

- **Status line**: the "Shield regen" line now shows the combined multiplier (post-hit stage ×
  flux). For example, the slow stage (50%) at 75% flux reads "17% rate", and max flux reads
  "paused". The labels themselves did not change.
- **Recharge wave**: unchanged. It still fires only when the post-hit pause or Fortress Shield
  ends, never because flux moved.

- **Hullmod description** (Covenant Shield): a new line, "Regeneration slows once flux passes
  %s, down to %s at maximum flux". Both numbers are calculated from `FLUX_REGEN_BONUS`, so the
  text stays correct while it is tuned. With bonus 0 it reads 50% and 0%. The knee is shown
  capped at 100%.
- **Fleet Slipspace Jump AI**: the strand survival test multiplies the regen it counts by the
  falloff factor at the ship's current flux when it decides (`FleetSlipspaceJumpAI.effectiveHp`).

## Tuning

`FLUX_REGEN_BONUS` is a live console tunable in `ShieldTune`. It changes on the next frame and
resets when the game restarts:

- `ShieldTune` lists it along with the outline values.
- `ShieldTune FLUX_REGEN_BONUS 0.5` sets it.
- `ShieldTune reset FLUX_REGEN_BONUS` resets it.

## Files

- `src/.../systems/CovenantShieldsStats.kt`:
  - In the companion: `FLUX_REGEN_BONUS` (reuses `ShieldOutline.Tunable`) and `regenTunables`.
  - Also in the companion: `fluxRegenMultAt(fluxLevel)` (the formula) and `fluxRegenKnee()`.
  - New `fluxRegenMult(ship)`, which reads the current flux level and calls `fluxRegenMultAt`.
    It is applied at the regen site in `ShipListener.advance()` and in `maintainRegenStatus`.
- `src/.../hullmods/CovenantShield.kt`: description params 5 (knee) and 6 (regen at max flux)
  are new. The broken-shield cooldown moved from param 5 to 7.
- `data/hullmods/hull_mods.csv`: the new Covenant Shield description line.
- `src/.../ai/FleetSlipspaceJumpAI.kt`: `effectiveHp` scales regen by `fluxRegenMult(ship)`.
- `src/.../commands/ShieldTune.kt`: works over `ShieldOutline.tunables` plus
  `CovenantShieldsStats.regenTunables`, with case-insensitive lookup. The doc and reset message
  now say "shield" instead of "outline".
- `data/console/commands.csv`: ShieldTune help text names the regen falloff.

## Decisions (developer 2026-10-12)

- The formula is the developer's, kept as written. That includes the full-rate plateau up to half
  of `cap`; a linear falloff was offered and declined.
- The bonus is a fraction of max flux, not absolute flux, so it scales the same on every hull.
- The flux source is total flux (`fluxTracker.currFlux`, soft + hard).
- Tuning goes through a console tunable in the existing `ShieldTune` command, not LunaLib or a
  const.
- The status line shows the combined multiplier, and the recharge wave is untouched.
- Hullmod text: a new line with calculated numbers, not a plain line without figures.
- Jump AI: the falloff is taken at current flux, not the worst case (max flux).
- Implementation call: the result is clamped at 0, so a negative bonus typed in the console cannot
  drain the shield.

## Open items

- Tune `FLUX_REGEN_BONUS` in game, then hard-code the chosen default.
- In-game check: the hullmod tooltip shows the new line with the correct numbers, and the
  broken-shield cooldown is still right after the param shift.

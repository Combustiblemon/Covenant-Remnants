# Implementation — shield regen status line (2026-10-02)

The two optional shield-regen cues from TODO, built on the developer's go-ahead (2026-10-02):
nothing told the player that Fortress Shield freezes regen, or which regen phase the Covenant
shield is in after a hit.

## What it does

The player's Covenant ship gets one line in the top-left status list (vanilla
`maintainStatusForPlayerShip`, the Covenant shield's icon), title **"Shield regen"**:

| State | Text | Style |
| --- | --- | --- |
| Fortress Shield active (incl. charge-up/down) | frozen by Fortress Shield | debuff (red) |
| Post-hit pause (hull's `shieldRegenDelay`) | paused | debuff |
| Slow stage (`REGEN_SLOW_DURATION` 3 s at `REGEN_SLOW_MULT`) | 50% rate | debuff |
| Regenerating normally | full rate | normal |

Shown only while the shield is below full and not broken. At full shield there is nothing to say,
and a broken shield is on its own break cooldown (regen does not run then).

## Files

- `src/.../systems/CovenantShieldsStats.kt` — `regenStatusKey`, `maintainRegenStatus(ship,
  fortressActive)` called every frame from `ShipListener.advance()` (runs whether the shield is up
  or down, like the regen itself); the slow-stage percentage is read from `regenMult()`.

## Decisions

- Developer 2026-10-02: build both cues. Wording, one combined line (the states never overlap) and
  hiding it at full / broken are implementation calls, listed here for review.

## Open items

- In-game check (TODO): paused → 50% rate → full rate after shield hits; Fortress text on capital1;
  gone at full shield and while broken.

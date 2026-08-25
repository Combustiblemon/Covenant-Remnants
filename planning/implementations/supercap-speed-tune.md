# Implementation — Supercap speed tune

Raised base mobility of both supercapitals in `data/hulls/ship_data.csv`. Developer-approved
values (2026-08-25), prompted by CAS feeling absurdly slow at flux (effective 18) versus its
zero-flux speed (78).

## What changed

Rows `covenant_remnants_supercap1` (CAS) and `covenant_remnants_supercap2` (CVS), identical values:

| Column | Old | New | Effective (Nanolaminate ×1.2) |
| --- | --- | --- | --- |
| max speed | 15 | 30 | 36 at-flux, 96 zero-flux |
| acceleration | 10 | 15 | 18 |
| deceleration | 8 | 12 | 14.4 |

Turn rate / turn accel (4/4) unchanged.

## Why / key facts

- The 78-vs-18 gap the developer noticed is vanilla `zeroFluxEngineBoost` (+50 flat, all hull
  sizes) amplified by Nanolaminate Armor's ×1.2 `maxSpeed` mult: `(base + 50) × 1.2`. Not a bug —
  the tiny base of 15 just made it look extreme.
- Old effective at-flux speed (18) was below every vanilla capital, including the Atlas
  superfreighter (25), and half the Invictus (35) — the closest vanilla analogue (dreadnought,
  same `NEVER_DODGE_MISSILES` hint). Violated both the "slightly above vanilla" balance rule and
  the long-range-elite combat identity.
- New effective 36 sits just above Invictus (35) — consistent with "slightly above vanilla".
- Both supercaps kept identical mobility (developer choice: consistency over carrier-hangs-back).

## Files touched

- `data/hulls/ship_data.csv` — 2 rows, 3 columns each.

## Open items

- In-game verification: CAS in simulator should read 36 at-flux / 96 zero-flux.
- Part of the broader "tune all numbers" balance pass in [../TODO.md](../TODO.md).
- CLAUDE.md references a root `balance.txt` that does not exist in the repo — stale reference,
  flagged to developer.

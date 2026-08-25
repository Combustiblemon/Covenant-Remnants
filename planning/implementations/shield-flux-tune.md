# Implementation — Covenant flux + shield-regen tune

Follow-up to [supercap-speed-tune.md](supercap-speed-tune.md): after the speed buff the supercaps
felt too strong. Developer-approved (2026-08-25) flux cut + shield rework.

## What changed

### Max flux −20% on all 16 Covenant combat hulls (`data/hulls/ship_data.csv`)

Rationale: the custom Covenant shield absorbs damage with its own HP pool and generates **no
flux**, so Covenant hulls get less flux headroom than vanilla peers.

| hull | old → new | hull | old → new |
| --- | --- | --- | --- |
| frigate1 | 2800 → 2240 | cruiser3 | 9500 → 7600 |
| frigate2 | 3200 → 2560 | cruiser4 | 9000 → 7200 |
| frigate3 | 2000 → 1600 | capital1 | 25000 → 20000 |
| destroyer1 | 6500 → 5200 | capital2 | 25000 → 20000 |
| destroyer2 | 2600 → 2080 | capital3 | 18000 → 14400 |
| destroyer3 | 6000 → 4800 | supercap1 | 50000 → 40000 |
| destroyer4 | 8000 → 6400 | supercap2 | 45000 → 36000 |
| cruiser1 | 11000 → 8800 | cruiser2 | 5500 → 4400 |

Wings untouched (no Covenant shield). The `8/6/5/4%` helper column is ignored by the game — left
as-is.

### Supercap1 shield HP cut (`data/hulls/shield_data.csv`)

- `covenant_remnants_supercap1`: shield hp **45000 → 20000**. Regen rate stays 1200/s
  (developer choice). Supercap2 untouched (developer choice).

### Regen-slow mechanic — all Covenant shields (`CovenantShieldsStats.kt`)

- New `regenMult()`: prunes `damageInstances` older than a trailing 3s window
  (`regenSlowWindow`), sums the rest; if the sum exceeds 1% of max shield HP
  (`regenSlowThreshold`) regen is multiplied by 0.5 (`regenSlowMult`). Cumulative trigger —
  swarms of small hits also slow regen; every hit inside the window keeps the penalty alive.
- Wired into `ShipListener.advance()` regen line.
- **Replaced dead code:** `shouldRegen()` was never called anywhere and was buggy — its prune
  compared against `now + shieldDamageRechargeCooldown` (should be `now −`), discarding every
  damage instance. This is why regen previously ran at full rate even under fire.
  `shieldDamageRechargeCooldown` removed with it.

## Files touched

- `data/hulls/ship_data.csv` — 16 rows, max flux column.
- `data/hulls/shield_data.csv` — 1 row.
- `src/org/combustiblelemon/covenantremnants/systems/CovenantShieldsStats.kt` — regen mechanic.

## Key decisions (all developer-approved via AskUserQuestion)

- Flux cut: all Covenant combat hulls, flat ×0.8.
- Mechanic scope: all Covenant shields (thresholds relative to each hull's max shield HP).
- Window 3s, refreshed per hit; trigger cumulative within window, not per-hit.
- Supercap1 regen rate stays 1200/s; supercap2 shield untouched.

## Open items

- In-game verification (see TODO).
- Possible follow-up: UI indicator when regen is slowed (not requested).

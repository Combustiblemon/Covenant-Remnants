# Implementation — campaign burn speed raise (2026-10-10)

The Covenant hulls' campaign `max burn` in `data/hulls/ship_data.csv` goes up.

## Developer rulings (2026-10-10)

| Topic | Decision |
| --- | --- |
| Raise | +2 to every Covenant hull's `max burn`, capped at 9. |
| Hulls already above the cap | Kept: frigate1 and frigate2 stay at 10. The cap only limits the raise, so nothing gets slower. |

## Result

| Hull | Before | After |
| --- | --- | --- |
| frigate1, frigate2 | 10 | 10 (kept) |
| frigate3, destroyer1–4, cruiser3, cruiser5 | 9 | 9 (already at the cap) |
| cruiser1, cruiser2, cruiser4, capital2 | 8 | 9 |
| capital1 | 7 | 9 |
| supercap1, supercap2 | 6 | 8 |

The wings (`wing1`–`wing3`) have no `max burn` and are untouched.

A fleet moves at its slowest ship's burn. A Covenant-only fleet is now at least 8 with a supercap
and 9 without one, before skills and hullmods.

## Files

- `data/hulls/ship_data.csv` — `max burn` on 7 rows. The CSV was rewritten through a reader / writer
  round-trip that was checked to be byte-identical first, so no other field or line ending changed.

## Open items

- The values remain placeholders for the balance pass.

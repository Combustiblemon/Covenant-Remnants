# Implementation — engine flames render below the hull (2026-10-07)

Covenant engine flames were drawn on top of the ship sprite. Starsector has no engine-style
(`engine_styles.json`) or `.ship` `engineSlots` field for draw order. It is a **hull hint**:
`ShipTypeHints.RENDER_ENGINES_BELOW_HULL` (starsector-knowledge, 0.98a-RC8, API
`combat/ShipTypeHints`). The combat `Ship` reads it once in its constructor
(`renderEnginesUnder = hullSpec.getHints().contains(RENDER_ENGINES_BELOW_HULL)`), so it applies to
every engine slot on that hull. No vanilla hull uses it.

## Change (developer ruling 2026-10-07: all Covenant hulls, not fighter wings)

- `data/hulls/ship_data.csv`, `hints` column, 16 rows:
  - Empty hints set to `RENDER_ENGINES_BELOW_HULL`: frigate1-3, destroyer1, destroyer3-4,
    cruiser1, cruiser3-5, capital1-2.
  - `CARRIER` changed to `"CARRIER, RENDER_ENGINES_BELOW_HULL"`: destroyer2, cruiser2.
  - `NEVER_DODGE_MISSILES` changed to `"NEVER_DODGE_MISSILES, RENDER_ENGINES_BELOW_HULL"`: supercap1-2.
- wing1-3 (Seraph, Type-26/27 Banshee) unchanged.

## Open items

- In-game check (see TODO.md): flames under the sprite edge in combat; check the campaign fleet
  view too.
- Fighter wings: add the hint later if wanted. Not part of this change.

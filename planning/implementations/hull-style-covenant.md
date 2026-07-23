# Implementation — COVENANT hull style on all ships (2026-07-23)

All Covenant hulls now use the mod's own `COVENANT` hull style instead of vanilla `HIGH_TECH`.

## What changed

- Top-level `"style"` in all 19 `data/hulls/covenant_remnants_*.ship` files: `HIGH_TECH` → `COVENANT`.
- **Engine-slot styles left as `HIGH_TECH`** — those are a separate per-slot field (engine glow/flame
  look), not the hull style. Only the ship-level hull style was changed.

## Why

`COVENANT` is already defined in `data/config/hull_styles.json` (orange/purple explosion + flux-vent
colours, empty slot-cover sprites, Covenant graphics paths). Pointing the hulls at it gives Covenant
ships their own explosion/flux/shield-ring/slot-cover identity instead of the vanilla high-tech look.

## Open items

- Engine-slot styles could get a bespoke Covenant treatment later if the vanilla high-tech engine
  glow ever clashes; left as-is for now (not part of this change).

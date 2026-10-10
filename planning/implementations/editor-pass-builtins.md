# Implementation — Covenant Shield built-in restored on cruiser5 + supercap1 (2026-10-10)

Bug fix after the ship-editor pass (hulls committed in `13af425`). A read-only check on 2026-10-10
found two combat hulls without `covenant_remnants_covenantshieldhullmod` in `builtInMods`:

- `covenant_remnants_cruiser5` (CPV-class Heavy Destroyer);
- `covenant_remnants_supercap1` (CAS-class Assault Carrier).

Both use the Covenant shield defense in `ship_data.csv` (`shield type = PHASE`,
`defense id = covenant_remnants_covenantshield`). Every Covenant combat hull carries the hullmod
built-in since the incompatibility pass (2026-08-25, see
[vanilla-hullmod-incompatibility.md](vanilla-hullmod-incompatibility.md)).

## Cause

It is the same kind of editor drop as on 2026-10-01 ([in-game-test-pass-2026-10-01.md](in-game-test-pass-2026-10-01.md),
item 2). Git history (one `git log -S` check):

- `supercap1` lost the id in `1250a9b` (2026-10-02, an editor save in a shader commit);
- `cruiser5` was created without it in `1b51ad8` (2026-10-02).

`13af425` kept both as they were.

## Fix

The id is added as the 4th `builtInMods` entry in both `.ship` files, in the same order as the
other 14 hulls (covenantsystems, nanolaminate_armor, slipspace_drive, covenantshieldhullmod). The
editor formatting is unchanged.

## Files

- `data/hulls/covenant_remnants_cruiser5.ship`
- `data/hulls/covenant_remnants_supercap1.ship`

## Verification done

The fit checker (scratchpad `check_fits.py`, loose-JSON parser) checked all 16 combat hulls. Each
one carries all four built-ins: `covenantshieldhullmod`, `covenantsystems`, `nanolaminate_armor` and
`slipspace_drive`.

## In-game check

Open the refit screen of a CPV Heavy Destroyer and a CAS Assault Carrier:

- Covenant Shield is listed as built-in (0 OP);
- the vanilla shield hullmods are greyed out with "Incompatible with Covenant Shield".

## Open items

- After every ship-editor save, re-check `builtInMods` (standing rule).

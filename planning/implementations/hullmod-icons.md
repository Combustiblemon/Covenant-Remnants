# Implementation: hullmod icons (2026-11-18)

The developer supplied 32×32 hullmod icons in `graphics/covenant_remnants/icons/hullmods/`. Vanilla
hullmod icons are 32×32 too. They are wired through the `sprite` column of
`data/hullmods/hull_mods.csv`, as paths relative to the mod root, in the same format vanilla uses.
No code sets hullmod sprites.

## Mapping

| Hullmod id | Icon | Source |
|---|---|---|
| `covenant_remnants_covenantsystems` | `CR_Hullmods_Core.png` | developer list |
| `covenant_remnants_covenantshieldhullmod` | `CR_Hullmods_Shield.png` | developer list |
| `covenant_remnants_overcharged_emitters` | `CR_Hullmods_Energy.png` | developer list |
| `covenant_remnants_capacitor_discharge` | `CR_Hullmods_Vent.png` | developer list (ruling below) |
| `covenant_remnants_nanolaminate_armor` | `CR_Hullmods_Blaster.png` | developer list |
| `covenant_remnants_plasma_capacitors` | `CR_Hullmods_Weapons.png` | developer list |
| `covenant_remnants_hardened_shield_matrix` | `CR_Hullmods_Shield_matrix.png` | name match (ruling) |
| `covenant_remnants_deep_shield_reservoir` | `CR_Hullmods_Shield_deep.png` | name match (ruling) |
| `covenant_remnants_reactive_shell` | `CR_Hullmods_Armor_React.png` | name match (ruling) |
| `covenant_remnants_target_lattice` | `CR_Hullmods_Target.png` | name match (ruling) |
| `covenant_remnants_slipspace_drive` | `CR_Hullmods_Drive.png` | name match (ruling) |
| `covenant_remnants_slipspace_tuning` | `CR_Hullmods_Drive_Plus.png` | name match (ruling) |
| `covenant_remnants_huragok_crew` | `CR_Hullmods_Engeer.png` | name match (ruling) |
| `covenant_remnants_accelerated_emitters` | `CR_Hullmods_Weapon_Acc.png` | name match (ruling) |
| `covenant_remnants_stabilized_emitters` | `CR_Hullmods_Weapon_Stabile.png` | name match (ruling) |

- **Capacitor Discharge (ruling):** the developer's list mapped it twice, to Vent and to
  MainCannon. The ruling gives it Vent and leaves MainCannon unused.
- **Name matches (ruling):** the developer approved filename matches for the 9 hullmods that were
  not in the list.
- **Hidden helper:** `covenant_remnants_hullmodmanager` keeps the vanilla `generic_mod` icon.
- **Unused icons:** `CR_Hullmods_MainCannon.png`, `CR_Hullmods_Fighter.png`, `CR_Hullmods_Slot.png`.

## Edit method

- `hull_mods.csv` has multi-line quoted descriptions, so it was not rewritten row by row.
- `sprite` is the last column and was empty on every target row. Each path was appended at the end
  of the physical line where that record ends.
- The diff is 15 single-line edits, and the file still parses.

## Verification

- **Harness:** `hullmod_specs` (new command, works from the title screen) reports the sprite path
  of every `covenant_remnants_` spec, plus the loaded texture id and size. In 0.98.5a-RC7, all 15
  report the mapped path, a real texture id and 32×32.
- **Load log:** no errors or warnings from Covenant (vanilla lines only).

## Files touched

- **Covenant Remnants:** `data/hullmods/hull_mods.csv`, `data/campaign/abilities.csv`, `CHANGELOG.md`,
  `planning/TODO.md`, this file.
- **StarsectorHarness:** `src/.../cmd/CampaignCommands.kt` (`hullmod_specs`, `ability_spec`), `CHANGELOG.md`.

## Open items

- Decide uses for the MainCannon, Fighter and Slot icons.

## Slipspace Jump ability icon (same day)

- `data/campaign/abilities.csv`: the `covenant_remnants_slipspace_jump` `icon` changed from the
  vanilla `graphics/icons/abilities/direct_jump.png` to
  `graphics/covenant_remnants/icons/abilities/CR_Ability_WARP.png` (developer request).
- The icon is 48×48, the same size as all 16 vanilla ability icons. No code sets the icon.
- The commented-out `#Transverse Jump` row keeps the vanilla icon.
- **Verification:** the new harness `ability_spec` command (`id`) reports the new path, a real
  texture id and 48×48. The load log is clean.

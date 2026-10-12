# Loot-pool exclusion — Covenant content drops only from combat (2026-10-12)

Prompted by a player report: the game crashed while looting a station / salvage entity when the
random LPC roll landed on one of our fighter wings. No stack trace was available and the local
logs hold none; mod wing / variant / hull data checked out valid and nothing in `src/` touches LPC
generation. The crash itself is still open (see [../TODO.md](../TODO.md)). What the report did
expose: Covenant content was eligible for the random loot pool, against the quest-gated
acquisition rule in [../../CLAUDE.md](../../CLAUDE.md).

## Developer ruling (2026-10-12)

Hulls, wings, weapons and hullmods drop **only from combat** (post-battle salvage of a defeated
Covenant fleet) or from future quest content. Never from random salvage rolls, never as random
blueprints (drop groups, bar blueprint seller, contact dealer).

## Vanilla tag semantics (0.98a-RC8, verified in `FleetEncounterContext`, `DropGroupRow`, `BaseSalvageSpecial`, codex)

| Tag | Random loot (`drop_groups.csv`) | Combat loot (`lootWings` / `lootWeapons` / `lootHullMods`) | Other |
| --- | --- | --- | --- |
| `no_drop` | blocked | **blocked** | also blocks battle debris-field drops |
| `no_drop_salvage` | blocked | allowed | only used as a drop-group filter |
| `restricted` | blocked | allowed | also hides from codex and mission refit |
| `rare_bp` | enables random blueprints | n/a | enables bar / dealer blueprint sales |

`no_drop_salvage` is the tag that matches the ruling. `restricted` was rejected (hides codex
entries). `no_drop` was removed from hullmods because it also killed the combat drop.

## Changes (data only)

- `data/hulls/wing_data.csv` — 4 wings: `rare_bp` → `no_drop_salvage`.
- `data/weapons/weapon_data.csv` — 13 weapons: `rare_bp` → `no_drop_salvage`. The 3 decorative
  cover caps keep `no_drop, no_drop_salvage, restricted`.
- `data/hulls/ship_data.csv` — 16 combat hulls: `rare_bp` removed (tags cell now empty). Ship
  recovery after battle is not tag-gated, so hulls still salvage from combat.
- `data/hullmods/hull_mods.csv` — 7 player-facing hullmods: `no_drop` removed, `no_drop_salvage`
  kept. `covenant_remnants_hullmodmanager` (hidden helper) keeps `hide_in_codex, no_drop,
  no_drop_salvage`.

## Open items

- Crash root cause unknown; needs the reporter's `starsector.log` trace. Closing the random-pool
  path removes the reported trigger but does not prove the LPC itself is sound.
- In-game check: repeated salvage rolls show no Covenant LPC / weapon / blueprint; a defeated
  Covenant carrier yields LPCs, weapons and hullmods in the loot screen; codex still lists wings
  and weapons.

## LPC generation path audit (2026-10-12, read-only)

Traced end to end in 0.98a-RC8 (`SalvageEntity.generateSalvage` → `DropGroupRow.getPicker` /
`resolveToSpecificItem` → `CargoData.addItems(FIGHTER_CHIP)` → `CargoItemStack.readResolve` →
loot-screen icon `campaign.ui.trade.A` and tooltip `CargoTooltipFactory` + `trade.D`), plus the
decompiled `FighterWingSpec` / `FighterWingSpreadsheetLoader` / `ShipHullSpreadsheetLoader`.
Every value the path reads, and what our data gives it:

| Step reads | Default if blank | Ours | Failure mode if wrong |
| --- | --- | --- | --- |
| `rarity` (picker weight) | 1.0 | blank → 1.0 | 0 → never picked, no crash |
| `tier` (`ftr_:{tier:N}` filter) | 0 | 3 / 3 / 1 / 3 | mismatch → filtered out |
| `tags` (`!no_drop`, `!restricted`, `!no_drop_salvage`) | none | now `no_drop_salvage` | — |
| `base value` (`qty = value / baseUnitValue`) | 100 | 5000–11000 | 0 → `Infinity` → int overflow stack |
| `variant` id → `HullVariantSpec` | required | all 4 resolve | missing → NPE in `getDisplayName` / icon |
| hull `hullName`, `descriptionId`, `tech/manufacturer` | id / "Common" | set, descriptions exist | missing description → NPE in tooltip |
| hull style `fighterChipIconColor` | required | set in `hull_styles.json` | missing → NPE in icon |
| `min crew` / `max crew` | 1 / 1 | blank → 1 | — |
| tooltip builds a combat `Ship` from the variant (weapons + effect plugins instantiated) | — | repeater (no plugin), light torpedo (`PlasmaTorpedoEffect`, no-op constructor) | plugin touching `CombatEngine` in constructor → NPE outside combat |

No edge case in the generation path matches our data. The remaining crash candidates are outside
this path (loot-screen GL rendering of the fighter sprite, or another mod). Needs the reporter's
trace. `hs_err_pid34456.log` in `starsector-core` is from 2025-09-01 (combat damage-decal native
crash under the IntelliJ debug agent) and is unrelated.

# Implementation — descriptions audit + Codex lore (2026-10-17)

A pass over every player-facing description:
- added the missing ones;
- corrected the wrong ones;
- fixed what the Codex showed wrongly;
- added Codex-only lore.

Developer rulings (2026-10-17):
- **Lore voice:** written as Sector analysts studying recovered Covenant technology.
- **Hullmods:** get a Codex-only lore paragraph; refit tooltips stay mechanical.
- **EMP System:** hidden from the Codex.
- **Hull texts:** reworded to match the stock fits.
- **No em dashes** in descriptions. Spaced hyphens used as a stand-in dash are banned too.

## Audit findings and fixes

| Area | Finding | Fix |
| --- | --- | --- |
| Weapons | No `WEAPON` rows at all: 13 weapons read "No description... yet" in refit and Codex. | 13 rows written from current behaviour (research report on `.wpn`/`.proj`/Kotlin). |
| Ship systems | Only the Slipspace Drive had a row; Covenant Shield, PD Overdrive and Fleet Slipspace Jump showed "Special / No description". | 3 rows in vanilla's layout: text1 description, text2 type, text3 tooltip summary, text5 Codex "Effects" with `{{}}` highlights. |
| EMP System | `covenant_remnants_pd_frigate` is used by no hull (leftover of a removed frigate) but was listed in the Codex. | `hide_in_codex` tag in `ship_systems.csv`; files kept. |
| Cover caps | The three decorative nose caps appeared as Codex weapons under raw ids. | `hide_in_codex` added to their tags in `weapon_data.csv`. |
| Hull texts | frigate2 and destroyer3 named a "plasma barrage system" they don't have (High Energy Focus / Microburn). DDS claimed Targeting Feed sharpens its own guns (vanilla: fighters only). Ket, ORS light cruiser, CPV heavy destroyer and CAS described "projectors" their fits don't carry. Reverence claimed the deepest shield of any Covenant hull (the supercarriers are deeper). CPV heavy destroyer was called a "capital" (it is a cruiser). | 13 SHIP rows reworded to match the stock fits and systems. frigate1, destroyer1, Reverence and CAS also now name their systems. |
| Fighters | Seraph had "shielded pilots" (fighter hulls have no shield); Type-26 text ignored its interceptor role. | Reworded. |
| Em dashes | supercap2 text had one, and so did a Slipspace Jump block message ("Already in slipspace — ..."). | Replaced; the CSV pass rejects em/en dashes and " - " in any description cell. |
| Covenant Shield Codex page | The Codex calls `getDescriptionParam` with no ship, so the per-hull numbers fell back to the default row: "3000" capacity, "1" regen, "1" s delay, "1" s collapse. | With no ship, those four show the range across the Covenant hulls ("2400 to 35000", ...). |
| Slipspace Jump Codex page | Built with an empty fleet, it read "Costs 0 fuel per light-year" and left out the Navigation System upgrade. | Codex mode states the fuel rate (% of the fleet's usual rate) and adds a paragraph on the upgrade. |

Left as is:
- Hullmod `short` column: there is no API accessor for it in 0.98a, so it is unused.
- UI separators such as "System - 12 LY" and "Fuel required: X - available: Y". They are labels, not descriptions.

## Codex lore

**Archive pages.** `data/codex/covenant_remnants_archive.txt` is written in vanilla Codex text markup. It holds the "Covenant Archive" category with six pages:
- The Covenant
- Ship Designations (links every hull)
- Huragok
- Slipspace
- Energy Shielding
- Plasma Weaponry

Pages cross-link with `{{rel:...}}` and `RELATED`.

**Loading.**
- 0.98a ships its text-entry loader switched off (`CodexDataV2.WITH_GAME_MECHANICS_CAT = false`), so `data/codex/text_codex_entries.csv` is never read.
- `utils/CodexLore.addArchiveEntries()` runs vanilla's own parser instead. It is called from `CovenantRemnantsPlugin.onAboutToLinkCodexEntries()`.
- The sequence is `CodexTextEntryLoader.parseContents` → `CodexDataV2.rebuildIdToEntryMap` → `CodexTextEntryLoader.linkRelated`.
- The file is deliberately not listed in `text_codex_entries.csv`, so a future vanilla that turns the loader on won't load it twice.

**Hullmod lore.**
- `CodexLore.addHullmodLore(tooltip, id)` is called from `addPostDescriptionSection` on the 14 visible optional and built-in hullmods; the shield tweaks get it through `CovenantShieldTweak`.
- It adds a gray paragraph only while `Global.CODEX_TOOLTIP_MODE` is on. The Codex sets that flag while it renders a page.
- Text lives in `descriptions.csv` as `CUSTOM` rows `<hullmod id>_codex`. No `%` signs, because the text goes through `addPara`.
- Slipspace Drive is left out: its refit description is already pure flavour.

**Provenance.** The archive deliberately leaves how Covenant ships reached the Sector unexplained ("None of it explains how Covenant ships came to be in the Sector"), so the quests can still decide it. It does commit to:
- the Covenant's self-description: a multi-species faith alliance with a priestly caste and a schism;
- surviving Huragok, which "do not appear to take sides";
- slipspace leaving no trace on hyperspace charts.

## Files

- `data/strings/descriptions.csv`: 13 SHIP rows reworded, 13 WEAPON rows, 3 SHIP_SYSTEM rows, 14 `_codex` CUSTOM rows. Written line by line so untouched rows stay byte-identical (the file quotes empty fields `""`).
- `data/codex/covenant_remnants_archive.txt` (new).
- `data/weapons/weapon_data.csv` and `data/shipsystems/ship_systems.csv`: `hide_in_codex` tags.
- `src/.../utils/CodexLore.kt` (new); `CovenantRemnantsPlugin.kt` (Codex hook).
- Hullmods with the lore hook: `CovenantSystems`, `NanolaminateArmor`, `CovenantShield`, `HuragokCrew`, `SlipspaceTuning`, `PlasmaCapacitors`, `CapacitorDischarge`, `TargetLattice`, `OverchargedEmitters`, and `CovenantShieldTweak` (which covers the five shield mods).
- `hullmods/CovenantShield.kt`: Codex ranges.
- `campaign/abilities/SlipspaceJump.kt`: Codex text.
- `campaign/abilities/SlipspaceJumpRules.kt`: em dash removed.

## Verify

`./gradlew.bat buildMod` compiles clean. An offline check of the archive found:
- BEGIN/END balanced, no text outside entries;
- every `rel`/`RELATED` target resolves to a real Codex entry id;
- no dashes.

In game:
1. **Codex root:** "Covenant Archive" sits last in the list with six pages. The related-entries panels link to hulls, weapons, hullmods, systems, items and the ability.
2. **Weapons:** every Covenant weapon shows its description in refit and Codex. The cover caps and EMP System are absent from the Codex.
3. **Ship systems:** the Covenant Shield, PD Overdrive and Fleet Slipspace Jump Codex pages show a type, a description and an "Effects" section.
4. **Hullmod Codex pages:** each ends with a gray "Analyst's note" paragraph; the same hullmods in refit don't show it. The Covenant Shield page reads "2400 to 35000" capacity instead of 3000.
5. **Slipspace Jump Codex page:** no "0 fuel"; the Navigation System paragraph is present.

## Open items

- Bespoke Codex icons (vanilla `generic` / `ships` / `hullmods` / `abilities` / `manual_shields` / `weapons` for now).
- Revisit the archive once the quests define how Covenant ships reached the Sector.
- The static numbers in the system "Effects" texts (30%, 6 s raise, 3 s slow stage, PD Overdrive and Fleet Jump values) need updating whenever those are retuned.
- PD Overdrive has no AI (`aiType NONE` in its `.system`), so AI-flown SDVs never use it. Found during the research, not fixed here.

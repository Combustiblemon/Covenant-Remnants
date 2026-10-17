# Implementation — tester cache in Corvus (2026-10-10)

The quests, which are the real way to get Covenant content (CLAUDE.md: quest-gated only), do not
exist yet. Testers still need everything, so this is a **temporary exception**:

- The storage of vanilla's Abandoned Terraforming Platform at Asharu, Corvus
  (`corvus_abandoned_station`), gets the whole Covenant catalogue once per save.
- It also gets a new item that teaches the Slipspace Jump ability.

The developer skipped the in-game test session the same day; the build goes to players for testing.

## Developer rulings (2026-10-10)

| Topic | Decision |
| --- | --- |
| Where | Corvus abandoned station storage (vanilla, already unlocked). |
| Ships | 1 of each supercap, 3 of each capital, 4 of each cruiser, 6 of each destroyer, 10 of each frigate. |
| Ship fits | The standard `_std` variants (the approved refit fits). |
| Weapons / wings | 30 of each. |
| Hullmods | Included. 1 modspec each of the optional ones; built-ins and hidden ones are skipped. Quantity and filter were the assistant's call, stated in the ruling round. |
| Item | **Huragok Slipspace Engram**: right-click teaches the Slipspace Jump ability. |
| Item icon | Vanilla placeholder (`graphics/icons/cargo/janus_device.png`) until real art exists. |
| When | Once per save, on the first load: new and existing saves both get it. The `TesterCache.ENABLED` constant turns it off for a real release. |

## What the cache holds (current content)

- **82 ships**, mothballed:
  - CAS and CVS: 1 each.
  - Reverence and ORS Heavy Cruiser: 3 each.
  - cruiser1–5: 4 each.
  - destroyer1–4: 6 each.
  - frigate1–3: 10 each.
- **13 weapons × 30.** All `covenant_remnants_*` weapons, the Suppressor Beam included. The three
  `*covercap` nose decorations have no prefix and are tagged `restricted`, so they are not included.
- **4 wings × 30**: Seraph fighter and bomber, Type-26, Type-27.
- **3 modspecs**: Huragok Crew, Slipspace Tuning, Plasma Capacitors.
- **1 Huragok Slipspace Engram.**

Content is found by the `covenant_remnants_` prefix, as `CovenantGiveAll` does, so new content
joins the cache automatically. That only affects saves that have not had the cache yet.

## How it is wired

- **`campaign/TesterCache.kt`** — `addIfNeeded()`:
  - It stops when `ENABLED` is off, or when the sector memory flag
    `$covenant_remnants_tester_cache_added` is set.
  - It finds the station's `storage` submarket cargo. With no Corvus (random sector) it logs a
    warning and stops.
  - Ships: `addMothballedShip(SHIP, "<hullId>_std")`, falling back to `<hullId>_Hull` with a
    warning if a variant is missing.
  - Weapons, wings and the engram are added with `addWeapons`, `addFighters` and `addSpecial`.
  - Modspecs: `addSpecial(SpecialItemData("modspec", id))` for every prefix hullmod that is not
    hidden and not in any Covenant hull's `builtInMods`.
  - Finally it sets the flag and logs one `Tester cache added to corvus_abandoned_station storage: …`
    line with the counts.
- **`CovenantRemnantsPlugin.onGameLoad`** calls it. That hook also runs for new games, after the
  sector exists.
- **`data/campaign/special_items.csv`** — new row for `covenant_remnants_slipspace_engram`:
  - manufacturer "Covenant Remnants", base price 100000 (as the Janus Device), stack 1000;
  - ultratech pickup / drop sounds, plugin below, placeholder description;
  - no tags. Vanilla's `mission_item` was tried and dropped, because the engine blocks moving
    mission items between cargo screens. Without tags the item is in no random loot pool.
- **`campaign/items/HuragokSlipspaceEngramPlugin.kt`** — modelled on vanilla's `JanusDevicePlugin`,
  with the blueprint items' "already known" handling:
  - The tooltip shows the title, design type, description, cost and "Right-click to learn the
    Slipspace Jump ability", or "Slipspace Jump already known".
  - Right-click calls `SlipspaceJumpRules.grantToPlayer()`, plays the item sound and shows a
    message.
  - `shouldRemoveOnRightClickAction = !known`: the engram is used up only when it teaches the
    ability.

## Verification done

- `gradlew buildMod` succeeded, and both classes are in the jar.
- `special_items.csv`: 16 columns on the header and the row, and the icon exists in vanilla.
- `package_mod.sh` ran with BUILD SUCCESSFUL. The zip carries the CSV and the classes.
- The zip pre-check (every referenced path resolves from the zip + vanilla) found 0 problems across
  251 paths.

## For testers / in game

1. Start or load a Corvus game. `starsector.log` should have the `Tester cache added …` line on the
   first load and none after.
2. At Asharu, open the Abandoned Terraforming Platform's storage: the ships, weapons, wings,
   modspecs and the engram are there.
3. Take the engram, right-click it: the Slipspace Jump ability is learned and the engram is used up.
   A second engram (via console) shows "already known" and stays.

## Open items

- Turn `TesterCache.ENABLED` off, or remove the class and CSV row, once the quests hand out
  Covenant content. The engram itself can become the granting quest's reward.
- Engram art (bespoke icon) and final description text.

## Later changes

- 2026-10-12 — the engram is renamed **Covenant Slipspace Drive Schematics** (developer ruling;
  same id `covenant_remnants_slipspace_engram`, same plugin class). The cache also holds one
  **Covenant Slipspace Navigation System** (`covenant_remnants_slipspace_navigation`, the Slipspace
  Jump navigation upgrade). Saves whose cache predates it get one copy on the next load, under the
  separate flag `$covenant_remnants_tester_cache_navigation_added`; the log line reads
  `Tester cache: added 1 covenant_remnants_slipspace_navigation …`. See
  [advanced-slipspace-jump.md](advanced-slipspace-jump.md).
- 2026-10-17 — three new optional hullmods (Capacitor Discharge, Target Lattice, Overcharged
  Emitters) join the cache as modspecs via the prefix scan, which makes **6 modspecs** for new
  caches. Saves whose cache predates them get one modspec of each on the next load, under the flag
  `$covenant_remnants_tester_cache_weapon_hullmods_added` (developer ruling, same pattern as the
  Navigation System). See [weapon-hullmods.md](weapon-hullmods.md).

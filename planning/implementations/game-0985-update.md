# Implementation: Starsector 0.98.5a-RC7 update (2026-11-18)

The game went from 0.98a-RC8 to 0.98.5a-RC7 (patch notes: `readme_files/patch-notes.txt`). This
pass checked whether Covenant Remnants, LemonLib and the test harness still work, fixed what broke,
and adopted the new API where it fits. Developer rulings from 2026-11-18 are marked (ruling).

## Compatibility findings

- **API:** an old-vs-new source diff found 16 removed or re-signed members. None is used by
  Covenant or LemonLib, among them `ShipAPI.isBeingIgnored()`, `UIComponentAPI.processInput`,
  `renderOnMapAbove(float,float)` and `FleetEncounterContext.computeLossFraction` (now returns
  `CrewLossFraction`).
- **Binary scan:** every shipped jar was checked against the new game jars, member by member and
  including the class hierarchy. The jars: Covenant, LemonLib, Harness, MagicLib, Ashlib, LazyLib,
  LunaLib, Console.
  - 0 missing classes and 0 missing members. Ashlib's one missing class is an AoTD reference, not
    vanilla.
  - 0 classes miss a new abstract method, because the new interface methods are `default`.
- **Obf and reflection targets** all still resolve: `CampaignFleet.setInJumpTransition`,
  `jumpDestination`, `CampaignFleetMemberView.setExtraAlphaMult`, `StarSystem.displayName`,
  `BeamWeaponRay`, `Missile`, campaign UI `showNoise(FFF)`, `Ship.setSuppressFloaties(Z)`, hull
  spec `clone()` (class now `loading.specs.O00o`), and `BeamWeaponRay.render(layer, viewport)`.
- **Script classloader:** the blocklist is unchanged. Its class is now named
  `com.fs.starfarer.loading.scripts.ScriptClassLoader` (was `loading.scripts.B`).
- **Launcher version check** (`ModManager$VersionInfo`, disassembled): the version string is split
  on `\.|a-RC|a`. `0.98.5a-RC7` parses to major `0.98`, minor `5`, patch `7`.
  - Our old `0.98a` was a MINOR_MISMATCH: a launcher warning only, and the mod still loads.
  - Only MAJOR_MISMATCH, an unspecified version or a missing module disables a mod.
  - Mods declaring `0.98a-RCx` get a patch-level mismatch.
- **Data formats:**
  - `ship_data.csv` gained `parts/dmod`, `salary` and `salMult`.
  - `.proj` gained `explodesWhenHittingFlares`, `.ship` gained `renderOrderMod`, and `.variant`
    gained `suppressedMods`.
  - Nothing we use was removed. Our `settings.json` overrides no changed key.
- **Acquisition:** no Covenant hullmod can drop as a modspec, since all carry `no_drop_salvage` and
  the new known-hullmod fallback keeps the drop-row tag filters. No Covenant hull reaches markets.
- **`CodexDataV2.WITH_GAME_MECHANICS_CAT` is still false**, so `utils/CodexLore.kt` is still needed.

## What was built

- **Version metadata:** `gameVersion` is `0.98.5a-RC7` in `mod_info.json` here, in LemonLib and in
  the harness. The game version was also updated in `CLAUDE.md`, `README.md` and `ARCHITECTURE.md`.
  - ARCHITECTURE's manifest line also had a stale `0.1.0`, now `0.1.3`.
- **Crew salary and d-mod repair cost (ruling).** Two columns were appended to `ship_data.csv`
  (column order is free, and appending keeps the other cells byte-identical). `salary` stays blank,
  so the vanilla formula is used: supplies/mo × 50/50/75/150 by size.
  - **`salMult` (by hull size):** 1.25 for frigates and destroyers, 1.5 for cruisers and capitals,
    2.0 for the CAS and CVS supercaps.
  - **`parts/dmod`:** 1.5 × the vanilla default, rounded half up. The default is
    max(1/2/4/8 by size, floor(base value / 15000)).

  | Hull | Size | salMult | Salary/mo | Parts per d-mod |
  |---|---|---|---|---|
  | frigate1, frigate2 | frigate | 1.25 | 375 | 3 |
  | frigate3 | frigate | 1.25 | 500 | 3 |
  | destroyer1, destroyer2 | destroyer | 1.25 | 625 | 6 |
  | destroyer3 | destroyer | 1.25 | 625 | 5 |
  | destroyer4 | destroyer | 1.25 | 688 | 5 |
  | cruiser1, cruiser4 | cruiser | 1.5 | 2812 | 14 / 12 |
  | cruiser2, cruiser3 | cruiser | 1.5 | 2250 | 12 |
  | cruiser5 (CPV Heavy Destroyer) | cruiser | 1.5 | 4500 | 35 |
  | capital1 (Reverence) | capital | 1.5 | 13500 | 54 |
  | capital2 (ORS Heavy Cruiser) | capital | 1.5 | 11250 | 50 |
  | supercap1 (CAS) | capital | 2.0 | 18000 | 90 |
  | supercap2 (CVS) | capital | 2.0 | 18000 | 80 |

  Checked in game: the hull spec reports 13500 / 1.5 / 54 for capital1 and 18000 / 2.0 / 90 for
  supercap1. `getCrewSalaryPerMonth` already includes `salMult`.
- **Dealer leak closed** (a pre-existing gap). `CustomProductionContract` builds its offers from
  every hull, weapon and wing spec, and skips only `no_dealer`, `no_sell` and `restricted`.
  - `no_dealer` (read by that contract only) was added to the 16 hulls, the 13 weapons and the 4
    wings.
  - The 3 cover-cap weapons are already `restricted`.
- **Torpedoes resist flares (ruling):** `"explodesWhenHittingFlares": false` on the standard, light
  and heavy plasma torpedo `.proj`. A flare now does its damage to the torpedo (150 / 300 / 500 HP)
  instead of detonating it, as vanilla does for Neutron and Rift torpedoes.
- **LemonLib `HullmodIncompatibilityWrapper` bug fix.**
  - Kotlin `by` delegation skips Java default methods, so the old jar never forwarded
    `applyEffectsAfterShipAddedToCombatEngine` or `getRequiredItem`, and never forwarded the new
    `applyEffectsAfterShipCreation2ndPass`.
  - All three are now forwarded explicitly, and the rebuilt class has them.
  - No hullmod we wrap overrides them today (shield, armor and targeting mods), so nothing was
    visibly broken.
- **Pre-0.98.5a save crash, fixed (`campaign/JitterSaveRepair.kt`, called first in `onGameLoad`).**
  - 0.98.5a added `JitterRenderer.collatedOffsets`, initialised where it is declared. XStream
    leaves it null for renderers read from old saves, so the first campaign frame threw an NPE in
    `JitterRenderer.render`.
  - A `CampaignFleetMemberView` keeps its renderer for good once `setJitter` has run, and
    `SlipspaceTransitionScript` jitters the player fleet's views on every arrival. The harness
    fixture (0.1.1) held 18 of them, all in our Slipspace color.
  - The repair walks every fleet's views. It finds the renderer field by type and the list by name
    (the class is `DoNotObfuscate`), then fills null lists.
  - It logs `repaired N pre-0.98.5a fleet jitter renderer(s)`. Vanilla Generate Slipsurge leaves the
    same trap.
- **Harness remap** (`../StarsectorHarness`, its own repo): `obf-map.json` pins `0.98.5a-RC7`.
  - The menu enum is now `com.fs.starfarer.title.OoO…O$o$o` (283 chars). Its `<clinit>` names are
    readable now, `CONTINUE` … `QUIT`, at ordinals 0-12 and cases 1-13, same as before.
  - The mission class is `com.fs.starfarer.title.C.K`. The unused `missionSpecClass` was dropped.
  - `docs/obf-notes.md` was updated.
  - The `ship` and `ships(weapons=true)` detail now also reports `phase` (`cloak`, `phased`,
    `really_phase_ship`) and `hull_economy` (salary, salary mult, parts per d-mod).

## Looked at, not adopted

- **`ShipRenderListener` for the Covenant shield (ruling: skip).** A decompile of the new
  `Ship.render` shows all three callbacks fire right after the hull sprite, inside the ship's
  translated matrix and clip. That is before its damage decals, engine glow, weapons and vent
  animation. Vanilla draws its own shields at the end of that per-ship pass, where no callback
  fires. Hooking in would draw our shield under the ship's turrets, so it stays on
  `ABOVE_SHIPS_LAYER`.
- **`SpriteAPI.setTexClamp`:** `Sprite` applies the flag only around its own `render*()` quad
  draws, and `SpriteAPI.bindTexture()` (`settings.B`) just binds. `ShieldTexture` / `RuptureTexture`
  bind the texture id and draw with our shaders, so the flag would have no effect. The raw
  `glTexParameteri` wrap calls stay.
- **`ShipAPI.isReallyPhaseShip()` (ruling: verify only).** Our four `phaseCloak.id ==` checks ask
  "is the Covenant shield up", not "is this a real phase ship", so swapping them would be less
  precise. The in-game probe gave Covenant capital1 / supercap1 / frigates `false` and Afflictor /
  Doom `true`. Vanilla's new phase-only rules (Neural Link hold, the survival-mode AM Blaster phase
  block) therefore skip Covenant ships.
- **Quest-side APIs** are logged in TODO for the Granting quest: `noBountyForDefeating`, the
  `settings.json` `rules` multi-file list, the `HubMissionWithTriggers` flee/aggressive triggers and
  `addStandardMarketDesc`.

## Files touched

- **Covenant Remnants:**
  - Metadata: `mod_info.json`, `CLAUDE.md`, `README.md`, `planning/ARCHITECTURE.md`.
  - Data: `data/hulls/ship_data.csv`, `data/hulls/wing_data.csv`, `data/weapons/weapon_data.csv`
    and the 3 `data/weapons/proj/covenant_remnants_plasmatorpedo*.proj`.
  - Code: `src/.../CovenantRemnantsPlugin.kt` and the new `src/.../campaign/JitterSaveRepair.kt`.
  - Docs: `CHANGELOG.md`, `balance.md`, `planning/TODO.md` and this file.
- **LemonLib:** `mod_info.json`, `src/.../hullmods/HullmodIncompatibilityWrapper.kt`, `CHANGELOG.md`.
- **StarsectorHarness:** `mod_info.json`, `data/harness/obf-map.json`, `docs/obf-notes.md`,
  `src/.../cmd/CombatCommands.kt`, `CHANGELOG.md`.

## Verification (2026-11-18)

- `gradlew buildMod --rerun-tasks` passes for Covenant + LemonLib (composite). The harness build
  passes with `JAVA_HOME=../../jre`, since its wrapper is not pinned.
- The binary scan of the rebuilt jars is clean: 0 missing members, 0 unimplemented abstract
  methods.
- **Harness `smoke`: PASS.** Game `Starsector 0.98.5a-RC7`, 8 mods, 0 target errors.
- **Harness `combat`:** FAIL then PASS. The fail was "enemy took no damage": the enemy shields
  absorbed 30 s of fire, at about 2800 hard flux each. The same check failed once on 0.98a
  (2026-10-17 run), so it is AI-dependent, not a regression.
- **Harness `compat`** on fixture `0.1.1/save_gdsfgdfgdfs_…`: it crashed on load before the
  jitter repair and passed every case after it (load, state intact, campaign battle, save/reload,
  state intact after the round trip).

## Open items

- In-game feel check of the torpedo flare change and of the new salary and parts numbers.
- Third-party deps still declare `0.98a-RCx` and get a patch-level mismatch warning in the
  launcher. Update them when their authors ship.
- The harness `gradlew` is not pinned to `../../jre` the way Covenant and LemonLib are (see
  [build-jdk17-pin.md](build-jdk17-pin.md)).
- ~~`SlipspaceTransitionScript.showNoise` named the blocked `java.lang.reflect.Method`, so the noise
  overlay never played.~~ Fixed the same day, see
  [slipspace-noise-reflection-fix.md](slipspace-noise-reflection-fix.md).
- The starsector-knowledge skill is still built from 0.98a-RC8. Rebuild it with its
  `scripts/build_skill.py` when the developer asks.

# In-game test pass — 2026-10-10 sheet

One play session that covers the four open in-game checks:

- variant refit — [variant-refit.md](variant-refit.md);
- graphics move — [graphics-folder-move.md](graphics-folder-move.md);
- LunaLib settings — [lunalib-settings.md](lunalib-settings.md);
- release clean install — [release-packaging.md](release-packaging.md).

The session runs on the **release zip**, not the dev folder. That way the clean-install check comes
for free, and every other check also proves the packaged files are enough.

Write results next to each box (ok / what went wrong). When done, tell the assistant: it scans
`starsector.log` for errors and closes the TODO items.

## Static pre-check (done 2026-10-10, no game)

`Covenant Remnants.zip` was unzipped into a scratch folder. Every path it references was resolved
against the zip, then vanilla:

- 250 paths: 190 in the zip, 60 vanilla;
- 77 `sounds.json` entries and 77 `settings.json` sprites;
- the `mod_info` jar and plugin class, `version_files.csv` → `.version`, the shader modules.

**0 problems.** The excluded `noise.png` / `fire_fp_*` are not referenced. Script:
`zipcheck.py`, session scratchpad, not kept.

## 0. Setup

- [ ] Close the game (it locks `LemonLib.jar`; rebuilds fail while it runs).
- [ ] Run `package_mod.bat`. Check that `Covenant Remnants.zip` is fresh, with today's time.
- [ ] Move the dev folder `mods/Covenant Remnants` **out of** `mods/` (e.g. to the Desktop).
  Renaming is not enough, because the launcher scans every folder in `mods/`.
- [ ] Unzip `Covenant Remnants.zip` into `mods/`. That gives `mods/Covenant Remnants/` with the
  release files only.
- [ ] Launcher: enable Covenant Remnants, LemonLib, MagicLib, LazyLib, LunaLib and Console Commands.
  Start a **new game** (old saves throw unrelated errors).

## 1. Startup and menus

- [ ] The game starts with no crash.
- [ ] LunaLib Version Checker panel: Covenant Remnants and LemonLib are listed, not under
  "unsupported".
  - Update checks will fail until the repos are public, which is expected.
- [ ] Settings (F2): the Covenant Remnants page shows **Performance** (particle density, heat haze,
  shield hit distortion) and **Audio** (effect volume 0–1).

## 2. Refit (variant refit 1–2)

- [ ] Console: `CovenantGiveAll`.
- [ ] Open each Covenant hull in refit:
  - no red OP;
  - weapons sit in the slots listed in variant-refit.md;
  - hullmods are not greyed out (Slipspace Tuning on frigate1 / frigate3, Expanded Deck Crew on the
    carriers).
- [ ] destroyer1 (SDV) shows in the fleet screen.
- [ ] capital1: `WS0014` is a medium slot with a Plasma Cannon.

## 3. Combat (simulator or a fight)

Variant refit 2–6 and the graphics move.

- [ ] destroyer1 deploys with no crash.
- [ ] destroyer2: both Banshee wings launch from the hangar openings beside the spine. If they do
  not, note where they come from.
- [ ] CVS (supercap2): all 9 wings launch, and the ship **hangs back** like a carrier.
- [ ] CAS (supercap1): all 3 wings launch, and the ship **keeps fighting** (battle carrier).
- [ ] capital1: the Energy Projector fires from the nose (`WS0008`).
- [ ] AI ships fire torpedoes, the Pulse Lasers autofire, and the Beam Emitters get used.
- [ ] Graphics: these all render, with no white boxes or missing sprites:
  - the Pulse Laser, Repeater and Autocannon bolts;
  - all three torpedo sizes;
  - the Mortar and Bombardment shells and blasts;
  - the Plasma Cannon impact;
  - shield hits and the shield break;
  - the Energy Projector beam and impact;
  - the Beam Emitter;
  - the Fleet Slipspace Jump rupture (CAS);
  - the Slipspace Drive blink.

## 4. LunaLib settings, live (combat + F2 between fights)

After each save, `starsector.log` gets a new `Covenant settings: …` line, with no restart needed.

- [ ] Particle density **0.1**: the fleet jump and blink show visibly fewer particles. Set it back
  to 1.
- [ ] Heat haze **off**: the fleet jump has no screen ripple, everything else still draws, and the
  frame rate is better. Back on.
- [ ] Shield hit distortion **off**: shield hits show no refraction rings, but the plasma flare
  stays. Back on.
- [ ] Effect volume **0**: the shield pop, impact booms, torpedo hum and jump sounds go silent,
  while weapon fire still plays. **0.5**: quieter. Back to 1.

## 5. Campaign

- [ ] The campaign Slipspace Jump (granted by `CovenantGiveAll`) works: the Ark portals render
  (graphics move), and the pocket background shows.

## 6. Restore

- [ ] Close the game, delete the release `mods/Covenant Remnants/`, and move the dev folder back.
- [ ] Tell the assistant, which scans `starsector.log`.

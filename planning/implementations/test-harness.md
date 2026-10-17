# Implementation — in-game test harness (2026-10-17)

The developer asked for a harness that boots the actual game so the assistant can run tests. It
lives in its **own repo and mod**, `../StarsectorHarness` (mod id `combustiblelemon_harness`,
name "Starsector Harness"), and treats Covenant Remnants as its first *target*. This file records
what the harness does for this mod and the one change made here (the release script). The
harness's own docs: `../StarsectorHarness/README.md`, `docs/protocol.md`, `docs/obf-notes.md`.

## Developer rulings (2026-10-17)

| Topic | Decision |
| --- | --- |
| Where | Standalone repo, generic harness mod in `mods/StarsectorHarness`; Covenant-specific content as data under `targets/covenant/`, no compile dependency on this mod. |
| Control | JSON-lines TCP bridge on 127.0.0.1 inside the harness mod, every request handled on the game thread. |
| Entering combat | Programmatic first (reflection on un-obfuscated internals), WinDrive clicks as fallback. Simulation battles are fine ("if that's easier"). |
| Scope v1 | Combat + campaign load + save backwards-compatibility with four checks: load clean, mod state intact, save/reload round trip, enter combat from campaign; plus a real fleet encounter route (M4b). |
| Fixtures | One snapshot per release, **git-ignored, local only**. |
| Repo init | Assistant runs `git init` and commits in the harness repo (no branches). |

## What the harness does for Covenant Remnants

- **Boot:** `python -m harness run smoke --target covenant` launches `jre\bin\java.exe` with the
  arguments from `Starsector/vmparams`, per-run `paths.saves/mods/logs`, `-DlaunchDirect` to skip
  the launcher, and `-Dharness.port`. Title screen in about 15 s. The mod set is the dependency
  closure of `targets/covenant/target.json` (LemonLib, MagicLib, Ashlib, LazyLib, plus Console
  Commands and LunaLib when installed) built as a directory of junctions; the user's
  `mods/enabled_mods.json` and `saves/` are never written.
- **Campaign:** `run campaign --fixture <ver>/<save>` points the game's `continue` registry pref at
  a copied save, presses Continue through the title menu enum, waits for `campaign_ready`, and
  queries: sector, `has_system Slipspace`, player fleet (members, hullmods, s-mods, weapons,
  abilities, cargo, known hullmods), sector memory keys (`$covenant_remnants_tester_cache_*`),
  storage of `corvus_abandoned_station`.
- **Combat:** `run combat` starts a battle with the simulator recipe (`title.Object.L` mission
  definition + `CombatEngine.init`) from the title screen for each `targets/covenant/scenarios/*.json`,
  deploys both sides, turns autopilot on, runs N seconds, asserts (alive, enemy damaged, flux rose),
  checks the log window, ends combat, returns to the title.
- **Compat:** `run compat --fixture <ver>/<save>` = load → log clean → state vs `fixture.json.expected`
  → campaign sim battle with copies of the player's fleet → save-and-exit → Continue → checks again
  → no `org.combustiblelemon.harness` string in the round-tripped `campaign.xml`.
  `python -m harness capture --fixture ...` records the expected state.
- **Log oracle:** vanilla `ERROR` lines (missing vanilla hull specs, version-checker noise) are
  reported but only fail a case when they mention `org.combustiblelemon`, `covenant_remnants_`,
  `covenantremnants` or the mod id. Every mutating command writes `HARNESS-MARK` into the log so
  windows can be scanned per step.

## Files touched in this repo

- `scripts/release.ps1` — optional `-Fixture <save_dir>` parameter and `$HarnessRoot` /
  `$HarnessTarget` / `$HarnessModId` config. Pre-flight checks the save and the destination
  before any edit; after `packageMod` it runs `..\StarsectorHarness\scripts\snapshot-save.ps1`
  (copies `campaign.xml` + `descriptor.xml` to `fixtures\saves\<version>\<save_dir>\`, writes
  `fixture.json` with the mod versions from the descriptor). `-DryRun` only prints the command.
  A failed snapshot warns; the release itself continues.
- `planning/TODO.md` — harness entries. No changelog entry: the changelog is player-facing and
  dev tooling does not belong there (developer ruling 2026-10-17).

## Key decisions and findings

- **Input phase.** Actions that tear the current state down (Continue, start battle, save-and-exit)
  must run from `EveryFrameCombatPlugin.processInputPreCoreControls` or a `CampaignInputListener`,
  where a mouse click runs. Run from `advance()` they leave `CombatEngine.advanceInner` on a
  destroyed world (NPE, game stuck on its fatal dialog).
- **Mod classloader whitelist.** `java.io.*` is blocked except a short list (`PrintStream`,
  `BufferedReader`, `InputStreamReader`, ... but not `OutputStreamWriter`); see memory note
  `starsector-mod-classloader-blocks` and the harness `docs/obf-notes.md`.
- **Fixture seeded** from the developer's save `save_gdsfgdfgdfs_2352390989278051030` (made with
  Covenant **0.1.1**) under `fixtures/saves/0.1.1/` for the M1 bring-up: it loads cleanly under the
  current 0.1.2 build, Slipspace system present, tester-cache flags intact.
- Vanilla logs two `ERROR` lines at every boot (`flare`, `module_hightech_decor` hull specs); they
  are not ours and are reported, not failed.
- **Unattended runs.** Vanilla stops rendering when combat is paused and the window is unfocused
  (`idleWhileWindowNotVisible`); a screenshot during that state hung a run for 5.5 h and the game
  had to be closed by hand (developer, 2026-10-17). The harness mod now overrides that key, the
  runner bounds every capture, and a watchdog kills the game after 10 min per step / 40 min per
  run. Runs no longer need the window in the foreground.
- **Campaign battles** (the compat suite's "enter combat from campaign") use the refit screen's
  simulator recipe with copies of the save's fleet. Three game facts had to be handled: the
  simulator session token must be removed after returning or the next load NPEs; copied members
  need crew + stats before CR or they spawn with locked controls and a free retreat order; direct
  orders need command points. All in the harness `docs/obf-notes.md`.
- The seed fixture's fleet is the `_giveall` empty hulls from the console command, so its
  campaign battle proves deployment, CR, AI engagement and the return path, not weapon damage.
  Weapon behaviour is covered by the combat suite's scenarios with real `_std` variants.

## Results (2026-10-17)

| Suite | Result |
| --- | --- |
| smoke | PASS: title in ~15 s, bridge ticking, 8 mods, 0 target errors |
| campaign (0.1.1 fixture) | PASS: load 2.5 s, Slipspace system present, 18 members, tester-cache flags intact |
| combat (`frigates_vs_vanilla`) | PASS: 3 Covenant frigates vs Hammerhead + Enforcer, 30 s, enemy damaged, back to title |
| compat (0.1.1 fixture under 0.1.2 build) | PASS, 16/16: load clean, state intact, campaign battle (18 copies, no locked controls), save/reload, state intact again, no harness classes in the save |
| encounter (M4b) | PASS: pirate fleet spawned next to the player, ENGAGE → CONTINUE_INTO_BATTLE → deployment dialog → 25 s battle → LET_THEM_GO → LEAVE → back on the map, 0 target errors |

Every run closes the game itself and leaves the user's `saves/`, `mods/enabled_mods.json` and
registry prefs unchanged (report footer invariants, all runs today).

## Open items

- More combat scenarios: one `_std` variant per hull vs a vanilla peer (the suite takes any
  `targets/covenant/scenarios/*.json`); asserts are first-cut and tuned on one scenario so far.
- A fixture made with a real, armed fleet (the seed fleet is `_giveall` empty hulls), and the
  first release snapshot via `release.bat vX.Y.Z -Fixture <save_dir>` followed by
  `python -m harness capture`.
- A LemonLib target (`targets/lemonlib`) when LemonLib gets testable behaviour.
- Fixtures are git-ignored: back them up by hand. The harness repo has no remote yet.
- Covenant repo changes from this work (release script, docs, TODO, CHANGELOG) are uncommitted.

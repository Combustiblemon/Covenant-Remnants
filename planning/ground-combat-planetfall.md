# Ground combat via Planetfall

**Status: parked 2026-10-08.** Only the Planetfall skeleton exists. Nothing in Covenant Remnants
depends on it yet.

## What it is

Covenant Remnants gets ground combat (missions, skirmishes, planetside quest steps) from a separate
library mod, **Planetfall**, at `../../Planetfall` (`Starsector\mods\Planetfall`). Planetfall runs a
fork of the OpenRA RTS engine as a child process and shows the battle inside a Starsector dialog
panel, not in a second window. Starsector mods start a battle through a Kotlin API and get a result
back. Ground content (maps, units, missions) is built with the standard OpenRA Mod SDK.

It is a library on purpose: other mods can use it, and Covenant Remnants only supplies Halo content
on top. That content is not planned in detail yet.

## Where the plan lives

All design, status and decisions are in Planetfall's own docs:

- `../../Planetfall/CLAUDE.md`: what Planetfall is and its fixed decisions.
- `../../Planetfall/planning/PLAN.md`: milestones, status, and the "Resume here" list.
- `../../Planetfall/planning/planetfall-bridge-plan.md`: the approved end-to-end design.
- `../../Planetfall/planning/engine-fork-plan.md` and `openra-sdk-plan.md`: plans for the two
  OpenRA-side repos, which do not exist yet.

## What Covenant Remnants does, and when

Nothing until Planetfall milestone M5. Then:

- Add `combustiblelemon_planetfall` as a dependency in `mod_info.json`.
- Add one test ground-battle option on a planet dialog.
- Record it in `planning/implementations/` and the TODO list.

Constraints already known:

- **Planetfall can be unavailable** (no .NET 10 runtime installed, engine folder missing). Every
  Covenant Remnants use must keep a non-ground fallback and check `Planetfall.isAvailable()` first.
- Acquisition stays quest-gated and there is still no new faction (see [../CLAUDE.md](../CLAUDE.md)).
  How ground battles fit into quests is a design question for the developer, not decided here.

## Open questions for Covenant Remnants

- Which quests, if any, get ground steps.
- Halo ground roster (infantry, vehicles) and how it maps to OpenRA actors.

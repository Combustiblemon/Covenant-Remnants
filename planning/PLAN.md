# Roadmap & status

Living checklist and open questions. Project context: [CLAUDE.md](../CLAUDE.md). Conventions:
[DOCUMENTATION.md](DOCUMENTATION.md). Architecture: [ARCHITECTURE.md](ARCHITECTURE.md).

Status as of version `0.1.0` (game `0.98a`). Keep this file current — it is the place for
in-progress work and unresolved questions, not settled decisions (those live in [CLAUDE.md](../CLAUDE.md)).

## Shipped

- **Custom energy shield system** — hullmod + stats + AI + shader render + per-ship `shield_data.csv`.
- **Slipspace travel** — jump ability, portal entity, generated slipspace system, drive + shader,
  regen/debug console commands.
- **Plasma weapon family** — artillery, beam, cannon, lance, missile, repeater, barrage;
  energy projector effect; plasma projectiles.
- **Ships** — 28 `.ship` hulls across frigate → super-capital, `shield type NONE`
  (named: CCS, CAS, ORS, Seraph, Phantom, Banshee, + class-generic hulls).
- **Fighters** — via `wing*` hulls + variants + `wing_data.csv`.
- **Hullmods** — nanolaminate armor, Huragok crew, Covenant systems, hullmod manager.

## In progress / open

- **Quests & narrative content — not started.** No `data/campaign/rules.csv`, missions, intel, or
  bar events yet. This is the primary acquisition path (ships/weapons are quest-gated only — see
  [CLAUDE.md](../CLAUDE.md)) and the next major area of work.
- **ID migration `covenantremnants_` → `covenant_remnants_`.** Two conventions coexist in the repo.
  Canonical is `covenant_remnants_`; legacy no-separator ids need migrating (ships, weapons,
  variants). Do this carefully — ids are referenced across `.ship`/`.variant`/CSV/Kotlin.
- **Dependency declaration gap.** `mod_info.json` declares only MagicLib + LazyLib, but the build
  uses LunaLib and Console Commands. **Open question:** are LunaLib / Console Commands required
  runtime deps (declare them) or optional (guard usage)? Decide, then reconcile `mod_info.json`.
- **Balance TODOs** (from [balance.txt](../balance.txt)):
  - Make the energy projector a **ship system**; nerf it for anything other than the CAS.
  - Add a hullmod that buffs the weapons, and nerf the weapons themselves to compensate.
  - Add 3 PD frigates — stinger proximity mine.

## Planned scope

- **Items** — Covenant items (per the mod's stated goals).
- **More quests** — expand the quest-gated content and acquisition arcs.
- **More ships & fighters** — fill out the roster; keep the long-range-elite-brawler identity and
  the swarm weakness (see [CLAUDE.md](../CLAUDE.md)).
- Ongoing polish of shaders and feel.

## Open questions

- LunaLib / Console Commands: required vs optional (see above).
- Final acquisition design for each quest — which ships/weapons unlock where.

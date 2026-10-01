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
- **Ships** — 19 `.ship` hulls (16 combat + 3 wing) across frigate → super-capital, `shield type NONE`
  (named: CCS, CAS, ORS, Seraph, Phantom, Banshee, + class-generic hulls).
- **Fighters** — via `wing*` hulls + variants + `wing_data.csv`.
- **Hullmods** — nanolaminate armor, Huragok crew, Covenant systems, hullmod manager.
- **ID migration `covenantremnants_` → `covenant_remnants_`** — done. No legacy ids remain in
  `data/` or `src/` (Kotlin package `covenantremnants` is unaffected).

## In progress / open

- **Quests & narrative content — not started.** No `data/campaign/rules.csv`, missions, intel, or
  bar events yet. This is the primary acquisition path (ships/weapons are quest-gated only — see
  [CLAUDE.md](../CLAUDE.md)) and the next major area of work.
- **Balance TODOs** (originally from the old root `balance.txt`, no longer in the repo):
  - Make the energy projector a **ship system**; nerf it for anything other than the CAS.
  - Add a hullmod that buffs the weapons, and nerf the weapons themselves to compensate.
  - Add 3 PD frigates — stinger proximity mine.

## Planned scope

- **Items** — Covenant items (per the mod's stated goals).
- **More quests** — expand the quest-gated content and acquisition arcs.
- **More ships & fighters** — fill out the roster; keep the long-range-elite-brawler identity and
  the swarm weakness (see [CLAUDE.md](../CLAUDE.md)).
- Ongoing polish of shaders and feel.
- **LunaLib settings** — in-game options to control the mod. LunaLib is optional, so each setting
  needs a default for when LunaLib is absent.

## Open questions

- Final acquisition design for each quest — which ships/weapons unlock where.

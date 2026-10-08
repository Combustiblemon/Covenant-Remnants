# Roadmap & status

Living checklist and open questions. Project context: [CLAUDE.md](../CLAUDE.md). Conventions:
[DOCUMENTATION.md](DOCUMENTATION.md). Architecture: [ARCHITECTURE.md](ARCHITECTURE.md).

Status as of version `0.1.0` (game `0.98a`). Keep this file current — it is the place for
in-progress work and unresolved questions, not settled decisions (those live in [CLAUDE.md](../CLAUDE.md)).

## Shipped

- **Custom energy shield system** — hullmod + stats + AI + shader render + per-ship `shield_data.csv`.
- **Fortress Shield (capital1)** — vanilla `fortressshield` now hardens the Covenant shield (×0.1
  damage). Regen is frozen while it's on, it only works with the shield up, a custom
  incoming-damage AI drives it, and a fortress shader layer shows it. See
  [implementations/fortress-shield.md](implementations/fortress-shield.md).
- **Slipspace Drive blink (frigate1 / frigate3 / destroyer2)** — Wolf-style short-range blink
  (engine teleporter, 500 su toward the cursor, 3 charges) with ODST-style hull ripple + portals.
  Placeholder portal until the ODST kit data is pulled. See
  [implementations/slipspace-drive-blink.md](implementations/slipspace-drive-blink.md).
- **Fleet Slipspace Jump (supercap1)** — the CAS's unique radius mass-jump: opens a rupture off
  the bow, lurches into it, and grabs everything around the portal (ships of any side, fighters,
  shots, hulks, asteroids) and jumps it together,
  with rupture damage to unshielded ships, a pull-home / strand / reinforce / hunt / regroup AI, a rupture remade from
  Halo: Reach's own Long Night of Solace slipspace bomb data, and live console tuning
  (`FleetJumpTune`). See [implementations/fleet-slipspace-jump.md](implementations/fleet-slipspace-jump.md).
  Awaiting in-game test and tuning.
- **Plasma weapon family** — artillery, beam, cannon, lance, missile, repeater, barrage;
  energy projector effect; plasma projectiles.
- **Ships** — 19 `.ship` hulls (16 combat + 3 wing) across frigate → super-capital. Combat hulls use `shield type PHASE` with the Covenant shield as
  their defense; wings use `NONE`.
  (named: CCS, CAS, ORS, Seraph, Phantom, Banshee, + class-generic hulls).
- **Fighters** — via `wing*` hulls + variants + `wing_data.csv`.
- **Hullmods** — nanolaminate armor, Huragok crew, Covenant systems, hullmod manager.
- **ID migration `covenantremnants_` → `covenant_remnants_`** — done. No legacy ids remain in
  `data/` or `src/` (Kotlin package `covenantremnants` is unaffected).

## In progress / open

- **Campaign Slipspace Jump** — built 2026-10-07 (map picker, charge + Reach rupture, custom
  transition, slipstream pocket path with push-back, exit dialog, edge arrival facing the origin,
  Tuning bonus, `CovenantGiveAll` grant). Awaiting the in-game checklist in
  [implementations/campaign-slipspace-jump.md](implementations/campaign-slipspace-jump.md).
  All numbers are placeholders. The granting quest is not built (quests below).
- **Ark slipspace portals** — built 2026-10-08 (Halo 3 The Ark exit look for the pocket portals
  and the destination arrival), then the visual pass the same day (lathed tube, procedural
  streaks, wall lightning, analytic rings, particles, pre-roll / afterglow / idle life; all
  tunables, in-game checklist pending). Details in
  [implementations/ark-slipspace-portals.md](implementations/ark-slipspace-portals.md) and
  [implementations/ark-portal-visuals-pass.md](implementations/ark-portal-visuals-pass.md).
- **Quests & narrative content — not started.** No `data/campaign/rules.csv`, missions, intel, or
  bar events yet. This is the primary acquisition path (ships/weapons are quest-gated only — see
  [CLAUDE.md](../CLAUDE.md)) and the next major area of work.
- **Balance TODOs** (originally from the old root `balance.txt`, no longer in the repo):
  - Make the energy projector a **ship system**; nerf it for anything other than the CAS.
  - ~~Add a hullmod that buffs the weapons, and nerf the weapons themselves to compensate.~~
    Scrapped for now (developer 2026-10-02).
  - Add 3 PD frigates — stinger proximity mine.

## Planned scope

- **Items** — Covenant items (per the mod's stated goals).
- **More quests** — expand the quest-gated content and acquisition arcs.
- **More ships & fighters** — fill out the roster; keep the long-range-elite-brawler identity and
  the swarm weakness (see [CLAUDE.md](../CLAUDE.md)).
- Ongoing polish of shaders and feel.
- **LunaLib settings** — in-game options to control the mod. LunaLib is optional, so each setting
  needs a default for when LunaLib is absent.
- **Ground combat** — via the separate Planetfall library mod (OpenRA engine in a child process,
  shown in a dialog panel). Parked 2026-10-08 after its skeleton. See
  [ground-combat-planetfall.md](ground-combat-planetfall.md).

## Open questions

- Final acquisition design for each quest — which ships/weapons unlock where.

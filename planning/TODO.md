# TODO — what's left to do

Actionable checklist of outstanding work. Settled decisions live in [../CLAUDE.md](../CLAUDE.md);
roadmap/status in [PLAN.md](PLAN.md); per-feature details in [implementations/](implementations/).
Keep this file current — tick items off and add new ones as work lands.

## Art / geometry (placeholders in place)

- [ ] **Ship bounds + weapon-slot positions** — currently outline-traced / placeholder; refine each
      hull in the in-game ship editor. See [implementations/ships.md](implementations/ships.md).
- [ ] **Bespoke weapon art** — the 13 weapons use vanilla-analog sprites recolored purple; make real
      Covenant turret/hardpoint sprites. See [implementations/weapons-arsenal.md](implementations/weapons-arsenal.md).
- [ ] **Projectile / missile recolor** — bolts and torpedoes reference vanilla `projectileSpecId`,
      so they render in vanilla colours; author Covenant `.proj` (purple) when art is ready.
- [ ] **Energy Projector art** — bespoke turret + beam (currently vanilla Tachyon Lance sprites).

## Code stubs to finish

- [ ] **Fortress-shield wiring (capital1)** — `fortressshield` is assigned but inert vs the custom
      shield. Wire `CovenantShieldsStats` to detect `ship.system.id == "fortressshield" && isActive`
      and, while active, drop `shieldDamageMult` (~0.3→0.05) + halt shield decay. TODO comment left
      in `src/.../systems/CovenantShieldsStats.kt`.
- [ ] **Mass slipspace jump (supercap1)** — currently the placeholder `slipspacedrive`; build the
      radius mass-jump (grab ships/fighters/projectiles/missiles in a radius, jump them together).
      Full spec is in the plan-file git history; model on `SlipspaceDrive.kt` + `SlipspaceDriveRenderPlugin`.

## Balance

- [ ] **Tune all numbers** — every ship, weapon, shield, and system value is a vanilla-anchored
      placeholder. Pass over stats once content is playable in-sim.
- [ ] Decide Suppressor Beam damage type — ENERGY (current) vs KINETIC (harder anti-shield).

## From PLAN.md (still open)

- [ ] Quests & narrative content (primary acquisition path; not started).
- [ ] `mod_info.json` dependency reconciliation (LunaLib / Console Commands: required vs optional).
- [ ] Items (Covenant items).

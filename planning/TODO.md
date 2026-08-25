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
- [ ] **Energy Projector art** — bespoke turret sprite (currently vanilla Tachyon Lance). The beam
      itself, including the pierce segment, now renders from the `.wpn` beam spec, so it inherits
      whatever beam colours/texture that spec defines.
- [ ] **Verify exit scorch in-game** — REAL vanilla scorch decals at the pierce exit point via
      damage-then-restore (zero net gameplay change; fades out over ~2.5s starting ~3s after the
      last burn) are built but not yet verified in the simulator. Check: no hull/flux/armor change,
      no floaty numbers, no weapon/engine disable flicker, smooth fade (no pop), ember dies at fade
      start. See [implementations/energy-projector.md](implementations/energy-projector.md).

## Code stubs to finish

- [ ] **Fortress-shield wiring (capital1)** — `fortressshield` is assigned but inert vs the custom
      shield. Wire `CovenantShieldsStats` to detect `ship.system.id == "fortressshield" && isActive`
      and, while active, drop `shieldDamageMult` (~0.3→0.05) + halt shield decay. TODO comment left
      in `src/.../systems/CovenantShieldsStats.kt`.
- [ ] **Mass slipspace jump (supercap1)** — currently the placeholder `slipspacedrive`; build the
      radius mass-jump (grab ships/fighters/projectiles/missiles in a radius, jump them together).
      Full spec is in the plan-file git history; model on `SlipspaceDrive.kt` + `SlipspaceDriveRenderPlugin`.
- [ ] **Verify shield stats-script sharing** — deploy 2+ Covenant ships in one battle and check
      whether they share one shield pool (`CovenantShieldsStats` keeps per-ship state in instance
      fields; Starsector may share one script instance per system spec). If confirmed, refactor
      to per-ship state. See [implementations/shield-dhull-lookup-fix.md](implementations/shield-dhull-lookup-fix.md).

## Hullmods

- [x] **Hullmod pass** — fixed Covenant Systems' inverted penalty (now standalone, Huragok offsets),
      fixed the maxCR units bug (was +2000% CR), added OP costs to optional mods, baked Covenant
      Systems + Nanolaminate into all combat hulls as built-in, added **Slipspace Tuning**,
      **Plasma Capacitors**, and the fluff/marker **Slipspace Drive** hullmod (no-op, built into all
      combat hulls, for campaign scripts). See [implementations/hullmod-pass.md](implementations/hullmod-pass.md).
      Not yet verified in-game.
- [ ] **Tune hullmod numbers** — OP costs and all new-mod values are vanilla-anchored placeholders.
- [ ] **Weapon-buff hullmod** (from PLAN) — still unbuilt: buff plasma/energy weapons, nerf the weapons
      themselves to compensate. Deferred from this pass.

## Balance

- [ ] **Tune all numbers** — every ship, weapon, shield, and system value is a vanilla-anchored
      placeholder. Pass over stats once content is playable in-sim.
- [x] **Supercap mobility** — CAS + CVS base speed 15→30, accel 10→15, decel 8→12 (effective 36
      at-flux with Nanolaminate, just above Invictus). See
      [implementations/supercap-speed-tune.md](implementations/supercap-speed-tune.md). Not yet
      verified in-game.
- [x] **Flux + shield-regen tune** — max flux ×0.8 on all 16 Covenant hulls (shield makes no
      flux); supercap1 shield 45000→20000; regen halved while >1% max-shield damage taken in a
      trailing 3s window (replaced dead/buggy `shouldRegen()`). See
      [implementations/shield-flux-tune.md](implementations/shield-flux-tune.md). Not yet
      verified in-game.
- [ ] **UI indicator for slowed shield regen** — optional follow-up; mechanic currently invisible
      to the player.
- [ ] Decide Suppressor Beam damage type — ENERGY (current) vs KINETIC (harder anti-shield).

## Dev tooling

- [x] **`CovenantGiveAll` console command** — grants all Covenant ships/weapons/fighters/hullmods
      (dynamic prefix scan). See [implementations/console-give-all-command.md](implementations/console-give-all-command.md).
      Not yet verified in-game.

## From PLAN.md (still open)

- [ ] Quests & narrative content (primary acquisition path; not started).
- [ ] `mod_info.json` dependency reconciliation (LunaLib / Console Commands: required vs optional).
- [ ] Items (Covenant items).

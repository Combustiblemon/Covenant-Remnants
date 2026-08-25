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
- [x] **Vanilla-style incompatibility registry (full)** — replaced MagicLib's post-install removal
      with vanilla refit UX for the whole mod: 14 vanilla hullmods (ITU/DTC, Heavy Armor, Solar
      Shielding, 8 shield mods, 2 phase mods) grey out on Covenant ships via runtime effect wrap
      (name-agnostic reflection, reinstall-on-WARN each game load). NanolaminateArmor + HuragokCrew
      migrated off MagicIncompatibleHullmods. Covenant Shield hullmod now built-in on all hulls;
      its desc rewritten with per-ship shield numbers.
      See [implementations/vanilla-hullmod-incompatibility.md](implementations/vanilla-hullmod-incompatibility.md).
      Not yet verified in-game.
- [ ] **frigate3 builtInMods** — `covenant_remnants_frigate3.ship` lacks
      `covenant_remnants_slipspace_drive` unlike every other combat hull; probable oversight, ask.
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

## Rendering / shaders

- [x] **Shield shader composition refactor** — split the monolithic shield shader into per-layer
      GLSL modules + Kotlin `ShieldLayer` classes (fill/border/sweep/ripple/honeycomb/debug),
      assembled into one program at init; dropped all dead uniforms incl. the no-op pop-wave
      effect. See [implementations/shield-shader-composition.md](implementations/shield-shader-composition.md).
      Verified in-game 2026-08-25.
- [x] **Delete old `shieldShader.frag`/`.vert`** — done after in-game sign-off.
- [x] **Shield shader debug tooling** — solo/subset layer rendering (neutral GLSL stubs +
      console command) and module-tagged GLSL compile errors (driver log line numbers mapped back
      to module file + local line, assembled source dumped to log).
      See [implementations/shield-shader-composition.md](implementations/shield-shader-composition.md).
      Not yet verified in-game.
- [x] **Generic shader composition system** — extracted the composition engine
      (`ShaderLayer`, `ComposedShaderProgram`, `CompositionDebug`); shield is first consumer;
      console command generalized to `ShaderLayers <system> [...]`. See
      [implementations/shader-composition-generic.md](implementations/shader-composition-generic.md).
      Not yet verified in-game.
- [x] **LemonLib library mod** — composition system + render core + generic geometry/ship utils
      extracted to the standalone `LemonLib` utility mod (`../../LemonLib/`); Covenant now depends
      on it (mod_info + gradle). Usage docs at `LemonLib/docs/shader-composition/`. See
      [implementations/lemonlib-extraction.md](implementations/lemonlib-extraction.md).
      Not yet verified in-game. Developer: `git init` the LemonLib repo when ready.
- [ ] **Decide fate of remaining monolith shaders** — MinimalShader / PolygonShader /
      SlipspacePortalEntityShader: migrate to the composition system or discard, per shader.
- [x] **ReflectionUtils → LemonLib** — moved whole file to
      `org.combustiblelemon.lemonlib.reflection.ReflectionUtils` (public), together with the
      generic hullmod-incompatibility system (`lemonlib.hullmods`: `HullmodBlocker`, wrapper,
      stateless `HullmodIncompatibilityInstaller.install/verifyAndReinstall(registry)`); mod keeps
      only `hullmods/CovenantIncompatibilities.kt` (registry). Docs:
      `LemonLib/docs/hullmod-incompatibility/README.md`. Not yet verified in-game.
- [ ] **Evaluate more LemonLib candidates** — `CubicCurve2D.kt`, `extensions/`
      (`Data.kt` likely stays — Covenant shield data).
- [ ] **Ripple zoom consistency** (pre-existing TODO) — ripple speed/radius/width inconsistent
      across zoom levels.

## Dev tooling

- [x] **`CovenantGiveAll` console command** — grants all Covenant ships/weapons/fighters/hullmods
      (dynamic prefix scan). See [implementations/console-give-all-command.md](implementations/console-give-all-command.md).
      Not yet verified in-game.

## From PLAN.md (still open)

- [ ] Quests & narrative content (primary acquisition path; not started).
- [ ] `mod_info.json` dependency reconciliation (LunaLib / Console Commands: required vs optional).
- [ ] Items (Covenant items).

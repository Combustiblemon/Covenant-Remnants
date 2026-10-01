# TODO — what's left to do

Actionable checklist of outstanding work. Settled decisions live in [../CLAUDE.md](../CLAUDE.md);
roadmap/status in [PLAN.md](PLAN.md); per-feature details in [implementations/](implementations/).
Keep this file current — tick items off and add new ones as work lands.

## Art / geometry (placeholders in place)

- [ ] **Ship bounds + weapon-slot positions** — currently outline-traced / placeholder; refine each
      hull in the in-game ship editor. See [implementations/ships.md](implementations/ships.md).
      In progress (developer): slots sit off the hull in-game (2026-10-01 test). The editor pass
      dropped Covenant Shield from `builtInMods` (restored) and removed slots on cruiser1 /
      destroyer2 (incl. both launch bays) / supercap1 — see
      [implementations/in-game-test-pass-2026-10-01.md](implementations/in-game-test-pass-2026-10-01.md).
      Re-check `builtInMods` after every editor save.
- [ ] **Bespoke weapon art** — the 13 weapons use vanilla-analog sprites recolored purple; make real
      Covenant turret/hardpoint sprites. See [implementations/weapons-arsenal.md](implementations/weapons-arsenal.md).
- [ ] **Projectile / missile recolor** — bolts and torpedoes reference vanilla `projectileSpecId`,
      so they render in vanilla colours; author Covenant `.proj` (purple) when art is ready.
- [ ] **Energy Projector art** — bespoke turret sprite (currently vanilla Tachyon Lance). The beam
      itself, including the pierce segment, now renders from the `.wpn` beam spec, so it inherits
      whatever beam colours/texture that spec defines.
- [x] **Verify exit scorch in-game** — verified in-game 2026-10-01. See
      [implementations/energy-projector.md](implementations/energy-projector.md).

## Code stubs to finish

- [ ] **Fortress-shield wiring (capital1)** — `fortressshield` is assigned but inert vs the custom
      shield. Wire `CovenantShieldsStats` to detect `ship.system.id == "fortressshield" && isActive`
      and, while active, drop `shieldDamageMult` (~0.3→0.05) + halt shield decay. TODO comment left
      in `src/.../systems/CovenantShieldsStats.kt`.
- [ ] **Mass slipspace jump (supercap1)** — currently the placeholder `slipspacedrive`; build the
      radius mass-jump (grab ships/fighters/projectiles/missiles in a radius, jump them together).
      Full spec is in the plan-file git history; model on `SlipspaceDrive.kt` + `SlipspaceDriveRenderPlugin`.
- [x] **Verify shield stats-script sharing** — verified 2026-10-01: with 2+ Covenant ships only the
      hit ship's shield drops; state is per-ship, no refactor needed.

## Hullmods

- [x] **Hullmod pass** — fixed Covenant Systems' inverted penalty (now standalone, Huragok offsets),
      fixed the maxCR units bug (was +2000% CR), added OP costs to optional mods, baked Covenant
      Systems + Nanolaminate into all combat hulls as built-in, added **Slipspace Tuning**,
      **Plasma Capacitors**, and the fluff/marker **Slipspace Drive** hullmod (no-op, built into all
      combat hulls, for campaign scripts). See [implementations/hullmod-pass.md](implementations/hullmod-pass.md).
      Verified in-game 2026-10-01 (CR/Huragok, OP costs).
- [x] **Vanilla-style incompatibility registry (full)** — replaced MagicLib's post-install removal
      with vanilla refit UX for the whole mod: 14 vanilla hullmods (ITU/DTC, Heavy Armor, Solar
      Shielding, 8 shield mods, 2 phase mods) grey out on Covenant ships via runtime effect wrap
      (name-agnostic reflection, reinstall-on-WARN each game load). NanolaminateArmor + HuragokCrew
      migrated off MagicIncompatibleHullmods. Covenant Shield hullmod now built-in on all hulls;
      its desc rewritten with per-ship shield numbers.
      See [implementations/vanilla-hullmod-incompatibility.md](implementations/vanilla-hullmod-incompatibility.md).
      Verified in-game 2026-10-01 (grey-outs + reasons, vanilla ships unaffected, codex, save/load).
- [x] **Covenant Shield built-in restored** — ship-editor pass had dropped it from all 16 combat
      hulls; re-added. Verified in refit 2026-10-01 (built-in + new description). See
      [implementations/in-game-test-pass-2026-10-01.md](implementations/in-game-test-pass-2026-10-01.md).
- [x] **Slipspace Tuning eligibility** — now any ship with the Slipspace Drive hullmod; combat
      bonuses only on drive-system ships. Verified in-game 2026-10-01.
- [ ] **Slipspace Tuning campaign bonus** — on ships without the drive system the mod has no effect
      yet; give it a bonus to the planned campaign slipspace-jump ability when that is built.
- [x] **frigate3 builtInMods** — `covenant_remnants_slipspace_drive` now present on
      `covenant_remnants_frigate3.ship` (added in the ship-editor bounds pass); all 16 combat hulls match.
- [ ] **Tune hullmod numbers** — OP costs and all new-mod values are vanilla-anchored placeholders.
- [ ] **Weapon-buff hullmod** (from PLAN) — still unbuilt: buff plasma/energy weapons, nerf the weapons
      themselves to compensate. Deferred from this pass.

## Balance

- [ ] **Tune all numbers** — every ship, weapon, shield, and system value is a vanilla-anchored
      placeholder. Pass over stats once content is playable in-sim.
- [x] **Supercap mobility** — CAS + CVS base speed 15→30, accel 10→15, decel 8→12 (effective 36
      at-flux with Nanolaminate, just above Invictus). See
      [implementations/supercap-speed-tune.md](implementations/supercap-speed-tune.md). In-game
      2026-10-01: 40 / 100 — consistent with 36 / 96 plus an external +10% (skill/nav).
- [x] **Flux + shield-regen tune** — max flux ×0.8 on all 16 Covenant hulls (shield makes no
      flux); supercap1 shield 45000→20000; regen halved while >1% max-shield damage taken in a
      trailing 3s window (replaced dead/buggy `shouldRegen()`). See
      [implementations/shield-flux-tune.md](implementations/shield-flux-tune.md). Shield look
      verified 2026-10-01. Regen rule replaced 2026-10-01 (didn't visibly slow under fire): any hit
      pauses regen for the hull's `shield regen delay`, then 50% for 3 s, then full. Verified
      in-game 2026-10-01. See
      [implementations/in-game-test-pass-2026-10-01.md](implementations/in-game-test-pass-2026-10-01.md).
- [ ] **UI indicator for paused/slowed shield regen** — optional follow-up; the pause shows in the
      HP bar, but nothing tells the player which phase (paused / 50% / full) is active.
- [x] Decide Suppressor Beam damage type — **ENERGY** (developer ruling 2026-10-01; no change).

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
      2026-10-01: `ShaderLayers shield border` failed (`undefined variable "debugMode"` — base
      footer read a debug-layer uniform); fixed and verified in-game 2026-10-01.
- [x] **Generic shader composition system** — extracted the composition engine
      (`ShaderLayer`, `ComposedShaderProgram`, `CompositionDebug`); shield is first consumer;
      console command generalized to `ShaderLayers <system> [...]`. See
      [implementations/shader-composition-generic.md](implementations/shader-composition-generic.md).
      Verified in-game 2026-10-01 (listing, shield renders, solo/subset + `all`).
- [x] **LemonLib library mod** — composition system + render core + generic geometry/ship utils
      extracted to the standalone `LemonLib` utility mod (`../../LemonLib/`); Covenant now depends
      on it (mod_info + gradle). Usage docs at `LemonLib/docs/shader-composition/`. See
      [implementations/lemonlib-extraction.md](implementations/lemonlib-extraction.md).
      Verified in-game 2026-10-01 (runs, hard dependency enforced by launcher). Developer:
      `git init` the LemonLib repo when ready.
- [ ] **Decide fate of remaining monolith shaders** — MinimalShader / PolygonShader /
      SlipspacePortalEntityShader: migrate to the composition system or discard, per shader.
- [x] **ReflectionUtils → LemonLib** — moved whole file to
      `org.combustiblelemon.lemonlib.reflection.ReflectionUtils` (public), together with the
      generic hullmod-incompatibility system (`lemonlib.hullmods`: `HullmodBlocker`, wrapper,
      stateless `HullmodIncompatibilityInstaller.install/verifyAndReinstall(registry)`); mod keeps
      only `hullmods/CovenantIncompatibilities.kt` (registry). Docs:
      `LemonLib/docs/hullmod-incompatibility/README.md`. Verified in-game 2026-10-01 (via the
      incompatibility registry).
- [ ] **Evaluate more LemonLib candidates** — `CubicCurve2D.kt`, `extensions/`
      (`Data.kt` likely stays — Covenant shield data).
- [ ] **Ripple zoom consistency** (pre-existing TODO) — ripple speed/radius/width inconsistent
      across zoom levels.

## Dev tooling

- [x] **`CovenantGiveAll` console command** — grants all Covenant ships/weapons/fighters/hullmods
      (dynamic prefix scan). See [implementations/console-give-all-command.md](implementations/console-give-all-command.md).
      Verified in-game 2026-10-01.
- [x] **IntelliJ legacy files** — deleted `covenant-remnants.iml`, `.idea/libraries/*.xml`,
      `.idea/artifacts/Create__jar.xml` (pre-Gradle leftovers) 2026-10-01. Red-underline fix =
      Gradle → Reload All Gradle Projects.

## From PLAN.md (still open)

- [ ] Quests & narrative content (primary acquisition path; not started).
- [x] `mod_info.json` dependency reconciliation — LunaLib / Console Commands are **optional**
      (developer ruling 2026-10-01); `mod_info.json` already correct (declares only LemonLib,
      MagicLib, LazyLib). Removed a stale `ShaderLayers` row from `data/console/commands.csv` that
      pointed at a class moved to LemonLib.
- [x] **LunaLib settings were dead config** — `data/config/LunaSettings.csv` (25 old debug shield
      settings no code read) deleted 2026-10-01 per developer.
- [ ] **LunaLib settings (future)** — add real LunaLib settings to control mod options. LunaLib
      stays optional: every setting needs a built-in default used when LunaLib is absent.
- [ ] Items (Covenant items).

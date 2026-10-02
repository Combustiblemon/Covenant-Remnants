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

- [x] **Fortress-shield wiring (capital1)** — vanilla `fortressshield` now hardens the Covenant
      shield. The shield honours `shieldDamageTakenMult` (×0.1 at full). Regen is frozen while it's
      active and comes back at full rate after. It only works with the shield up, a custom
      incoming-damage AI drives it, and a magenta tint + new `fortress` shader layer show it. Also
      fixed 3 shield-damage bugs (sticky converter flag, beams ~10× too strong vs shield, breaking hit
      passing 100%). See [implementations/fortress-shield.md](implementations/fortress-shield.md).
- [x] **Fortress Shield in-game test** — developer: working in-game (2026-10-01). See
      [implementations/fortress-shield.md](implementations/fortress-shield.md).
- [x] **Fortress visual follow-up re-check** — breathing stop, hex overlap and white lattice all
      verified in-game (developer 2026-10-01). Fortress Shield signed off.
- [ ] **Fortress Shield tuning** — AI thresholds, tint and layer constants are placeholders
      (`systems/FortressShield.kt`, `utils/render/shield/FortressLayer.kt`). The vanilla flux drain is
      free on this hull (no time limit); revisit if Fortress proves too strong.
- [x] **Per-type shield-damage stats** — decided: not honoured. The Covenant shield takes the same
      damage from all sources: no damage-type multipliers, no per-type `*ShieldDamageTakenMult`.
      Only the general `shieldDamageTakenMult` applies (developer ruling 2026-10-01, "for now").
- [ ] **UI cue for frozen regen under Fortress** — optional; nothing tells the player that regen
      is frozen.
- [x] **Mass slipspace jump (supercap1)** — built as **Fleet Slipspace Jump**
      (`covenant_remnants_fleet_slipspace_jump`), the CAS's unique system: grabs everything in a
      1000 su radius (ships of any side, fighters, shots, hulks, asteroids) and jumps it together,
      min range 2000, 60 s cooldown, 10% max-hull rupture damage to unshielded ships, pull-home /
      strand / regroup AI, rupture remade from Halo: Reach data, `FleetJumpTune` live tuning. Designed with the
      developer 2026-10-01. See
      [implementations/fleet-slipspace-jump.md](implementations/fleet-slipspace-jump.md).
- [x] **Fleet Slipspace Jump visual-only mode — turn off** — the developer set `VISUAL_ONLY`'s
      default to 0 on 2026-10-01 (full jump); the mode stays as a dev toggle.
- [ ] **Fleet Slipspace Jump activation flow in-game** — new 2026-10-01: system key primes (aiming
      circles at the cursor), left click confirms, right click cancels, works while paused
      (`plugins/FleetSlipspaceJumpTargetingPlugin.kt`). Check item 1 of the checklist in
      [implementations/fleet-slipspace-jump.md](implementations/fleet-slipspace-jump.md).
- [ ] **Fleet Slipspace Jump in-game test** — developer: run the checklist in
      [implementations/fleet-slipspace-jump.md](implementations/fleet-slipspace-jump.md) (mechanics,
      damage, abort paths, AI, visuals).
- [ ] **Fleet Slipspace Jump tuning** — every number is a placeholder (radius, range, costs, AI
      thresholds, visual timings/colours). Tune live with `FleetJumpTune`, then bake the values into
      `systems/FleetSlipspaceJump.kt` / the layer classes.
- [ ] **Fleet Slipspace Jump — exit check vs. stations** — the AI never anchors on friendly
      stations and the strength model ignores them; decide whether a friendly station should count
      as a pull-home cluster.
- [ ] **Slipspace Drive AI** — the drive's `.system` has `aiType: NONE`, so AI-piloted frigate1,
      frigate3 and destroyer2 never jump. Decide whether they should. (supercap1 no longer carries
      the drive.)
- [ ] **Slipspace Drive rupture damage** — the drive renderer's "apply damage to non-shielded ships
      in radius" TODO is still open; the Fleet Jump's rupture damage
      (`FleetSlipspaceJumpSession.applyRuptureDamage`) is a ready model if the drive should get it too.
- [x] **Slipspace Drive portal after the first battle** — `SlipspaceDrive` now drops its render
      plugin + phase flags whenever the combat engine changes (same pattern as
      `CovenantShieldsStats`). 2026-10-02. In-game check still pending (second battle, same
      session). See [implementations/cleanup-batch-2026-10-02.md](implementations/cleanup-batch-2026-10-02.md).
- [ ] **Drive portal art** — the three portal sprites (`portal_outer_loop`, `portal_inner_loop`,
      `portal_noise_long`) were deleted in commit `9b5ecc1` and unregistered in `a22e7d8`; until
      2026-10-02 the drive **crashed the game on activation** (`getSprite` throws on an unknown key).
      Now guarded: untextured placeholder rings/disc + one log warn. Developer will redo the drive
      visuals; old textures recoverable from `9b5ecc1^` at
      `graphics/covenant_remnants/fx/slipspace/{slipspace_portal_ring_outer,slipspace_portal_ring_inner,noiseTexture}.png`.
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
      HP bar, but nothing tells the player which phase (paused / 50% / full) is active. The
      recharge wave (2026-10-02) now marks the moment regen resumes.
- [x] Decide Suppressor Beam damage type — **ENERGY** (developer ruling 2026-10-01; no change).

## Rendering / shaders

- [x] **Reach shield look — in-game test** (signed off 2026-10-02 after three rounds): hits flare blue at the
      impact and fade in ~0.35 s, bigger when HP is low; low HP = faint always-on plasma + rim glow,
      no tint; flares stay on the hull while moving/turning, same at every zoom; break = flash,
      collapse flare fading in 0.3 s with a slight expansion, EMP arcs, ring + sparks, pop sound, no
      2 s wipe afterwards; re-raise after the cooldown → wave once the sweep completes; hit → pause
      end → one wave (none at full HP, none while rapid hits keep the pause alive); Fortress end →
      wave; Fortress lattice visible with hits on top; `ShaderLayers shield plasma` isolates,
      `ShaderLayers shield all` restores; nothing else mis-textured; no `OpenGL error` in the log.
      2026-10-02 round 1 (supercap1): hit strip + break OK; rim ring textured on request. Round 2:
      refraction rings OK (now warp the hexes too). Round 3: no hitch when shields first come up
      (one shared program compiled at game start), `ShaderLayers` still isolates layers. Developer:
      "both work great".
      See [implementations/shield-reach-impact.md](implementations/shield-reach-impact.md).
- [ ] **Reach shield look — tuning** — every default in `ShieldImpactEffect` (baseline 0.05/0.45,
      hit floor 0.25, hit colour floor 0.3, tile 200 su, scroll 0.2, flash 0.8, recharge 1.2 s) and
      the break particles in `CovenantShieldsRenderPlugin`; distortion ring constants in
      `DistortionLayer` (travel, duration, amplitude, width). Profile on the supercaps (the ring adds a
      bounding-box framebuffer copy per hit ship per frame).
- [x] **Shield shader composition refactor** — split the monolithic shield shader into per-layer
      GLSL modules + Kotlin `ShieldLayer` classes (fill/border/sweep/ripple/honeycomb/debug;
      ripple replaced by the plasma layer 2026-10-02),
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
- [x] **Decide fate of remaining monolith shaders** — `MinimalShader` + `PolygonShader` (and
      `data/shaders/minimalShader.*`) deleted 2026-10-02 (nothing instantiated them; developer
      ruling). `SlipspacePortalEntityShader` stays as-is (live, campaign portal entity) — migrate to
      the composition system only if the portal look gets reworked.
- [x] **Rupture remake from Reach game data** — developer decision 2026-10-01. The slipspace bomb
      (m45 `045lf_jorge`) was dumped from the official Reach editing kit (ManagedBlam, curves
      included) and rebuilt faithfully: `utils/render/rupture/` (`RuptureRenderer`, sphere + sprite
      programs, `RuptureEffect` timeline, generated `ReachCurves`), `data/shaders/rupture/`
      (`ruptureSphere`, `ruptureSprite`), 21 Reach bitmaps in `graphics/fx/slipspace_rupture/`.
      Replaces the composed procedural rupture shader (passes 1–2, removed). Extracted data and
      tools: `assets/reference/long-night-of-solace/reach-data/`. See
      [implementations/fleet-slipspace-jump.md](implementations/fleet-slipspace-jump.md).
- [ ] **Rupture in-game look** — first in-game look at the Reach remake: texture row order (flame
      direction), `EXPOSURE`, `SPRITE_SCALE` (charge core, twinkles), heat-haze strength, frame rate
      during the burst (~2000 sprites). Tune with `FleetJumpTune`.
- [x] **Unregistered noise sprites** — `shield_noise` went with `PolygonShader` (deleted);
      `portal_noise_long` + the two portal ring ids are now guarded (see "Drive portal art").
      `graphics/covenant_remnants/fx/shield/noise.png` is an orphan (never registered, no reader
      left) — kept for the developer's art pass; delete or reuse there.
- [x] **ReflectionUtils → LemonLib** — moved whole file to
      `org.combustiblelemon.lemonlib.reflection.ReflectionUtils` (public), together with the
      generic hullmod-incompatibility system (`lemonlib.hullmods`: `HullmodBlocker`, wrapper,
      stateless `HullmodIncompatibilityInstaller.install/verifyAndReinstall(registry)`); mod keeps
      only `hullmods/CovenantIncompatibilities.kt` (registry). Docs:
      `LemonLib/docs/hullmod-incompatibility/README.md`. Verified in-game 2026-10-01 (via the
      incompatibility registry).
- [x] **Evaluate more LemonLib candidates** — `CubicCurve2D` moved to
      `org.combustiblelemon.lemonlib.geometry` 2026-10-02. `extensions/CampaignFleetAPI.kt`
      (`isInSlipspace`) and `Data.kt` stay — Covenant-specific.
- [x] **Ripple zoom consistency** — obsolete: the ripple layer was replaced by the plasma layer
      2026-10-02; nothing ripple-related is left in the shield stack.

## Dev tooling

- [x] **`CovenantGiveAll` console command** — grants all Covenant ships/weapons/fighters/hullmods
      (dynamic prefix scan). See [implementations/console-give-all-command.md](implementations/console-give-all-command.md).
      Verified in-game 2026-10-01.
- [x] **IntelliJ legacy files** — deleted `covenant-remnants.iml`, `.idea/libraries/*.xml`,
      `.idea/artifacts/Create__jar.xml` (pre-Gradle leftovers) 2026-10-01. Red-underline fix =
      Gradle → Reload All Gradle Projects.
- [x] **`FleetJumpTune` console command** — lists / sets / resets every Fleet Slipspace Jump
      tunable live (CHARGE/FADE/ARRIVAL from the next battle). See
      [implementations/fleet-slipspace-jump.md](implementations/fleet-slipspace-jump.md).
- [ ] **LemonLib repo is dirty** — hullmod-incompatibility + reflection source committed
      (`65a5b80`). Still uncommitted as of 2026-10-02: `jars/LemonLib.jar`, new
      `src/.../lemonlib/geometry/CubicCurve2D.kt`, `README.md` row. Developer to commit.

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

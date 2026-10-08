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
- [ ] **Projectile / missile recolor** — the energy bolts still reference vanilla `projectileSpecId`,
      so they render in vanilla colours; author Covenant `.proj` (purple) when art is ready. The
      three plasma torpedoes are done (Reach look, 2026-10-02, below).
- [x] **Plasma torpedoes — Halo: Reach look** — built 2026-10-02: the m45 space Phantom torpedo
      (muzzle flash, light-volume body, star flare, lightning ribbon trail, fireball / plasma / smoke /
      sparks / lens-flare impact, Reach fire / hum / explosion sounds) on all three torpedoes, scaled
      by size; all three are now plain homing torpedoes with their own `.proj` (no Sabot / MIRV
      splits). See [implementations/plasma-torpedo-reach.md](implementations/plasma-torpedo-reach.md).
- [ ] **Plasma torpedoes — in-game check** — run the checklist in
      [implementations/plasma-torpedo-reach.md](implementations/plasma-torpedo-reach.md). Note: the
      heavy now does one 500 hit instead of 7 × 500 MIRV warheads, the light one 100 hit instead of
      5 × 200 kinetic darts (numbers unchanged; tuning later).
- [ ] **Weapons pass — one weapon at a time** (rulings 2026-10-08: visuals + sounds + stats, mount
      art deferred, mixed full/cheap route ruled per weapon). Order: Energy Projector ✔ → Plasma
      Lance → Plasma Beam Emitter → Suppressor Beam → Pulse Laser → Plasma Cannon → Repeater →
      Autocannon → Mortar → Bombardment → torpedo launcher polish. Per-weapon workflow in the plan
      (`~/.claude/plans/go-through-the-weapons-generic-ritchie.md`, step 1 = ruling round).
- [x] **Energy Projector — Halo: Reach glassing look** — built 2026-10-08: the CCS cruiser glassing
      beam (vanilla-textured engine beam at 1.25x Tachyon width, overlay: charge rings /
      flashes / flare, flicker half-dome, impact bubble / half-dome / blue tongues / cloud ring,
      Reach glassing + Scarab impact sounds), white core / purple fringe, 0.5 su per unit, Reach
      timing (2 s charge, 3 s + 1 s beam, 10 s cooldown, 750 dps, 2000 flux/s). See
      [implementations/energy-projector-reach.md](implementations/energy-projector-reach.md).
- [ ] **Energy Projector — in-game check** — run the checklist in
      [implementations/energy-projector-reach.md](implementations/energy-projector-reach.md); decide on `beamFireOnlyOnFullCharge` (faint engine beam
      and damage during the 2 s chargeup), tune the placeholder gains.
- [ ] **Energy Projector art** — bespoke turret sprite (currently vanilla Tachyon Lance); deferred
      by the weapons-pass ruling. The beam, including the pierce segment, renders from the `.wpn`
      beam spec.
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
- [x] **UI cue for frozen regen under Fortress** — built 2026-10-02: player status line "Shield
      regen — frozen by Fortress Shield". See
      [implementations/shield-regen-status.md](implementations/shield-regen-status.md). In-game
      check below.
- [x] **Mass slipspace jump (supercap1)** — built as **Fleet Slipspace Jump**
      (`covenant_remnants_fleet_slipspace_jump`), the CAS's unique system: grabs everything in a
      1000 su radius (ships of any side, fighters, shots, hulks, asteroids) and jumps it together,
      min range 2000, 60 s cooldown, 10% max-hull rupture damage to unshielded ships, pull-home /
      strand / reinforce / hunt / regroup AI, rupture remade from Halo: Reach data, `FleetJumpTune` live tuning. Designed with the
      developer 2026-10-01. See
      [implementations/fleet-slipspace-jump.md](implementations/fleet-slipspace-jump.md).
- [x] **Fleet Slipspace Jump visual-only mode — turn off** — the developer set `VISUAL_ONLY`'s
      default to 0 on 2026-10-01 (full jump); the mode stays as a dev toggle.
- [x] **Fleet Slipspace Jump activation flow in-game** — developer OK 2026-10-02. New 2026-10-01: system key primes (aiming
      circles at the cursor), left click confirms, right click cancels, works while paused
      (`plugins/FleetSlipspaceJumpTargetingPlugin.kt`). Check item 1 of the checklist in
      [implementations/fleet-slipspace-jump.md](implementations/fleet-slipspace-jump.md).
- [x] **Fleet Slipspace Jump — portal off the bow (in-game check)** — developer OK 2026-10-02. Built 2026-10-02: the rupture
      and grab circle sit one ship length off the bow (`PORTAL_AHEAD` 2), the CAS lurches into the
      portal during the fade and lands at the exit centre; entry ring in the aiming preview; AI
      counts the circle off its current bow. Check items 1–4 and 9 of the checklist in
      [implementations/fleet-slipspace-jump.md](implementations/fleet-slipspace-jump.md).
- [x] **Fleet Slipspace Jump — white charge core (in-game look)** — developer OK 2026-10-02 — built 2026-10-02, revised same
      day: white sphere at the centre of the charge-up (star-flare core size, ≈ 12–18 su radius, so
      the spokes reach past it; breathing, soft bloom, below
      saturation so the charge energy and the heat-haze ripples — now drawn after the spheres — show
      over it), swallowed by the opening glow sphere; same at the exit seed. Constants `CORE_*` in `RuptureEffect`
      (placeholders). Checklist items 2–3 in
      [implementations/fleet-slipspace-jump.md](implementations/fleet-slipspace-jump.md).
- [x] **Fleet Slipspace Jump — black body inside the white (in-game look)** — developer OK 2026-10-02 ("great for now") — 2026-10-02: the
      opening's black body now has Reach's angle fade (10°/25°), so the white ball grows first and
      the darkness fills in inside it, edge staying a white rim; and it now darkens the glow in HDR
      (multiply before tone mapping, like Reach), so the burst stays white before going lavender →
      blue → black; and the black now trails the white by `BLACK_DELAY` 0.3 s (same curves, so a
      white ball shows first and the dark grows out from the centre). Checklist item 3 in
      [implementations/fleet-slipspace-jump.md](implementations/fleet-slipspace-jump.md).
- [x] **Fleet Slipspace Jump in-game test** — developer 2026-10-02: "all good" (checklist in
      [implementations/fleet-slipspace-jump.md](implementations/fleet-slipspace-jump.md)).
- [ ] **Fleet Slipspace Jump — warp-in + stations AI (in-game check)** — built 2026-10-02: only
      live ships block the CAS's exit; hulks under it are shoved aside, asteroids under any arrival
      explode (vanilla death); the AI counts friendly stations as allies and enemy stations
      against a group, weighs flux/hull on both sides, and has two new plans — Reinforce (losing
      allied group; pincer if survivable, else beside the allies) and Hunt (weakened / crippled enemy
      groups). Checklist items 1, 4 and 9.
- [ ] **Fleet Slipspace Jump tuning** — after all gameplay systems are done (developer
      2026-10-02). Every number is a placeholder (radius, range, costs, AI
      thresholds, visual timings/colours). Tune live with `FleetJumpTune`, then bake the values into
      `systems/FleetSlipspaceJump.kt` / the layer classes.
- [x] **Fleet Slipspace Jump — exit check vs. stations** — developer 2026-10-02: friendly
      stations count (anchors + cluster strength); arrivals keep nudging around stations. Built.
- [x] **Slipspace Drive blink** — rebuilt 2026-10-02 as a Wolf-style blink: engine `TELEPORTER`
      toward the cursor / AI target, 500 su, 3 charges (1 per 10 s), 1.5 s in / 0.5 s out, no
      restrictions, `PHASE_TELEPORTER_2` AI; ODST-style visuals (hull ripple bow→stern + portal
      ahead, ghost slides in; exit portal at the landing point, ghost slides out). Slipspace Tuning
      → +30 % charge regen. See [implementations/slipspace-drive-blink.md](implementations/slipspace-drive-blink.md).
- [x] **Slipspace Drive blink — in-game test** — developer 2026-10-02: "all good". Checklist in
      [implementations/slipspace-drive-blink.md](implementations/slipspace-drive-blink.md)
      (ripple on the hull at every facing/zoom — flip `CORNER_Y` if mirrored, portals, ghost slide,
      charges, AI use, no `OpenGL error`).
- [x] **Slipspace Drive blink — portal + ripple from ODST data** — done 2026-10-02 from the
      `H3ODSTEK` tags (`c100_intro` slipspace_jump effects; ManagedBlam unusable there, tags read via
      `tool print-tag-to-xml`): portal = rupture `BLINK` kind with the ODST build (blue glow sphere,
      origin core, plasma web white→blue, soft light, blue flare, linear growth, shrink-close);
      ripple = plasma puffs + outward spark streaks + bow flash. ODST bitmaps are not exportable
      (resource data missing) — Reach textures + analytic glows instead. Data under
      `assets/reference/odst-carrier-jump/odst-data/`.
- [ ] **Slipspace Drive blink — tuning** — after all gameplay systems are done (developer
      2026-10-02). Every number is a placeholder (timings split, portal
      size/offset, `BLINK_*` intensities in `RuptureEffect`, ripple width/tail/spark grid/colours,
      jitter); bespoke sound (vanilla `system_phase_teleporter` for now).
- [x] **Slipspace Drive AI** — `PHASE_TELEPORTER_2` (developer ruling 2026-10-02).
- [x] **Slipspace Drive rupture damage** — no damage (developer 2026-10-02): the blink portals
      harm nothing.
- [x] **Slipspace Drive portal after the first battle** — per-combat reset added 2026-10-02
      (cleanup batch), carried into the blink rewrite (script state + the one-per-combat render
      plugin both reset on engine change).
- [x] **Drive portal art** — superseded 2026-10-02: the drive is now the blink (below); the old
      portal plugin and its missing `portal_*` sprite ids are gone.
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
- [x] **Slipspace Tuning campaign bonus** — built 2026-10-07 with the campaign Slipspace Jump:
      each tuned ship +10 % corridor transit speed (cap ×1.5); each S-modded one −10 % charge-up
      and cooldown (floor ×0.5). Placeholders. See
      [implementations/campaign-slipspace-jump.md](implementations/campaign-slipspace-jump.md).
- [x] **frigate3 builtInMods** — `covenant_remnants_slipspace_drive` now present on
      `covenant_remnants_frigate3.ship` (added in the ship-editor bounds pass); all 16 combat hulls match.
- [ ] **Tune hullmod numbers** — after all gameplay systems are done (developer 2026-10-02). OP costs and all new-mod values are vanilla-anchored placeholders.
- [x] **Weapon-buff hullmod** (from PLAN) — scrapped for now (developer 2026-10-02). Was: buff
      plasma/energy weapons, nerf the weapons themselves to compensate.

## Balance

- [ ] **Tune all numbers** — every ship, weapon, shield, and system value is a vanilla-anchored
      placeholder. Developer 2026-10-02: tuning waits until all gameplay systems are finished.
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
- [x] **UI indicator for paused/slowed shield regen** — built 2026-10-02: player status line
      "Shield regen — paused / 50% rate / full rate" while the shield is below full. See
      [implementations/shield-regen-status.md](implementations/shield-regen-status.md).
- [ ] **Shield regen status line (in-game check)** — player Covenant ship: take shield hits, watch
      the top-left status list go paused → 50% rate → full rate, gone at full shield and while
      broken; capital1 with Fortress on shows "frozen by Fortress Shield".
- [x] Decide Suppressor Beam damage type — **ENERGY** (developer ruling 2026-10-01; no change).

## Rendering / shaders

- [ ] **Engines below hull — in-game check** — all 16 Covenant hulls now carry the
      `RENDER_ENGINES_BELOW_HULL` hint (2026-10-07): in combat, engine flames should sit under the
      sprite edge, not over it. Also note what the campaign fleet view does (the hint is read by the
      combat ship). See [implementations/engines-below-hull.md](implementations/engines-below-hull.md).
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
      ruling). `SlipspacePortalEntityShader` was deleted 2026-10-08 when the portal look became
      the Ark rupture effect (no monolith shader left).
- [x] **Rupture remake from Reach game data** — developer decision 2026-10-01. The slipspace bomb
      (m45 `045lf_jorge`) was dumped from the official Reach editing kit (ManagedBlam, curves
      included) and rebuilt faithfully: `utils/render/rupture/` (`RuptureRenderer`, sphere + sprite
      programs, `RuptureEffect` timeline, generated `ReachCurves`), `data/shaders/rupture/`
      (`ruptureSphere`, `ruptureSprite`), 21 Reach bitmaps in `graphics/fx/slipspace_rupture/`.
      Replaces the composed procedural rupture shader (passes 1–2, removed). Extracted data and
      tools: `assets/reference/long-night-of-solace/reach-data/`. See
      [implementations/fleet-slipspace-jump.md](implementations/fleet-slipspace-jump.md).
- [x] **Rupture in-game look** — developer 2026-10-02: Fleet Jump "all good". First in-game look at the Reach remake: texture row order (flame
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
- [x] **`SlipspaceJumpTune` console command** — lists / sets / resets every campaign Slipspace
      Jump tunable; `NO_SHIP_RESTRICTION 1` and `FREE 1` for testing. See
      [implementations/campaign-slipspace-jump.md](implementations/campaign-slipspace-jump.md).
- [ ] **LemonLib repo is dirty** — hullmod-incompatibility + reflection source committed
      (`65a5b80`). Still uncommitted as of 2026-10-02: `jars/LemonLib.jar`, new
      `src/.../lemonlib/geometry/CubicCurve2D.kt`, `README.md` row. Developer to commit.

## Campaign Slipspace Jump (built 2026-10-07)

- [ ] **In-game checklist** — run the 9-point list in
      [implementations/campaign-slipspace-jump.md](implementations/campaign-slipspace-jump.md)
      (old-save migration, gating tooltip, picker, charge + cancel, transition, pocket path and
      push-back, exit + edge arrival, Tuning, save/load mid-flight, visuals / log).
- [ ] **Tuning** — after all gameplay systems are done: every constant in
      `campaign/abilities/SlipspaceJumpRules.kt` (costs, timings, portal size, path length / wind,
      stream width / burn, push-back, Tuning multipliers; tune live with `SlipspaceJumpTune`, then
      bake the defaults) and the ambience / stream colours.
- [x] **Pocket entrance / exit look** — Halo 3 "The Ark" slipspace exit, built 2026-10-08 (also
      the destination arrival; fleet slides out of the tube). See
      [implementations/ark-slipspace-portals.md](implementations/ark-slipspace-portals.md).
- [x] **Ark portals — visual pass** — built 2026-10-08 (developer rulings: all layers; mouth
      ahead, body back toward the fleet; tube one fleet length; flow out of the mouth; sound at the opening):
      lathed mesh + depth shading, procedural streaks, wall lightning, rim / glow / pool, analytic
      chromatic rings, sparks / glitter / anamorphic, refraction front + dim, carrier extras,
      pre-roll, afterglow, idle life; split passes around the fleets (back wall + far ring half
      below on `TERRAIN_7A`, the rest above). See
      [implementations/ark-portal-visuals-pass.md](implementations/ark-portal-visuals-pass.md).
- [ ] **Ark portals — in-game checklist** — the 5-point list in
      [implementations/ark-portal-visuals-pass.md](implementations/ark-portal-visuals-pass.md)
      (`SlipspaceJumpTune ark cruiser|carrier` for the look, combat jump / blink / torpedo
      unchanged, then pocket idle / entrance / exit / destination, variants, save/load, pause);
      the base 8-point list in
      [implementations/ark-slipspace-portals.md](implementations/ark-slipspace-portals.md).
- [x] **Ark portals — assumptions to confirm** — confirmed 2026-10-08: tube direction (mouth
      ahead, body back toward the fleet; the first complaint was the over-long tube running over
      the fleet; `ARK_TUBE_FLIP` stays 0) and the `late()` reading (`ARK_LATE_MIRROR 1`).
- [ ] **Ark portals — tuning** — every `ARK_*` tunable (scale, tilt, intensities, distortion cap,
      idle look, slide, and the visual pass's streak / lightning / ring / particle / phase values);
      the carrier burst outlasts the exit lurch + hold; noise aliasing when zoomed out
      (`ARK_TUBE_STREAK_U`); per-layer intensity budget (layers tone-map separately and add).
- [ ] **Pocket art** — background is a solid black jpg, light streaks are a placeholder entity.
      The engine's star / dust particle layer is hidden in the pocket since 2026-10-08
      (`SlipspaceSystemEFS.hideParticles`: the particle colour shifted to transparent every frame,
      the abyss mechanism — the engine only applies the colour while a shift is active, a base
      colour alone did nothing).
- [ ] **Granting quest** — the ability is quest-gated; until the quest exists `CovenantGiveAll`
      grants it (`SlipspaceJumpRules.grantToPlayer()`).
- [x] **`SlipspaceSystemEFS` bug** — fixed 2026-10-07: it disabled Transverse Jump sector-wide
      (non-current systems advance every frame); now guarded on the player being in the pocket.

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

## Planetfall (ground combat library in ../Planetfall; parked 2026-10-08)

- [ ] M5 hook, only after Planetfall reaches M5: add `combustiblelemon_planetfall` dependency, one test ground-battle option on a planet dialog, implementation doc. See [ground-combat-planetfall.md](ground-combat-planetfall.md).

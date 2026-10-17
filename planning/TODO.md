# TODO — what's left to do

Actionable checklist of outstanding work. Settled decisions live in [../CLAUDE.md](../CLAUDE.md);
roadmap/status in [PLAN.md](PLAN.md); per-feature details in [implementations/](implementations/).
Keep this file current — tick items off and add new ones as work lands.

## Art / geometry (placeholders in place)

- [x] **Ship bounds + weapon-slot positions** — done by the developer in the ship editor
      (reported 2026-10-10; hulls committed in `13af425`). See
      [implementations/ships.md](implementations/ships.md) and
      [implementations/in-game-test-pass-2026-10-01.md](implementations/in-game-test-pass-2026-10-01.md).
      Re-check `builtInMods` after every editor save.
- [x] **After the editor pass — built-ins + variant refit** (read-only check 2026-10-10):
      - [x] `cruiser5` and `supercap1` lost `covenant_remnants_covenantshieldhullmod` from `builtInMods`
        (both use the Covenant shield defense; same editor drop as 2026-10-01) — restored
        2026-10-10, all 16 combat hulls re-checked. See
        [implementations/editor-pass-builtins.md](implementations/editor-pass-builtins.md).
      - [x] Variants lag the new slot layouts — refit 2026-10-10 (rulings: role first, empty slots
        OK, core set + Mortar / Bombardment, spare OP to vents / caps + hullmods; all 16 fits
        approved). Also fixed: dead `WS0004` on destroyer1, wrong-size weapons on destroyer3 /
        cruiser1 / supercap1 / supercap2, destroyer2 over OP. Hull edits: destroyer2 gets `LB 1` /
        `LB 2`, capital1 `WS0014` → MEDIUM; `capital3_std.variant` renamed `cruiser5_std.variant`.
        See [implementations/variant-refit.md](implementations/variant-refit.md).
- [ ] **Variant refit — in-game check** — 6-point list in
      [implementations/variant-refit.md](implementations/variant-refit.md): refit screens, destroyer1
      deploys, destroyer2 wings launch from the new bays (move them in the editor if wrong), supercap
      carriers launch all wings and act as carriers, capital1 nose Projector, AI uses the new groups.
      Supercaps got the missing `CARRIER` hint 2026-10-10 (CAS `CARRIER, COMBAT`, CVS `CARRIER`).
- [ ] **Bespoke weapon art** — the 13 weapons use vanilla-analog sprites recolored purple; make real
      Covenant turret/hardpoint sprites. See [implementations/weapons-arsenal.md](implementations/weapons-arsenal.md).
- [x] **Projectile / missile recolor** — done by the weapons pass (checked 2026-10-10): every
      projectile weapon now has its own Covenant `.proj` (Pulse Laser, Plasma Cannon, Repeater,
      Autocannon, Mortar, Bombardment, the three torpedoes); no `.wpn` references a vanilla
      `projectileSpecId` any more. The beams use their own `.wpn` beam specs.
- [x] **Plasma torpedoes — Halo: Reach look** — built 2026-10-02: the m45 space Phantom torpedo
      (muzzle flash, light-volume body, star flare, lightning ribbon trail, fireball / plasma / smoke /
      sparks / lens-flare impact, Reach fire / hum / explosion sounds) on all three torpedoes, scaled
      by size; all three are now plain homing torpedoes with their own `.proj` (no Sabot / MIRV
      splits). See [implementations/plasma-torpedo-reach.md](implementations/plasma-torpedo-reach.md).
- [x] **Plasma torpedoes — in-game check** — folded into the launcher polish check (developer
      2026-10-09), see "Plasma torpedo launchers — in-game check" below. The split damage it noted is
      re-ruled there.
- [x] **Weapons pass — one weapon at a time** (rulings 2026-10-08: visuals + sounds + stats, mount
      art deferred, mixed full/cheap route ruled per weapon). Order: Energy Projector ✔ → Plasma
      Lance ✔ → Plasma Beam Emitter ✔ → Suppressor Beam ✔ → Pulse Laser ✔ → Plasma Cannon ✔ → Repeater ✔ →
      Autocannon ✔ → Mortar ✔ → Bombardment ✔ → torpedo launcher polish ✔ (all built 2026-10-09; the
      open in-game checks are listed per weapon). Per-weapon workflow in the plan
      (`~/.claude/plans/go-through-the-weapons-generic-ritchie.md`, step 1 = ruling round).
- [x] **Energy Projector — Halo: Reach glassing look** — built 2026-10-08: the CCS cruiser glassing
      beam (vanilla-textured engine beam at 1.25x Tachyon width, overlay: charge rings /
      flashes / flare, flicker half-dome, impact bubble / half-dome / blue tongues / cloud ring,
      Reach glassing + Scarab impact sounds), white core / purple fringe, 0.5 su per unit, Reach
      timing (2 s charge, 3 s + 1 s beam, 10 s cooldown, 750 dps, 2000 flux/s). See
      [implementations/energy-projector-reach.md](implementations/energy-projector-reach.md).
- [x] **Energy Projector — in-game check** — 2026-10-08: beam strips reverted to ROUGH, width
      1.25× Tachyon, impact pinned to the live collision point (two fixes, see
      [implementations/energy-projector-reach.md](implementations/energy-projector-reach.md));
      developer: "looks ok for now". Still open there: `beamFireOnlyOnFullCharge`, placeholder gains.
- [x] **Plasma Lance — burst beam** — 2026-10-08: Tachyon-style burst (0.5 / 0.5 / 1 / 4), 2000 HE dps (1000 per
      burst), 2400 flux/s, white core / bluish-white fringe, Halo 3 beam rifle shot. See
      [implementations/plasma-lance.md](implementations/plasma-lance.md).
- [x] **Plasma Lance — in-game check** — burst feel, colours, sound tail length; developer 2026-10-10: "all ok".
- [x] **Plasma Beam Emitter — Reach focus rifle** — built 2026-10-08 (rulings: Reach colours
      as-is, 80 su/WU, continuous, numbers unchanged, fire in+loop+out + 5 impacts, suppression
      kept and extended to Covenant shields): violet plasma sheath + peach centre + 30/s pulses
      over the engine beam, blue muzzle tongues / embers / light cone, gold impact licks / sparks /
      orange flare, new sprite shader mode 11. See
      [implementations/plasma-beam-emitter.md](implementations/plasma-beam-emitter.md).
- [x] **Plasma Beam Emitter — in-game check** — developer 2026-10-08: "looks good"; sound too
      loud for a support weapon → volumes lowered (loop 0.55, in/out 0.6, impacts 0.5). Re-check
      the mix next session. See
      [implementations/plasma-beam-emitter.md](implementations/plasma-beam-emitter.md).
- [x] **Suppressor Beam — hot Plasma Beam Emitter** — built 2026-10-08 (rulings: reuse the focus
      rifle data, "a better Emitter that actually deals damage": 250 ENERGY dps / 200 flux / 14 OP,
      width 24, ×1.3 brightness, own sound ids at pitch 0.9). See
      [implementations/suppressor-beam.md](implementations/suppressor-beam.md).
- [x] **Suppressor Beam — in-game check** — 4-point list in
      [implementations/suppressor-beam.md](implementations/suppressor-beam.md); developer 2026-10-10: "all ok".
- [x] **Pulse Laser Turret — plasma machine gun** — built 2026-10-09 (rulings: machine gun not
      beam, Seraph-style cyan streaks, cheap `.proj` + PNG route, no Kotlin, Vulcan-shaped stats
      20 × 15 ENERGY = 300 dps / 150 flux/s / 500 range / 6 OP, Reach Spectre plasma cannon shot).
      See [implementations/pulse-laser-turret.md](implementations/pulse-laser-turret.md).
- [x] **Pulse Laser Turret — in-game check** — developer 2026-10-09: "we're good" after the fire
      volume dropped 0.5 → 0.25. See
      [implementations/pulse-laser-turret.md](implementations/pulse-laser-turret.md).
- [x] **Plasma Cannon — Reach ball-turret round** — built 2026-10-09 (rulings: Reach corvette
      ball turret look, cheap `PLASMA` bolt in Reach blue / peach + Kotlin on-hit splash reusing the
      torpedo impact burst without smoke, kit dumped for the data, corvette big-gun shot, stats
      slightly above Heavy Blaster: 500 / 1 s / 700 range / 700 flux / 13 OP; first in-game look:
      the bare `PLASMA` ball was an urchin → Kotlin energy aura added, aura only, no trail). See
      [implementations/plasma-cannon.md](implementations/plasma-cannon.md).
- [x] **Plasma Cannon — in-game check** — developer 2026-10-09: "looks good" after the aura +
      no-smoke pass. See [implementations/plasma-cannon.md](implementations/plasma-cannon.md).
- [x] **Plasma Repeater — Reach repeater bolt** — built 2026-10-09 (rulings: Reach plasma
      repeater teardrop, cheap `.proj` + sprite after a kit dump, Ion Cannon EMP arcs kept, Reach
      repeater shot, 0.4 s refire). See
      [implementations/plasma-repeater.md](implementations/plasma-repeater.md).
- [x] **Plasma Repeater — in-game check** — developer 2026-10-09: good after the fire volume
      dropped 0.4 → 0.2. See [implementations/plasma-repeater.md](implementations/plasma-repeater.md).
- [x] **Plasma Autocannon — Seraph quad cannon bolt** — built 2026-10-09 (rulings: Seraph quad
      cannon dumped, cheap tracer from the dump, Seraph cannon shot, vanilla hit sounds, +100 range
      / +1 OP). See [implementations/plasma-autocannon.md](implementations/plasma-autocannon.md).
- [x] **Plasma Autocannon — in-game check** — developer 2026-10-09: "looks good now" after the
      spear fix (round head, 30-long trail). See
      [implementations/plasma-autocannon.md](implementations/plasma-autocannon.md).
- [x] **Plasma Mortar — Reach Wraith mortar** — built 2026-10-09 (rulings: Wraith blob + AOE
      blast, kit dumped, Wraith fire + explosion sounds, mortar-shaped stats: 400 HE + 150 HE splash
      r 75, 1.5 s, 700 range, speed 500, 10 OP). Cannon aura generalised into `AuraStyle`. See
      [implementations/plasma-mortar.md](implementations/plasma-mortar.md).
- [x] **Plasma Mortar — in-game check** — developer 2026-10-09: renders fine, "good job" after the
      projectile-list tracking fix and the explosion volume 0.6 → 0.3. See
      [implementations/plasma-mortar.md](implementations/plasma-mortar.md).
- [x] **Plasma Mortar — mount it** — not needed (developer 2026-10-10: "they are weapons, they can
      go on anything"): players fit it; no variant placement planned.
- [x] **Plasma Bombardment — Reach Covenant AA gun** — built 2026-10-09 (rulings: heavy siege shell,
      AA gun "bfg" dumped, full rebuilt round + 1 s muzzle charge + rebuilt
      `covenant_explosion_artillery_huge`, short tail, AA gun fire (+ core fire mixed in: plain
      `bfg_fire` is the Plasma Cannon's sound) / charge / `cov_damage_large` sounds at 0.25, stats
      1000 HE + 400 HE splash r 150, 2.5 s cycle, 900 range, speed 500, 1000 flux, 20 OP). See
      [implementations/plasma-bombardment.md](implementations/plasma-bombardment.md).
- [x] **Plasma Bombardment — in-game check** — developer 2026-10-09: "all good" (checklist in
      [implementations/plasma-bombardment.md](implementations/plasma-bombardment.md); the scales stay
      placeholders for tuning).
- [x] **Plasma Bombardment — mount it** — not needed (developer 2026-10-10, as the Mortar).
- [x] **Plasma torpedo launchers — polish** — built 2026-10-09, last weapons-pass item (rulings:
      stats re-anchored now the splits are gone — light Atropos+ 1200 HE / 2 ammo / 400 su/s / 5 OP,
      medium Harpoon Pod+ 850 HE × 4 / 12 OP, heavy one 3000 HE hit / 8 ammo / HP 500 / 27 OP; no
      splash but a **plasma armor melt**: 50 % of the hit again out of the armor within 50 / 75 / 125
      su over 2 s, molten patch on the hull, yellow numbers; ammo regen 1 per 30 / 60 / 90 s;
      per-size engines (light Atropos, heavy 45°/s); fire 0.25 / impact 0.3, pitched by size; no dark
      smoke; tracking hardened: `.proj` onFire + `.wpn` everyFrame onFire + missile-list scan). See
      [implementations/plasma-torpedo-launchers.md](implementations/plasma-torpedo-launchers.md).
- [x] **Plasma torpedoes — size looks** — built 2026-10-10 after the first in-game look ("all the
      same, the heavy does not read as big damage"; rulings: colour + shape per size, not red):
      light small cyan dart with a thin trail, medium unchanged Reach violet, heavy white-hot head in
      a breathing magenta halo at look scale 12 (flight, muzzle and impact), louder / deeper hum;
      muzzle and impact take the size colour, melt patch unchanged. Developer 2026-10-10: "they look
      ok for now". See "Size looks" in
      [implementations/plasma-torpedo-launchers.md](implementations/plasma-torpedo-launchers.md).
- [x] **Shield at UI scale — in-game check** — developer 2026-10-10: "looks good in combat,
      scaling and ultrawide". Fixed 2026-10-10. The shield broke at any UI scale other than 100 %
      because of a hardcoded 1920 × 1080 versus virtual-px viewport coordinates, and the hit-ring
      screen copy mixed virtual and physical px. The audit of the rest of the mod also fixed the
      Fleet Jump aiming-ring line width. See
      [implementations/shield-ui-scaling-fix.md](implementations/shield-ui-scaling-fix.md).
- [x] **One-off `OpenGL error after setting uniforms: 1282` at the first shield draw** — closed
      2026-10-10: **not ours.**
      - **Symptom:** logged once per session as combat starts, with no visible effect. In the logs:
        every combat session at UI scale 1.4 / 1.5, but only 2 of about 20 at 1.0. `1282` is
        `GL_INVALID_OPERATION`.
      - **Why the shield reports it:** GL errors stay flagged until read, and the shield's check is
        the first `glGetError` in the session.
      - **How it was traced:** two diagnostic runs at UI scale 1.5 with labelled checkpoints.
        Clean: the mod's start-up GL work (shader warm-up, pocket texture), the menus before save
        load, and every shield GL step. The error first appeared between save load and the first
        shield's creation, where the mod draws nothing. So it comes from vanilla or another mod
        (LunaLib, MagicLib, Console Commands, ShipBrowser, PracticeTargets, TAR).
      - **Outcome:** checkpoints removed. The original single check stays, with a comment pointing
        here.
- [x] **Crash on first shield raise near damaged ships — in-game check** — developer 2026-10-10: "all ok". Fixed 2026-10-10
      (`ShieldShader.initQuad` left its VBO bound; vanilla's damage decals use client arrays and
      LWJGL threw "Cannot use Buffers when Array Buffer Object is enabled"). Damage some armor, then
      raise a Covenant shield for the first time in the battle: no crash. See
      [implementations/shield-vbo-crash-fix.md](implementations/shield-vbo-crash-fix.md).
- [x] **Plasma torpedo launchers — in-game check** — developer 2026-10-10: "all ok". The 11-point checklist in
      [implementations/plasma-torpedo-launchers.md](implementations/plasma-torpedo-launchers.md)
      (folds in the 2026-10-02 Reach look check; check `starsector.log` for `Plasma torpedo
      registered by …` / `Plasma torpedo hit …`). Melt glow / ember constants are placeholders from
      the headless harness.
- [x] **Reach curve audit** — requested by the developer 2026-10-10, closed the same day. The stock tag dumper printed
      unclamped curves as raw 0..1 and unclamped Basic constants as 0 (found 2026-10-09, see
      [implementations/plasma-bombardment.md](implementations/plasma-bombardment.md)).
      - [x] Report — done 2026-10-10: 134 tags re-dumped with the patched dumper, 211 curves + every
        suspect constant checked, Ark / ODST blobs re-decoded. 12 Reach-side values differ from the
        tags (beam impact plasma aspect + spark_long + muzzle light / plasma aspect; torpedo muzzle
        round scale / flash intensity / count; projector flicker / half glow / bubble aspect; rupture
        flame aspect + spark stretch); `ReachCurves.kt` / `ShieldImpactCurves.kt` regenerate
        byte-identical; one ODST doc number fixed. See
        [implementations/reach-curve-audit.md](implementations/reach-curve-audit.md).
      - [x] Developer rulings 2026-10-10: keep the current look for all four effects (beam,
        torpedoes, projector, rupture); no code change, differences recorded as deliberate.
      - [x] Open question: non-ranged Basic "ramps" in the Halo 3 Ark tags (Reach evaluates them as
        constant min) — not pursued (developer 2026-10-10); the Ark portals keep the ramp reading
        they passed in game with.
- [x] **Plasma Cannon splash — switch to the new impact** — built 2026-10-10 (rulings: the
      Bombardment's rebuilt `covenant_explosion_artillery_huge` (`ArtilleryBurst`) at unit 8 after a
      headless preview of 6 / 8 / 10 / 12, 30 sparks, 1 s blast light, quiet `cov_damage_large` boom at
      0.15 pitched ~1.2, still visual only). See
      [implementations/plasma-cannon-artillery-impact.md](implementations/plasma-cannon-artillery-impact.md).
- [x] **Plasma Cannon impact — in-game check** — closed by the developer 2026-10-10. 5-point list in
      [implementations/plasma-cannon-artillery-impact.md](implementations/plasma-cannon-artillery-impact.md):
      blue flash / fireballs / sparks at the old splash size, glow gone within 1 s, quiet boom, no area
      damage, Bombardment and Mortar unchanged, frame rate.
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
- [x] **Fleet Slipspace Jump — warp-in + stations AI (in-game check)** — developer 2026-10-10: "all ok". Built 2026-10-02: only
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
- [x] **Huragok Crew update** — built 2026-10-17: max CR bonus only with Covenant Systems, repair
      rate ×1/0.75 (exact cancel, always on), in-combat hull repair with elite Combat Endurance
      numbers (own pool, stacks with the skill). See
      [implementations/huragok-crew-hull-repair.md](implementations/huragok-crew-hull-repair.md).
- [ ] **Huragok Crew — in-game check** — 4-point list in
      [implementations/huragok-crew-hull-repair.md](implementations/huragok-crew-hull-repair.md):
      refit CR/repair with and without Covenant Systems, tooltip, combat regen rate + cap, redeploy.
- [x] **Covenant Shield flux penalty + enforced built-in** — built 2026-10-17: −20 % max flux on
      every ship with the hullmod; load-time check makes it built-in on every hull whose defense is
      the Covenant shield. See
      [implementations/covenant-shield-flux-and-builtin.md](implementations/covenant-shield-flux-and-builtin.md).
- [ ] **Covenant Shield flux — in-game check** — 3-point list in
      [implementations/covenant-shield-flux-and-builtin.md](implementations/covenant-shield-flux-and-builtin.md):
      refit flux on Covenant and non-Covenant hulls, tooltip, load-time built-in fallback.
- [x] **Plasma Capacitors rework** — built 2026-10-17: energy weapon rate of fire and ammo regen
      ×1.2, energy flux cost ×1.2 (beams exempt); old flux capacity / vent / overload effects
      removed. See [implementations/plasma-capacitors-rof.md](implementations/plasma-capacitors-rof.md).
- [ ] **Plasma Capacitors — in-game check** — 3-point list in
      [implementations/plasma-capacitors-rof.md](implementations/plasma-capacitors-rof.md).
- [ ] **Plasma Capacitors — variant re-check** — six stock variants carry it from when it was a
      flux-sustain mod (frigate1, frigate2, destroyer4, cruiser5, capital1, capital2); decide whether
      they keep it.
- [x] **Weapon hullmods** — built 2026-10-17: Capacitor Discharge, Target Lattice, Overcharged
      Emitters (any ship, OP 5/10/15/25, no dock, "Weapons" filter); Plasma Capacitors lost its dock
      requirement. See [implementations/weapon-hullmods.md](implementations/weapon-hullmods.md).
- [ ] **Weapon hullmods — in-game check** — 5-point list in
      [implementations/weapon-hullmods.md](implementations/weapon-hullmods.md).
- [ ] **Weapon hullmods — status icons** — vanilla ship-system icons are placeholders.
- [x] **Shield hullmods** — built 2026-10-17: Hardened Shield Matrix, Deep Shield Reservoir,
      Accelerated Emitters, Stabilized Emitters, Reactive Shell (Covenant-shield ships only,
      "Shields" filter, no dock; Reactive ⟂ Reservoir), per-ship shield modifiers via dynamic stats,
      shield bar overflow fixed. See [implementations/shield-hullmods.md](implementations/shield-hullmods.md).
- [ ] **Shield hullmods — in-game check** — 7-point list (incl. Fortress AI on a hardened Reverence) in
      [implementations/shield-hullmods.md](implementations/shield-hullmods.md).
- [ ] **Tune hullmod numbers** — after all gameplay systems are done (developer 2026-10-02). OP costs and all new-mod values are vanilla-anchored placeholders.
- [x] **Weapon-buff hullmod** (from PLAN) — scrapped for now (developer 2026-10-02). Was: buff
      plasma/energy weapons, nerf the weapons themselves to compensate.

## Loot / acquisition

- [x] **Covenant content out of the random loot pool** — 2026-10-12 ruling: hulls, wings, weapons,
      hullmods drop only from combat (or future quests). `rare_bp` removed everywhere,
      `no_drop_salvage` on wings + weapons, hullmods lose `no_drop` so they can drop from defeated
      Covenant ships. See [implementations/loot-pool-exclusion.md](implementations/loot-pool-exclusion.md).
- [ ] **LPC loot crash — root cause unknown** — player report: crash when a salvage roll produced one
      of our LPCs. No trace available; local logs clean; wing / variant / hull data valid. Need the
      reporter's `starsector.log` stack trace. The random-pool path is closed (above), so the
      reported trigger is gone, but the LPC itself is unproven.
- [ ] **Loot-pool exclusion — in-game check** — repeated salvage shows no Covenant LPC / weapon /
      blueprint; a defeated Covenant carrier yields LPCs, weapons, hullmods; codex still lists them.

## Balance

- [ ] **Tune all numbers** — every ship, weapon, shield, and system value is a vanilla-anchored
      placeholder. Developer 2026-10-02: tuning waits until all gameplay systems are finished.
- [x] **Campaign burn speed raise** — 2026-10-10 (ruling: +2, cap 9, hulls already above 9 kept):
      cruiser1 / 2 / 4 and capital2 8→9, capital1 7→9, supercaps 6→8; frigate1 / 2 stay 10, the
      9s unchanged. See [implementations/burn-speed-raise.md](implementations/burn-speed-raise.md).
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
- [ ] **Shield regen flux falloff — tune + follow-ups** — built 2026-10-12: regen ×
      `min(maxFlux·(1+bonus)/flux − 1, 1)`, live bonus via `ShieldTune FLUX_REGEN_BONUS` (starts 0).
      Hullmod text (calculated knee / max-flux line) and fleet-jump AI strand estimate (current-flux
      snapshot) updated 2026-10-12. Left to do: in-game check (incl. hullmod tooltip), pick the
      bonus and hard-code it. See
      [implementations/shield-flux-regen.md](implementations/shield-flux-regen.md).
- [x] **Shield regen status line (in-game check)** — developer 2026-10-10: "all ok". Player Covenant ship: take shield hits, watch
      the top-left status list go paused → 50% rate → full rate, gone at full shield and while
      broken; capital1 with Fortress on shows "frozen by Fortress Shield".
- [x] Decide Suppressor Beam damage type — **ENERGY** (developer ruling 2026-10-01; no change).

## Rendering / shaders

- [x] **Shield outline sits inside the sprite** — 2026-10-12: Laplacian smoothing shrank the polygon.
      Now Taubin smoothing (no shrink) + outward normal offset `MARGIN` (default 14 su), scale
      workaround (1.05, 1.1) dropped to (1, 1). All live-tunable via the `ShieldTune` console
      command. See [implementations/shield-outline-margin.md](implementations/shield-outline-margin.md).
- [ ] **Shield outline — in-game check** — `ShieldTune` in combat: rim clears the sprite on every
      hull (frigates to supercaps), corners rounded (adaptive Catmull-Rom, `CURVE_DEG` /
      `CURVE_MAX`), no spikes at sharp bow/wing corners, collapse extrusion and sweep still line
      up, honeycomb unaffected, no frame cost on the 56-point hulls. Dial `MARGIN` /
      `ITERATIONS` / `CURVE_DEG`, then bake the winners into `ShieldOutline` defaults.

- [x] **Engines below hull — in-game check** — developer 2026-10-10: "all ok". All 16 Covenant hulls now carry the
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
      (`ruptureSphere`, `ruptureSprite`), 21 Reach bitmaps in `graphics/covenant_remnants/fx/slipspace_rupture/`.
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
- [x] **Release packaging** — 2026-10-10 (rulings: Gradle zips, scripts wrap; `Covenant
      Remnants.zip` without version; no LICENSE; skip `noise.png` + lance `fire_fp_*`):
      `packageMod` → `Covenant Remnants.zip`, one `Covenant Remnants/` folder, runtime files
      only (271 files, 6.4 MB). `package_mod.bat` / `.sh` call it. See
      [implementations/release-packaging.md](implementations/release-packaging.md).
- [x] **v0.1.1 + `CHANGELOG.md`** — 2026-10-10: the version is bumped in `mod_info.json`, the
      `.version` file and `build.gradle.kts`. `CHANGELOG.md` starts at v0.1.1 (UI-scaling fixes, plus
      the save-load crash fix added 2026-10-11 before release), and the whole file ships in the zip. See "v0.1.1" in
      [implementations/release-packaging.md](implementations/release-packaging.md).
- [x] **Crash on loading a save made after a slipspace jump — in-game check** — developer
      2026-10-11: "i loaded a save after jump" (no crash). Fixed 2026-10-11.
      `SlipspaceStreamTerrainPlugin` never got vanilla's package-private `readResolve` (XStream
      skips it across packages), so `bounds` / `particles` were null after any load. It now has its
      own. See
      [implementations/slipspace-stream-load-crash-fix.md](implementations/slipspace-stream-load-crash-fix.md).
- [x] **System background and music wrong after leaving slipspace (until reload)** — 2026-10-11.
      Fixed and verified in game. Not the pocket EFS. See
      [implementations/slipspace-exit-music-background-fix.md](implementations/slipspace-exit-music-background-fix.md).
      - [x] **Background:** fixed. The pocket's 1024² `slipspace_bg.jpg` shrank the shared system
        background texture, and the 2048² vanilla backgrounds then failed to upload on exit. It is
        now a 2048² pure-black image (overwrite approved by the developer 2026-10-11). Rule: pocket
        art must be 2048×2048.
      - [x] **Music:** fixed. The pocket and the destination share the `CAMPAIGN_SYSTEM` music
        token, so the previous system's track kept playing. The exit now calls
        `restartCurrentMusic()` when the token is unchanged. The diagnostic logging is removed.
      - [x] **In-game check** — developer 2026-10-11: "tested the change and it works".
- [x] **v0.1.2 bump** — 2026-10-11: v0.1.1 was released (commit `ddac966`). The version is now
      0.1.2 in all three files, and `CHANGELOG.md` has a v0.1.2 entry with the slipspace-exit
      background and music fixes. See "v0.1.2" in
      [implementations/release-packaging.md](implementations/release-packaging.md).
- [x] **Version Checker master file** — the online `.version` on GitHub `master` must match each
      released version, or players get no update notice. Covered 2026-10-12: `release.bat` only
      runs on `master` and pushes it with the bumped `.version`.
- [x] **Release script** — 2026-10-12: `release.bat vX.Y.Z [-DryRun]` (wrapper over
      `scripts/release.ps1`) bumps `mod_info.json`, `covenant-remnants.version`, `build.gradle.kts`,
      `gradle.properties`, rolls `CHANGELOG.md` Unreleased into the new version, runs `packageMod`,
      commits, tags, pushes `master`, creates the GitHub release with the zip (rulings: gh CLI,
      master only, attach zip). See [implementations/release-script.md](implementations/release-script.md).
- [x] **In-game test harness** — 2026-10-17: new sibling mod + repo `../StarsectorHarness`
      (id `combustiblelemon_harness`) boots the real game from Python with a loopback JSON bridge,
      runs smoke / campaign / combat / compat suites against this mod, and keeps save fixtures per
      release. `release.bat vX.Y.Z -Fixture <save_dir>` snapshots a save into its fixture corpus.
      See [implementations/test-harness.md](implementations/test-harness.md).
- [ ] **Test harness — open items** — see the "Open items" list in
      [implementations/test-harness.md](implementations/test-harness.md): more combat scenarios
      (one per hull), a fixture with an armed fleet + the first release snapshot, LemonLib target,
      fixture backups (git-ignored). All five suites (smoke, campaign, combat, compat, encounter)
      passed on 2026-10-17.
- [ ] **Release script — first real run** — install `gh` (`winget install --id GitHub.cli`,
      `gh auth login`), then on a clean `master`: `release.bat v0.1.2 -DryRun`, inspect, undo with
      the printed `git checkout --` line, then the real `release.bat v0.1.2`. Note: the v0.1.2 bump
      from 2026-10-11 was reverted to 0.1.1 (never released); the script does the bump now.
- [x] **Graphics under `graphics/covenant_remnants/`** — 2026-10-10 (developer ruling: that folder
      holds all mod graphics): `graphics/fx/` → `graphics/covenant_remnants/fx/`, `graphics/missiles/`
      → `graphics/covenant_remnants/missiles/` (81 files, `git mv`); `settings.json`, 8 `.proj`, docs
      and reference tools re-pathed, vanilla `graphics/fx/` refs untouched. See
      [implementations/graphics-folder-move.md](implementations/graphics-folder-move.md).
- [ ] **Graphics move — in-game check** — no missing-sprite errors in the log; bolts, torpedoes,
      shells and all fx (shield, rupture, Ark, projector, beam) still render.
- [x] **LemonLib release packaging** — 2026-10-10 (rulings: same shape as Covenant, jar code-only,
      ship README + docs/): `packageMod` → `LemonLib/LemonLib.zip` (12 files), `package_mod.bat` /
      `.sh`, `gradlew.bat` added, jar no longer carries `data/` + `mod_info.json`. `jars/LemonLib.jar`
      untracked 2026-10-10 (`git rm --cached`; build output, `/jars` already ignored), the removal is
      staged for the developer to commit. See
      [implementations/release-packaging.md](implementations/release-packaging.md).
- [ ] **Release packaging — clean-install check** — unzip `Covenant Remnants.zip` into a clean
      `mods/` and start the game once. Static pre-check done 2026-10-10 (250 referenced paths, 77
      sounds, 77 sprites all resolve from the zip + vanilla; 0 problems).
- [x] **In-game test session** — skipped (developer 2026-10-10): the build goes to player testers
      instead; the open in-game checks above stay open until their reports. The sheet stays as a
      reference: [implementations/in-game-test-pass-2026-10-10.md](implementations/in-game-test-pass-2026-10-10.md).
- [x] **Tester cache** — 2026-10-10 (rulings: Corvus abandoned station storage, once per save,
      `_std` fits, 1 / 3 / 4 / 6 / 10 ships by size, 30 per weapon / wing, optional-hullmod
      modspecs, Huragok Slipspace Engram with a vanilla placeholder icon): temporary exception to
      quest-gated acquisition. See [implementations/tester-cache.md](implementations/tester-cache.md).
      2026-10-12: the engram is renamed Covenant Slipspace Drive Schematics, and the cache also
      holds the Covenant Slipspace Navigation System (old tester saves get it once).
- [ ] **Tester cache — remove for release** — turn `TesterCache.ENABLED` off (or delete the class +
      CSV rows) once the quests hand out Covenant content; art and final text for both slipspace
      items.
- [x] **LemonLib repo is dirty** — hullmod-incompatibility + reflection source committed
      (`65a5b80`), `CubicCurve2D.kt` + `README.md` row committed (`0ed9f18`). Only the rebuilt
      `jars/LemonLib.jar` showed as modified on 2026-10-10; closed by the developer the same day.
- [x] **Kotlin runtime in LemonLib, LazyLib dropped** (2026-10-12). Rulings: drop `lw_lazylib`,
      Kotlin 2.2.20, commit both jars.
      - LemonLib ships `jars/lib/Kotlin-Runtime.jar` (stdlib 2.2.20 + reflect + coroutines 1.11).
      - Both builds are on Kotlin 2.2.20 and compile against that jar.
      - Covenant's 5 LazyLib calls moved to LemonLib `geometry/Collision.kt` and
        `render/Primitives.kt`, plus `Misc.getDistance` and an index loop.
      - `lw_lazylib` was removed from `mod_info.json` and from the plugin check.
      - See [implementations/kotlin-runtime-lemonlib.md](implementations/kotlin-runtime-lemonlib.md).
- [x] **Kotlin runtime — in-game check.** Passed 2026-10-12.
      - The developer reports that everything works, including the plasma burn and slipspace.
      - `starsector.log` confirms the order: LemonLib (`~LemonLib`) is listed last "in order of
        priority", and `LemonLib.jar` and `lib/Kotlin-Runtime.jar` load before LazyLib's jars.
      - The log has no `NoClassDefFoundError`, `NoSuchMethodError` or other linkage errors.
      - The only errors are unrelated: two vanilla hull-spec warnings, and Ship Browser probing
        mods that have no `ship_data.csv`.
- [ ] **Kotlin runtime — commit and release order.**
      - LemonLib has an uncommitted runtime change: `.gitignore`, the two `jars/lib/*.jar`, the build,
        `mod_info.json`, the README and the CHANGELOG.
      - Release LemonLib **before** Covenant, so Covenant's `release.ps1` pins a LemonLib version
        that ships the runtime.
- [x] **Kotlin runtime shadowing guard** (2026-10-12, developer: "LemonLib needs to be available when
      loading the rest of our mods").
      - LemonLib's `mod_info.json` has `"sortString": "~LemonLib"`, so it sorts after every mod
        and its jars and runtime come first on the classpath.
      - A simulated launcher sort over all 36 installed mods put it last, after "zz GraphicsLib".
      - None of our mods has a `sortString`, so they all stay behind it.
      - Rule: none of our mods may set a `sortString` that sorts after `~LemonLib`.
      - See [implementations/lemonlib-sortstring.md](implementations/lemonlib-sortstring.md).

## Campaign Slipspace Jump (built 2026-10-07)

- [x] **In-game checklist** — developer 2026-10-10: "all ok". The 9-point list in
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
- [x] **Ark portals — in-game checklist** — developer 2026-10-10: "all ok". The 5-point list in
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
- [ ] **Granting quest** — the ability and its navigation upgrade are quest-gated; until the quest
      exists `CovenantGiveAll` grants both (`SlipspaceJumpRules.grantToPlayer()` /
      `grantUpgradeToPlayer()`), and testers get the Covenant Slipspace Drive Schematics and the
      Covenant Slipspace Navigation System (right-click each) in the Corvus tester cache — candidate
      quest rewards. See [implementations/tester-cache.md](implementations/tester-cache.md).
- [x] **`SlipspaceSystemEFS` bug** — fixed 2026-10-07: it disabled Transverse Jump sector-wide
      (non-current systems advance every frame); now guarded on the player being in the pocket.
- [x] **Advanced Slipspace (navigation upgrade)** — built 2026-10-12 (rulings: upgrade of the same
      ability via the Covenant Slipspace Navigation System item; a `<system> - plot coordinates` row
      per star opens Ashlib's system map; other systems still go through the pocket and land at the
      point; the current system is a direct jump, no pocket; zones radius × 1.5 around stars, outer
      radius limit, never-entered systems hidden, 2000 su minimum in-system; in-system cost 1 LY of
      fuel + the jump's CR hit + cooldown; facing = travel direction; Ashlib required). See
      [implementations/advanced-slipspace-jump.md](implementations/advanced-slipspace-jump.md).
- [ ] **Advanced Slipspace — in-game checklist** — the list in
      [implementations/advanced-slipspace-jump.md](implementations/advanced-slipspace-jump.md),
      with Ashlib enabled and Ashes of the Domain **disabled** (Ashlib's planet renderer references
      it; expected safe, unproven). Confirm the assumptions listed there too.
- [ ] **Advanced Slipspace — tuning** — `BLINK_FUEL_LY`, `BLINK_CR_MULT`, `BLINK_COOLDOWN_MULT`,
      `BLINK_MIN_DISTANCE`, `STAR_EXCLUSION_MULT` (live via `SlipspaceJumpTune`), the overlay colours
      in `SlipspacePointMap`, the system-map layout in `SlipspacePointDialog`.
- [ ] **Slipspace items — art and text** — both items use vanilla placeholder icons (Janus Device,
      hyperspace data) and draft descriptions.

## From PLAN.md (still open)

- [ ] Quests & narrative content (primary acquisition path; not started).
- [x] `mod_info.json` dependency reconciliation — LunaLib / Console Commands are **optional**
      (developer ruling 2026-10-01); `mod_info.json` already correct (declares only LemonLib,
      MagicLib, LazyLib). Removed a stale `ShaderLayers` row from `data/console/commands.csv` that
      pointed at a class moved to LemonLib.
- [x] **LunaLib settings were dead config** — `data/config/LunaSettings.csv` (25 old debug shield
      settings no code read) deleted 2026-10-01 per developer.
- [x] **LunaLib settings** — built 2026-10-10 (rulings: performance + effect volume, isolated
      bridge, live apply): slipspace particle density 0.1–1, slipspace heat haze on/off, shield hit
      distortion on/off, effect volume 0–1 for code-played sounds (clamped from 0–2 the same day). `settings/CovenantSettings`
      holds the defaults; `settings/LunaSettingsBridge` is the only `lunalib` importer, loaded only
      when LunaLib is enabled. See [implementations/lunalib-settings.md](implementations/lunalib-settings.md).
- [x] **Version Checker support** — 2026-10-10: `data/config/version/version_files.csv` registers
      the existing `covenant-remnants.version` with LunaLib's Version Checker; `packageMod` now ships
      the `.version` file (it was left out). LemonLib got the same: new `lemonlib.version` + CSV. See
      [implementations/version-checker.md](implementations/version-checker.md).
- [ ] **Version Checker — master URLs 404** — both `masterVersionFile`s (Covenant GitHub raw
      `master`, LemonLib `main`) returned 404 on 2026-10-10: the repos look private. Update checks
      need them reachable. Optional later: `modThreadId`, `modNexusId`, `directDownloadURL`,
      `changelogURL`. LemonLib: commit the two new files (its repo is the developer's).
- [ ] **LunaLib settings — in-game check** — 7-point list in
      [implementations/lunalib-settings.md](implementations/lunalib-settings.md): page + startup log
      line, live apply on save, density 0.1, haze off, shield rings off, volume 0 / 0.5. Start with
      LunaLib disabled passed 2026-10-10 (no `NoClassDefFoundError`).
- [ ] Items (Covenant items).
- [x] **Custom ship-name list for Covenant hulls** — skipped 2026-10-10. The game only supports
      name lists per faction: `data/strings/ship_names.json` sets, picked through a faction's
      `shipNameSources`. There is no per-hull hook, and the mod has no faction (non-goal). Developer
      ruling: no home-built renaming code. Confirmed by decompiling `com.fs.starfarer.loading.
      ShipNameStore` from `starfarer_obf.jar`: it uses the fleet member only to name fighter wings,
      and ships always get a random name from the picked group.

## Planetfall (ground combat library in ../Planetfall; parked 2026-10-08)

- [ ] M5 hook, only after Planetfall reaches M5: add `combustiblelemon_planetfall` dependency, one test ground-battle option on a planet dialog, implementation doc. See [ground-combat-planetfall.md](ground-combat-planetfall.md).

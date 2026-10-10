# Implementation — Fleet Slipspace Jump (CAS-class Assault Carrier, supercap1)

`covenant_remnants_supercap1` carried the placeholder single-ship Slipspace Drive. TODO.md asked for
a radius mass-jump with no spec beyond one line. This pass designs it with the developer (six
AskUserQuestion rounds, 2026-10-01) and builds it: a new ship system unique to the CAS, its AI,
rupture damage, live console tuning, and a rupture effect modelled on the Halo: Reach *Long Night of
Solace* slipspace bomb — first from the developer's video (procedural shader, passes 1–2), then
remade from Reach's own effect data, textures and sounds (official editing kit). Player activation:
prime → aim → confirm.

## Research findings (starsector-knowledge, game 0.98a-RC8)

- **Vanilla yardsticks.** Recall Device (Astral, Scintilla) moves only the carrier's own wings:
  phased + jitter + fade over a 0.5 s charge, then `land()`; 50% of base flux capacity per use,
  30 s cooldown. Phase Teleporter (Hyperion): self only, 1500 su, 15 s. Phase Skimmer: self only,
  300 su, 3 charges. No vanilla system moves other ships.
- **Script lifecycle.** One `ShipSystem` per ship per battle (`Ship` constructor → `createSystem`);
  the stats script is fetched once per system. Timing overrides (`getIn/Active/OutOverride`) are read
  in the `ShipSystem` constructor, so they only change between battles; `setCooldown` applies live.
- **`isUsable` / `getInfoText`.** Called every frame by the ship-info widget for the player ship
  (`class.new` `update`), and on each activation attempt by the system (`F.advance`).
- **`CombatEngineAPI.isUIAutopilotOn()` is inverted.** It returns `!combatUI.isAutopilotOn()`
  (`combat` ~l.2062), i.e. **true while the player flies manually**. Turning autopilot off sets the
  player ship's AI to null (`CombatState.setAutopilot`), so "player at the controls" is checked as
  `engine.playerShip === ship && ship.shipAI == null`.
- **`runScriptWhileIdle`.** With it, `apply` runs every frame in every state (IDLE/COOLDOWN too)
  and `unapply` is never called; without it, `apply` runs only while the effect level is > 0 and
  `unapply` runs once when it drops to 0 (`combat.systems` ~l.1581). The engine itself shows
  `getStatusData` lines for the player ship.
- **Custom AI.** `"aiType":"CUSTOM"` + `aiScript`. AI destination: `giveCommand(USE_SYSTEM, loc, 0)`
  → `F.fire(loc)` sets `targetLoc`, but the order vs. `isUsable` is unclear, so the AI hands the
  destination to the script through a map instead. `mouseTarget` on AI ships is the aim point.
- **Moving entities.** `getLocation()` is the live `Vector2f`; `set()` persists. No `setLocation`.
  Missiles: `interruptContrail()` before and after. `setPhased(true)` = `CollisionClass.NONE` +
  phased render layer; `setPhased(false)` restores the hull spec's collision class. Vanilla
  teleporter nudges blocked arrivals: map bounds + ship/asteroid overlap, 10 rings × 12 directions.
- **`noShield = TRUE` kills the Covenant shield.** The engine treats the phase slot as "special"
  and forces it off while such a system is IN/ACTIVE (`combat.systems` ~l.1713). The drive has it;
  this system does not.
- Render layer `ABOVE_SHIPS_AND_MISSILES_LAYER` draws over phased ships, fighters and missiles.
- **Input while paused** (`CombatState`): `controlPlayerShip` — where the keys become ship commands
  and the ship's mouse target is updated — only runs when not paused (unless dev mode or
  `allowShipControlWhilePaused`), so the system key does nothing while paused. Plugins'
  `processInputPreCoreControls` runs every frame, paused too, before it; consumed events are skipped
  by the controls. `ShipAPI.useSystem()` gives the same `USE_SYSTEM` command as the key;
  `blockCommandForOneFrame` adds to an `EnumSet` cleared after the ship processes its commands.
  A failed activation (isUsable false) plays the system's `outOfUsesSound` (at most every 2 s).
  The key binding's control enum name is `SHIP_USE_SYSTEM` (read from `starfarer_obf.jar`; the
  title package is not in the knowledge base). `SettingsAPI.getMouseX/Y` = virtual screen coords.

## Reference analysis (`Halo_Reach_Slipspace_Bomb.mp4`, 9.9 s, 30 fps)

Frames in `assets/reference/long-night-of-solace/` (contact sheets + key crops; scratch, not shipped;
`/assets` is gitignored, so they exist only on the developer's machine — the source clip is
`Halo_Reach_Slipspace_Bomb.mp4`).
From detonation: **0–0.15 s** white-out → cyan wash. **0.15–0.5 s** white-hot sphere, violet halo,
interior → pale lavender mottling, horizontal glitch slices tearing the edge. **0.5–2.0 s** interior
→ near-black indigo with a crawling violet-white lightning web, thick white rim, violet outer glow,
opaque. **2.0–2.6 s** collapse: rim fades, sphere shrinks to ~half, outward needle spikes, inward
radial streaks to a white core. **2.6–3.1 s** pop: white → salmon-pink bloom ~1.7× with a cross
flare, shifting pink → magenta → violet. **3.5 s+** remnant: periwinkle glow → translucent violet
nebula with vein wisps, expanding past the original size and fading.

## Reach game data (official Reach editing kit, extracted 2026-10-01)

Data, textures and the extraction tools are in `assets/reference/long-night-of-solace/reach-data/`
(gitignored; see its README). How it was read: HR Tool `export-tag-to-xml` drops function curves,
so the tags were dumped through the kit's `bin\ManagedBlam.dll` (`TagValueCustomFunctionEditor.
Evaluate/EvaluateColor` sample every curve); bitmaps via `tool export-bitmap-tga` (the five
lens-flare bitmaps only as 16-bit DDS). No HLSL source ships with the kit; shader behaviour comes
from the render-method options (`shaders\particle.render_method_definition`).

**Where it lives.** `m45` ending cinematic `045lf_jorge`, scene `_030` (30 fps). Shot 1 frame 300:
`slipspace_init` on the corvette. Shot 2 frame 0: `slipspace_secondary` (charge), frame 100:
`slipspace_event` (the rupture) on the anchor `fx_bro_splosion_blackship`; later shots add
`slipspace_event_1_5` / `slipspace_event2` (aftermath clouds).

**Charge — `slipspace_secondary` (3 s before the open):** `slipspace_init.particle` stream —
screen-facing sprites, albedo `palettized_2d_plasma` (two scrolling 512 plasma-cloud maps, index =
their difference, palette `slipspace_init_gradient` = a blue diagonal band), `sphere` warp (−1,
makes each sprite read as a small sphere), black point on, blend `add_src_times_srcalpha`,
intensity 7, alpha map `thin_smoke_large`; life 0.2–0.5 s, size 80–100, scale 0.1 → 1 and alpha
1 → 0 over life, black point 0 → 1, emission rising to 50/s. The overlapping, growing, dying
sprites are the "small white vibrating sphere". Plus a distortion ring (3 s), a violet star flare
(`star_flare`, 3.65 s) and a flickering blue light.

**Open — `slipspace_event` t = 0 (three model particles on one sphere mesh, 3 s life):**

- *glow*: `diffuse_only` (white), `alpha_blend`; colour salmon (1, .67, .53) → blue-violet
  (.41, .40, 1) by 0.28 s and back to salmon at the end, intensity 10 → 5; size 125 → 138
  (1.1–1.4 s) → 117 (2–2.3 s) → 125; scale 0 → 7.3 in 0.1 s → 10, hold, → 0 over 2.2–3 s. The
  ~10% oversize is the white rim around the dark body.
- *multiply*: `palettized_plasma` (`plasma_clouds01` ×4 scrolling 2.5/3 s, `plasma_clouds02` ×16
  scrolling 4/5 s) through palette `multiply_plasma` (near black with faint streaks), blend
  `multiply`; alpha 0 → 1 over 0.65 s — the body going black. Scale like the glow, → 0 over
  2.35–3 s. **Angle fade range 25°, cutoff 10°**: gone where the surface is within 10° of
  edge-on, full from 35° (≈ the outer 18 % of the radius) — the glow shows through there as the
  white rim from the first frame, the darkness filling in inside it.
- *plasma*: `palettized_plasma` (`plasma_clouds01` ×7, `plasma_clouds02` ×3, 6 s scroll) through
  palette `plasma_clouds` (violet only at index 0 → thin violet lines where the two noises cross:
  the lightning web), alpha map `slipspace_sphere_alpha` (jagged fade at the poles), blend
  `add_src_times_srcalpha`.
- Screen exposure boost with violet filter for 0.5 s; violet-blue light 0 → max in 0.7 s, off by
  2.5 s.

**Collapse — t = 2.0 s:** 150 sparks (`spark_ember`, parallel-to-direction streaks, lavender
(.72, .70, 1) ×4) on a 600 shell, 0.8 s life, grow then vanish — the inward streaks. Spheres shrink
to 0 over 2.2–3 s.

**Burst — t = 2.5 s (5.4 s):** `blast_radius` shell (`beam` texture, violet (.58, .52, 1), alpha
0.5 → 0.2, scale 1 → 35 over 6 s), `plasma_sphere` (plasma on the same mesh, violet gradient
palette, alpha 0.75, 1 → 35), `slipspace_burst_plasma` and `plasma_vertical` sprite clouds drifting
outward, ~840 `glitter` twinkles (blue-violet, flickering ×10–12) flying out, `smoke_fiery_large`
tinted salmon → violet (the pink pop), debris meshes. Collapse flare at 2.6 s: `light_volume_glow`,
radius 500 → 1500, colour lavender → salmon.

**Meshes and scale** (fitted from the particle models' raw vertices, `tools/fit_uv.py`):
`slipspace_sphere` is a UV sphere of radius 0.5, pole on mesh X, v = colatitude / π,
u = atan2(z, y) / 2π − 0.1575; `blast_radius` is a UV sphere of radius 0.3, pole on mesh Z,
v = colatitude / π, u = atan2(y, x) / 2π + 0.75. So the dark body is 0.5 × 125 × 10 = **625 Reach
units** (= our grab radius); the charge sprites (35–72 across as the charge builds) are ≈ 0.06 of it
(the video measured ≈ 0.057); the collapse sparks sit on a 600 shell (≈ 0.96); the burst shell ends
at 0.3 × 130 × 35 = 1365 (≈ **2.2×**; the 3.6× quoted before the fit was wrong).

## Decisions (developer, 2026-10-01)

| Topic | Decision |
| --- | --- |
| Structure | New system unique to supercap1, unrelated to the Slipspace Drive / Slipspace Tuning hullmods (Tuning untouched; it has no combat effect on supercap1 now). The drive stays on frigate1/frigate3/destroyer2. |
| Name / id | **Fleet Slipspace Jump**, `covenant_remnants_fleet_slipspace_jump`. |
| Grab scope | Everything inside the radius: ships of any size, allied **and enemy** (the player ship too), fighters of all sides, missiles (incl. mines), projectiles, hulks, asteroids. Excluded: stations and their modules. Ship modules ride with their parent, never alone. |
| Radius | 1000 su from the carrier's center; easy to fine-tune. |
| Flow | Carrier locked through charge-up; the rupture grows around it. The grab is decided at the jump: during the fade everything inside is phased, faded and jittered under the opaque rupture, then lands at the exit in one frame keeping offset, velocity, facing and targets. No forward launch. |
| Range | Destination = mouse target (player) / AI-chosen point. Min 2000 su (2× radius), **no max**. |
| Exit | Activation needs the CAS's landing spot clear of ships that aren't coming along, and inside the map. At the jump anything blocked or off-map (CAS included) nudges to the nearest free spot. (Narrowed 2026-10-02: only live ships block; see "Warp-in through small stuff".) |
| Costs | Drive locks (noFiring, noTurning, noStrafing, noAccel, noVent) but **the shield stays up** (noShield off). Flux gate < 70%; carrier flux → 80% at the jump; cargo pays nothing. **Cooldown 60 s.** Charge 8 s, **fade 2 s**, arrival 3 s (placeholders). |
| Damage | Every grabbed ship without a shield up (vanilla shield on / Covenant shield up — nothing else counts), any side, fighters and the CAS included: **10% of max hull, ignoring armor**; can kill. |
| Tuning | All constants in one helper object + live console command; reset on restart. |
| AI | Pull the fight home (only if the destination allies can take it) → else strand enemy heavies (survival check) → return / regroup. A strand may carry allies only if they are all frigates/destroyers. |
| Visuals | Composed shader from the reference. ~~Charge-up: see-through preview. Fade (2 s): flash → white sphere → opaque dark rupture.~~ (superseded by visual pass 2 below) Jump: collapse → pink pop → remnant cloud. Exit: ~~crackling seed~~, then flash + white sphere dissolving outward. **Local bloom only.** **Reference ratios**: dark sphere = grab radius, pop 1.7×, cloud 0.6× → 1.3× over ~6 s. |
| Visual pass 2 (superseded by the Reach remake) | After the first in-game look: draw quad **1.5× larger** (glows were cut at the quad edge). **Charge-up** = small white sphere at the carrier with a **vibrating surface**, violet halo and **lightning arcs**, building over the whole charge (point → `CORE_RADIUS` 0.08 R ≈ 80 su), plus a **thin violet ring at R** marking the grab radius; no dark tint, no web. **Activation** (fade start): it **expands rapidly to R** (`EXPAND` 0.3 s, video timing), then **clears like the reference**: the white thins through lavender mottling to a **pure black** body (slipspace is a dimension with no light) over `OPEN` 0.8 s while the white stays as the thick rim; the **lightning web comes in only after the expansion, as an overlay** on the black (like the hexes on the shield). **Exit seed mirrors the entry** (same core + ring, building toward the jump). |
| Reach game data | Remake the effect from Halo: Reach's own data (the official Reach editing kit's tags) instead of the video. Reach **textures/meshes may be used and shipped** if needed — non-commercial fan project, small audience; the developer accepts the takedown risk. |
| Activation (player) | System key **primes**; the aiming circles appear only while primed; **left click confirms**, **right click cancels**; must work while paused (developer 2026-10-01). |
| Startup trims | Drop the white flash going out and back in (Reach's arming flare); the grab-radius ring during the charge off for now (developer 2026-10-01). |
| Portal off the bow (2026-10-02) | The rupture opens **one ship length off the bow** (`PORTAL_AHEAD` 2 collision radii ≈ 660 su ahead of the CAS centre) and the **grab radius is centred on it**, not on the carrier (grabs ~1000 su ahead, ~340 su behind the stern). **Lurch**: during the 2 s fade the CAS physically eases into the portal centre (phased and fading like the cargo), reaching it at the jump; the CAS always comes along. **Exit**: the CAS lands at the destination = exit portal centre; cargo keeps its offsets from the portal centre. **Preview**: the entry grab ring off the bow is drawn too while aiming. `MIN_RANGE` is measured portal centre → destination. **AI** judges the circle off its current bow, no turning to aim. |
| Charge core (2026-10-02) | A **white sphere at the centre of the charge-up** (not in Reach): **the size of the star flare's bright core** so the flare's spokes reach past it (radius = flare half-size × 0.11, measured from star_flare.png; ≈ 12–18 su; the spokes reach ~3×), grown in from a point over the charge, eased out; **breathes** (radius ±8 %, brightness ±15 %, 1.6 s period); limb-darkened white kept below saturation (new sphere mode `CORE`) so the **charge energy and the heat-haze ripples stay visible over it** — the ripple pass now draws after the spheres; soft bloom building with the charge. At the fade it holds until the opening glow sphere outgrows it, then fades in 0.15 s. The exit seed grows the same sphere. First pass (80 su, flat saturated white, flicker) hid the energy and ripples: developer asked for half the size (40 su), breathing, and both visible on top; then for the flare-core size. (The developer considered switching the whole portal to the Halo 3: ODST look and kept the Reach one.) |
| Black body inside the white (2026-10-02) | Developer: in Reach the white ball grows and the darkness comes in *inside* it; ours grew a dark disc with the white only arriving later. Cause: the remake missed the multiply sphere's **angle fade** (range 25°, cutoff 10°), so the black covered the glow edge to edge until the glow's 10 % oversize at 1.1–1.4 s. Now ported (shader `BLACK` mode): the black fades out toward the limb, the glow stays a white rim around it from the first frame. |
| Burst stays white (2026-10-02) | Developer: the opening's white burst read off-purple. Cause: the black was alpha-blended *after* tone mapping, so 20–40 % black already dimmed the white to grey (purple under the web). Reach's multiply works on the HDR glow (intensity 10 → 5) before tone mapping, so the glow stays white while the black is thin. Now: `BLACK` multiplies (blend `ZERO, SRC_COLOR`) by `tonemap(glow × (1 − k)) / tonemap(glow)` over the glow, `1 − k` outside it. Expected: white ball (~0.1 s), light lavender (~0.2 s), lavender-blue (~0.3 s), blue-violet (~0.4 s), black by ~0.7 s; the collapse's shrinking core also comes back brighter as the black thins. |
| Black trails the white (2026-10-02) | Developer: the white burst should stay white; the real issue was the black ball growing as fast as the white (Reach: same scale curve), so the darkness covered the white from the start. Now the black body runs the **same scale and alpha curves delayed by `BLACK_DELAY` (0.3 s, placeholder)**: a white ball first, then the dark grows out from the centre inside it, fading in (Reach alpha, HDR multiply: lavender → blue → black inside the black only); the white keeps Reach's tint. At the collapse the black shrinks on the normal timeline (min of both curves), so it never outgrows the white. |
| Warp-in through small stuff (2026-10-02) | After the in-game test ("all good"): the CAS's exit is blocked only by **live ships** (stations and their modules included). **Fighters, missiles, hulks and asteroids never block**: at the warp-in, **hulks under the CAS are shoved** straight out from it to the nearest free spot (the rest of the cargo still nudges around hulks), and **asteroids under any arrival** (CAS and cargo, fighters and hulks included) **are destroyed** with vanilla's own death (engine damage path: explosion, removed). Arrivals no longer nudge around asteroids. |
| Friendly stations in the AI (2026-10-02) | A friendly station (the station itself, not its modules) counts as an ally: an anchor for pull-home, reinforce and regroup, and strength in a group (supplies to recover × hull level: 50 / 75 / 100 for vanilla small / medium / large; modules 0). Never carried, so it counts even inside the grab circle. |
| Enemy stations in the AI (2026-10-02) | "No-no for jumping next to, unless the allies around would have overwhelming presence": an enemy station counts against any group it sits in, like enemy ships, so the usual `PULL_MARGIN` test only passes next to one when the allies there overwhelm it. Its modules' weapons count in the survival test. |
| Flux/hull-aware AI: Reinforce + Hunt (2026-10-02) | Strength on **both sides** = supplies × hull level × (1 − flux × `FLUX_PRESSURE`); **overload/venting counts as no flux** (it is over by the time the jump lands). Two new plans after Strand, only when no enemy ships are in the grab circle: **Reinforce** — an allied group losing (allied/hostile < `REINFORCE_NEED` 1, hostiles ≥ `REINFORCE_MIN_THREAT`) that our arrival turns (`PULL_MARGIN`); most desperate first; lands **pincer** on the hostiles' far side if the CAS survives the hostiles around that spot (strand survival test), else **beside the allies facing the hostiles** to draw fire and score kills. **Hunt** — an enemy group weakened (≥ `HUNT_MIN_WEAKNESS` 0.25 of full strength lost) or holding a crippled ship (hull ≤ `HUNT_CRIPPLED_HULL` 0.3 or flux ≥ `HUNT_CRIPPLED_FLUX` 0.8, overload/vent not counted), worth ≥ `HUNT_MIN_VALUE` 15, beatable at `PULL_MARGIN`; score = full strength × (weakness + `HUNT_CRIPPLED_BONUS` 0.25 per crippled ship); lands beside the target away from the rest of its group. All placeholders. |
| Reach remake | **Faithful**: real particle systems and the three spheres, with Reach's textures, curves and timings (supersedes pass 2's procedural look; its *intent* carries over — charging sphere building over the whole charge, thin violet ring at R, expansion to R, white rim, web over black, exit mirrors the entry's charge). **Exit**: the same charge stream at the destination, then at the jump the glow sphere bursts to R as a white-violet flash and fades, with twinkles and a star flare; **no black sphere** at the exit. **Body pure black** (not Reach's near-black multiply palette). **Extras**: heat-distortion ring during the charge — yes; full-screen violet exposure flash — no (local only); debris meshes — no; remnant **capped near 1.3 R** (not Reach's ~2.2 R). |

Implementation calls made while building (in the approved plan): fighters never block a landing
spot; cargo ships cannot fire during the fade; rupture damage hits parent ships only (not modules,
not hulks); shots are not nudged; shots and asteroids are left alone until the jump (only ships are
phased during the fade — a projectile has no "phased" state, and `CollisionClass.NONE` on shots
inside the opaque sphere would only matter for shots flying out of it).

## What was built

### Data

- `data/shipsystems/covenant_remnants_fleet_slipspace_jump.system` (new): `STAT_MOD`, `aiType CUSTOM`,
  `aiScript ai.FleetSlipspaceJumpAI`, `statsScript systems.FleetSlipspaceJumpStats`,
  `runScriptWhileIdle true` (so the renderer and the destination preview exist before the first
  jump), `useSound system_phase_teleporter`.
- `data/shipsystems/ship_systems.csv`: new row — charge up 8, active 2, down 3, cooldown 60;
  noFiring/noTurning/noStrafing/noAccel/noVent TRUE, noShield empty. Icon: the drive's placeholder.
- `data/hulls/ship_data.csv`: supercap1 `system id` → `covenant_remnants_fleet_slipspace_jump`.
- `data/console/commands.csv`: `FleetJumpTune`.

### Mechanics

- **`systems/FleetSlipspaceJump.kt`** (new) — helper object: `SYSTEM_ID`, every tunable as a
  `Tunable` (name, default, live `value`, description) in a registry the console command reads;
  per-combat state (engine identity reset): AI→script `pendingDestination` (2 s lifetime), the
  `inTransit` identity set (two carriers never grab the same entity), the one render plugin. Shared
  helpers: `isGrabbableShip` (no stations, no modules, alive or hulk), `isCombatant`, `hasShieldUp`,
  `isInsideMap` / `clampIntoMap`, `isSpotBlocked` (live ships only — fighters, missiles, hulks
  and asteroids ignored since 2026-10-02), `portalCenter` (the
  portal off the bow: `PORTAL_AHEAD` collision radii along the facing; 2026-10-02), `currentCargo`
  (around the portal, plus the carrier), `destinationProblem` → `TOO CLOSE` (portal centre →
  destination < `MIN_RANGE`) / `OFF MAP` / `EXIT BLOCKED`.
- **`systems/FleetSlipspaceJumpStats.kt`** (new, `BaseShipSystemScript`) — `isUsable` and
  `getInfoText` both compute the problem live (`NO TARGET`, `FLUX TOO HIGH`, or the destination
  problem); timing overrides from the tunables, clamped to ≥ 0.1 s so the OUT frame the jump needs
  can never be skipped. `apply` (every frame, thanks to `runScriptWhileIdle`) ensures the per-combat
  renderer exists; on IN start it consumes the destination, sets the cooldown and starts a session;
  on OUT it tells the session to jump. Status lines via `getStatusData` ("slipspace rupture
  forming" / "everything in the rupture is phased" / "fleet jump complete").
- **`systems/FleetSlipspaceJumpSession.kt`** (new, `BaseEveryFrameCombatPlugin`, one per
  activation; registers and removes itself):
  - The portal centre is fixed at activation (`portalCenter`; the carrier cannot move or turn
    through the charge-up); the entry rupture and the grab radius are centred on it (2026-10-02).
  - IN: zeroes the carrier's velocity and turn every frame; drives the entry preview from the
    system's effect level; spawns the exit seed `EXIT_SEED_LEAD` s before the fade.
  - ACTIVE (fade): the carrier **lurches** into the portal — moved each frame (with its modules)
    from where it stood to the portal centre, eased in (progress²), reaching it at the jump (not in
    `VISUAL_ONLY`). Each frame, grabbable ships inside the radius around the portal (the carrier
    always, wherever the lurch has got it to) are **held**:
    themselves plus child modules `setPhased(true)` (prior state remembered), alpha fade to 0.4,
    purple jitter, `setHoldFireOneFrame` + `blockCommandForOneFrame(FIRE)`. Ships that leave are
    released. Another carrier's cargo is skipped.
  - First OUT frame (triggered by the stats script's `apply(OUT)`; the session's own state check is
    the backup) — the jump: the carrier is snapped to the portal centre (= origin); cargo = the
    carrier plus grabbable ships, `engine.projectiles` (missiles included) and asteroids inside the
    radius. Obstacles = live ships staying behind (no fighters), plus cargo as it lands. Carrier
    lands first (`findFreeSpot`: target if free and in-map, else rings × directions, else clamped
    into the map); `delta` = carrier's actual landing − portal centre. `shoveHulks`: hulks staying
    behind that overlap the carrier are pushed straight out from it to the nearest free spot; all
    staying hulks then become obstacles. Then ships (alive largest-first, fighters clamped only,
    hulks), asteroids, `destroyAsteroidsUnder` (asteroids staying behind under any arrival:
    `engine.applyDamage` lethal → vanilla explosion + removal), then shots translated by `delta`
    (`interruptContrail` around missile moves). Child modules move with their parent. Rupture damage,
    flux → max(current, 80%), everything released, jump sound, visual transitions.
  - Rest of OUT: arrival jitter on the cargo, then the session ends.
  - **Abort** (carrier dead/hulk, or the system state left IN/ACTIVE without reaching OUT): release
    everything in place, fade both ruptures out, end.
  - **Damage**: `hasShieldUp` false → `maxHitpoints × DAMAGE_HULL_FRACTION`. Non-lethal: hitpoints
    reduced directly (armor ignored) plus our own floating number (direct hull loss bypasses the
    damage pipeline). Lethal: hitpoints set to 1, then `engine.applyDamage(…, max(damage, 5 × armor
    rating + 10), OTHER, bypassShields, source = carrier)` so the kill is credited; the engine
    reports that hit itself. The CAS and fighters are included (developer decision).
- **`ai/FleetSlipspaceJumpAI.kt`** (new, `ShipSystemAIScript`), every `AI_INTERVAL` while ready, not
  overloaded/venting, flux below the gate. Strength = `suppliesToRecover × hullLevel × (1 − flux
  × FLUX_PRESSURE)` for both sides, overload/venting counted as no flux (2026-10-02; before, only
  allied groups had the flux discount); fighters `FIGHTER_STRENGTH`. Groups count ships and
  stations within `CLUSTER_RADIUS` that are not coming along; enemy stations count against them. "Inside the radius" = inside the circle around the portal off the current
  bow (2026-10-02; the AI does not turn to aim it); the regroup distance is measured from the
  carrier itself.
  1. **Pull home**: enemy strength in radius ≥ `PULL_MIN_THREAT`; for each allied non-fighter anchor
     ≥ `MIN_RANGE` away, cluster surplus = Σ allies within `CLUSTER_RADIUS` (not coming along) ×
     (1 − flux × `FLUX_PRESSURE`) + carried allies + the CAS − `PULL_MARGIN` × (enemies there +
     enemies carried); best surplus wins. Lands `radius + anchor radius + 100` from the anchor on the
     side away from the enemy centroid (8 bearings tried, each validated with `destinationProblem`).
  2. **Strand**: not already stranded; enemy CRUISER/CAPITAL with max speed ≤ `STRAND_MAX_SPEED`
     inside, total ≥ `STRAND_MIN_STRENGTH`; allied non-fighters inside all FRIGATE/DESTROYER;
     survival: Σ enemy weapons' `sustainedDps` × (cooldown + arrival) × `HIT_FRACTION` ≤
     (Covenant shield HP + regen × exposure × `REGEN_SHARE` + armor rating × `ARMOR_FACTOR` + hull
     above `MIN_HULL_AFTER`) / `SURVIVAL_SAFETY`, or every carried enemy ship slower than the CAS.
     Destination: 9×7 in-map grid (margin radius + CAS radius), the valid point farthest from
     everyone staying behind.
  3. **After a strand**: `BACK_OFF` + `DO_NOT_PURSUE` while enemy ships are within 1.5× radius;
     the stranded flag clears once none are within 2× radius; no second strand before that.
  4. **Reinforce** (2026-10-02): no enemy ships inside; allied anchors ≥ `MIN_RANGE` away whose
     group is losing (allied / hostile < `REINFORCE_NEED`, hostile ≥ `REINFORCE_MIN_THREAT`) and
     passes the surplus test with what we carry; lowest ratio wins. Landing: pincer — `radius + 100`
     beyond the hostiles' centroid along allies → hostiles (±30°, ±60° tried), valid and survivable
     (strand survival test vs. hostiles within `CLUSTER_RADIUS` of the spot); else beside the anchor
     facing the hostiles (8 bearings).
  5. **Hunt** (2026-10-02): no enemy ships inside; each non-fighter enemy ≥ `MIN_RANGE` away seeds
     a group (hostiles within `CLUSTER_RADIUS`); full ≥ `HUNT_MIN_VALUE`; weakness = 1 − strength /
     full ≥ `HUNT_MIN_WEAKNESS`, or a crippled ship in it (hull ≤ `HUNT_CRIPPLED_HULL`, or flux ≥
     `HUNT_CRIPPLED_FLUX` while not overloaded/venting); passes the surplus test; best score = full ×
     (weakness + `HUNT_CRIPPLED_BONUS` × crippled). Lands beside the target, away from the rest of
     its group.
  6. **Regroup**: no enemy ships inside; nearest allied anchor > `REGROUP_DISTANCE`; nearest anchor
     whose cluster passes the surplus test (carried enemies = fighters only) → land beside it.
  - Anchors and group strength include **friendly stations** (2026-10-02): the station itself
    (`isStation`, no `parentStation`), never carried. **Enemy stations** count against any group.
  - The survival test counts child modules' weapons (station modules, modular ships).
- **`commands/FleetJumpTune.kt`** (new, Console Commands; stays isolated in `commands/`):
  `FleetJumpTune` lists `name = value (default) - description`, `FleetJumpTune <name> <value>`,
  `FleetJumpTune reset [<name>]`.

### Player activation (prime → aim → confirm)

Developer request 2026-10-01: the system key no longer fires the jump. **System key = prime**: the
aiming circles (grab-radius ring + carrier footprint, green valid / red invalid) appear at the
cursor; **left click = confirm** (the system activates toward that point; ignored while the point is
invalid); **right click = cancel**. The system key while aiming does nothing. Works while paused
(the jump starts on unpause).

- **`plugins/FleetSlipspaceJumpTargetingPlugin.kt`** (new, one per combat, registered by the stats
  script through `FleetSlipspaceJump.ensureTargetingPlugin`) — all in `processInputPreCoreControls`
  (runs every frame, paused too, before the ship controls; consumed events never reach them):
  key-down of the `SHIP_USE_SYSTEM` binding (via `getControlStringForEnumName`, F if it is not a
  keyboard key) primes when the system is idle, off cooldown, not overloaded/venting; while primed
  the aim point follows `Settings.getMouseX/Y` → world (the ship's mouse target is not updated while
  paused), FIRE and TOGGLE_SHIELD_OR_PHASE_CLOAK are blocked so the clicks do not shoot or toggle
  the shield, LMB/RMB events are consumed; confirm stores the point
  (`FleetSlipspaceJump.confirm`, expires after 2 s of game time) and calls `useSystem()`, re-asked
  each unpaused frame until the system leaves IDLE. Every frame without a confirmed point the
  engine's own `USE_SYSTEM` command is blocked for the player's CAS, so the key cannot start the
  system directly. Priming is dropped when the player is no longer flying the CAS manually or the
  system stops being ready.
- **`FleetSlipspaceJump`** — primed ship, `aimPoint`, confirmed destinations (per combat);
  `destinationFor(player)` = the confirmed point, else the aim point while primed.
- **`FleetSlipspaceJumpStats`** — player idle and not aiming counts as ready (the widget shows
  READY); while aiming the widget shows the aim point's problem (TOO CLOSE / OFF MAP / EXIT
  BLOCKED / FLUX TOO HIGH); IN consumes the confirmed point and drops any priming.
- **Render plugin** — the aiming circles only while primed, drawn at `aimPoint`; since
  2026-10-02 also the entry grab ring around the portal off the bow (follows the facing while
  aiming), same validity colour.

AI ships are unchanged (they hand their destination over and press the system themselves).

### Sounds (Halo: Reach audio)

Developer request 2026-10-01 ("pull some sound effects… that make sense"). Source: the editing kit's
FMOD bank `HREK\fmod\pc\sfx.fsb` (FSB5, 16 162 Vorbis samples; names in `sfx.fsb.info`), decoded
with vgmstream-cli (`-s <index + 1>`), cut/mixed to mono with `tools/make_sounds.py`, encoded to
44.1 kHz Vorbis by ffmpeg. Files in `sounds/fleet_slipspace_jump/`, ids in `data/config/sounds.json`.

| Moment | Sound id | Source (FSB index) |
| --- | --- | --- |
| Prime (aiming circles appear; UI sound, plays paused too) | `covenant_remnants_fleet_jump_prime` | concussion rifle power-up `concrifle_powerup` (7842) |
| Charge-up: hum at the carrier, pitch 0.7 → 1.25 and volume 0.35 → 1 with the charge; the exit seed hums the same at 70% volume until the jump | `…_charge_loop` (`playLoop`) | m45 sound scenery `covenant_power2_1` (11881), 3.1 s loop |
| Fade start (the rupture opens) | `…_open` | m45 ending foley `m45_jorge_foley_fr0` (11674) 83.40–86.15 s — the real opening swell |
| Jump, where the fleet left | `…_collapse` | same foley 86.20–92.20 s: falling sweeps, boom at +0.85 s, tail |
| Jump, where the fleet lands | `…_arrival` (3 variants) | Seraph slipspace jumps `seraph_slip_space1–3` (12144–12146) |

The cut points come from the cinematic timeline (scene 010 = 1756 frames, 020 = 300, 030 shot 1 =
359; `slipspace_event` at frame 2515 = 83.83 s) checked against the foley's loudness/spectrogram
(`reach-data/audio/foley_zoom.png`). The foley has no charge whine before the rupture, hence the
power hum for the charge. The `.system`'s `useSound` (vanilla phase teleporter) is gone; the hum
starts the charge. Hum pitch/volume constants are in `FleetSlipspaceJumpSession`.

### Visual (Reach remake)

The rupture is a remake of the Reach slipspace bomb (see "Reach game data" above). The two
procedural passes before it (a composed one-quad shader: cloud/sphere/arcs/lightning/rim/spikes/
flash layers) were removed.

- **`utils/render/rupture/RuptureEffect.kt`** (rewritten) — per-rupture timeline in world units
  (Reach units × grab radius / 625). Entry: **CHARGE** — `slipspace_secondary`'s 3 s stretched over
  the charge-up (input = the system's charge level): charge stream (start 2, rate curve 0 → ~12/s
  → dip → 20/s; life 0.2–0.5 s; size curve 35–72 by the charge level × 0.1 → 1 over life; alpha
  1 → 0; black point after half-life; intensity 7; random palette row), heat-haze rings (rate
  5 → 10/s, life 0.8–1.5 s, size 50–400 by the charge × 0.1 → 1.25, alpha 0 → 0.5 → 0), arming
  flare (`large_flare`, radius spike to 906), star flare (`star_flare`, 100 → 70, flicker 2.3–7.3),
  flickering blue glow (Reach's charge light), thin violet ring at R. **OPEN** (fade) —
  `slipspace_event` 0..2 s (held at 2 s if FADE is longer): glow sphere (size × scale curves,
  salmon → blue-violet, intensity 10 → 5, local bloom halo), black body (alpha curve 0 → 1 over
  0.65 s, pure black, Reach's angle fade 10°/25° so it stays inside a white rim), web (scale curve), violet rupture glow from 0.37 s; charge extras fade over
  0.3 s. **COLLAPSE** (from the jump, event clock 2 s on): 150 sparks on the 600 shell (inward
  streaks, life 0.8 s), spheres shrink to 0 by 3 s; burst at 2.5 s: blast shell + plasma shell
  (6 s, × `REMNANT_SCALE`), 10 + 7/s plasma clouds, 50 + 15/s plasma flames, 120 + rate-curve
  twinkles and the lingering twinkles, 30 fiery smoke puffs, the haze; collapse flare 2.6–3.6 s
  (+0.4 s fade). Exit: **SEED** (the same charge; level = progress from spawn to the jump) →
  **FLASH** (glow curve's first 0.75 s played in 0.3 s so it covers R, then fades over `DISSOLVE`;
  twinkles; star flare using the arming flare's radius spike). `abort()` fades the last frame through
  `master`. Draw lists: `sprites`, `spheres` (light, shell, plasma shell, glow, black, web, ring),
  `flares`.
- **`ReachCurves.kt`** (generated, 44 curves at 33 samples, ManagedBlam evaluation) and
  **`ReachCurve.kt`** (linear lookup; colour variant).
- **`RuptureParts.kt`** — `NoiseTransform` (Reach's animated texture scale + looping translation),
  `SpriteMaterial` (Reach particle shader → sprite mode, blend, textures, transforms, warp),
  `SpriteKind`, `RuptureSprite`, `SphereMode`, `SphereDraw`, `FlareDraw`.
- **`RuptureTexture.kt`** — the 21 Reach bitmaps (`graphics/covenant_remnants/fx/slipspace_rupture/`, registered in
  `data/config/settings.json` under `covenant_remnants`, ids `rupture_*`); noise maps tile, palettes
  and shapes clamp; linear filtering; set once on first bind.
- **`data/shaders/rupture/ruptureSphere.vert/.frag`** ↔ **`RuptureSphereShader.kt`** — analytic
  spheres, one quad each: GLOW (tone-mapped HDR colour + bloom halo, alpha blend), BLACK (alpha
  blend), WEB (palettized plasma on the sphere's exact UVs, side view, pole mask; additive), SHELL
  (beam band seen from the pole = soft ring; additive), PLASMA_SHELL (additive), RING, LIGHT
  (centre-weighted glow).
- **`data/shaders/rupture/ruptureSprite.vert/.frag`** ↔ **`RuptureSpriteShader.kt`** — CPU-built
  quads (14 floats per vertex), one draw per material per rupture: PLASMA (palettized 2D plasma,
  sphere warp, uv zoom, black point), TEXTURE_ADD, SMOKE, TEXTURE_ALPHA (twinkles, 2×2 atlas),
  DISTORTION (offsets a copy of the screen by the ring map's RG).
- **`RuptureRenderer.kt`** (LemonLib `Shader`) — builds both programs lazily on the GL thread;
  order: heat haze (one `glCopyTexSubImage2D` of the viewport per frame while rings exist), then per
  rupture spheres → additive sprites (smoke, plasma clouds, flames, charge stream, sparks) → flares
  → twinkles; saves/restores program, VAO, VBO, active texture and units 0–4.
- **`RuptureProgram.kt`** — plain vertex + fragment program with cached uniform locations.
- HDR: Reach's intensities (5–12) are tone-mapped, `1 − exp(−c · EXPOSURE)`.
- **`plugins/FleetSlipspaceJumpRenderPlugin.kt`** (one per combat, layer
  `ABOVE_SHIPS_AND_MISSILES_LAYER`) — owns the renderer and the live effects (advanced here, DONE
  ones dropped). Player-only destination preview: grab-radius ring + carrier footprint at the mouse
  while the system is idle, green if the jump is valid, red if not.

Startup trims (developer 2026-10-01): Reach's arming flare (`slip_space_secondary.lens_flare`, a large
flare spiking out to ≈ 1.45 grab radii and back — the "white flash going out and back in") is left
out, and its `large_flare` texture dropped (21 textures shipped); the thin violet ring at the grab
radius during the charge is off by default (`CHARGE_RING 1` turns it on).

Not in the tags (stand-ins, named constants in `RuptureEffect`): the glow sphere's bloom halo, the
light glows' strength, spark streak length, flame aspect, twinkle drag (keeps them near the
remnant), the shells' fade over their last 15%, the collapse flare's 0.4 s fade (Reach's event cut
it off at full brightness), and the exit flash timing.

### Visual-only mode (dev toggle)

Developer request 2026-10-01: while the look was being dialled in, activating the system only played
the rupture animation. Tunable `VISUAL_ONLY` (frozen per activation); the developer set its default
to **0** (full jump) on 2026-10-01 — `FleetJumpTune VISUAL_ONLY 1` brings it back:

- Plays the full timeline: charge stream, heat haze, flares and ring at both ends during
  charge-up, the open (glow sphere → black body with web) during the fade, entry collapse → burst
  → remnant and the exit flash at the jump, jump sound.
- Nothing else: no cargo is held or phased, nothing is moved (the CAS stays where it is), no rupture
  damage, no exit flux. The `.system` locks still apply during charge-up (no thrust, turning,
  firing or venting) and the carrier is still held in place, as in the real jump.
- Usability is unchanged (destination ≥ MIN_RANGE, in map, landing spot clear, flux below the gate),
  so the exit rupture always has a sensible spot. The player status line reads
  "visual only: nothing jumps".
- The AI does not use the system while it is on (it would believe it had jumped).
- `FleetJumpTune VISUAL_ONLY 0` restores the full jump; `FleetJumpTune COOLDOWN 1` speeds up
  replays (applies at the next activation); CHARGE/FADE/ARRIVAL apply from the next battle.

## Files touched

- `data/shipsystems/covenant_remnants_fleet_slipspace_jump.system` (new), `data/shipsystems/ship_systems.csv`,
  `data/hulls/ship_data.csv`, `data/console/commands.csv`
- `data/shaders/rupture/` (new: `ruptureSphere.vert/.frag`, `ruptureSprite.vert/.frag`; the
  procedural `rupture.vert`, `base.frag` and `*.glsl` layer modules of passes 1–2 were removed)
- `graphics/covenant_remnants/fx/slipspace_rupture/` (new: 21 Reach bitmaps, 0.7 MB; `large_flare.png` removed with
  the arming flare), `data/config/settings.json` (their sprite ids)
- `src/.../systems/FleetSlipspaceJump.kt`, `FleetSlipspaceJumpStats.kt`, `FleetSlipspaceJumpSession.kt` (new)
- `src/.../ai/FleetSlipspaceJumpAI.kt` (new)
- `src/.../commands/FleetJumpTune.kt` (new)
- `src/.../plugins/FleetSlipspaceJumpRenderPlugin.kt` (new), `FleetSlipspaceJumpTargetingPlugin.kt` (new)
- `data/config/sounds.json` (new), `sounds/fleet_slipspace_jump/` (new: 7 Reach-derived OGGs, 0.3 MB)
- `src/.../utils/render/rupture/` (new: `RuptureEffect.kt`, `ReachCurves.kt` (generated),
  `ReachCurve.kt`, `RuptureParts.kt`, `RuptureTexture.kt`, `RuptureSphereShader.kt`,
  `RuptureSpriteShader.kt`, `RuptureRenderer.kt`, `RuptureProgram.kt`; the pass 1–2
  `utils/render/RuptureShader.kt` and its 8 layer classes were removed)
- `assets/reference/long-night-of-solace/` (new; reference frames and `reach-data/`, not shipped)
- Docs: `planning/TODO.md`, `planning/PLAN.md`, `planning/ARCHITECTURE.md`, `planning/DOCUMENTATION.md`
  (also fixed two dead links: `design document.txt` → `design_document.md`, `balance.txt` → `balance.md`)

Untouched: `SlipspaceDrive.kt`, `SlipspaceDriveRenderPlugin.kt`, `SlipspaceTuning.kt`, LemonLib.

## Placeholder numbers (all live-tunable)

Mechanics: `GRAB_RADIUS 1000`, `PORTAL_AHEAD 2` (collision radii; ≈ 660 su on the CAS), `MIN_RANGE 2000`, `FLUX_GATE 0.7`, `EXIT_FLUX 0.8`, `CHARGE 8`, `FADE 2`,
`ARRIVAL 3`, `COOLDOWN 60`, `DAMAGE_HULL_FRACTION 0.10`, `NUDGE_RINGS 10`, `NUDGE_DIRECTIONS 12`.
AI: `AI_INTERVAL 0.75`, `FIGHTER_STRENGTH 1`, `PULL_MIN_THREAT 15`, `CLUSTER_RADIUS 1500`,
`PULL_MARGIN 1.5`, `FLUX_PRESSURE 0.5`, `STRAND_MAX_SPEED 60`, `STRAND_MIN_STRENGTH 15`,
`HIT_FRACTION 0.5`, `MIN_HULL_AFTER 0.4`, `SURVIVAL_SAFETY 1.25`, `ARMOR_FACTOR 1`, `REGEN_SHARE 0.5`,
`REGROUP_DISTANCE 4000`, `REINFORCE_MIN_THREAT 15`, `REINFORCE_NEED 1`, `HUNT_MIN_VALUE 15`,
`HUNT_MIN_WEAKNESS 0.25`, `HUNT_CRIPPLED_HULL 0.3`, `HUNT_CRIPPLED_FLUX 0.8`, `HUNT_CRIPPLED_BONUS 0.25`.
Visual (timings, sizes and colours come from Reach; these scale or bridge it): `EXIT_SEED_LEAD 2`,
`CHARGE_RING 0`,
`DISSOLVE 0.6`, `REMNANT_SCALE 0.6` (burst shell ends ≈ 1.3 R; 1 = Reach's ≈ 2.2 R),
`SPRITE_SCALE 1`, `PARTICLE_DENSITY 1`, `EXPOSURE 1`, `DISTORTION 1`, `BLACK_DELAY 0.3` (the
black body's growth and fade-in start this long after the white sphere's). The pass 1–2 tunables
(`PREVIEW_ALPHA`, `CORE_RADIUS`, `EXPAND`, `OPEN`, `COLLAPSE`, `POP`, `POP_SCALE`, `CLOUD_START`,
`CLOUD_END`, `CLOUD_LIFE`) are gone with the procedural shader.

## Verification

- `.\gradlew.bat buildMod`: builds clean (LemonLib via the composite build).
- `glslangValidator` (VS Code GLSL extension copy) passes on the vertex shader and on the assembled
  fragment shader in every configuration: all layers, each layer solo, each layer stubbed, all
  stubs (script: assemble like `ComposedShaderProgram` — marker replaced by modules in order, stubs
  from the layer classes).
- Code review (subagent, 2026-10-01): no state-machine, GL-lifecycle, uniform-name or bounds
  defects. It flagged the CAS and fighters taking rupture damage (intended: developer decision) and
  a misleading floating number on lethal hits (fixed: the engine reports those). Own re-check found
  the destination preview missing until the first jump of a battle (renderer was created on first
  activation) — fixed with `runScriptWhileIdle`.
- In-game 2026-10-01 (developer, first try): the system showed `NO TARGET` and could not be used.
  Cause: the player check used `engine.isUIAutopilotOn`, which is inverted (see research), so the
  player was treated as AI and the mouse cursor was never read. Fixed: player = player ship with no
  AI. The destination is the mouse cursor (a point, no ship target needed). Re-test pending.
- Visual pass 2 (2026-10-01): builds clean; `glslangValidator` passes in all 19 configurations
  (arcs layer added). Frames of the reference re-checked first (84–91 small vibrating sphere with a
  violet halo, 92–96 white-out, 97–102 white sphere growing, 103–115 interior clearing to dark with
  the rim staying white). **Not yet seen in-game.**
- Reach remake (2026-10-01): `glslangValidator` passes on all four shaders; builds clean. Offline
  check: `RuptureHarness.java` runs the real `RuptureEffect` headless (35 sampled frames: charge,
  open, jump, exit) and `render_frames.py` draws its draw lists with the real shaders and shipped
  textures (moderngl); sheets in `assets/reference/long-night-of-solace/reach-data/preview/`. Charge
  core reads as the video's crackling white sphere; open = white sphere → black body with the
  violet web and a white rim at ~1–1.5 s; collapse = rim sparks, shrink to a white core, salmon pop,
  violet veined remnant; exit flash covers R by 0.3 s and dissolves. Two fixes from that check: the
  violet light was a flat disc (now centre-weighted), the exit flash only reached ~0.6 R (now the
  glow curve's first 0.75 s plays in 0.3 s). Own re-read: the exit flash's star flare was being
  overwritten by the fading seed extras (fixed: extras first). Code review of the GL side
  (subagent): uniform names/types, vertex layout, texture ids vs `settings.json` all match; one
  defect — on the first frame of a combat the programs were built before the GL state capture, so
  the restore reset bindings to 0 (fixed: capture first, then build). **Not yet seen in-game.**
- In-game 2026-10-01 (developer, first look at the remake): looks right; asked to drop the startup
  flash (Reach's arming flare) and the charge ring, and reported that the system could not be used
  while paused. Changed: startup trims; new prime → aim → confirm activation (see "Player
  activation"), built clean, timeline harness re-run. **Activation flow not yet tested in-game.**
- In-game: rest of the checklist **not yet tested**.

### In-game test checklist (developer)

Setup: campaign → console `CovenantGiveAll` → fit a CAS (std variant works) → sim or a real fight
with allies and enemies; or AI-vs-AI in the sim with the CAS on either side.

**Visual-only mode is off by default now** (the full jump runs); `FleetJumpTune VISUAL_ONLY 1` plays
only the animation (items 1–3 and the look in 4).

1. **Activation.** Player CAS idle: widget shows `READY` (or `FLUX TOO HIGH` above 70% flux); no
   circles. System key → aiming circles (grab radius + carrier footprint) at the cursor, plus the
   entry grab ring one ship length off the bow (turns with the ship), green/red;
   widget shows `TOO CLOSE` near the ship, `OFF MAP` past the border, `EXIT BLOCKED` over a live
   ship that isn't inside your 1000 su ring (wrecks, asteroids, fighters and missiles never block:
   aim on a wreck — it gets shoved aside; on asteroids — they explode as the fleet lands). Left click on red: nothing. Left click on green: the jump
   starts toward that point, no shot fired. Right click while aiming: circles gone, shield not
   toggled. Repeat the whole thing while **paused**: aiming and confirming work; the jump starts on
   unpause. Autopilot on while aiming: aiming drops.
2. **Charge-up.** After confirming: CAS stops and can't turn/fire/vent; **Covenant shield stays
   up**; a **white sphere** the size of the star flare's core grows at the centre (the flare's spokes
   reaching past it; breathing, soft bloom) with the
   crackling blue energy visible on it and the heat-haze ripples warping it, **one ship length off
   the bow**, with a
   blue star flare and glow, no big white flash; heat-haze rings ripple outward (ships behind them
   bend); no ring at the grab radius (`CHARGE_RING 1` to see it); the same charge appears at the
   destination ~2 s before the fade. Ships inside can still fly out.
3. **Fade (2 s).** The white core is swallowed as a white sphere grows to the ring (~0.75 s) going
   salmon → violet-white; a white ball first, then ~0.3 s later the darkness grows out from the
   centre **inside** it (lavender, then blue, then pure black) while the edge stays a white rim, with the violet lightning web on
   top; the rim thickens around ~1–1.5 s;
   the CAS lurches forward into the sphere's centre, accelerating, and reaches it at the jump;
   everything inside (allies, enemies, fighters, the CAS) is phased, faded and jittered, cannot fire,
   and shots pass through it. Enemies that walk out during the fade are released.
4. **Jump.** Entry: sparks streak inward at the rim, the sphere shrinks to a white core, salmon-pink
   pop and glow, then a violet veined shell (~1.3 R) and drifting plasma wisps with tiny twinkles
   for several seconds. Exit: a white-violet sphere bursts to the ring in 0.3 s and dissolves to
   reveal the arrivals in formation with velocities/facings kept (the CAS at the exit centre,
   the cargo at its offsets from the entry portal's centre), with twinkles; enemy missiles keep
   tracking; nothing stuck inside another ship or off map; blocked spots nudged (try jumping onto a
   crowd). Sound at the jump.
5. **Damage.** Ships with shields down lose 10% max hull (floating purple number), shielded ones
   don't; fighters take it; the CAS takes it if its shield is broken; a ship at < 10% hull dies and
   the kill is credited to the CAS.
6. **Costs.** CAS flux 80% after the jump; cooldown 60 s; Slipspace Tuning on supercap1 changes
   nothing (it never targeted this system).
7. **Abort paths.** Kill the CAS mid-charge and mid-fade (console or overwhelming fire): nothing
   stays phased or faded; both ruptures fade out cleanly. Also check an EMP/overload interruption.
8. **Tuning.** `FleetJumpTune` lists values; `FleetJumpTune GRAB_RADIUS 1200` applies to the next
   jump (preview ring too); `FleetJumpTune EXPOSURE 1.5` / `SPRITE_SCALE 2` / `REMNANT_SCALE 1` /
   `DISTORTION 0` change the look from the next frame; `FleetJumpTune reset`.
9. **AI.** AI-controlled CAS: pull-home only toward an allied cluster that can take the enemies
   (lands on the far side of the cluster); strand with slow enemy heavies when no cluster can — it
   survives, backs off, and regroups once clear (log lines
   `fleet jump: PULL_HOME/STRAND/REINFORCE/HUNT/REGROUP`); never strands twice in a row; never
   carries a cruiser+ ally on a strand. Out of a fight: jumps to an allied group that is losing
   (pincer behind the enemies when that is survivable, else beside the allies facing them) or onto
   a weakened enemy group (crippled / high-flux / low-hull ships), but never next to an enemy station
   unless the allies there overwhelm it; a friendly station counts as allies.
10. **Two CASes** (both sides, or two allied): overlapping radii — each entity jumps with one carrier
    only.
11. **Second battle in a row** with frigate1/frigate3/destroyer2: the drive's portal still renders
    (open drive TODO; fix as a bug if not).
12. **Sounds.** Prime blip when the circles appear (also paused); a hum that rises through the
    charge at the carrier and, ~2 s before the fade, at the destination; the opening swell at the
    fade; at the jump the collapse sweeps and boom where the fleet left and a slipspace whoosh where
    it lands. Check the mix against weapons/explosions (volumes in `sounds.json`).
13. **Performance.** Watch the frame rate during the burst (~2000 sprites for ~15 s) and the charge
    (one screen copy per frame for the heat haze); `PARTICLE_DENSITY` / `DISTORTION 0` if needed.

## Open items

- Curve audit (2026-10-10, [reach-curve-audit.md](reach-curve-audit.md)): `FLAME_ASPECT` 2 (tag `plasma_vertical` aspect 1.5→3) and
  `SPARK_STRETCH` 6 (tag charge-spark aspect 15) differ from the tags — kept as they are by developer
  ruling. `ReachCurves.kt` regenerates byte-identical from a patched re-dump.
- **Reach remake: first in-game look.** Built and checked offline only. Things the offline check
  cannot settle: Starsector's texture row order (the flame sprites assume the bitmap's top row is
  v = 0 — flames pointing inward would mean it is flipped), how the tone-mapped HDR reads over
  real backgrounds (`EXPOSURE`), the size of the charge core and twinkles at normal zoom
  (`SPRITE_SCALE`), heat-haze strength, and frame rate.
- Reach parts left out by decision: debris meshes, the full-screen exposure flash, Reach's
  near-black multiply palette (body is pure black). The secondary anamorphic/prismatic flares were
  never shown in Reach either (their event delay is 385 s).
- In-game test + tuning pass (all numbers are placeholders; visual look vs. the reference frames).
- Timing tunables (CHARGE/FADE/ARRIVAL) only apply from the next battle (engine reads overrides at
  ship creation).
- The rupture damage model is a ready candidate for the drive's open "damage non-shielded ships"
  TODO.

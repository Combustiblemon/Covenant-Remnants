# Implementation — Slipspace Drive blink (frigate1 / frigate3 / destroyer2)

The single-ship slipspace drive (`covenant_remnants_slipspacedrive`) rebuilt as a short-range blink
"like the Wolf's Phase Skimmer", with entry/exit visuals after the Halo 3: ODST carrier jump.
Replaces the old STAT_MOD drive (8 s chargeup, 1500 su launch through a 14 s portal, flux gate, exit
clearance check, 80 % flux on exit, `aiType NONE`).

## Research (starsector-knowledge, 0.98a-RC8)

- Wolf's system is `displacer` ("Phase Skimmer"): type `DISPLACER`, 300 su along the direction of
  travel, 3 charges, 1 charge / 10 s, 0.25 s in / 0.25 s out, shield drops on use. Hyperion's
  `phaseteleporter`: type `TELEPORTER`, 1500 su toward the targeted point, 0.5 s in / 0.5 s out,
  15 s cooldown, vulnerable during chargeup.
- Both are the engine's teleporter class (`combat/systems/int`, extends the base system `F`): the
  destination is computed in `useSystem()` (DISPLACER: velocity/facing × range; TELEPORTER: the
  AI's `targetLoc`, else the mouse target, clamped to `range`, then nudged to a clear spot), the
  ship is moved in `systemActivated()` — i.e. at the **end of the chargeup** — and the ship is
  phased (untargetable) through chargeup/chargedown unless `vulnerableChargeup/Chargedown`.
  `renderCopyDuringTeleport` drives the engine's own fading ghost (alpha + `setCopyLocation`).
- A `statsScript` runs on any system type (read in `F`'s constructor), so a TELEPORTER system can
  carry a script for visuals. `ShipSystemAPI.getTargetLoc()` exposes the AI's target.
- Charge regen scales with `MutableShipStatsAPI.systemRegenBonus` (`F.updateValuesFromStats`).
- Ship sprites: `Sprite.renderAtCenter` draws the quad from (0,0) to (w,h) with the hull centre at
  (`centerX`,`centerY`) measured from the sprite's bottom-left, rotated by the sprite angle
  (facing − 90°); texcoords map linearly, so y = 0 is the stern and y = h the bow.

## Reference (`Halo_3_odst_carrier_jump.mp4`, 12.3 s, 30 fps, frames in the session scratchpad)

ODST opening, the assault carrier over New Mombasa: at ~0.8 s violet energy with blue-white
lightning arcs gathers at the bow; the band sweeps bow → stern over ~1.9 s, the hull lights
glowing behind it; at ~3.5 s a small bright sphere forms just ahead of the bow and grows into the
portal (blue-white, bright rim) while the carrier accelerates into it; ~6.8 s white flash and a
shockwave (left out per ruling).

## Decisions (developer, 2026-10-02)

| Topic | Ruling |
| --- | --- |
| Direction | Toward the cursor / AI target, short range (engine `TELEPORTER`), not along travel |
| Range | 500 su (slightly above Wolf's 300, per the balance rule) |
| Charges | 3, one per 10 s (as Wolf) |
| Restrictions | None: no flux gate, no exit clearance check, no flux cost |
| Timings | 1.5 s chargeup (ripple + portal start together for 1.0 s, then 0.5 s lurch), 0.5 s chargedown, no cooldown, not vulnerable during chargeup |
| Entry | Portal opens one ship length off the **bow** (along the facing) and the ship slides in nose first. First pass put it toward the destination; a sideways jump then slid the ship into it sideways (developer 2026-10-02) |
| Exit | Small portal at the landing point, the ship slides out of it nose first (along its current facing), portal closes; no flash / after effects |
| Portal size | Collision radius × 1.3 |
| Hull ripple | Band + lightning arcs over the hull sprite (first pass) → **sparks + plasma puffs + bow flash after the ODST tags** (same day) |
| Portal look | Placeholder disc (first pass) → **ODST-faithful via the rupture renderer** (same day, once `H3ODSTEK` was extracted) |
| Slipspace Tuning | −30 % cooldown + 85 % flux gate → **+30 % charge regen** |
| AI | `PHASE_TELEPORTER_2` (vanilla Hyperion teleport AI) |

## Halo 3: ODST game data (H3ODSTEK, extracted 2026-10-02)

Kit: `E:\SteamLibrary\steamapps\common\H3ODSTEK`. ManagedBlam is unusable there (every `Start`
overload throws NotImplemented; `Initialize` wants a Project Chooser the kit does not ship), so the
tags were read with `tool.exe print-tag-to-xml <absolute tag path>` and summarized with
`assets/reference/odst-carrier-jump/odst-data/tools/xmlsum.py` (function curves are 32-byte
base64 blobs: header `01 24` constant / `01 25` ranged, then floats; colours as `#aarrggbb`).
Bitmaps cannot be exported from this kit (`export-bitmap-tga/dds` → "failed to decompress
[0x80070057] invalid", every file the same placeholder), but nothing new was needed — see below.
Summaries and XML under `assets/reference/odst-carrier-jump/odst-data/` (gitignored).

- **Cinematic:** `tags\cinematics\c100_intro\c100_05.cinematic_scene`. Shot 1 fires
  `radiation_flash.effect` + `radiation_crackle.effect` on the carrier (`cap_ship`) at frame 350;
  shot 2 fires `slipspace_sphere.effect` on `slipspace_fx_marker` at frame 1 and `tether_blast`
  at 180; shot 3 `pod_crashing`. All under `tags\fx\cinematics\c100_intro\slipspace_jump\`.
- **Hull ripple** (`radiation_flash.effect`): event `ripple` (1 s) emits from the carrier hull point
  cloud `fx\emitter_shapes\cov_capital_ship\cov_capital_ship.particle_emitter_custom_points`
  (374 points ordered bow → stern — that ordering *is* the sweep): `radiation_energy` (200/s,
  life 0.25–0.5 s, velocity 40–50 along the normals, size 25–40, tint `#2141e3` through the
  `fire_01` palette = blue-white sparks) and `radiation_plasma` (50/s, life 0.1–0.35 s, size 30–40,
  `plasma_clouds_a/b` + `thick_smoke_large` through the `plasma_clouds_1` palette, intensity 2–5 =
  violet-blue puffs). Event `flash` at 2.5 s: `radiation_flash` (soft flash, size 75, 0.65 s) at the
  bow marker + `distortion_bubble` ×5 (white → `#2d47da`). `radiation_crackle.effect`: the same
  plasma puffs at LOD rate for 15 s (life 1.5–2 s, scale 0.85–1).
- **Portal** (`slipspace_sphere.effect`, build event 0–6 s; collapse at 6 s and explosion /
  shockwave at 6.5 s skipped per ruling): particles living 6 s with scale = age (linear growth,
  bounding radius 282): `slipspace_sphere` (white mesh sphere, grayscale palette, fades edge-on,
  tint black → white), `slipspace_sphere_glow` ×2 (`#0e31f9`, intensity 40, in front and behind),
  `slipspace_sphere_origin` (`#2e30ff`, origin bitmap), `slipspace_sphere_outer_plasma`
  (`plasma_clouds01/02` + `slipspace_sphere_alpha` through the `plasma_clouds` palette — the same
  bitmaps the mod ships from Reach — tint white → `#2d47db`, intensity 1–3), `glow_soft` (tint
  `#243ad5`, intensity 1–4), lens flares `slipspace_build` (colour 0.27/0.36/0.98, radius 700 → 200)
  and `slipspace_build_anamorphic` (0.09/0.21/1.0). `portal_enter_flare` at 6.25 s.

## What was built

### Data

- `data/shipsystems/covenant_remnants_slipspacedrive.system` — `TELEPORTER`, `PHASE_TELEPORTER_2`,
  `range 500`, `renderCopyDuringTeleport false` (the mod draws its own ghost), not vulnerable,
  violet jitter, placeholder `useSound system_phase_teleporter`, stats script kept.
- `data/shipsystems/ship_systems.csv` — 3 uses, regen 0.1, in 1.5 / active 0 / out 0.5, cooldown 0,
  locks unchanged (no fire/turn/strafe/accel/shield/vent during the blink), tag `movement`, icon
  `teleport.png` (was flare launcher).
- `data/strings/descriptions.csv` — new `SHIP_SYSTEM` row (codex / tooltip; the mod had none).
- `data/hullmods/hull_mods.csv` — Slipspace Tuning text: "Increases slipspace drive charge
  regeneration by %s."

### Mechanics / script

- `systems/SlipspaceDrive.kt` (rewritten) — `BaseShipSystemScript`, visuals only. IN: on the first
  frame records the origin and the bow direction (the facing; turning is locked through the
  chargeup), adds the entry portal (centre = origin + dir × collision radius × `PORTAL_AHEAD_MULT` 2,
  radius × `PORTAL_RADIUS_MULT` 1.3, opening over chargeup × `LURCH_AT` ⅔) and the hull ripple
  (same duration). From `LURCH_AT`: real ship `extraAlphaMult 0`, ghost (`setCopyLocation`) slides
  from the origin to the portal centre with ease-in, fading over the last `LURCH_FADE_SHARE` 0.4.
  First ACTIVE/OUT frame (the engine has moved the ship): closes the entry portal, cancels the
  ripple, opens the exit portal at the landing point (`EXIT_OPEN_DURATION` 0.15 s). OUT: ghost
  slides out from `EXIT_START_INSIDE_MULT` 1 × radius inside the portal to the ship with ease-out,
  along the ship's current facing (the engine turns it toward its target during the chargedown),
  while the real ship fades in. `unapply`: alpha 1, copy cleared, portals closed. Per-combat reset
  on engine change. Status lines "slipspace translation" / "emerging from slipspace". The
  one-per-combat render plugin is registered through the companion (`renderPlugin(engine)`), same
  pattern as `FleetSlipspaceJump`.
- `hullmods/SlipspaceTuning.kt` — `systemRegenBonus ×1.3` (drive-system ships only); `FLUX_GATE`
  and the cooldown constant removed.

### Rendering

- `plugins/SlipspaceDriveRenderPlugin.kt` (rewritten) — one per combat; owns every live
  `BlinkPortal` / `BlinkRipple`; portals at `BELOW_SHIPS_LAYER` (the ghost slides over them),
  ripples at `ABOVE_SHIPS_LAYER`; pauses with the engine.
- `utils/render/blink/BlinkEffects.kt` — `BlinkPortal` (OPENING ease-out → OPEN → `close()` →
  CLOSING 0.3 s ease-in → DONE) and `BlinkRipple` (wavefront 1 → 0 over its duration, then a 0.3 s
  fade; `cancel()` fades in 0.15 s).
- `utils/render/blink/BlinkRenderer.kt` — owner of the ripple program (compiled at game start via
  `warmUp()` from the mod plugin, next to `ShieldProgram.warmUp()`), GL state save/restore around
  each batch.
- **Portals = rupture effects.** `utils/render/rupture/RuptureEffect.kt` gained `Kind.BLINK`
  (`RuptureEffect.blink(center, radius, buildDuration)`, phases BUILD → `close()` → CLOSING 0.3 s →
  DONE): the ODST build, every part scaling linearly with the build — soft light (`#243ad5`), glow
  sphere (`#0e31f9` toward white, halo), a new `origin` GLOW draw (`#2e30ff`, 0.35 R, bright), the
  outer plasma **web** at 1.12 R tinted white → `#2d47db` with intensity 1 → 3 (ruptureSphere.frag
  mode 2 now multiplies by `color`; white = unchanged for the fleet jump), and the blue build lens
  flare (1.6 R, shrinking from 2.4 R). No black body, no web collapse, no burst. The render plugin
  owns a `RuptureRenderer` for them (the placeholder disc shader was deleted).
- `utils/render/rupture/RupturePrograms.kt` (new) — the sphere + sprite programs are now shared
  by every `RuptureRenderer` (fleet jump and blink) and compiled at game start (`warmUp()` from
  the mod plugin); `RuptureRenderer.cleanup()` only frees its screen copy.
- `utils/render/blink/BlinkRippleShader.kt` ↔ `data/shaders/blink/blinkRipple.vert/.frag` — draws
  the ship's own sprite quad (same placement as the engine: hull centre at the sprite centre, up
  axis along the facing), masked by the sprite alpha, additive. After the ODST tags: **plasma
  puffs** (the wavefront band, half-width 0.07 hull lengths, and its wake, e-fold 0.3, broken up
  by scrolling plasma noise, violet-blue → blue-white), **spark streaks** (a cell grid over the
  hull; a cell fires as the front passes it and its streak flies outward from the centreline over
  0.14 hull lengths of front travel, fading), and the **bow flash** (`flash` uniform, a soft
  blue-white bloom at the bow after a completed sweep, 0.35 s). `BlinkRipple` carries `flash`
  (only after a natural completion; `cancel()` gives none).
- `utils/render/PlainProgram.kt` — `RuptureProgram` moved out of the rupture package and renamed;
  the plain vertex+fragment program helper is now shared by the rupture and blink renderers.
  `RuptureView` (world → NDC helper) is reused as-is.
- `utils/render/blink/BlinkEffects.kt` — now only `BlinkRipple` (the portal timeline lives in
  `RuptureEffect`).

## Verification

- `./gradlew.bat buildMod` — builds clean 2026-10-02; jar carries `utils/render/blink/*` and
  `data/shaders/blink/*`.
- GLSL: `data/shaders/blink/blinkRipple.*` and the edited `ruptureSphere.frag` pass glslang 16.6.0
  (offline, 2026-10-02).
- Reference frames kept in `assets/reference/odst-carrier-jump/` (scratch, gitignored).
- In-game (developer, pending): simulator, frigate1 (Rasho). Press the system key with the cursor
  ~400 su away: hull ripple bow → stern with arcs for 1.0 s while a portal opens ahead; the ship
  slides into it and vanishes; it reappears sliding out of a portal at the cursor point; both
  portals close; three charges, one back every 10 s (7.7 s with Slipspace Tuning). Check: aiming
  closer than 500 su lands exactly there; AI frigate1 blinks on its own; the ripple sits on the hull
  at every facing and zoom (if the band is mirrored, flip `CORNER_Y` in `BlinkRippleShader`); no
  `OpenGL error` in the log; the shield / fleet jump still render.

## Placeholder numbers

`LURCH_AT ⅔`, `PORTAL_RADIUS_MULT 1.3`, `PORTAL_AHEAD_MULT 2`, `EXIT_START_INSIDE_MULT 1`,
`EXIT_OPEN_DURATION 0.15`, `LURCH_FADE_SHARE 0.4` (`SlipspaceDrive`); `FADE_DURATION 0.35`,
`CANCEL_DURATION 0.15` (`BlinkRipple`); `FRONT_WIDTH 0.07`, `TAIL 0.3`, the two colours
(`BlinkRippleShader`); the spark grid / travel / length / thickness / chance and the bow-flash
size (`blinkRipple.frag`); the `BLINK_*` intensities and proportions in `RuptureEffect` (ODST
colours are exact, its intensities are HDR values for its own tone map); jitter in the `.system`;
the vanilla sound.

## Open items

- **ODST bitmaps** — not exportable from the kit (resource data missing); the portal uses the
  Reach plasma/sphere bitmaps and analytic glows instead. Revisit only if the `grayscale` /
  `slipspace_sphere_origin` / `glow_soft` looks are missed in-game.
- **Portal / ripple tuning** — every `BLINK_*` constant and the ripple shader constants after the
  first in-game look (ODST's HDR intensities do not translate 1:1).
- **Sound** — bespoke slipspace drive sounds (the fleet jump has Reach audio; the blink uses
  `system_phase_teleporter`).
- **Rupture damage** — the old TODO ("apply damage to unshielded ships in radius") is moot for a
  blink; decide whether the portals should harm anything at all.
- `CubicCurve2D` (LemonLib) has no Covenant user left after this rewrite; stays in the library.

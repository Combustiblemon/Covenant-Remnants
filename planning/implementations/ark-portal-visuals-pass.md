# Implementation — Ark portal visual pass (2026-10-08)

The Halo 3 "The Ark" slipspace exit ([ark-slipspace-portals.md](ark-slipspace-portals.md)) was a
faithful remake of a 2007 Xbox 360 effect (77-triangle tube, 256² bitmaps). This pass keeps its
structure and blue-white `#3155ff` identity and makes the execution look current. Parent feature:
[campaign-slipspace-jump.md](campaign-slipspace-jump.md). Every number is a `SlipspaceJumpTune`
tunable (`SlipspaceJumpRules`, section "Ark visual pass"); the defaults are placeholders until the
global tuning pass.

## Developer rulings (2026-10-08)

1. **Tube side.** Settled after seeing both orientations in the debug burst: mouth ahead of the
   fleet, **tube body running back toward the fleet** (ending about a radius short of it with the
   shorter tube). That is the original orientation (`debugBurst` facing = the fleet's facing,
   `ARK_TUBE_FLIP` 0); a facing-the-player variant (+180°) was tried and rejected. The first
   "wrong side" report was the old 3.75-radii tube running over the fleet. Arrival geometry
   unchanged (the fleet exits the wide mouth, the body trails behind).
2. **Tube layers (all):** smooth funnel + depth shading; procedural animated streaks; lightning on
   the wall; mouth rim light + inner glow + background light pool.
3. **Burst layers (all):** analytic elliptical ring with a chromatic edge + a second, faster ring;
   layered flashes (core + halo), sparks out of the mouth, glitter twinkles, anamorphic streaks on
   big carrier flashes; a harder refraction front + a brief black-body dim in the mouth; carrier
   extras (lens + star flare, shockwave ring after the snap-close, final refraction pulse).
4. **Phases (all):** charge-up pre-roll before the opening (the arrival slide and the exit lurch
   wait for it); afterglow / remnant haze; colour grading (white-hot cores, `#3155ff` body, violet
   falloff, deep tube violet-black); idle portal life.
5. **Streak flow:** out of the mouth (`ARK_TUBE_FLOW` > 0; the sign flips it).
6. **Ring curve:** holds then collapses late (`ARK_LATE_MIRROR 1`) confirmed in-game.
7. **Tube size:** smaller, length only — about one fleet length (2 × fleet radius) at the end
   scale (`ARK_TUBE_LENGTH_MULT` 2.5 → 1.33); the mouth stays 1.5 fleet radii.
8. **View tilt** 35° unchanged (no instruction; tunable).
9. **Sound:** the arrival / exit one-shots play **at the opening**, after the pre-roll
   (`RuptureEffect.onArkOpen`); the pre-roll is silent.

## Facts that shaped it

- A dense lathe as a Kotlin `floatArrayOf` literal would exceed the JVM's 64 KB method limit, so
  the generated file holds only the radius profile and the mesh is lathed at load.
- `glow_hard` (Halo's "ring") is a soft-edged **filled disc** (alpha and luma 255 to 0.75 of the
  half-size, fading 0.8 → 0.94): the analytic ring keeps a fill term and puts its line at
  `ARK_RING_PEAK` 0.85 of the Halo half-size, so Halo's sizes stay valid.
- The pocket portals' renderer never advances its clock (`SlipspacePortalEntity` does not call
  `renderer.advance`), so the tube's streak flow and lightning crawl are CPU-integrated phases
  (`TubeDraw.flow` / `arcPhase`, advanced in `ArkTimeline.advance`) — which also freezes them on pause.
- Reach sprite kinds are sized from `ReachCurves`, so the Ark particles are new `ARK_*` kinds on
  the existing materials, refreshed by `ArkTimeline.refresh`.
- Sprites all draw after the tube, so the black-body dim and the mouth glow are **elliptical
  spheres** (new `aspect` / `axis` / offset on `SphereDraw`; defaults keep every Reach / blink
  sphere circular), drawn in the sphere pass before the tube.
- The tube program now binds four textures (units 0–3: bitmap, noise A, noise B, web palette); the
  renderer already saves / restores units 0–4.

## What was built

**Mesh** — `assets/reference/ark-slipspace-exit/tools/gen_tube_profile.py` (new; local) decompresses
the tag's vertices, fits a least-squares circle per station for one averaged axis, takes the mean
radius per station (7 stations), fits a monotone cubic (Fritsch–Carlson) r(x), resamples 33
stations in weighted arc length (dense on the bell) with v = 1 − arc / total (`--v halo` keeps the
tag's v), and writes `ArkTubeProfile.kt` (GENERATED: `SEGMENTS_AROUND` 48, `PROFILE_X/R/V`,
`MOUTH_RADIUS` 0.5396, `LENGTH` 0.5511). `ArkTubeMesh.kt` is now hand-written: lathes `DATA`
(48 × 32 × 6 = 9216 vertices, 3072 triangles, seam column duplicated at u = 1) in its `init`;
`VERTEX_COUNT` is a `val`. `tube_preview.py` lathes from the profile (`--shaded` for a crude
depth-cue render). The old `gen_tube_mesh.py` is kept for reference.

**Tube shader v2** (`data/shaders/rupture/arkTube.vert/.frag`, `ArkTubeShader.kt`, `TubeDraw`):
varyings `vDepth` (0 mouth → 1 far end), `vFront` (+1 the wall facing the viewer), `vMouth`
(position in mouth-ellipse space). Fragment: Halo bitmap mixed with three ridged-noise octaves
stretched along the tube and scrolled by `flow` (whole-number u repeats, no seam); colour
`hotColor` → body → `deepColor` by depth; `exp(−depthFade · depth)`; the back wall dimmed except
where it is seen through the mouth ellipse (`ARK_TUBE_BACK_MOUTH`); lightning = Reach palettized
plasma (|A − B| → `SPHERE_WEB_PALETTE`) gated by a slow noise, strongest near the mouth, not
black-pointed. Black point as before.

**Sprite shader** (`ruptureSprite.frag`, `RuptureSpriteShader.kt`, `RuptureParts.kt`): mode 8
`ANALYTIC_RING` (signed distance from the ring line in sprite-local space, scaled to screen
proportions by the quad's aspect: white core (≥ 1 px, energy kept), body, inner / outer chromatic
fringe, interior fill; style uniforms `ringCore` / `ringInner` / `ringOuter` / `ringShape` from
`RuptureEffect.arkRingStyle`, written each frame by the timeline; `RING_STYLE_FLOATS` 13), mode 9
`REFRACT_RING` (a thin ring that pushes the screen copy across its line, derivatives taken before
any discard; drawn in the screen-copy pass: `SpriteMaterial.usesScreenCopy`,
`RuptureRenderer.SCREEN_SPRITES`). `RuptureSprite.shape[4]` carries their params. Materials
`ARK_RING_ANALYTIC`, `ARK_REFRACT`, `ARK_GLOW` (light_volume_glow), `ARK_STREAK` (the torpedo's
anamorphic flare). Kinds `ARK_RING` (now analytic), `ARK_RING_BITMAP` (`ARK_RING_BITMAP 1` A/B),
`ARK_RING2`, `ARK_RIM`, `ARK_SHOCKWAVE`, `ARK_REMNANT`, `ARK_REFRACT`, `ARK_RIPPLE`,
`ARK_FLASH_HALO`, `ARK_SPARK`, `ARK_TWINKLE`, `ARK_HAZE`, `ARK_STREAK`.

**Spheres** (`ruptureSphere.frag`, `RuptureSphereShader.kt`): `aspect` / `axis` uniforms (an
ellipse when aspect < 1) and `SphereDraw.offsetX/Y`. `RuptureEffect`: `arkPool` (LIGHT),
`arkDim` (BLACK, elliptical), `arkGlow` (LIGHT, elliptical) at the end of `spheres`; `arkStar`
flare; `arkRingStyle`; `onArkOpen` callback.

**Timeline** (`ArkTimeline.kt`, rewritten): `burstTime` runs from −`ARK_PREROLL`. Pre-roll: the
idle look fades out, the mouth glow grows (p²), the pool rises, heat-haze ripples (`ARK_RIPPLE`)
gather from `ARK_PREROLL_GATHER_RADII` × R, `ARK_PREROLL_TWINKLES` converge on the mouth, the rim
fades in. Opening (`open()`): Halo's distortion ring, an `ARK_REFRACT` front, the dim envelope,
`ARK_SPARK_COUNT` sparks on the mouth ellipse, carrier: anamorphic streak; `onArkOpen` fires
(sound). Burst: tube (flow, lightning, grading), main analytic ring (late collapse), `ARK_RING2`
collapsing `ARK_RING2_SPEED`× faster, rim pinned to the current mouth, glow ramping in as the dim
fades, pool shifted toward the body, flashes (core + halo + glitter, carrier: streak chance),
sparks streaming for `ARK_SPARK_SECONDS`, carrier lens + star flare, snap-close + close distortion +
`ARK_SHOCKWAVE` + refraction pulse. After life + tail: `ARK_HAZE_COUNT` haze puffs, the pool and
glow fade over `ARK_AFTERGLOW`, carrier `ARK_REMNANT` ring. One-shot effects end when the afterglow
is over, the carrier close has settled and only the held sprites remain; persistent portals start
the idle fade-in at life + tail. Idle: breathing tube with `ARK_IDLE_FLOW`, lightning bursts gated
by noise (`ARK_IDLE_ARC_RATE` / `_INTENSITY`), pulsing rim, pool, glow, drifting twinkles
(`ARK_IDLE_TWINKLE_RATE`), the flicker flashes. All colours go through `hdr()` =
× `ARK_EXPOSURE_MULT`. Geometry helpers: `mouthRadius(u, scale)`, `mouthAspect()` (exact sin tilt,
for everything pinned to the mesh; the clamped `aspect()` stays for Halo's ring / flashes),
`mouthPoint(θ, R, spread)` (a point on the mouth ellipse and the out-of-mouth direction, flip-aware).

**Campaign** — `SlipspaceJump.debugBurst` unchanged in orientation (ruling 1); `beginExit` / `onArrivalBurst` /
`debugBurst` and `SlipspacePortalEntity.burst(…, onOpen)` play their sound from `onArkOpen`;
`advanceExiting` holds the fleet for `ARK_PREROLL` before the lurch;
`SlipspaceTransitionScript.slideOut` fires the burst at `ARK_ARRIVAL_DELAY` and starts the slide
`ARK_PREROLL` later. Nothing new is saved: a load mid-transition re-calls `burst()` as before.

**Tunables** (`SlipspaceJumpRules`, "Ark visual pass"; `tuneRgb` registers `NAME_R/G/B`):
`ARK_TUBE_BITMAP_MIX`, `ARK_TUBE_STREAK_*` (INTENSITY, U, V, SHARP, GAIN), `ARK_TUBE_FLOW`,
`ARK_TUBE_HOT_SPAN`, `ARK_TUBE_VIOLET_FROM`, `ARK_TUBE_DEPTH_FADE`, `ARK_TUBE_BACK_DIM`,
`ARK_TUBE_BACK_MOUTH`, `ARK_TUBE_HOT_INTENSITY`, `ARK_TUBE_DEEP_INTENSITY`, `ARK_WHITE_HOT_*`,
`ARK_VIOLET_DEEP_*`, `ARK_TUBE_ARC_*` (INTENSITY, U, V, SPEED, GATE, REACH, PALETTE_V),
`ARK_RIM_INTENSITY/WIDTH`, `ARK_MOUTH_GLOW_INTENSITY/RADII`, `ARK_POOL_INTENSITY/RADII/OFFSET`,
`ARK_RING_BITMAP`, `ARK_RING_PEAK/FILL/CORE_WIDTH/BODY_WIDTH/CORE_INTENSITY/FRINGE_OFFSET/
FRINGE_WIDTH/FRINGE_INTENSITY`, `ARK_RING_FRINGE_IN_*`, `ARK_RING_FRINGE_OUT_*`,
`ARK_RING2_SPEED/SIZE/INTENSITY/WIDTH`, `ARK_FLASH_HALO_SIZE/INTENSITY`, `ARK_SPARK_*` (COUNT,
RATE, SECONDS, SPEED, SPREAD, DRAG, LIFE, SIZE, STRETCH, INTENSITY), `ARK_GLITTER_PER_FLASH/LIFE/
SIZE/INTENSITY`, `ARK_STREAK_CHANCE/LENGTH/ASPECT/INTENSITY/ALONG_FACING`,
`ARK_REFRACT_STRENGTH/WIDTH/SECONDS/MAX_RADII`, `ARK_DIM_STRENGTH/SECONDS/RADIUS/SOFT`,
`ARK_STAR_RADIUS/BRIGHTNESS`, `ARK_SHOCK_SECONDS/RADII/INTENSITY/WIDTH`,
`ARK_REMNANT_SECONDS/INTENSITY/GROWTH`, `ARK_PREROLL` (0.4) and `ARK_PREROLL_LIGHT/RIPPLE_RATE/
TWINKLES/GATHER_RADII/RIM`, `ARK_AFTERGLOW`, `ARK_HAZE_COUNT/SIZE/INTENSITY`, `ARK_EXPOSURE_MULT`,
`ARK_IDLE_FLOW/RIM/RIM_PULSE/ARC_RATE/ARC_INTENSITY/TWINKLE_RATE/POOL`. Changed default:
`ARK_TUBE_LENGTH_MULT` 2.5 → 1.33. Parity with the old look: `ARK_TUBE_BITMAP_MIX 1`,
`ARK_TUBE_BACK_DIM 1`, `ARK_TUBE_DEPTH_FADE 0`, `ARK_TUBE_ARC_INTENSITY 0`, `ARK_RING_BITMAP 1`,
`ARK_PREROLL 0`, `ARK_AFTERGLOW 0`.

**Offline checks done**: Gradle build green; the three shader pairs compile and link in a
standalone GL 3.3 context (moderngl, NVIDIA); the lathe preview shows a smooth bell with the mouth
ellipse at the marker and the body receding. Not yet seen in-game.

## Files

New: `utils/render/rupture/ArkTubeProfile.kt` (generated), `assets/reference/ark-slipspace-exit/
tools/gen_tube_profile.py`, this doc.
Changed: `utils/render/rupture/{ArkTimeline,ArkTubeMesh,ArkTubeShader,RuptureParts,RuptureEffect,
RuptureRenderer,RuptureSpriteShader,RuptureSphereShader}.kt`, `data/shaders/rupture/{arkTube.vert,
arkTube.frag,ruptureSprite.frag,ruptureSphere.frag}`, `campaign/abilities/{SlipspaceJump,
SlipspaceJumpRules,SlipspaceTransitionScript}.kt`, `campaign/entities/SlipspacePortalEntity.kt`,
`assets/reference/ark-slipspace-exit/tools/tube_preview.py`.

## In-game checklist (not yet run)

1. Start: "Covenant rupture programs compiled", no "Compiling … failed" / "Linking … failed", no
   `OpenGL error`. Combat Fleet Slipspace Jump, the blink and the plasma torpedo look as before
   (the sprite and sphere programs changed).
2. `SlipspaceJumpTune ark carrier` / `ark cruiser`: mouth ahead, tube body running back toward
   the fleet, the tube about one fleet length, smooth silhouette, streaks flowing
   out of the mouth, lightning in bursts, rim light + inner glow + light pool, rings elliptical with
   a white core and cyan / violet fringe, the second ring, sparks and twinkles at the opening, a brief
   dark disc in the mouth, the thin refraction front, carrier: star flare, anamorphic streak,
   shockwave and remnant ring; pre-roll (glow, ripples, converging twinkles) → opening → afterglow;
   the sound lands on the opening. `ARK_TUBE_BITMAP_MIX 1` etc. for parity; `ARK_FORCE_VARIANT`.
3. Pocket: the standing portals idle (flow, rim pulse, occasional lightning, drifting twinkles),
   dim with the screen fade, freeze on pause, rebuilt after save / load.
4. Entrance / exit / destination: the slide and the lurch wait for the pre-roll; save / load
   mid-transition (also during the pre-roll) resumes.
5. 100 % / 150 % UI scale; the zoom range (noise aliasing); intensity not blown out at the default
   `EXPOSURE`.

## Open items

- Every number is a placeholder (the look tuning loop: `SlipspaceJumpTune ark …`).
- The noise textures have no mipmaps: high `ARK_TUBE_STREAK_U` shimmers when zoomed out (cap it, or
  fade the top octave below ~40 px mouth size).
- Layers tone-map separately and add (ONE, ONE): overlaps clip to white; budget per-layer intensities.
- The pre-roll charge sound is a hook only (`onArkOpen` plays the existing one-shot at the opening).
- `ArkTimeline` is ~800 lines; split spawn / refresh into `ArkParticles.kt` if it grows further.
- The Ark refraction rings are still gated by the global `FleetJumpTune DISTORTION` (0 = off).

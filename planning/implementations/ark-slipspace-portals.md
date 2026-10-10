# Implementation — Ark slipspace portals (2026-10-08)

The slipspace pocket's entrance / exit portals and the destination arrival of the campaign
Slipspace Jump now use the Halo 3 "The Ark" intro slipspace exit (the Covenant fleet dropping out
of slipspace), remade from the Halo 3 editing kit's own tags. Replaces the placeholder portal disc
(`SlipspacePortalEntityShader`, deleted) and the Reach exit flash at the destination.

Reference data and decoded tables: `assets/reference/ark-slipspace-exit/README.md` (gitignored).
Parent feature: [campaign-slipspace-jump.md](campaign-slipspace-jump.md).

## Developer rulings (2026-10-08)

1. **Scope**: pocket entrance + exit entities, and the destination arrival. The departure keeps
   the Reach entry rupture.
2. **Idle**: standing portals show a dim held tube + ring with occasional flickers; the full burst
   plays on use.
3. **Arrival motion**: the fleet starts hidden inside the tube and slides out along the exit
   direction over the burst (pocket entrance and destination), instead of fading in in place.
4. **Variant by largest hull**: any CAPITAL_SHIP → the 3.75 s carrier burst (lens flare at the
   start, snap-closed flash at the end); otherwise the 1.5 s cruiser burst. Scale from the fleet radius.
5. The old look is deleted. Every number is a `SlipspaceJumpTune` tunable (placeholders).

**Visual pass 2026-10-08**: [ark-portal-visuals-pass.md](ark-portal-visuals-pass.md) — the tube
mesh is now lathed at load from the tag's radius profile, the tube / ring / flash shaders and the
timeline gained the new layers (pre-roll, afterglow, idle life); the notes below describe the base
remake.

## Assumptions (not rulings, each with a tunable)

- **Tube direction**: the bright, wide end of the funnel (x = 0, v = 1) is the opening a
  fleet comes out of; the tube narrows away behind the mouth along −facing. The cinematic's
  anchor orientation lives in animation graphs, not the tags, so this is read from the mesh.
  `ARK_TUBE_FLIP 1` reverses it. **Confirmed 2026-10-08** (debug burst: mouth ahead of the
  fleet, body running back toward it, with the tube shortened to one fleet length).
- **Facing** = the direction a fleet leaves the tube. Entrance: p0→p1; exit: pN→pN−1 (mouth toward
  the arriving fleet, tube past the path end). Set by `orientSlipspacePortals` on every route
  layout and on migration of an existing route.
- **Burst timing**: after the screen fade-in + `ARK_ARRIVAL_DELAY` (not at the switch, where the
  black fade would hide the carrier's 0.2 s lens flare).
- **Layer**: portal entities render on `TERRAIN_8` only (above the fleets and the stream, drawn
  once); the ability's effects stay on `ABOVE`.
- **Projection**: orthographic with a fixed view tilt `ARK_VIEW_TILT` (35°) so the funnel and the
  rings read as ellipses instead of a flat band and a line.
- **Distortion ring** stays circular / screen-aligned (the sprite distortion path does not
  rotate); Halo's 80× expansion is scaled into `ARK_DISTORTION_MAX_RADII` fleet radii and fades.
- **late() curve**: holds then drops (mirrored bezier of the decoded handles); `ARK_LATE_MIRROR 0`
  flips it. **Confirmed 2026-10-08.**
- Invented: the end fade `ARK_TAIL`, the distortion alpha fade, the whole idle look, the arrival
  sound at the pocket entrance (`covenant_remnants_fleet_jump_arrival`), `ARK_EXIT_HOLD`.

## What was built

**Render (utils/render/rupture)**
- `RuptureEffect.Kind.ARK` with phases `ARMED` (invisible until `burst`), `IDLE`, `BURST`;
  factory `ark(center, facing, radius, persistent)`; `burst(variant, radius)`; `facing`;
  `TubeDraw tube`, `arkLens` flare. Logic in `ArkTimeline.kt` (`ArkVariant { CRUISER, CARRIER }`).
- Tube mesh: `ArkTubeMesh.kt` (generated from the H3 particle_model, 231 verts × pos3 + uv2,
  decompressed from the tag's position bounds and centred on its axis — the export holds
  normalised 0..1 positions; a funnel: mouth radius 0.55 flaring down to 0.27, length 0.55),
  `ArkTubeShader.kt` + `data/shaders/rupture/arkTube.vert/.frag` (static VBO; spin about the axis,
  tilt, NDC via the `RuptureView` maths; additive, tone-mapped, black point on streak brightness),
  shared through `RupturePrograms.tube()` and warmed up at app load. Drawn after the spheres and
  before the heat haze, so the distortion ripples over it.
- Sprites: materials `ARK_RING` (`glow_hard`), `ARK_FLASH` (`flash_soft`, new fragment mode 7 =
  UV rotation + mirror), `ARK_FLARE` (`simple_flare`); kinds `ARK_RING`, `ARK_FLASH`, `ARK_CLOSE`,
  `ARK_DISTORTION` (reuses Reach's `rupture_distortion_ring`). Textures `ark_tube`, `ark_ring`,
  `ark_flash`, `ark_flare` in `graphics/covenant_remnants/fx/slipspace_ark/`, registered in settings.json.
- One world-units-per-Halo-unit conversion `u = R × ARK_MOUTH_RADII / (ARK_TUBE_SCALE1_CRUISER ×
  mesh mouth radius)`, R = the fleet's Ark radius (`ARK_RADIUS_MULT` × fleet radius, floor
  `ARK_RADIUS_MIN`) or the portal's idle radius (`ARK_PORTAL_RADIUS_MULT` × entity radius), so
  Halo's proportions hold. "Perpendicular to direction" sprites become ellipses (aspect sin tilt).

**Campaign**
- `SlipspacePortalEntity` rewritten: a persistent ARK effect per portal (transient, lazily built,
  dropped when the player leaves the pocket), one shared `RuptureRenderer`, render on `TERRAIN_8`
  only, `burst(variant, radius)`. `custom_entities.json`: the portal types list one layer.
- `SlipspaceSystem.orientSlipspacePortals` (+ migration of existing routes).
- `SlipspaceTransitionScript`: `ArrivalPlan(from, to, mouth, facing, seconds)`; with a plan the
  arrival is a slide out of the tube (ease-out `ARK_SLIDE_EASE`), the ships showing as the fleet
  centre passes the mouth (`ARK_REVEAL_BEHIND` / `ARK_REVEAL_SPAN`), the burst fired through
  `SlipspaceJump.onArrivalBurst` after `ARK_ARRIVAL_DELAY`; `ARK_EXIT_HOLD` on the exit leg.
- `SlipspaceJump`: the entrance plan at commit; `commitExit` arms an ARK effect at the system
  edge facing the centre and plans the slide; `beginExit` bursts the exit portal and lurches into
  its tube; `onArrivalBurst` (entrance plugin / destination effect, rebuilt after a load);
  `debugBurst` for `SlipspaceJumpTune ark [cruiser|carrier]`. `SlipspaceJumpRules`: the `ARK_*`
  tunables, `arkRadius`, `arkVariant` (`ARK_FORCE_VARIANT`), `arkSeconds`, `arrivalPlan`.
- `SlipspaceStreamTerrainPlugin` ignores fleets in a hyperspace transition (the slide starts
  beyond the path's start).

**Review fixes (2026-10-08)**: the arrival-burst callback carries the tube mouth (a rebuilt
destination effect after a load was placed at the fleet's held position inside the tube); flash
and distortion sprites pin their variant at spawn (a restarted burst or the idle look no longer
changes an in-flight sprite's curves); idle flickers are always cruiser-sized.

## Files

New: `utils/render/rupture/{ArkTimeline,ArkTubeShader,ArkTubeMesh}.kt`,
`data/shaders/rupture/arkTube.{vert,frag}`, `graphics/covenant_remnants/fx/slipspace_ark/*.png`,
`assets/reference/ark-slipspace-exit/` (README, data, tools).
Changed: `RuptureEffect.kt`, `RuptureParts.kt`, `RuptureTexture.kt`, `RupturePrograms.kt`,
`RuptureRenderer.kt`, `ruptureSprite.frag`, `campaign/entities/SlipspacePortalEntity.kt`,
`campaign/abilities/{SlipspaceJump,SlipspaceTransitionScript,SlipspaceJumpRules}.kt`,
`campaign/systems/SlipspaceSystem.kt`, `campaign/terrain/SlipspaceStreamTerrainPlugin.kt`,
`commands/SlipspaceJumpTune.kt`, `data/config/custom_entities.json`, `data/config/settings.json`.
Deleted: `utils/render/SlipspacePortalEntityShader.kt`, `data/shaders/slipspacePortalEntityShader.{frag,vert}`.

## In-game checklist

1. Start: no missing-texture lines, the rupture programs compile (log line "Covenant rupture
   programs compiled"), no `OpenGL error`. Combat: Fleet Slipspace Jump, blink and plasma torpedo
   unchanged.
2. `SlipspaceJumpTune ark cruiser` / `ark carrier` in any system: tube behind the mouth ahead of
   the fleet (compare `ARK_TUBE_FLIP 1`), ring and flashes as ellipses, flashes spin and mirror,
   distortion ring capped and aligned at 100 % / 150 % UI scale, carrier lens flare at the start
   and the snap-closed flash at 3.65 s, readable across the zoom range, intensity not blown out.
3. Pocket: both portals idle dim and point the right way (entrance down the corridor, exit back
   toward the arriving fleet), drawn once, dim with the screen fade, rebuilt after save / load,
   a prototype save still loads.
4. Entrance: burst at arrival, fleet slides out of the tube and shows past the mouth, the stream
   does not tug during the slide.
5. Exit: dialog → burst, fleet lurches into the tube, `ARK_EXIT_HOLD`, fade.
6. Destination: burst at the system edge, fleet slides out toward the centre, cooldown at landing.
7. With and without a capital (or `ARK_FORCE_VARIANT`): the right variant plays.
8. Save / load mid-transition (OUT and BACK) resumes with the slide and the burst; pause freezes
   everything (idle flicker included).

## Open items

- Every number is a placeholder (tuning pass; `SlipspaceJumpTune`).
- Tube direction and the `late()` reading are assumptions (flip tunables).
- The carrier burst (3.75 s) is longer than the exit lurch (`EXIT_LURCH` 1 s) + hold; tuning.
- Rupture-renderer screen-copy texture of the shared portal renderer is never freed (one per session).

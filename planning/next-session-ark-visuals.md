# Next session brief — Ark slipspace portal visuals (handoff written 2026-10-08)

**Consumed 2026-10-08** — the pass was built; see
[implementations/ark-portal-visuals-pass.md](implementations/ark-portal-visuals-pass.md) (rulings,
what was built, the in-game checklist still to run). Kept for the record.

Paste into a fresh session: "Read `planning/next-session-ark-visuals.md` and follow it."

## Mission

Improve the visuals of the campaign Slipspace Jump's **Ark exit portals** (the pocket's entrance /
exit entities and the destination arrival). The current look is a faithful remake of the Halo 3
"The Ark" intro slipspace exit — tags, mesh and 256² bitmaps from a 2007 Xbox 360 game. The
structure stays (funnel tube, collapsing ring, expanding refraction ring, flickering flashes,
carrier lens flare + snap-closed flash, blue-white `#3155ff` family, 1.5 s / 3.75 s variants); the
execution should look like a 2026 effect: richer light, motion and detail. The departure (Reach
entry rupture) and the combat jump are not in scope.

## Read first (in this order)

1. `CLAUDE.md` — project rules. The assistant does not hold design authority: **ask, don't guess**;
   every look / number decision is the developer's. Caveman style always on.
2. `planning/implementations/ark-slipspace-portals.md` — what exists, rulings, assumptions, the
   8-point in-game checklist (not yet run by the developer), open items.
3. `planning/implementations/campaign-slipspace-jump.md` — the ability the portals hang off.
4. `assets/reference/ark-slipspace-exit/README.md` — the decoded Halo 3 data, gotchas, tools.
5. `planning/implementations/fleet-slipspace-jump.md` §"Visual (Reach remake)" — how the richer
   Reach rupture (spheres, plasma sprites, web, heat haze) is built; most of that machinery is
   reusable here.

## Where the code is

`src/org/combustiblelemon/covenantremnants/`:

- `utils/render/rupture/ArkTimeline.kt` — the whole Ark timeline (burst + idle), every number a
  `SlipspaceJumpRules` tunable. `RuptureEffect.kt` kind `ARK` (phases ARMED / IDLE / BURST),
  `ArkTubeShader.kt` + `data/shaders/rupture/arkTube.vert/.frag` (the funnel mesh, orthographic
  top-down with a 35° tilt, spin about its axis), `ArkTubeMesh.kt` (generated; regenerate with
  `assets/reference/ark-slipspace-exit/tools/gen_tube_mesh.py`), `RuptureParts.kt` (materials
  `ARK_RING` / `ARK_FLASH` / `ARK_FLARE`, kinds, `TubeDraw`), `RuptureRenderer.kt` (draw order:
  spheres → tube → heat haze over a screen copy → additive sprites → flares → twinkles),
  `ruptureSprite.frag` (mode 7 = rotated / mirrored sprite), `RuptureSphereShader.kt` +
  `ruptureSphere.frag` (analytic sphere modes GLOW / BLACK / WEB / SHELL / PLASMA_SHELL / RING /
  LIGHT / CORE — all available to the Ark effect, unused by it so far).
- `campaign/entities/SlipspacePortalEntity.kt` — the standing portals (idle + burst, one shared
  renderer, layer `TERRAIN_8`). `campaign/abilities/SlipspaceTransitionScript.kt` — the arrival
  slide out of the tube. `campaign/abilities/SlipspaceJump.kt` — `onArrivalBurst`, `beginExit`,
  `debugBurst`. `campaign/abilities/SlipspaceJumpRules.kt` — the `ARK_*` tunables (section
  "Ark portals") and `arrivalPlan`.
- Textures: `graphics/covenant_remnants/fx/slipspace_ark/` (Halo 3: `slipspace_tube`, `glow_hard`, `flash_soft`,
  `simple_flare`), `graphics/covenant_remnants/fx/slipspace_rupture/` (Reach: noise, palettes, flares, glitter,
  `distortion_ring`, `beam`, `light_volume_glow`) — all registered in `data/config/settings.json`
  under `covenant_remnants`.

## Testing hooks

- Build: `.\gradlew.bat buildMod` (jar → `jars/CovenantRemnants.jar`). Console Commands mod is
  optional but installed.
- `CovenantGiveAll` grants the ability. `SlipspaceJumpTune` lists / sets / resets every tunable;
  `SlipspaceJumpTune NO_SHIP_RESTRICTION 1` and `FREE 1` for testing; **`SlipspaceJumpTune ark
  cruiser|carrier`** plays a one-shot burst ahead of the player fleet anywhere (the main look
  iteration loop — no jump needed). `ARK_FORCE_VARIANT 1|2`. `FleetJumpTune EXPOSURE` /
  `DISTORTION` also affect the Ark draw (shared renderer tunables).
- The session cannot see the game. Ask the developer for screenshots or short clips (they drop
  them in `C:\Users\Tasos\Downloads\`; ffmpeg is on PATH — extract contact sheets into the
  scratchpad and read them). Offline: `tools/tube_preview.py` (wireframe of the mesh with the
  shader's projection), and the Reach harness route (`assets/reference/long-night-of-solace/
  reach-data/tools/RuptureHarness.java` + `render_frames.py`, moderngl in a scratch venv) renders
  the real shaders headless if a bigger preview is needed.

## State at handoff

- Built and compiling; nothing committed (the developer commits). The in-game checklist has not
  been run. First developer feedback: the tube was orbiting the portal — fixed (mesh positions in
  the tag export were compressed 0..1; now decompressed and centred). Nothing else verified yet.
- Assumptions with flip tunables, to confirm with the developer in-game before polishing on top
  of them: tube direction (`ARK_TUBE_FLIP`), ring "holds then collapses" curve (`ARK_LATE_MIRROR`),
  view tilt (`ARK_VIEW_TILT` 35°), tube length (`ARK_TUBE_LENGTH_MULT` 2.5).
- All numbers are placeholders; the global tuning pass is deferred until every gameplay system is
  done (developer 2026-10-02) — but **look** tuning of these effects is this session's job.

## Suggested approach

1. Ask the developer to run `SlipspaceJumpTune ark carrier` and `ark cruiser` and describe (or
   screenshot) what reads badly. Fix the assumptions first.
2. Agree the upgrade direction with the developer before building — present options, one
   question at a time. Candidates (not decisions):
   - Tube: replace / augment the 256² streak texture with procedural streaks in the fragment
     shader (scrolling noise along v, several octaves, sharper filaments), a soft volumetric
     glow inside the funnel (a LIGHT / GLOW sphere or an inner-wall glow), lightning filaments
     (the Reach WEB sphere or `beam`-textured arcs) licking the rim, a rim light at the mouth.
   - Ring: the Reach RING / SHELL sphere modes (analytic, resolution-independent) instead of the
     `glow_hard` bitmap; a second thin bright ring; chromatic edge.
   - Flashes: more, smaller, layered (core + halo), with Reach `glitter` twinkles and sparks
     streaming out of the mouth (SPARK sprites along −facing); anamorphic streak on the big ones.
   - Distortion: a stronger, sharper refraction front (the `DISTORTION` material supports a
     screen-copy push; `ARK_DISTORTION_STRENGTH`), plus a brief black-body dim (BLACK sphere
     multiply) at the opening for contrast.
   - Opening / closing: a charge-up shimmer before the burst, an afterglow / remnant haze that
     lingers (Reach `HAZE` / `SMOKE` sprites), ambient light pulses on the pocket background.
   - Colour grading: keep `#3155ff` / `#3175ff` as the identity, add white-hot cores and a
     violet falloff (the mod's shield / rupture palette) for depth.
   - Idle portals: a slow energy flow along the tube, breathing glow, occasional arcs.
3. Keep everything in the existing architecture: new draws are `SphereDraw` / sprite kinds /
   `TubeDraw` fields on `RuptureEffect`, logic in `ArkTimeline`, new GLSL only in
   `data/shaders/rupture/` with loaders in `utils/render/rupture/`, every number a tunable in
   `SlipspaceJumpRules` (section "Ark portals"), programs compiled at game start
   (`RupturePrograms.warmUp`). No GraphicsLib. Save-safe: nothing new saved on entities.
4. Document per `planning/DOCUMENTATION.md`: extend `planning/implementations/ark-slipspace-portals.md`
   (or add `ark-portal-visuals-pass.md`), tick / add `planning/TODO.md` items.

## Gotchas

- Bash tool: unbalanced apostrophes break even quoted heredocs — write scripts with the Write
  tool and run them. `rm` of tracked source is denied by the sandbox; use `git rm` or overwrite.
  IDE diagnostics lag behind new files; trust the Gradle build.
- `RuptureEffect` is one big timeline class; `switchTo` is internal, `master` has an internal
  setter; the renderer scales `master` by the viewport alpha for the campaign.
- Halo 3 tag exports: `export-tag-to-xml` works with absolute paths; batch exports can come out
  empty — re-export singles; particle_model vertices are compressed (see the README).

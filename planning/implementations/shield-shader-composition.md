# Shield shader source-composition refactor

Broke the monolithic shield shader (`ShieldShader.kt` ~1075 lines + one giant
`shieldShader.frag`) into composable layers. Each visual effect is now its own
GLSL module file paired with a Kotlin class that owns its state and uniforms.
The fragment shader is assembled from the modules at init time and still drawn
in a **single pass**, so the output is pixel-identical to the old shader.

## Architecture

### Composition model

- `ShieldLayer` interface (`utils/render/shield/ShieldLayer.kt`): a layer
  declares its GLSL module file (`moduleFile`) and uploads its own uniforms
  (`uploadUniforms(program, viewport)`).
- `ShieldShader` (orchestrator, unchanged path `utils/render/ShieldShader.kt`)
  holds `layers = listOf(fill, border, sweep, ripple, honeycomb, debug)`.
  **List order = GLSL concatenation order = uniform upload order.**
- Fragment source assembly (`assembleFragmentSource`): module files are
  concatenated at the `// {{LAYER_MODULES}}` marker inside
  `data/shaders/shield/base.frag`, then `POINT_COUNT_PLACEHOLDER` is replaced
  (after concatenation, so occurrences inside modules resolve too).

### GLSL module contract

- Every module function is prefixed `<layerName>_` (e.g. `fill_color`,
  `honeycomb_apply`) — avoids collisions.
- Every uniform is declared in exactly **one** file: shared uniforms
  (`points[]`, `resolution`, `center`, `shipRotationMatrix`, `viewportScale`,
  `shipFacing`, `time`) live in `base.frag`; layer-owned uniforms live in the
  layer's module. A duplicate global uniform declaration is a GLSL compile
  error at runtime.
- Cross-module reads only point **backwards** in concat order (sweep reads
  `borderColor`, border comes first).
- Mix order in `base.frag`'s `shieldFrag()` is the pixel contract:
  fill → border → sweep edge → ripples → honeycomb. Do not reorder.
- Sweep is mask-modifying: its early return (`pixelShipRelX < sweepPosition` →
  sweep edge only) is hardcoded in `base.frag`; the `SweepLayer` only owns
  state/uniforms. `sweepPosition` is computed CPU-side in `updatePolygon`
  (depends on smoothed polygon bounds + `phaseCloak.effectLevel`) — it must
  stay there, `updatePolygon` runs every frame from the plugin.
- Layers compile in unconditionally; runtime disable is via uniforms
  (`honeycombEnabled`, `debugMode`, `numRipples = 0`). This is what keeps the
  single-program output identical.

### Layer table

| Kotlin (`utils/render/shield/`) | GLSL (`data/shaders/shield/`) | Owns |
| --- | --- | --- |
| `FillLayer` | `fill.glsl` | `shieldColor`, `shieldHPPercent` (HP brightness/opacity) |
| `BorderLayer` | `border.glsl` | `borderColor`, `borderWidth` (exposes width for bbox margin) |
| `SweepLayer` | `sweep.glsl` | `sweepPosition`, `sweepEdgeWidth` |
| `RippleLayer` | `ripple.glsl` | ripple list + `numRipples` and the 5 `ripple*[10]` arrays |
| `HoneycombLayer` | `honeycomb.glsl` | `honeycombEnabled`, `honeycombSize`, `edgeWidth` |
| `DebugLayer` | `debug.glsl` | `debugMode`, `boundingBoxMin/Max` |

Plus `base.frag` (mask + shared helpers `distanceToLine`/`isPointInPolygon` +
`shieldFrag`/`main`) and the slimmed `shield.vert` (NDC passthrough +
`TexCoord` only). `PolygonSmoothing.kt` holds the `SmoothingMethod` enum and
the CPU smoothing functions (moved verbatim out of `ShieldShader`).

## Debug tooling (added 2026-08-25)

Answers "concatenated source can't be debugged per layer":

- **Solo/subset layer rendering.** Each layer declares `layerName` and a
  neutral `stubSource` (same function signatures, identity behavior, no
  uniforms — exception: border stub declares `borderColor` because sweep reads
  it; uploads are skipped while disabled so it stays transparent). Assembly
  substitutes the stub for disabled layers; disabled layers are also skipped
  in the upload loop. `ShieldShader.setEnabledLayers(names)` per instance, or
  the static `ShieldShader.globalEnabledLayers` for all instances; programs
  rebuild lazily at the next `render()` (GL thread). Prerequisite refactor:
  the sweep cull in `base.frag` now goes through `sweep_isCulled()` so no
  layer uniform is read directly by the base template.
- **Console command** — originally `CovenantShieldLayers`; superseded by the
  generic `ShaderLayers <system> [...]` when the composition engine was
  extracted (see
  [shader-composition-generic.md](shader-composition-generic.md)).
- **Module-tagged shader errors.** Assembly records a line map
  (`SourceSegment(name, startLine)` per piece: base header, each module or
  stub, base footer). On fragment compile failure the driver log's line
  numbers (NVIDIA `0(123)` and AMD/Intel `ERROR: 0:123:` formats) are
  translated to `module.glsl:localLine` in the exception message, original log
  kept below, and the full assembled source is dumped line-numbered to
  `starsector.log` at error level.

### Adding a layer

1. Write `data/shaders/shield/<name>.glsl` — declare its uniforms, prefix its
   functions `<name>_`.
2. Write a `ShieldLayer` Kotlin class uploading those uniforms.
3. Add it to the `layers` list in `ShieldShader` and call its
   `<name>_apply(...)` at the right point in `base.frag`'s `shieldFrag()`.

## Dead code removed

The old Kotlin set many uniforms the frag never declared (`glGetUniformLocation`
returned -1, silently ignored). All deleted:

- **Pop wave** — entire effect rendered *nothing* (no `popWave*` uniforms in
  frag). Removed `PopWave`/`RadialLine` classes, `triggerShieldPop`,
  `updateShieldPop`, `isPopWaveActive`, `drawShieldPop`, `popEffectRadius`
  constructor param. The plugin's `triggerShieldPop()` remains (cloak
  deactivate + EMP-arc lightning — those are real).
- Dead uniforms: `glowIntensity/glowWidth` (+ `updateGlow`), `zoomLevel`
  (+ `updateZoomLevel`), `chargeProgress`, `shieldSweepProgress`, `rotation`,
  `debugPolygon`, `scale` (frag).
- Dead vert outputs `screenPos`/`shipRelPos`/`shipRelX`/`distanceFromCenter`,
  their uniforms, and the dummy `worldPoint` attribute — VBO is now 2 floats
  per vertex.
- `calculateMaxPointsAfterSmoothing` collapsed to `minOf(count, 1000)` (all
  three branches were identical).

## Key decisions

- **Source composition over true multi-pass** (developer decision): the old
  shader `mix()`es layers before the single framebuffer blend; separate
  blended passes would change the output (hex/ripples brighter). Single
  assembled program keeps it pixel-identical.
- **Bounding-box margin preserved**: was `maxOf(borderWidth, glowWidth) + 10f`
  = 20 at runtime; after glow removal it is `maxOf(borderWidth, 10f) + 10f`
  with a comment — keeps quad size and debug bbox identical.
- `updateBorder` keeps its (already unused) `blend` parameter so the plugin
  call site stays untouched.

## Files

**New:** `src/.../utils/render/shield/{ShieldLayer, FillLayer, BorderLayer,
SweepLayer, RippleLayer, HoneycombLayer, DebugLayer, PolygonSmoothing}.kt`;
`data/shaders/shield/{shield.vert, base.frag, fill.glsl, border.glsl,
sweep.glsl, ripple.glsl, honeycomb.glsl, debug.glsl}`
**Modified:** `src/.../utils/render/ShieldShader.kt` (rewritten as
orchestrator), `src/.../plugins/CovenantShieldsRenderPlugin.kt` (dead calls
removed, `SmoothingMethod` import from new package)
**Deleted:** `data/shaders/shieldShader.frag`, `data/shaders/shieldShader.vert`
(after in-game sign-off, 2026-08-25)

## Open items

- **Verified in-game 2026-08-25** — developer confirmed the composed shader
  works; old monolithic `.frag`/`.vert` deleted.
- Follow-up candidates (not done): uniform-location caching (still
  `glGetUniformLocation` per frame), pre-existing ripple zoom-speed TODO.

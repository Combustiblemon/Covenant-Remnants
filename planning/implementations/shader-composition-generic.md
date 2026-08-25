# Generic shader composition system

Extracted the composition architecture (layers = GLSL module + Kotlin class assembled into one
program, solo-layer debug, module-tagged errors) out of `ShieldShader` into a reusable framework.
Shield is the first and only consumer for now (developer ruling: other shaders —
MinimalShader/PolygonShader/SlipspacePortalEntityShader — get migrated or discarded later).

Full usage documentation lives in [../../docs/shader-composition/](../../docs/shader-composition/README.md)
(developer-facing docs folder; see DOCUMENTATION.md).

## What was built

New package `utils/render/composition/`:

- **`ShaderLayer`** — generic layer interface (was `shield/ShieldLayer`): `layerName`,
  `moduleFile`, `stubSource`, `uploadUniforms(program, viewport)`, `advance(dt)`.
- **`ComposedShaderProgram`** — the engine; owns program lifecycle only (owner keeps VAO/VBO,
  render loop, GL state, shared uniforms). Constructor: `(systemName, vertexShaderFile,
  baseFragmentFile, layers, placeholders)`. Absorbed from `ShieldShader`: marker splice at
  `// {{LAYER_MODULES}}`, stub substitution for disabled layers, placeholder replacement after
  concatenation (values asserted single-line so the error line map stays valid),
  `SourceSegment` line map, driver-log error translation (NVIDIA `0(123)` + AMD/Intel
  `ERROR: 0:123:`), numbered assembled-source dump tagged `[systemName]`, eager compile in init
  (GL thread required). API: `programId`, `ensureUpToDate()` (debug pickup + lazy rebuild, called
  by owner at render start), `setEnabledLayers`, `getLayerNames`, `uploadLayerUniforms(viewport)`,
  `advance(dt)`, `dispose()`.
- **`CompositionDebug`** — object registry: `registerSystem` (called from program init),
  `systems()`, `setEnabledLayers(system, layers?)` (null/empty clears), `enabledLayersFor`.
  Backed by `ConcurrentHashMap`s. Systems appear once first instance constructed.

## Changes to existing code

- `ShieldShader` slimmed to owner role: geometry, polygon pipeline, sweep position, bbox, GL
  state, shared uniforms; program lifecycle delegated to
  `ComposedShaderProgram("shield", ...)` with `POINT_COUNT_PLACEHOLDER` passed via the
  placeholders map. Deleted: own assembly/compile/error-translation code, enabled-layers state,
  `companion globalEnabledLayers`.
- Shield layer classes implement `composition.ShaderLayer`; `shield/ShieldLayer.kt` deleted.
- Console command **`ShaderLayers`** replaces `CovenantShieldLayers` (deleted): generic over any
  registered system — `ShaderLayers` lists systems; `ShaderLayers shield border,fill` restricts;
  `ShaderLayers shield all` clears the override (cleaner than the old emptySet workaround).
  Registered in `data/console/commands.csv`. Talks only to `CompositionDebug`.

## Key decisions

- Engine owns program lifecycle ONLY — geometry/orchestration stay with the owner, keeping the
  engine reusable for shaders with different geometry.
- `systemName` shared by all instances of a system (one per ship) on purpose: debug override
  applies to all.
- `ShaderLayers <system> all` clears the override via `setEnabledLayers(system, null)`;
  `ensureUpToDate` re-expands to all layers when no override present.
- Docs went to `docs/shader-composition/` (developer-facing usage docs) per developer instruction;
  planning/ keeps the change history. DOCUMENTATION.md notes the split.

## Files

**New:** `src/.../utils/render/composition/{ShaderLayer,ComposedShaderProgram,CompositionDebug}.kt`;
`src/.../commands/ShaderLayers.kt`;
`docs/shader-composition/{README,creating-a-system,writing-layers,debugging,example-shield}.md`
**Modified:** `src/.../utils/render/ShieldShader.kt`; six `src/.../utils/render/shield/*Layer.kt`
(interface swap); `data/console/commands.csv`
**Deleted:** `src/.../utils/render/shield/ShieldLayer.kt`; `src/.../commands/CovenantShieldLayers.kt`

## Open items

- **Not yet verified in-game.** Checklist: shield renders identically; `ShaderLayers` lists
  `shield` once in combat; `ShaderLayers shield border` isolates; `ShaderLayers shield all`
  restores; mid-combat rebuild no crash.
- Future: migrate or discard MinimalShader / PolygonShader / SlipspacePortalEntityShader
  (developer decides per shader).
- Follow-up candidate: uniform-location caching in layers (still `glGetUniformLocation` per frame).

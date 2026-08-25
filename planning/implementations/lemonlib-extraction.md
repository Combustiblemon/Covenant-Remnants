# LemonLib extraction

Extracted the shader composition system + generic utils into a standalone library mod
**LemonLib** (`Starsector/mods/LemonLib/`, mod id `combustiblelemon_lemonlib`, package
`org.combustiblelemon.lemonlib`, jar `jars/LemonLib.jar`). Motivation: developer plans 3 mods;
Starsector mods share a classpath, so copying the same packages into several mod jars risks
first-loaded-wins class conflicts — a library mod (LazyLib/MagicLib pattern) is the ecosystem
standard.

## What moved (Covenant to LemonLib)

| From (covenantremnants) | To (lemonlib) |
| --- | --- |
| `utils/render/composition/{ShaderLayer,ComposedShaderProgram,CompositionDebug}` | `render.composition` |
| `utils/render/Shader` interface | `render` |
| `getShaderCode` (from `utils/Utils.kt`) | `render` (`ShaderCode.kt`) |
| `utils/render/shield/PolygonSmoothing` + `SmoothingMethod` | `geometry` |
| Geometry/math from `Utils.kt`: `rotatePointByDegrees`, `normalize`, `getPercentage`, `isPointInPolygon` (now public), `getRandomPointsInPolygon`, `getPerpendicularIntersectionPoint`, `getPointAlongSegmentFromEnd`, `hasPerpendicularIntersectionPoint` | `geometry` (`Geometry.kt`) |
| ShipAPI helpers from `Utils.kt`: `isShieldFacingShip`, `getLocationInFrontOfShip`, `getVelocityVectorInFacing`, `getShipDimensions`, `getShipPolygonInWorld` | `ship` (`ShipUtils.kt`) |
| `commands/ShaderLayers` + its commands.csv row | `commands` + LemonLib's `data/console/commands.csv` |
| `docs/shader-composition/` (5 usage docs) | `LemonLib/docs/shader-composition/` |

Bodies moved verbatim (package/import renames only). `Utils.kt` now holds just `logText` +
`getUnapplicableReasonForHullmod`.

## Covenant changes

- Imports updated across ~17 files (shield stack, slipspace, 5 hullmods, `CovenantShieldsStats`
  got explicit lemonlib imports next to its `utils.*` wildcard).
- The three `Shader` implementors (`ShieldShader`, `MinimalShader`, `SlipspacePortalEntityShader`)
  import `org.combustiblelemon.lemonlib.render.Shader`.
- `mod_info.json`: LemonLib dependency added (first in the list).
- `build.gradle.kts`: `compileOnly(files("../LemonLib/jars/LemonLib.jar"))`.
- `commands/ShaderLayers.kt` + its commands.csv row removed (served by LemonLib now).
- `docs/shader-composition/` deleted here — docs live in the LemonLib repo (docs follow code).

## Build order

**Automated via Gradle composite build**: Covenant's `settings.gradle.kts` has
`includeBuild("../LemonLib")`; the dependency is `compileOnly("org.combustiblelemon:LemonLib")`
(versionless — substitution matches on group:name, always the local build),
which the composite substitutes with LemonLib's compiled classes (NOT the runtime jar — compiling
against `jars/LemonLib.jar` made the IDE language server lock it and block replacement on Windows).
`compileKotlin` additionally depends on the included build's `:buildMod` so the runtime jar is
refreshed too. Both `buildMod` tasks use rename-aside replacement: a locked previous jar is renamed
to `<name>.stale-<timestamp>` (cleaned up on later builds) and the fresh jar takes its place; if
even rename fails, the build stops with a clear "close the holder" error. LemonLib still builds
standalone with its own `gradlew buildMod`. Runtime: Starsector's shared mod classloader + the
mod_info dependency (load order + clear missing-dependency error).

## Key decisions

- Shield-only consumer for now: `MinimalShader`/`PolygonShader`/`SlipspacePortalEntityShader` keep
  their monolith form (only import swaps); migrate-or-discard later, per shader.
- Console Commands stays an optional runtime dependency of LemonLib (not in its mod_info
  dependencies) — only the `ShaderLayers` command touches it.
- LemonLib has no `modPlugin` — pure code + one commands.csv.
- Git for LemonLib: developer initializes it themselves.

## Open items

- **Not yet verified in-game**: both mods enabled, shield identical, `ShaderLayers shield border`
  works, missing-LemonLib error shows when disabled.
- Evaluate for LemonLib later: `Data.kt` (Covenant shield data — likely stays),
  `CubicCurve2D.kt`, `reflection/ReflectionUtils.kt`, `extensions/`.
- LemonLib versioning discipline once mod #2 exists (Covenant currently tracks the jar by path).

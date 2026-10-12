# Shield outline: Taubin smoothing + margin (2026-10-12)

Developer report with screenshot: the smoothed shield outline sat visibly inside the ship sprite.
Cause: the outline used `SmoothingMethod.SPLINE`, which is plain Laplacian smoothing (8 iterations,
factor 0.3). Laplacian moves each vertex toward the midpoint of its neighbours, so convex outlines
shrink every pass. The render plugin partly hid this with an anisotropic polygon scale of
(1.05, 1.1) about the ship centre.

## Rulings (developer 2026-10-12)

- Method: **both** — Taubin smoothing (no shrink) plus an outward offset along vertex normals.
- Margin: **tunable, default 4** world units, via the existing `tune()` console-command pattern.

## What was built

LemonLib (`src/org/combustiblelemon/lemonlib/geometry/PolygonSmoothing.kt`):

- `SmoothingMethod.TAUBIN` + `PolygonSmoothing.taubin(points, iterations, lambda, mu)`: each
  iteration is a Laplacian step with `lambda` (0.5, shrinks) followed by one with `mu` (-0.53,
  inflates). Sanity check on a jagged 40-gon: area 3.13 original, 2.95 after Laplacian 0.3×8,
  3.14 after Taubin ×8.
- `PolygonSmoothing.offsetAlongNormals(points, distance)`: mitre offset along the vertex normal
  (normalised sum of adjacent edge normals, scaled so each edge moves by `distance`, mitre capped
  at 2×distance). Outward direction from the shoelace sign, so hull winding order does not matter.
  Checked on CW and CCW squares.

Covenant Remnants:

- `utils/render/shield/ShieldOutline.kt` — tunable registry (same shape as `FleetSlipspaceJump`):
  `MARGIN` 14 (4 at first; +10 per developer 2026-10-12 after the in-game look), `ITERATIONS` 8, `LAMBDA` 0.5, `MU` -0.53, `SCALE_X` 1, `SCALE_Y` 1.
- `utils/render/ShieldShader.kt` — `updatePolygon` runs Taubin with the tunables when the method
  is `TAUBIN`, then `offsetAlongNormals(margin)` for every method, before scale / transform.
- `plugins/CovenantShieldsRenderPlugin.kt` — method `SPLINE` → `TAUBIN`; the (1.05, 1.1) scale
  workaround is replaced by the `SCALE_X` / `SCALE_Y` tunables, default (1, 1). Stated here
  because it is a visible change: the old scale pushed the bow/stern out 10 % and the sides 5 %;
  the margin now does that job uniformly.
- `commands/ShieldTune.kt` + `data/console/commands.csv` — `ShieldTune [<name> <value> | reset
  [<name>]]`, Console Commands only (compile-only dependency, isolated in `commands/`).
- Docs: `LemonLib/README.md`, `LemonLib/docs/shader-composition/example-shield.md` pipeline list.

Build: `gradlew buildMod` (LemonLib built first through the composite build) — both jars rebuilt
2026-10-12.

## Round 2: corner rounding (developer 2026-10-12, after the first in-game look)

The outline hugged the hull but still showed the polygon corners: Taubin and the offset only move
the 10-56 bound points, they cannot round a corner. Rulings: adaptive Catmull-Rom (recommended
option), keep the shader point capacity at 128.

- LemonLib `PolygonSmoothing.catmullRomAdaptive(points, maxPoints, degreesPerSegment, maxPerEdge)`:
  centripetal Catmull-Rom (alpha 0.5, no cusps) through every point; each edge gets
  `ceil(turn / degreesPerSegment)` segments (1..maxPerEdge) from the sharper of its two end
  turns, and if the total exceeds `maxPoints` the extra segments are scaled down proportionally.
  Straight runs cost nothing, corners get the samples. Python check: a square at 15 deg / 6 ->
  24 points, a jagged 56-gon -> 94 points (under 128).
- `ShieldOutline`: `CURVE_DEG` 15 (0 = off), `CURVE_MAX` 6.
- `ShieldShader.updatePolygon`: Taubin -> `catmullRomAdaptive(..., ShieldProgram.POINT_CAPACITY)`
  -> `offsetAlongNormals(margin)`; `calculateMaxPointsAfterSmoothing` now returns the capacity
  (the upload already uses the live count). Fighter hulls (4-10 points) round to at most 60.

## Open items

- In-game check per [../TODO.md](../TODO.md) (Rendering / shaders): rim clearance on all hulls,
  corner rounding on the bow / wing tips, collapse extrusion / sweep alignment, frame cost on the
  cruiser3 / supercap hulls (now up to 128 points per pixel loop). Bake dialled-in values into the
  defaults.
- The margin is in world units, so it does not grow with zoom; if a constant screen-pixel gap is
  wanted instead, divide by `viewport.viewMult` in `updatePolygon`.

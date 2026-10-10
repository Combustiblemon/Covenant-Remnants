# Implementation — Covenant shield at UI scaling (2026-10-10)

The developer reported that the shield rendering breaks at 130 % UI scaling, and at any scale other
than 100 %.

## Root cause

- **Starsector has two pixel units under UI scaling.** The game was checked in the decompiled
  0.98a-RC8 `combat.java` (startup, around `GL11.glViewport`):
  - `glViewport` covers the **physical** pixels (`Display` size × `getPixelScaleFactor()`).
  - The combat viewport, `convertWorldXtoScreenX/Y` and the ortho projection work in **virtual
    pixels**: physical ÷ `screenScaleMult`. The API doc on `SettingsAPI.getScreenWidth()` says the
    same.
- **`ShieldShader` used a hardcoded 1920 × 1080.** `updateScreenResolution()` existed but nothing
  called it.
  - The polygon points come from `convertWorldXtoScreenX/Y`, so they are in virtual px.
  - The bounding-box quad (`updateQuadVertices`, virtual px → NDC) and the per-pixel position
    (`pixelPos = TexCoord * resolution` in `base.frag`) both divided by 1920 × 1080.
  - At 130 % on a 1080p screen the virtual size is about 1477 × 831. The quad landed in the wrong
    place and every pixel tested the polygon at the wrong position.
  - So the shield was only right on 1920 × 1080 at 100 %. Any other resolution would have broken it
    too.
- **The hit-ring distortion mixed the two units.**
  - `ShieldScreenCopy.copyRegion` passed the virtual-px bounding box straight to
    `glCopyTexSubImage2D`, which takes physical px, so it copied the wrong part of the screen.
  - `distortion.glsl` added the virtual-px ring offset to physical `gl_FragCoord`.
- The rupture renderer already did this correctly (`RuptureView.screenWidth = viewport.visibleWidth / viewport.viewMult`,
  offsets in UV space), so the shield now follows the same pattern.

## Developer ruling (2026-10-10)

> "We need to make everything scale with the scaling factor AND look the same; it may not be the
> same width or height but it needs to look the same way, just scaled."

Every shield width (border, sweep edge, hexes, plasma, distortion rings) stays in virtual px or
world ÷ `viewMult`. Both grow with the UI scale the same way the hull does, so the shield looks like
the 100 % shield, only bigger.

## Fix

| File | Change |
| --- | --- |
| `src/.../utils/render/ShieldShader.kt` | New private `updateScreenSize(viewport)` sets `screenWidth/Height = visibleWidth/Height / viewMult`. It is called at the start of `updatePolygon` and `render`, every frame. The dead `updateScreenResolution()` is removed. The `resolution` uniform now carries the virtual size. |
| `src/.../utils/render/shield/ShieldScreenCopy.kt` | `copyRegion(x, y, w, h, screenW, screenH)` scales the virtual-px region to physical px by `viewport / screen` (floor min, ceil max + 1, clamped). The ratio also covers HiDPI. |
| `src/.../utils/render/shield/DistortionLayer.kt` | Passes the virtual screen size to `copyRegion`. |
| `data/shaders/shield/distortion.glsl` | `screenUv = (gl_FragCoord.xy - viewport.xy) / viewport.zw + offset / resolution`: the offset becomes UV, so it no longer mixes units. |
| `data/shaders/shield/sweep.glsl` | Drops the `resolution.x / 1920` factor on the sweep edge width. It was always 1.0 before, because the size was hardcoded. With the real size it would have shrunk the edge relative to the rest of the shield (ruling above). |

Checked:

- The assembled shield program (all eight layer modules) compiles and links offline on the NVIDIA
  driver (moderngl). `resolution`, `distortionViewport`, `distortionScreen` and `sweepEdgeWidth`
  are all active.
- `gradlew buildMod` is green.

## Audit of the rest of the mod (2026-10-10)

The rest of the mod's screen-space code was checked for the same unit mix (screen sizes, raw GL
pixel calls, `gl_FragCoord`, mouse coordinates, line widths, fixed pixel sizes in shaders). LemonLib
was checked too and has no screen-space code.

| Where | Verdict |
| --- | --- |
| `plugins/FleetSlipspaceJumpRenderPlugin.kt`, aiming rings | **Fixed.** `glLineWidth(2f)` is physical px, so the rings got relatively thinner at higher UI scales. It is now `2f * screenScaleMult`. |
| `utils/render/rupture/` (`RuptureRenderer.copyScreen`, `ruptureSprite.frag` heat haze / refract ring) | OK. It copies the whole GL viewport (physical), samples at `gl_FragCoord / viewport`, and the push is a fraction of the screen width (`halfWidth / view.screenWidth`), so it is scale-invariant. This covers the combat jump, the Ark portals and the campaign slipspace jump (same renderer). |
| `RuptureView` (spheres, Ark tube, sprites) and `blink/BlinkRippleShader.kt` | OK. Screen size is `visibleWidth / viewMult` and NDC is derived from it. |
| `ruptureSprite.frag` `fwidth` minimums | OK. These are anti-aliasing floors of one physical pixel, which is correct at any scale. |
| `plugins/FleetSlipspaceJumpTargetingPlugin.kt` mouse aim | OK. `SettingsAPI.getMouseX/Y` are already virtual px (API doc). |
| Shield layers (border, honeycomb, fortress, plasma, debug) | OK. Widths are virtual px or world ÷ `viewMult`. |
| `plugins/CovenantShieldsUIRenderPlugin.kt` | OK. MagicUI does its own HUD scaling. |

Found while auditing (not a scaling issue): `plugins/CovenantShieldsUIRenderer.kt` is dead code.
`render()` draws nothing and `renderCooldownBar` is never called. It is left as is.

## In-game check

**Passed.** Developer 2026-10-10: "looks good in combat, scaling and ultrawide". The checklist:

Use a Covenant shield ship at **100 %, 130 % and 150 %** UI scale:

1. The shield sits on the hull. The outline matches, and nothing is clipped, offset or squashed.
2. It looks the same as at 100 %, only bigger: border, hexes, sweep edge and plasma flares keep
   their proportions to the hull.
3. Shield hits: the refraction rings bend the background right under the shield, with no stale or
   smeared patches inside the shield's box.
4. Zoom in and out at 130 %: the shield stays aligned.
5. Fleet Slipspace Jump aiming rings at 130 %: same thickness relative to the ships as at 100 %.

## Note

Rule for any new screen-space GL code in the mod: positions from `ViewportAPI` are virtual px. Take
the screen size from `viewport.visibleWidth / viewport.viewMult`, never from a constant or
`Display`. Convert to physical px only for raw GL calls (`glCopyTexSubImage2D`, `glScissor`,
`gl_FragCoord`) by the ratio of `GL_VIEWPORT` to that size.

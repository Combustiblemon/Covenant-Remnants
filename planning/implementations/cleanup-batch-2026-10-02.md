# Implementation — bug/cleanup batch (2026-10-02)

Non-overlapping work done while the developer reworked ship bounds / slots / engines in the
in-game ship editor. Nothing under `data/hulls/`, `data/variants/` or `data/weapons/` was touched.

## 1. Slipspace Drive crashed on activation (bug)

`SlipspaceDriveRenderPlugin` loaded three sprites at construction — `RingRenderer("covenant_remnants",
"portal_outer_loop")`, `RingRenderer(..., "portal_inner_loop")`, `getSprite(..., "portal_noise_long")`.
None is registered in `data/config/settings.json`: the textures were deleted in commit `9b5ecc1`
("cleanup") and the ids dropped in `a22e7d8`, but the plugin kept referencing them. Per
starsector-knowledge (0.98a-RC8, `StarfarerSettings.o00000`), the two-arg `getSprite` **throws**
`RuntimeException("Texture [..] from category [..] not found")`, and `RingRenderer(cat, key)` calls
that same form. `SlipspaceDrive.apply` builds the plugin on first apply, so any use of
`covenant_remnants_slipspacedrive` (frigate1 / frigate3 / destroyer2) crashed the game.

Developer ruling: leave the art (drive visuals will be redone), add a guard.

- Each sprite is resolved with `runCatching { … }.getOrNull()`; a failure logs one warn naming the
  missing key.
- `renderPortal`: ring renderers are skipped when null; a null noise sprite draws the inner disc as a
  flat `Color(139,130,251)` circle instead of the scrolling textured polygon.
- Old textures (for the art pass) are recoverable from `9b5ecc1^` at
  `graphics/covenant_remnants/fx/slipspace/{slipspace_portal_ring_outer,slipspace_portal_ring_inner,noiseTexture}.png`.

## 2. Slipspace Drive per-combat reset (bug)

`SlipspaceDrive` created its render plugin once per script instance with no per-combat reset, so if
system-script instances outlive a battle the portal stopped rendering after the first one.
Now mirrors `CovenantShieldsStats`: the script remembers the `CombatEngineAPI`; when it changes,
`renderPlugin`, `isIn` / `isActive` / `isOut`, `launchStartTime` and `portalExit` are reset.

## 3. Dead monolith shaders deleted

`PolygonShader` and `MinimalShader` had no instantiation anywhere in the mod (developer ruling:
delete both). Removed with `data/shaders/minimalShader.frag` / `.vert`. `shield_noise` (only read by
`PolygonShader`) went with it; `graphics/covenant_remnants/fx/shield/noise.png` is now an orphan,
kept for the developer. `SlipspacePortalEntityShader` stays — it is live (campaign portal entity).
The sibling `ShadyLib` mod carries its own copies of these files; untouched.

## 4. Ripple TODO closed

The ripple layer was replaced by the plasma layer on 2026-10-02; the only leftover was a comment in
`ShieldShader.render` ("viewport scale for ripple calculations"), reworded.

## 5. `CubicCurve2D` → LemonLib

Generic cubic-bezier easing (used by `SlipspaceDrive` and `SlipspaceDriveRenderPlugin`) moved
verbatim to `org.combustiblelemon.lemonlib.geometry.CubicCurve2D` (developer ruling). Covenant's
copy deleted, two imports updated, LemonLib README contents table updated. Remaining candidates
stay: `extensions/CampaignFleetAPI.kt` (`isInSlipspace`) and `Data.kt` are Covenant-specific.

## Files

Covenant Remnants:

- `src/org/combustiblelemon/covenantremnants/plugins/SlipspaceDriveRenderPlugin.kt` — guard,
  placeholder render, LemonLib import.
- `src/org/combustiblelemon/covenantremnants/systems/SlipspaceDrive.kt` — per-combat reset,
  LemonLib import.
- `src/org/combustiblelemon/covenantremnants/utils/render/ShieldShader.kt` — comment only.
- Deleted: `utils/render/PolygonShader.kt`, `utils/render/MinimalShader.kt`, `utils/CubicCurve2D.kt`,
  `data/shaders/minimalShader.frag`, `data/shaders/minimalShader.vert`.
- Docs: `README.md` (shader example pointer), `planning/ARCHITECTURE.md` (Slipspace render note,
  Shader framework), `planning/TODO.md`.

LemonLib (`../LemonLib/`, uncommitted — developer commits):

- `src/org/combustiblelemon/lemonlib/geometry/CubicCurve2D.kt` (new), `README.md`,
  `jars/LemonLib.jar` (rebuilt by Covenant's composite build).

## Verification

- `./gradlew.bat buildMod` — builds clean 2026-10-02 (LemonLib jar carries
  `lemonlib/geometry/CubicCurve2D.class`; Covenant jar has no `PolygonShader` / `MinimalShader` /
  `utils/CubicCurve2D` classes and no `minimalShader.*`).
- `grep -rnE "PolygonShader|MinimalShader|covenantremnants\.utils\.CubicCurve2D|shield_noise" src/ data/`
  → no matches.
- In-game (developer, pending): frigate1 in the simulator, fire the slipspace drive — no crash, one
  "Portal sprite … is not registered" warn in the log, placeholder portal draws, ship jumps. Second
  battle in the same session — portal still draws. `ShaderLayers shield …` unaffected.

## Open items

- Drive portal art pass (developer) — see TODO "Drive portal art".
- `noise.png` orphan — delete or reuse in the art pass.
- In-game checks above.

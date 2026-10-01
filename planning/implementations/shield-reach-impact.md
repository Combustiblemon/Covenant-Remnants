# Implementation — Halo: Reach Elite shield look: damage shimmer, break, recharge

The Covenant shield showed damage as a global tint (`fill.glsl` brightened the fill ×(2 − hp) and
boosted alpha as HP dropped) plus a white expanding ring per hit (the `ripple` layer). The break was
a cloak deactivate + EMP arcs. This pass replaces all of that with the Halo: Reach Elite energy
shield: plasma shimmer + rim glow that flares on hits and intensifies as the shield weakens, a
collapse when it breaks, and the recharge wave. Developer decisions 2026-10-02 (AskUserQuestion).

## Source: the Reach editing kit

`E:\SteamLibrary\steamapps\common\HREK`. The shield overlay is a dedicated, fixed-function tag
class `.shield_impact` (no render_method; `tags\globals\rasterizer_globals` binds it to
`rasterizer\shaders\shield_impact.*`). Its HLSL is readable:
`tags\shaders\explicit\shield_impact.hlsl_include` (+ `_registers`, `_procedural_uv`).

- Per-hit look: `fx\shield_impacts\elite_3p.shield_impact` (bound in `elite.model` damage section
  `shield`, and as the model's `shield impact parameter override`).
- Break: `objects\characters\elite\fx\shield\shield_collapse.effect` (model damage-section
  transition effect at marker `fx_shield_core`): event `flash` 0.1 s (lens flare + light + area
  screen effect + sound `shield_pop_elite`), `particles` 0.2 s (`shield_sphere_plasma` mesh,
  `shield_collapse_spark`, `shield_collapse_ring`), `shield_collapse` 0.25 s starting at 0.05 s
  (`elite_3p_collapse.shield_impact`). `shield_down.effect` (biped) is the lingering sparks on the
  body while down; not used.
- Recharge: `shield_recharge.effect` (`elite_3p_recharge.shield_impact`, 2 s) preceded by
  `shield_precharge.effect` 0.25 s (sparks + ring + light).
- Bitmaps: `fx\bitmaps\plasma\plasma_clouds_512_a/b` (plasma noise, already exported for the
  rupture), `test\chris\noise\blur_noise256_a/b` (vertex oscillation, skipped).
- Sound: `sound\characters\elite\shield_pop_elite` = FMOD bank samples 5741–5743.

Dumps (`dump_tags.ps1`, 16 and 32 samples), the generator and the sound script live in
`assets/reference/elite-shield/reach-data/` (gitignored; README there).

### The shader, decoded

Pixel stage of `shield_impact.hlsl` (vertex stage = extrusion along the normal + noise wobble):

```
edge_fade    = cosine(view, normal)           // 0 at the silhouette, 1 facing the camera
edge_linear  = sat(min(fade*s.x+s.y, fade*s.z+s.w))   // tent: Inner 0 → peak at Center 0.15 → 0 at Outer 0.75
edge         = edge_linear^4                           // rim glow just inside the silhouette
edge_plasma  = tent Inner 0.5 → Center 0.75 → Outer 1.0 // interior band (0 at the rim and at dead centre)
n1 = tex(noiseA, uv*TILE + off(t)); n2 = tex(noiseB, uv*TILE − off(t))
plasma       = sat(1 − |n1 − n2|) ^ mix(EDGE_SHARP 5, CENTER_SHARP 85, edge_plasma)  // thin filaments in the band
hit_color    = HIT_COLOR * sat(1 − dist(p, hitPos)/hitRadius)
final        = edge*EDGE_GLOW + (PLASMA_EDGE*edge_plasma + PLASMA_CENTER + hit_color) * plasma   // additive
```

Function inputs: `shield_intensity` (runtime, spikes on a hit and decays — not in the tags),
`shield_vitality` (HP fraction), `event_time` (collapse/recharge progress), `hit_time`.

Per-hit tag (`elite_3p`): glow radii 0.75/0.15/0; plasma radii 1.0/0.75/0.5; tiling 0.75, scroll
1.5; sharpness 5 edge / 85 centre; hit time 0.35 s. Edge Glow Color = f(intensity) black →
(0.106,0.290,0.843) fast; Edge Glow Intensity = f(vitality) 1.0@0 → 0.5@1; Plasma Center Color
black until intensity ≈ 0.4 then → (0.208,0.294,0.871), intensity 1.5@0 → 1.0@1; Plasma Edge Color
→ (0.184,0.408,1.0) by 0.4, intensity 5@0 → 2@1; Hit Color (0.278,0.647,1.0) → black over the hit
time, ranged by vitality (black at full HP); Hit Radius 0.25@0 → 0.10 by vitality 0.5.

Collapse tag (`elite_3p_collapse`, x = event time): Edge Glow (0.208,0.565,0.980) → black by 0.45,
intensity 10; Plasma Center (0.118,0.318,1.0) → black by 0.45, intensity 5; Plasma Edge constant
(0.122,0.341,1.0), intensity 10 → 0 by 0.61; tiling 1.75, scroll 0.25, centre sharpness 35;
extrusion 0.035 → 0.075 m (≈ +2 % → +4 % of a 2 m body). Screen effect: 0.5 s, exposure boost 3,
colour filter (0.667,0.784,1.0).

Recharge tag (`elite_3p_recharge`, x over 2 s): Edge Glow Color and Plasma Edge Color ramp 0 → peak
at 0.5 → 0 (peaks (0.208,0.384,0.980) / (0.118,0.400,1.0)), intensities 2 / 1; Hit Radius 0.1 →
1.0 by 0.7 (the wave sweeps out from the core); Hit Color (0.129,0.204,0.643) → black.

## Decisions

| Topic | Decision |
| --- | --- |
| Damage model | **Hybrid**: Reach hit flares (decaying, vitality-scaled) + faint always-on plasma/rim baseline growing as HP drops. Replaces the fill tint. |
| Per-hit | **Reach hit spot** at the impact point; **ripple layer dropped**. |
| Hit filter | **Every hit** (projectiles, missiles, beam ticks), strength = `max(0.25, level)`. The old rule (raw > 100, no beams) is gone. |
| Break | Shader collapse flare + polygon expansion, **keep EMP arcs**, **pop sound**, **ring + spark particles**, **0.1 s flash**. |
| Recharge wave | **Included.** Fires on every regen paused → running transition while visible (post-hit pause ending, **Fortress ending included**), and **after a break once the activation sweep completes** (effectLevel reaches 1). |
| Textures | **Copy** `plasma_clouds_512_a/b.png` to `graphics/fx/shield/`, keys `shield_plasma_clouds_a/b`. Oscillation textures skipped. |
| Hit distortion (2nd round, after the first in-game test) | **Expanding refraction ring** from the hit point; **amplitude and travel both scale with level** (small hits: weak bend, dies after ~25 % of the body; big hits: strong, crosses the whole shield); the Reach hit colour spot is **kept and boosted** (mostly unmasked by the filament pattern). |

### Defaults chosen by the assistant (tunables — not asked, flagged here)

All constants live in `ShieldImpactEffect` unless noted.

- Reach's `shield_intensity` = `max(baseline(v), max over hits of strength × (1 − age/0.35))`,
  `baseline(v) = mix(0.05, 0.45, 1 − v)` (`BASELINE_FULL/EMPTY`; 0.45 sits just past the 0.4 knee
  of the Reach colour curves).
- Hit colour is ranged by vitality in Reach (black at full HP); floored with `HIT_COLOR_MIN` 0.3 so
  hits always read. Hit radius = Reach fraction × body radius (`HIT_RADIUS_MULT` 1).
- 2D mapping: fresnel → "depth into the body" = outline distance / body depth, where body depth =
  half the shorter ship-local extent (screen px) and body radius = farthest polygon point. Reach's
  radii are in cosine terms, so they map without inversion.
- Vertex extrusion/oscillation reduced to a polygon-scale pulse: collapse ×1.02 → ×1.04
  (`COLLAPSE_EXPAND_*`); no hit-time push, no noise wobble.
- Collapse timeline: flash 0 → 0.1 s (`FLASH_*`, colour = the screen effect's filter, strength
  0.8); event 0.05 → 0.30 s; fill/border/honeycomb fade out over the first 45 % of the event
  (`COLLAPSE_BODY_FADE_END`); then the shield is not drawn while the cloak winds down (no 2 s wipe).
- Plasma layer is drawn **last** (after Fortress): the lattice's mix toward opaque white would wipe
  hits otherwise. Plasma colours are not tinted by the Fortress level.
- Recharge wave 1.2 s (`RECHARGE_DURATION`), centred on the ship.
- **Rim glow textured** (deviation from Reach, developer request 2026-10-02 after the first in-game
  test: the ring read as a flat colour band): `edge * EDGE_GLOW * mix(1, plasmaBroad, k)` where
  `plasmaBroad = plasmaBase ^ EDGE_SHARPNESS` (`plasma_GLOW_NOISE` in `plasma.glsl`; developer set
  k = 1.0 in-game).
- **Hit spot unmasked** (`plasma_HIT_UNMASKED` 0.75 in `plasma.glsl`): `hit * mix(plasma, 1, 0.75)`
  instead of Reach's `hit * plasma`, which was nearly invisible here.
- **Distortion rings** (`DistortionLayer` constants): level floor 0.25; travel 0.25 → 1.2 × body
  radius; duration 0.35 → 0.8 s; amplitude 3 → 12 su; ring half-width 20 → 50 su; strength fades
  linearly with progress; ring profile `sin(πp)(1 − p²)` across the half-width, radial direction;
  up to 8 rings (oldest by progress evicted). Rings are cleared on a break.
- Noise: fixed world tile (`TILE_WORLD` 200 su per repeat at tiling 1), scroll
  `SCROLL_UV_PER_SEC` 0.2 × Reach speed, the two maps scrolling in different directions.
- Additive light is composited inside the existing straight-alpha pass (see `plasma.glsl`): output
  `a' = max(a, max(premul))`, `rgb' = premul / a'`, which reproduces `rgb·a + light + bg·(1 − a')`
  — exact additive light, except the background is dimmed by `a' − a` where the light is bright.
  No blend-func change, stubs and `main()` untouched.
- Break particles (render plugin companion): 28 ring puffs (`addSmoothParticle`, 0.3–0.5 s, size
  12 % of the collision radius clamped 12–60) drifting outward at 150 su/s, 28 sparks
  (`addHitParticle`, 4–9 px, 0.2–0.45 s, 200–500 su/s outward ±35°), one centre flare (2.5 ×
  collision radius, 0.15 s).

## What was built

### Plasma layer — `data/shaders/shield/plasma.glsl` + `utils/render/shield/PlasmaLayer.kt`

One layer for hits, baseline, collapse, recharge and flash (Reach swaps tags, not shaders).
Owns the two samplers (`plasmaNoiseA/B`, units 0 and 1, bound every upload — the program can be
rebuilt by `ShaderLayers`), the bands as Reach scale/offset quads (`bandScales(inner, center,
outer)` in Kotlin), sharpness, uv scale (`viewMult × tiling / TILE_WORLD` — pattern locked to the
hull, zoom-independent), scroll offsets, the three colours × intensities, up to 8 hit spots
(screen px + colour each), and the flash. `textureLod(…, 0)` because the fragment is past a
non-uniform discard. Stub = identity, no samplers.

### Distortion layer — `data/shaders/shield/distortion.glsl` + `utils/render/shield/DistortionLayer.kt` + `ShieldScreenCopy.kt`

Two hooks in `base.frag`'s `main()`: `warp = distortion_offset(p)` first, then
`FragColor = distortion_compose(shieldFrag(p + warp), p, warp)`. The whole mix chain is evaluated at
the displaced position, so the hexes, border and plasma bend with the ring (developer request
2026-10-02, second test); the polygon mask stays on the real pixel. Where the summed offset is
≥ ¼ px the compose step samples the framebuffer copy at the displaced position (`gl_FragCoord`
relative to the GL viewport) and outputs the shield colour alpha-composited over it with alpha 1;
elsewhere the plain blended colour passes through, so the blend func is untouched. Inside the
polygon only.
`ShieldScreenCopy` is one viewport-sized texture shared by all shields; each shield copies only its
bounding box (`glCopyTexSubImage2D`) in `uploadUniforms`, right before its draw and only while a
ring is running, so the copy already holds earlier shields and idle shields cost nothing. Texture
unit 2 (saved/restored by `ShieldShader.render`). Waves are ship-local like the hits.

### State — `utils/render/shield/ShieldImpactEffect.kt`

CPU only. Hits stored **ship-local** (rotated by −facing) so they stay on the hull; `vitality`;
collapse / flash / recharge clocks; scroll offsets accumulated and wrapped (no drift, no jump when
the scroll speed switches). `advance(dt)` reduces everything to the per-frame parameter set:
NORMAL = colour(intensity) × intensity(vitality) + hit spots; RECHARGE adds the recharge tag's glow
and band colours and a pseudo-hit at the centre whose radius follows the recharge Hit Radius curve;
COLLAPSE replaces everything with the collapse curves, clears hits, drives `shellScale` and
`bodyFade`.

### Curves — `utils/render/shield/ShieldImpactCurves.kt` (generated)

`assets/reference/elite-shield/reach-data/tools/gen_shield_curves.py` on the 32-sample dumps:
19 curves (`ReachCurve` / `ReachColorCurve` from the rupture package) + the scalar fields of the
three tags. Regenerate instead of editing.

### Textures — `utils/render/shield/ShieldTexture.kt`, `graphics/fx/shield/`, `settings.json`

`RuptureTexture` pattern: sprite → textureId once, REPEAT + LINEAR.

### Owner — `utils/render/ShieldShader.kt`

Layers `fill, border, sweep, honeycomb, fortress, plasma, distortion, debug`. `updatePolygon` multiplies the
scale by `shellScale` (the bounding box follows the scaled points), tracks the ship-local Y extent
and the farthest point for the body metrics, and holds the sweep at full while collapsing.
`updateTime` now forwards to `program.advance`. `updateShieldHPPercent` feeds the plasma vitality.
New API: `triggerHit` (plasma hit + distortion ring), `startCollapse`, `triggerRecharge`,
`isCollapseActive/Finished`, `resetImpact`. `render()` saves/restores `GL_ACTIVE_TEXTURE` and the 2D
bindings of units 0–2. `calculateBoundingBox` hands the box to the distortion layer as its copy region.

### Fill — `fill.glsl`, `FillLayer.kt`

Flat colour only; the HP brightness/alpha tint is gone.

### Ripple removed

`ripple.glsl`, `RippleLayer.kt`, `startRipple/updateRipple`, `triggerRippleAtPoint` (its cooldown
never fired: `lastRippleTriggeredTime` was never written).

### Render plugin — `plugins/CovenantShieldsRenderPlugin.kt`

- `triggerShieldHit(x, y, level)` (ignored while broken / cloak down).
- `triggerRechargeWave()`.
- `triggerShieldPop()`: cloak off + EMP arcs (unchanged) + `startCollapse` + particles + sound
  `covenant_remnants_shield_pop` + queue the post-break wave.
- `render`: draws while the cloak is up or the collapse is active; `suppressUntilOff` after the
  collapse until `effectLevel` is 0, then `resetImpact()`.
- `advance`: `updateTime` (drives the layers), EMP retrigger, the post-break wave once
  `effectLevel ≥ 1`.
- Dead `RippleData`/`activeRipples`/`hexAnimation*`/`wasPressed` removed.

### Stats — `systems/CovenantShieldsStats.kt`

- `ShieldDamageListener`: every hit → `triggerShieldHit(point, level)` (beams included, per tick).
- `ShipListener.advance`: `wasRegenActive` edge (`!broken && !fortress && hp < max && regenMult > 0`);
  paused → running while visible fires `triggerRechargeWave()`. Reset per combat.

### Sound — `sounds/covenant_shield/pop_{1,2,3}.ogg`, `sounds.json`

vgmstream-cli on `HREK\fmod\pc\sfx.fsb` (indices 5741–5743 → `-s 5742..5744`), then
`make_pop_sounds.py` (ffmpeg only: mono downmix, 50 ms tail fade, peak −1 dBFS, 44.1 kHz q6).

## Performance: one shared program, compiled at game start

Second in-game test (2026-10-02): a multi-second hitch when shields first came up. Measured on the
developer's RTX 5080 with a standalone GL context (`time_compile.py`, moderngl, session scratchpad):
one shield program compiles in ~250 ms cold (old and new shader alike), ~1–10 ms when the NVIDIA
driver cache has it. Each ship compiled its own program on first activation (the source embedded
its bound count), so a fleet raising shields after a shader edit stacked cold compiles into seconds.
Developer ruling: compile at game start, hooked into the mod plugin.

- **`utils/render/shield/ShieldProgram.kt`** (new): the one `ComposedShaderProgram` every shield
  uses, built from throw-away template layers, `POINT_CAPACITY` 128 (hulls have 16–64 bound
  points; more is clamped with a warning). `warmUp()` compiles it from
  `CovenantRemnantsPlugin.onApplicationLoad` (GL thread; failure is logged and the first ship
  compiles lazily instead) and logs the compile time.
- **Loop bound is a uniform**: `pointCount` (declared in `base.frag`, uploaded with the points)
  replaces the `const int numPoints = POINT_COUNT_PLACEHOLDER` in all six outline loops, so the
  source no longer depends on the hull and the driver stops unrolling (cold compile ~100 ms instead
  of ~250). The per-frame zero-fill of unused `points[]` slots is gone with it.
- **`ShieldShader`** keeps its own layer instances for per-ship state and uploads their uniforms
  itself, skipping layers disabled by the `ShaderLayers` override
  (`CompositionDebug.enabledLayersFor`); it advances them itself and never disposes the program.
  `PlasmaLayer`/`DistortionLayer` take a nullable ship (null = template instance).
- Not done: on-disk caching of the compiled binary. The driver already does it (NVIDIA/AMD), and
  a single ~100 ms compile at load does not need it.

## Files touched

New: `data/shaders/shield/{plasma,distortion}.glsl`; `src/.../utils/render/shield/{PlasmaLayer,
ShieldImpactEffect, ShieldImpactCurves, ShieldTexture, DistortionLayer, ShieldScreenCopy, ShieldProgram}.kt`; `graphics/fx/shield/plasma_clouds_512_{a,b}.png`;
`sounds/covenant_shield/pop_{1,2,3}.ogg`; `assets/reference/elite-shield/` (local).
Modified: `data/shaders/shield/{base.frag, fill.glsl, border.glsl, fortress.glsl, debug.glsl}`;
`data/config/{settings,sounds}.json`; `src/.../CovenantRemnantsPlugin.kt`;
`src/.../utils/render/ShieldShader.kt`; `src/.../utils/render/shield/FillLayer.kt`;
`src/.../plugins/CovenantShieldsRenderPlugin.kt`; `src/.../systems/CovenantShieldsStats.kt`;
`planning/{TODO,ARCHITECTURE}.md`; LemonLib `docs/shader-composition/{example-shield, debugging,
writing-layers}.md`.
Deleted: `data/shaders/shield/ripple.glsl`, `src/.../utils/render/shield/RippleLayer.kt`.

## Verification

- `.\gradlew.bat buildMod`: clean.
- Assembled fragment shader validated with glslang (`#version 330 core`) in eight layer
  configurations: all layers; plasma only; plasma stubbed; fill + plasma; fortress + plasma; debug
  only; distortion only; all but distortion (`assemble_shield.py` in the session scratchpad mirrors
  `ComposedShaderProgram`).
- In-game 2026-10-02 (developer, supercap1): hit strip and break look right. Follow-ups: the rim
  ring was a flat colour band → textured by the plasma noise (OK at k = 1.0); hits were invisible →
  distortion rings + unmasked hit spot (OK; then made to warp the hexes too, OK); first-activation
  hitch → shared program compiled at game start (OK: no hitch, `ShaderLayers` still works).
- **Signed off** by the developer 2026-10-02 (hits, rings, break, recharge, no hitch).

## Open items

- In-game tuning of every default above (baseline 0.05/0.45, hit floor 0.25, hit colour floor 0.3,
  tile 200 su, scroll 0.2, flash 0.8, particle counts/sizes, recharge 1.2 s).
- Hits are multiplied by `plasma` (Reach-faithful): with centre sharpness 85 they show on thin
  filaments only and may read faint on big hulls — lower the sharpness or add an unmultiplied hit
  term if so.
- Performance: a third outline loop per pixel + two texture fetches + up to 8 hits, baseline always
  on. Profile on the supercaps; hoist a shared `outlineDistance()` helper into `base.frag` if needed.
- Outline wobble (Reach's oscillation textures) skipped; revisit if the flat outline reads static.
- Recharge wave on a manual re-raise without a break: not fired (only regen resume and post-break).
- `ShieldScreenCopy` keeps one GL texture for the app's lifetime (never disposed; the rupture
  disposes its own per renderer). Harmless; add a dispose hook if GL resource audits ever matter.
- Distortion copy cost ∝ bounding-box area per ship with a running ring; worst case a few supercaps
  under fire. Profile with the rest.

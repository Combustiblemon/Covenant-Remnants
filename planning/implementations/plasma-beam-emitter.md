# Implementation — Plasma Beam Emitter, Halo: Reach focus rifle look (2026-10-08)

Third weapon of the weapons pass. The Plasma Beam Emitter (`covenant_remnants_plasmabeam`, medium
energy beam, Graviton Beam mirror) gets the look and sound of the Reach focus rifle, remade from
the official Reach editing kit's tags, and its shield suppression now also triggers on Covenant
shields. Stats unchanged.

## Developer rulings (2026-10-08)

| Topic | Decision |
| --- | --- |
| Source | **Reach focus rifle** (`objects\weapons\rifle\focus_rifle\`): third-person beam system, firing / impact effects, light volume, sounds. |
| Route | Full: engine beam kept (vanilla ROUGH textures, standing ruling) + GL overlay. |
| Colour | **Reach as-is**: peach-orange centre, violet plasma sheath, blue muzzle plasma, gold impact plasma, orange sparks / flare. No hue rotation. |
| Scale | **80 su per Reach WU**: Reach's 0.25 WU sheath = the 20 su engine beam. Muzzle flash ≈ 30 su, impact flash ≈ 20 su. Proposal accepted "and we'll check" (in game). |
| Pattern | **Continuous** (Graviton-style loop; Reach's heat cycle not reproduced). |
| Numbers | Unchanged: 100 ENERGY dps, 75 flux/s, range 1000, 9 OP, turn 15, chargeup / down 0.1. |
| Sounds | Fire **in + loop + out** (Reach `focus_rifle_fire`, FMOD 10952–10954; loop = `fireSoundTwo`, in / out played by the beam effect); **all 5** `focus_beam_impact` takes (10946–10950). Tails / lod_far / powerup layers not used. |
| Suppression | **Kept and extended to Covenant shields.** Vanilla's listener (`shieldDamageTakenMult`, which the Covenant shield honours) is reused; only the trigger is ours: vanilla shield arc OR Covenant cloak at effect level > 0. |

## Source (`assets/reference/plasma-beam/reach-data/`, gitignored)

`spec.md` — every part decoded engine-neutrally. `dump32/`, `summary/` — ManagedBlam dumps (33
curve samples, 29 tags). `textures/` — 20 bitmap exports + contact sheet. `audio/` — 28 decoded
sounds + envelope plots. `tools/` — `build_plasma_beam_textures.py`, `make_plasma_beam_sounds.py`,
`PlasmaBeamHarness.java` + `render_plasma_beam.py` (offline preview), plus the Reach dump / export
scripts. `preview/` — the offline renders (`plasma_beam.png` sheet, `_muzzle` / `_impact` close-ups).

## The look (from the tags; sizes × 80 in game)

- **Beam** (Reach `projectile_3p.beam_system`, 4 additive layers over the engine beam, which
  now carries the sheath's violet fringe (128,81,187) and the peach core (255,195,118)):
  two **violet plasma sheath** layers (plasma_trail_a × plasma_trail_b through the
  `focus_rifle_plasma` palette, shaped across by the energy_trail profile, one / half a tile
  along the beam, trails sliding toward the target), the thin **peach centre** (energy_trail
  band, intensity ×2.5 over the last 12 % — Reach ×4 over the last ~10 %), and the **pulses**:
  one per Reach round (30/s, 0.2 s life), a tube swelling from the centre to the sheath width,
  peach → blue-violet, black point rising, scrolling stripes (new sprite shader mode 11
  `TEXTURE_STRIP`).
- **Muzzle**: peach `flash_large` starburst per round (0.3–0.4 WU, 0.085 s), gold embers
  (200/s, 0.1–0.3 s, 160 su/s, 30° cone — Reach's 0.005 WU size floored at 2 su), **blue plasma
  tongues** (100/s, 0.1–0.2 s, 0.15–0.25 WU, 80° cone, `soft_plasma_blue`, intensity 2), the
  blue-lavender **light-volume cone** (6 glow sprites over the first 0.35 WU, thickness 0.15 →
  0.05, intensity 2 → 1 × 1.5), the blue-violet flickering light (1 WU glow, noise 2 Hz × 0.3).
- **Impact** (Reach re-fires its 0.1 s impact event while the beam holds; same here): peach
  flash (0.2–0.25 WU, intensity 5 → 1), 5 **gold plasma licks** (`soft_plasma_gold`, 0.1–0.15
  WU, 0.15–0.2 s, drifting out), 6 orange-red **sparks** back toward the shooter (45° cone,
  stretched ×10 then shrinking), the **orange lens flare** (0.3 WU, brightness 2, 5 Hz noise
  flicker) and the peach light (0.5 WU glow, 10 → 0 per cycle × 0.3). Impact sprites are pinned
  to the live hit point (EP fix 1); the flare / light release when the beam leaves the target.
- **Left out**: the distortion ring at the muzzle, the grey impact smoke, the overheat gas, the
  first-person beam, Reach's heat cycle.

## Files

- `data/weapons/covenant_remnants_plasmabeam.wpn` — `beamEffect` → `PlasmaBeamEmitterEffect`,
  fringe / core colours, `fireSoundTwo` → `covenant_remnants_plasma_beam_fire_loop` (width 20,
  ROUGH, scroll unchanged).
- `data/config/settings.json` — `plasma_beam_*` texture ids; `data/config/sounds.json` —
  `covenant_remnants_plasma_beam_fire_in` / `_fire_loop` / `_fire_out` / `_impact` (5 takes).
- `graphics/covenant_remnants/fx/plasma_beam/` (new, 8 textures: palette, gold palette, 2 trails, profile, pulse,
  fire shape, spark) and `sounds/plasma_beam/` (new, 8 OGG).
- `data/shaders/rupture/ruptureSprite.frag` — mode 11 `TEXTURE_STRIP` (base map tiled /
  scrolled by `xformA`, shaped by the alpha map). moderngl compile / link OK.
- `src/.../weapons/PlasmaBeamEmitterEffect.kt` (new) — hands the beam to the render plugin;
  Graviton suppression with the Covenant-shield trigger, vanilla listener reused.
- `src/.../plugins/PlasmaBeamRenderPlugin.kt` (new) — one per combat (`engine.customData`), one
  `PlasmaBeamLook` per weapon, fire in / out at beam start / end, impact sound on first contact.
- `src/.../utils/render/plasmabeam/` (new) — `PlasmaBeamCurves.kt` (the Reach curves),
  `PlasmaBeamLook.kt` (beam / muzzle / impact emitters, sprite update), `PlasmaBeamRenderer.kt`.
- `src/.../utils/render/rupture/RuptureTexture.kt`, `RuptureParts.kt` — `PLASMA_BEAM_*` textures,
  materials (`PLASMA_BEAM_SHEATH`, `_SHEATH_2`, `_CORE`, `_PULSE`, `_MUZZLE_PLASMA`,
  `_IMPACT_PLASMA`, `_SPARK_LONG`) and sprite kinds; `NoiseTransform` gained `scaleY`;
  `MODE_TEXTURE_STRIP`.
- `src/.../utils/render/rupture/SpriteGlState.kt` (new) — the GL state wrap the torpedo, Energy
  Projector and Plasma Beam renderers all used to carry a copy of (the EP doc's open item:
  "extract when the third copy appears"); `TorpedoRenderer` / `ProjectorRenderer` now use it.

## Offline check (done)

Jar builds (`gradlew buildMod`). Shader pair compiles / links on the real driver (moderngl).
`PlasmaBeamHarness` (stand-in `WeaponAPI` / `BeamAPI` proxies: 0.1 s chargeup, 3 s beam on a
target drifting 40 su, 0.1 s fade) + `render_plasma_beam.py` (real `ruptureSprite` shader, the
engine beam stood in by vanilla `beam_rough2` in the new colours): `preview/plasma_beam.png` and
the close-ups. The first render whited the beam out (6 overlapping pulses at Reach intensity 2) —
`PULSE_GAIN` 0.35 added.

## In-game checklist

1. Beam on: violet sheath with streaks flowing toward the target, peach centre brighter at the
   far end, pulses flickering peach → blue along the beam; not blown out to white. 20 wide.
2. Muzzle: blue tongues in a wide cone, gold ember spray, peach flashes, blue-lavender cone
   glow hugging the first ~30 su.
3. Hit on shield / armor / hull: peach flash, gold licks, orange sparks back toward the shooter,
   flickering orange flare; everything stays on the live hit point when the target moves or the
   beam sweeps.
4. Sounds: `fire_in` at beam start, the loop while firing, `fire_out` at release (not clashing
   with the loop's own fade), one impact take when the beam first lands / jumps targets.
5. Suppression: vs a vanilla shielded ship, the shield-damage-taken bonus shows in the target's
   stats (or visibly higher shield damage); vs a Covenant ship with the shield up, the same.
6. 4–6 emitters firing at once (a Covenant capital broadside): frame rate.
7. Turret and hardpoint mounts identical.

## In-game feedback (2026-10-08)

- Developer: "looks good". Sound drowned everything out (support weapon) — volumes lowered in
  `sounds.json`: loop 1 → 0.55, in / out 1 → 0.6, impacts 0.8 → 0.5. Re-check the mix.
- **Energy flowing toward the ship**: the pulse strip (shader mode 11) samples `vUv.y`, which
  runs 0 at the far end to 1 at the muzzle, so its negative v travel ran the stripes inward.
  Flipped to +1 (`PLASMA_BEAM_PULSE` xform). The sheath layers sample sprite-local x (muzzle →
  target) with a negative travel and flow outward. Re-check; if the sheath streaks still read as
  inward, their `NoiseTransform` travel signs flip the same way.

## Open items

- Curve audit (2026-10-10, [reach-curve-audit.md](reach-curve-audit.md)): the impact plasma width (raw 0→1, tag 1→2), the `spark_long`
  placeholders (tag 15 / 0.15–0.25 s / 1–2 WU/s / 45–70°), the muzzle light (tag periodic 2.5–6) and the
  muzzle plasma aspect (tag 1–1.5) differ from the tags — kept as they are by developer ruling.
- Scale check in game (80 su/WU): sheath vs engine beam, muzzle / impact sizes, the sheath's
  streak speed (`PLASMA_BEAM_SHEATH` xforms — Reach scrolls 8 beam lengths / s, ours 1 and 0.5)
  and the pulse scroll (mode 11 xform).
- Placeholders: `PULSE_GAIN`, `LIGHT_VOLUME_GAIN`, `MUZZLE_LIGHT_GAIN`, `IMPACT_LIGHT_GAIN`,
  `CORE_TIP_*`, `MIN_SPRITE_SIZE` (Reach's embers / sparks are sub-su), `SPARK_LONG_*` (its
  emitter functions did not decode), `IMPACT_PLASMA_ASPECT` sampled from Reach's spline.
- The impact's grey smoke and the muzzle distortion ring are left out.
- Mount art (vanilla Graviton sprites) — deferred by ruling.
- Numbers placeholder until the balance pass.

# Implementation — plasma torpedoes, Halo: Reach space Phantom look (2026-10-02)

The three plasma torpedoes (`covenant_remnants_plasmatorpedo_light` / `_plasmatorpedo` /
`_plasmatorpedo_heavy`) get the look of the plasma torpedoes the space Phantoms fire at Anchor 9's
AA guns in m45 (Long Night of Solace), remade from the official Reach editing kit's tags.

## Developer rulings (2026-10-02)

| Topic | Decision |
| --- | --- |
| Scope | **All three torpedoes**, one look, **scaled by weapon size**. |
| Behaviour | **All plain homing torpedoes**, like Reach: one plasma ball each, no splitting. Own `.proj` specs replace vanilla Sabot / Harpoon / Hurricane MIRV. Speeds and turn rates stay as they were (tuning later). |
| Sounds | **Reach sounds**: fire, in-flight hum, explosion. |

## Source (Reach kit, `assets/reference/plasma-torpedo/reach-data/`, gitignored)

- Weapon `objects\vehicles\covenant\space_phantom\turrets\chin_gun\weapons\space_phantom_turret.weapon`
  (m45 script: `cs_waf_phg_attack_*` → "Phantom torpedoes away!"). Projectile: guided, 15 → 35 Reach
  units/s, turns 30–60°/s, max range 400.
- `spec.md` — every part decoded engine-neutrally (curves as 9 samples, render options decoded
  against the render-method definitions, textures, sounds). `dump/`, `summary/` — ManagedBlam dumps.
  `textures/` — new bitmap exports. `audio/` — decoded FMOD samples.
- `tools/TorpedoHarness.java` + `tools/render_torpedo.py` — run the real `TorpedoFlight` /
  `TorpedoBurst` headless with a stand-in missile (Java proxy) and render the draw lists with the
  real sprite shader and textures (moderngl). `preview/` — the renders.

## The look (all from the tags unless marked)

- **Firing** (`firing.effect`): `muzzle_flash_round` — palettized sprite (2-frame sheet through a
  violet → white gradient), 1–3 units, 0.025–0.15 s, white → violet, black point rising; plus
  `flash_large` — 3 units shrinking to half over 0.15 s, light violet.
- **Body** (`projectile.light_volume_system` hot_core): 24 `light_volume_glow` glows along 8 units of
  the travel axis, from 6.7 behind the projectile to 1.3 ahead; colour salmon at the tail → blue-violet
  at the head, alpha and intensity (to ~3×) peaking near the head. Thickness 0.4 read as the glow's
  radius.
- **Flare** (`projectile.lens_flare`): `star_flare`, radius 3, blue-violet.
- **Trail** (`projectile.contrail_system`): an aligned ribbon of `hunter_beam` (a lightning band),
  15 points/s, 1 s life, width 1.5 → 3, orange flash at the head (intensity 3.5) cooling to
  blue-violet, alpha fading, black point rising; uv tiling 0.085 per unit, scrolling 0.5/s.
  *Not in the tags:* a point also every 2 units travelled, so the ribbon stays smooth through turns
  at Starsector speeds.
- **Impact** (`impact.effect`):
  - 15 `fireball_space_covenant` — palettized fireball plate (sphere warp 0.8, zooming in 0.2 → 1.2)
    through the Covenant fireball gradient, masked by `fireball_mask`, intensity 25 → 10, white →
    violet, alpha-blended.
  - 15 `plasma_blue_space_large` — palettized 2D plasma (two scrolling cloud maps through the blue
    plasma gradient, sphere warp 0.45), violet → white → violet, flickering 0–7.5×.
  - 15 `fireball_smoke` — dark grey to near-black smoke, growing 1 → 2.5×, flung fastest.
  - Sparks — `spark_orange_space` cheap particles: orange × 20, streaks along their velocity, up to
    45 units/s, 0.15–1.2 s. *Placeholder count:* 40 (Reach: 200–3600/s over a 0.1 s event).
  - Lens flares (0.8 s): `simple_flare` and a horizontal `anamorphic_flare`. **Their curves are read
    back to front** (time left): the flare starts small, white-hot and bright (7×), swells to 25
    units while it cools to blue, and fades. Read forwards they would grow dim and then spike at
    the very end; the reversed reading is the normal explosion flash.
  - Physics: each system's physics template has an unnamed second parameter (3 / 2.5 / 2); it is
    read as drag (velocity × exp(−drag·t)).
- **Sounds**: fire (3 permutations), the in-flight hum (Reach: −18 dB vs. −6 dB for fire and
  explosion, +5 semitones, 0.2 s fades), explosion (3 permutations, 0 to −2 semitones random).

Left out (like the fleet jump's look): the full-screen exposure flash (`main_explosion
.area_screen_effect`), the 2-unit muzzle point light, the flyby sound (no camera passes a torpedo in
top-down view), the fireball's second rolling-smoke layer (`base_map2` / frame blend), and the
impact damage effect (Starsector deals the damage).

## Units

Starsector units per Reach unit by weapon size (placeholders, `TorpedoLook`): **small 3, medium 5,
large 8**. At medium: the body ~40 su long, the star flare 15 su in radius, the trail 7.5 → 15 su
wide, the fireball and plasma core ~45 su in radius, the smoke out to ~115 su, the impact flare up
to 125 su.

## Files

- `data/weapons/proj/covenant_remnants_plasmatorpedo{_light,,_heavy}.proj` (new) — `MISSILE`,
  invisible 8×16 sprite (`graphics/missiles/covenant_remnants_plasma_torpedo.png`), no engine
  slots, no vanilla explosion (`explosionRadius 0`); `engineSpec` copied from the vanilla missile
  each used (Sabot 600/150/100/100, Harpoon 210/70/1000/500, MIRV 600/150/250/200).
- `data/weapons/covenant_remnants_plasmatorpedo*.wpn` — own `projectileSpecId`, `onFireEffect`
  `PlasmaTorpedoOnFire`, `animationType NONE` (Reach muzzle flash instead of vanilla smoke),
  `fireSoundTwo covenant_remnants_plasma_torpedo_fire`.
- `src/.../weapons/PlasmaTorpedoOnFire.kt` (new) — hands each torpedo to the plugin.
- `src/.../plugins/TorpedoRenderPlugin.kt` (new) — one per combat (`engine.customData`), layer
  `ABOVE_SHIPS_AND_MISSILES_LAYER`. Launch → muzzle burst + `TorpedoFlight`. Each frame: hum
  (`playLoop`) while flying; when the missile leaves play: impact + explosion sound if it hit
  (`didDamage`) or was shot down (hitpoints ≤ 0); a torpedo that fizzles or fades out just fades
  (body over 0.5 s; Reach would detonate at max range).
- `src/.../utils/render/torpedo/` (new) — `TorpedoCurves.kt` (the Reach curves), `TorpedoEffects.kt`
  (`TorpedoLook` units + noise, `TorpedoFlight` body/flare/trail, `TorpedoBurst` muzzle/impact
  particles), `TorpedoRenderer.kt` (GL state wrap, draw order).
- Shared with the rupture: `RuptureSpriteShader` (`drawSprites` overload for any sprite list,
  `drawRibbon` for the trail), `ruptureSprite.frag` (modes 1 and 3 gain a black point; new modes
  5 `PALETTE_ADD` and 6 `PALETTE_ALPHA`), `RuptureParts.kt` (torpedo `SpriteMaterial`s /
  `SpriteKind`s), `RuptureTexture.kt` (`TORPEDO_*`), `RuptureEffect.kt` (`else` branch for the new
  kinds).
- `graphics/fx/plasma_torpedo/` (new, 14 Reach textures; the fireball mask's shape moved into alpha,
  the 2-frame muzzle sheet repacked as a 2×2 atlas), registered in `data/config/settings.json` as
  `torpedo_*`. Reused from the rupture: `light_volume_glow`, `star_flare`, `plasma_clouds_256_a`.
- `sounds/plasma_torpedo/` (new: `fire_1-3`, `loop`, `impact_1-3`, mono OGG) and
  `data/config/sounds.json` (`covenant_remnants_plasma_torpedo_fire` / `_loop` / `_impact`).

## In-game checklist

1. Fire each torpedo (light on a small mount, medium, heavy): violet-white muzzle flash, Reach fire
   sound, no vanilla smoke.
2. In flight: bright blue-violet head with a star flare, a short glowing body, a lightning ribbon
   trail (orange at the head, blue behind), a hum that follows it. Turns stay smooth.
3. Hit a ship (shield up and down): white flash and horizontal streak, pink-white fireballs,
   blue-violet plasma, orange sparks, dark smoke; explosion sound. Sizes grow light → medium → heavy.
4. PD shoots one down: same impact. One that runs out of flight time: fades, no impact.
5. No vanilla missile explosion or engine glow left; the loaded missiles on the launcher are
   invisible (art pass pending).
6. Many torpedoes at once (heavy volleys): frame rate.

## Open items

- **Damage dropped with the splits** (values untouched, tuning later): the heavy was 7 MIRV
  warheads × 500 HE, now one 500 hit; the light was 5 Sabot darts × 200 kinetic + EMP, now one 100
  hit. Damage types also unchanged (light KINETIC, others HE).
- Units per size, spark count, trail spacing, body/flare brightness — placeholders.
- Launcher sprites are still vanilla (Bespoke weapon art TODO).
- `build.gradle.kts` also packs `data/` and `graphics/` into the jar as resources (twice), so new
  textures grow the jar (5.6 → 7.1 MB here). Pre-existing; not changed.

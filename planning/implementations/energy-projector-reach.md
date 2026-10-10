# Implementation — Energy Projector, Halo: Reach glassing-beam look (2026-10-08)

First weapon of the weapons pass (plan: one weapon at a time, Energy Projector first). The
Energy Projector (`covenant_remnants_energyprojector`) gets the look and sound of the CCS cruiser's
glassing beam, remade from the official Reach editing kit's tags. Behaviour (melt, pierce, exit
scorch, [energy-projector.md](energy-projector.md)) is unchanged.

## Developer rulings (2026-10-08)

| Topic | Decision |
| --- | --- |
| Weapons pass scope | Projectile/beam visuals, sounds, stats. **Mount art deferred** (vanilla sprites stay). Mixed route: full Reach-tag route for some weapons, cheap `.proj`/texture route for others, ruled per weapon. One look per weapon id. Order: EP → beams → bolts → torpedo polish. |
| Source | **Reach CCS cruiser glassing beam** (`objects\vehicles\covenant\covenant_fleet\cruiser\fx\glassing\`) + the m70 gameplay variant. |
| Beam route | **Engine beam kept** (collision, damage, pierce re-render) with its **vanilla ROUGH textures** (developer 2026-10-08: a Reach-strip `textureType` pair looked bad in game — "have the normal beam render and then do any effects over it"). Custom GL overlay for everything else. |
| Colour | **White core, purple fringe.** Reach's orange family is hue-rotated to violet (orange ≈ 25° → 250°); whites, greys and already-cool hues (the blue-violet far-end flare, the blue plasma) stay. |
| Scale | **0.5 su per Reach unit** for the overlay. Beam width **1.25× the vanilla Tachyon Lance** (25 → 31.25; ruling 2026-10-08, superseding the earlier 15). |
| Timing | **Reach timing kept, weapon lengthened**: chargeup 2 s, 3 s burst + 1 s fade (`burst size 3`, `chargedown 1`), 10 s cooldown (`burst delay 10`). DPS 750 (≈3000 per burst, 2× Tachyon). Flux **2000/s**. Melt ramp 1 s unchanged. |
| Sounds | Reach m52 corvette glassing (charge = its first 1.95 s whine, fire = the roar from 1.9 s, cut to 5.7 s with a 1.4 s fade) + Scarab beam impacts. |

## Source (`assets/reference/energy-projector/reach-data/`, gitignored)

`spec.md` — every part decoded engine-neutrally. `dump32/`, `summary/` — ManagedBlam dumps (33
curve samples). `textures/` — bitmap exports + contact sheet. `audio/` — decoded sounds and
envelope plots. `tools/` — `build_textures.py` (hue rotation, overlay textures),
`make_projector_sounds.py`, `ProjectorHarness.java` + `render_projector.py` (offline preview),
plus the Reach dump/export scripts. `preview/` — the offline renders (`projector.png` sheet).

## The look (from the tags; sizes ×0.5 in game)

- **Charge (2 s):** `emp_charge` rings (palettized plasma through the beam palette shaped by the
  `emp_mask` arc ring, 3/s, 1 s life, each growing ×7, size ramping 0.5→7 units over the charge),
  `explosive_flash` flashes (1.5/s, intensity 15 fading, lavender), the charge lens flare
  (`light_volume_glow`, radius 150, deep blue-violet, brightness rising over the charge, slow
  rotation) and the charge light (approximated as a soft glow, radius 80).
- **Beam:** the engine beam, vanilla ROUGH textures, `.wpn` width 31.25, fringe (139,130,251), core
  white, scroll 292 / `pixelsPerTexel` 5 (unchanged). While the beam lives: `glassing_flicker_glow` (the `glow_soft_half` half-dome of
  rays, apex at the muzzle fanning along the beam, size 225, alpha 0.15, intensity wandering
  1.4–1.6 at 4 Hz, lavender warming), and at the hit point the blue-violet `glassing_glow` flare
  (radius 300, brightness capped at 5 — placeholder, Reach peaks at 15).
- **Impact (on first contact, and again if the hit point jumps > 120 su):** `glassing_bubble`
  (one, size 45 growing ×3.85 over 1.25 s, palettized through the re-hued fireball palette,
  intensity 7, white → lavender → violet), `glow_soft_half` (one, size 300, 1.5 s, apex toward the
  beam), then while the beam holds: `glassing_plasma_burst` tongues on a tube around the hit
  (radius 25→50 su, 2/s, 4–6 s life, tip toward the beam, blue gravlift palette), and from 1 s in
  the vaporization ring: `glassing_plasma_huge` clouds (disc radius growing to 200 su, rate
  halved — placeholder, 8 s life, drifting outward with drag) and `plasma_blue_huge` wisps.
  Everything spawned outlives the beam on its own clock.
- **Phases** come from the live beam's brightness (handed over by the beam effect): rising = charge,
  1 = beam, falling = fade; no beam = idle (persistent sprites fade over 0.4 s).
- **Left out:** the full-screen exposure/colour screen effects, the real point light, the m52
  cinematic's animated beam model.

## Files

- `data/weapons/covenant_remnants_energyprojector.wpn` — width 31.25, `fireSoundOne` charge /
  `fireSoundTwo` fire (textures stay ROUGH).
- `data/weapons/weapon_data.csv` — Energy Projector row: dps 750, flux 2000/s, chargeup 2,
  chargedown 1, burst size 3, burst delay 10.
- `data/config/settings.json` — `projector_*` texture ids; `data/config/sounds.json` —
  `covenant_remnants_energy_projector_charge` / `_fire` / `_impact`.
- `graphics/covenant_remnants/fx/energy_projector/` (new, 8 overlay textures) and `sounds/energy_projector/` (new, 6 OGG).
- `src/.../plugins/EnergyProjectorRenderPlugin.kt` (new) — one per combat (`engine.customData`),
  one `ProjectorEffect` per weapon, impact sound on first contact, layer
  `ABOVE_SHIPS_AND_MISSILES_LAYER`.
- `src/.../utils/render/projector/` (new) — `ProjectorCurves.kt` (the Reach curves, colours
  re-hued), `ProjectorEffects.kt` (`ProjectorEffect`: phases, emitters, one-shots, sprite update),
  `ProjectorRenderer.kt` (GL state wrap, draw order).
- `src/.../utils/render/rupture/RuptureTexture.kt`, `RuptureParts.kt` — `PROJECTOR_*` textures,
  materials (`PROJECTOR_EMP`, `_FLASH`, `_FLARE`, `_GLOW_HALF`, `_BUBBLE`, `_BURST`, `_CLOUD`,
  `_BLUE`) and sprite kinds. No shader changes (modes 0, 1 and 5 reused).
- `src/.../weapons/EnergyProjectorEffect.kt` — registers the weapon with the render plugin each
  frame (turn lock unchanged). `EnergyProjectorBeamEffect.kt` — one added line handing the live
  beam to the plugin. Pierce plugin untouched.

## Offline check (done)

Jar builds (`gradlew buildMod`). `ProjectorHarness` (stand-in `WeaponAPI`/`BeamAPI` proxies: 2 s
charge, 3 s beam on a target, 1 s fade) + `render_projector.py` (real `ruptureSprite` shader, the
engine beam stood in by vanilla `beam_rough2`): `preview/projector.png`. Charge glow swells,
bubble + half-dome flash white-lavender at the hit, tongues and cloud ring linger after the beam.

## In-game checklist

1. Charge: rings and flashes at the muzzle, blue-violet flare swelling over 2 s, charge whine.
2. Burst: the (vanilla-textured, purple/white) beam with the ray half-dome flickering at the
   muzzle; fire roar.
3. Hit on shield / armor / bare hull: bubble + half-dome flash, impact sound, blue tongues around
   the hit, cloud ring spreading from 1 s in; melt ramp, pierce and exit scorch still work.
4. Fade: overlay dims with the beam; spawned impact particles age out on their own.
5. 4–5 projectors firing at once: frame rate.
6. Turret and hardpoint mounts identical.

## In-game fixes (2026-10-08)

- **Impact effect in the wrong place** (developer screenshot): the bubble and half-dome were
  one-shots left at the first contact point in world space, so a moving target or a beam sweeping
  under 120 su left them behind the live beam end. Now pinned to the live hit point each frame
  while the beam keeps hitting (`ProjectorEffect.updateImpact`). Reach never needed this: its
  glassing target is static ground.
- **Impact in the middle of the ship** (second screenshot): the overlay read the beam from the
  weapon's everyFrame hook, where `getTo()` is still the full-range ray end (the engine shortens it
  to the collision point only after the beam effect runs — the same ordering the pierce work hit).
  Now `EnergyProjectorBeamEffect.advance` hands the beam to the plugin (`beam()` →
  `ProjectorEffect.snapshot`), and the overlay uses those endpoints; no snapshot in a frame = beam
  gone.
- **Beam texture**: the Reach strip pair looked blobby; back to vanilla ROUGH. Width ruled at
  1.25× the Tachyon Lance (31.25).

## Open items

- Curve audit (2026-10-10, [reach-curve-audit.md](reach-curve-audit.md)): the flicker glow (tag aspect 1.7), impact half glow (1.7) and
  bubble (1→1.25) are drawn square — kept as they are by developer ruling.
- **Beam strips dropped (2026-10-08):** a Reach `plasma_trail` / `wispy_trail` fringe+core pair for
  the vanilla renderer looked blobby in game; reverted to ROUGH, the strips are no longer built.
  The engine draws a faint beam during the 2 s chargeup
  (vanilla burst-beam behaviour; Reach has no beam while charging) — `beamFireOnlyOnFullCharge`
  would also stop chargeup damage: balance question for the developer.
- Placeholders: far-flare brightness cap, charge flare / light gains, the cloud ring's halved
  rates, Reach particle sizes read as diameters, `emp_charge` noise transforms (its Reach
  uv scroll was not decoded).
- Mount art (vanilla Tachyon sprites) — deferred by ruling.
- ~~`ProjectorRenderer` duplicates the torpedo renderer's GL state wrap~~ — extracted 2026-10-08
  as `utils/render/rupture/SpriteGlState` with the Plasma Beam Emitter (the third copy).

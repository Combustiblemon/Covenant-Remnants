# Implementation — Plasma Cannon, Reach ball-turret round (2026-10-09)

Sixth weapon of the weapons pass. `covenant_remnants_plasmacannon` (medium energy bolt, 5 mounts on
3 variants) turns from a vanilla Heavy Blaster mirror (blue `heavyblaster_shot`, vanilla sound) into
the **Halo: Reach corvette ball-turret round**: a fat blue plasma ball with a peach core, the Reach
plasma splash on hit, and the Long Night of Solace corvette big-gun shot.

## Developer rulings (2026-10-09)

| Topic | Decision |
| --- | --- |
| Look | Reach ball-turret plasma ball: fat glowing sphere, bright core, blue corona, big plasma splash on hit. |
| Route | Cheap bolt + Kotlin on-hit splash; then (same day, first in-game look: the `PLASMA` ball at width 40 read as a spiky urchin) a Kotlin **energy aura** over a small engine core — aura only, no trail. |
| Source | Reach kit searched for `ball_turret` (dumped, see below). |
| Splash | Reuse the plasma torpedoes' Reach impact burst (same `covenant_explosion` family) at a cannon-sized unit, no extra sound, **no dark smoke** (developer, first in-game look). |
| Palette | Reach colours: fringe (105,120,253) blue, core (255,200,160) peach-white; muzzle flash pale blue; mount glow blue. |
| Sound | `device_machines\omaha\corvette\bfg_fire 1-3` (FMOD bank 11684–11686) to start; wraith mortar (9951), Phantom turret (7750), concussion rifle (7830–7835) pulled raw as alternatives. |
| Stats | Slightly above Heavy Blaster: 500 ENERGY / shot, 1 s, range 700 (+100), proj speed 900 (−100), 700 flux / shot (−20), 13 OP (+1), turn 15. |
| Mount art | deferred: vanilla `blaster_*` sprites. |

Vanilla anchor (0.98a-RC8): Heavy Blaster 500 ENERGY, 1 s, 600 range, 720 flux, 1000 speed, 12 OP.

## Reach source (HREK, dumped 2026-10-09)

`objects\vehicles\covenant\covenant_fleet\corvette\turrets\corvette_ball_turret\weapon\
corvette_ball_turret.weapon` → `covenant_ball_turret.projectile` (also a copy under
`objects\levels\ca_dev\dlc_condemned\corvette_battle\fx\`), its `fx\ball_turret\projectile.*`
attachments and the impact `fx\fx_library\explosions\covenant_explosion_artillery_huge`.

- Weapon: 1 round / s, 0.7 s recovery, error 0.1°, no firing effect (the kit has none).
- Projectile: 200 WU/s constant, 2000 WU range, detonates on anything, damage effect
  `bfg_round` (75–300, AOE core 0.75, radius 0.75–1.75 WU, shake 17).
- Body: `projectile.light_volume_system` — one light volume 8 WU long, offset −7 (trails the
  round), brightness ×3.33, thickness 1→1.5 along the profile, colour blue (0.40,0.47,0.99) for
  84 % then to peach (1.0,0.65,0.46) at the tip, intensity 1→5 toward the tip.
  `projectile.lens_flare` — `star_flare`, radius 0.15, colour (0.16,0.33,1.0), tint power 6.
- Trails: `projectile.contrail_system` — `hunter_beam` ribbon (0.5 s, size 6→1, colour
  (0.68,0.73,1.0) through the `energy_trail_blue` palette, uv scroll 2) and `soft_contrail`
  (0.75 s, size 2→1, `plasma` noise, three-colour peach → indigo (0.08,0.06,0.57)). Looping sound
  `cap_ship_proj`. **Not built** (cheap route).
- Impact: `covenant_explosion_artillery_huge.effect` — 0.2 s `explosive_flash` (size 4, scale
  0.5→1.5), `fireball_covenant_artillery_huge` (0.05 s delay, 0.75–1.25 s, size 0.9–1.5, scale
  0.45→1.8, tube radius 0.25→0.6), `covenant_explosion_rolling_smoke_large` (2–4 s) +
  `rolling_smoke_large_impact` (5–8 s), `plasma_gas`, `plasma_blue_huge` (0.15 s delay, 1–3 s,
  size 0.85–1.25, alpha 0.5), `sparks_long_huge` + `sparks_huge`, metal chips / fragments,
  `huge_glow` lens flare, sounds `cov_damage_large` + `phantom_destroyed_lod`, `huge_explosion`
  screen effect. The torpedo impact burst (`TorpedoBurst.impact`) is the same family built from
  the space Phantom torpedo, hence the reuse.
- Bitmaps exported: `fx\bitmaps\contrails\plasma` (512×128 grey noise), `_gradients\
  energy_trail_blue` (256×1, black → blue → white); `hunter_beam`, `light_volume_glow`,
  `star_flare` already in `graphics/covenant_remnants/fx/`. None shipped this pass.

Data under `assets/reference/plasma-cannon/reach-data/` (gitignored): `dump32/`, `summary/`,
`textures/`, `audio/raw_*.wav`, `tools/`.

## Files

- `data/weapons/weapon_data.csv` — Plasma Cannon row: range 700, OP 13, energy/shot 700, proj
  speed 900 (damage 500, chargedown 1, turn 15 unchanged).
- `data/weapons/covenant_remnants_plasmacannon.wpn` — `projectileSpecId`
  `covenant_remnants_plasmacannon_shot`, `fireSoundTwo` `covenant_remnants_plasma_cannon_fire`,
  blue `glowColor`, `muzzleFlashSpec` (10 pale-blue particles, 40 long, 0.2 s).
- `data/weapons/proj/covenant_remnants_plasmacannon_shot.proj` (new) — `PLASMA` spawn, width 16
  (the hot core only), fade 0.5, Reach colours, hit glow 90, `onHitEffect` `PlasmaCannonOnHit`.
- `src/.../weapons/PlasmaCannonOnFire.kt` (new, `onFireEffect` in the `.wpn`) — hands each round
  to `TorpedoRenderPlugin.trackCannonRound`.
- `src/.../utils/render/torpedo/TorpedoEffects.kt` — new `CannonFlight`: the aura at unit 4 —
  two glow spheres (`TORPEDO_BODY` glow: blue 3.5-unit body breathing ±15 %, peach 1.8-unit heart),
  the Reach `star_flare` (2.5 units, (0.16,0.33,1), slow spin), six `TORPEDO_PLASMA` wisps orbiting
  at 2.2 ± 0.8 units (1.5–2.6 rad/s either way, size 2.6, palette flicker like the impact plasma);
  fades over 0.25 s after the round hits / expires. `TorpedoBurst.impact(…, smoke = false)`.
- `src/.../utils/render/torpedo/TorpedoRenderer.kt` — draws cannon wisps with the impact plasma,
  cannon glows / star with the torpedo bodies.
- `src/.../weapons/PlasmaCannonOnHit.kt` (new) — `OnHitEffectPlugin`; calls
  `TorpedoRenderPlugin.splash(point, 4f)`.
- `src/.../plugins/TorpedoRenderPlugin.kt` — new `splash(location, unit)` (impact burst only, no
  sound, no smoke) and `trackCannonRound(projectile, unit)` (aura until hit / expiry / out of play).
- `sounds/plasma_cannon/fire_1-3.ogg` (new; bfg_fire cut to 1.6 s, 0.6 s fade-out),
  `data/config/sounds.json` — `covenant_remnants_plasma_cannon_fire` (volume 0.6).
- `jars/CovenantRemnants.jar` rebuilt (`gradlew buildMod`).

## In-game checklist

1. Refit card: 500 ENERGY / shot, 1 s, range 700, 700 flux, 13 OP.
2. Bolt: small hot core wrapped in a breathing blue glow with a peach heart, star flare, six
   flickering plasma wisps orbiting it; no spiky urchin; aura fades where the round died.
3. Hit on shield / armor / hull: Reach plasma splash (fireball, blue plasma, sparks, flare, no
   smoke) at the hit point, sized like a medium torpedo hit but a touch smaller; vanilla hit sound only.
4. Muzzle: pale-blue flash + turret glow each shot.
5. Sound: corvette big-gun shot once per second; tail not stacking into mud with 2 cannons.
6. Frame rate with several cannons firing (each hit adds one impact burst).
7. Turret and hardpoint identical.

## Impact switch (2026-10-10)

The hit splash is now the Bombardment's rebuilt `covenant_explosion_artillery_huge` (`ArtilleryBurst`)
at unit 8 with 30 sparks and a 1 s blast light, plus a quiet `cov_damage_large` boom (volume 0.15,
pitch ~1.2). It replaces the torpedo burst described above. See
[plasma-cannon-artillery-impact.md](plasma-cannon-artillery-impact.md).

## Open items

- Numbers placeholder until the balance pass.
- ~~Splash unit (4) and whether the splash wants its own impact sound~~ — resolved 2026-10-10 by the
  impact switch above.
- No in-flight ribbon trail (developer: aura only). Aura constants in `CannonFlight` are placeholders
  (glow radii / intensities, wisp count / radius / speed, core width 16).
- Alternative fire sounds pulled raw: wraith mortar, Phantom turret, concussion rifle.
- Mount art.

# Implementation — Pulse Laser Turret, plasma machine gun (2026-10-09)

Fifth weapon of the weapons pass. `covenant_remnants_pulselaserturret` (small energy PD, 37 mounts
across 14 variants) turns from a recoloured Burst PD Laser mirror (beam) into a **projectile
machine gun**: a continuous stream of Seraph-style cyan plasma streaks on Vulcan Cannon timings.
No Halo effect source (developer ruling: hand-made bolt); the fire sound is the Halo: Reach mounted
plasma cannon shot.

## Developer rulings (2026-10-09)

| Topic | Decision |
| --- | --- |
| Kind | Machine gun, not a beam. |
| Look | Seraph cannon streaks: long thin tracers, cyan-white core, sky-blue fringe, blue glow, slight spread. |
| Palette | core (235,255,255), fringe (90,190,255), glow (60,140,255), cyan muzzle flash — deliberately not the mod's purple, so PD fire reads apart from the Repeater / Cannon bolts. |
| Route | Cheap: own `.proj` + hand-made PNG + `.wpn` muzzle flash. No Kotlin. |
| Source | Hand-made from vanilla data; no kit tag search. One sound pull only. |
| Sound | Reach `weapons\plasma_cannon\plasma_cannon_fire\spectre_turret14_1-3` (FMOD bank 10207–10209), not the plasma repeater / Phantom turret / Banshee. |
| Stats | Vulcan-shaped, ENERGY: 20 shots/s × 15 = 300 dps, range 500, 7.5 flux/shot (150/s), spread 0–15 (+1/shot, −15/s), proj speed 900, 6 OP, `PD,ANTI_FTR`. |
| Mount art | deferred (weapons-pass ruling 2026-10-08): vanilla `burstpd_*` sprites stay, glow recoloured blue. |

Vanilla anchors (0.98a-RC8): Vulcan Cannon 20 shots/s × 25 FRAG, 250 range, 1 flux/shot, spread
0/15/1/15, speed 800. PD Laser 75 dps / 40 flux/s. Burst PD Laser 350 dps bursts / 200 flux/s.
The new gun sits "slightly above vanilla" (300 dps at 500 range) and pays in flux (150/s).

## Look

- Bolt: `BALLISTIC_AS_BEAM` tracer, length 55 / width 5 (Vulcan 30/3.5), 0.25 s fade, blue glow
  radius 14, hit glow 25, texture scroll −128 at 5 px/texel, plus a 12×48 bullet sprite drawn by
  `assets/reference/pulse-laser/tools/make_bolt.py` (cyan-white core line, blue fringe, tapered
  tail, bright head cap).
- Muzzle: vanilla `MUZZLE_FLASH` particles, 6 per shot, cyan (150,230,255), 22 long, 6° spread.
- Sound: three 0.45 s cuts of the Spectre turret shot (mono, −1 dBFS), `fireSoundTwo`, volume 0.25 (0.5 was too loud in-game, developer 2026-10-09).

## Files

- `data/weapons/weapon_data.csv` — Pulse Laser Turret row: damage/shot 15 (dps column blank),
  chargeup 0, chargedown 0.05, burst size 1, energy/shot 7.5, spread 0 / 15 / 1 / 15, proj speed
  900; range 500, turn 100, OP 6, hints / tags unchanged; ammo + beam fields cleared.
- `data/weapons/covenant_remnants_pulselaserturret.wpn` — rewritten as `specClass: projectile`,
  `LINKED` single barrel, `MUZZLE_FLASH` + `muzzleFlashSpec`, `projectileSpecId`
  `covenant_remnants_pulselaser_shot`, `fireSoundTwo` `covenant_remnants_pulse_laser_fire`. All beam
  fields dropped.
- `data/weapons/proj/covenant_remnants_pulselaser_shot.proj` (new).
- `graphics/missiles/covenant_remnants_pulselaser_shot.png` (new, generated).
- `sounds/pulse_laser/fire_1-3.ogg` (new), `data/config/sounds.json` —
  `covenant_remnants_pulse_laser_fire` (3 permutations, volume 0.25).
- `assets/reference/pulse-laser/` (gitignored): `README.md`, `tools/make_bolt.py`,
  `preview/bolt_6x.png`, `reach-data/audio/raw_10207-10209.wav`, `reach-data/tools/make_sounds.py`.

No Kotlin, no shader, no `settings.json` change (`bulletSprite` loads by path). Variants untouched
(same weapon id, same slot size).

## In-game checklist

1. Refit card: projectile PD, 15 × 20/s (300 dps), range 500, 150 flux/s, 6 OP; no "beam" wording.
2. Held fire: continuous cyan-white stream; spread walks out toward 15° and recovers when released.
3. Bolts: long thin tracer with blue glow, cyan hit glow on shield / armor / hull; no vanilla yellow
   shell visible.
4. Muzzle: cyan flash each shot; turret glow sprite tinted blue.
5. Sound: short plasma pop per shot, not a wall of noise with 4+ turrets (0.5 was too loud; now 0.25).
6. PD: autofire shoots down missiles / fighters; AI puts it in a PD group.
7. Frame rate: supercap2 (6 mounts) + a fleet battle.
8. Turret and hardpoint mounts identical.

## Open items

- All numbers placeholder until the balance pass (300 dps / 150 flux/s may be hot for a 6 OP PD).
- Bolt size (55 × 5) and sprite brightness: first in-game look.
- Sound volume with many mounts (0.5 → 0.25 after first test); cut length (0.45 s) if shots blur together.
- Mount art (vanilla Burst PD sprite) — weapons-pass deferral.

# Implementation — Suppressor Beam, hot Plasma Beam Emitter (2026-10-08)

Fourth weapon of the weapons pass. The Suppressor Beam (`covenant_remnants_frigate1_turret`,
medium energy beam, the Rasho-pattern Escort Corvette's built-in and only gun) becomes a
**stronger sister of the Plasma Beam Emitter**: the same Halo: Reach focus rifle look and sounds
(see [plasma-beam-emitter.md](plasma-beam-emitter.md)), hotter and wider, with real damage.

## Developer rulings (2026-10-08)

| Topic | Decision |
| --- | --- |
| Source | **Reuse the focus rifle data** — no new kit searches; sister weapon of the Emitter. |
| Identity | "A better Plasma Beam Emitter that actually deals damage": the frigate is a support ship with no other armament. Suppression kept (the Emitter's Covenant-shield extension included). |
| Numbers | **250 ENERGY dps, 200 flux/s, range 1100, 14 OP**, turn 15 (was 120 / 90 / 1100 / 9). 2.5× the Emitter, half an HIL, efficiency 0.8. |
| Look | **Same effects, hotter**: beam width **24** (the overlay scales to the beam width: 96 su per Reach WU), brightness **×1.3** on the sheath, centre and pulses. |
| Sounds | **Own ids, pitch 0.9** (same OGG files as the Emitter, lowered volumes). |

## Files

- `data/weapons/weapon_data.csv` — Suppressor Beam row: dps 250, flux 200, OP 14.
- `data/weapons/covenant_remnants_frigate1_turret.wpn` — `beamEffect` → `PlasmaBeamEmitterEffect`,
  width 24, fringe violet / core peach, `fireSoundTwo` → `covenant_remnants_suppressor_beam_fire_loop`.
- `data/config/sounds.json` — `covenant_remnants_suppressor_beam_fire_in` / `_fire_loop` /
  `_fire_out` / `_impact`, pitch 0.9, volumes 0.6 / 0.55 / 0.6 / 0.5.
- `src/.../utils/render/plasmabeam/PlasmaBeamLook.kt` — `Style` (gain + sound ids) per weapon id
  (`EMITTER`, `SUPPRESSOR`); the Reach unit scale now comes from the live beam's width
  (`DEFAULT_UNIT` 80 = a 20 su beam). `PlasmaBeamRenderPlugin` plays the style's sounds.
- No new textures, sounds or shader work.

## In-game checklist

1. Frigate1's beam: wider (24) and visibly brighter than a Plasma Beam Emitter next to it;
   muzzle and impact effects a fifth larger.
2. Deeper fire loop / in / out / impact than the Emitter's (pitch 0.9).
3. 250 dps shows in the weapon card; suppression still applies (vanilla and Covenant shields).
4. Same look on the frigate's built-in turret and when mounted as a normal weapon.

## Open items

- Numbers placeholder until the balance pass (the ×2.5 step over the Emitter is the biggest jump
  above vanilla in the beam family so far).
- The frigate1 turret sprite (`covenant_remnants_frigate1_turret.png`) is the one bespoke-ish mount
  the mod has; mount art pass deferred by ruling.

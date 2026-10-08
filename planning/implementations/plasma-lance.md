# Implementation — Plasma Lance, burst beam (2026-10-08)

Second weapon of the weapons pass. `covenant_remnants_plasmalance` (large energy mount, HE beam)
turns from a continuous High-Intensity-Laser mirror into a **burst beam on Tachyon Lance timings**
with a white core and a bluish-white fringe. No Halo effect source (developer ruling); the fire
sound is the Halo 3 beam rifle shot.

## Developer rulings (2026-10-08)

| Topic | Decision |
| --- | --- |
| Pattern | Burst like the Tachyon Lance but half as long: chargeup 0.5, **burst 0.5 s**, chargedown 1, cooldown 4 (ruling: half burst, same damage per burst). |
| Numbers | 2000 HE dps over 0.5 s (≈1000 per burst), 2400 flux/s (1200 per burst); range 1000, OP 20, turn 5 unchanged. |
| Look | White core, blue fringe (90,160,255; the first (190,220,255) read as white in game); glow (120,180,255). Vanilla ROUGH texture, width 30, HIL mount sprites (mount art deferred). |
| Sound | Halo 3 beam rifle shot, found in H3EK on request. |
| Halo searches | New standing ruling: no browsing the kits unless asked for something specific (memory `no-unprompted-halo-file-searches`). |

## Files

- `data/weapons/weapon_data.csv` — Plasma Lance row: dps 2000, flux 2400/s, chargeup 0.5,
  chargedown 1, burst size 0.5, burst delay 4, beam speed 10000.
- `data/weapons/covenant_remnants_plasmalance.wpn` — colours, `fireSoundTwo`
  `covenant_remnants_plasma_lance_fire`; `beamFireOnlyOnFullCharge` kept (no damage during the
  chargeup, like the HIL it came from).
- `data/config/sounds.json` — `covenant_remnants_plasma_lance_fire` (2 permutations).
- `sounds/plasma_lance/fire_1-2.ogg` (third-person `cov_snip_shot9/10`, 2.9 s with tail) and
  `fire_fp_1-4.ogg` (first-person takes, ~1 s, unused — swap in if the tail is too long).
- `assets/reference/plasma-lance/h3-data/` (gitignored): `audio/` raw WAVs + envelope,
  `subsounds.tsv`; `tools/fsb5_names.py` (H3's `sfx.fsb.info` order ≠ subsound order, so the
  FSB5 name table is parsed instead).

## In-game checklist

1. Fires in 0.5 s bursts every ~5 s; no beam damage while charging.
2. White core, pale blue fringe; 30 wide.
3. Beam rifle shot plays once per burst; tail not too long over the beam.

## Open items

- Sound tail (2.9 s on a 0.5 s burst) — first-person takes are the shorter alternative.
- Numbers placeholder until the balance pass.

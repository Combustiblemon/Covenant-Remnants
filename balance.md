# Balance notes

Per-feature balance notes and TODOs. Everything is a vanilla-anchored placeholder until the balance
pass (developer 2026-10-02: tuning waits until all gameplay systems are finished).

## Weapons

- **Pulse Laser Turret** (2026-10-09) — PD machine gun: 20 × 15 ENERGY = 300 dps, 500 range, 150
  flux/s, 6 OP, spread 0–15. Vanilla yardsticks: Vulcan 500 FRAG dps (125 effective) at 250 range
  for 20 flux/s, PD Laser 75 dps / 40 flux/s, Burst PD 350 dps bursts / 200 flux/s. Slightly above
  on dps + range, pays in flux. TODO: check it is not the best small energy gun outright (300
  sustained ENERGY dps at 6 OP vs IR Pulse Laser 150 dps / 5 OP).
- **Plasma Cannon** (2026-10-09) — 500 ENERGY / 1 s (500 dps), 700 range, 700 flux/shot, 13 OP,
  speed 900. Yardstick Heavy Blaster: 500 / 1 s, 600 range, 720 flux, 12 OP, speed 1000. +100
  range for +1 OP, slightly cheaper flux. TODO: the on-hit splash is visual only — keep it that way
  or give it a small AOE (Reach `bfg_round` has 0.75–1.75 WU AOE)? Developer call.

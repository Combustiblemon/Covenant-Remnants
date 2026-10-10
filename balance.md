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
  or give it a small AOE (Reach `bfg_round` has 0.75–1.75 WU AOE)? Developer call. (2026-10-10: the
  splash is now the Bombardment's artillery impact at unit 8 with a quiet boom; still no area damage,
  stats unchanged.)
- **Plasma Repeater** (2026-10-09) — fighter gun: 25 ENERGY + 400 EMP, 0.4 s (62.5 dps + 1000
  EMP/s), 500 range, 30 flux, 6 OP. Yardstick Ion Cannon: same at 0.5 s. +25 % rate; only on the
  three wings, so ship balance untouched. TODO: revisit if the wings strip engines too easily.
- **Plasma Autocannon** (2026-10-09) — 150 ENERGY, 20/s bursts of 30 (regen 2/s), 125 flux, 800
  range, 21 OP. Yardstick Autopulse Laser: same at 700 range / 20 OP. +100 range for +1 OP.
- **Plasma Mortar** (2026-10-09) — 400 HE direct + 150 HE splash (core 20, radius 75, falloff to
  0), 1.5 s (267 dps direct), 700 range, speed 500, 400 flux/shot, 10 OP, `USE_LESS_VS_SHIELDS`.
  Yardsticks: Mining Blaster 200 HE + 100 armor bypass, 4-shot magazine (~2 shots/s burst, 1 per
  2 s sustained), 500 range, 10 OP; Heavy Mortar 314 HE dps at 700 range for 7 OP (ballistic).
  TODO: splash vs fighter swarms may make it a strong anti-fighter tool; check in testing.
- **Plasma Bombardment** (2026-10-09) — 1000 HE direct + 400 HE splash (core 64, radius 150, falloff
  to 0), 2.5 s cycle (1 s chargeup + 1.5 s), 400 dps direct, 900 range, speed 500, 1000 flux/shot,
  impact 200, 20 OP, `USE_LESS_VS_SHIELDS`. Yardsticks: Hellbore 750 HE / 3 s (250 dps), 900 range,
  speed 500, 750 flux, 16 OP; Hephaestus 480 HE dps, 900 range, speed 800, 20 OP. Slightly above
  Hellbore per OP on alpha and splash, below Hephaestus on sustained dps; the slow round and the 1 s
  charge telegraph keep agile frigates out of it. TODO: the 150 su splash vs fighter swarms and
  clustered frigates (cuts against "weak when swarmed"); check in testing.
- **Plasma torpedoes** (launcher polish 2026-10-09) — plain homing torpedoes (no splits) with a plasma
  armor melt: a hit that reaches armor / hull takes another 50 % of its damage out of the armor
  within 50 / 75 / 125 su over 2 s (armor only, direct grid write, so no armor reduction). Ammo
  regenerates 1 per 30 / 60 / 90 s. (Hits × 1.6 — 1920 / 1360 / 4800 — were tried 2026-10-10 and
  reverted the same day: well above the anchors.)
  - Light: 1200 HE, 2 ammo, 400 su/s, 1200 range, 75°/s, 5 OP. Yardstick Atropos rack 1000 HE / 2 /
    400 / 1200 / 3 OP (no regen; 125 su explosion).
  - Medium: 850 HE × 4-burst, 12 ammo, 300 su/s, 2500 range, 12 OP. Yardstick Harpoon Pod 750 × 4 /
    12 / 10 OP.
  - Heavy: 3000 HE single hit, 8 ammo, 250 su/s, 2500 range, 45°/s, torpedo HP 500, 27 OP.
    Yardsticks Hurricane 7 × 500 HE MIRV / 10 ammo / 25 OP; Cyclone Reaper 2 × 4000 HE unguided / 14
    ammo / 1200 range / 26 OP.
  - TODO: the melt makes every hit worth ~1.5× vs armor (more with damage-to-size skills); the
    guided 3000 HE heavy at 2500 range plus melt may outclass the Hurricane. The light also arms the
    Covenant bomber (3 launchers per fighter, wing of 2): 6 × 2 torpedoes per sortie plus regen in
    flight — check bomber strike strength. Light melt concentrates its 600 pool in a 50 su patch, so
    it strips centre cells on lightly armored hulls (part of the pool is lost there).

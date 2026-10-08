# Implementation — Covenant weapon arsenal

A complete arsenal so every ship can be outfitted with **zero vanilla weapons**, across every mount
the fleet uses (ENERGY + SYNERGY, sizes S/M/L). Developer decisions: **mostly energy + a few analogs**
(kinetic for anti-shield, HE for anti-armor / bombardment / torpedoes); **mirror vanilla stats +
reskin**; **placeholder art = the mirrored vanilla weapon's sprites**, recolored purple, so the
arsenal is usable in-game now.

## The 13 weapons

| Weapon | Size | Mount | Dmg | Mirror | Notes |
| --- | --- | --- | --- | --- | --- |
| Pulse Laser Turret | S | energy | ENERGY | pdburst | PD beam |
| Plasma Repeater | S | energy | ENERGY | ioncannon | rapid dps |
| Plasma Torpedo (Light) | S | missile | KINETIC | sabot | anti-shield |
| Suppressor Beam | M | energy | ENERGY | gravitonbeam | **frigate1 built-in** + normal weapon; shield-suppress (also vs Covenant shields); hot Plasma Beam Emitter sister 2026-10-08 (250 dps, width 24, ×1.3, pitch 0.9), see [suppressor-beam.md](suppressor-beam.md) |
| Plasma Beam Emitter | M | energy | ENERGY | gravitonbeam | shield-suppress beam (also vs Covenant shields); Reach focus rifle look + sounds 2026-10-08, see [plasma-beam-emitter.md](plasma-beam-emitter.md) |
| Plasma Cannon | M | energy | ENERGY | heavyblaster | anti-hull bolt |
| Plasma Mortar | M | energy | HIGH_EXPLOSIVE | miningblaster | anti-armor bolt |
| Plasma Torpedo | M | missile | HIGH_EXPLOSIVE | harpoonpod | tracking |
| Energy Projector | L | energy | ENERGY | custom | see [energy-projector.md](energy-projector.md) |
| Plasma Lance | L | energy | HIGH_EXPLOSIVE | hil | anti-armor beam |
| Plasma Autocannon | L | energy | ENERGY | autopulse | burst bolt |
| Plasma Bombardment | L | energy | HIGH_EXPLOSIVE | hephag | artillery |
| Plasma Torpedo (Heavy) | L | missile | HIGH_EXPLOSIVE | hurricane | MIRV / support |

- Beams recolored purple via `.wpn` colors. Projectiles/missiles reference the **vanilla
  `projectileSpecId`** directly (e.g. `heavyblaster_shot`, `sabot_srm`) — guaranteed load, but the
  bolt/missile visual is vanilla-colored for now. **Exception (2026-10-02):** the three plasma
  torpedoes have their own `.proj` (plain homing torpedoes, no Sabot/MIRV splits) and the Halo:
  Reach space Phantom torpedo look — see [plasma-torpedo-reach.md](plasma-torpedo-reach.md).
  **Energy Projector (2026-10-08):** Reach CCS glassing beam look + sounds, stats re-ruled (2 s
  charge, 3 s + 1 s beam, 10 s cooldown, 750 dps, 2000 flux/s) — see
  [energy-projector-reach.md](energy-projector-reach.md). First weapon of the weapons pass
  (visuals + sounds + stats per weapon, mount art deferred; see [../TODO.md](../TODO.md)).
  **Plasma Lance (2026-10-08):** now a half-second burst beam, 2000 HE dps (1000 per burst), white core /
  bluish-white fringe, Halo 3 beam rifle shot — see [plasma-lance.md](plasma-lance.md).
- `.wpn` `type` = mount (ENERGY/MISSILE); `weapon_data` `type` = damage — they differ deliberately
  (HE weapons use an ENERGY mount so they fit energy slots).

## Files

- `data/weapons/covenant_remnants_*.wpn` (13) + rows in `data/weapons/weapon_data.csv` (48 cols).
- **Ship re-fit:** all 20 `data/variants/*.variant` swapped to Covenant weapons (by slot size/type/arc)
  + Covenant wings. Verified: no vanilla ids, every weapon size-fits its slot.
- **Suppressor Beam** = the migrated `covenant_remnants_frigate1_turret`; set as frigate1's `BUILT_IN`
  weapon (`builtInWeapons` in the hull) and registered as a normal medium-energy weapon. Its sprite
  `graphics/covenant_remnants/weapons/covenant_remnants_frigate1_turret.png`.

## Cleanup done

Deleted the exploration orphans that referenced missing art / were unused: `plasmabarrage_*` +
`plasmamissile*` + `plasmaartillery` `.wpn`, the 4 covenant `.proj`, the 2 `plasmabarrage` `.system`
+ their `ship_systems.csv` rows, and `ai/PlasmaBarrageAI.kt`.

## Open / TODO

Bespoke weapon art; recolor projectile/missile bolts (currently vanilla visuals); balance tuning;
optional KINETIC variant of the Suppressor Beam. See [TODO.md](../TODO.md). **Resolved 2026-10-01:**
developer ruled the Suppressor Beam stays ENERGY.

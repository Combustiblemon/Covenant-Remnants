# Implementation — Plasma Cannon hit switched to the artillery impact (2026-10-10)

The Plasma Cannon's hit used to borrow the plasma torpedoes' Reach impact burst (`TorpedoBurst`, unit
4, no smoke) as a stand-in for its real Reach detonation. The real one is
`covenant_explosion_artillery_huge`, which the Plasma Bombardment has since rebuilt from the tags
(`ArtilleryBurst`, [plasma-bombardment.md](plasma-bombardment.md)). The developer asked for the switch
on 2026-10-10. The Cannon now draws that rebuilt impact at a cannon-sized scale with a quiet boom.
The splash stays visual only.

## Developer rulings (2026-10-10)

| Topic | Decision |
| --- | --- |
| Impact | `ArtilleryBurst.impact`, the Bombardment's rebuilt `covenant_explosion_artillery_huge`. |
| Unit | 8 su per Reach unit (Bombardment 20), chosen from the headless preview of 6 / 8 / 10 / 12: the same footprint as the old splash. |
| Sparks | 30 per hit (the Bombardment keeps 60). |
| Blast light | 1 s (the Bombardment keeps 2 s), so a Cannon firing once a second never stacks two. |
| Sound | Add a quiet artillery boom: the Bombardment's `cov_damage_large` explosion at volume 0.15, pitched up ~1.2 to read as a smaller gun. |
| Damage | Unchanged: the splash is visual only, no area damage. |

Choices made inside those rulings (stated in the approved plan; say if any is wrong):

- **New `artilleryImpact` signature.** `TorpedoRenderPlugin.artilleryImpact(location, unit, sparks, lightLife)`
  gains a unit, a spark count and a light life. The last two default to the Bombardment's values.
  `PlasmaBombardmentOnHit` passes only its `UNIT` (20), so the Bombardment is unchanged.
- **Same knobs on `ArtilleryBurst.impact`.** It gains the same two parameters. `SPARKS` (60) and
  `BLAST_LIGHT_LIFE` (2 s) are no longer private, so the plugin can use them as defaults. The 5 long
  sparks (`sparks_long_huge`) stay at 5.
- **Shorter light.** The blast light's intensity curve plays over the shorter life.
- **Two Cannon scales.** `PlasmaCannonOnHit.UNIT` (4) remains the in-flight aura scale, which
  `PlasmaCannonOnFire` uses. The hit has its own `IMPACT_UNIT` (8), `IMPACT_SPARKS` (30) and
  `IMPACT_LIGHT_LIFE` (1 s).
- **Sound id.** New id `covenant_remnants_plasma_cannon_explosion` in `sounds.json`, pointing at the
  same 4 `sounds/plasma_bombardment/explosion_*.ogg` files at volume 0.15. The pitch is random in
  1.14–1.26 (1.2 ± 5 %, the Bombardment's spread pattern).
- **`splash()` stays.** It is still the Plasma Mortar's impact (the plan said to remove it; the Mortar
  turned out to use it too). Only its doc comment changed.

## Preview (headless, `assets/reference/plasma-cannon/`)

`tools/CannonImpactHarness.java` runs the real bursts from the jar. One hit per row, frames 0.05 / 0.2 /
0.5 / 1.0 s after it, at 0.75 su/px. `tools/render_cannon_impact.py` renders them with the real sprite
shader, over scale rings at 25 / 50 / 100 su.

| Row | Look | Most sprites alive |
| --- | --- | --- |
| Old splash (torpedo burst, unit 4) | white-violet fireballs inside ~25 su, violet glow to ~40 su; gone by 1 s | 72 |
| **Shipped: artillery, unit 8, 30 sparks, 1 s light** | blue-white flash core ~25 su (glow to ~45 su), blue fireball cluster ~25 su, sparks to ~50 su; light gone at 1 s | 64 |
| Artillery unit 6 | flash ~20 su, sparks ~40 su | 94 |
| Artillery unit 10 | glow ~55 su, sparks ~60 su | 94 |
| Artillery unit 12 | flash ~60 su, sparks to ~85 su | 94 |
| Bombardment (unit 20) | flash to ~170 su, sparks past 150 su | 94 |

Previews: `preview/impact_units.png` (all rows), `preview/impact_close_u4_u8_u10.png` (the ruling-round
close-up) and `preview/impact_final_vs_old.png`.

## Files

- `src/.../weapons/PlasmaCannonOnHit.kt`:
  - calls `artilleryImpact(location, IMPACT_UNIT, IMPACT_SPARKS, IMPACT_LIGHT_LIFE)`;
  - plays the new explosion id;
  - new constants `IMPACT_UNIT`, `IMPACT_SPARKS`, `IMPACT_LIGHT_LIFE`.
- `src/.../plugins/TorpedoRenderPlugin.kt` — `artilleryImpact(location, unit, sparks, lightLife)`; the
  `splash()` comment now names the Mortar.
- `src/.../utils/render/torpedo/BombardmentEffects.kt` — `ArtilleryBurst.impact(location, unit, sparks,
  lightLife)`; `SPARKS` / `BLAST_LIGHT_LIFE` no longer private.
- `src/.../weapons/PlasmaBombardmentOnHit.kt` — passes its `UNIT`.
- `src/.../weapons/PlasmaMortarOnHit.kt` — comment only (its splash unit is compared with the torpedoes,
  not the Cannon).
- `data/config/sounds.json` — `covenant_remnants_plasma_cannon_explosion` (4 files, volume 0.15).
- `jars/CovenantRemnants.jar` rebuilt (`gradlew buildMod`).
- `assets/reference/plasma-cannon/tools/` (gitignored) — `CannonImpactHarness.java`,
  `render_cannon_impact.py`, README; `preview/`.

## Verification done

- `gradlew buildMod` green. The jar holds the new `PlasmaCannonOnHit` (with its constants and sound id),
  `ArtilleryBurst` and `TorpedoRenderPlugin`. No compiler warnings in the touched files.
- `sounds.json` parses (34 ids), and every sound file it names exists.
- The harness runs against the rebuilt jar. Its numbers are in the preview table above.

## In-game checklist

1. Plasma Cannon hit on shield, armor and hull:
   - a blue-white flash, a small blue fireball cluster and sparks at the hit point;
   - about the old splash's size (~25 su core, sparks to ~50 su);
   - no dark smoke; the glow is gone within a second.
2. Sound:
   - a quiet, higher-pitched boom on each hit, under the corvette fire shot and the vanilla hit sound;
   - two Cannons do not turn into mud.
3. No area damage: ships next to the hit take nothing from the splash.
4. The Bombardment's impact and sound are unchanged (big, 2 s glow, 60 sparks). The Mortar's impact
   is unchanged.
5. Frame rate with several Cannons firing (64 sprites per hit at most, vs 72 before).

Closed by the developer 2026-10-10.

## Open items

- `IMPACT_UNIT` 8, 30 sparks, 1 s light and the 0.15 volume are placeholders for the in-game look.
- The aura constants in `CannonFlight` are unchanged placeholders.
- Area damage stays off (balance.md TODO: the developer may revisit).

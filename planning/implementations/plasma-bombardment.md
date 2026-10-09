# Implementation — Plasma Bombardment, Reach Covenant AA gun (2026-10-09)

Tenth weapon of the weapons pass. `covenant_remnants_plasmabombardment` (large energy mount, HE; not
mounted on any variant yet) turns from a plain Hephaestus Assault Gun mirror (vanilla `hephag_shot`,
`hephaestus_fire`, 120 HE / 0.25 s) into the **Halo: Reach Covenant AA gun** (the stationary "bfg"
battery that is a mission objective): a 1 s charge at the muzzle, then one heavy blue plasma bolt
with a short comet tail, the AA gun's firing flash, and on impact an HE splash with the rebuilt
`covenant_explosion_artillery_huge`. Built the Mortar's way: invisible engine round, everything drawn
by `TorpedoRenderPlugin` from the dumped tags.

## Developer rulings (2026-10-09)

| Topic | Decision |
| --- | --- |
| Behaviour | Heavy siege shell: slow cycle, one big slow round, big HE splash, straight flight (no lob). |
| Source | Reach Covenant AA gun, HREK `objects\vehicles\covenant\turrets\bfg\` (developer: "the big stationary weapon that is the objective to destroy in a bunch of missions"). |
| Route | Full rebuilt round: invisible engine round + a new flight class from the dumped tags, HE splash on-hit. |
| Charge-up | Short charge: Reach timing capped ~1 s (Reach charges 2 s) — charge glow + plasma bolts at the muzzle, `bfg_charge` sound. |
| Impact | Rebuild `covenant_explosion_artillery_huge`: flash, fireball, blue plasma, plasma gas, sparks, glow flare, light. No smoke, no metal chips / debris models. |
| Tail | "Short tail": Reach widths, colours and curves, contrails cut to ~120 su (blue `hunter_beam`) and ~200 su (wispy) behind the round (Reach: 100 / 200 WU, a sky streak). |
| Sounds | AA gun fire + impact, volumes 0.25, no in-flight loop. `weapons\bfg\bfg_fire` turned out to be the same recording as the Plasma Cannon's corvette `bfg_fire` (sample correlation 1.000) — per the ruling, `bfg_core_fire` is mixed under it into new files. |
| Stats | 1000 HE + 400 HE splash (radius 150), 2.5 s cycle, 900 range, 500 speed, 1000 flux/shot, 20 OP, `USE_LESS_VS_SHIELDS`. |
| Mount art | deferred: vanilla Hephaestus sprites. |

Choices made inside those rulings (stated in the approved plan; say if any is wrong):
- **Cycle** = chargeup 1 s + chargedown 1.5 s (`autocharge`, Gigacannon pattern: `fireSoundOne` =
  charge, `fireSoundTwo` = fire).
- **Splash** 400 HE inside a 64 su core, falling to 0 at 150 su (Reach AOE core 0.75 of 1.75 WU, the
  Mortar's mapping); it also hits the struck target (direct hit up to 1400).
- **Unruled CSV columns**: impact 200 (Hellbore), turn rate 10 (Hellbore 5 / Hephaestus 20), spread 0,
  projectile hitpoints blank (Mortar-proven), base value 5500 kept.
- **One fire point** on the centreline (`[25,0]` / `[40,0]`): the charge has one known muzzle; the
  shell leaves between the placeholder twin barrels. `animationType NONE` (no engine flash: the
  Reach firing flash is rebuilt); recoil kept.
- **Firing flash rebuilt** from `firing.effect` (long muzzle flash, plasma pulses, flash, plasma gas,
  muzzle light volume, big glow + anamorphic flares, light). Not built: `muzzle_smoke`, the vent gas,
  everything inside the turret body (`core_firing`, `core_charge` event), the overheat vent.
- **Scales** (placeholders, set from the headless harness): round 12 su per Reach unit (at the
  Mortar's 8 the 0.3 WU-thick bolt read as a needle thinner than the Mortar round), charge 10 (at 6
  it was a ~10 su sparkle), firing flash 6, impact 20 (the main cloud then fills about the 150 su
  splash, gas and sparks run past it).
- **Impact sound** `cov_damage_large` (the 4 with the earliest peak); `phantom_destroyed_lod`
  decoded, not used. A miss at max range fades, no detonation (Mortar behaviour).

Vanilla anchors (0.98a-RC8): Hellbore L 750 HE / 3 s (250 dps) / 900 / 500 speed / 750 flux / 16 OP;
Hephaestus L 120 HE x 4/s (480 dps) / 900 / 800 / 20 OP; Gigacannon = the chargeup precedent.

## Look (`utils/render/torpedo/BombardmentEffects.kt`, curves `BombardCurves.kt`)

- **Charge** (`BombardCharge`, fed every frame by `PlasmaBombardmentEffect` with the weapon's charge
  level; a projectile weapon's level rises 0 -> 1 over the chargeup and falls over the cooldown, so
  "charging" = level in (0,1) with no cooldown — read from the obf weapon class): two
  `core_charge.light_volume_system` glows + `core_charge.light` swelling with the charge,
  `plasma_blue_medium` gathering along the barrel (2 -> 60 / s), `core_charge_plasma` bursts (new
  material: blue plasma through `plasma_mask`), `plasma_bolt` lightning (new material: `plasma_bolt_a/b`
  through the `blue_plasma_bolt` bands) swinging from sideways to along the barrel. Particles follow
  the turret. Fades 0.15 s after the charge ends.
- **Firing flash** (`ArtilleryBurst.muzzle`, spawned when the round is picked up, at its spawn point):
  `muzzle_flash_long_soft` (first frame, base at the muzzle, palettized through
  `muzzle_flare_plasma_turret_long`), 5-7 `plasma_pulse` jets thrown forward (intensity 25 -> 1), the
  `flash_large`, 10-25 `plasma_gas` puffs (soft_smoke_small shape; the `plasma_blue_02` palette
  exports corrupt, so the puffs take the particle's own colour curve), the 10 -> 20 WU muzzle light
  volume (24 profiles, white-hot at the muzzle), `big_glow` and anamorphic flares (curves read back to
  front, as on the torpedo), `firing.light`.
- **Round** (`BombardFlight`, the Wraith pattern): 8 light-volume profiles over 5.5 WU behind the
  round, each stretched along the flight by its 2 WU `profile_length`, blue with a peach-white head;
  `star_flare`, the Wraith mortar star, a faint anamorphic streak and the light glow; contrails
  `hunter_beam` through `energy_trail_blue` and `wispy_trail` (peach -> indigo), points every 0.75 WU,
  each living length / speed so the tails stay ~120 / ~200 su.
- **Impact** (`ArtilleryBurst.impact`): `explosive_flash` (flash_soft, intensity 15), 10 artillery
  fireballs (0.05 s delay; new material through the `fireball_plasma` blue rows, alpha-blended), 10
  plasma gas puffs, 6 `plasma_blue_huge` (0.15 s delay; the projector's material), 60 + 5 blue sparks
  (Reach 150 + 5, sizes floored — placeholder), `huge_glow` (up to 10 WU, brightness 4), the 2 s
  `covenant_explosion_huge` light.
- Reach lights are scene lights: drawn as glows at intensity x `BombardLook.LIGHT_SCALE` 0.07 (the
  round's light 5 -> 0.35, the Wraith round's value). Placeholder.

### Decoder fix found on the way

The stock `dump_tags.ps1` maps sampled curves to their output range only for "clamped" functions.
Every scalar function's range sits in its mapping blob (floats at bytes 4 and 8): unclamped curves
were printed as raw 0..1 and unclamped Basic constants as 0 (e.g. the charge plasma scale is really
0.2 -> 2.5, the firing light 80 -> 0, the firing pulse count 7 / 5). The bombardment copy of the
dumper prints the blob (`hex=`), and `gen_bombard_curves.py` maps unclamped curves through it. Open
item below: earlier weapons' curves may hold such normalized values.

## Reach source (HREK, dumped 2026-10-09)

Full numbers in `assets/reference/plasma-bombardment/reach-data/spec.md`. In short:
- `bfg.weapon`: charge 2 s, then 3 shots at 1.3 / s, overheats after a volley. Round `bfg_round`:
  200 WU/s, 2000 WU range, damage radius 0.75-1.75 (core 0.75), detonates with
  `covenant_explosion_artillery_huge`.
- Round fx: light volume 5.5 WU, contrails `hunter_beam` + `soft_contrail`, anamorphic + star + Wraith
  lens flares, light; in-flight `wraith_projectile` loop (not used).
- `charging.effect`, `firing.effect`, `covenant_explosion_artillery_huge.effect`: see Look.
- Sounds (`fsb_names.tsv` index, vgmstream `-s index+1`): 11563 `bfg_charge` (used, 1.05 s), 11565
  `bfg_core_fire` (mixed into the fire), 11575-11577 `bfg_fire1-3` (used), 11564 `bfg_core_charge`,
  11578-11580 `bfg_fire_surr1-3` (raw only), 4005-4023 `cov_damage_large` (4011 / 4015 / 4020 / 4006
  used), 4223 `phantom_destroyed_mono_lod` (raw only).

Data under `assets/reference/plasma-bombardment/reach-data/` (gitignored): `dump32/`, `summary/`,
`textures/`, `audio/`, `preview/`, `tools/`, README, `spec.md`.

## Files

- `data/weapons/weapon_data.csv` — Bombardment row: damage 1000, impact 200, turn 10, energy/shot 1000,
  chargeup 1, chargedown 1.5, spread 0, proj speed 500, proj hitpoints blank, `USE_LESS_VS_SHIELDS`
  (range 900, OP 20, base value 5500 kept).
- `data/weapons/covenant_remnants_plasmabombardment.wpn` — centreline offsets, `animationType NONE`,
  `autocharge`, `projectileSpecId` `covenant_remnants_plasmabombardment_shot`, `everyFrameEffect`
  `PlasmaBombardmentEffect`, `fireSoundOne` charge / `fireSoundTwo` fire.
- `data/weapons/proj/covenant_remnants_plasmabombardment_shot.proj` (new) — invisible `BALLISTIC`
  round, width 24, `onHitEffect` `PlasmaBombardmentOnHit`.
- `src/.../weapons/PlasmaBombardmentEffect.kt` (new) — creates the render plugin, feeds the charge.
- `src/.../weapons/PlasmaBombardmentOnHit.kt` (new) — `DamagingExplosionSpec` HE splash (400, core 64,
  radius 150), `artilleryImpact`, explosion sound (pitch 0.94-1.04); the unit constants.
- `src/.../utils/render/torpedo/BombardmentEffects.kt` (new) — `BombardLook`, `BombardContrail`,
  `BombardFlight`, `BombardCharge`, `ArtilleryBurst`.
- `src/.../utils/render/torpedo/BombardCurves.kt` (new, generated) — 87 tag curves.
- `src/.../plugins/TorpedoRenderPlugin.kt` — `charge(weapon)`, `artilleryImpact(location)`, bombard
  rounds / charges / artillery bursts advanced and ended like the Wraith rounds; the projectile-list
  scan is now one pass for the Mortar and Bombardment rounds (`scanRounds`; Mortar behaviour and log
  lines unchanged), new one-time log lines `Bombardment round picked up …` / `Bombardment round ended …`.
- `src/.../utils/render/torpedo/TorpedoRenderer.kt` — Bombardment contrails with the ribbons, artillery
  fireballs with the burst fireballs, plasma gas, the plasma passes (`PLASMA_ORDER`), bodies / glows /
  stars, `PROJECTOR_FLASH` and the muzzle streak with the flashes.
- `src/.../utils/render/rupture/RuptureTexture.kt` — 12 `BOMBARD_*` textures; `RuptureParts.kt` —
  materials `BOMBARD_CONTRAIL`, `BOMBARD_WISPY`, `BOMBARD_MUZZLE`, `BOMBARD_PULSE`, `BOMBARD_GAS`,
  `BOMBARD_CORE`, `BOMBARD_BOLT`, `ARTILLERY_FIREBALL`; kinds `BOMBARD_*` / `ARTILLERY_*`. No shader
  change.
- `graphics/fx/plasma_bombardment/` (new, 12 Reach exports) — registered in `data/config/settings.json`
  (`plasma_bombardment_*`).
- `sounds/plasma_bombardment/charge.ogg`, `fire_1-3.ogg`, `explosion_1-4.ogg` (new),
  `data/config/sounds.json` — `covenant_remnants_plasma_bombardment_{charge,fire,explosion}` at 0.25.
- `jars/CovenantRemnants.jar` rebuilt (`gradlew buildMod`).

## Verification done

- `gradlew buildMod` green; the new classes are in the jar; no new compiler warnings.
- `.wpn`, `.proj`, `sounds.json`, `settings.json` parse (comments / trailing commas stripped);
  every new sound / texture path exists; every CSV row has 48 fields.
- Shipped textures previewed (`textures/shipped_preview.png`).
- Headless harness (`BombardHarness` + `render_bombard.py`, real classes, real sprite shader):
  charge 0.3 / 0.7 / 0.98 s, flash 1.03-1.22 s, flight, impact 2.23-3.2 s
  (`preview/bombard.png`). Blue tail ~120 su behind the round; impact cloud ~100 su across with sparks
  reaching the 150 su ring; muzzle flash peaks at ~100 su radius for a frame or two.

## In-game checklist

Not on any variant: give it with `CovenantGiveAll` and fit a large energy slot.

1. Refit card: 1000 HE, range 900, 1000 flux, 20 OP; refire about 2.5 s.
2. Charge: holding fire, about 1 s of blue glow, gathering plasma and small lightning bolts at the
   gun's tip, swinging forward as it fills; charge sound. Tap fire: it still completes (autocharge).
3. Shot: white-blue muzzle flash with plasma jets and gas thrown forward, brief blue bloom; fire sound
   once per shot (no Plasma Cannon double).
4. Round: no engine ball / spikes; a heavy blue bolt with a peach-white head, a short blue tail
   (~120 su) and fainter wisps (~200 su) starting at the round, not a spear; flies slowly (500 su/s).
5. Hit: warm-white flash into blue, blue fireballs and plasma, sparks, big blue glow; no dark smoke;
   explosion sound; ships / fighters within ~150 su take splash damage.
6. Miss at max range fades out (capital: Covenant Systems range x1.8 -> ~1600 su).
7. Volumes (all 0.25): charge, fire, explosion.
8. Plasma Mortar and Plasma Cannon look and sound unchanged.
9. Frame rate with several Bombardments.

If something does not draw: `starsector.log` should show `Bombardment round picked up from the
projectile list` once, and up to three `Bombardment round ended after …` lines.

## Open items

- Numbers placeholder until the balance pass (splash, cycle, speed).
- Scales (`ROUND_UNIT` 12, `CHARGE_UNIT` 10, `MUZZLE_UNIT` 6, `UNIT` 20), `LIGHT_SCALE`, spark count /
  size floors, drag values: placeholders for the in-game look.
- `plasma_blue_02` palette exports corrupt: plasma gas uses the particle colour instead.
- Not built: in-flight `wraith_projectile` hum, `muzzle_smoke`, vent gas, turret-body core effects,
  overheat vent, Reach's 3-shot volley.
- Earlier weapons' generated / hand-copied curves (torpedo, Wraith, projector, beam) may contain
  unclamped functions read as raw 0..1 (see Decoder fix) — audit on request.
- The Plasma Cannon's splash stands in for this same `covenant_explosion_artillery_huge`
  (`TorpedoBurst`); it could switch to `ArtilleryBurst` at a cannon-sized unit (developer call).
- `WraithFlight` could share `BombardContrail` (left untouched; it is in-game verified).
- No tooltip line for the splash (the Mortar has none either).
- Not mounted on any variant (developer to place when ready). Mount art.

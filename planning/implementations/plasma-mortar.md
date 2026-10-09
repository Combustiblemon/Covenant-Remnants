# Implementation — Plasma Mortar, Reach Wraith mortar (2026-10-09)

Ninth weapon of the weapons pass. `covenant_remnants_plasmamortar` (medium energy mount, HE; not
mounted on any variant yet) turns from a vanilla Mining Blaster mirror (`miningblaster_shot`,
vanilla sound) into the **Halo: Reach Wraith mortar**: a slow fat cyan-blue plasma blob with an
energy aura, an HE splash on impact, the Reach impact burst and the Wraith fire / explosion sounds.
**Revised the same day:** the engine-drawn `PLASMA` core still read as a spiky urchin, so the
engine round is now invisible and the whole Reach Wraith mortar round is rebuilt from its tags
(`WraithFlight`, see Look below).

## Developer rulings (2026-10-09)

| Topic | Decision |
| --- | --- |
| Behaviour / look | Wraith blob + AOE blast: slow plasma round, HE splash damage on impact via Kotlin on-hit, Reach impact burst at a bigger unit. Not a lob (straight flight). First build (engine `PLASMA` core + the cannon aura) read as an urchin; developer: "recreate the wraith projectile" → invisible engine round + the Reach round rebuilt from the dump. |
| Source | Reach Wraith mortar tags dumped (below). |
| Sounds | Fire `vehicles\wraith\wraith_fire_mortar` (9951); impact `vehicles\wraith\wraith_projectile_expl 1-4` (9956–9959) played by the on-hit. In-flight loop `wraithlp1-3` (9953–9955) pulled raw, not used. |
| Stats | Mortar-shaped, slightly above: 400 HE direct + 150 HE splash (radius 75), 1.5 s refire, 700 range, proj speed 500, 400 flux / shot, 10 OP, `USE_LESS_VS_SHIELDS`. Mining Blaster magazine and its +100 armor-bypass on-hit dropped. |
| Mount art | deferred: vanilla `blaster2_*` sprites, cyan glow. |

Choices made inside those rulings (say if any is wrong):
- **No smoke** in the impact burst, as the developer asked for the cannon.
- **Splash falloff** from Reach: full 150 inside a 20 su core, falling to 0 at 75 su (Reach
  `wraith_mortar_round`: core 0.6 of a 0.75–2.25 WU radius, lower bound 0). The splash also hits
  the directly struck target, so a direct hit deals up to 550.
- **Trail length:** the Reach round flies ~15 WU/s, ours 500 su/s. Everything it leaves behind
  (contrail, puffs, embers, plasma) is emitted per distance (Reach rate / Reach speed) and lives its
  Reach life × the speed ratio, so the tail keeps Reach's length (~7 WU) instead of a 250 su spear.
- **Round scale 8 su per Reach unit** (`ROUND_UNIT`, the impact stays at 6): Reach proportions
  read small at 6. Embers floored from the tag's 0.02 WU to 0.25 (sub-pixel otherwise). Light
  intensity 0.35 is not in the dump.
- A miss that runs out of range just fades: no detonation (on-hit only).

Vanilla anchor (0.98a-RC8): Mining Blaster 200 HE (+100 armor), 4-shot magazine regen 0.5/s,
0.2 s, 500 range, 1000 speed, 300 flux, 10 OP. Heavy Mortar 2 × 110 HE, 0.7 s, 700 range, 500 speed.

## Look — the rebuilt Wraith round (`utils/render/torpedo/WraithMortarEffects.kt`)

Engine round invisible: `BALLISTIC` spawn, fringe / core alpha 0, no `glowRadius`, the invisible
torpedo sprite (obf `BallisticProjectile.render` draws only the glow and the tracer, both scaled
by those alphas). `WraithFlight` at 8 su / WU draws, from the tags:

- body: `projectile.light_volume_system`, 8 glow profiles over 2 WU from 1.5 behind the round,
  thickness 0.5 → 0.6, cyan-blue (0.42,0.70,1.0), intensity 1 → 2 toward the head;
- `projectile.lens_flare` `star_flare` radius 1.5 WU, (0.22,0.30,1.0); `projectile.light` as a
  soft 4 WU glow (0.60,0.63,1.0);
- contrail: `flaming_trail` ribbon (new `WRAITH_CONTRAIL`, palette mode through
  `wraith_mortar_contrail`), size 0.65, colour white → (0.58,0.80,1.0), alpha curve from the tag,
  uv 0.5 / WU, starting at the round (alpha 0 at age 0);
- left behind: `plasma_trail` puffs (new `WRAITH_PLASMA_TRAIL`, tinted by the tag's colour curve,
  shaped by `fire_large`, intensity 2, random black point between the tag's two curves),
  `spark_ember` sparkles (flicker 3.6–16), `plasma_blue_medium` (the torpedo plasma material,
  same `blue_plasma` palette, intensity 2).

### Fix — rebuilt round never drew (2026-10-09)

In-game the round was invisible (only the muzzle flash and the hit). A headless run of the real
`WraithFlight` (`assets/reference/plasma-mortar/reach-data/tools/WraithHarness.java`, stand-in round
at 500 su/s) drew correctly (body, star, light, ~70 puffs, 34-point contrail), so the flight was
being ended on its first frame by `TorpedoRenderPlugin`. Two suspects, both handled:
`isExpired` (a `BALLISTIC` round is expired when `health < 0`, and this row's projectile hitpoints
are blank — the loader default is not in the bundled sources) is no longer checked; and "not in
play" now only ends a round after it was once seen in play (a new round may not be in the engine's
lists yet), with a 3 s fallback for a round never seen. Any end sooner than 0.2 s or of a round
never seen in play is logged (`Wraith round ended after …`) — check `starsector.log` if it still
does not draw.

Second in-game test: still invisible, and no `Wraith round ended` line in the log — so the rounds
were most likely never registered by the on-fire hook (the projectile factory is in the obf
`loading` package, not in the bundled sources, so this could not be read). Tracking no longer
depends on it: `TorpedoRenderPlugin` also picks mortar rounds (spec id
`covenant_remnants_plasmamortar_shot`) up from `engine.projectiles` each frame, deduped against
`onFire`, and the new `everyFrameEffect` `PlasmaMortarEffect` creates the plugin as soon as a mortar
is on the field. One-time log lines say which path registered the first round (`registered by
onFire` / `picked up from the projectile list`) and the first three ends give age and distance.
Projectile hitpoints: a `BALLISTIC` round is removed only when `health < 0`, so 0 HP keeps it alive
(and the hits prove it lives).

Third test (developer: "it now renders fine"): the log showed `picked up from the projectile list`
for every round and never `registered by onFire` — **the weapon's `onFireEffect` is not called for
`BALLISTIC` rounds** (0.98a-RC8); the projectile-list scan is what tracks them. Misses flew ~1400 su
because the test ship was a capital: Covenant Systems gives energy weapons ×1.8 range on capitals
(×1.2–1.6 smaller), 700 × 1.8 = 1260 plus the 0.3 s fade. Explosion volume 0.6 → 0.3 (developer).

## Reach source (HREK, dumped 2026-10-09)

`objects\vehicles\covenant\wraith\turrets\wraith_mortar\weapon\`: `wraith_mortar.weapon`,
`projectiles\wraith_mortar_round.projectile`, `damage_effects\wraith_mortar_round{,_impact,
_shockwave}.damage_effect`; `objects\vehicles\covenant\wraith\fx\weapon\mortar\`:
`projectile.{effect,contrail_system,lens_flare,light,light_volume_system}`,
`particles\{plasma_trail,glow_soft,smoke_fiery_large,muzzle_flash_long_fiery}.particle`,
`detonation.{effect,light}`, `detonation_{embers,sparks_long}.cheap_particle_emitter`,
`firing.{effect,light}`.

- Weapon: 3 s fire recovery, no error. Firing: `glow_soft` 0.085 s (size 2),
  `muzzle_flash_long_soft` 0.125 s, `smoke_fiery_large` 0.25 s (size 1–2), `flash_large` 0.15 s;
  sounds `wraith_fire_mortar` + `flak_cannon_lod_far`.
- Round: 20 → 10 WU/s, gravity 0.66 (lobbed), 200 WU range. Damage: detonation 445 (core 0.6,
  radius 0.75–2.25, shake 18), direct impact 223, plasma.
- Body: light volume 2 WU, offset −1.5, thickness 0.5 → 0.6, cyan-blue (0.42,0.70,1.0),
  intensity 1 → 2 toward the head; `star_flare` radius 0.15 (×1.5 over age), (0.22,0.30,1.0);
  light (0.60,0.63,1.0). Looping sound `wraith_projectile`.
- In flight: contrail `flaming_trail` 0.5 s, size 0.65, white → (0.31,0.61,1.0) via
  `wraith_mortar_contrail`; `plasma_trail` puffs 150/s (0.35–0.5 s, size 0.35–0.5,
  `plasma_clouds_128_a`), `spark_ember` 100/s, `plasma_blue_medium` 60/s (0.25–0.35 s, size
  0.65–0.75). Read as the aura's wisps; trail not built.
- Detonation: `blast_radius` 0.2 s (size 5, scale 0.5 → 2), `explosive_flash` 0.17 s (size 2),
  `fireball_plasma_large` (0.3 s at 30/s, 0.35–1.25 s, size 0.5–0.75), rolling smoke, ejecta,
  `plasma_blue_large` 0.5 s; light 40 → 0, cyan-white (0.72,0.78,1.0) → blue; sounds
  `wraith_projectile_expl` + far / tail layers. Mapped to the torpedo impact burst (no smoke) at
  unit 6 + `hitGlowRadius` 110.

Data under `assets/reference/plasma-mortar/reach-data/` (gitignored): `dump32/`, `summary/`,
`audio/raw_*.wav`, `tools/`.

## Files

- `data/weapons/weapon_data.csv` — Plasma Mortar row: range 700, damage 400, ammo cleared,
  energy/shot 400, chargedown 1.5, proj speed 500 (OP 10, turn 7, `USE_LESS_VS_SHIELDS` kept).
- `data/weapons/covenant_remnants_plasmamortar.wpn` — `projectileSpecId`
  `covenant_remnants_plasmamortar_shot`, `onFireEffect` `PlasmaMortarOnFire`, `fireSoundTwo`
  `covenant_remnants_plasma_mortar_fire`, cyan `glowColor`, `muzzleFlashSpec` (10 cyan-white
  particles, 45 long, 0.25 s).
- `data/weapons/proj/covenant_remnants_plasmamortar_shot.proj` (new) — invisible `BALLISTIC` round
  (zero-alpha colours, invisible sprite), width 16 = collision diameter, `onHitEffect`
  `PlasmaMortarOnHit`.
- `src/.../weapons/PlasmaMortarOnFire.kt` (new) — `TorpedoRenderPlugin.trackWraithRound(projectile, 8)`.
- `src/.../weapons/PlasmaMortarEffect.kt` (new, `everyFrameEffect`) — creates the render plugin; the
  plugin's projectile-list scan is the fallback tracker.
- `src/.../utils/render/torpedo/WraithMortarEffects.kt` (new) — `WraithCurves` (tag curves) +
  `WraithFlight` (body, star, light, contrail, puffs, embers, plasma).
- `src/.../utils/render/rupture/RuptureTexture.kt` — `WRAITH_FLAMING_TRAIL`,
  `WRAITH_CONTRAIL_PALETTE`; `RuptureParts.kt` — materials `WRAITH_CONTRAIL`,
  `WRAITH_PLASMA_TRAIL`, kind `WRAITH_PLASMA_TRAIL`.
- `graphics/fx/plasma_mortar/flaming_trail.png`, `wraith_mortar_contrail.png` (Reach exports),
  registered in `data/config/settings.json` (`plasma_mortar_*`).
- `src/.../utils/render/torpedo/TorpedoRenderer.kt` — draws the Wraith rounds (contrail with the
  trails, puffs before the plasma, light / body / star with the bodies, embers before the flashes).
- `src/.../weapons/PlasmaMortarOnHit.kt` (new) — `DamagingExplosionSpec` HE splash (150, core 20,
  radius 75, min 0, no graphic, source spared), `TorpedoRenderPlugin.splash(point, 6)`, Wraith
  explosion sound (pitch 0.94–1.04).
- `src/.../utils/render/torpedo/TorpedoEffects.kt` — `CannonFlight` constants moved into a new
  `AuraStyle` (preset `CANNON`, unchanged values; the first-build `MORTAR` preset was removed).
- `src/.../plugins/TorpedoRenderPlugin.kt` — `trackCannonRound` takes a style (now `internal`);
  new `trackWraithRound`, the Wraith rounds advanced / ended like the cannon rounds.
- `sounds/plasma_mortar/fire.ogg`, `explosion_1-4.ogg` (new), `data/config/sounds.json` —
  `covenant_remnants_plasma_mortar_fire` (0.7), `covenant_remnants_plasma_mortar_explosion` (0.3; 0.6 was too loud).
- `jars/CovenantRemnants.jar` rebuilt (`gradlew buildMod`).

## In-game checklist

Not on any variant: give it with `CovenantGiveAll` and fit a medium energy slot.

1. Refit card: 400 HE, 1.5 s, range 700, 400 flux, 10 OP.
2. Round: no engine ball / spikes at all; a short cyan-blue glowing body brightest at the head,
   blue star flare, soft blue light, a short flaming blue-white contrail starting at the round,
   white-blue plasma puffs, sparkles and blue plasma left just behind it; the tail stays short
   (~60 su). The Plasma Cannon's look unchanged.
3. Hit: cyan flash + Reach impact burst (fireball, blue plasma, sparks, flare, no smoke), Wraith
   explosion sound; nearby ships / fighters within ~75 su take splash damage.
4. Fire sound once per shot, not stacking into mud at 1.5 s.
5. Frame rate with several mortars.

## Open items

- Numbers placeholder until the balance pass (splash, refire, speed, aura constants).
- In-flight hum (`wraithlp1-3`) not used.
- No headless preview render was made for the rebuilt round (in-game check only).
- `ROUND_UNIT` 8, ember floor, light intensity: placeholders.
- No detonation on a miss at max range.
- Not mounted on any variant (developer to place when ready).
- Mount art.

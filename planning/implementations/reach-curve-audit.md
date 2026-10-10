# Implementation — Reach curve audit (2026-10-10)

The developer asked for this audit on 2026-10-10. The Plasma Bombardment pass found a decoder trap in
the Halo: Reach tag dumper ([plasma-bombardment.md](plasma-bombardment.md), "Decoder fix found on the
way"):

- Every scalar function stores its real output range as the two floats at bytes 4 and 8 of its
  `Mapping` blob, and the real value is `min + f · (max − min)`.
- The stock `dump_tags.ps1` applied that range only to functions flagged "clamped".
- So it printed unclamped curves as raw 0..1 and unclamped Basic constants as 0.
- Its `summarize.py` then dropped unclamped scalars altogether.

Every effect built from Reach dumps before the Bombardment could hold such values. This audit
re-dumps the same tags with the patched dumper and checks every value the Kotlin uses. It reports
only; the developer then ruled to keep every current look (below), so no code changed.

## Method

1. **Tag lists.** Each effect's tag list is taken from the header line (`# <tag path>`) of every file
   in its existing dump folder. Bitmaps, shaders, sounds, material effects, physics templates and the
   cheap-particle library carry no curves the Kotlin uses and are skipped. That leaves 134 tags in 9
   effects, and no new tags were searched (standing ruling).
2. **Re-dump.** The tags are dumped again with the patched dumper
   (`plasma-bombardment/reach-data/tools/dump_tags.ps1`, `hex=` per function blob) into
   `<effect>/reach-data/dump32_audit/`. Samples are 33, or 32 for long-night-of-solace and
   elite-shield to match their original dumps. The dumper `Set-Location`s into the kit, so the output
   folder is passed as an absolute path.
3. **Decoder check.** Every function in the audit dumps has its blob (2 653 functions). For every
   clamped scalar function, the blob's bytes 4 / 8 equal the stock dumper's printed `clamp=[min,max]`
   (0 differences), so the byte layout is confirmed.
4. **Suspects.** A function is a suspect when its real values differ from what the stock dump showed:
   an unclamped scalar whose range is not 0..1 (curves), or whose min is not 0 (Basic). Only these can
   be wrong. There are 118 suspects over the 9 effects, and the elite shield has none.
5. **Curves.** For every scalar `ReachCurve` in the six curve objects, the source function is found by
   matching samples. Candidates are narrowed by the tag the section comment names, with a fallback to
   the whole effect. Each match compares both the real values and the stock reading:
   - **OK** when the curve matches the real values;
   - **MISMATCH** when it matches only a suspect's stock reading.

   Tolerance 0.08 relative: the Kotlin keeps 9 samples, and resampling a 33-step dump moves the
   shoulders of a curve by up to ~0.07. A raw-versus-real difference is range-sized.

   `BombardCurves.kt` (made with the patched pipeline) is the control: 72 / 72 OK.
6. **Generated curve files.** `ReachCurves.kt` and `ShieldImpactCurves.kt` were regenerated from the
   audit dumps with their own generators (`gen_curves.py`, `gen_shield_curves.py`). The output is
   byte-identical to the shipped files (44 and 19 curves).
7. **Constants.** Every suspect was checked by hand against the Kotlin constants, the inline formulas
   and the cheap-route `.proj` / `.wpn` values.
8. **Halo 3 Ark / ODST.** Their XML exports already on disk were re-decoded with a range-aware decoder
   (no kit access).

## Findings — values that differ from the tags

None of these is a crash or a broken feature: every look below passed its in-game check. They are
places where the Kotlin does not use the tag's real value. The verdict column says what the tag
really holds.

| # | Effect | Kotlin | Tag :: field | Value used | Real value | Kind |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | Plasma Beam / Suppressor | `PlasmaBeamCurves.kt:61` `IMPACT_PLASMA_ASPECT` (used as the impact plasma's width factor, `PlasmaBeamLook.kt:581`) | `impact_plasma.particle` :: aspect ratio (Curve, unclamped, range 1..2) | 0 → 1 (raw), floored at 0.05 | 1 → 2 | the curve trap |
| 2 | Plasma Beam / Suppressor | `PlasmaBeamLook.kt:108-111` `SPARK_LONG_COUNT` 6, `_LIFE` 0.3 s, `_SPEED` 1.5 WU/s, `_CONE` 45° | `impact.effect` > `spark_long` emitter: starting count, lifespan, initial velocity, emission angle (Basic, unclamped) | placeholders ("did not decode") | 15; 0.15–0.25 s; 1–2 WU/s; 45–70°; plus emission radius 0.02 WU, self-acceleration 1 | invisible constants |
| 3 | Plasma Beam / Suppressor | `PlasmaBeamLook.kt:407` muzzle light: noise × `MUZZLE_LIGHT_GAIN` 0.3 | `firing_3p.light` :: intensity (Periodic, unclamped, range 2.5..6) | 0–0.3 placeholder flicker | 2.5–6 periodic (the stock dump showed 0.21–0.86) | invisible range |
| 4 | Plasma Beam / Suppressor | `PlasmaBeamLook.kt:561-562` muzzle plasma drawn square | `muzzle_flash_plasma.particle` :: aspect ratio (Basic, ranged, unclamped) | 1 | 1–1.5 per particle | invisible constant |
| 5 | Plasma torpedoes | `TorpedoEffects.kt:554` muzzle round size `baseSize · 0.5 · x` | `firing.effect` > `muzzle_flash_round` emitter :: particle scale (Curve, unclamped, range 0.5..1) | 0 → 1 (raw) | 0.5 → 1 | the curve trap |
| 6 | Plasma torpedoes | `TorpedoEffects.kt:567` muzzle `flash_large` colour, no intensity | `flash_large.particle` :: intensity (Basic, unclamped) | 1 | 1.5 | invisible constant |
| 7 | Plasma torpedoes | `TorpedoEffects.kt:426` `MUZZLE_ROUNDS` 2 ("one per sheet frame") | `firing.effect` > `muzzle_flash_round` emitter :: particle starting count (Basic, unclamped) | 2 | 1 | invisible constant |
| 8 | Energy Projector | `ProjectorEffects.kt:275-276` flicker glow drawn square | `glassing_flicker_glow.particle` :: aspect ratio (Basic, unclamped) | 1 | 1.7 | invisible constant |
| 9 | Energy Projector | `ProjectorEffects.kt:468-471` impact half glow drawn square | `glow_soft_half.particle` :: aspect ratio (Basic, unclamped) | 1 | 1.7 | invisible constant |
| 10 | Energy Projector | `ProjectorEffects.kt:457-460` impact bubble drawn square | `glassing_bubble.particle` :: aspect ratio (Curve, unclamped, range 1..1.25) | 1 | 1 → 1.25 | invisible curve |
| 11 | Fleet Slipspace Jump (rupture) | `RuptureEffect.kt:105` `FLAME_ASPECT` 2 | `plasma_vertical.particle` :: aspect ratio (Curve, unclamped, range 1.5..3) | 2 constant | 1.5 → 3 (range input 0) … 1.5 → 2.53 (range input 1) | invisible curve |
| 12 | Fleet Slipspace Jump (rupture) | `RuptureEffect.kt:104` `SPARK_STRETCH` 6 | `slipspace_charge_sparks.particle` :: aspect ratio (Basic, unclamped) | 6 | 15 | invisible constant |

Halo 3 Ark / ODST findings (one doc-only fix, one deliberate mirror, one open question): see the section below.

What "aspect ratio" means here: Reach stretches the particle's quad by it. The Kotlin draws these
sprites square (or with its own stretch). Which quad axis Reach stretches has to be confirmed per sprite
in a preview before any change.

## Not affected

- **TorpedoCurves.kt** — 27 / 27 curves OK; all sources clamped. The 12 torpedo suspects are aspect
  ratios of 1 (the Kotlin draws square), starting counts of 1, and items 5–7 above. Also
  `fireball_space_covenant` animation rate 0.5: the sprite-sheet animation is not built (the fireball
  is palette-animated on one frame).
- **WraithCurves** (`WraithMortarEffects.kt`) — 9 / 9 OK. The round's light uses the real intensity 5
  (`LIGHT_INTENSITY` 0.35 = 5 × `BombardLook.LIGHT_SCALE` 0.07). That light is 1 of the 31 mortar
  suspects. Another 24 sit in `firing.effect`, `firing.light`, `detonation.effect` and the firing
  `glow_soft` intensity. Those were never rebuilt: the Mortar fires with a `.wpn` muzzle flash and
  hits with the torpedo impact burst. The last 6 are aspect ratios of 1.
- **ProjectorCurves.kt** — 30 / 30 OK. `CHARGE_LIGHT` is the m70 light curve divided by its 500 peak
  (normalised by design; the source is clamped). Other projector suspects:
  - not built: `flash_soft` intensity 5, `glassing_plasma_huge` animation rate 0.25, the burst's
    palette animation;
  - `glassing_plasma_burst` aspect 0.5–0.75: the Kotlin uses 0.5 (the 1:2 bitmap), inside the range.
- **PlasmaBeamCurves.kt** — 23 / 24 OK (item 1 is the exception). Other beam suspects are aspect ratios
  of 1 and starting counts of 1, `flash_large` intensity 1 (the Kotlin uses 1), and parts not built
  (impact soft smoke, distortion ring, overheat / wheel gas).
- **ReachCurves.kt** — regenerates byte-identical; 40 / 40 scalar curves OK. The slipspace twinkle
  alpha is really 1, and the Kotlin uses 1.
- **ShieldImpactCurves.kt** — regenerates byte-identical; 0 suspects in the elite shield tags.
- **Plasma Cannon** (cheap route) — the 6 suspects are the `plasma_gas` emitter of
  `covenant_explosion_artillery_huge`. The Cannon's own `.proj` / aura copied none of them, and
  `ArtilleryBurst` (now also the Cannon's hit) already uses the real values: 10 puffs, radius 0.35,
  1–1.8 s, 8–12 WU/s. `AuraStyle.CANNON` takes only colours and the clamped lens-flare radius.
- **Plasma Repeater / Plasma Autocannon** (cheap route) — their `.proj` / `.wpn` values came from
  clamped fields (light volume, flare, cheap light, contrail, durations). The suspects are muzzle and
  `projectile.effect` emitters and aspect ratios the cheap route never used.
- **Pulse Laser** — no tags were dumped (hand-made bolt).
- **BombardCurves.kt** (control, patched pipeline) — 72 / 72 OK.

## Halo 3 Ark and ODST blink

These were read with `tool print-tag-to-xml` / `export-tag-to-xml` and summarised with `xmlsum.py`, not
with the Reach dumper. `xmlsum.py` prints every float after the 4-byte header (so min / max are the
first two numbers) and applies no range. The trap above does not happen here in the same form: the
reader saw both numbers. What the re-decode checks is whether each value was taken from the right
number.

A sonnet agent re-decoded all 942 function blobs in `ark-slipspace-exit/xml/` and
`odst-carrier-jump/odst-data/xml/` into `reach-curve-audit/h3/blobs.txt`. The decoder is
`h3/decode_blobs.py`, using the Reach layout. I re-checked two of its readings in `blobs.txt` (the
`radiation_plasma` lifespan and the Ark ring size).

- **All 32-byte blobs.** 614 are `01240000` (Basic, clamped), 190 `01200000` (Basic, unclamped), 50
  `01250000` (Basic, ranged), plus 2 Periodic and 4 Identity. The words from byte 12 hold
  `0 0 cx cy`: the Bézier handles `ArkTimeline.bezier` uses (0.1944 / 0.8111 for the "late" curve).
- **Colours.** All match the packed blob colours or the lens-flare `color` field: `TUBE_TINT` #3155ff,
  `RING_TINT` #3175ff, `FLASH_TINT` #333fff, `LENS_COLOR`, and the five `ODST_*` colours in
  `RuptureEffect.kt:203-207`.
- **Ark constants equal to the blob's min.** These match:
  - durations: rupture 1.5 / 3.75 s, flash 0.2 s, close at 3.65 s, close flash 0.15 s, close
    distortion 0.5 s;
  - tube intensity 5;
  - ring intensity 1.5 / 2;
  - flare rate 10 and flare life 0.35 s;
  - flare intensity 1;
  - lens radius 4–8 (a real-bounds field, not a blob).
- **Ark ranged values match their range use.**
  - Flare size 0.5–1 / 2–3.5: ranged, with the range variable = particle age, so a ramp over age.
  - Flare spin ±0.35 / ±0.15: ranged on particle random, so the code's random draw fits.
- **ODST doc numbers.** These match: `radiation_energy` 200/s, life 0.25–0.5, velocity 40–50, size
  25–40, tint #2141e3; `radiation_plasma` 50/s, size 30–40; `radiation_crackle` life 1.5–2;
  `radiation_flash` size 75 / 0.65 s; `distortion_bubble`. The `BLINK_*` constants are marked
  placeholders and were skipped.

Findings:

| # | Where | Value | Blob | Verdict |
| --- | --- | --- | --- | --- |
| H1 | `slipspace-drive-blink.md` (doc only; no code uses it) | `radiation_plasma` life 0.1–0.35 s | `radiation_flash.effect` lifespan, ranged 0.35 / 0.25 | MISMATCH in the doc — **fixed** (a doc defect: now 0.25–0.35 s). The 0.1 was another blob's number. |
| H2 | `ArkTimeline.late` with `ARK_LATE_MIRROR` = 1 | the 0.1944 / 0.8111 Bézier, mirrored (hold, then collapse late) | read literally, the curve changes early | Deliberate: the docs record the mirror, and the developer confirmed it in game on 2026-10-08. Reported only. |
| H3 | ODST lens flare radius 700 → 200 | — | a real-bounds pair `700,200`, not a blob | CANNOT VERIFY the direction (the code uses placeholders anyway). |

**Open question: do non-ranged Basic functions ramp?** Several Ark values are read as ramps from min to
max over particle / system age. Each is a non-ranged Basic blob whose min and max differ:

- tube scale 0.75 → 1.75 (carrier 2.75 → 1.75);
- tube spin 0.15 → −0.2 (carrier 0.5 → −0.2);
- the black point 0 → 0.25;
- ring size 0.3 / 1.25 → 0.005 (with the Bézier);
- close flash size 1 → 1.75;
- distortion scale 0.75 / 2 → 80.

The same reading underlies the ODST doc ranges: sphere glow intensity 40, outer plasma 1–3, glow_soft
1–4, `radiation_plasma` intensity 2–5, crackle scale 0.85–1.

The only real evaluator we have, Reach's ManagedBlam, evaluates a non-ranged Basic as a constant
equal to its min, with max ignored. Across the torpedo, mortar, projector, beam and Bombardment dumps,
all 755 non-ranged Basic functions with min ≠ max sample flat at min. One example:
`fireball_space_covenant` `depth_fade_range` has min 5 and max 0.25 and evaluates to 5 everywhere.
If Halo 3 does the same, those ramps are not in the tags.

On the other side:

- the designers set deliberate maxima and Bézier handles there (a ring max of 0.005, a distortion max
  of 80);
- the Ark visuals were matched to the reference footage;
- the developer passed the Ark portals in game (2026-10-10).

It cannot be settled from the XML. The Halo 3 kit's own evaluator would settle it (H3EK ManagedBlam
is untested; the ODST kit's is unusable). That needs kit access, which is the developer's call.


## Every value checked

### Curves (generated by `match_curves.py`, five-point summaries at x = 0, 0.25, 0.5, 0.75, 1)

#### TorpedoCurves — `TorpedoCurves.kt` (27 scalar curves)

| Kotlin | Tag :: field | Kotlin values | Real values | Verdict |
| --- | --- | --- | --- | --- |
| TorpedoCurves.kt:13 `BODY_THICKNESS` | projectile.light_volume_system :: light_volumes > [0] hot_core > profile_thickness (clamped, 0.4..0.5) | 0.4 0.4 0.402 0.455 0.4 | 0.4 0.4 0.402 0.455 0.4 | OK |
| TorpedoCurves.kt:19 `BODY_ALPHA` | projectile.light_volume_system :: light_volumes > [0] hot_core > profile_alpha (clamped, 0..1) | 0 0.101 0.4 0.892 0 | 0 0.101 0.4 0.892 0 | OK |
| TorpedoCurves.kt:20 `BODY_INTENSITY` | projectile.light_volume_system :: light_volumes > [0] hot_core > profile_intensity (clamped, 0.5..3) | 0.5 1.14 2.23 2.97 0.5 | 0.5 1.14 2.23 2.97 0.5 | OK |
| TorpedoCurves.kt:23 `TRAIL_SIZE` | projectile.contrail_system :: contrails > [0] contrail_0 > profile size (clamped, 1.5..3) | 1.5 1.57 1.93 2.46 3 | 1.5 1.57 1.93 2.46 3 | OK |
| TorpedoCurves.kt:29 `TRAIL_ALPHA` | projectile.contrail_system :: contrails > [0] contrail_0 > profile alpha (clamped, 0..1) | 0 0.983 0.456 0.08 0 | 0 0.981 0.456 0.08 0 | OK |
| TorpedoCurves.kt:30 `TRAIL_BLACK_POINT` | projectile.contrail_system :: contrails > [0] contrail_0 > profile black point (clamped, 0..0.85) | 0 0 0.058 0.379 0.85 | 0 0 0.0585 0.379 0.85 | OK |
| TorpedoCurves.kt:31 `TRAIL_INTENSITY` | projectile.contrail_system :: contrails > [0] contrail_0 > profile intensity (clamped, 1..3.5) | 3.5 1.02 1 1 1 | 3.5 1.03 1 1 1 | OK |
| TorpedoCurves.kt:39 `MUZZLE_ROUND_ALPHA` | firing.effect :: re > particle systems > [0] muzzle_flash_round > emitters > [0] emitter_0 > particle alpha (clamped, 0..1) | 1 1 1 0.985 0 | 1 1 1 0.984 0 | OK |
| TorpedoCurves.kt:40 `MUZZLE_ROUND_BLACK_POINT_HOT` | firing.effect :: e systems > [0] muzzle_flash_round > emitters > [0] emitter_0 > particle alpha black point (clamped, 0..1) | 0 0.069 0.266 0.581 1 | 0 0.0687 0.266 0.581 1 | OK |
| TorpedoCurves.kt:41 `MUZZLE_ROUND_BLACK_POINT_DIM` | firing.effect :: e systems > [0] muzzle_flash_round > emitters > [0] emitter_0 > particle alpha black point (clamped, 0..1, r1) | 0 0.042 0.15 0.297 0.456 | 0 0.0422 0.15 0.297 0.456 | OK |
| TorpedoCurves.kt:44 `MUZZLE_FLASH_ALPHA` | firing.effect ::  [0] fire > particle systems > [1] flash_large > emitters > [0] emitter_0 > particle alpha (clamped, 0..1) | 1 1 0.956 0.67 0 | 1 1 0.956 0.669 0 | OK |
| TorpedoCurves.kt:47 `FIREBALL_SCALE_A` | impact.effect :: particle systems > [0] fireball_space_covenant > emitters > [0] emitter_0 > particle scale (clamped, 0.8..1.6) | 0.8 1.25 1.49 1.58 1.6 | 0.8 1.25 1.49 1.58 1.6 | OK |
| TorpedoCurves.kt:48 `FIREBALL_SCALE_B` | impact.effect :: particle systems > [0] fireball_space_covenant > emitters > [0] emitter_0 > particle scale (clamped, 0.8..1.6, r1) | 0.8 1.09 1.27 1.35 1.37 | 0.8 1.09 1.27 1.35 1.37 | OK |
| TorpedoCurves.kt:54 `FIREBALL_ALPHA` | impact.effect :: particle systems > [0] fireball_space_covenant > emitters > [0] emitter_0 > particle alpha (clamped, 0..1) | 1 0.978 0.4 0.058 0 | 1 0.976 0.401 0.0579 0 | OK |
| TorpedoCurves.kt:55 `FIREBALL_BLACK_POINT` | impact.effect :: tems > [0] fireball_space_covenant > emitters > [0] emitter_0 > particle alpha black point (clamped, 0..1) | 0 0.032 0.666 0.939 1 | 0 0.0342 0.664 0.939 1 | OK |
| TorpedoCurves.kt:56 `FIREBALL_INTENSITY` | fireball_space_covenant.particle :: intensity (clamped, 10..25) | 25 14.4 11.5 10.3 10 | 25 14.4 11.6 10.3 10 | OK |
| TorpedoCurves.kt:57 `FIREBALL_PALETTE_A` | fireball_space_covenant.particle :: palette animation (clamped, 0..1) | 0 0.396 0.713 0.924 1 | 0 0.396 0.713 0.924 1 | OK |
| TorpedoCurves.kt:58 `FIREBALL_PALETTE_B` | fireball_space_covenant.particle :: palette animation (clamped, 0..1, r1) | 0 0.115 0.358 0.672 1 | 0 0.115 0.358 0.672 1 | OK |
| TorpedoCurves.kt:61 `PLASMA_SCALE` | impact.effect :: particle systems > [1] plasma_blue_space_large > emitters > [0] emitter_0 > particle scale (clamped, 0.5..1.25) | 0.5 0.852 1.08 1.21 1.25 | 0.5 0.851 1.08 1.21 1.25 | OK |
| TorpedoCurves.kt:67 `PLASMA_ALPHA` | impact.effect :: particle systems > [1] plasma_blue_space_large > emitters > [0] emitter_0 > particle alpha (clamped, 0..1) | 1 1 0.773 0.16 0 | 1 1 0.772 0.161 0 | OK |
| TorpedoCurves.kt:68 `PLASMA_BLACK_POINT` | impact.effect :: tems > [1] plasma_blue_space_large > emitters > [0] emitter_0 > particle alpha black point (clamped, 0..1) | 0 0 0.138 0.672 1 | 0 0 0.139 0.672 1 | OK |
| TorpedoCurves.kt:71 `SMOKE_SCALE` | impact.effect :: losion > particle systems > [2] fireball_smoke > emitters > [0] emitter_0 > particle scale (clamped, 1..2.5) | 1 1.7 2.17 2.42 2.5 | 1 1.7 2.17 2.42 2.5 | OK |
| TorpedoCurves.kt:72 `SMOKE_ALPHA` | impact.effect :: losion > particle systems > [2] fireball_smoke > emitters > [0] emitter_0 > particle alpha (clamped, 0..1) | 1 1 0.998 0.482 0 | 1 1 0.996 0.482 0 | OK |
| TorpedoCurves.kt:73 `SMOKE_BLACK_POINT` | impact.effect :: ticle systems > [2] fireball_smoke > emitters > [0] emitter_0 > particle alpha black point (clamped, 0..0.4) | 0 0 0.057 0.27 0.4 | 0 0 0.0569 0.27 0.4 | OK |
| TorpedoCurves.kt:76 `FLARE_RADIUS` | impact.lens_flare :: reflections > [0] 0. reflection > radius curve (clamped, 5..25) | 10.9 16.7 21.8 24.8 5 | 10.9 16.7 21.8 24.8 5 | OK |
| TorpedoCurves.kt:77 `FLARE_BRIGHTNESS` | impact.lens_flare :: reflections > [0] 0. reflection > brightness curve (clamped, 0..7) | 0 0.141 0.551 1.21 7 | 0 0.142 0.552 1.21 7 | OK |
| TorpedoCurves.kt:83 `ANAMORPHIC_BRIGHTNESS` | impact_anamorphic.lens_flare :: reflections > [0] 0. reflection > brightness curve (clamped, 0..0.25) | 0 0.006 0.023 0.053 0.25 | 0 0.00566 0.0231 0.0532 0.25 | OK |

#### WraithCurves — `WraithMortarEffects.kt` (9 scalar curves)

| Kotlin | Tag :: field | Kotlin values | Real values | Verdict |
| --- | --- | --- | --- | --- |
| WraithMortarEffects.kt:25 `BODY_THICKNESS` | projectile.light_volume_system :: light_volumes > [0] light_volume_0 > profile_thickness (clamped, 0.5..0.6) | 0.5 0.541 0.572 0.594 0.6 | 0.5 0.542 0.573 0.593 0.6 | OK |
| WraithMortarEffects.kt:26 `BODY_INTENSITY` | projectile.light_volume_system :: light_volumes > [0] light_volume_0 > profile_intensity (clamped, 1..2) | 1 1.01 1.1 1.42 2 | 1 1.01 1.12 1.41 2 | OK |
| WraithMortarEffects.kt:34 `TRAIL_ALPHA` | projectile.contrail_system :: contrails > [0] contrail_0 > profile alpha (clamped, 0..1) | 0 0.999 0.976 0.392 0 | 0 0.999 0.959 0.411 0 | OK |
| WraithMortarEffects.kt:42 `PUFF_ALPHA` | plasma_trail.particle :: alpha (clamped, 0..1) | 0 1 1 0.809 0 | 0 1 1 0.831 0 | OK |
| WraithMortarEffects.kt:43 `PUFF_SCALE` | projectile.effect ::  event_0 > particle systems > [0] plasma_trail > emitters > [0] emitter_0 > particle scale (clamped, 0.5..1) | 0.5 0.672 0.819 0.941 1 | 0.5 0.677 0.827 0.938 1 | OK |
| WraithMortarEffects.kt:44 `PUFF_BLACK_POINT_HOT` | projectile.effect :: article systems > [0] plasma_trail > emitters > [0] emitter_0 > particle alpha black point (clamped, 0..1) | 0 0.007 0.095 0.413 1 | 0 0.00822 0.105 0.4 1 | OK |
| WraithMortarEffects.kt:45 `PUFF_BLACK_POINT_DIM` | projectile.effect :: article systems > [0] plasma_trail > emitters > [0] emitter_0 > particle alpha black point (clamped, 0..1, r1) | 0 0 0 0.061 0.34 | 0 0 0 0.0557 0.34 | OK |
| WraithMortarEffects.kt:48 `PLASMA_ALPHA` | plasma_blue_medium.particle :: alpha (clamped, 0..1) | 0 0.952 1 0.969 0 | 0 0.96 1 0.975 0 | OK |
| WraithMortarEffects.kt:49 `PLASMA_SCALE` | projectile.effect :: _0 > particle systems > [2] plasma_blue_medium > emitters > [0] emitter_0 > particle scale (clamped, 0.75..1) | 0.75 0.892 0.937 0.973 1 | 0.75 0.894 0.939 0.972 1 | OK |

#### ProjectorCurves — `ProjectorCurves.kt` (30 scalar curves)

| Kotlin | Tag :: field | Kotlin values | Real values | Verdict |
| --- | --- | --- | --- | --- |
| ProjectorCurves.kt:16 `EMP_SIZE` | glassing_ship.effect :: ents > [0] charge > particle systems > [0] emp_charge > emitters > [0] emp > particle size (clamped, 0.5..7) | 0.5 1.23 3.02 6.33 7 | 0.5 1.27 3.17 6.22 7 | OK |
| ProjectorCurves.kt:17 `EMP_SCALE` | glassing_init.effect :: nts > [0] charge > particle systems > [0] emp_charge > emitters > [0] emp > particle scale (clamped, 1..7) | 1 4.09 5.9 6.82 7 | 1 4.16 5.97 6.8 7 | OK |
| ProjectorCurves.kt:18 `EMP_ALPHA` | emp_charge.particle :: alpha (clamped, 0..1) | 1 1 1 0.559 0 | 1 1 1 0.584 0 | OK |
| ProjectorCurves.kt:21 `FLASH_SIZE` | glassing_init.effect :: > [0] charge > particle systems > [1] explosive_flash > emitters > [0] emp > particle size (clamped, 0..4) | 0 0.418 1.45 3.39 4 | 0 0.442 1.54 3.33 4 | OK |
| ProjectorCurves.kt:22 `FLASH_INTENSITY` | explosive_flash.particle :: intensity (clamped, 0..15) | 15 15 14.3 9.69 0 | 15 15 14.2 9.89 0 | OK |
| ProjectorCurves.kt:23 `FLASH_ALPHA` | glassing_init.effect ::  [0] charge > particle systems > [1] explosive_flash > emitters > [0] emp > particle alpha (clamped, 0..1) | 1 1 1 0.489 0 | 1 1 1 0.512 0 | OK |
| ProjectorCurves.kt:26 `CHARGE_FLARE_BRIGHTNESS` | glassing_charge.lens_flare :: age brightness > [0] 0. lens_flare_scalar_animation_block > function (clamped, 0..1) | 0 0.925 1 0.48 0 | 0 0.941 0.999 0.503 0 | OK |
| ProjectorCurves.kt:29 `CHARGE_LIGHT` | m70 glassing_charge.light :: intensity (clamped, 0..500) | 0 0.115 0.431 0.815 1 | 0 61.6 227 403 500 (/500: 0 0.123 0.454 0.805 1) | OK (normalised /500 by design; brightness set in code) |
| ProjectorCurves.kt:32 `FLICKER_SCALE` | glassing_ship.effect :: low > particle systems > [0] glassing_flicker_glow > emitters > [0] flash > particle scale (clamped, 0.25..1) | 0.25 0.773 0.99 1 1 | 0.25 0.784 0.995 1 1 | OK |
| ProjectorCurves.kt:38 `FLICKER_ALPHA` | glassing_ship.effect :: low > particle systems > [0] glassing_flicker_glow > emitters > [0] flash > particle alpha (clamped, 0..0.15) | 0.15 0.15 0.15 0.0969 0 | 0.15 0.15 0.15 0.101 0 | OK |
| ProjectorCurves.kt:41 `FAR_FLARE_BRIGHTNESS` | glassing_glow.lens_flare :: reflections > [0] 0. reflection > brightness curve (clamped, 0..50) | 0 4.03 14.7 32.1 50 | 0 4.29 15.6 31.6 50 | OK |
| ProjectorCurves.kt:42 `FAR_FLARE_AGE` | glassing_glow.lens_flare :: age brightness > [0] 0. lens_flare_scalar_animation_block > function (clamped, 0..0.3) | 0.3 0.3 0.3 0.287 0 | 0.3 0.3 0.3 0.29 0 | OK |
| ProjectorCurves.kt:45 `BUBBLE_SCALE` | glassing_ship.effect :: > particle systems > [1] glassing_bubble > emitters > [0] glassing_bubble > particle scale (clamped, 0.75..3.85) | 0.75 2.02 3.09 3.71 3.85 | 0.75 2.05 3.14 3.7 3.85 | OK |
| ProjectorCurves.kt:51 `BUBBLE_ALPHA` | glassing_ship.effect :: > particle systems > [1] glassing_bubble > emitters > [0] glassing_bubble > particle alpha (clamped, 0..0.7) | 0.7 0.7 0.563 0.0814 0 | 0.7 0.7 0.52 0.0862 0 | OK |
| ProjectorCurves.kt:52 `BUBBLE_BLACK_POINT` | glassing_ship.effect :: ystems > [1] glassing_bubble > emitters > [0] glassing_bubble > particle alpha black point (clamped, 0..1) | 0 0 0.096 0.871 1 | 0 0 0.163 0.859 1 | OK |
| ProjectorCurves.kt:55 `HALF_GLOW_SCALE` | glassing_ship.effect :: beam_impact > particle systems > [2] glow_soft_half > emitters > [0] glow > particle scale (clamped, 1..1.3) | 1 1.12 1.21 1.28 1.3 | 1 1.12 1.22 1.28 1.3 | OK |
| ProjectorCurves.kt:61 `HALF_GLOW_ALPHA` | glassing_ship.effect :: beam_impact > particle systems > [2] glow_soft_half > emitters > [0] glow > particle alpha (clamped, 0..1) | 1 0.55 0.205 0.0341 0 | 1 0.535 0.191 0.0368 0 | OK |
| ProjectorCurves.kt:64 `BURST_RADIUS` | glassing_ship.effect ::  particle systems > [0] glassing_plasma_burst > emitters > [0] emitter_0 > emission radius (clamped, 50..100) | 50 62.1 74.2 87.9 100 | 50 62.5 75 87.5 100 | OK |
| ProjectorCurves.kt:65 `BURST_SIZE` | glassing_ship.effect ::  > particle systems > [0] glassing_plasma_burst > emitters > [0] emitter_0 > particle size (clamped, 60..20) | 20 21.9 28.3 41.7 60 | 20 22.1 28.9 41.2 60 | OK |
| ProjectorCurves.kt:66 `BURST_SCALE` | glassing_ship.effect :: > particle systems > [0] glassing_plasma_burst > emitters > [0] emitter_0 > particle scale (clamped, 0.8..2.5) | 0.8 1.48 2 2.38 2.5 | 0.8 1.5 2.03 2.38 2.5 | OK |
| ProjectorCurves.kt:67 `BURST_ALPHA` | glassing_ship.effect :: > particle systems > [0] glassing_plasma_burst > emitters > [0] emitter_0 > particle alpha (clamped, 0..1) | 0 1 1 0.605 0 | 0 1 1 0.63 0 | OK |
| ProjectorCurves.kt:68 `BURST_BLACK_POINT` | glassing_ship.effect :: ystems > [0] glassing_plasma_burst > emitters > [0] emitter_0 > particle alpha black point (clamped, 0..0.6) | 0 0 0 0.142 0.6 | 0 0 0 0.131 0.6 | OK |
| ProjectorCurves.kt:71 `CLOUD_RADIUS` | glassing_ship.effect :: stems > [0] glassing_plasma_huge > emitters > [0] expanding_debris_cloud > emission radius (clamped, 50..400) | 50 180 289 373 400 | 50 184 295 372 400 | OK |
| ProjectorCurves.kt:72 `CLOUD_RATE` | glassing_ship.effect ::  [0] glassing_plasma_huge > emitters > [0] expanding_debris_cloud > particle emission rate (clamped, 8..25) | 8 12.1 16.2 20.9 25 | 8 12.2 16.5 20.8 25 | OK |
| ProjectorCurves.kt:73 `CLOUD_SCALE` | glassing_ship.effect :: ystems > [0] glassing_plasma_huge > emitters > [0] expanding_debris_cloud > particle scale (clamped, 1..3) | 1 1.86 2.48 2.89 3 | 1 1.89 2.51 2.88 3 | OK |
| ProjectorCurves.kt:74 `CLOUD_ALPHA` | glassing_plasma_huge.particle :: alpha (clamped, 0..1) | 0 1 0.931 0.344 0 | 0 1 0.908 0.362 0 | OK |
| ProjectorCurves.kt:75 `CLOUD_BLACK_POINT` | glassing_ship.effect ::  glassing_plasma_huge > emitters > [0] expanding_debris_cloud > particle alpha black point (clamped, 0..1) | 0 0 0.0025 0.364 1 | 0 0 0.00805 0.348 1 | OK |
| ProjectorCurves.kt:78 `BLUE_RATE` | glassing_ship.effect :: ms > [1] plasma_blue_huge > emitters > [0] expanding_debris_cloud > particle emission rate (clamped, 10..35) | 10 16.1 22.1 28.9 35 | 10 16.2 22.5 28.8 35 | OK |
| ProjectorCurves.kt:79 `BLUE_SCALE` | glassing_ship.effect :: le systems > [1] plasma_blue_huge > emitters > [0] expanding_debris_cloud > particle scale (clamped, 2.5..3) | 2.5 2.72 2.87 2.97 3 | 2.5 2.72 2.88 2.97 3 | OK |
| ProjectorCurves.kt:80 `BLUE_BLACK_POINT` | glassing_ship.effect ::  [1] plasma_blue_huge > emitters > [0] expanding_debris_cloud > particle alpha black point (clamped, 0..1) | 1 0.0002 0.615 0.888 1 | 1 0.00668 0.635 0.882 1 | OK |

#### PlasmaBeamCurves — `PlasmaBeamCurves.kt` (24 scalar curves)

| Kotlin | Tag :: field | Kotlin values | Real values | Verdict |
| --- | --- | --- | --- | --- |
| PlasmaBeamCurves.kt:19 `CORE_INTENSITY` | projectile_3p.beam_system :: beams > [0] center > profile_intensity (clamped, 1..4) | 1 1 1 1 4 | 1 1 1 1 4 | OK |
| PlasmaBeamCurves.kt:22 `PULSE_THICKNESS` | projectile_3p.beam_system :: beams > [3] pulse > profile_thickness (clamped, 0.0015..0.25) | 0.048 0.131 0.193 0.237 0.25 | 0.048 0.133 0.196 0.236 0.25 | OK |
| PlasmaBeamCurves.kt:28 `PULSE_INTENSITY` | projectile_1p.beam_system :: beams > [1] pulse > profile_intensity (clamped, 0..2) | 2 2 2 1.44 0 | 2 2 2 1.47 0 | OK |
| PlasmaBeamCurves.kt:29 `PULSE_BLACK_POINT` | projectile_1p.beam_system :: beams > [1] pulse > profile_black_point (clamped, 0..1) | 0 0 0 0.53 1 | 0 0 0.00235 0.507 1 | OK |
| PlasmaBeamCurves.kt:32 `LIGHT_VOLUME_THICKNESS` | firing_3p.light_volume_system :: light_volumes > [1] inner_glow > profile_thickness (clamped, 0.05..0.15) | 0.15 0.101 0.071 0.054 0.05 | 0.15 0.1 0.0697 0.0543 0.05 | OK |
| PlasmaBeamCurves.kt:33 `LIGHT_VOLUME_INTENSITY` | firing_3p.light_volume_system :: light_volumes > [1] inner_glow > profile_intensity (clamped, 1..2) | 2 1.51 1.21 1.04 1 | 2 1.5 1.2 1.04 1 | OK |
| PlasmaBeamCurves.kt:36 `FLASH_SCALE` | firing.effect :: st_person > particle systems > [0] flash_large > emitters > [0] emitter_0 > particle scale (clamped, 0.5..1) | 0.5 0.621 0.742 0.879 1 | 0.5 0.625 0.75 0.875 1 | OK |
| PlasmaBeamCurves.kt:37 `FLASH_ALPHA` | firing.effect :: st_person > particle systems > [0] flash_large > emitters > [0] emitter_0 > particle alpha (clamped, 0..0.5) | 0.5 0.432 0.277 0.0917 0 | 0.5 0.428 0.266 0.0962 0 | OK |
| PlasmaBeamCurves.kt:45 `EMBER_ALPHA` | firing.effect :: st_person > particle systems > [2] spark_ember > emitters > [0] emitter_0 > particle alpha (clamped, 0..1) | 1 0.527 0.222 0.0437 0 | 1 0.515 0.208 0.0468 0 | OK |
| PlasmaBeamCurves.kt:48 `MUZZLE_PLASMA_SCALE` | firing.effect :: n > particle systems > [3] muzzle_flash_plasma > emitters > [0] emitter_0 > particle scale (clamped, 0.25..1) | 0.25 0.362 0.561 0.815 1 | 0.25 0.368 0.575 0.808 1 | OK |
| PlasmaBeamCurves.kt:49 `MUZZLE_PLASMA_BLACK_POINT_A` | firing.effect ::  systems > [3] muzzle_flash_plasma > emitters > [0] emitter_0 > particle alpha black point (clamped, 0..1) | 0 0.116 0.392 0.754 1 | 0 0.123 0.412 0.744 1 | OK |
| PlasmaBeamCurves.kt:50 `MUZZLE_PLASMA_BLACK_POINT_B` | firing.effect ::  systems > [3] muzzle_flash_plasma > emitters > [0] emitter_0 > particle alpha black point (clamped, 0..1, r1) | 0 0.0507 0.175 0.349 0.489 | 0 0.0538 0.184 0.344 0.489 | OK |
| PlasmaBeamCurves.kt:57 `PLASMA_ALPHA` | impact_plasma.particle :: alpha (clamped, 0..1) | 1 1 1 0.809 0 | 1 1 1 0.831 0 | OK |
| PlasmaBeamCurves.kt:60 `IMPACT_PLASMA_SCALE` | impact.effect ::  impact > particle systems > [0] impact_plasma > emitters > [0] emitter_0 > particle scale (clamped, 0.5..1) | 0.5 0.909 1 1 1 | 0.5 0.916 1 1 1 | OK |
| PlasmaBeamCurves.kt:61 `IMPACT_PLASMA_ASPECT` | impact_plasma.particle :: aspect ratio (unclamped, 1..2) | 0 0.243 0.595 0.909 1 | 1 1.2 1.54 1.86 2 | MISMATCH |
| PlasmaBeamCurves.kt:62 `IMPACT_PLASMA_BLACK_POINT_A` | impact.effect :: rticle systems > [0] impact_plasma > emitters > [0] emitter_0 > particle alpha black point (clamped, 0..1) | 0 0.0667 0.257 0.599 1 | 0 0.071 0.272 0.588 1 | OK |
| PlasmaBeamCurves.kt:63 `IMPACT_PLASMA_BLACK_POINT_B` | impact.effect :: rticle systems > [0] impact_plasma > emitters > [0] emitter_0 > particle alpha black point (clamped, 0..1, r1) | 0 0.0259 0.107 0.272 0.489 | 0 0.0276 0.114 0.266 0.489 | OK |
| PlasmaBeamCurves.kt:71 `IMPACT_FLASH_INTENSITY` | impact_flash.particle :: intensity (clamped, 1..5) | 5 2.84 1.64 1.09 1 | 5 2.79 1.6 1.1 1 | OK |
| PlasmaBeamCurves.kt:72 `IMPACT_FLASH_ALPHA` | impact.effect :: ] impact > particle systems > [2] impact_flash > emitters > [0] emitter_0 > particle alpha (clamped, 0..1) | 1 0.966 0.832 0.503 0 | 1 0.963 0.819 0.515 0 | OK |
| PlasmaBeamCurves.kt:75 `SPARK_LONG_ASPECT` | spark_long.particle :: aspect ratio (clamped, 1..10) | 1 8.66 6.21 3.45 1 | 1 8.58 6.06 3.53 1 | OK |
| PlasmaBeamCurves.kt:76 `SPARK_LONG_SCALE` | impact.effect :: [0] impact > particle systems > [3] spark_long > emitters > [0] emitter_0 > particle scale (clamped, 0..1) | 0 0.591 0.999 0.898 0.494 | 0 0.61 0.998 0.905 0.494 | OK |
| PlasmaBeamCurves.kt:77 `SPARK_LONG_INTENSITY` | spark_long.particle :: intensity (clamped, 1..5) | 5 3.4 2.25 1.42 1 | 5 3.35 2.19 1.43 1 | OK |
| PlasmaBeamCurves.kt:78 `SPARK_LONG_ALPHA` | impact.effect :: [0] impact > particle systems > [3] spark_long > emitters > [0] emitter_0 > particle alpha (clamped, 0..1) | 1 1 1 0.572 0 | 1 1 1 0.596 0 | OK |
| PlasmaBeamCurves.kt:81 `IMPACT_LIGHT` | impact.cheap_light :: intensity (clamped, 10..0) | 10 3.46 1.95 0.799 0 | 10 3.4 1.88 0.827 0 | OK |

#### ReachCurves — `ReachCurves.kt` (40 scalar curves)

| Kotlin | Tag :: field | Kotlin values | Real values | Verdict |
| --- | --- | --- | --- | --- |
| ReachCurves.kt:11 `CHARGE_RATE` | slipspace_secondary.effect ::  particle systems > [0] slipspace_init > emitters > [0] emitter_0 > particle emission rate (clamped, 0..20) | 0 10.9 4.32 6.97 20 | 0 10.9 4.32 6.97 20 | OK |
| ReachCurves.kt:14 `CHARGE_SIZE_R0` | slipspace_secondary.effect :: [0] emp > particle systems > [0] slipspace_init > emitters > [0] emitter_0 > particle size (clamped, 30..80) | 39.9 46.6 60.3 73.9 80 | 39.9 46.6 60.3 73.9 80 | OK |
| ReachCurves.kt:17 `CHARGE_SIZE_R1` | slipspace_secondary.effect :: [0] emp > particle systems > [0] slipspace_init > emitters > [0] emitter_0 > particle size (clamped, 30..80, r1) | 30 35.6 47.7 59.8 64.9 | 30 35.6 47.7 59.8 64.9 | OK |
| ReachCurves.kt:20 `CHARGE_SCALE` | slipspace_secondary.effect :: 0] emp > particle systems > [0] slipspace_init > emitters > [0] emitter_0 > particle scale (clamped, 0.1..1) | 0.1 0.435 0.723 0.924 1 | 0.1 0.435 0.723 0.924 1 | OK |
| ReachCurves.kt:23 `CHARGE_BLACK_POINT` | slipspace_secondary.effect :: ticle systems > [0] slipspace_init > emitters > [0] emitter_0 > particle alpha black point (clamped, 0..1) | 0 0 0.0263 0.556 1 | 0 0 0.0263 0.556 1 | OK |
| ReachCurves.kt:26 `CHARGE_ALPHA` | slipspace_init.particle :: alpha (clamped, 0..1) | 1 0.844 0.5 0.156 0 | 1 0.844 0.5 0.156 0 | OK |
| ReachCurves.kt:29 `DISTORTION_RATE` | slipspace_secondary.effect :: particle systems > [0] distortion_ring > emitters > [0] emitter_0 > particle emission rate (clamped, 5..10) | 5 5.28 5.82 7.2 10 | 5 5.28 5.82 7.2 10 | OK |
| ReachCurves.kt:32 `DISTORTION_SIZE_R0` | slipspace_secondary.effect :: ortion > particle systems > [0] distortion_ring > emitters > [0] emitter_0 > particle size (clamped, 50..400) | 119 160 220 300 400 | 119 160 220 300 400 | OK |
| ReachCurves.kt:35 `DISTORTION_SIZE_R1` | slipspace_secondary.effect :: ortion > particle systems > [0] distortion_ring > emitters > [0] emitter_0 > particle size (clamped, 50..400, r1) | 50 80.6 131 203 294 | 50 80.6 131 203 294 | OK |
| ReachCurves.kt:38 `DISTORTION_SCALE` | slipspace_secondary.effect :: rtion > particle systems > [0] distortion_ring > emitters > [0] emitter_0 > particle scale (clamped, 0.1..1.25) | 0.1 0.529 0.896 1.15 1.25 | 0.1 0.529 0.896 1.15 1.25 | OK |
| ReachCurves.kt:41 `DISTORTION_ALPHA` | slipspace_secondary.effect :: rtion > particle systems > [0] distortion_ring > emitters > [0] emitter_0 > particle alpha (clamped, 0..0.5) | 0 0.499 0.36 0.125 0 | 0 0.499 0.36 0.125 0 | OK |
| ReachCurves.kt:44 `ARM_FLARE_RADIUS` | slip_space_secondary.lens_flare :: reflections > [0] 0. reflection > radius curve (clamped, 40..1800) | 40 153 117 66.1 40 | 40 153 117 66 40 | OK |
| ReachCurves.kt:47 `ARM_FLARE_BRIGHTNESS` | slip_space_secondary.lens_flare :: reflections > [0] 0. reflection > brightness curve (clamped, 0..1.5) | 0 1.5 1.5 1.5 1.5 | 0 1.5 1.5 1.5 1.5 | OK |
| ReachCurves.kt:50 `STAR_FLARE_RADIUS` | slip_space_secondary_star.lens_flare :: reflections > [0] 0. reflection > radius curve (clamped, 70..100) | 100 93.7 82.4 73.6 70 | 100 93.7 82.4 73.6 70 | OK |
| ReachCurves.kt:53 `CHARGE_LIGHT_INTENSITY` | slipspace_secondary.light :: intensity (clamped, 50..500) | 50 118 268 422 500 | 50 118 268 422 500 | OK |
| ReachCurves.kt:56 `GLOW_SIZE` | slipspace_event.effect ::  > particle systems > [0] slipspace_sphere_glow > emitters > [0] emitter_0 > particle size (clamped, 110..140) | 125 126 137 118 125 | 125 126 137 118 125 | OK |
| ReachCurves.kt:59 `GLOW_SCALE` | slipspace_event.effect :: > particle systems > [0] slipspace_sphere_glow > emitters > [0] emitter_0 > particle scale (clamped, 0..10) | 0 9.89 9.99 8.14 0 | 0 9.89 9.99 8.14 0 | OK |
| ReachCurves.kt:62 `BLACK_SCALE` | slipspace_event.effect :: rticle systems > [1] slipspace_sphere_multiply > emitters > [0] emitter_0 > particle scale (clamped, 0..10) | 0 9.89 10 9.14 0 | 0 9.89 10 9.14 0 | OK |
| ReachCurves.kt:65 `BLACK_ALPHA` | slipspace_event.effect :: rticle systems > [1] slipspace_sphere_multiply > emitters > [0] emitter_0 > particle alpha (clamped, 0..1) | 0 0.997 1 1 0 | 0 0.997 1 1 0 | OK |
| ReachCurves.kt:68 `WEB_SCALE` | slipspace_event.effect :: particle systems > [2] slipspace_sphere_plasma > emitters > [0] emitter_0 > particle scale (clamped, 0..10) | 0 9.88 10 7.97 0 | 0 9.88 10 7.97 0 | OK |
| ReachCurves.kt:74 `GLOW_INTENSITY` | slipspace_sphere_glow.particle :: intensity (clamped, 5..10) | 10 5 5 5 10 | 10 5 5 5 10 | OK |
| ReachCurves.kt:77 `RUPTURE_LIGHT_INTENSITY` | slipspace_rupture.light :: intensity (clamped, 0..3000) | 0 3e+03 3e+03 3e+03 0.0458 | 0 3e+03 3e+03 3e+03 0 | OK |
| ReachCurves.kt:80 `SPARK_SIZE` | slipspace_event.effect ::  particle systems > [0] slipspace_charge_sparks > emitters > [0] emitter_0 > particle size (clamped, 0..15) | 0 1.5 6.3 13.2 0 | 0 1.5 6.3 13.2 0 | OK |
| ReachCurves.kt:83 `SPARK_ALPHA` | slipspace_charge_sparks.particle :: alpha (clamped, 0..1) | 1 0.703 0.375 0.109 0 | 1 0.703 0.375 0.109 0 | OK |
| ReachCurves.kt:86 `BLAST_SCALE` | slipspace_event.effect :: se_burst > particle systems > [0] blast_radius > emitters > [0] emitter_0 > particle scale (clamped, 1..35) | 1 8.49 16.3 25 35 | 1 8.49 16.3 25 35 | OK |
| ReachCurves.kt:89 `BLAST_ALPHA` | slipspace_event.effect :: se_burst > particle systems > [0] blast_radius > emitters > [0] emitter_0 > particle alpha (clamped, 0.2..0.5) | 0.5 0.411 0.313 0.233 0.2 | 0.5 0.411 0.313 0.233 0.2 | OK |
| ReachCurves.kt:92 `PLASMA_VERTICAL_SCALE` | slipspace_event.effect :: burst > particle systems > [1] plasma_vertical > emitters > [0] emitter_0 > particle scale (clamped, 1..35) | 1 12.5 21.8 29.2 35 | 1 12.5 21.8 29.2 35 | OK |
| ReachCurves.kt:95 `PLASMA_VERTICAL_ALPHA` | plasma_vertical.particle :: alpha (clamped, 0..1) | 1 1 1 0.988 0 | 1 1 1 0.988 0 | OK |
| ReachCurves.kt:98 `PLASMA_SHELL_SCALE` | slipspace_event.effect :: e_burst > particle systems > [2] plasma_sphere > emitters > [0] emitter_0 > particle scale (clamped, 1..35) | 1 8.17 16.1 25 35 | 1 8.17 16.1 25 35 | OK |
| ReachCurves.kt:101 `BURST_PLASMA_SCALE` | slipspace_event.effect ::  particle systems > [3] slipspace_burst_plasma > emitters > [0] emitter_0 > particle scale (clamped, 1..50) | 1 27.8 42.5 48.7 50 | 1 27.8 42.5 48.7 50 | OK |
| ReachCurves.kt:104 `BURST_PLASMA_BLACK_POINT` | slipspace_event.effect :: stems > [3] slipspace_burst_plasma > emitters > [0] emitter_0 > particle alpha black point (clamped, 0..0.001) | 0 0 0.0001 0.0007 0.001 | 0 0 5.39e-05 0.000747 0.001 | OK |
| ReachCurves.kt:107 `TWINKLE_RATE` | slipspace_event.effect :: rticle systems > [4] slipspace_twinkle > emitters > [0] emitter_0 > particle emission rate (clamped, 0..600) | 600 600 0 0 0 | 600 600 0 0 0 | OK |
| ReachCurves.kt:110 `TWINKLE_SIZE` | slipspace_event.effect :: urst > particle systems > [4] slipspace_twinkle > emitters > [0] emitter_0 > particle size (clamped, 1.5..6) | 1.5 1.76 2.02 2.8 6 | 1.5 1.76 2.02 2.8 6 | OK |
| ReachCurves.kt:113 `TWINKLE_SCALE` | slipspace_event.effect :: rst > particle systems > [4] slipspace_twinkle > emitters > [0] emitter_0 > particle scale (clamped, 0..1) | 1 1 1 1 0 | 1 1 1 1 0 | OK |
| ReachCurves.kt:116 `SMOKE_SCALE` | slipspace_event.effect :: rst > particle systems > [5] smoke_fiery_large > emitters > [0] emitter_0 > particle scale (clamped, 8..30) | 8 17.9 24.7 28.7 30 | 8 17.9 24.7 28.7 30 | OK |
| ReachCurves.kt:122 `SMOKE_ALPHA` | slipspace_event.effect :: rst > particle systems > [5] smoke_fiery_large > emitters > [0] emitter_0 > particle alpha (clamped, 0..0.75) | 0.75 0.164 0.0695 0.0166 0 | 0.75 0.164 0.0695 0.0166 0 | OK |
| ReachCurves.kt:125 `HAZE_SCALE` | slipspace_event.effect :: rst > particle systems > [6] smoke_fiery_large > emitters > [0] emitter_0 > particle scale (clamped, 0..22) | 0 8.08 14.3 18.9 22 | 0 8.08 14.3 18.9 22 | OK |
| ReachCurves.kt:131 `TWINKLE_LINGER_RATE` | slipspace_event.effect :: rticle systems > [8] slipspace_twinkle > emitters > [0] emitter_0 > particle emission rate (clamped, 0..200) | 0 200 200 200 0 | 0 200 200 200 0 | OK |
| ReachCurves.kt:134 `COLLAPSE_FLARE_RADIUS` | slip_space_collapse.lens_flare :: reflections > [0] 0. reflection > radius curve (clamped, 500..1500) | 500 829 1.27e+03 1.46e+03 1.5e+03 | 500 829 1.27e+03 1.46e+03 1.5e+03 | OK |
| ReachCurves.kt:137 `COLLAPSE_FLARE_BRIGHTNESS` | slip_space_collapse.lens_flare :: reflections > [0] 0. reflection > brightness curve (clamped, 0..1.5) | 0 0.082 0.344 0.809 1.5 | 0 0.082 0.344 0.809 1.5 | OK |

#### ShieldImpactCurves — `ShieldImpactCurves.kt` (9 scalar curves)

| Kotlin | Tag :: field | Kotlin values | Real values | Verdict |
| --- | --- | --- | --- | --- |
| ShieldImpactCurves.kt:98 `EDGE_GLOW_INTENSITY` | elite_3p.shield_impact :: Edge Glow Intensity (clamped, 0.5..1) | 1 0.765 0.621 0.542 0.5 | 1 0.765 0.621 0.542 0.5 | OK |
| ShieldImpactCurves.kt:104 `PLASMA_CENTER_INTENSITY` | elite_3p.shield_impact :: Plasma Center Intensity (clamped, 1..1.5) | 1.5 1.12 1.04 1.01 1 | 1.5 1.12 1.04 1.01 1 | OK |
| ShieldImpactCurves.kt:110 `PLASMA_EDGE_INTENSITY` | elite_3p.shield_impact :: Plasma Edge Intensity (clamped, 2..5) | 5 4.68 4.06 3.16 2 | 5 4.68 4.06 3.16 2 | OK |
| ShieldImpactCurves.kt:116 `HIT_RADIUS` | elite_3p.shield_impact :: Hit Radius (clamped, 0.1..0.25) | 0.25 0.134 0.1 0.1 0.1 | 0.25 0.134 0.1 0.1 0.1 | OK |
| ShieldImpactCurves.kt:119 `EXTRUSION` | elite_3p.shield_impact :: Extrusion Amount (clamped, 0.01..0.025) | 0.025 0.0157 0.0122 0.0105 0.01 | 0.025 0.0157 0.0122 0.0105 0.01 | OK |
| ShieldImpactCurves.kt:131 `COLLAPSE_PLASMA_EDGE_INTENSITY` | elite_3p_collapse.shield_impact :: Plasma Edge Intensity (clamped, 0..10) | 10 3.79 1.42 0.316 0 | 10 3.79 1.42 0.316 0 | OK |
| ShieldImpactCurves.kt:134 `COLLAPSE_EXTRUSION` | elite_3p_collapse.shield_impact :: Extrusion Amount (clamped, 0.035..0.075) | 0.035 0.045 0.055 0.065 0.075 | 0.035 0.045 0.055 0.065 0.075 | OK |
| ShieldImpactCurves.kt:146 `RECHARGE_HIT_RADIUS` | elite_3p_recharge.shield_impact :: Hit Radius (clamped, 0.1..1) | 0.1 0.629 0.914 1 1 | 0.1 0.629 0.914 1 1 | OK |
| ShieldImpactCurves.kt:149 `RECHARGE_OSCILLATION` | elite_3p_recharge.shield_impact :: Oscillation Amplitude (clamped, 0..0.005) | 0.005 0.0037 0.0025 0.0012 0 | 0.005 0.00375 0.0025 0.00125 0 | OK |

#### BombardCurves (control) — `BombardCurves.kt` (72 scalar curves)

| Kotlin | Tag :: field | Kotlin values | Real values | Verdict |
| --- | --- | --- | --- | --- |
| BombardCurves.kt:15 `BODY_THICKNESS` | projectile.light_volume_system :: light_volumes > [0] light_volume_0 > profile_thickness (clamped, 0.25..0.35) | 0.273 0.325 0.347 0.348 0.311 | 0.272 0.325 0.347 0.348 0.311 | OK |
| BombardCurves.kt:21 `BODY_INTENSITY` | projectile.light_volume_system :: light_volumes > [0] light_volume_0 > profile_intensity (clamped, 1..6) | 1 1.39 2.53 4.51 6 | 1 1.39 2.53 4.51 6 | OK |
| BombardCurves.kt:24 `TRAIL_SIZE` | projectile.contrail_system :: contrails > [0] hunter_beam > profile size (clamped, 0.75..1.5) | 0.75 1.5 1.34 0.962 0.75 | 0.75 1.5 1.34 0.962 0.75 | OK |
| BombardCurves.kt:27 `TRAIL_ALPHA` | projectile.contrail_system :: contrails > [0] hunter_beam > profile alpha (clamped, 0..0.6) | 0 0.599 0.337 0.0964 0 | 0 0.599 0.337 0.0965 0 | OK |
| BombardCurves.kt:30 `TRAIL_BLACK_POINT` | projectile.contrail_system :: contrails > [0] hunter_beam > profile black point (clamped, 0..1) | 0 0 0.0578 0.761 1 | 0 0 0.0578 0.761 1 | OK |
| BombardCurves.kt:33 `TRAIL_INTENSITY` | projectile.contrail_system :: contrails > [0] hunter_beam > profile intensity (clamped, 0.5..2) | 0.5 1.63 0.512 0.5 0.5 | 0.5 1.63 0.512 0.5 0.5 | OK |
| BombardCurves.kt:36 `WISPY_SIZE` | projectile.contrail_system :: contrails > [1] soft_contrail > profile size (clamped, 0.5..1.5) | 0.5 1.41 0.919 0.623 0.5 | 0.5 1.41 0.919 0.623 0.5 | OK |
| BombardCurves.kt:42 `WISPY_ALPHA` | projectile.contrail_system :: contrails > [1] soft_contrail > profile alpha (clamped, 0..0.6) | 0 0.599 0.319 0.0964 0 | 0 0.599 0.319 0.0965 0 | OK |
| BombardCurves.kt:45 `WISPY_BLACK_POINT` | projectile.contrail_system :: contrails > [0] hunter_beam > profile black point (clamped, 0..1) | 0 0 0.0578 0.761 1 | 0 0 0.0578 0.761 1 | OK |
| BombardCurves.kt:48 `WISPY_INTENSITY` | projectile.contrail_system :: contrails > [1] soft_contrail > profile intensity (clamped, 0.5..6) | 6 1 0.581 0.5 0.5 | 6 1 0.581 0.5 0.5 | OK |
| BombardCurves.kt:51 `CHARGE_PLASMA_RATE` | charging.effect :: ticle systems > [0] plasma_blue_medium > emitters > [0] emitter_0 > particle emission rate (clamped, 2..60) | 2 25.7 52.2 59.7 60 | 2 25.7 52.2 59.7 60 | OK |
| BombardCurves.kt:54 `CHARGE_PLASMA_SCALE` | charging.effect :: es > particle systems > [0] plasma_blue_medium > emitters > [0] emitter_0 > particle scale (unclamped, 0.2..2.5) | 0.2 1.21 1.93 2.36 2.5 | 0.2 1.21 1.93 2.36 2.5 | OK |
| BombardCurves.kt:57 `CHARGE_PLASMA_BLACK_POINT` | charging.effect :: e systems > [0] plasma_blue_medium > emitters > [0] emitter_0 > particle alpha black point (unclamped, 0..1) | 0 0 0.206 0.712 1 | 0 0 0.206 0.712 1 | OK |
| BombardCurves.kt:60 `CHARGE_CORE_SIZE` | charging.effect :: les > particle systems > [1] core_charge_plasma > emitters > [0] emitter_0 > particle size (clamped, 0.45..1.25) | 0.45 0.569 0.832 1.24 1.25 | 0.45 0.569 0.832 1.24 1.25 | OK |
| BombardCurves.kt:63 `CHARGE_CORE_SCALE` | charging.effect :: es > particle systems > [1] core_charge_plasma > emitters > [0] emitter_0 > particle scale (clamped, 0.5..1) | 0.5 0.567 0.678 0.825 1 | 0.5 0.567 0.678 0.825 1 | OK |
| BombardCurves.kt:66 `CHARGE_BOLT_RATE` | charging.effect :: s > particle systems > [2] plasma_bolt > emitters > [0] emitter_0 > particle emission rate (clamped, 0..5) | 0 1.21 3.92 4.94 5 | 0 1.21 3.92 4.94 5 | OK |
| BombardCurves.kt:69 `CHARGE_BOLT_LIFE` | charging.effect :: ticles > particle systems > [2] plasma_bolt > emitters > [0] emitter_0 > particle lifespan (clamped, 0.25..0.75) | 0.75 0.625 0.5 0.375 0.25 | 0.75 0.625 0.5 0.375 0.25 | OK |
| BombardCurves.kt:72 `CHARGE_BOLT_SIZE` | charging.effect :: _particles > particle systems > [2] plasma_bolt > emitters > [0] emitter_0 > particle size (clamped, 0.8..1.8) | 0.8 0.8 1.14 1.6 1.8 | 0.8 0.8 1.14 1.6 1.8 | OK |
| BombardCurves.kt:75 `CHARGE_BOLT_ALPHA` | charging.effect :: particles > particle systems > [2] plasma_bolt > emitters > [0] emitter_0 > particle alpha (clamped, 0..1) | 0 0.629 1 1 1 | 0 0.629 1 1 1 | OK |
| BombardCurves.kt:78 `CHARGE_BOLT_BLACK_POINT` | charging.effect :: particle systems > [2] plasma_bolt > emitters > [0] emitter_0 > particle alpha black point (unclamped, 0..1) | 0 0 0.0086 0.338 1 | 0 0 0.0086 0.338 1 | OK |
| BombardCurves.kt:84 `PLASMA_ALPHA` | plasma_blue_medium.particle :: alpha (clamped, 0..1) | 0 0.96 1 0.975 0 | 0 0.96 1 0.975 0 | OK |
| BombardCurves.kt:90 `CORE_INTENSITY` | core_charge_plasma.particle :: intensity (clamped, 0..10) | 10 9.45 7.7 4.6 0 | 10 9.45 7.7 4.6 0 | OK |
| BombardCurves.kt:96 `BOLT_ALPHA` | plasma_blue_medium.particle :: alpha (clamped, 0..1) | 0 0.96 1 0.975 0 | 0 0.96 1 0.975 0 | OK |
| BombardCurves.kt:99 `CHARGE_GLOW_LENGTH` | core_charge.light_volume_system :: light_volumes > [0] outer_glow > length (clamped, 0.35..1) | 0.35 0.674 0.873 0.973 1 | 0.35 0.674 0.873 0.973 1 | OK |
| BombardCurves.kt:102 `CHARGE_GLOW_THICKNESS` | core_charge.light_volume_system :: light_volumes > [0] outer_glow > profile_length (clamped, 0..0.7) | 0 0.349 0.563 0.671 0.7 | 0 0.349 0.563 0.671 0.7 | OK |
| BombardCurves.kt:108 `MUZZLE_SCALE` | firing.effect ::  particle systems > [0] muzzle_flash_long_soft > emitters > [0] emitter_0 > particle scale (unclamped, 0.5..1) | 0.5 0.625 0.75 0.875 1 | 0.5 0.625 0.75 0.875 1 | OK |
| BombardCurves.kt:114 `MUZZLE_ALPHA` | firing.effect ::  particle systems > [0] muzzle_flash_long_soft > emitters > [0] emitter_0 > particle alpha (clamped, 0..1) | 1 1 1 0.781 0 | 1 1 1 0.781 0 | OK |
| BombardCurves.kt:117 `PULSE_SCALE` | charging.effect :: es > particle systems > [0] plasma_blue_medium > emitters > [0] emitter_0 > particle scale (unclamped, 0.2..2.5) | 0.2 1.21 1.93 2.36 2.5 | 0.2 1.21 1.93 2.36 2.5 | OK |
| BombardCurves.kt:120 `PULSE_BLACK_POINT` | charging.effect :: e systems > [0] plasma_blue_medium > emitters > [0] emitter_0 > particle alpha black point (unclamped, 0..1) | 0 0 0.206 0.712 1 | 0 0 0.206 0.712 1 | OK |
| BombardCurves.kt:126 `PULSE_INTENSITY` | plasma_pulse.particle :: intensity (clamped, 1..25) | 25 6.67 3.72 1.73 1 | 25 6.67 3.72 1.73 1 | OK |
| BombardCurves.kt:129 `PULSE_ALPHA` | plasma_pulse.particle :: alpha (clamped, 0..1) | 1 1 0.988 0.457 0 | 1 1 0.988 0.457 0 | OK |
| BombardCurves.kt:132 `FLASH_SCALE` | firing.effect :: [0] flash > particle systems > [2] flash_large > emitters > [0] emitter_0 > particle scale (clamped, 0.5..1) | 1 0.875 0.75 0.625 0.5 | 1 0.875 0.75 0.625 0.5 | OK |
| BombardCurves.kt:135 `FLASH_ALPHA` | firing.effect :: [0] flash > particle systems > [2] flash_large > emitters > [0] emitter_0 > particle alpha (clamped, 0..0.5) | 0.5 0.375 0.25 0.125 0 | 0.5 0.375 0.25 0.125 0 | OK |
| BombardCurves.kt:138 `GAS_SCALE` | charging.effect :: es > particle systems > [0] plasma_blue_medium > emitters > [0] emitter_0 > particle scale (unclamped, 0.2..2.5) | 0.2 1.21 1.93 2.36 2.5 | 0.2 1.21 1.93 2.36 2.5 | OK |
| BombardCurves.kt:141 `GAS_ALPHA_HOT` | firing.effect ::  [0] flash > particle systems > [4] plasma_gas > emitters > [0] emitter_0 > particle alpha (clamped, 0..1) | 1 1 0.638 0.174 0 | 1 1 0.638 0.174 0 | OK |
| BombardCurves.kt:144 `GAS_ALPHA_DIM` | firing.effect ::  [0] flash > particle systems > [4] plasma_gas > emitters > [0] emitter_0 > particle alpha (clamped, 0..1, r1) | 0.6 0.596 0.288 0.0471 0 | 0.6 0.596 0.288 0.0471 0 | OK |
| BombardCurves.kt:147 `GAS_BLACK_POINT_HOT` | firing.effect ::  particle systems > [4] plasma_gas > emitters > [0] emitter_0 > particle alpha black point (unclamped, 0..1) | 0 0.0325 0.526 0.923 1 | 0 0.0325 0.526 0.923 1 | OK |
| BombardCurves.kt:150 `GAS_BLACK_POINT_DIM` | firing.effect ::  particle systems > [4] plasma_gas > emitters > [0] emitter_0 > particle alpha black point (unclamped, 0..1, r1) | 0 0 0.0117 0.547 0.847 | 0 0 0.0117 0.547 0.847 | OK |
| BombardCurves.kt:156 `GAS_PARTICLE_ALPHA` | plasma_gas.particle :: alpha (clamped, 0..1) | 0 0.92 0.444 0.109 0 | 0 0.92 0.444 0.109 0 | OK |
| BombardCurves.kt:159 `MUZZLE_GLOW_LENGTH` | muzzle_flash.light_volume_system :: light_volumes > [0] outer_glow > length (clamped, 10..20) | 10 15.4 18.4 19.7 20 | 10 15.4 18.4 19.7 20 | OK |
| BombardCurves.kt:162 `MUZZLE_GLOW_THICKNESS_START` | muzzle_flash.light_volume_system :: light_volumes > [0] outer_glow > profile_thickness (clamped, 1.5..3) | 1.5 2.99 2.43 2.02 1.85 | 1.5 2.99 2.43 2.02 1.85 | OK |
| BombardCurves.kt:165 `MUZZLE_GLOW_THICKNESS_END` | muzzle_flash.light_volume_system :: light_volumes > [0] outer_glow > profile_thickness (clamped, 1.5..3, r1) | 1.5 2.02 1.89 1.62 1.5 | 1.5 2.02 1.89 1.62 1.5 | OK |
| BombardCurves.kt:174 `MUZZLE_GLOW_ALPHA` | muzzle_flash.light_volume_system :: light_volumes > [0] outer_glow > profile_alpha (clamped, 0..1) | 1 0.114 0.046 0.0104 0 | 1 0.114 0.046 0.0104 0 | OK |
| BombardCurves.kt:177 `MUZZLE_GLOW_INTENSITY` | muzzle_flash.light_volume_system :: light_volumes > [0] outer_glow > profile_intensity (clamped, 0..15) | 15 2.45 0.953 0.204 0 | 15 2.45 0.952 0.204 0 | OK |
| BombardCurves.kt:180 `BIG_GLOW_RADIUS` | big_glow.lens_flare :: reflections > [0] 0. reflection > radius curve (clamped, 6..18) | 6 7.88 12 16.1 18 | 6 7.88 12 16.1 18 | OK |
| BombardCurves.kt:183 `BIG_GLOW_BRIGHTNESS` | big_glow.lens_flare :: reflections > [0] 0. reflection > brightness curve (clamped, 0..2) | 0 0.118 0.539 1.36 2 | 0 0.118 0.539 1.36 2 | OK |
| BombardCurves.kt:186 `MUZZLE_ANAMORPHIC_RADIUS` | anamorphic.lens_flare :: reflections > [0] 0. reflection > radius curve (clamped, 8..4) | 8 8 8 7.98 4 | 8 8 8 7.98 4 | OK |
| BombardCurves.kt:189 `MUZZLE_ANAMORPHIC_BRIGHTNESS` | anamorphic.lens_flare :: reflections > [0] 0. reflection > brightness curve (clamped, 0..0.3) | 0 0 0 0.0262 0.3 | 0 0 0 0.0263 0.3 | OK |
| BombardCurves.kt:195 `FIRING_LIGHT_INTENSITY` | firing.light :: intensity (unclamped, 0..80) | 80 10.5 3.55 0.614 0 | 80 10.5 3.55 0.614 0 | OK |
| BombardCurves.kt:198 `BLAST_FLASH_SCALE` | covenant_explosion_artillery_huge.effect :: flash > particle systems > [0] explosive_flash > emitters > [0] emitter_0 > particle scale (clamped, 0.5..1.5) | 0.5 0.75 1 1.25 1.5 | 0.5 0.75 1 1.25 1.5 | OK |
| BombardCurves.kt:204 `BLAST_FLASH_ALPHA` | covenant_explosion_artillery_huge.effect :: flash > particle systems > [0] explosive_flash > emitters > [0] emitter_0 > particle alpha (clamped, 0..1) | 1 0.75 0.5 0.25 0 | 1 0.75 0.5 0.25 0 | OK |
| BombardCurves.kt:207 `EXPLOSIVE_FLASH_INTENSITY` | explosive_flash.particle :: intensity (clamped, 0..15) | 15 15 14.2 9.89 0 | 15 15 14.2 9.89 0 | OK |
| BombardCurves.kt:210 `FIREBALL_RADIUS` | covenant_explosion_artillery_huge.effect :: ystems > [0] fireball_covenant_artillery_huge > emitters > [0] emitter_0 > emission radius (clamped, 0.25..0.6) | 0.25 0.337 0.425 0.513 0.6 | 0.25 0.337 0.425 0.513 0.6 | OK |
| BombardCurves.kt:213 `FIREBALL_SPEED` | covenant_explosion_artillery_huge.effect :: 0] fireball_covenant_artillery_huge > emitters > [0] emitter_0 > particle initial velocity (clamped, 4..8) | 8 7 6 5 4 | 8 7 6 5 4 | OK |
| BombardCurves.kt:216 `FIREBALL_SCALE` | covenant_explosion_artillery_huge.effect :: systems > [0] fireball_covenant_artillery_huge > emitters > [0] emitter_0 > particle scale (clamped, 0.45..1.8) | 0.45 1.01 1.43 1.71 1.8 | 0.45 1.01 1.43 1.7 1.8 | OK |
| BombardCurves.kt:219 `FIREBALL_ALPHA` | covenant_explosion_artillery_huge.effect :: systems > [0] fireball_covenant_artillery_huge > emitters > [0] emitter_0 > particle alpha (clamped, 0..0.8) | 0.8 0.4 0.165 0.048 0 | 0.8 0.4 0.165 0.048 0 | OK |
| BombardCurves.kt:222 `FIREBALL_BLACK_POINT` | covenant_explosion_artillery_huge.effect :: ] fireball_covenant_artillery_huge > emitters > [0] emitter_0 > particle alpha black point (clamped, 0..1) | 0 0.435 0.768 0.947 1 | 0 0.435 0.768 0.947 1 | OK |
| BombardCurves.kt:225 `FIREBALL_INTENSITY_HOT` | fireball_covenant_artillery_huge.particle :: intensity (clamped, 1..15) | 15 12.8 8 3.19 1 | 15 12.8 8 3.19 1 | OK |
| BombardCurves.kt:228 `FIREBALL_INTENSITY_DIM` | fireball_covenant_artillery_huge.particle :: intensity (clamped, 1..15, r1) | 9.56 5.52 2.88 1.44 1 | 9.56 5.52 2.87 1.44 1 | OK |
| BombardCurves.kt:231 `FIREBALL_PALETTE_FAST` | fireball_covenant_artillery_huge.particle :: palette animation (clamped, 0..1) | 0 0.413 0.728 0.929 1 | 0 0.413 0.728 0.929 1 | OK |
| BombardCurves.kt:234 `FIREBALL_PALETTE_SLOW` | fireball_covenant_artillery_huge.particle :: palette animation (clamped, 0..1, r1) | 0 0.0457 0.178 0.392 0.68 | 0 0.0457 0.178 0.392 0.68 | OK |
| BombardCurves.kt:237 `FIREBALL_PARTICLE_ALPHA` | fireball_covenant_artillery_huge.particle :: alpha (clamped, 0..1) | 1 1 0.906 0.226 0 | 1 1 0.906 0.226 0 | OK |
| BombardCurves.kt:240 `BLAST_GAS_ALPHA` | covenant_explosion_artillery_huge.effect :: ] fireball > particle systems > [2] plasma_gas > emitters > [0] emitter_0 > particle alpha (clamped, 0..1) | 1 1 1 0.522 0 | 1 1 1 0.522 0 | OK |
| BombardCurves.kt:243 `BLAST_GAS_BLACK_POINT` | covenant_explosion_artillery_huge.effect ::  particle systems > [2] plasma_gas > emitters > [0] emitter_0 > particle alpha black point (clamped, 0..0.75) | 0 0.00498 0.248 0.499 0.75 | 0 0.00497 0.248 0.499 0.75 | OK |
| BombardCurves.kt:246 `HUGE_RADIUS` | covenant_explosion_artillery_huge.effect :: sma > particle systems > [0] plasma_blue_huge > emitters > [0] emitter_0 > emission radius (clamped, 0.25..1) | 0.25 0.497 0.711 0.883 1 | 0.25 0.497 0.711 0.883 1 | OK |
| BombardCurves.kt:249 `HUGE_SCALE` | covenant_explosion_artillery_huge.effect :: asma > particle systems > [0] plasma_blue_huge > emitters > [0] emitter_0 > particle scale (clamped, 0.5..1) | 0.5 0.879 1 1 0.994 | 0.5 0.879 1 1 0.994 | OK |
| BombardCurves.kt:252 `HUGE_BLACK_POINT_HOT` | covenant_explosion_artillery_huge.effect :: cle systems > [0] plasma_blue_huge > emitters > [0] emitter_0 > particle alpha black point (clamped, 0..1) | 0 0.0704 0.271 0.586 1 | 0 0.0704 0.271 0.586 1 | OK |
| BombardCurves.kt:255 `HUGE_BLACK_POINT_DIM` | covenant_explosion_artillery_huge.effect :: cle systems > [0] plasma_blue_huge > emitters > [0] emitter_0 > particle alpha black point (clamped, 0..1, r1) | 0 0.0267 0.114 0.275 0.52 | 0 0.0267 0.114 0.275 0.52 | OK |
| BombardCurves.kt:261 `HUGE_ALPHA` | plasma_blue_huge.particle :: alpha (clamped, 0..1) | 0 1 1 1 0 | 0 1 1 1 0 | OK |
| BombardCurves.kt:264 `HUGE_GLOW_RADIUS` | huge_glow.lens_flare :: reflections > [0] 0. reflection > radius curve (clamped, 6..10) | 6 6.24 6.97 8.21 10 | 6 6.24 6.97 8.21 10 | OK |
| BombardCurves.kt:267 `HUGE_GLOW_BRIGHTNESS` | huge_glow.lens_flare :: reflections > [0] 0. reflection > brightness curve (clamped, 0..4) | 0 0.144 0.716 1.93 4 | 0 0.144 0.716 1.93 4 | OK |
| BombardCurves.kt:273 `BLAST_LIGHT_INTENSITY` | covenant_explosion_huge.light :: intensity (clamped, 0..30) | 30 25.4 15.3 5 0 | 30 25.4 15.3 5 0 | OK |

### Constants and inline values (by hand, against the suspects)

| Kotlin | Tag :: field | Value used | Real | Verdict |
| --- | --- | --- | --- | --- |
| `TorpedoEffects.kt:554` muzzle round size | `firing.effect` muzzle_flash_round :: particle scale | 0 → 1 | 0.5 → 1 | MISMATCH (#5) |
| `TorpedoEffects.kt:567` muzzle flash intensity | `flash_large.particle` :: intensity | 1 | 1.5 | MISMATCH (#6) |
| `TorpedoEffects.kt:426` `MUZZLE_ROUNDS` | `firing.effect` muzzle_flash_round :: particle starting count | 2 | 1 | MISMATCH (#7) |
| `TorpedoEffects.kt:508` muzzle flash count (1 spawned) | `firing.effect` flash_large :: particle starting count | 1 | 1 | OK |
| torpedo burst sprites drawn square | aspect ratio of `fireball_space_covenant`, `explosive_flash`, `glow_soft`, `plasma_blue_space_large`, `flash_large`, `muzzle_flash_round`, `fireball_smoke` | 1 | 1 | OK |
| torpedo fireball, one frame | `fireball_space_covenant.particle` :: animation rate | not built | 0.5 | n/a |
| `WraithMortarEffects.kt:86` `LIGHT_INTENSITY` 0.35 | `projectile.light` :: intensity | 5 × 0.07 | 5 | OK |
| Wraith puffs / plasma drawn square | aspect ratio of `plasma_trail`, `plasma_blue_medium`, `spark_ember`, … | 1 | 1 | OK |
| — | Wraith `firing.effect` (counts, scales 0.5 → 1 / 0.6 → 1), `firing.light` intensity 10, `glow_soft` intensity 20, `detonation.effect` (smoke and plasma_blue_large emitters) | not built | — | n/a |
| `ProjectorEffects.kt:275-276` flicker glow | `glassing_flicker_glow.particle` :: aspect ratio | 1 | 1.7 | MISMATCH (#8) |
| `ProjectorEffects.kt:468-471` half glow | `glow_soft_half.particle` :: aspect ratio | 1 | 1.7 | MISMATCH (#9) |
| `ProjectorEffects.kt:457-460` bubble | `glassing_bubble.particle` :: aspect ratio | 1 | 1 → 1.25 | MISMATCH (#10) |
| `ProjectorEffects.kt` burst tongue 1:2 | `glassing_plasma_burst.particle` :: aspect ratio | 0.5 | 0.5–0.75 | OK (inside the range) |
| — | `flash_soft` intensity 5, `glassing_plasma_huge` animation rate 0.25, burst palette animation 0.5 / 0 | not built | — | n/a |
| `PlasmaBeamLook.kt:108-111` spark_long | `impact.effect` spark_long :: starting count / lifespan / initial velocity / emission angle / emission radius / self-acceleration | 6 / 0.3 / 1.5 / 45 / — / — | 15 / 0.15–0.25 / 1–2 / 45–70 / 0.02 / 1 | MISMATCH (#2) |
| `PlasmaBeamLook.kt:407` muzzle light | `firing_3p.light` / `firing_1p.light` :: intensity | noise × 0.3 | 2.5–6 periodic | MISMATCH (#3) |
| `PlasmaBeamLook.kt:561-562` muzzle plasma | `muzzle_flash_plasma.particle` :: aspect ratio | 1 | 1–1.5 | MISMATCH (#4) |
| `PlasmaBeamLook.kt:545` muzzle flash | `flash_large.particle` :: intensity | 1 | 1 | OK |
| beam impact flash / embers square | aspect ratio of `impact_flash`, `spark_ember`, `flash_large` | 1 | 1 | OK |
| — | `soft_smoke_small_impact` (count, black point, intensity), `distortion_ring`, `overheated_gas`, `wheel_gas` | not built | — | n/a |
| `RuptureEffect.kt:105` `FLAME_ASPECT` | `plasma_vertical.particle` :: aspect ratio | 2 | 1.5 → 3 / 1.5 → 2.53 | MISMATCH (#11) |
| `RuptureEffect.kt:104` `SPARK_STRETCH` | `slipspace_charge_sparks.particle` :: aspect ratio | 6 | 15 | MISMATCH (#12) |
| `RuptureEffect.kt:939` twinkle alpha | `slipspace_twinkle.particle` :: alpha | 1 | 1 | OK |
| rupture plasma / smoke / distortion square | aspect ratio of `slipspace_burst_plasma`, `slipspace_init`, `smoke_fiery_large`, `distortion_ring` | 1 | 1 | OK |
| `ArtilleryBurst` blast gas (also the Cannon's hit) | artillery `plasma_gas` emitter: count / radius / lifespan / velocity / angle | 10 / 0.35 / 1–1.8 / 8–12 / — | 10 / 0.35 / 1–1.8 / 8–12 / 0–75 | OK |
| Repeater / Autocannon `.proj`, `.wpn` | muzzle / `projectile.effect` emitter counts, rates, scales; aspect ratios | not used | — | n/a |

## Tools and data (`assets/reference/reach-curve-audit/`, gitignored)

- `tags/<effect>.txt` — the 134 audited tag paths.
- `tools/run_audit_dumps.sh [effect …]` — re-dumps them with the patched dumper into
  `<effect>/reach-data/dump32_audit/` (`DUMP_SAMPLES` defaults to 33).
- `tools/reach_dump.py` — dump parser:
  - field paths;
  - blob range;
  - `real()` = min + raw · (max − min);
  - `stock()` = what the stock dumper showed;
  - `suspect`.
- `tools/triage.py <assets/reference> <out>` — writes `triage/<effect>.suspects.txt` and runs the
  clamp-versus-blob check.
- `tools/match_curves.py <mod root> <out.md>` — the curve matcher. It writes `triage/curve_matches.md`
  (details) and `triage/curve_matches_table.md` (the table above). The `CHARGE_LIGHT` row is
  hand-corrected, because the matcher compares raw values and this curve is normalised.
- `h3/` — the Ark / ODST blob decoder and its output.

## Verification done

- Decoder: 2 653 functions decoded, and every clamped one's blob range equals its printed clamp.
- Control: `BombardCurves.kt` 72 / 72 OK.
- `ReachCurves.kt` and `ShieldImpactCurves.kt` regenerate byte-identical from the audit dumps.
- The 12 Reach-side findings were each re-read in the Kotlin source at the lines cited.
- Ark / ODST: 942 blobs decoded; two readings re-checked by hand in `h3/blobs.txt`.

## Developer rulings (2026-10-10)

Offered per effect: preview old against faithful headless first, set the tag values now, or keep the
current look.

| Effect | Findings | Decision |
| --- | --- | --- |
| Plasma Beam Emitter / Suppressor Beam | 1–4 | Keep current look. |
| Plasma torpedoes | 5–7 | Keep current look. |
| Energy Projector | 8–10 | Keep current look. |
| Fleet Slipspace Jump rupture | 11–12 | Keep current look. |

No code changed. The differences are now recorded as deliberate in each effect's doc (open items in
[plasma-beam-emitter.md](plasma-beam-emitter.md), [plasma-torpedo-reach.md](plasma-torpedo-reach.md),
[energy-projector-reach.md](energy-projector-reach.md) and [fleet-slipspace-jump.md](fleet-slipspace-jump.md)).

## Open items

- Open question above: non-ranged Basic ramps in the Halo 3 Ark tags (needs the H3 kit's evaluator if
  the developer wants it settled).

- If any look is revisited later, the hand-pasted curve objects (`TorpedoCurves`, `WraithCurves`,
  `ProjectorCurves`, `PlasmaBeamCurves`) could get `gen_*` scripts on the patched pipeline, as
  `BombardCurves` has. Every hand-pasted curve matches the real tag values except
  `IMPACT_PLASMA_ASPECT` (kept by ruling).

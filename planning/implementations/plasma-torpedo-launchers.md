# Implementation — plasma torpedo launchers, polish + plasma armor melt (2026-10-09)

Last item of the weapons pass. The three plasma torpedoes (`covenant_remnants_plasmatorpedo_light` /
`_plasmatorpedo` / `_plasmatorpedo_heavy`) kept their 2026-10-02 Halo: Reach look
([plasma-torpedo-reach.md](plasma-torpedo-reach.md)) but their numbers were still exact vanilla
copies (light = Sabot rack, medium = Harpoon Pod, heavy = Hurricane). Since the Reach rework made
them plain homing torpedoes, the light had lost its 5 × 200 kinetic darts (100 KIN left) and the
heavy its 7 × 500 MIRV warheads (500 HE left). This pass re-rules their stats, ammo, guidance and
sounds, drops the dark impact smoke, and adds a **plasma armor melt** in place of splash damage. It
also folds in the Reach look's in-game check, which never ran.

## Developer rulings (2026-10-09)

| Topic | Decision |
| --- | --- |
| Scope | Stats per size, ammo / reload / refire, sounds + muzzle / impact look, guidance feel; fold in the pending torpedo in-game check. Mount art stays deferred (2026-10-08 ruling). |
| Light | Atropos+ HE: 1200 HE single hit, 2 ammo, 400 su/s, 1200 range. |
| Medium | Harpoon Pod+: 850 HE × 4-burst, 12 ammo, 300 su/s, 2500 range. |
| Heavy | Big single hit: 3000 HE, 8 ammo, 250 su/s, 2500 range, torpedo HP 500. |
| Splash | No splash. Instead an extra effect that deals more armor damage in the area hit ("plasma melting the armor"). |
| Melt model | Burn over time: over 2 s, a pool = 50 % of the hit's damage is removed from the armor around the hit, strongest at the centre. Armor only, never hull, written to the grid directly. |
| Melt radius | 50 / 75 / 125 su (light / medium / heavy). |
| Melt trigger | Whenever some of the hit reached armor or hull: shield-absorbed hits melt nothing; shield-breaking hits and fading torpedoes that still deal damage do. Ships and fighters. |
| Melt cells | Only cells whose centre is inside the hull. |
| Melt look | Molten glow patch stuck to the hull, orange-white cooling to blue-violet over the burn, plus small armor-coloured floating numbers. Centre shifted ~R/3 into the hull; radius capped at the target's collision radius. |
| Hulks | Target dies mid-burn: melting stops, the glow keeps cooling on the wreck. |
| Ammo | Slow regen: 1 torpedo per 30 s (light) / 60 s (medium) / 90 s (heavy); +1 OP each. |
| Guidance | Per-size vanilla engines: light = Atropos (75°/s, accel 1200); medium = Harpoon (unchanged); heavy = ponderous 45°/s (Reach's Phantom torpedo turns 30–60°/s), accel 250. Hints kept. |
| Sounds | Fire 0.25, impact 0.3, hum 0.25 (unchanged); pitch light 1.15 / medium 1.0 / heavy 0.85. |
| Impact | Dark smoke dropped (as on the Plasma Cannon splash). Reach muzzle flash unchanged. |

Choices made inside those rulings (stated in the approved plan; say if any is wrong):

- **OP:** light 3 → 4 (Atropos+) → **5** (regen); medium 10 → 11 → **12**; heavy 25 → 26 → **27**.
- **Light, rest of the row from Atropos:** impact 40, launch speed 150, flight time 4, torpedo HP 300,
  damage type **HIGH_EXPLOSIVE** (was KINETIC), tooltip Torpedo / Fast / Good. Launcher turn rate 30,
  1 s chargedown, `GUIDED_POOR`, base value 400 kept.
- **Medium:** everything else stays Harpoon Pod (8.25 s, 0.25 s burst delay, HP 150, flight 12.5,
  launch 100, tooltip Finisher / Medium / Medium).
- **Heavy:** impact 50, launch 50, flight 12, chargedown 15, `DO_NOT_AIM, CONSERVE_5` kept; tooltip
  tracking Good → Medium. Engine turnAcc 135 (3 × the turn rate, Harpoon's ratio), dec 200 kept.
- **Regen columns:** `ammo/sec` 0.0333 / 0.0167 / 0.0111, `reload size` 1 written explicitly (the
  ammo tracker's reload interval is reloadSize / ammoPerSec, obf `combat.entities.java` ~21141).
- **Melt pool** = 0.5 × the torpedo's damage (`damageAmount`, so weapon damage bonuses count) ×
  vanilla's `DisintegratorEffect.getDamageTypeMult(source, target)` (the target's armor damage taken,
  the source's damage to that hull size) — the vanilla Breach SRM pattern
  (`BreachOnHitEffect.dealArmorDamage`). Rate falls linearly from impact to 0 at 2 s and integrates
  to the pool. Each cell's share = (1 − d / R) of the total weight. A cell holds at most its current
  armor; the part a stripped cell cannot take is lost. No hull cell within R → the hit cell takes it
  all. Several hits = independent burns that stack.
- **Melt cells** are picked once at the hit: the cell's centre (`ArmorGridAPI.getLocation` returns
  its corner; the centre is half a cell further along both sprite axes) within R of the hit point and
  inside the hull (LemonLib `isPointWithinBounds`, ported from LazyLib `CollisionUtils` 2026-10-12). Cell indices are ship-relative, so
  the melt follows the ship.
- **Floating numbers** every 0.5 s with the armor melted since the last one, yellow
  `Misc.FLOATY_ARMOR_DAMAGE_COLOR`, gated like vanilla by `Misc.shouldShowDamageFloaty` (so none on
  fighters). `syncWithArmorGridState()` once per touched ship at those ticks and at the end: it
  rebuilds the decal renderer, so vanilla scorch decals appear where the armor melted.
- **Trigger** read from the hit's damage result (armor + hull damage > 0, and no `shieldHit`). The
  Covenant shield (a phase cloak) zeroes absorbed damage with a damage-taken modifier, so a fully
  absorbed hit shows 0. Without a result: no `shieldHit` and no raised Covenant shield cloak.
- **Glow placement:** centre R/3 inward along the torpedo's travel direction (its velocity), radius
  min(R, the target's collision radius); plasma spots are picked inside the hull too (8 tries, else
  the centre).
- **Sounds:** one fire id per size in `sounds.json` (pitch per entry, no code); one hum pitch for all
  sizes; impact pitch = size factor × the existing random 0.891–1.
- **Tooltip** `customPrimary` on all three: "Hits on armor or hull melt the armor around them: %s more
  armor damage within %s su over %s seconds." (50% / 50, 75 or 125 / 2). The engine splits the
  highlights on `|` and trims them (obf `ui.impl.java` ~1149).
- **The light also arms the Covenant bomber** (`covenant_remnants_wing1_bomber`: 3 launchers per
  fighter, wing of 2), so the bomber gets the new numbers and regen too.
- **Impact drawn at the on-hit point.** Before this pass it was drawn at the missile's location one
  frame later, up to ~16 su off.
- **Tracking hardened** (bug prevention, the torpedo look had never been seen in-game): every vanilla
  missile keeps its `onFireEffect` in the `.proj`, and the weapon reads the hook from the projectile
  / missile spec (obf `combat.entities.java` ~14028). The Plasma Mortar's `.wpn` onFire never fired for
  BALLISTIC rounds. Now:
  - `PlasmaTorpedoOnFire` is in the three `.proj`.
  - The `.wpn` `everyFrameEffect` `PlasmaTorpedoEffect` also implements `OnFireEffectPlugin`. The
    engine calls `onFire` on such an every-frame effect (obf ~14167, vanilla's "handled in everyframe
    in .wpn file").
  - `TorpedoRenderPlugin` scans `engine.missiles` for the three spec ids.
  - Duplicates are skipped. A one-time log line per path says which one registered.
  - A flight now ends only once its missile was seen in play (3 s fallback), as the Wraith rounds do.

Vanilla anchors (0.98a-RC8, `weapon_data.csv` + `.proj`):

| Weapon | Damage | Ammo | Speed | Range | Turn | OP |
| --- | --- | --- | --- | --- | --- | --- |
| Atropos rack | 1000 HE | 2 | 400 | 1200 | 75°/s, accel 1200 | 3 |
| Sabot rack | 5 × 200 KIN darts | 3 | 150 carrier | 1200 | — | 4 |
| Harpoon Pod | 750 HE × 4 | 12 | 300 | 2500 | 70°/s | 10 |
| Hurricane MIRV | 7 × 500 HE | 10 | 250 | 2500 | 150°/s carrier | 25 |
| Cyclone Reaper | 2 × 4000 HE, unguided | 14 | 400 | 1200 | — | 26 |

Melt precedent: Breach SRM (`BreachOnHitEffect`, extra armor damage written to the grid).

## The melt (`weapons/PlasmaTorpedoMelt.kt`, `weapons/PlasmaTorpedoOnHit.kt`)

`PlasmaTorpedoOnHit` (the `.proj` `onHitEffect`) tells the plugin where the torpedo struck. When the
trigger holds, it starts the melt with `TorpedoRenderPlugin.startMelt`.

`MeltBurn.pick` chooses the cells and their shares. `MeltBurn.advance` removes this frame's part,
(1 − t₀/2)² − (1 − t₁/2)² of the pool. `MeltBurn` makes no engine calls, so the headless harness runs
it on a fake grid.

The plugin advances each burn every unpaused frame. A burn stops when its target is a hulk, dead or
out of play. The plugin shows the floating numbers and re-syncs the decals.

Headless result on a 1000-armor hull (15 su cells, 66.7 armor per cell), from the harness below. The
harness starts from undamaged armor; in game the hit itself has already stripped the cells at the
impact, so a little less lands.

| Hit | Pool | Cells | Melted |
| --- | --- | --- | --- |
| Light | 600 | 15 | 549.5 (3 centre cells stripped; their share is lost) |
| Medium | 425 | 37 | 425 |
| Heavy | 1500 | 76 | 1500 |
| Heavy on armor at 20 % | 1500 | 76 | 886 |

## The look (`utils/render/torpedo/MeltEffects.kt`)

`MeltPatch` keeps its offset in the target's frame (the vanilla Disintegrator pattern), so it moves
and turns with the ship, wreck included; it freezes in place if the target leaves play. None of it
comes from the Reach tags; every value is a placeholder. It uses existing materials only (no new
texture or shader mode): kinds `MELT_GLOW` / `MELT_CORE` (torpedo body glow), `MELT_PLASMA` (torpedo
plasma), `MELT_EMBER` (torpedo spark), drawn with the bursts by `TorpedoRenderer`.

- Glow, radius R → 0.75 R: orange-white (1, .78, .5) × 2 cooling to blue-violet (.42, .38, 1) × 0.9 over
  2 s, then a 0.5 s fade.
- White core at 0.35 R, gone after 0.5 s.
- 4 blue plasma flickers fading in from 0.8 s, using the Plasma Cannon wisp formulas, fixed on the hull.
- Embers, 12 / s at impact falling to none as the patch cools: orange streaks thrown outward with the
  ship's velocity, 0.3–0.8 s.

Impact burst: `TorpedoBurst.impact(..., smoke = false)`, now at the on-hit point. Muzzle flash, body,
star flare, trail and hum are unchanged from 2026-10-02 (until the size looks below).

## Size looks (2026-10-10)

First in-game look: all three torpedoes read the same, so a hit could not be told apart, and the
3000 HE heavy did not look like a big-damage projectile. (The developer gave the Reaper as the
example of an obviously heavy hit; that was **not** a request for red.)

| Topic | Decision |
| --- | --- |
| Scheme | Colour + shape per size. |
| Light | Cyan-blue small dart, thin short trail. |
| Medium | The Reach blue-violet torpedo, unchanged. |
| Heavy | White-hot core with a deep magenta-violet halo and trail. Bigger: look scale 8 → 12 for flight, muzzle and impact. A pulsing halo and a heavier hum. Not red. |
| Colour reach | Flight, muzzle and impact take the size colour. The melt patch is the same for all sizes. |

Choices made inside those rulings (stated in the approved plan; say if any is wrong):

- **Recolour = selective hue shift** (`TorpedoHue` in `TorpedoEffects.kt`):
  - Only cool hues move, at full weight from 180 to 320°, fading to none at 160 / 340°.
  - Light −40° (Reach 232° → cyan ~192°); heavy +60° (→ magenta-violet ~292°).
  - A moved hue stops at 185° / 325°, so pinks never wrap into salmon and blues never into green.
  - Warm accents stay on every size: the salmon body tail, the orange flash at the trail head and
    the orange sparks.
  - The palettized parts use offline-made PNG copies with the same function:
    `graphics/covenant_remnants/fx/plasma_torpedo/{fireball_covenant,blue_plasma,muzzle_flare_phantom_turret}_{light,heavy}.png`.
- **Heavy white-hot:**
  - The star flare goes 60 % toward white.
  - The body's front goes up to 60 % toward white, by p² along the body.
  - The trail's head goes up to 60 % toward white, by (1 − age)².
  - Flight glow × 1.3.
- **Light dart:** trail width × 0.6, trail life 1 → 0.5 s, body thickness × 0.7. Because the light
  flies faster (400 su/s), its trail is still ~200 su long, against ~300 su (medium) and ~250 su
  (heavy).
- **Heavy halo:** one soft glow at the head, 5 Reach units (60 su), breathing ± 25 % at 2 Hz. It
  takes the star colour shifted magenta (not whitened), alpha 0.7.
- **Heavy hum:** `covenant_remnants_plasma_torpedo_loop_heavy`, volume 0.4, pitch 1.068
  (Reach 1.335 × 0.8).
- The Plasma Cannon splash (the same impact burst) keeps the unchanged `TorpedoStyle.REACH`.
  (Since 2026-10-10 the Cannon no longer uses the torpedo burst: it draws the Bombardment's
  `ArtilleryBurst`, see [plasma-cannon-artillery-impact.md](plasma-cannon-artillery-impact.md). The
  Plasma Mortar still uses the torpedo burst.)
- All the new constants are placeholders.

How it is wired:

- `TorpedoSize` now carries the `style` and `loopId`, and the heavy's unit is 12.
- `TorpedoStyle` holds:
  - `REACH`, `LIGHT` and `HEAVY`;
  - the hue shift, head whiten, intensity and shape multipliers;
  - the sprite kinds for the palettized parts.
- `TorpedoFlight(missile, size)` tints its body, star and trail, and owns the heavy's `halo`.
- `TorpedoBurst.muzzle` and `TorpedoBurst.impact` take a style and spawn that style's kinds. They
  tint the colour-only parts: flash, flares and the tints of the palettized parts.
- New in `RuptureParts.kt` and `RuptureTexture.kt`:
  - materials and kinds `TORPEDO_MUZZLE_ROUND_*`, `TORPEDO_FIREBALL_*` and `TORPEDO_PLASMA_*`
    (`_LIGHT` / `_HEAVY`);
  - kind `TORPEDO_HALO`, drawn with the bodies;
  - 6 textures (`torpedo_*_light` / `_heavy` in `settings.json`).
- `TorpedoRenderer` draws the new materials in its burst, plasma and flash passes.

Headless preview (`assets/reference/plasma-torpedo-launchers/`):

- `tools/TorpedoSizesHarness.java` runs the real `TorpedoFlight` and `TorpedoBurst` per size, with
  frames at muzzle, flight 0.6 s, and 0.1 s / 0.4 s after the hit.
- `tools/render_torpedo_sizes.py` renders them; it is `render_torpedo.py` plus the new materials.
- `preview/sizes.png` (1 su/px) and `preview/sizes_close.png` (0.4 su/px) show:
  - the light as a thin cyan streak with a small cyan impact;
  - the medium as the old violet look;
  - the heavy with a white-hot head in a ~60 su magenta halo, a pale pink-violet trail and a
    magenta impact with its flare out to ~300 su.
- Measured largest sprite radius: muzzle 9 / 15 / 71 su, impact 75 / 125 / 299 su.

In-game, developer 2026-10-10: "they look ok for now". The same session hit a crash when the shield
was first raised near ships the torpedoes had damaged. It was an older shield bug (a VBO left bound
vs vanilla's client-array damage decals), fixed in
[shield-vbo-crash-fix.md](shield-vbo-crash-fix.md).

## Damage × 1.6 — tried and reverted (2026-10-10)

The developer asked for the torpedo hits × 1.6 (1920 / 1360 / 4800 HE; the melt pools would have
followed to 960 / 680 / 2400). Once it was noted that this put them well above their vanilla anchors
(heavy: 4800 guided at 2500 range vs the Reaper's 4000 unguided at 1200), the developer reverted it
the same day: "ok you are correct, revert the numbers change". The stats stay as above.

## Files

- `data/weapons/weapon_data.csv` — three torpedo rows (damage, impact, OP, ammo, ammo/sec, reload
  size, type, proj speed, launch speed, flight time, proj HP, tooltip strings, melt `customPrimary`).
  All 48 fields per row.
- `data/weapons/covenant_remnants_plasmatorpedo{_light,,_heavy}.wpn` — `onFireEffect` →
  `everyFrameEffect` `PlasmaTorpedoEffect`; `fireSoundTwo` per size.
- `data/weapons/proj/covenant_remnants_plasmatorpedo{_light,,_heavy}.proj` — `onFireEffect`
  `PlasmaTorpedoOnFire`, `onHitEffect` `PlasmaTorpedoOnHit`; light engine = Atropos (500 / 75 / 1200
  / 300), heavy 135 / 45 / 250 / 200.
- `data/config/sounds.json` — `covenant_remnants_plasma_torpedo_fire_light` / `_fire` / `_fire_heavy`
  (0.25, pitch 1.15 / 1 / 0.85), impact 0.3.
- `src/.../weapons/PlasmaTorpedoEffect.kt` (new) — every-frame + on-fire hook of the launchers.
- `src/.../weapons/PlasmaTorpedoOnFire.kt` — now the `.proj` hook; `launch(missile, weapon, via)`.
- `src/.../weapons/PlasmaTorpedoOnHit.kt` (new) — hit point, melt trigger, pool.
- `src/.../weapons/PlasmaTorpedoMelt.kt` (new) — `MeltBurn`.
- `src/.../utils/render/torpedo/MeltEffects.kt` (new) — `MeltPatch`.
- `src/.../utils/render/torpedo/TorpedoEffects.kt` — `TorpedoSize` (spec id, look unit 3 / 5 / 8,
  melt radius, pitch); `TorpedoLook.unitFor` uses it; `TorpedoFlight` gains `pitch`, `hitPoint`,
  `age`, `seenInPlay`.
- `src/.../plugins/TorpedoRenderPlugin.kt` — dedupe map, `scanTorpedoes`, `torpedoHit`, `startMelt`,
  melt sessions (advance, floating numbers, decal re-sync), seen-in-play end rule, smoke-free impact
  at the hit point, impact pitch by size; log lines `Plasma torpedo registered by …` (once per path)
  and `Plasma torpedo hit …` (first 3 hits).
- `src/.../utils/render/torpedo/TorpedoRenderer.kt` — draws the melt patches with the bursts.
- `src/.../utils/render/rupture/RuptureParts.kt` — kinds `MELT_GLOW`, `MELT_CORE`, `MELT_PLASMA`,
  `MELT_EMBER`.
- `jars/CovenantRemnants.jar` rebuilt (`gradlew buildMod`).
- Size looks (2026-10-10): `TorpedoEffects.kt` (`TorpedoHue`, `TorpedoStyle`, `TorpedoSize.style` /
  `loopId`, styled `TorpedoFlight` / `TorpedoBurst`), `RuptureParts.kt` / `RuptureTexture.kt` (6
  materials + kinds, `TORPEDO_HALO`, 6 textures), `TorpedoRenderer.kt`, `TorpedoRenderPlugin.kt`
  (styled muzzle / impact, per-size hum), `graphics/covenant_remnants/fx/plasma_torpedo/*_light.png` / `*_heavy.png` (6,
  new), `data/config/settings.json` (6 ids), `data/config/sounds.json` (`…_loop_heavy`).
- `assets/reference/plasma-torpedo-launchers/` (gitignored) — `tools/MeltHarness.java`,
  `tools/render_melt.py`, `tools/TorpedoSizesHarness.java`, `tools/render_torpedo_sizes.py`,
  `tools/make_torpedo_palettes.py`, `preview/melt.png`, `preview/sizes*.png`, README.

## Verification done

- `gradlew buildMod` green; `PlasmaTorpedoEffect`, `PlasmaTorpedoOnHit`, `MeltBurn`, `MeltPatch`,
  `TorpedoSize` are in the jar; no new compiler warnings.
- The three `.wpn` and three `.proj` files and `sounds.json` parse (comments and trailing commas
  stripped). Every sound path exists. Every CSV row has 48 fields (csv module).
- `MeltHarness` runs the real `MeltBurn` + `MeltPatch` against the jar. The fake ship flies 100 su/s
  and turns 30°/s, and its fake armor grid uses the engine's cell formulas. Results:
  - The totals in the table above.
  - Floating-number ticks every 0.5 s following the falling rate (medium: 186 / 133 / 80 / 27).
  - Patch drift in the ship frame 0.00 su.
  - Never more than the pool.
- `render_melt.py` renders the patch with the real sprite shader (`preview/melt.png`). It stays on
  the moving, turning hull and cools white → orange-white → blue-violet with blue plasma. A first
  render showed plasma spots floating off a narrow hull; they are now picked inside the hull.

## In-game checklist

Give with `CovenantGiveAll` and fit small / medium / large synergy slots. The heavy is on
`supercap2_std` and the light on the Covenant bomber.

1. Refit cards:
   - light 1200 HE / 2 ammo / 5 OP;
   - medium 850 HE × 4 / 12 ammo / 12 OP;
   - heavy 3000 HE / 8 ammo / 27 OP;
   - each shows the melt line; the codex lists the regen ("Recharged / minute").
2. Fire each launcher:
   - muzzle flash in the size colour (light cyan, medium violet-white, heavy big magenta-white), no
     vanilla smoke;
   - fire sound quiet (0.25), higher on the light and deeper on the heavy;
   - the medium's 4-burst not muddy.
3. In flight (size looks, 2026-10-10):
   - light: a small cyan-blue dart with a thin cyan trail;
   - medium: unchanged, a blue-violet head with a star flare, short glowing body and a lightning
     ribbon trail (orange at the head, blue behind);
   - heavy: clearly bigger, a white-hot head inside a breathing magenta halo, a pale magenta-violet
     trail, and a louder, deeper hum;
   - in a mixed volley, each size can be told apart at a glance;
   - the light is quick (400 su/s), the medium as before, the heavy slow and wide-turning (45°/s).
4. Hit bare armor:
   - impact at the hit point in the size colour (light cyan and small, medium violet, heavy magenta
     and about 1.5x the old heavy size): white flash and streak, fireballs, plasma, orange sparks,
     **no dark smoke**; quieter explosion, pitched by size;
   - a glow patch on the hull that follows the ship as it moves and turns, orange-white cooling to
     blue-violet over ~2 s;
   - yellow numbers every 0.5 s; the armor bar drops, scorch decals appear in the patch, and the melt
     does not touch the hull bar.
5. Hit a raised shield (vanilla ship, Covenant ship): impact, no melt. A shield-breaking hit melts.
6. PD kill: impact, no melt. A torpedo out of flight time fades with no impact.
7. Target dies mid-burn: the glow keeps cooling on the wreck.
8. Empty a launcher: one torpedo comes back after ~30 / 60 / 90 s.
9. `starsector.log`: the `Plasma torpedo registered by …` lines say which path works; the
   `Plasma torpedo hit …` lines show the damage result the melt reads (armor / hull > 0 on bare
   hits, 0 when a Covenant shield absorbs).
10. Heavy volleys and many patches at once: frame rate.
11. Plasma Cannon (its hit splash is the same burst), Mortar and Bombardment look and sound unchanged.

If no torpedo draws: the log should show at least one `Plasma torpedo registered by …` line. If hits
never melt: check the `Plasma torpedo hit …` lines (the trigger reads the damage result).

## Open items

- Numbers are placeholders until the balance pass (see [../../balance.md](../../balance.md)):
  damage, OP, regen, the 50 % / 2 s melt, the radii.
- Melt look constants in `MeltPatch` (intensities, sizes, plasma / ember counts) are placeholders
  from the headless preview.
- Size look constants (`TorpedoStyle` shifts / whiten / multipliers, heavy halo, heavy unit 12) are
  placeholders. The light's trail is still ~200 su long in absolute terms because it flies faster.
- The palette PNGs `*_light` / `*_heavy` are generated (hue band function in `TorpedoHue`). If a
  base palette or the shifts change, rerun
  `assets/reference/plasma-torpedo-launchers/tools/make_torpedo_palettes.py <graphics/covenant_remnants/fx/plasma_torpedo>`
  and keep its constants in step with `TorpedoHue` / `TorpedoStyle`.
- The light's 600 pool in a 50 su patch strips centre cells on lightly armored hulls; the lost share
  is the ruled behaviour.
- Covenant bomber strike strength with the new light torpedo and regen: check in testing.
- `syncWithArmorGridState` rebuilds decals: a cosmetic Energy Projector exit scorch on the same
  ship may be redrawn at the real armor value (cosmetic only).
- The loaded missiles on the launchers stay invisible (`RENDER_LOADED_MISSILES` with the invisible
  torpedo sprite): developer ruling 2026-10-10, "the missiles should stay invisible".
- In-game check: developer 2026-10-10, "all ok" (the 11-point checklist below).
- The old `TorpedoHarness.java` (plasma-torpedo/reach-data) no longer compiles (`TorpedoBurst.impact`
  gained a parameter in the Cannon pass); `MeltHarness` is based on `BombardHarness`.
- Mount art.

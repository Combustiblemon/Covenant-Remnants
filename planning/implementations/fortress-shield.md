# Implementation — Fortress Shield on the Reverence-class Battleship (capital1)

`covenant_remnants_capital1` carries the vanilla `fortressshield` ship system, which did nothing on a
Covenant hull:

- Vanilla `FortressShieldStats` only modifies `shieldDamageTakenMult`, which the Covenant shield never
  read.
- Vanilla's FORTRESS_SHIELD AI returns early when `ship.getShield() == null`, so AI ships never used it.

This pass makes Fortress harden the Covenant shield, gives it an AI and a visual cue, and fixes three
bugs in the shield damage path. Developer decisions 2026-10-01 (via AskUserQuestion).

## Research findings (starsector-knowledge, game 0.98a-RC8)

- **Activation:** a SHIELD_MOD system activates on a hull with no vanilla shield.
  `canBeActivated()` only checks the zero-flux-boost flag; `FortressShieldStats` has no
  `isUsable`. The player could always toggle it.
- **AI:** vanilla's FORTRESS_SHIELD AI (`combat.ai.system`) bails on `getShield() == null` /
  `isOff()`, so it never activates on Covenant hulls.
- **Drain:** 2.5% of base max flux per second as hard flux (500/s on capital1). Hard flux only
  stops dissipating while a vanilla shield is on, so on capital1 the 1250/s dissipation eats the
  drain. Fortress on capital1 has no flux cost and no time limit; the Paragon is capped at
  roughly 40 s.
- **`ApplyDamageResultAPI.damageToShields`:** written by a `DamageListener` after the damage is
  applied, so it has no effect on flux, HP or FX. It only feeds the ship's rolling damage
  statistics, which the AI reads for retreat and danger checks.
- **Listener order:** `modifyDamageTaken` and `reportDamageApplied` are called back-to-back for
  the same hit (`Ship.applyDamage`). Beam frames that deal no damage skip the listeners entirely.

## Decisions

| Topic | Decision |
| --- | --- |
| Trigger | Keep vanilla `fortressshield`. Our code drives the AI. Vanilla data/scripts untouched (Paragon unaffected). |
| Strength | ×0.1 at full (vanilla's hardcoded `1 - 0.9 * effectLevel`). Shield multiplier 0.3 → 0.03. |
| Wiring | **B**: the Covenant shield honours `stats.shieldDamageTakenMult` on **all** Covenant ships. Accepted side effects: skills (Field Modulation, Systems Expertise elite, …), low-CR malus, Degraded Shields d-mod, enemy Entropy Amplifier / Graviton Beam now affect Covenant shields. |
| Damage sources | Every source damages the Covenant shield equally: no damage-type multipliers, no per-type shield stats (developer ruling 2026-10-01, "for now"). |
| Trade-offs | No firing (vanilla `noFiring`) + shield regen frozen while Fortress is active (including charge-up/down). Vanilla drain kept (free on this hull). |
| Regen after | Full rate immediately once Fortress is fully off (skips the post-hit pause and the 50% stage). |
| Gate | Usable only while the Covenant shield is on and not broken; forced off when the shield breaks or is turned off. Player and AI. |
| AI | Incoming-damage estimate, "burst, more eager": ON when projected shield loss over the next 1 s is ≥ 5% of max (or ≥ remaining HP). OFF after 1 s below 2% of max. At least 1 s on. |
| Visual | Magenta tint (fill/border colours lerped by effect level) + new `fortress` shader layer: locked hex lattice + pulsing edge glow. |
| Damage reporting | `ShieldDamageConverter` keeps its ×0.3; only the sticky flag is fixed. |

## What was built

### Damage reduction: `systems/CovenantShieldsStats.kt`, `ShieldDamageListener`

`damageNumber = rawDamage * shieldDamageMult * attachedTo.mutableStats.shieldDamageTakenMult.modifiedValue`.
Vanilla Fortress's modifier comes through unchanged and ramps with its 1.5 s charge-up.

Only the general `shieldDamageTakenMult` is honoured. The per-type variants (beam, projectile,
missile, kinetic/HE/frag/energy `*ShieldDamageTakenMult`) and damage-type multipliers are not, by
design: every source damages the Covenant shield equally (developer ruling 2026-10-01).

### Gate and regen: `CovenantShieldsStats.ShipListener.advance()` (every frame, any pilot)

- **Gate:** while `ship.phaseCloak?.isOn != true || breakCooldownActive`, call
  `ship.blockCommandForOneFrame(USE_SYSTEM)`, and `fortress.deactivate()` if Fortress is on.
- **Regen freeze:** no regen while `FortressShield.effectLevel(ship) > 0`.
- **Full rate after:** on the transition to 0, `lastShieldDamageTime = -inf`, so `regenMult()` returns
  1 straight away. Edge-tracked with `wasFortressActive`, reset per combat.
- **Status line (2026-10-02):** the player sees "Shield regen — frozen by Fortress Shield" while
  it's active — see [shield-regen-status.md](shield-regen-status.md).

### Helper: `systems/FortressShield.kt` (new)

`SYSTEM_ID`, `system(ship)`, `effectLevel(ship)`, and all tuning constants: AI thresholds and tint
colours.

A system-id check is unavoidable for the gate, regen, AI and visual, because the trigger is the
vanilla system. The damage path itself has no id check.

### AI: `ai/FortressShieldAI.kt` (new), run from `ai/CovenantShieldsAI.kt`

`CovenantShieldsAI` is the phase-slot AI, so it only runs while the ship is AI-controlled. It
creates a `FortressShieldAI` when the ship's system is Fortress.

Each check, on a 0.25–0.5 s interval like vanilla:

- `projected = estimateIncomingRawDamage(1 s) × 0.3`. This uses the base multiplier only, so
  Fortress's own reduction doesn't feed back into the OFF test.
- **ON** when Fortress is idle, the shield is up, `canBeActivated()` is true and the ship is not
  overloaded or venting.
- **OFF** via `USE_SYSTEM` once the low-threat timer and the on-time both reach 1 s.

The estimate counts raw damage with no damage-type multipliers, because the Covenant shield ignores
them:

- **Enemy projectiles and unguided missiles within 2500 su:** linear closest approach (relative
  position and velocity, clamped to `[0, T]`) inside the ship's collision radius.
- **Guided missiles targeting the ship:** counted when `distance ≤ maxSpeed × T + radius`.
- **Enemy beams on the ship:** `baseDamage × modifier × brightness × T`. `beam.damage.damage` is
  zeroed between damage ticks, so it can't be used directly.

### Visual

- **`plugins/CovenantShieldsRenderPlugin.kt`:** lerps fill (133,237,255,30) → (255,100,255,45) and
  border (255,255,255,80) → (255,255,255,100) by the Fortress effect level, then calls
  `shader.setFortressLevel(level)`. Alpha uses the 0–100 scale of `updateColor`/`updateBorder` (the
  layers divide by 100). The plan's border value of 160 assumed 0–255 and would have pushed alpha
  past 1, so it was capped at full opacity (100).
- **`data/shaders/shield/fortress.glsl`** (new) + **`utils/render/shield/FortressLayer.kt`** (new).
  - Layer `fortress`, placed after `honeycomb` and before `debug`; `base.frag` gets one extra mix line.
  - Early-outs when `fortressLevel <= 0`, so it costs nothing while Fortress is off.
  - **Locked lattice:** sits exactly on the honeycomb hexes. It uses the same shared grid function at
    the same size (`HoneycombLayer.HEX_SIZE`, 10 world units). Line half-width is 1.5 world units, so
    the lattice line runs along the middle of each honeycomb line. No time term.
  - **Edge glow:** a 25-unit band along the polygon edge, pulsing at 3 rad/s
    (`0.6 + 0.4 sin(time × 3)`).
  - The glow blends toward `fortressGlowColor` (255,150,255, α 0.75). The lattice blends toward
    `fortressHexColor`: opaque white, matching the honeycomb at the peak of its breathing (developer
    request, 2026-10-01; it was magenta). Both scale with the effect level, and the lattice is drawn
    over the glow.
  - Uses only shared base uniforms (`center`, `shipFacing`, `viewportScale`, `time`, `points`) and
    shared base helpers, so there are no cross-module reads.
- **Honeycomb breathing stops under Fortress** (`honeycomb.glsl`, `HoneycombLayer.kt`). New uniform
  `honeycombBreathing`: 1 = the normal brightness wave, 0 = held at full brightness.
  `setFortressLevel` sets it to `1 - level`, so the wave fades out over the charge-up and back in over
  the charge-down.
- **Shared hex grid** (`base.frag`). The honeycomb's grid maths moved verbatim into shared helpers
  `shipLocalPos()` and `hexGridEdgeDistance()`, which both hex layers call. The normal honeycomb
  look is unchanged.
- **`utils/render/ShieldShader.kt`:** registers the layer. `setFortressLevel()` drives the fortress
  layer and the honeycomb breathing.

## Bugs fixed (`CovenantShieldsStats.kt`)

1. **Sticky `lastDamageIsShield`** (`ShieldDamageConverter`). The flag was never reset, so after the
   first shield hit every later hit (hull hits included) was reported as shield damage. Now
   consumed and reset on every report. The ×0.3 is unchanged (developer).
2. **Beams drained the shield ~10× too fast** (`ShieldDamageListener`). The listener used
   `damage.damage`, which for beams is DPS; the engine applies beam damage in ~0.1 s ticks. Now
   `damage × dpsDuration` when `isDps`.
   **Balance impact:** Covenant shields are much tougher against beams than before.
3. **Breaking hit passed 100% to the hull** (`ShieldDamageListener`). The pass-through multiplier
   and the floating text read `shieldHealth` after `dealShieldDamage` had zeroed it. Now the pre-hit
   HP is captured first, so the hull takes only the overflow and the text shows the HP actually
   absorbed.

## Files touched

- `src/org/combustiblelemon/covenantremnants/systems/FortressShield.kt` (new)
- `src/org/combustiblelemon/covenantremnants/systems/CovenantShieldsStats.kt`
- `src/org/combustiblelemon/covenantremnants/ai/FortressShieldAI.kt` (new)
- `src/org/combustiblelemon/covenantremnants/ai/CovenantShieldsAI.kt`
- `src/org/combustiblelemon/covenantremnants/plugins/CovenantShieldsRenderPlugin.kt`
- `src/org/combustiblelemon/covenantremnants/utils/render/ShieldShader.kt`
- `src/org/combustiblelemon/covenantremnants/utils/render/shield/FortressLayer.kt` (new)
- `src/org/combustiblelemon/covenantremnants/utils/render/shield/HoneycombLayer.kt`
- `data/shaders/shield/fortress.glsl` (new), `data/shaders/shield/base.frag`,
  `data/shaders/shield/honeycomb.glsl`
- Docs: `planning/TODO.md`, `planning/ARCHITECTURE.md`, LemonLib
  `docs/shader-composition/example-shield.md` + `debugging.md` (shield layer lists)

## Verification

- `.\gradlew.bat buildMod`: builds clean.
- The assembled shield fragment shader validates with `glslangValidator` (`#version 330 core`) in
  every layer configuration tried: all layers, fortress only, honeycomb only, fortress stubbed,
  fill + fortress, debug only.
- In-game 2026-10-01 (developer): working.
- **Follow-up from that test** (developer: OK 2026-10-01):
  - honeycomb breathing stops while Fortress is on;
  - the fortress lattice moved onto the honeycomb grid. It used to be its own 16-unit grid.
- **Second follow-up** (developer: OK 2026-10-01): the lattice colour changed from magenta to
  bright white. The edge glow stays magenta.
- **Signed off** by the developer 2026-10-01.

## Open items

- Tune the AI thresholds, tint and layer constants in-game (all placeholders, in `FortressShield.kt` /
  `FortressLayer.kt`).
- Vanilla drain is free on this hull (no time limit). Revisit if Fortress proves too strong; a
  custom system row with `noHardDissipation` would mirror the Paragon's ~40 s cap.
- Optional UI cue telling the player that shield regen is frozen while Fortress is on.

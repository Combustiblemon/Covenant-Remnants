# Implementation — Covenant shield hullmods

Five optional hullmods that tune the Covenant shield. Each is the Covenant counterpart of a vanilla
shield hullmod; the Covenant Shield blocks those vanilla mods. The developer picked them from an
ideas list and confirmed all numbers and rules on 2026-10-17.

## The hullmods

| Hullmod (vanilla source) | Effect | OP |
| --- | --- | --- |
| **Hardened Shield Matrix** (Hardened Shields) | Shield takes 20 % less damage. No downside. | 5 / 10 / 15 / 25 |
| **Deep Shield Reservoir** (Extended Shields) | +30 % capacity. Downside: regen delay after a hit +50 %. | 4 / 8 / 12 / 20 |
| **Accelerated Emitters** (Accelerated Shields) | Shield raises twice as fast (+100 % rate, 6 s to 3 s). Regen delay after a hit −33 %. | 3 / 6 / 9 / 15 |
| **Stabilized Emitters** (Stabilized Shields) | Flux falloff eased: regen stays full up to 75 % flux (was 50 %) and runs at 50 % at full flux (was 0 %). | 3 / 6 / 9 / 15 |
| **Reactive Shell** (Shield Conversion - Omni) | Regen +50 %, regen delay −50 %. Downside: capacity −30 %. | 3 / 5 / 8 / 12 |

Rules shared by all five:

- **Where they install:** only on ships with the Covenant shield, meaning Covenant hulls or any
  ship carrying the Covenant Shield hullmod. Reason shown otherwise: "Requires the Covenant
  Shield".
- **Removing the shield:** the mods become unapplicable and the refit screen strips them.
- **Tags:** `shields, defensive, no_drop_salvage`, refit filter "Shields", no dock needed.
- **S-mods:** no bonus (developer ruling).
- **Incompatibility:** Reactive Shell and Deep Shield Reservoir can't share a ship (developer
  ruling, opposite builds). Everything else stacks.
- **Stacking math:** regen delay multipliers multiply. Accelerated + Reactive = ⅓ of the delay
  (1.5 s to 0.5 s). Reservoir + Accelerated = ×1.0.
- **Acquisition:**
  - `CovenantGiveAll` learns them through its prefix scan.
  - New tester caches include them.
  - Existing tester caches get one modspec each, once, under the flag
    `$covenant_remnants_tester_cache_shield_hullmods_added`.
- **Tooltip:** the Covenant Shield tooltip now shows each ship's modified numbers: capacity, regen,
  regen delay and flux falloff.

Design notes behind the counterparts:

- The Covenant shield is a full bubble with no arc or turn rate, so Extended becomes capacity.
- It has no flux upkeep, so Stabilized targets the flux falloff instead.
- Accelerated keeps vanilla's "raise rate" and swaps turn rate for a shorter regen delay.

## How it is wired

- **`systems/CovenantShieldModifiers.kt`:** five vanilla dynamic-stat keys on the ship's
  `MutableShipStatsAPI`. Capacity, regen, regen delay and raise time are MutableStats on base 1;
  the hullmods use `modifyMult`. The flux falloff bonus is a StatBonus on base 0, set with
  `modifyFlat`.
  - `effective(row, stats)` applies the keys to a `shield_data.csv` row and rounds capacity to
    whole points.
- **`systems/CovenantShieldsStats.kt`:**
  - **Shield start:** applies `effective()` to the row. Everything downstream reads the modified
    values: capacity, regen rate, the regen delay in `regenMult()`, the Fleet Slipspace Jump AI's
    regen estimate, and the Fortress Shield AI's max shield.
  - **Flux falloff:** `fluxRegenMultAt` and `fluxRegenKnee` take an extra bonus. `fluxRegenMult(ship)`
    reads the ship's own bonus live, which keeps ShieldTune's global tunable working.
  - **Raise time:** `getInOverride(ship)` returns the system's charge-up × the raise-time multiplier.
    The engine calls it once per ship, when the ship's systems are created (`F` constructor →
    `getInOverride`, 0.98a). That makes it per-ship, with no shared spec edited.
- **Hardened Shield Matrix:** uses vanilla `shieldDamageTakenMult`, which the shield's damage
  listener already applies.
  - The Fortress Shield AI first estimated with the base 0.3 multiplier only, so it turned Fortress
    on early on hardened ships.
  - Fixed the same day (developer request): the AI now includes `shieldDamageTakenMult` without
    Fortress's own multiplier. See [fortress-shield.md](fortress-shield.md), "Later changes".
- **`hullmods/CovenantShieldTweak.kt`:** base class with the applicability rules. It requires the
  Covenant shield (`CovenantShield.hasCovenantShield`, now public) and reuses LemonLib's
  `isApplicableGiven` / `unapplicableReasonFor` for the Reactive / Reservoir pair.

## Fixes made along the way

- **Shield system bar overflow (bug).** While ready, the bar fill was set to the hull's regen delay
  (1.5–5) instead of 1. MagicLib's `drawSystemBar` doesn't clamp, so the bar was drawn 1.5–5× too
  wide. The ready state is now 1 (full). The break countdown is unchanged.
- **`isUsable` now checks `!breakCooldownActive` directly.** It used to compare the bar fill against
  the regen delay, which only worked while every delay was above 1 s. With delays as low as 0.5 s,
  the shield could have been raised mid-break. Behaviour is unchanged for the stock hulls.
- **Tooltip rounding.** Capacity and regen are rounded instead of truncated. Seconds are rounded to
  2 decimals (1.5 × ⅔ printed as 1.0000001 before).

## Files

- New: `systems/CovenantShieldModifiers.kt`, `hullmods/CovenantShieldTweak.kt`,
  `hullmods/HardenedShieldMatrix.kt`, `DeepShieldReservoir.kt`, `AcceleratedEmitters.kt`,
  `StabilizedEmitters.kt`, `ReactiveShell.kt`.
- `systems/CovenantShieldsStats.kt`: modifiers at shield start, per-ship flux falloff,
  `getInOverride`, `isUsable`, bar fill.
- `hullmods/CovenantShield.kt`: `hasCovenantShield()`, and the tooltip now shows modified numbers.
- `campaign/TesterCache.kt`: the top-up is generalised into `addModspecsIfNeeded(flag, ids)`, used
  for the weapon and shield batches.
- `data/hullmods/hull_mods.csv`: five rows.

## Verify

`./gradlew.bat buildMod` compiles clean (2026-10-17). `hull_mods.csv` parses at full width, and the
`%s` counts match the description params. In game:

1. **Refit, Covenant hull:** all five are listed under "Shields", no dock is needed, and the costs
   match the table. Installing one changes the Covenant Shield tooltip numbers. With Reservoir and
   Accelerated on a frigate1, capacity reads 3120 and delay 1.5 s.
2. **Refit, non-Covenant hull without the Covenant Shield:** all five are greyed out with "Requires
   the Covenant Shield". Installing the Covenant Shield makes them installable.
3. **Reactive and Reservoir:** each greys out the other with "Incompatible with …".
4. **Combat:**
   - Hardened: floating shield-damage numbers drop by 20 %.
   - Accelerated: the raise sweep takes about 3 s instead of 6 s.
   - Stabilized: at full flux the regen status reads 50 % instead of paused.
   - Reactive: refills fast after hits.
5. **Shield system bar:** stays inside its box when ready and drains during a break.
6. **Existing tester save:** the five modspecs appear once, and the log has the top-up line.
7. **Fortress AI:** an AI Reverence with Hardened Shield Matrix raises Fortress Shield under a
   slightly heavier barrage than one without it, and still doesn't flicker it on and off.

## Open items

- Numbers fall under the general "Tune hullmod numbers" pass.

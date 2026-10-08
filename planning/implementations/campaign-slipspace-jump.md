# Implementation — campaign Slipspace Jump (2026-10-07)

The campaign-layer slipspace travel ability. Replaces the prototype (listed as "shipped" in
PLAN.md but never working: hard-coded "Askonia" destination, a hand-rolled transition that called
the combat engine from the campaign, an exit leg `isUsable` could never reach). Built against the
starsector-knowledge skill (0.98a-RC8).

## What it does (developer rulings 2026-10-07)

1. **Press the ability** (star systems only; gating below). A dialog opens the gate-style sector
   map picker over every star system except the current one, the pocket, deep space, systems
   without a hyperspace anchor and `THEME_HIDDEN` ones. Fuel for the distance is shown; the
   confirm button is disabled when it is short; the fuel range ring is drawn. Cancel (picker
   cancel, Escape, or the dialog's Cancel) returns the ability to idle for free.
2. **Charge** (the ability's `activationDays` ramp, 0.4 d × S-mod Tuning): player controls are
   taken (click-to-interact blocked, move override, interaction target cleared, other abilities
   force-disabled), the fleet brakes to a stop, the Reach rupture charges ahead of the fleet
   (`PORTAL_AHEAD_RADII` 2 fleet radii, radius `max(1.5 × fleet radius, 40)`), the charge hum
   plays. Pressing the ability again cancels for free; so does joining a battle or docking.
3. **Lurch** (2 s): the rupture opens; the fleet slides into its centre (t², placed, not steered).
   Still cancellable.
4. **Commit**: fuel (per LY of hyperspace distance) and the CR hit (transverse-jump style, damage
   roll on low-CR ships) are taken; the fleet is hidden (member views alpha 0), the rupture
   collapses, and the custom transition starts.
5. **Transition out** (`SlipspaceTransitionScript`, a transient sector-level script): hold 0.6 s
   (collapse visible) → screen fade → location switch into the **pocket system "Slipspace"** at
   the entrance, facing down the path → fade back → member views warp in with jitter.
6. **Pocket**: pitch black background (`graphics/covenant_remnants/backgrounds/slipspace_bg.jpg`,
   solid black placeholder) with sparse drifting light streaks (`SlipspaceAmbienceEntity`,
   placeholder). A **winding slipstream path** (`SlipspaceStreamTerrainPlugin`, subclass of
   vanilla `SlipstreamTerrainPlugin2`) carries the fleet from the entrance to the exit; the player
   can slow or speed it slightly (vanilla slipstream steering). Off the path the fleet is pushed
   back, harder the further out; hard clamp at one fleet length (2 radii) past the edge. Vanilla
   Transverse Jump, Sustained Burn and Emergency Burn are force-disabled in the pocket. The
   entrance is arrival-only (`non_clickable`).
7. **Path per pair**: random length `[6000, 14000]` su and shape (seed) generated on first use for
   an unordered (origin, destination) pair and saved in the sector's persistent data
   (`covenant_remnants_slipspace_paths`), so the pair always gets the same path. Shape: the heading
   drifts around a base direction by three summed sine waves (≤ 75° total), points every 100 su.
8. **Exit**: the exit portal is interactable (`SlipspaceExitDialogPlugin`, routed by the transient
   `SlipspaceCampaignPlugin`). "Exit slipspace" → 1 s lurch into the exit (views fade out) →
   transition back: the fleet lands at the destination's **outer edge on the side facing the
   origin** (`BaseThemeGenerator.getOuterRadius + 500`), under the exit-rupture flash that reveals
   it; views warp in; facing the system centre.
9. **Cooldown** (0.5 d × S-mod Tuning) starts at landing.

**Costs** (placeholders): fuel per LY × 1.0, CR 10 % of deployment cost × `DIRECT_JUMP_CR_MULT`,
damage roll `EmergencyBurnAbility.ACTIVATION_DAMAGE_PROB` on non-ready ships, cooldown 0.5 d.

**Gating**: granted by a quest (not built yet); until then `CovenantGiveAll` grants it
(`SlipspaceJumpRules.grantToPlayer()`, the quest will call the same). `unlockedAtStart` is blank.
Usable only when every ship that must carry `covenant_remnants_slipspace_drive` does: if the fleet
has any cruiser or capital, every cruiser and capital; otherwise every destroyer and frigate. All
non-fighter members count (mothballed and civilian included). The tooltip lists the offenders.

**Slipspace Tuning** (`covenant_remnants_slipspace_tuning`): each ship carrying it +10 % transit
speed (stream burn level), capped at ×1.5; each **S-modded** one also −10 % charge-up and cooldown,
floored at ×0.5. Frozen at activation (charge) and at landing (cooldown).

**AI**: player only.

## Assumptions (not rulings — flagged for the developer)

- Pair key is unordered (`min(id)|max(id)`).
- CR hit reuses vanilla `Stats.DIRECT_JUMP_CR_MULT`, so Navigation-type skills reduce it.
- Joining a battle / docking during charge or lurch cancels for free.
- Tuning scales per ship count with a cap; S-mod counted per ship.
- Pocket stays at hyperspace (0, 0); it is hidden from map and codex anyway.
- Exit with no saved route (debug only) falls back to the origin, then to any system.
- Placeholder `descriptions.csv` rows for the entrance and exit entities.

## Files

New:

- `src/.../campaign/abilities/SlipspaceJumpRules.kt` — every number (placeholders), gating,
  destinations, fuel / CR, Tuning multipliers, arrival point, `grantToPlayer()`.
- `src/.../campaign/abilities/SlipspaceDestinationDialog.kt` — host dialog + map picker.
- `src/.../campaign/abilities/SlipspaceTransitionScript.kt` — the custom location switch.
- `src/.../campaign/abilities/SlipspaceJumpVisuals.kt` — campaign owner of the rupture renderer,
  effects tagged by location, hum and one-shots.
- `src/.../campaign/systems/SlipspacePaths.kt` — per-pair records + polyline builder.
- `src/.../campaign/terrain/SlipspaceStreamTerrainPlugin.kt` — slipstream subclass with push-back
  and clamp (own nearest-point-on-polyline; no `readResolve`, the superclass's is package-private).
- `src/.../campaign/entities/SlipspaceExitDialogPlugin.kt`, `SlipspaceCampaignPlugin.kt`,
  `SlipspaceAmbienceEntity.kt`.
- `data/campaign/terrain.json` — `covenant_remnants_slipspace_stream` (vanilla slipstream
  sound block).
- `graphics/covenant_remnants/backgrounds/slipspace_bg.jpg` — 1024² solid black (ffmpeg).

Changed:

- `src/.../campaign/abilities/SlipspaceJump.kt` — rewritten (same class name: saves store it).
  Phase machine, `pressButton` / `isUsable` overrides (vanilla `pressButton` only activates and
  `isUsable` is false while turned on, so the cancel needs both), Tuning via
  `getActivationDays` / `getCooldownDays`, resume-after-load, ability-layer rendering (`ABOVE`).
  The prototype's four fields are kept, unused, for save compatibility.
- `src/.../campaign/systems/SlipspaceSystem.kt` — `findSlipspaceSystem`, `ensureSlipspaceSystem`
  (create or migrate: tag, typed entrance / exit / ambience entities, legacy portals removed),
  `prepareSlipspaceRoute`, route memory.
- `src/.../campaign/systems/scripts/SlipspaceSystemEFS.kt` — **bug fix**: acted every frame
  sector-wide (non-current systems advance round-robin), disabling Transverse Jump everywhere. Now
  guarded on the player being in the pocket; also disables Sustained / Emergency Burn.
- `src/.../campaign/entities/SlipspacePortalEntity.kt` — stray obf import removed; look unchanged
  (placeholder, developer wants a replacement later).
- `src/.../utils/extensions/CampaignFleetAPI.kt` — `isInSlipspace` by pocket tag (name fallback).
- `src/.../utils/render/rupture/RuptureRenderer.kt` — `alphaMult` (the ability renderer ignores the
  viewport alpha, so the campaign owner passes the screen fade); `RuptureEffect.master` setter
  opened to `internal` for it. Combat unaffected.
- `src/.../hullmods/SlipspaceTuning.kt` — description params for the campaign bonus + S-mod text.
- `src/.../commands/CovenantGiveAll.kt` — grants the ability. `RegenerateSlipspace.kt` — null-safe,
  refuses inside the pocket, uses `ensureSlipspaceSystem`.
- `src/.../CovenantRemnantsPlugin.kt` — `onApplicationLoad` preloads the background;
  `onGameLoad` ensures the pocket and registers `SlipspaceCampaignPlugin`; `onNewGame` ensures.
- `data/campaign/abilities.csv` — `unlockedAtStart` blank, `deactivationDays` 0, new desc.
- `data/config/custom_entities.json` — `covenant_remnants_slipspace_entrance` / `_exit` /
  `_ambience`; the legacy `slipspace_jump_point` type stays registered for old saves.
- `data/hullmods/hull_mods.csv` — Slipspace Tuning desc + `sModDesc`.
- `data/strings/descriptions.csv` — entrance / exit rows. `data/console/commands.csv` — help text.

Deleted: `src/.../utils/abilities/Slipspace.kt` (the broken prototype transition).

## Key decisions / findings

- **Vanilla facts that shaped the code** (verified in the skill): `BaseDurationAbility.pressButton`
  only activates; the picker's cancel does fire `cancelledEntityPicking`; `setInJumpTransitionWarpOutStage`
  is a no-op in 0.98a; `FleetAbilityRenderer` ignores viewport alpha; abilities advance inside the
  location, so the switch runs from a sector-level script; `CampaignFleet` and
  `CampaignFleetMemberView` are `DoNotObfuscate` (direct calls, no reflection); `showNoise` is
  obf-only (reflection in try/catch, optional).
- **Save model**: XStream bypasses constructors and old saves predate the new fields, so every
  saved field is nullable / zero-safe and transients are lazily rebuilt. Mid-flight loads resume:
  charge / lurch → free cancel; mid-transition → finish the switch on the next engine frame.
- **Phase reconciliation** (post-review fixes): every frame the ability checks the phase against
  where the fleet is (IN_POCKET outside the pocket → IDLE; IDLE inside it, e.g. a prototype save →
  IN_POCKET; PICKING with no dialog open → IDLE). Costs are taken only after the pocket entrance
  is confirmed to exist. Locking controls also turns off an already-active ability (Sustained
  Burn), since `forceDisable` only blocks activation. The picker open is retried at most 5 times.
- **`SlipspaceJumpTune` console command** (developer request 2026-10-07, same shape as
  `FleetJumpTune`): every number in `SlipspaceJumpRules` is a live `Tunable` — list / set / reset.
  Dev overrides: `NO_SHIP_RESTRICTION 1` ignores the drive-hullmod rule, `FREE 1` drops fuel and
  CR costs; `CHARGE_TIME_MULT` / `COOLDOWN_MULT` scale the csv timings. `PATH_*` apply to pairs
  jumped for the first time (saved records keep their length). Resets on restart.
- **Round 2 (developer feedback 2026-10-07)**: the control lock no longer touches every ability
  (it had been switching the transponder off) — only Sustained Burn, Emergency Burn and Transverse
  Jump are held off. The fleet now turns toward the portal while charging (`TURN_RATE` 120°/s) and
  stays pointed at it through the lurch. The corridor's width tapers toward both ends
  (`STREAM_TAPER` 1500 su to `STREAM_END_WIDTH` 30 %) so it opens and closes gracefully, and past
  either end the clamp allows only one fleet length (no half-width allowance), so the fleet cannot
  sail past the exit.
- **Placeholders** (all numbers): see the constants in `SlipspaceJumpRules` and the ambience /
  stream colours. Tune after all gameplay systems are done (developer 2026-10-02).

## In-game checklist

1. Old prototype save loads; the pocket gains its tag and the new entities. New game: no ability;
   `CovenantGiveAll` puts it on the bar. Transverse Jump works in normal systems.
   `RegenerateSlipspace` works with and without a pocket.
2. Tooltip reasons: small-only fleet without drives, mixed fleet with a driveless cruiser, all
   good; blocked in hyperspace and in the pocket. Picker: lists systems (not current / pocket),
   fuel ring, info grid, confirm greyed when fuel is short. Cancel and Escape: no cooldown, pause
   state restored.
3. Charge: clicks and other abilities inert; brake; rupture charging ahead; hum. Cancel during
   charge and lurch is free. Commit takes fuel and CR (low-CR damage messages).
4. Transition: fleet vanishes into the rupture, collapse plays, fade, lands at the pocket entrance
   facing down the path, views warp in. `isInHyperspaceTransition` true only during the script.
5. Pocket: black background, sparse streaks; the current carries the fleet to the exit; slight
   steering; push-back ramps up off the path, hard stop one fleet length out; Sustained /
   Emergency Burn and Transverse Jump disabled only here. Same pair → same path across reloads;
   different pairs differ.
6. Exit: the exit opens the dialog, the entrance does nothing; lurch in; flash at the destination
   reveals the fleet at the system edge facing the origin; cooldown starts on landing.
7. Tuning: transit time drops with N tuned ships; S-mod shortens the charge bar and the cooldown;
   hullmod and ability tooltips show the values.
8. Save / load mid-charge (clean cancel), in the pocket, mid-exit.
9. Visual: rupture scale on frigate vs capital fleets, zoom in / out, heat haze alignment at
   100 % and 150 % UI scale, dims with the fade, nothing from the origin shows in the pocket, no
   `OpenGL error` in the log.

## Open items

- Everything numeric is a placeholder (tuning pass later).
- Pocket entrance / exit look: placeholder shader, replacement undecided (developer).
- Pocket background and light streaks: placeholders.
- Picker opened from the dialog's `init` (vanilla opens it from a rule command later); fallback
  retries from `advance` if that is too early — check.
- Heat-haze `copyScreen()` vs UI scaling in the campaign — fallback is distortion off.
- The granting quest.

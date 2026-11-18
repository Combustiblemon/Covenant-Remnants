# Implementation: Slipspace noise overlay fix (2026-11-18)

## Problem

`SlipspaceTransitionScript.showNoise()` plays the campaign static-noise overlay, the one the
engine shows on its own jumps. It runs when the player's Slipspace Jump fades out. It called
`ui.javaClass.getMethod("showNoise", float, float, float).invoke(...)`, and that compiled code names
`java.lang.reflect.Method`. The script classloader blocks that class (see the classloader rules in
[game-0985-update.md](game-0985-update.md)). The lookup therefore failed when the call ran, the
`catch (Throwable)` swallowed it, and the noise never played. Nothing was logged.

## Fix

- **Call path:** `showNoise()` now goes through LemonLib `ReflectionUtils.invoke` (MethodHandles,
  with the field and method types kept as `Any`).
  - It tries the declared lookup on the live object first, made accessible.
  - The public lookup is the fallback.
- **Failure:** if both lookups fail, it logs one warning per session naming the class and method,
  so a game patch that renames the method is noticed. The transition still runs without the noise.
- **Whole-jar check:** after the change, no class in `CovenantRemnants.jar` names `java.lang.reflect`.

## Verification

- **Live object:** in 0.98.5a-RC7, `Global.getSector().campaignUI` is
  `com.fs.starfarer.campaign.CampaignState`, which declares a public `showNoise(FFF)V`.
- **Harness check:** the new `campaign_ui_invoke` command makes the same call on the live game.
  On fixture `0.1.1/save_gdsfgdfgdfs_…` it returned `lookup: declared` with no error.
- **Screenshot:** one taken right after the call shows the film-grain overlay; one taken before
  does not.
- **Log:** the run logged no `noise overlay unavailable` warning.

## Files touched

- **Covenant Remnants:**
  - `src/.../campaign/abilities/SlipspaceTransitionScript.kt`
  - `CHANGELOG.md`, `planning/TODO.md`, `planning/implementations/game-0985-update.md`
- **StarsectorHarness:** `src/.../cmd/CampaignCommands.kt` (the `campaign_ui_invoke` command),
  `CHANGELOG.md`.

## Open items

- Not yet seen during a real Slipspace Jump: the harness cannot drive the destination picker. The
  call path is the same, and the noise plays as the fade to black starts.

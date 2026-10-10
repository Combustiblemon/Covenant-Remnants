# Implementation — system background and music wrong after leaving slipspace (2026-10-11)

The developer reported that after a campaign slipspace exit, "the system music and background
disappear", and that reloading the game fixes it. They asked whether the pocket's
`SlipspaceSystemEFS` keeps running after the exit.

**The EFS is not the cause.** It does keep running for good, but it only acts on the pocket:

- the movement abilities are blocked only while the player is `isInSlipspace`;
- the particle shift targets the pocket's own `BackgroundAndStars`, which is per location.

A reload re-runs the same EFS, so the EFS cannot be what a reload fixes. There are two separate
engine behaviours behind the report.

## Background: the shared texture shrinks (fixed)

- **One shared texture.** Every star system's `BackgroundAndStars` builds its `ReplaceableSprite`
  from the default background id (`backgrounds.defaultSpaceBackground`, `background4.jpg`), so all
  systems share one GL texture from the `TextureLoader` cache.
- **How backgrounds swap.** Switching location swaps backgrounds by loading the new image into that
  texture (`ReplaceableSprite.update()` → `TextureLoader.o00000(Texture, String)`, obf
  `graphics.java`). The loader renames the texture and re-keys its cache entry.
- **The size rule.** An image of 1024 × 1024 or less is uploaded with `glTexImage2D`, which
  reallocates the texture. A larger image is uploaded with `glTexSubImage2D` into the existing
  storage, so it must fit.
- **The failure.** The pocket's `slipspace_bg.jpg` is exactly 1024 × 1024 (pure black), so entering
  the pocket shrinks the shared texture to 1024. Vanilla system backgrounds are 2048 × 2048. On
  exit, their upload into the 1024 storage fails with a GL error and nothing is written. The texture
  keeps the black pocket image, and since its name already reads the destination's background, the
  engine never retries. A reload rebuilds the texture, which is why it fixes the problem.
- **Fix:** a 2048 × 2048 pure-black `slipspace_bg.jpg`, so the pocket goes through the same path as
  vanilla and never shrinks the texture. It looks identical. It was generated with ffmpeg and
  checked (Y 0, neutral chroma). The developer approved overwriting the tracked file on 2026-10-11;
  git still has the old one.
- **Rule for the real pocket art:** 2048 × 2048, like vanilla backgrounds. A pocket background of
  1024 or less in both dimensions brings the bug back.

## Music: the previous system's track keeps playing (fixed)

- **What the log showed.** A temporary diagnostic (`SlipspaceMusicDiagnostic`, now removed) logged
  the music state across a full jump: Gamma Sunda → pocket → Corvus. The music never stopped.
  `campaign_noncore_part1.ogg` (the non-core set) played the whole way, and kept playing in Corvus
  for 50+ s after arrival. Corvus is `THEME_CORE`, so its set is `music_campaign`
  (`miscallenous_corvus_campaign_music.ogg`). The "missing" system music is the destination's own
  track never starting.
- **Cause.** The engine switches music only when `MusicPlayerPlugin.getStateTokenForCampaignLocation()`
  changes (`CampaignState`, obf `campaign.java` ~6813). Every star system without `$musicSetId`
  returns `CAMPAIGN_SYSTEM`: Gamma Sunda, the pocket and Corvus alike. A vanilla trip between
  systems always passes through hyperspace, which changes the token and restarts the music. Our jump
  goes system → system directly, so the token never changes. Vanilla's own comment in
  `MusicPlayerPluginImpl.getStateTokenForCampaignLocation` describes exactly this case.
- **Fix** (`SlipspaceTransitionScript`, `State.SWITCH`, player and `Leg.BACK` only):
  - read the token before and after `switchLocations()`;
  - if it is unchanged, call `Global.getSoundPlayer().restartCurrentMusic()`. That re-queries
    `getMusicSetIdForCampaignStateToken` for the new location (obf `D.java`, `OO0000()`) and
    starts its track, as arriving from hyperspace does;
  - a changed token is left to the engine.
- **Unchanged:** the jump into the pocket. The pocket keeps playing the origin's track, as before.

## Files

- `src/.../campaign/abilities/SlipspaceTransitionScript.kt` — the music restart on exit, plus
  `musicStateToken()`.
- `graphics/covenant_remnants/backgrounds/slipspace_bg.jpg` — now 2048 × 2048, pure black.
- `CHANGELOG.md` — two v0.1.2 bug-fix lines (background, music). They were first added under
  v0.1.1, then moved to v0.1.2 when the developer released v0.1.1 without them (2026-10-11).
- Diagnostic added and removed in the same session: `SlipspaceMusicDiagnostic.kt`, and hooks in
  `SlipspaceExitDialogPlugin.kt` and `SlipspaceTransitionScript.kt`. Nothing of it remains.

## In-game check

**Passed.** Developer 2026-10-11: "tested the change and it works". The checklist:

1. From a non-core system, jump and exit into a core system (e.g. Corvus). The core music starts on
   arrival, and the background shows straight away.
2. The other way round, core → non-core: the music switches and the background shows.
3. Save in the destination and reload: same music and background.

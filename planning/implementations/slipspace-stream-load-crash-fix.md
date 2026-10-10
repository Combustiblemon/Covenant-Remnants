# Implementation — crash on loading a save made after a slipspace jump (2026-10-11)

The developer made a campaign slipspace jump, exited the pocket, saved, and loaded that save. The
game crashed on the first campaign frame:

```text
java.lang.NullPointerException: Cannot invoke "java.util.List.iterator()" because "this.bounds" is null
    at com.fs.starfarer.api.impl.campaign.velfield.SlipstreamTerrainPlugin2.getSegmentsNear(SlipstreamTerrainPlugin2.java:2724)
    at com.fs.starfarer.api.impl.campaign.velfield.SlipstreamTerrainPlugin2.addParticles(SlipstreamTerrainPlugin2.java:791)
    at com.fs.starfarer.api.impl.campaign.velfield.SlipstreamTerrainPlugin2.advance(SlipstreamTerrainPlugin2.java:409)
    at com.fs.starfarer.campaign.CampaignTerrain.advance(Unknown Source)
    at com.fs.starfarer.campaign.StarSystem.advance(Unknown Source)
```

## Root cause

- **The terrain:** the pocket's path is `SlipspaceStreamTerrainPlugin`, our subclass of vanilla
  `SlipstreamTerrainPlugin2`. It stays in the pocket system after the jump until the next jump
  replaces it, so every save made after the first jump contains it.
- **What vanilla relies on:** `SlipstreamTerrainPlugin2` keeps `bounds`, `particles` and `layers` in
  transient fields. They are not saved, and XStream does not run field initializers on load. Its
  `readResolve()` rebuilds them, but that method is **package-private**.
- **Why it never ran for us:** the game's XStream 1.4.10 (`SerializationMembers.getRRMethod`, checked
  by decompiling the game's `xstream-1.4.10.jar`) uses an inherited `readResolve` only when it is
  public or protected, or declared in the same package as the loaded class. Our class is in another
  package, so XStream skipped it, and the three fields came back null.
- **Where it fails:** the next real `advance` (`amount > 0`) calls `addParticles`.
  - Player more than 2 LY from the pocket: it calls `getSegmentsNear`, which iterates `bounds` and
    crashes. This is the crash above, after the exit.
  - Player close to or inside the pocket: it iterates `particles` and crashes the same way.
- **Old comment was wrong:** the class's doc comment claimed the opposite ("no `readResolve` here
  on purpose: the superclass's is package-private and ours would shadow it").

## Fix

`src/.../campaign/terrain/SlipspaceStreamTerrainPlugin.kt`:

- **New public `readResolve()`.** XStream finds it on the class itself. It does what vanilla's does:
  - `particles` and `bounds` become new empty lists;
  - `setNeedsRecompute()` makes the first `advance` rebuild the bounding boxes from the saved
    segments.
- **`getActiveLayers()` overridden** to return `TERRAIN_SLIPSTREAM`, the value vanilla's
  `readResolve` sets. The superclass keeps its layer set in a private field that we cannot reset,
  and it would otherwise return null after a load.
- **The wrong comment is removed.**

Saves that already crash are repaired by the fix: the saved state is intact, only the transient
fields were missing, and they are rebuilt on load now.

Checked:

- No other mod class extends a vanilla class with a package-private `readResolve`. All 42 vanilla
  API classes that have one were compared with every superclass used in `src/`, and only
  `SlipstreamTerrainPlugin2` matched.
- The mod's own `@Transient` fields (`SlipspaceJump`, `SlipspaceAmbienceEntity`,
  `SlipspacePortalEntity`, `SlipspaceSystemEFS`) are all nullable or default to `0` / `false`. A
  load gives them the same values as their initializers.
- `gradlew buildMod` is green.

## In-game check

**Passed.** Developer 2026-10-11: "i loaded a save after jump", with no crash. Save / load while
inside the pocket (point 3) was not reported separately, but it goes through the same
`readResolve`. The checklist:

1. Load the save that crashed: it loads and keeps running, and the pocket path is still there.
2. Jump, exit, save, load: no crash.
3. Save inside the pocket (mid-transit) and load: the path renders, the current carries the fleet,
   and the push-back still works.

## Note

Rule for any new subclass of a vanilla class: check the superclass for a package-private
`readResolve`. If it has one, declare a public `readResolve` in the subclass that restores the same
state, because the game's XStream will not call the inherited one across packages.

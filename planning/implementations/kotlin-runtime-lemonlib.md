# Implementation — Kotlin runtime in LemonLib, LazyLib dropped (2026-10-12)

Both mods are written in Kotlin but shipped no Kotlin runtime. They only worked because LazyLib's
`jars/internal/Kotlin-Runtime.jar` (stdlib 2.1.x, coroutines 1.10.1) happened to be on the mod
classpath. The developer downloaded a fat runtime jar and asked for LemonLib to ship it, so
Covenant would stop depending on LazyLib just for the runtime.

## Rulings (developer, 2026-10-12)

- **Scope:** LemonLib ships the runtime **and** Covenant drops its `lw_lazylib` dependency. That
  means replacing Covenant's 5 LazyLib calls.
- **Compiler:** both builds move from Kotlin 2.1.0 to **2.2.20** and compile against the shipped jar.
- **Git:** both jars (runtime and sources) are committed to the LemonLib repo.
- **drawCircle:** becomes a public LemonLib helper, not a private function in the one plugin that
  uses it.
- **Approved deviations from LazyLib:**
  - the collision helpers return fresh vectors, not the caller's own vectors or the entity's live
    location
  - `getCollisionPoint` refreshes the exact bounds itself

## How Starsector loads mod jars (decompiled, 0.98a-RC8)

These facts drove the design.

**One classloader for all mods.** `StarfarerLauncher` puts every enabled mod's `jars` into a single
`URLClassLoader`, with a filtering parent (`com.fs.starfarer.loading.scripts.B`).

**Last name wins.** Mods are sorted by `sortString`, falling back to `name`, and their jars are
added in **reverse** order. The mod that sorts last is searched first. "LemonLib" sorts after
"LazyLib", so LemonLib's 2.2.20 classes win over LazyLib's for every mod. The new jar contains
every `kotlin.*` class LazyLib's has. The game itself ships no Kotlin.

**Dependencies don't set order.** `dependencies` in `mod_info.json` do not affect load order. The
LemonLib README used to claim they did; that is now fixed.

**Some reflection is blocked.** The parent loader blocks `java.lang.reflect.Method`/`Field`/
`Constructor` and `java.io.File`. Most of `kotlin-reflect` and the `kotlin.io` file helpers will
throw if used. Neither mod uses them.

**LazyLib stays installed.** Players still have LazyLib, because MagicLib requires it. Covenant
only stopped declaring it and stopped calling it.

## What was built

### LemonLib

- `jars/lib/Kotlin-Runtime.jar` holds kotlin-stdlib 2.2.20, kotlin-reflect and kotlinx-coroutines
  1.11.0. It is listed in `mod_info.json` `jars`.
- `jars/lib/Kotlin-Runtime-sources.jar` is for IDE source attach only. It is not in `jars`, and it
  is not in the release zip.
- `.gitignore`: `/jars` became `/jars/*` plus `!/jars/lib/`, so the runtime jars are tracked and the
  built jar is not.
- `build.gradle.kts`:
  - **Kotlin:** 2.2.20, with `compileOnly(files("jars/lib/Kotlin-Runtime.jar"))` replacing
    `implementation(kotlin("stdlib"))`.
  - **Compiler options:** `kotlinOptions` (an error in Kotlin Gradle plugin 2.2) became
    `kotlin { compilerOptions { jvmTarget = JVM_1_8; jvmDefault = NO_COMPATIBILITY } }`.
    `NO_COMPATIBILITY` is the documented equivalent of `-Xjvm-default=all`.
  - **cleanMod:** now deletes only `jars/LemonLib.jar` and the `*.stale-*` copies. It used to
    delete the whole `jars/` folder.
  - **Release:** `releaseFiles` includes the runtime jar.
- `gradle.properties`:
  - Added `kotlin.stdlib.default.dependency=false`.
  - Removed `kotlin.incremental.useClasspathSnapshot`, which KGP 2.2 reports as unsupported.
- `geometry/Collision.kt` is new. Its functions are ported from LazyLib's `CollisionUtils`, whose
  licence says "All modders have my permission to copy and use any code I've written":
  - `getSegmentIntersection(start1, end1, start2, end2)`: endpoint snap (`ptSegDistSq ≤ 0.11111`),
    then an exact `denom == 0` parallel test, then r/s in [0,1]. LazyLib's
    `MathUtils.equals(0f, denom)` reduces to an exact zero test.
  - `getCollisionPoint(lineStart, lineEnd, entity)`:
    - With null bounds it does a segment-vs-collision-circle test and returns a copy of the center.
    - Otherwise it calls `bounds.update` once. A start inside the bounds returns a copy of the start;
      anything else returns the nearest segment hit.
  - `isPointWithinBounds(point, entity)`:
    - Null bounds: inclusive circle test.
    - Outside the circle: false.
    - Otherwise: `bounds.update`, an on-segment test, then the ray-cast through the existing
      `isPointInPolygon`, on LazyLib's point list (every P1, then the last P2).
- `render/Primitives.kt` is new. `drawCircle(cx, cy, r, segments, filled)` uses LazyLib's vertex
  walk (incremental rotation, no draw under 3 segments), drawn with `GL_LINE_LOOP` or
  `GL_TRIANGLE_FAN` in immediate mode. That avoids the client-array path behind "Cannot use Buffers
  when Array Buffer Object is enabled".
- `README.md`:
  - New "Kotlin runtime" section.
  - Contents table rows for the new helpers.
  - Corrected the load-order claim.
- `CHANGELOG.md`: added the `## Unreleased` heading back with the changes.

### Covenant Remnants

- `build.gradle.kts`: the same Kotlin 2.2.20 and `compilerOptions` changes. It compiles against
  `../LemonLib/jars/lib/Kotlin-Runtime.jar`, and the `LazyLib.jar` and `LazyLib-Kotlin.jar`
  `compileOnly` lines are gone.
- `gradle.properties`: the same two property changes as LemonLib.
- Call sites:
  - `utils/Data.kt`: `for (row in data)` (LazyLib's JSON iterator) became an index loop with
    `getJSONObject(i)`.
  - `weapons/EnergyProjectorBeamEffect.kt`: `CollisionUtils.getCollisionPoint` became LemonLib
    `getCollisionPoint`, and `MathUtils.getDistance` became `Misc.getDistance`.
  - `plugins/TorpedoRenderPlugin.kt`: `CollisionUtils.isPointWithinBounds` became LemonLib
    `isPointWithinBounds`.
  - `plugins/FleetSlipspaceJumpRenderPlugin.kt`: `DrawUtils.drawCircle` became LemonLib
    `drawCircle`.
- `CovenantRemnantsPlugin.kt`: removed the "This mod requires LazyLib to work." gate. The MagicLib
  gate stays.
- `mod_info.json`: removed the `lw_lazylib` dependency.
- Docs: `README.md` Requirements (LemonLib added; LazyLib marked "required by MagicLib"),
  `CLAUDE.md` Stack, `planning/ARCHITECTURE.md`, `GRADLE_README.md` (dependency list, Avast note),
  `CHANGELOG.md`.

## Verified

- `gradlew buildMod` in Covenant builds LemonLib through the composite and then Covenant, with no
  LazyLib on the compile classpath. It compiles clean; the only warnings are the existing
  unchecked-cast ones in LemonLib `ReflectionUtils`.
- Both output jars have class files at `major version: 52` (Java 8).
- `CovenantRemnants.jar` has no `org/lazywizard/lazylib` references.
- LemonLib `packageMod` produces a zip with `jars/LemonLib.jar` and `jars/lib/Kotlin-Runtime.jar`
  and no sources jar.
- `cleanMod`, with its doLast run alone, removes `LemonLib.jar` and the stale copies and keeps
  `jars/lib/`.
- The full `cleanMod` failed at Gradle's own `clean`, because something held
  `build/libs/LemonLib-0.1.1.jar` open. That is unrelated to this change.
- `git status` in LemonLib shows both `jars/lib/*.jar` as new files and keeps `LemonLib.jar`
  ignored.

## Build note: Avast HTTPS scanning

The first download of the Kotlin 2.2.20 compiler failed with `PKIX path building failed`. Avast
re-signs Maven Central's certificate with its own root, which Windows trusts and the JDK does not.
The fix for that one build was `JAVA_TOOL_OPTIONS=-Djavax.net.ssl.trustStoreType=Windows-ROOT`,
after `gradlew --stop`. The artifacts are cached now. This is recorded in `GRADLE_README.md`.

## Open items

- **In-game check:** passed 2026-10-12. The developer reports that everything works, and the log
  shows the expected jar order with no linkage errors.
- **Release order:** release LemonLib first, so Covenant's `release.ps1` pins a version that ships
  the runtime.
- **Shadowing risk:** closed the same day by LemonLib's `"sortString": "~LemonLib"`. See
  [lemonlib-sortstring.md](lemonlib-sortstring.md).
- **kotlin-reflect:** shipped, but mostly unusable under the classloader's `java.lang.reflect`
  block.

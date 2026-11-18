# Implementation: Kotlin 2.4.21 runtime and compiler (2026-11-18)

## What happened

- **The 2.5 attempt:** the developer replaced LemonLib's `jars/lib/Kotlin-Runtime.jar` and its sources
  jar with a Kotlin "2.5.0" runtime: stdlib plus coroutines 1.11.0, and no kotlin-reflect.
  - The builds then failed. The 2.2.20 compiler reads stdlib metadata only up to 2.3: "metadata
    version is 2.5.0, … can read versions up to 2.3.0".
  - Maven Central has no Kotlin 2.5.0 final, only 2.5.0-Beta1/Beta2/RC. That jar was therefore
    almost certainly the RC: its `KotlinVersion.CURRENT` says 2.5.0, which RCs report too.
  - Kotlin Gradle plugin 2.5.0-RC refuses Gradle 8.4 and needs 8.14.4+.
  - Plugin 2.4.x still runs on Gradle 8.4, with a "deprecated Gradle version" warning. Plugin
    2.4.0 and 2.4.21 both built all three repos against the 2.5 jar.
- **Developer ruling:** ship stable 2.4.21 instead. The assistant rebuilt the jars, because the
  developer did not know how.
- **kotlin-reflect:** the developer's 2.5 jar had already dropped it, and the 2.4.21 runtime leaves
  it out as well.
  - The developer asked whether to use kotlin-reflect instead of Java reflection. Answer: no.
    kotlin-reflect is built on `java.lang.reflect.Method/Field/Constructor`, which the script
    classloader blocks, so most of it throws in game.
  - LemonLib `ReflectionUtils` (MethodHandles) is the working route.
  - No installed mod references kotlin-reflect classes, and LazyLib's bundled runtime has none
    either.

## What was built

- **`LemonLib/scripts/build-kotlin-runtime.py`** (`--kotlin X --coroutines Y`) downloads
  `kotlin-stdlib` and `kotlinx-coroutines-core-jvm` with their sources jars from Maven Central and
  checks each `.sha1`. It writes `jars/lib/Kotlin-Runtime.jar` and `jars/lib/Kotlin-Runtime-sources.jar`.
  - The output is one flat jar, stdlib first. It drops `module-info`, `META-INF/versions/**`,
    `META-INF/maven/**` and signatures, and writes a minimal manifest. This matches the layout of
    the jar the developer supplied: the same META-INF set and Java 8 class files.
  - The output was content-identical to the jar that was tested.
- **Runtime:** stdlib 2.4.21 + coroutines 1.11.0, whose POM requires stdlib 2.2.20 or newer. That
  is 1862 classes, all class version 52.
- **Sources jar:** it is named `Kotlin-Runtime-sources.jar` again, the tracked name. The supplied
  file was `…-Sources.jar`, a case-only rename that git on Windows would not record.
- **Kotlin plugin:** `kotlin("jvm") version "2.4.21"` in Covenant, LemonLib and StarsectorHarness
  `build.gradle.kts`. Gradle stays 8.4 and the Java 17 build JDK pin is unchanged.
- **Docs:**
  - LemonLib: `README.md` (runtime contents, plugin limit, no reflect, rebuild script),
    `build.gradle.kts` comment, `CHANGELOG.md`.
  - Covenant: `CLAUDE.md` (stack), `GRADLE_README.md`, `planning/ARCHITECTURE.md`,
    `build.gradle.kts` comment, `planning/TODO.md`.
  - Harness: `CHANGELOG.md`.
- **Backup:** the developer's 2.5 jars are saved in the session scratchpad under
  `runtime-2.5-backup/`. They are not in any repo.

## Verification

- **Builds:** Covenant + LemonLib (composite, `--rerun-tasks`) and the harness build pass on Kotlin
  2.4.21. All outputs are class version 52.
- **Kotlin linkage scan:** every Kotlin reference from Covenant, LemonLib, Harness, MagicLib,
  Ashlib, LazyLib, LunaLib, Console, ShipBrowser and PracticeTargets resolves against the 2.4.21
  runtime, with 0 missing classes or members.
  - The new Covenant and LemonLib jars also resolve against the old 2.2.20 runtime. A player
    mixing versions is fine today, so there is no Covenant changelog entry.
- **Game API scan:** clean, and the wrapper still forwards all three default methods.
- **Harness:** `smoke`, `combat` and `compat` (fixture 0.1.1) all PASS. The log shows LemonLib's
  runtime jar loaded ahead of LazyLib's, with no linkage errors.

## Open items

- **Kotlin 2.5:** once 2.5.0 final is on Maven, move all three Gradle wrappers to 8.14.4 or newer,
  then run the script and bump the plugin.
- **Release:** the runtime change sits under LemonLib `## Unreleased`. Covenant pins LemonLib
  0.1.2, so bump both versions when releasing.

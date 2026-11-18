# Implementation — build JDK pinned to Starsector's Java 17 (2026-11-18)

The system Java on the dev machine is JDK 25. It is set as the machine-level `JAVA_HOME`, and its
`bin` is first on `PATH`. Both Covenant Remnants and LemonLib use Gradle wrapper **8.4**, which runs
on Java 8 to 20 only. The developer asked for both builds to use Java 17, from Starsector's bundled
JRE at `<Starsector>/jre`.

## The bundled JRE

- `<Starsector>/jre` holds Azul Zulu **17.0.10**.
- It is a full JDK image: `javac.exe`, `jar.exe`, `javap.exe` and the `jdk.compiler` module are
  all present. Gradle and the Kotlin compiler run on it as-is.
- From either mod folder it sits at `<mod>/../../jre`.

## What was built

Developer choice: the wrapper auto-detects the JRE. Two alternatives were rejected:

- `org.gradle.java.home` in `gradle.properties`, because it needs an absolute user path in the repo.
- A user-level `JAVA_HOME`, because it would affect every Java tool on the account.

Changes:

- **`gradlew.bat`** (both repos, identical): after `APP_HOME` is resolved, `JAVA_HOME` is set to
  the full path of `%APP_HOME%\..\..\jre` when `jre\bin\java.exe` exists there. Otherwise the stock
  `JAVA_HOME` / `PATH` lookup runs unchanged. `setlocal` keeps the override local to the script.
- **`gradlew`** (POSIX, both repos, identical): the same check, accepting `bin/java` or
  `bin/java.exe`, so it works in Git Bash. It exports `JAVA_HOME`.
- **Why the wrapper is enough:**
  - The Gradle client starts on that JDK.
  - With no `org.gradle.java.home` set, the daemon uses the client's JDK.
  - The Kotlin daemon uses the Gradle daemon's JDK.
  - This covers `package_mod.bat`, `scripts/release.ps1` in both repos, and CLI runs.
  - In the composite build (`includeBuild("../LemonLib")`), the root build's wrapper decides the
    JDK for both builds.
- **`.vscode/settings.json`** (Covenant only; LemonLib has no `.vscode`): added
  `java.import.gradle.java.home` with the absolute JRE path. VSCode's Gradle import uses the Tooling
  API, not `gradlew`. Without the setting it would fall back to JDK 25 or the embedded JDK 21, and
  Gradle 8.4 runs on neither.
- **Unchanged:** the bytecode target stays Java 8 (`jvmTarget = 1_8`, `sourceCompatibility = 1_8`),
  and the Gradle version stays 8.4.

## Docs touched

- Covenant: `GRADLE_README.md` (prerequisites, VS Code, troubleshooting), `README.md`
  (Contributing), `planning/ARCHITECTURE.md` (Build & data flow), `CLAUDE.md` (Stack) and
  `planning/TODO.md`.
- Covenant `CHANGELOG.md`: no entry. This is dev tooling, not a player-facing change (ruling
  2026-10-17).
- LemonLib: `README.md` (Building) and `CHANGELOG.md`. The changelog gets an Unreleased entry under
  "Behavioral changes", because LemonLib's changelog already records build tooling for modders.

## Verified (2026-11-18)

- After `gradlew --stop`, `gradlew.bat --version` in both repos reports
  `JVM: 17.0.10 (Azul Systems, Inc. 17.0.10+7-LTS)`. Git Bash `./gradlew --version` reports the same.
- `gradlew.bat buildMod` passes in Covenant, which also builds LemonLib through the composite
  build, and in LemonLib on its own.
- `javap -v` on a class from `CovenantRemnants.jar` and from `LemonLib.jar` reports
  `major version: 52`.
- Built jars are untracked, so git shows only the intended source and doc changes.

## Open items

- **Regenerating the wrapper drops the block.** Running `gradlew wrapper`, for example on a Gradle
  upgrade, rewrites `gradlew` and `gradlew.bat`. Re-add the block afterwards, or replace it with a
  Gradle 8.8+ daemon-JVM criterion (`gradle/gradle-daemon-jvm.properties`).
- **IntelliJ is not pinned.** `.idea/misc.xml` names a global SDK `17`, not a path. Its Gradle JVM
  is set in IntelliJ's own settings.
- **The VSCode path is machine-specific.** Edit `.vscode/settings.json` on another install.
- **LemonLib changes are uncommitted** (see `planning/TODO.md`).

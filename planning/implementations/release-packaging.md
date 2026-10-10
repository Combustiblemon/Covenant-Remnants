# Implementation — release packaging (2026-10-10)

`package_mod.bat` / `package_mod.sh` and Gradle's `packageMod` each had their own file list, and
both lists were wrong:

- The scripts zipped only `data/`, `graphics/` and `mod_info.json`: no jar, no `sounds/`, no
  `.version`. The zip was named `TheCovenant.zip` and had no top folder.
- `packageMod` copied a whitelist without the `.version` file (fixed earlier the same day), took
  every file in `jars/`, `*.stale-*` leftovers included, and never removed files from earlier runs.

## Developer rulings (2026-10-10)

| Topic | Decision |
| --- | --- |
| Approach | Gradle makes the zip; the scripts only call `packageMod`. One file list. |
| Zip name | `Covenant Remnants.zip`, no version in the name, one `Covenant Remnants/` folder inside. |
| Output location | The project root, next to `package_mod.bat`, where the old scripts wrote. It is git-ignored by `*.zip`. It was in `build/` until a same-day follow-up. |
| Extras | No LICENSE. Leave out `graphics/covenant_remnants/fx/shield/noise.png` (unused, kept in the repo for the art pass) and `sounds/plasma_lance/fire_fp_*.ogg` (unused first-person alternates). |

## What ships

- `mod_info.json` and `covenant-remnants.version`.
- `CHANGELOG.md`, the whole file (added in v0.1.1, see below).
- `jars/CovenantRemnants.jar`, and only that jar.
- `data/**`, `graphics/**` and `sounds/**`, minus the two exclusions above. Empty folders are
  dropped.

`data/` keeps the optional-dependency files: `data/console/commands.csv` for Console Commands, and
`data/config/LunaSettings.csv` plus `data/config/version/` for LunaLib. The game ignores them when
those mods are absent.

Nothing else from the repo ships. That leaves out `src/`, `planning/`, `assets/`, `docs/`,
`fx stuff/`, `reports/`, `readme_files/`, `out/`, `build/`, `.claude/`, the Gradle and IDE files,
the other `.md` files and LICENSE.

## How it is wired (`build.gradle.kts`)

- **`runtimeFiles`** — a `copySpec` holding the list above (`includeEmptyDirs = false`).
- **`stageMod`** (`Sync`, depends on `buildMod`) — writes `build/mod_package/Covenant Remnants/`.
  Because it is a `Sync`, files left from earlier runs are removed.
- **`packageMod`** (`Zip`) — `from(stageMod) { into("Covenant Remnants") }` →
  `Covenant Remnants.zip`. It takes only the staged output, so stray files under
  `build/mod_package/` cannot leak in.
- **`package_mod.bat`** — `cd /d "%~dp0"`, then `call "%~dp0gradlew.bat" packageMod`. The file has
  CRLF line endings. The explicit path is needed: with `NoDefaultCurrentDirectoryInExePath` set,
  which is the case in this dev shell and on some systems, `cmd` does not search the current folder,
  so a bare `gradlew.bat` was "not recognized".
- **`package_mod.sh`** — `cd "$(dirname "$0")" && sh ./gradlew packageMod`. Running it through `sh`
  means it also works when the checkout lost `gradlew`'s exec bit.

## Verification done

- `package_mod.sh` and `package_mod.bat` both end with BUILD SUCCESSFUL and exit code 0.
- `Covenant Remnants.zip` has a single top folder `Covenant Remnants/` with 271 files,
  6.4 MB unpacked:
  - `data` 104 (every file in `data/`), `graphics` 106, `sounds` 58;
  - 1 jar, `mod_info.json`, `covenant-remnants.version`.
- No `noise.png`, `fire_fp_*`, `*.stale-*`, `.kt`, `.md`, `.py` or `.kts` in the zip.
- Old flat leftovers of the previous task in `build/mod_package/` were deleted (generated output).

## LemonLib (same day)

The same build system in `../LemonLib` (its own repo). Developer rulings:

- Same shape as Covenant: `stageMod` / `packageMod`, with the zip in the project root.
- **Jar code-only.**
- **Ship the modder docs.**

Changes:

- **`build.gradle.kts`**
  - `buildMod` is code only. The `resources.srcDirs("data")` source set, `from("data")` and
    `from("mod_info.json")` are gone; they had put the data in the jar twice (at the jar root and
    under `data/`).
  - A new `releaseFiles` list: `mod_info.json`, `*.version`, `data/**`, `jars/LemonLib.jar`,
    `README.md` and `docs/**`.
  - `stageMod` writes `build/mod_package/LemonLib/`, and `packageMod` writes `LemonLib.zip` in the
    LemonLib root.
- **New files:** `package_mod.bat` (CRLF, explicit `gradlew.bat` path, with the `pause` the developer
  added to Covenant's) and `package_mod.sh`.
- **`gradlew.bat`** — copied from Covenant. LemonLib had only the Unix `gradlew`. Both use the same
  Gradle 8.4 wrapper.
- **`.gitignore`** — `/LemonLib.zip` added. The file also lacked a final newline.
- **`jars/LemonLib.jar`** — untracked. It is build output, but it had been committed despite the
  existing `/jars` ignore rule, so every rebuild showed it as modified. `git rm --cached` keeps the
  file on disk. Nothing else under `jars/` or `build/` is tracked in either repo.
- **`README.md`** — the Building section now covers `packageMod`. It also fixes the stale claim that
  dependent mods compile against `jars/LemonLib.jar`; they compile against the classes through the
  composite build.

Verification:

- `package_mod.sh` and `package_mod.bat` both end with BUILD SUCCESSFUL and exit code 0.
- `LemonLib.zip` has 12 files under `LemonLib/`: the jar, `mod_info.json`, `lemonlib.version`,
  2 data CSVs, the README and 6 docs.
- The jar now holds only classes plus `META-INF`.
- Covenant's `buildMod`, which builds LemonLib through the composite build, still succeeds.

## v0.1.1: version bump and changelog (2026-10-10)

Developer rulings:

- v0.1.0 is the 19:58 zip of 2026-10-10, which already held the tester cache, the Huragok Slipspace
  Engram and the max-burn change. v0.1.1 covers the UI-scaling work
  ([shield-ui-scaling-fix.md](shield-ui-scaling-fix.md)). On 2026-10-11, before release, the
  developer added the save-load crash fix
  ([slipspace-stream-load-crash-fix.md](slipspace-stream-load-crash-fix.md)).
- `CHANGELOG.md` starts at v0.1.1, with no back-filled v0.1.0 entry. New versions go on top. The
  whole file ships in the zip.
- The entries follow the changelog style in [../../CLAUDE.md](../../CLAUDE.md): categories by
  impact (breaking changes, new features, behavioral changes, bug fixes), and every bullet in
  "action + context + benefit" form, in a user-facing voice.

Changes:

- **Version 0.1.1** set in three places: `mod_info.json` (`version`), `covenant-remnants.version`
  (`patch` 1) and `build.gradle.kts` (`version`, which goes into the jar manifest's
  `Implementation-Version`).
- **`CHANGELOG.md`** added at the repo root.
- **`runtimeFiles`** now includes `CHANGELOG.md`.

Verification:

- `gradlew packageMod` succeeds.
- `Covenant Remnants.zip` holds 321 files (the 320 from before plus `CHANGELOG.md`).
- `mod_info.json`, the `.version` file and the jar manifest all read 0.1.1.
- None of the excluded files are in the zip.

## v0.1.2: version bump (2026-10-11)

The developer released v0.1.1 at commit `ddac966`, with the UI-scaling fixes and the save-load
crash fix. The two slipspace-exit fixes that came after it (background, music; see
[slipspace-exit-music-background-fix.md](slipspace-exit-music-background-fix.md)) move to a new
v0.1.2 entry at the top of `CHANGELOG.md`. The v0.1.1 entry is unchanged from the release. The
version is 0.1.2 in `mod_info.json`, `covenant-remnants.version` (`patch` 2) and `build.gradle.kts`.

## Open items

- **Version Checker:** after merging to `master`, the online master file
  (`raw.githubusercontent.com/Combustiblemon/Covenant-Remnants/master/covenant-remnants.version`)
  must carry patch 1, or players on 0.1.0 are not told about 0.1.1.
- When a file is added to the repo but not used at runtime, either add an `exclude` to
  `runtimeFiles` or keep it outside `data/`, `graphics/` and `sounds/`.
- Release check: unzip into a clean `mods/` folder and start the game once.

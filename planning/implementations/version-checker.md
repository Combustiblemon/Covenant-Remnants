# Implementation — Version Checker support (2026-10-10)

LunaLib's built-in Version Checker now picks up the mod. It already had a `.version` file
(`covenant-remnants.version`, from the initial commit), but not the CSV row that registers it.

## What Version Checker reads

`lunalib.backend.ui.versionchecker.VCModPlugin`:

- It reads the merged spreadsheet `data/config/version/version_files.csv` (column `version file`)
  from every enabled mod.
- It loads each listed file with `Global.getSettings().loadJSON`. A parse error throws, so a broken
  `.version` file stops the game at startup.
- Enabled mods without a row are listed as "unsupported" in its panel.
- The master file at `masterVersionFile` is fetched in the background and its `modVersion` compared
  with the local one.

## Files

- `data/config/version/version_files.csv` — new: header `version file`, row
  `covenant-remnants.version`.
- `covenant-remnants.version` — unchanged:
  - `modName` "Covenant Remnants", `modVersion` 0.1.0 (matches `mod_info.json`), `modThreadId` 0;
  - `masterVersionFile` =
    `https://raw.githubusercontent.com/Combustiblemon/Covenant-Remnants/master/covenant-remnants.version`.

- `build.gradle.kts` `packageMod` — `include("*.version")` added. A bug fix: the whitelist
  shipped `data/**` (which holds the CSV) but not the root `.version` file. A packaged release would
  therefore have thrown at startup under LunaLib.

## LemonLib (same day)

The same support for `../LemonLib` (its own repo, `Combustiblemon/LemonLib`, branch `main`):

- `LemonLib/lemonlib.version` — new:
  - `modName` "LemonLib", `modVersion` 0.1.0 (matches its `mod_info.json`), `modThreadId` 0;
  - `masterVersionFile` =
    `https://raw.githubusercontent.com/Combustiblemon/LemonLib/main/lemonlib.version`.
  - The file name and the raw-GitHub-on-default-branch URL follow Covenant's pattern.
- `LemonLib/data/config/version/version_files.csv` — new: `lemonlib.version`.
- LemonLib has no packaging task (it ships as its folder), so there is no whitelist to fix.
- The file parses with the game's `org.json`.
- Its master URL is also unreachable: the committed `README.md` on `main` returns 404, so the repo
  is private. Same open item as Covenant.

## Verification done

- The `.version` file parses with the game's own `org.json` (`starsector-core/json.jar`), with `#`
  comments stripped as the game does; the trailing comma is accepted.
- `gradlew packageMod`: `build/mod_package/` now holds `covenant-remnants.version` next to
  `mod_info.json`.
- `masterVersionFile` returned **HTTP 404** on 2026-10-10. The file exists on the local `master`,
  so the GitHub repo is private or `master` is not pushed. Until the URL resolves, Version Checker
  lists the mod but cannot report updates.

## In-game checklist

1. The game starts, and there is no `Failed to parse version file` in `starsector.log`.
2. LunaLib's version panel lists Covenant Remnants and LemonLib, not under unsupported mods.
3. Once the master URL resolves: bump `modVersion` in the master copy only, and the panel shows an
   update.

## Open items

- Make `masterVersionFile` reachable, for both mods: public repos with `master` (Covenant) / `main`
  (LemonLib) pushed, or another raw host. This is the developer's call.
- Optional fields, once they exist: `modThreadId` (forum thread), `modNexusId`, and LunaLib's
  `directDownloadURL` / `changelogURL`.
- On each release, bump `modVersion` in both `mod_info.json` and the `.version` file, then push the
  master copy.

# Implementation — LemonLib sortString (2026-10-12)

This follows [kotlin-runtime-lemonlib.md](kotlin-runtime-lemonlib.md). LemonLib's Kotlin runtime
only beats other bundled runtimes while LemonLib sorts last. Until now that held by luck of the
name: "LemonLib" comes after "LazyLib", but any mod named after it would have won.

The developer asked for a `sortString`, with the intent that "LemonLib needs to be available when
loading the rest of our mods".

## How the order works (decompiled, 0.98a-RC8)

- **`StarfarerLauncher$2` sorts the enabled mods.** The key is `sortString`, or `name` when it is
  empty. The comparison is a case-sensitive `String.compareTo`.
- **That sorted list does two jobs.** It is the "Running with the following mods (in order of
  priority)" list. Data and settings also load in that order.
- **Jars are added to `ScriptStore`'s list in reverse.** So the mod that sorts **last** is searched
  **first**, and it wins any duplicate class.
- **`ScriptStore` builds a single `URLClassLoader` from all those jars at once**, before it loads any
  script or ModPlugin. Every mod's classes, LemonLib's included, are therefore reachable from every
  other mod whatever the order. Order only matters for duplicate classes.
- **ModPlugin order is plain `name` order** (`ModManager.getEnabledModPlugins`). `sortString` does
  not change it, and neither do `dependencies`.

## What was built

- **`LemonLib/mod_info.json`:** added `"sortString": "~LemonLib"`. `~` (0x7E) sorts after every
  letter, digit and common punctuation, so LemonLib sorts last and its jars come first.
- **Check:**
  - The launcher comparator was run in Java over all 36 installed mods. LemonLib came last, after
    "zz GraphicsLib".
  - No installed mod has a `sortString`, ours included: Achievement Pack, Covenant Remnants,
    Memes and Dreams, Planetfall, ShadyLib and ShieldLib. All of them stay behind LemonLib.
  - Only Planetfall, of our other mods, uses Kotlin, and it already depends on LemonLib.
- **Side effect:**
  - LemonLib now shows last in the priority log, and its data loads last.
  - Its only data files are merged CSVs (`data/config/version/version_files.csv` and
    `data/console/commands.csv`), so loading them last changes nothing.
  - The launcher mod list is unaffected; `GLModPickerV2` does not read `sortString`.
  - LemonLib's `release.ps1` rewrites only the `"version"` keys, so the new line is safe.
- **Docs:**
  - LemonLib `README.md` ("Which copy wins" and the load-order paragraph) and `CHANGELOG.md`
    (Unreleased).
  - Covenant `CLAUDE.md` (Kotlin runtime ruling) and `planning/TODO.md`.

## Rule going forward

None of our mods may set a `sortString` that sorts after `~LemonLib`. Third-party mods could still
win by using a later string, for example one starting with `~~` or a non-ASCII character. No such
mod is installed.

## Verified in game (2026-10-12)

`starsector.log` lists LemonLib last under "in order of priority". `LemonLib.jar` and
`lib/Kotlin-Runtime.jar` are added before LazyLib's jars. The developer reports that everything
works.

# Implementation — mod jar holds code only (2026-10-02)

`build.gradle.kts` packed every data file and texture into `jars/CovenantRemnants.jar` twice: the
`main` source set listed `data` and `graphics` as resource folders (their contents landed at the
jar root, e.g. `config/…`, `fx/…`), and `buildMod` copied `data/` → `data/`, `graphics/` →
`graphics/` and `mod_info.json` in again. The jar had grown to 7.1 MB, 0.8 MB of it code.

Starsector never read those copies: its file loader (starsector-knowledge, 0.98a-RC8,
`com.fs.util.C`) searches the mod folders first, then core, and the classpath only as a last
fallback; every file exists in the mod folder. The mod's own code loads files through
`SettingsAPI` (shaders via `loadText`), not the classpath.

## Change (developer go-ahead 2026-10-02)

- `build.gradle.kts`: removed the `resources { srcDirs("data", "graphics") }` block and the
  `from("data")`, `from("graphics")`, `from("mod_info.json")` copies in `buildMod`. The jar now holds
  the compiled classes only (0.43 MB).
- `packageMod` ships `data/`, `graphics/`, `jars/` and `mod_info.json` as folders. Bug fixed at
  the same time: it left out `sounds/` (fleet jump, shield pop, torpedo sounds) — now included.
- Docs: `planning/ARCHITECTURE.md` build section.

## Check

- In-game: the mod loads as before (textures, sounds, shaders, CSVs all come from the mod folder).

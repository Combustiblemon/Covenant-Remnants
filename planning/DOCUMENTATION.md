# Documentation & structure rules

Conventions for this repo — how the docs fit together, how content is named, and where things live.
Project context: [../CLAUDE.md](../CLAUDE.md). Architecture: [ARCHITECTURE.md](ARCHITECTURE.md).
Roadmap: [PLAN.md](PLAN.md).

## The four docs

| File | Holds | Does not hold |
| --- | --- | --- |
| [../CLAUDE.md](../CLAUDE.md) | Project context, fixed design decisions, AI-assistant working rules. Lives at repo root. | Implementation detail, task lists. |
| `planning/DOCUMENTATION.md` (this file) | Structure and naming conventions, where things live. | Design rationale, roadmap. |
| [ARCHITECTURE.md](ARCHITECTURE.md) | How the mod is actually built — layout, entry point, subsystems, build flow. | Future plans, opinions. |
| [PLAN.md](PLAN.md) | Roadmap, current status, open questions, TODOs. | Settled decisions (those go in CLAUDE.md). |

**`planning/` folder.** `CLAUDE.md` stays at repo root (it is the entry doc). Everything else —
`DOCUMENTATION.md`, `ARCHITECTURE.md`, `PLAN.md`, **and all future planning/design/spec docs** —
lives in `planning/`. New plan for a feature? Put it in `planning/` and link it from `PLAN.md`.

**`docs/` folder.** Developer-facing **usage documentation for reusable systems** (how-to guides,
API contracts, worked examples) lives in `docs/<system>/` of the repo that owns the code. Shared
systems live in the **LemonLib** library mod (`../../LemonLib/`) and are documented there — e.g.
the shader composition docs are at `LemonLib/docs/shader-composition/`. Planning/design history
stays in this repo's `planning/` (see
[implementations/lemonlib-extraction.md](implementations/lemonlib-extraction.md)).

**Rule: no dead cross-links.** If a doc links another doc or file, that target must exist. When you
add a new doc, wire it into this table. Links from inside `planning/` to root files use `../`.

## Change docs (required)

Beyond the four docs above, every substantive change is recorded:

| Path | Holds |
| --- | --- |
| `planning/implementations/<feature>.md` | **One file per implementation.** What was built, files touched, key decisions, open items. Add one whenever you build a feature. |
| `planning/TODO.md` | Actionable checklist of what is left to do. Tick items off and add new ones as work lands. |

`PLAN.md` stays the high-level roadmap/status/open-questions; `TODO.md` is the granular, checkable
task list. This documentation duty is a fixed AI-assistant rule — see
[../CLAUDE.md](../CLAUDE.md) ("Document every change").

## ID naming

- **Canonical prefix: `covenant_remnants_`** (underscore-separated). All new ship / weapon / variant
  / system / hullmod / ability ids use it.
- **Legacy prefix `covenantremnants_`** (no separators) — fully migrated; no content ids use it.
  Do not create new ids with it.
- The mod **id** in `mod_info.json` is `combustiblelemon_covenant_remnants` — separate from content
  id prefixes; do not change it.
- Match id to filename: `covenant_remnants_ccs.ship` defines hull id `covenant_remnants_ccs`.

## Content conventions

- **Ships** (`data/hulls/*.ship` + rows in `data/hulls/ship_data.csv`) declare
  `shield type = PHASE` with `defense id = covenant_remnants_covenantshield` — Covenant defense is
  the mod's **custom shield system** (hullmod + shader + render plugins), not a vanilla shield.
  See [ARCHITECTURE.md](ARCHITECTURE.md).
- **Fighters** are defined as `wing*` **ship hulls** + a `.variant` + a row in
  `data/hulls/wing_data.csv`. There are **no** `.wing` files — do not add them.
- **Weapons** (`data/weapons/*.wpn` + `weapon_data.csv`, projectiles in `data/weapons/proj/*.proj`)
  are plasma-family and energy. Use vanilla weapons of the same type/size as the stat reference
  (see [design_document.md](../design_document.md)); justify any large deviation.
- **Ship systems** (`data/shipsystems/*.system`) — Covenant-specific systems (e.g. energy projector
  as a system per [balance.md](../balance.md)).
- **Shaders** ship as a `.frag` + `.vert` pair in `data/shaders/`, loaded by a matching Kotlin class
  in `src/org/combustiblelemon/covenantremnants/utils/render/`. Keep the pair and its loader named
  together (e.g. `shieldShader.frag`/`.vert` ↔ `ShieldShader.kt`). A system with several shader
  pairs keeps them in `data/shaders/<system>/` with their loaders in a `<system>/` subpackage of
  `utils/render/` (`ruptureSphere.*` ↔ `rupture/RuptureSphereShader.kt`, `ruptureSprite.*` ↔
  `rupture/RuptureSpriteShader.kt`). Exception: **composed shaders** — per-layer `.glsl` modules
  under `data/shaders/<system>/` assembled into one program by the owner class
  (`ShieldShader.kt` ↔ `data/shaders/shield/`), with the layer classes in a `<system>/` subpackage
  of `utils/render/` (see
  [implementations/shield-shader-composition.md](implementations/shield-shader-composition.md)).
- **All mod graphics** live under `graphics/covenant_remnants/`: ships, weapons, projectile and
  missile sprites (`missiles/`), backgrounds, misc. Nothing goes directly under `graphics/` (ruling
  2026-10-10).
- **VFX textures** go under `graphics/covenant_remnants/fx/<effect>/` and are registered in
  `data/config/settings.json` (`graphics` → `covenant_remnants`). Halo game assets may be shipped
  (developer decision 2026-10-01); note the source tag path where the texture is used.
- **Reference material** for art/VFX (screenshots, extracted video frames) goes under
  `assets/reference/<topic>/` — scratch, not shipped, and gitignored (local only).
- **Descriptions** live in `data/strings/descriptions.csv`. Every hull, fighter hull, weapon and
  ship system gets a row; ship systems follow vanilla's layout (text1 description, text2 type,
  text3 tooltip summary, text5 Codex "Effects" with `{{}}` highlights). Hull and fighter texts
  describe the stock fit and system. Edit the file line by line: it quotes empty fields `""`, so a
  full CSV rewrite reformats every row.
- **No em dashes in player-facing text** (developer ruling 2026-10-17). No en dashes either, and no
  spaced hyphens (" - ") standing in for a dash; use commas, colons or a new sentence. Vanilla
  never uses them.
- **Codex lore**:
  - Hullmod lore paragraphs are CUSTOM rows `<hullmod id>_codex`, shown only in the Codex. No `%`
    in them.
  - The Covenant Archive pages are `data/codex/covenant_remnants_archive.txt`, in vanilla Codex
    markup, written in the Sector analyst voice.
  - Internal content that must not appear in the Codex gets the `hide_in_codex` tag. See
    [implementations/descriptions-codex-pass.md](implementations/descriptions-codex-pass.md).

## Kotlin source layout

Root package `org.combustiblelemon.covenantremnants`, organised **by feature/role**, not by type:

- `ai/` — weapon/ship AI plugins.
- `campaign/` — `abilities/`, `entities/`, `systems/`, `terrain/` for campaign-layer content.
- `commands/` — Console Commands.
- `hullmods/` — hullmod effects (shields, armor, crew, systems).
- `plugins/` — combat/render `EveryFrame`-style plugins.
- `systems/` — ship-system effect classes.
- `utils/` — `render/` (shaders), `reflection/`, `extensions/`, shared helpers.
- `weapons/` — weapon effect classes.

Put a new class in the package that matches its role. The `modPlugin` entry class
`CovenantRemnantsPlugin` stays at the package root.

## Shipped vs scratch

- **Shipped** (goes into the built mod): `data/`, `graphics/`, `src/`, `jars/`, `mod_info.json`,
  `CHANGELOG.md`.
- **`CHANGELOG.md`** (repo root, shipped in the release zip): player-facing changes per version,
  newest first. Style rules are in [../CLAUDE.md](../CLAUDE.md) ("Changelog style"). Bump the
  version in `mod_info.json`, `covenant-remnants.version` and `build.gradle.kts` together.
- **Scratch / reference / working art** (NOT shipped, do not depend on these at runtime):
  `assets/`, `fx stuff/`, `readme_files/`, `temp/`, `temp_data/` (vanilla-reference copies of
  `config/`, `hullmods/`, `shipsystems/`, `weapons/`).
- **Dev scripts** (NOT shipped): `scripts/` (`release.ps1`) and the root wrappers `release.bat`,
  `package_mod.bat` / `.sh`. Releasing: `release.bat vX.Y.Z` on `master` bumps the three version
  files above plus `gradle.properties` and rolls `CHANGELOG.md` — do not bump by hand. See
  [implementations/release-script.md](implementations/release-script.md). `mod_info.json` pins
  the LemonLib dependency version (ruling 2026-10-12); the release script sets the pin from
  `../LemonLib/mod_info.json` on every release, so release LemonLib first.
- **Build output** (regenerated, not authored): `build/`, `bin/`, `out/`.

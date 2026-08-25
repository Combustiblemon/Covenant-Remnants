# Architecture

How Covenant Remnants is built. Project context: [CLAUDE.md](../CLAUDE.md). Conventions:
[DOCUMENTATION.md](DOCUMENTATION.md). Roadmap: [PLAN.md](PLAN.md).

## Repo layout

| Path | Contents |
| --- | --- |
| `mod_info.json` | Mod manifest — id, version, `modPlugin`, dependencies, jar list. |
| `data/` | Game content: `hulls/`, `weapons/` (+ `proj/`), `variants/`, `shipsystems/`, `hullmods/`, `campaign/`, `shaders/`, `config/`, `console/`. |
| `graphics/` | Sprites/art: `covenant_remnants/`, `ships/`, `weapons/`, `missiles/`, `fx/`, `hullmods/`, `icons/`, `portraits/`, `misc/`. |
| `src/org/combustiblelemon/covenantremnants/` | Kotlin source (see below). |
| `jars/CovenantRemnants.jar` | The single compiled mod jar (built by Gradle). |
| `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `gradlew*`, `gradle/` | Gradle build. |
| `package_mod.bat` / `.sh` | Packaging helpers. |
| `assets/`, `fx stuff/`, `temp/`, `temp_data/`, `readme_files/` | Scratch / reference (not shipped). |
| `build/`, `bin/`, `out/`, `.idea/`, `.vscode/`, `.run/` | Build output & IDE config (not authored). |

## Manifest

`mod_info.json`:

- id `combustiblelemon_covenant_remnants`, name `Covenant Remnants`, author `Combustiblelemon`,
  version `0.1.0`, gameVersion `0.98a`.
- `modPlugin`: `org.combustiblelemon.covenantremnants.CovenantRemnantsPlugin`.
- jars: `jars/CovenantRemnants.jar`.
- **Declared dependencies:** `MagicLib`, `lw_lazylib` (LazyLib).

## Entry point — `CovenantRemnantsPlugin`

`src/.../CovenantRemnantsPlugin.kt` (extends `BaseModPlugin`):

- On application load: verifies MagicLib and LazyLib are enabled; loads `shield_data.csv` (custom
  per-ship shield parameters).
- On new game: generates the slipspace star system (`generateSlipspaceSystem()`).

## Subsystems

Each subsystem spans Kotlin (`src/`), data (`data/`), and — where it renders — a shader
(`data/shaders/` + a `utils/render/` loader).

### Custom energy shields

Covenant ships use `shield type NONE` and get defense from a custom shield instead:

- Hullmod: `hullmods/CovenantShield.kt`; stats via `systems/CovenantShieldsStats.kt`; per-ship data
  from `shield_data.csv` (loaded at app start).
- AI: `ai/CovenantShieldsAI.kt`.
- Render: `plugins/CovenantShieldsRenderPlugin.kt`, `plugins/CovenantShieldsUIRenderPlugin.kt`,
  `plugins/CovenantShieldsUIRenderer.kt`, using `utils/render/ShieldShader.kt` — composed from
  per-layer `ShieldLayer` classes in `utils/render/shield/` and GLSL modules in
  `data/shaders/shield/` (see
  [implementations/shield-shader-composition.md](implementations/shield-shader-composition.md)).

### Slipspace (campaign travel)

- Ability: `campaign/abilities/SlipspaceJump.kt` — declared in `data/campaign/abilities.csv` as
  `covenant_remnants_slipspace_jump` (a Transverse-Jump analog); helper `utils/abilities/Slipspace.kt`.
- World: `campaign/systems/SlipspaceSystem.kt` (+ `scripts/SlipspaceSystemEFS.kt`),
  entity `campaign/entities/SlipspacePortalEntity.kt`, drive `systems/SlipspaceDrive.kt`.
- Render: `plugins/SlipspaceDriveRenderPlugin.kt`, `utils/render/SlipspacePortalEntityShader.kt`
  (`data/shaders/slipspacePortalEntityShader.frag`/`.vert`).
- Console commands: `commands/RegenerateSlipspace.kt`, `commands/DebugHook.kt`.

### Weapons

- `weapons/EnergyProjectorEffect.kt` — energy projector (`covenant_remnants_energyprojector`).
- Plasma family (`.wpn` in `data/weapons/`): artillery, beam, cannon, lance, missile, repeater,
  barrage; AI `ai/PlasmaBarrageAI.kt`.

### Hullmods

`hullmods/NanolaminateArmor.kt`, `hullmods/HuragokCrew.kt`, `hullmods/CovenantSystems.kt`,
`hullmods/HullmodManager.kt`.

### Shader framework

`utils/render/Shader.kt` (base), `MinimalShader.kt` (simple example), `PolygonShader.kt`; plus the
subsystem shaders above. Reflection helper: `utils/reflection/ReflectionUtils.kt`. Misc:
`utils/Utils.kt`, `utils/Data.kt`, `utils/CubicCurve2D.kt`, `utils/extensions/CampaignFleetAPI.kt`.

## Build & data flow

Gradle Kotlin DSL, Kotlin JVM 2.1.0, Java 8 target (`build.gradle.kts`):

- **sourceSets:** `src` → Kotlin/Java classes; `data` + `graphics` → resources.
- **compileOnly (NOT declared runtime deps):** Starsector core jars (`../../starsector-core/*`,
  incl. `starfarer.api.jar`, obf jars, `json.jar`, `lwjgl`), LazyLib, MagicLib, LunaLib,
  Console Commands, log4j 1.2.17.
- **Tasks:** `buildMod` (compile → `jars/CovenantRemnants.jar`), `cleanMod`,
  `packageMod` (assemble `build/mod_package/` → `Covenant Remnants.zip`).

> **Dependency gap to know:** `mod_info.json` declares only MagicLib + LazyLib, but the code is
> built against LunaLib and Console Commands too (and GraphicsLib is wired as an IDE-only classpath
> convenience via `.idea/libraries/Graphics.xml`, not a real dependency). Whether LunaLib / Console
> Commands should be required or optional runtime deps is an open item — see [PLAN.md](PLAN.md).

# Architecture

How Covenant Remnants is built. Project context: [CLAUDE.md](../CLAUDE.md). Conventions:
[DOCUMENTATION.md](DOCUMENTATION.md). Roadmap: [PLAN.md](PLAN.md).

## Repo layout

| Path | Contents |
| --- | --- |
| `mod_info.json` | Mod manifest — id, version, `modPlugin`, dependencies, jar list. |
| `data/` | Game content: `hulls/`, `weapons/` (+ `proj/`), `variants/`, `shipsystems/`, `hullmods/`, `campaign/`, `shaders/`, `config/`, `console/`. |
| `graphics/` | Sprites/art: `covenant_remnants/`, `ships/`, `weapons/`, `missiles/`, `fx/`, `hullmods/`, `icons/`, `portraits/`, `misc/`. |
| `sounds/` | Sound files (ids in `data/config/sounds.json`): `fleet_slipspace_jump/` (Halo: Reach-derived). |
| `src/org/combustiblelemon/covenantremnants/` | Kotlin source (see below). |
| `jars/CovenantRemnants.jar` | The single compiled mod jar (built by Gradle). |
| `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `gradlew*`, `gradle/` | Gradle build. |
| `package_mod.bat` / `.sh` | Packaging helpers. |
| `assets/`, `fx stuff/`, `temp/`, `temp_data/`, `readme_files/` | Scratch / reference (not shipped). `assets/reference/<topic>/` holds VFX reference frames. |
| `build/`, `bin/`, `out/`, `.idea/`, `.vscode/`, `.run/` | Build output & IDE config (not authored). |

## Manifest

`mod_info.json`:

- id `combustiblelemon_covenant_remnants`, name `Covenant Remnants`, author `Combustiblelemon`,
  version `0.1.0`, gameVersion `0.98a`.
- `modPlugin`: `org.combustiblelemon.covenantremnants.CovenantRemnantsPlugin`.
- jars: `jars/CovenantRemnants.jar`.
- **Declared dependencies:** `combustiblelemon_lemonlib` (LemonLib), `MagicLib`, `lw_lazylib` (LazyLib).

## Entry point — `CovenantRemnantsPlugin`

`src/.../CovenantRemnantsPlugin.kt` (extends `BaseModPlugin`):

- On application load: verifies MagicLib and LazyLib are enabled; loads `shield_data.csv` (custom
  per-ship shield parameters).
- On new game: generates the slipspace star system (`generateSlipspaceSystem()`).

## Subsystems

Each subsystem spans Kotlin (`src/`), data (`data/`), and — where it renders — a shader
(`data/shaders/` + a `utils/render/` loader).

### Custom energy shields

Covenant combat hulls use `shield type PHASE` with `defense id = covenant_remnants_covenantshield`.
So the custom shield is a ship system in the phase-cloak slot (`ship.phaseCloak`), and `ship.system`
stays free for the hull's own ship system. Defense comes from this custom shield instead of a
vanilla one:

- Hullmod: `hullmods/CovenantShield.kt`; stats via `systems/CovenantShieldsStats.kt`; per-ship data
  from `shield_data.csv` (loaded at app start). Shield damage honours the vanilla
  `shieldDamageTakenMult` stat, so shield-damage modifiers (skills, CR, systems) apply. Every
  source damages the shield equally: no damage-type multipliers, no per-type shield stats
  (developer ruling 2026-10-01).
- AI: `ai/CovenantShieldsAI.kt`.
- Fortress Shield (vanilla `fortressshield` on capital1): its damage reduction arrives through
  `shieldDamageTakenMult`. `systems/FortressShield.kt` holds the system id, helpers and tuning
  constants. `CovenantShieldsStats` gates it on the shield being up and freezes regen while it's
  active. `ai/FortressShieldAI.kt` (run from `CovenantShieldsAI`) toggles it from an
  incoming-damage estimate. The `fortress` shield layer and a colour tint show it. See
  [implementations/fortress-shield.md](implementations/fortress-shield.md).
- Render: `plugins/CovenantShieldsRenderPlugin.kt`, `plugins/CovenantShieldsUIRenderPlugin.kt`,
  `plugins/CovenantShieldsUIRenderer.kt`, using `utils/render/ShieldShader.kt` — composed from
  per-layer `ShaderLayer` classes in `utils/render/shield/` and GLSL modules in
  `data/shaders/shield/` (fill, border, sweep, ripple, honeycomb, fortress, debug), on **LemonLib**'s
  composition engine
  (`org.combustiblelemon.lemonlib.render.composition`; usage docs in
  `LemonLib/docs/shader-composition/`). See
  [implementations/shield-shader-composition.md](implementations/shield-shader-composition.md)
  and [implementations/lemonlib-extraction.md](implementations/lemonlib-extraction.md).

### Slipspace (campaign travel)

- Ability: `campaign/abilities/SlipspaceJump.kt` — declared in `data/campaign/abilities.csv` as
  `covenant_remnants_slipspace_jump` (a Transverse-Jump analog); helper `utils/abilities/Slipspace.kt`.
- World: `campaign/systems/SlipspaceSystem.kt` (+ `scripts/SlipspaceSystemEFS.kt`),
  entity `campaign/entities/SlipspacePortalEntity.kt`, drive `systems/SlipspaceDrive.kt`
  (single-ship combat jump, `covenant_remnants_slipspacedrive`, on frigate1/frigate3/destroyer2).
- Render: `plugins/SlipspaceDriveRenderPlugin.kt`, `utils/render/SlipspacePortalEntityShader.kt`
  (`data/shaders/slipspacePortalEntityShader.frag`/`.vert`).
- Console commands: `commands/RegenerateSlipspace.kt`, `commands/DebugHook.kt`,
  `commands/CovenantGiveAll.kt`, `commands/FleetJumpTune.kt` (registered in
  `data/console/commands.csv`). `ShaderLayers` lives in LemonLib and is registered by LemonLib's
  own `commands.csv`.

### Fleet Slipspace Jump (supercap1)

The CAS-class Assault Carrier's unique system, `covenant_remnants_fleet_slipspace_jump`
(`data/shipsystems/`, `STAT_MOD` + `CUSTOM` AI). Jumps everything inside a radius of the carrier —
ships of any side, fighters, shots, hulks, asteroids — to the destination in one frame.

- `systems/FleetSlipspaceJump.kt` — system id, every tunable (live via `FleetJumpTune`), per-combat
  shared state (AI→script destination handoff, in-transit set, the one render plugin), shared
  helpers (grabbable / shield-up / map / landing-spot checks).
- `systems/FleetSlipspaceJumpStats.kt` — stats script: usability + info text, timing overrides,
  starts a `systems/FleetSlipspaceJumpSession.kt` per activation. The session is an every-frame
  plugin: holds the carrier, phases/fades the cargo during the fade, executes the jump (carrier
  first, nudged landing spots, offsets preserved, rupture damage, flux), restores on abort.
- AI: `ai/FleetSlipspaceJumpAI.kt` (pull the fight home → strand enemy heavies → regroup).
- Player activation: `plugins/FleetSlipspaceJumpTargetingPlugin.kt` (one per combat;
  `processInputPreCoreControls`, so it works paused): system key primes, left click confirms the
  cursor point, right click cancels; blocks the engine's own `USE_SYSTEM` for the player's CAS.
- Render: `plugins/FleetSlipspaceJumpRenderPlugin.kt` (one per combat; aiming circles while primed
  and all live ruptures) → `utils/render/rupture/RuptureRenderer.kt`, a remake of the Halo: Reach
  slipspace bomb: `RuptureSphereShader` (`data/shaders/rupture/ruptureSphere.*`, analytic spheres)
  and `RuptureSpriteShader` (`ruptureSprite.*`, batched particles + heat haze over a screen copy);
  `RuptureEffect.kt` is the per-rupture timeline, driven by Reach's curves (`ReachCurves.kt`,
  generated) and textures (`graphics/fx/slipspace_rupture/`, ids in `data/config/settings.json`).
- See [implementations/fleet-slipspace-jump.md](implementations/fleet-slipspace-jump.md).

### Weapons

- `weapons/EnergyProjectorEffect.kt` — energy projector (`covenant_remnants_energyprojector`).
- Plasma family (`.wpn` in `data/weapons/`): artillery, beam, cannon, lance, missile, repeater,
  barrage; AI `ai/PlasmaBarrageAI.kt`.

### Hullmods

`hullmods/CovenantSystems.kt`, `hullmods/NanolaminateArmor.kt`, `hullmods/CovenantShield.kt`,
`hullmods/HullmodManager.kt` (hidden helper: swaps hull spec for the Covenant shield),
`hullmods/HuragokCrew.kt`, `hullmods/PlasmaCapacitors.kt`, `hullmods/SlipspaceTuning.kt`,
`hullmods/SlipspaceDriveHullmod.kt` (flavor marker).

Incompatibility system: generic mechanism lives in **LemonLib**
(`org.combustiblelemon.lemonlib.hullmods` — `HullmodBlocker`, `HullmodIncompatibilityWrapper`,
stateless `HullmodIncompatibilityInstaller`; docs at `LemonLib/docs/hullmod-incompatibility/`).
This mod keeps only the registry, `hullmods/CovenantIncompatibilities.kt`, fed to the installer
from the mod plugin (`onApplicationLoad` install, `onGameLoad` re-verify). See
[implementations/vanilla-hullmod-incompatibility.md](implementations/vanilla-hullmod-incompatibility.md).

### Shader framework

`utils/render/Shader.kt` (base), `MinimalShader.kt` (simple example), `PolygonShader.kt`; plus the
subsystem shaders above. Composed systems registered with LemonLib's `ShaderLayers` command:
`shield` (ShieldShader). Reflection helper: `org.combustiblelemon.lemonlib.reflection.ReflectionUtils`
(in LemonLib). Misc: `utils/Utils.kt`, `utils/Data.kt`, `utils/CubicCurve2D.kt`,
`utils/extensions/CampaignFleetAPI.kt`.

## Build & data flow

Gradle Kotlin DSL, Kotlin JVM 2.1.0, Java 8 target (`build.gradle.kts`):

- **sourceSets:** `src` → Kotlin/Java classes; `data` + `graphics` → resources.
- **compileOnly:** Starsector core jars (`../../starsector-core/*`, incl. `starfarer.api.jar`, obf
  jars, `json.jar`, `lwjgl`), LemonLib (composite build — `settings.gradle.kts`
  `includeBuild("../LemonLib")`, resolved to LemonLib's classes), LazyLib, MagicLib, LunaLib,
  Console Commands, log4j 1.2.17.
- **Tasks:** `buildMod` (compile → `jars/CovenantRemnants.jar`), `cleanMod`,
  `packageMod` (assemble `build/mod_package/` → `Covenant Remnants.zip`).

> **Runtime dependencies:** `mod_info.json` declares LemonLib, MagicLib, LazyLib (required).
> LunaLib and Console Commands are **optional** (developer ruling 2026-10-01): compile-only, not
> declared, and the mod must run without them. Console Commands code is isolated in `commands/`.
> LunaLib is currently unused (old debug `LunaSettings.csv` removed 2026-10-01); mod settings via
> LunaLib are planned — see [PLAN.md](PLAN.md).

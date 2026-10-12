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
- **Declared dependencies:** `combustiblelemon_lemonlib` (LemonLib, which also ships the Kotlin
  runtime), `MagicLib`. LazyLib is no longer declared (2026-10-12); it is still installed, because
  MagicLib requires it.

## Entry point — `CovenantRemnantsPlugin`

`src/.../CovenantRemnantsPlugin.kt` (extends `BaseModPlugin`):

- On application load: verifies MagicLib is enabled; loads `shield_data.csv` (custom
  per-ship shield parameters); compiles the shared shader programs; preloads the slipspace pocket
  background; installs the LunaLib settings bridge when LunaLib is enabled.
- On game load:
  - re-verifies the hullmod incompatibilities;
  - `ensureSlipspaceSystem()` creates or migrates the slipspace pocket system;
  - registers the transient `SlipspaceCampaignPlugin` (exit portal dialog);
  - `TesterCache.addIfNeeded()` fills the Corvus abandoned station's storage once per save. This is
    a temporary tester build, see [implementations/tester-cache.md](implementations/tester-cache.md).
- On new game: `ensureSlipspaceSystem()`.

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
  `data/shaders/shield/` (fill, border, sweep, honeycomb, fortress, plasma, distortion, debug), on **LemonLib**'s
  composition engine
  (`org.combustiblelemon.lemonlib.render.composition`; usage docs in
  `LemonLib/docs/shader-composition/`). See
  [implementations/shield-shader-composition.md](implementations/shield-shader-composition.md)
  and [implementations/lemonlib-extraction.md](implementations/lemonlib-extraction.md).
- Look: the Halo: Reach Elite energy shield. The `plasma` layer (`utils/render/shield/PlasmaLayer.kt`,
  state in `ShieldImpactEffect.kt`, curves generated into `ShieldImpactCurves.kt` from the Reach
  editing kit) draws rim glow + scrolling plasma filaments that flare on every hit and with low HP,
  the collapse on a break (plus EMP arcs, particles, flash and the `covenant_remnants_shield_pop`
  sound from the render plugin) and the recharge wave when regen resumes. The `distortion` layer
  (`DistortionLayer.kt`, framebuffer copy via `ShieldScreenCopy.kt`) adds a refraction ring per hit,
  scaled by damage. All ships share one GL program (`ShieldProgram.kt`, point capacity 128, count as
  a uniform), compiled at game start from `CovenantRemnantsPlugin.onApplicationLoad`. Noise maps:
  `graphics/covenant_remnants/fx/shield/`. See
  [implementations/shield-reach-impact.md](implementations/shield-reach-impact.md).

### Slipspace Jump (campaign travel)

`covenant_remnants_slipspace_jump` (`data/campaign/abilities.csv`, quest-granted; `CovenantGiveAll`
grants it meanwhile). Press → sector-map picker → charge (controls locked, Reach rupture ahead of
the fleet) → lurch in → custom transition into the pocket system "Slipspace" → a winding slipstream
carries the fleet to the exit → exit dialog → transition to the destination's edge facing the
origin. See [implementations/campaign-slipspace-jump.md](implementations/campaign-slipspace-jump.md).

- Ability: `campaign/abilities/SlipspaceJump.kt` (phase machine, rendering on the `ABOVE` layer),
  `SlipspaceJumpRules.kt` (gating, costs, destinations, Tuning bonus, every placeholder number as a
  live tunable for the `SlipspaceJumpTune` console command),
  `SlipspaceDestinationDialog.kt` (host dialog + `showCampaignEntityPicker`),
  `SlipspaceTransitionScript.kt` (sector-level location switch), `SlipspaceJumpVisuals.kt`
  (campaign owner of `utils/render/rupture/RuptureRenderer`).
- Pocket: `campaign/systems/SlipspaceSystem.kt` (`ensureSlipspaceSystem`, `prepareSlipspaceRoute`,
  route memory) + `SlipspacePaths.kt` (per-pair saved path records, polyline builder) +
  `scripts/SlipspaceSystemEFS.kt` (vanilla movement abilities off while in the pocket);
  path terrain `campaign/terrain/SlipspaceStreamTerrainPlugin.kt` (`SlipstreamTerrainPlugin2`
  subclass with push-back + clamp; `data/campaign/terrain.json`).
- Entities (`data/config/custom_entities.json`): entrance / exit use
  `campaign/entities/SlipspacePortalEntity.kt`, the Halo 3 "The Ark" slipspace exit as a
  `RuptureEffect` of kind ARK (`utils/render/rupture/ArkTimeline.kt`, tube mesh
  `ArkTubeShader.kt` + `ArkTubeMesh.kt` (lathed at load from the generated `ArkTubeProfile.kt`) + `data/shaders/rupture/arkTube.*`, textures
  `graphics/covenant_remnants/fx/slipspace_ark/`); idle dim, full burst on use; the destination arrival uses the same
  effect, the fleet sliding out of the tube. See
  [implementations/ark-slipspace-portals.md](implementations/ark-slipspace-portals.md). Exit
  dialog `SlipspaceExitDialogPlugin.kt` routed by `SlipspaceCampaignPlugin.kt`;
  `SlipspaceAmbienceEntity.kt` draws the sparse light streaks.
- Background: `graphics/covenant_remnants/backgrounds/slipspace_bg.jpg` (solid black placeholder).

### Slipspace Drive blink (frigate1 / frigate3 / destroyer2)

`covenant_remnants_slipspacedrive` (`data/shipsystems/`): the engine's `TELEPORTER` type with
`PHASE_TELEPORTER_2` AI — 500 su toward the cursor / AI target, 3 charges, 1.5 s in / 0.5 s out.
The jump is the engine's; the mod only draws it (Halo 3: ODST carrier-jump look).

- `systems/SlipspaceDrive.kt` — stats script: entry portal off the bow + hull ripple during the
  first ⅔ of the chargeup, then the ghost (`setCopyLocation`) slides into the portal nose first;
  exit portal at the landing point, ghost slides out nose first during the chargedown. Registers the one-per-combat renderer.
- `plugins/SlipspaceDriveRenderPlugin.kt` — one per combat; portals at `BELOW_SHIPS_LAYER`,
  ripples at `ABOVE_SHIPS_LAYER`.
- Portals: `utils/render/rupture/RuptureEffect.kt` kind `BLINK` (the Halo 3: ODST carrier-jump
  portal build: blue glow sphere, origin core, plasma web, soft light, blue flare; `close()` shrinks
  it), drawn by the plugin's own `RuptureRenderer`.
- `utils/render/blink/` — `BlinkEffects.kt` (`BlinkRipple` timeline incl. the bow flash),
  `BlinkRenderer.kt` (ripple program, compiled at game start, GL state wrap),
  `BlinkRippleShader.kt` ↔ `data/shaders/blink/blinkRipple.*` (ODST ripple over the ship sprite:
  plasma puffs, outward spark streaks, bow flash).
- `utils/render/rupture/RupturePrograms.kt` — the rupture sphere + sprite programs, shared by the
  fleet jump, the blink and the plasma torpedoes, compiled at game start. `utils/render/PlainProgram.kt` — plain
  vertex+fragment program helper shared by the rupture and blink renderers.
- `hullmods/SlipspaceTuning.kt` — +30 % charge regen on drive-system ships.
- See [implementations/slipspace-drive-blink.md](implementations/slipspace-drive-blink.md).

**Plasma torpedoes** (2026-10-02) — the Halo: Reach space Phantom torpedo look on the three
torpedoes. `data/weapons/proj/covenant_remnants_plasmatorpedo*.proj` (plain guided missiles,
invisible sprite); the `.wpn` `onFireEffect` `weapons/PlasmaTorpedoOnFire.kt` hands each one to
`plugins/TorpedoRenderPlugin.kt` (one per combat: muzzle, flight, impact, sounds), which draws
through `utils/render/torpedo/` (`TorpedoCurves`, `TorpedoEffects`, `TorpedoRenderer`) with the
shared Reach sprite program (`RuptureSpriteShader.drawSprites` / `drawRibbon`). See
[implementations/plasma-torpedo-reach.md](implementations/plasma-torpedo-reach.md).
- Console commands: `commands/RegenerateSlipspace.kt`, `commands/DebugHook.kt`,
  `commands/CovenantGiveAll.kt`, `commands/FleetJumpTune.kt` (registered in
  `data/console/commands.csv`). `ShaderLayers` lives in LemonLib and is registered by LemonLib's
  own `commands.csv`.

### Fleet Slipspace Jump (supercap1)

The CAS-class Assault Carrier's unique system, `covenant_remnants_fleet_slipspace_jump`
(`data/shipsystems/`, `STAT_MOD` + `CUSTOM` AI). Opens a rupture one ship length off the bow,
lurches the carrier into it during the fade, and jumps everything inside the grab radius around the
portal — ships of any side, fighters, shots, hulks, asteroids — to the destination in one frame.

- `systems/FleetSlipspaceJump.kt` — system id, every tunable (live via `FleetJumpTune`), per-combat
  shared state (AI→script destination handoff, in-transit set, the one render plugin), shared
  helpers (grabbable / shield-up / map / landing-spot checks).
- `systems/FleetSlipspaceJumpStats.kt` — stats script: usability + info text, timing overrides,
  starts a `systems/FleetSlipspaceJumpSession.kt` per activation. The session is an every-frame
  plugin: holds the carrier, phases/fades the cargo during the fade, executes the jump (carrier
  first, nudged landing spots, offsets preserved, rupture damage, flux), restores on abort.
- AI: `ai/FleetSlipspaceJumpAI.kt` (pull the fight home → strand enemy heavies → reinforce a
  losing allied group → hunt weakened enemies → regroup; flux/hull-weighted strength, stations
  counted).
- Player activation: `plugins/FleetSlipspaceJumpTargetingPlugin.kt` (one per combat;
  `processInputPreCoreControls`, so it works paused): system key primes, left click confirms the
  cursor point, right click cancels; blocks the engine's own `USE_SYSTEM` for the player's CAS.
- Render: `plugins/FleetSlipspaceJumpRenderPlugin.kt` (one per combat; aiming circles while primed
  and all live ruptures) → `utils/render/rupture/RuptureRenderer.kt`, a remake of the Halo: Reach
  slipspace bomb: `RuptureSphereShader` (`data/shaders/rupture/ruptureSphere.*`, analytic spheres)
  and `RuptureSpriteShader` (`ruptureSprite.*`, batched particles + heat haze over a screen copy);
  `RuptureEffect.kt` is the per-rupture timeline, driven by Reach's curves (`ReachCurves.kt`,
  generated) and textures (`graphics/covenant_remnants/fx/slipspace_rupture/`, ids in `data/config/settings.json`).
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

`Shader` interface and `getShaderCode` live in LemonLib (`org.combustiblelemon.lemonlib.render`);
the subsystem shaders above implement it. Composed systems registered with LemonLib's
`ShaderLayers` command: `shield` (ShieldShader). No monolith (non-composed) shader is left: the
campaign portal shader was replaced by the Ark rupture effect on 2026-10-08. Reflection helper:
`org.combustiblelemon.lemonlib.reflection.ReflectionUtils` (in LemonLib); cubic-bezier easing:
`org.combustiblelemon.lemonlib.geometry.CubicCurve2D` (in LemonLib). Misc: `utils/Utils.kt`,
`utils/Data.kt`, `utils/extensions/CampaignFleetAPI.kt`.

## Build & data flow

Gradle Kotlin DSL, Kotlin JVM 2.2.20, Java 8 target (`build.gradle.kts`; `compilerOptions` with
`jvmDefault = NO_COMPATIBILITY`, the old `-Xjvm-default=all`):

- **sourceSets:** `src` → Kotlin/Java classes. The jar holds **code only** (2026-10-02): Starsector
  reads `data/`, `graphics/`, `sounds/` and `mod_info.json` from the mod folder, so they are not
  packed into it.
- **compileOnly:** every dependency is compile-only:
  - The Kotlin runtime is LemonLib's `jars/lib/Kotlin-Runtime.jar`.
    `kotlin.stdlib.default.dependency=false` in `gradle.properties` stops Maven stdlib being added.
  - Starsector core jars (`../../starsector-core/*`, incl. `starfarer.api.jar`, obf jars, `json.jar`,
    `lwjgl`).
  - LemonLib, through the composite build (`settings.gradle.kts` `includeBuild("../LemonLib")`),
    resolved to LemonLib's classes.
  - MagicLib, LunaLib, Console Commands, log4j 1.2.17.
  - No LazyLib. Its former uses map as follows:
    - `CollisionUtils`, `DrawUtils.drawCircle`: LemonLib (`geometry/Collision.kt`,
      `render/Primitives.kt`).
    - `MathUtils.getDistance`: `Misc.getDistance`.
    - JSON iterator: an index loop.
- **Tasks:**
  - `buildMod`: compiled classes → `jars/CovenantRemnants.jar`.
  - `cleanMod`.
  - `stageMod`: runtime files → `build/mod_package/Covenant Remnants/`.
  - `packageMod`: the staged folder → `Covenant Remnants.zip`.
  - The release file list lives only in `packageMod`'s `runtimeFiles`. `package_mod.bat` / `.sh`
    wrap it. See [implementations/release-packaging.md](implementations/release-packaging.md).

> **Runtime dependencies:** `mod_info.json` declares LemonLib and MagicLib (required). LemonLib
> provides the Kotlin 2.2.20 runtime. LazyLib only comes along because MagicLib requires it.
> MagicLib is used only for `MagicUI`: the Covenant shield HUD bars (`CovenantShieldsUIRenderPlugin`)
> and the `BLUCOLOR` of the shield damage numbers. It stays required (developer ruling 2026-10-10:
> nearly every player has it, and rewriting the HUD bars is not worth it).
> LunaLib and Console Commands are **optional** (developer ruling 2026-10-01): compile-only, not
> declared, and the mod must run without them. Console Commands code is isolated in `commands/`.
> LunaLib code is isolated in `settings/LunaSettingsBridge.kt` (built 2026-10-10). The plugin loads
> that class only after `isModEnabled("lunalib")`. It copies `data/config/LunaSettings.csv` values
> into the plain `settings/CovenantSettings` object, which holds the defaults and is what every
> reader uses. See [implementations/lunalib-settings.md](implementations/lunalib-settings.md).

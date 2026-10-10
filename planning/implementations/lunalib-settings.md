# Implementation — LunaLib settings (2026-10-10)

The mod now has a LunaLib settings page with four player options: three performance switches and
one volume control. LunaLib stays optional (ruling 2026-10-01). Without it the mod runs on
built-in defaults, and those defaults match how the mod looked and sounded before the settings
existed.

## Developer rulings (2026-10-10)

| Topic | Decision |
| --- | --- |
| Scope | Performance + effect volume. Not included: Fleet Jump damage, campaign jump cost multipliers, dev switches (`VISUAL_ONLY`, `FREE`, `NO_SHIP_RESTRICTION` stay console-only). |
| Wiring | Isolated bridge, the Console Commands pattern. Values apply live on every save of the settings page. |
| Rows | As proposed: density 0.1–1.0 (can only lower it), heat haze on, shield rings on, effect volume 0–2. Two headers, Performance and Audio. |
| Volume range | Clamped to 0–1 after the first in-game look (developer 2026-10-10). A value of 2 already saved by LunaLib is clamped to 1 by the bridge. |

## The settings

| Field id | Type | Default | Range | What it does |
| --- | --- | --- | --- | --- |
| `covenant_remnants_particle_density` | Double | 1.0 | 0.1–1.0 | Multiplies the rupture renderer's particle counts (fleet jump, blink, Ark portals). Stacks with `FleetJumpTune PARTICLE_DENSITY`, which stays a dev knob. |
| `covenant_remnants_heat_haze` | Boolean | true | — | Off forces the rupture renderer's distortion to 0. That skips its full-screen copy and every screen-copy sprite: the Reach heat haze, the Ark distortion rings and the Ark refraction front. |
| `covenant_remnants_shield_distortion` | Boolean | true | — | Off stops new Covenant shield hit refraction rings, so the per-ship screen copy stops too. Rings already running finish (0.8 s at most). |
| `covenant_remnants_effect_volume` | Double | 1.0 | 0–1 | Multiplies the volume of every sound the mod plays from code (list below). |

## How it is wired

- **`settings/CovenantSettings.kt`** — a plain `object` with the field ids, ranges and the four
  `var`s at their defaults. It has no LunaLib types, so it always loads. Every reader uses it.
- **`settings/LunaSettingsBridge.kt`** — the only class that imports `lunalib`.
  - It implements `LunaSettingsListener`.
  - `install()` reads all four values, then registers the listener once (guarded with
    `hasSettingsListenerOfClass`).
  - `settingsChanged(modID)` re-reads them when `modID` is ours. LunaLib calls it for every mod's
    save.
  - A null value (field or mod not found) keeps the current value.
  - Out-of-range values, such as from a hand-edited save, are clamped.
  - Each read logs one `Covenant settings: …` line.
- **`CovenantRemnantsPlugin.onApplicationLoad`** — calls `LunaSettingsBridge.install()` only when
  `isModEnabled("lunalib")` is true. The id constant lives in `CovenantSettings`, so the check does
  not touch the bridge class.
- **Jar check** — only `LunaSettingsBridge` and `LunaSettingsBridge$Companion` contain `lunalib/`
  references.

### Readers

- **Particle density** — `RuptureEffect.particleDensity()` returns the dev tunable times the
  setting. It replaced the 4 direct reads (charge, burst plasma, exit twinkles, `count()`).
- **Heat haze** — `RuptureRenderer.renderAll`: `distortion` becomes 0 when the setting is off. The
  existing `distortion > 0f` check then skips `copyScreen()` and the screen-copy sprites.
- **Shield rings** — `DistortionLayer.addWave` returns early when the setting is off.
- **Effect volume** — the volume argument at each code-played sound:

| Sound | File |
| --- | --- |
| shield pop | `plugins/CovenantShieldsRenderPlugin.kt` |
| Energy Projector impact | `plugins/EnergyProjectorRenderPlugin.kt` |
| Plasma Beam Emitter / Suppressor fire-in, fire-out and impacts (the beam loop is the engine's) | `plugins/PlasmaBeamRenderPlugin.kt` |
| torpedo impact and in-flight hum | `plugins/TorpedoRenderPlugin.kt` |
| Plasma Cannon / Mortar / Bombardment impact boom | `weapons/Plasma{Cannon,Mortar,Bombardment}OnHit.kt` |
| Fleet Jump aiming prime (UI sound) | `plugins/FleetSlipspaceJumpTargetingPlugin.kt` |
| Fleet Jump open / charge hum / collapse / arrival | `systems/FleetSlipspaceJumpSession.kt` |
| campaign Slipspace Jump charge hum and one-shots | `campaign/abilities/SlipspaceJumpVisuals.kt` |

The engine plays weapon fire, charge and loop sounds from `sounds.json` / the `.wpn` files. The
volume setting does not reach those; the game's sound slider does, as the setting's tooltip says.

## Files

- `data/config/LunaSettings.csv` — new: 2 headers, 4 settings, the `covenant_remnants_` prefix.
- `src/.../settings/CovenantSettings.kt`, `src/.../settings/LunaSettingsBridge.kt` — new.
- `src/.../CovenantRemnantsPlugin.kt` — guarded `install()`.
- `src/.../utils/render/rupture/RuptureEffect.kt`, `RuptureRenderer.kt`,
  `src/.../utils/render/shield/DistortionLayer.kt` — the three performance readers.
- The 10 sound files in the table above.
- Docs: [ARCHITECTURE.md](../ARCHITECTURE.md) (runtime dependencies note), [PLAN.md](../PLAN.md),
  [TODO.md](../TODO.md).

## Verification done

- `gradlew buildMod` succeeded, producing `jars/CovenantRemnants.jar`.
- Jar scan: `lunalib/` appears only in the two bridge classes.
- `LunaSettings.csv` parses, with 9 columns on every row. The header matches LunaLib's own file.
- No shader changed, so no GLSL check was needed.

## In-game checklist

1. **With LunaLib.**
   - The settings page (F2 in the campaign) shows "Covenant Remnants" with Performance and Audio
     headers and the four rows.
   - `starsector.log` has `Covenant settings: particleDensity=1.0 heatHaze=true …` at startup.
2. **Live apply.** Change a value and save. A new `Covenant settings:` log line shows the new
   values with no restart needed.
3. **Particle density 0.1.** Fleet jump, blink and Ark portal show visibly fewer particles. At 1.0
   they look as before.
4. **Heat haze off.**
   - Fleet jump: no heat-haze ripple. Ark portal: no refraction front or distortion rings.
   - Everything else draws, and the frame rate improves during a jump.
5. **Shield distortion off.** Covenant shield hits show no refraction rings. The plasma hit flare
   still shows.
6. **Effect volume.** At 0, the shield pop, impacts, torpedo hum and jump sounds are silent, while
   weapon fire still plays. At 0.5 they are quieter.
7. **Without LunaLib.** Disable LunaLib in the launcher. The game starts, there is no
   `NoClassDefFoundError`, and everything looks and sounds as at the defaults. **Passed** (developer
   2026-10-10: no error. Other load errors seen were old-save issues that a new save clears).

## Open items

- More settings later (fleet jump damage, campaign jump costs, etc.) go through the same object and
  bridge: add a CSV row, a `var` in `CovenantSettings`, and a read in the bridge.

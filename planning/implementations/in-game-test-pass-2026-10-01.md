# Implementation — In-game test pass + fixes (2026-10-01)

First in-game verification of the 2026-08-25 batch (hullmod pass, incompatibility registry,
supercap speed, shield/flux tune, shader composition + LemonLib, `CovenantGiveAll`). Results came
from the developer; this file records them and the fixes that followed.

## Test results (developer, 2026-10-01)

| Area | Result |
| --- | --- |
| Game load, `CovenantGiveAll` | Pass |
| Covenant Shield built-in on hulls | **Fail** — not built in (cause + fix below) → fixed, re-test pass |
| CR penalty / Huragok Crew offset | Pass |
| Optional hullmod OP costs | Pass |
| Slipspace Tuning eligibility | **Design change** — see below → re-test pass |
| Incompatibility grey-outs + reasons, vanilla ships unaffected, codex | Pass |
| Ship bounds / weapon slots | Slots sit off the hull — developer is reworking bounds + slots |
| CAS speed | 40 at flux / 100 zero-flux at 100% CR (mod alone: 36 / 96 — see below) |
| Shield look | Pass |
| Shield pool sharing (2+ Covenant ships) | Pass — only the hit ship's shield drops; state is per-ship |
| Energy Projector exit scorch | Pass |
| `ShaderLayers` listing | Pass — lists `shield` |
| LemonLib as hard dependency | Pass — launcher won't enable Covenant without it |
| `ShaderLayers shield border` | **Fail** — fragment compile error (fixed below) → re-test pass |
| Shield regen under fire (follow-up) | **Fail** — no visible slowdown (fix 5 below) → re-test pass |

## Fixes

### 1. Solo/subset shader layers — `undefined variable "debugMode"`

`base.frag`'s footer read the `debugMode` uniform, which is declared only in `debug.glsl`. Any
layer selection that stubbed the debug layer (e.g. `ShaderLayers shield border`) produced a
program with no `debugMode` declaration → compile failure. Fix: the gate moved inside
`debug_check()` (`if (!debugMode) return false;`); `base.frag` now calls `debug_check()`
unconditionally and references no layer-specific uniform. Audit: no other module symbol leaks
across modules except `borderColor` (`sweep.glsl` reads it), which the border stub already
declares.

- `data/shaders/shield/base.frag`, `data/shaders/shield/debug.glsl`
- LemonLib docs: `docs/shader-composition/example-shield.md` (per-pixel diagram),
  `docs/shader-composition/debugging.md` (new troubleshooting row: the template must only call layer
  functions, never read layer uniforms)

### 2. Covenant Shield missing from `builtInMods`

The developer's uncommitted ship-editor pass rewrote all 19 `.ship` files and dropped
`covenant_remnants_covenantshieldhullmod` from `builtInMods` on all 16 combat hulls (HEAD had it).
No code mutates hull specs at runtime, so the editor output was produced from older hull data.
Re-added the id to all 16 combat hulls; formatting of the editor output preserved.

The same pass also changed weapon slots on three hulls — left untouched (developer's in-progress
geometry work), flagged here:

- cruiser1: `WS 000`/`WS 001`/`WS 002` removed, `WS0003` LARGE → MEDIUM.
- destroyer2: `WS 000` and both launch bays (`LB 000`/`LB 001`) removed.
- supercap1: nine `WS 00x` slots removed, `WS0001`–`WS0004` TURRET → HARDPOINT, engines 3 → 4.

No variant references a missing slot id.

- `data/hulls/covenant_remnants_{capital1-3,cruiser1-4,destroyer1-4,frigate1-3,supercap1-2}.ship`

### 3. Slipspace Tuning eligibility (developer decision)

Was: only hulls whose ship system is `covenant_remnants_slipspacedrive` (frigate1, frigate3,
destroyer2, supercap1). Now:

- **Installable** on any ship carrying the Slipspace Drive hullmod (`covenant_remnants_slipspace_drive`),
  built-in or not — today all 16 combat hulls.
- **Combat effects** (−30% system cooldown, 85% jump flux gate) apply only when the ship's system is
  the slipspace drive. The cooldown cut is guarded in `applyEffectsBeforeShipCreation` so it never
  shortens another system (Fortress Shield, Displacer, …); the flux gate was already drive-only
  (read by `SlipspaceDrive.isUsable`).
- On other ships the mod has **no effect yet** — it will give a bonus to the planned campaign
  slipspace-jump ability (developer: "on ships without the system it only gives the campaign stuff").

`hull_mods.csv` description unchanged ("Reduces slipspace drive cooldown… Only installable on ships
with a slipspace drive.") — still accurate; extend it when the campaign bonus lands.

- `src/org/combustiblelemon/covenantremnants/hullmods/SlipspaceTuning.kt`

### 4. IntelliJ project files

Editor showed red everywhere: IntelliJ's stored Gradle import (2026-08-25) gave the
`CovenantRemnants.main` module **no libraries**. Gradle itself resolves the full classpath
(verified via CLI), so **Reload All Gradle Projects** fixes it. Separately:

- `.idea/kotlinc.xml` — removed stale `-version -jvm-target 17` extra compiler args (contradicted
  the 1.8 target).
- Deleted (developer-approved): legacy pre-Gradle files `covenant-remnants.iml`,
  `.idea/libraries/*.xml` (8, incl. GraphicsLib), and `.idea/artifacts/Create__jar.xml` (a JPS
  artifact writing the same `jars/CovenantRemnants.jar` as Gradle `buildMod`). None were loaded by
  the Gradle-imported project.

### 5. Shield regen under fire (developer decision)

Follow-up report: regen did not visibly slow under fire from 3 Onslaughts. Cause: the description
promised *"Regenerates %s per second after %s seconds without taking damage"* (per-hull
`shield regen delay`, 1.5–5 s), but the code never paused regen — it only halved it while >1% of max
shield HP was taken in a trailing 3 s window, which is invisible against heavy incoming fire.

Developer chose **pause, then halved**:

- Any shield hit (any size — the 1% threshold is gone) resets the timer.
- Regen is **0** until `shield regen delay` seconds pass with no shield damage.
- Then **50%** (`REGEN_SLOW_MULT`) for **3 s** (`REGEN_SLOW_DURATION`), then full rate.
- Implementation: `damageInstances` list replaced by one `lastShieldDamageTime` (reset per combat);
  `regenMult()` is a pure function of time since the last hit.
- Description line now reads "…after %s seconds without taking damage (%s rate for the first %s
  seconds)"; params 3/4 come from the two constants so text can't drift. Broken-shield cooldown
  moved to param 5.
- Untouched: `shieldRegenDelay` is also (oddly) used as the `isUsable` / `shieldCooldown` threshold
  around shield breaks — pre-existing, out of scope.

- `src/org/combustiblelemon/covenantremnants/systems/CovenantShieldsStats.kt`,
  `src/org/combustiblelemon/covenantremnants/hullmods/CovenantShield.kt`,
  `data/hullmods/hull_mods.csv`

### 6. Stale `ShaderLayers` console registration + dependency rulings

- `data/console/commands.csv` still registered `ShaderLayers` →
  `org.combustiblelemon.covenantremnants.commands.ShaderLayers`, a class that moved to LemonLib
  (`org.combustiblelemon.lemonlib.commands.ShaderLayers`, registered by LemonLib's own
  `commands.csv`). Both rows share the command key; it only worked because LemonLib's row won the
  merge (load-order dependent). Stale row removed.
- Developer rulings (2026-10-01): LunaLib + Console Commands are **optional** (recorded in
  `CLAUDE.md` Stack and `ARCHITECTURE.md`; `mod_info.json` already correct); Suppressor Beam stays
  **ENERGY**.
- Found while checking: `data/config/LunaSettings.csv` defined 25 shield settings no code read
  (old debug settings). Deleted per developer; real LunaLib settings are planned (PLAN.md).

## CAS speed note

Mod values: base 30, Nanolaminate ×1.2, vanilla zero-flux boost +50 → 36 / 96. Observed 40 / 100
matches exactly a **+10% top-speed bonus from outside the mod** on the base value:
`30×1.1×1.2 = 39.6`, `(30×1.1 + 50)×1.2 = 99.6`. Likely an officer/player skill or fleet nav
rating. Not a mod bug; recheck with an unofficered ship in the simulator if needed.

## Verification

- `./gradlew.bat buildMod` — builds clean.
- Re-tested in-game 2026-10-01 (developer): `ShaderLayers` solo/subset + `all` work; Covenant
  Shield shows as built-in with the new description; regen pauses under fire, then 50% for 3 s,
  then full.
- Slipspace Tuning re-tested 2026-10-01: installs on cruiser2 without changing its system
  cooldown; still gives −30% / 85% on frigate1. All fixes in this pass verified.
- Incompatibility grey-outs survive save/load (verified 2026-10-01).

## Open items

- Campaign slipspace-jump bonus for Slipspace Tuning (with the planned campaign ability).
- After each ship-editor save, re-check `builtInMods` (and restart the game first so the editor
  loads current files).

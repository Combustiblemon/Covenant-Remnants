# Vanilla-style hullmod incompatibility (Covenant registry)

> **Extracted to LemonLib (2026-08-25).** The generic mechanism (blocker, wrapper, reflective
> installer, shared direction-B helpers, ReflectionUtils) now lives in LemonLib
> (`org.combustiblelemon.lemonlib.hullmods` + `.reflection`), initialized from code with a
> registry map — usage doc: `LemonLib/docs/hullmod-incompatibility/README.md`. This mod keeps
> only `hullmods/CovenantIncompatibilities.kt` (the registry below) and the per-hullmod
> `BLOCKER`/`INCOMPATIBLE_HULLMODS` companions. Extraction also fixed a latent bug: `frontshield`
> previously matched the generic Covenant Shield blocker before the custom-reason one, so the
> "This ship already has a shield system" message never showed.

Replaces MagicLib's `MagicIncompatibleHullmods` post-install removal with vanilla refit UX across
the whole mod: blocked vanilla hullmods are greyed out in the refit picker on Covenant ships with
a proper reason, and cannot be installed at all — exactly like vanilla ITU-vs-DTC. Built in two
passes (2026-08-25): first ITU/DTC vs Covenant Systems, then the full audited registry below.

## Registry

Direction A: the vanilla mod refuses install when the Covenant blocker is present (via the
runtime wrapper). Direction B: the Covenant mod refuses install when the vanilla mod is present
(own `isApplicableToShip`/`getUnapplicableReason`).

| Vanilla id(s) | Covenant blocker | Grey-out reason |
| --- | --- | --- |
| `targetingunit`, `dedicated_targeting_core` | Covenant Systems | "Incompatible with Covenant Systems" |
| `heavyarmor`, `solar_shielding` | Nanolaminate Armor | "Incompatible with Nanolaminate Armor" |
| `advancedshieldemitter`, `extendedshieldemitter`, `hardenedshieldemitter`, `stabilizedshieldemitter`, `frontemitter`, `adaptiveshields`, `shield_shunt`, `adaptive_coils`, `phase_anchor` | Covenant Shield | "Incompatible with Covenant Shield" |
| `frontshield` (Makeshift Shield Generator) | Covenant Shield | "This ship already has a shield system" |

Deliberately NOT blocked (audited, developer-ruled): Safety Overrides (player freedom),
Distributed Fire Control (hidden + built-in-only, can never coexist), everything HuragokCrew /
PlasmaCapacitors / SlipspaceTuning might touch (vanilla stacking precedent).

## How it works

Vanilla's incompatibility check lives in the hullmod *being installed*. Covenant Systems /
Nanolaminate / Covenant Shield are built-in on every Covenant hull, so the message has to come
from the vanilla effects themselves. There is no declarative mechanism and `HullModSpecAPI` has
no `setEffect()`, so:

- `CovenantIncompatibilityWrapper` (Kotlin `by`-delegation over `HullModEffect`) wraps the vanilla
  effect with a list of `CovenantBlocker`s and overrides only the two applicability methods.
- `CovenantIncompatibilityInstaller.install()` runs in `onApplicationLoad`, builds the registry by
  inverting each Covenant hullmod's own `INCOMPATIBLE_HULLMODS` map + `BLOCKER`, and swaps the
  wrapper into each spec via reflection. The effect field is found **structurally, never by name**
  (obfuscation-rename-proof): value-identity scan over the spec class + superclass chain first,
  declared-type-assignable-to-`HullModEffect` scan as fallback. Post-install verification with
  rollback; every failure is log+skip, never a crash.
- `verifyAndReinstall()` runs on every `onGameLoad`: if another mod replaced a wrapped effect
  after us (load-order dependent), WARN + reinstall.
- Refit UI, tooltip, and autofit all call `spec.getEffect()` live (verified in decompiled
  0.98a-RC8 sources — no caching), so the wrapper is honored everywhere.
- Shared helpers in `CovenantIncompatibility.kt` (`CovenantBlocker`, `incompatibleWith`,
  `unapplicableReasonFor`, `isApplicableGiven`) are the ONE way both directions produce reasons —
  live spec `displayName` with hardcoded fallback.

## Key findings baked into the design (verified in decompiled sources)

- **F1 — blocker presence is a predicate, not `hasHullMod`.** Covenant hulls carry the shield via
  `ship_data.csv` (`shield type = PHASE`, `defense id = covenant_remnants_covenantshield`), not
  via the hullmod. `isPhase()==true` even made vanilla Adaptive Phase Coils / Phase Anchor
  installable on Covenant ships before this change. Covenant Shield's blocker checks
  `hasHullMod(ID) || hullSpec.shipDefenseId == DEFENSE_ID`.
- **F2 — null-ship NPE.** The hullmod tooltip factory (`ui.impl.java:8574`) passes a null ship
  into `getUnapplicableReason` (codex / no ship selected). All applicability overrides take
  `ShipAPI?`.
- **F3 — refit strips built-ins.** `removeNotApplicableMods` (`coreui.refit.java:2985`) removes
  ANY mod — built-ins included — whose `isApplicableToShip` is false. `isApplicableGiven` returns
  true when the mod is built into the hull, so a legacy variant carrying e.g. `heavyarmor` loses
  only `heavyarmor` on refit open, never the built-in Covenant mod.

## Key decisions (developer, 2026-08-25)

- Runtime effect wrap over overriding vanilla `hull_mods.csv` rows (mod compatibility).
- **No removal fallback.** If reflection fails, the vanilla mod stays installable — ERROR log is
  the signal. Recovered per-entry by reinstall-on-WARN each game load.
- **Covenant Shield hullmod is now built-in on all 16 Covenant hulls** (was player-installed
  only). Its spec-clone logic already no-ops when the hull's defense is already the Covenant
  shield, so built-in presence is purely a marker + tooltip on stock hulls.
- Makeshift Shield Generator gets the custom state-based reason, not the "Incompatible with X"
  template.
- Covenant Shield csv desc rewritten: fluff + mechanics + per-ship numbers (shield hp / regen /
  regen delay / broken cooldown) served by `getDescriptionParam(index, hullSize, ship)` from
  `shieldData` (keyed by `baseHullId`, same as `CovenantShieldsStats`).
- Reason-text change on Covenant hulls: shield mods now say "Incompatible with Covenant Shield"
  instead of vanilla's confusing "Ship has no shields".

## Files touched

- `src/.../hullmods/CovenantIncompatibility.kt` — **new**: shared blocker + helpers.
- `src/.../hullmods/CovenantIncompatibilityWrapper.kt` — renamed from
  `CovenantSystemsIncompatibilityWrapper.kt`; generalized wrapper + installer + re-verify.
- `src/.../hullmods/CovenantSystems.kt`, `NanolaminateArmor.kt`, `CovenantShield.kt` — companion
  `ID`/`DISPLAY_NAME`/`INCOMPATIBLE_HULLMODS`/`BLOCKER`, direction-B checks via shared helpers;
  MagicLib removal loops deleted.
- `src/.../hullmods/HuragokCrew.kt` — dead blocked-list/removal/applicability code deleted.
- `src/.../hullmods/HullmodManager.kt` — id literals replaced with `CovenantShield.ID` /
  `CovenantShield.DEFENSE_ID`; gained `ID` const.
- `src/.../utils/Utils.kt` — `getUnapplicableReasonForHullmod` deleted (superseded).
- `src/.../utils/reflection/ReflectionUtils.kt` — `findFieldsByValue`, `findFieldsAssignableTo`;
  `ReflectedField.set` now sets accessible first. Gotcha: any bytecode reference to
  `java.lang.reflect.Field` (e.g. a Kotlin `for` typed over `clazz.declaredFields`) throws
  `SecurityException: File access and reflection are not allowed to scripts` — results of
  `getDeclaredFields()` must be upcast to `Array<out Any>` immediately, access only through the
  MethodHandles.
- `src/.../CovenantRemnantsPlugin.kt` — installer at `onApplicationLoad`, re-verify at
  `onGameLoad`.
- `data/hulls/covenant_remnants_*.ship` (16 combat hulls) — `covenant_remnants_covenantshieldhullmod`
  added to `builtInMods`.
- `data/hullmods/hull_mods.csv` — Covenant Systems desc closing line (range-only wording kept);
  Covenant Shield desc rewritten with `%s` params.

## Open items

- Not yet verified in-game (see verification checklist in the plan: grey-outs + reasons on
  Covenant hull, vanilla ships unchanged, direction B on vanilla hull with Covenant Shield,
  codex null-ship tooltips, legacy-combo refit strip, save/load, simulator).
- ~~`covenant_remnants_frigate3.ship` lacks `covenant_remnants_slipspace_drive`~~ — resolved
  2026-10-01 in the ship-editor bounds pass.
- Kotlin `by` wrapper vs future `HullModEffect` interface additions → `AbstractMethodError` until
  recompiled against the new API jar. Accepted; the mod is version-pinned.

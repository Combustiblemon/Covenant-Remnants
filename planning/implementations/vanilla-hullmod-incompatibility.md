# Vanilla-style hullmod incompatibility for Covenant Systems

Replaces MagicLib's `MagicIncompatibleHullmods` post-install removal with vanilla refit UX for
Covenant Systems: Integrated Targeting Unit (`targetingunit`) and Dedicated Targeting Core
(`dedicated_targeting_core`) are greyed out in the refit picker on Covenant hulls with the reason
"Incompatible with Covenant Systems", and cannot be installed at all — exactly like vanilla
ITU-vs-DTC.

## How it works

Vanilla's incompatibility check lives in the hullmod *being installed* (its
`isApplicableToShip` / `getUnapplicableReason`). Covenant Systems is built-in on every Covenant
hull, so the message has to come from the vanilla ITU/DTC effects themselves. There is no
declarative mechanism and `HullModSpecAPI` has no `setEffect()`, so:

- `CovenantSystemsIncompatibilityWrapper` (Kotlin `by`-delegation over `HullModEffect`) wraps the
  vanilla effect and overrides only the two applicability methods; everything else delegates
  untouched.
- `CovenantIncompatibilityInstaller.install()` runs in `onApplicationLoad` and swaps the wrapper
  into each spec via reflection. The effect field is found **structurally, never by name**
  (obfuscation-rename-proof): value-identity scan over the spec class + superclass chain first,
  declared-type-assignable-to-`HullModEffect` scan as fallback. Post-install verification with
  rollback.
- Refit UI, tooltip, and autofit all call `spec.getEffect()` live (verified in decompiled
  0.98a-RC8 sources — no caching), so the wrapper is honored everywhere.

## Key decisions (developer, 2026-08-25)

- **Runtime effect wrap** over overriding vanilla `hull_mods.csv` rows (keeps compatibility with
  other mods that touch ITU/DTC).
- **No fallback removal.** The MagicLib removal loop is deleted. If reflection ever fails (future
  game version), ITU/DTC become installable alongside Covenant Systems — the ERROR log in
  `starsector.log` is the only signal.
- **Distributed Fire Control not blocked.** Considered and dropped: vanilla DFC is hidden,
  built-in-only, and can never coexist with Covenant Systems — blocking it is dead code.
- Symmetric checks added to `CovenantSystems` itself (`isApplicableToShip` /
  `getUnapplicableReason`) in case it is ever player-installable.

## Files touched

- `src/.../hullmods/CovenantSystems.kt` — MagicLib import + removal loop deleted; `BLOCKED_HULLMODS`
  replaced by public `INCOMPATIBLE_HULLMODS` map (id → fallback display name) +
  `INCOMPATIBLE_REASON`; symmetric vanilla-style checks added.
- `src/.../hullmods/CovenantSystemsIncompatibilityWrapper.kt` — **new**: wrapper +
  `CovenantIncompatibilityInstaller`.
- `src/.../utils/reflection/ReflectionUtils.kt` — new `findFieldsByValue` and
  `findFieldsAssignableTo` (both walk the superclass chain, both name-agnostic);
  `ReflectedField.set` now calls `setAccessible` first (was missing).
  Gotcha hit in testing: any bytecode reference to `java.lang.reflect.Field` (e.g. a Kotlin
  `for` loop typed over `clazz.declaredFields`) throws
  `SecurityException: File access and reflection are not allowed to scripts` from the game's
  script classloader. Results of `getDeclaredFields()` must be immediately upcast to
  `Array<out Any>` and only touched through the MethodHandles — the file's existing pattern.
- `src/.../CovenantRemnantsPlugin.kt` — installer call in `onApplicationLoad`.
- `data/hullmods/hull_mods.csv` — Covenant Systems desc gains the vanilla-style closing line
  "Can not work in conjunction with hullmods that increase weapon range." (behaviour-based wording,
  not a name list — developer ruling 2026-08-25).

## Open items

- Not yet verified in-game (refit grey-out, autofit, non-Covenant ships unchanged, save/load).
- `NanolaminateArmor.kt` and `HuragokCrew.kt` still use `MagicIncompatibleHullmods` — same
  treatment pending (see TODO).
- Kotlin `by` wrapper vs future `HullModEffect` interface additions → `AbstractMethodError` until
  recompiled against the new API jar. Accepted; the mod is version-pinned.

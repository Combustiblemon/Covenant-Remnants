# Implementation — Advanced Slipspace: the navigation upgrade (2026-10-12)

An upgrade to the campaign Slipspace Jump ([campaign-slipspace-jump.md](campaign-slipspace-jump.md)):
the jump can land at an exact point plotted on a system map, in another system or inside the
current one. Requested in `stuff_to_do.md` ("advanced slipspace jump (jump within system and to
specific system spot instead of just the edge)").

The developer pointed at Ashlib's "exampleMap" for the UI. No class of that name exists in Ashlib
2.2.3 (the installed jar) or on upstream `master`. The closest are `ui/models/resizable/examples/
PopUpOnStarSystems` and the template flow `ui/template/SlipSpaceJumpCoordinateUI`, which the
developer chose ("SlipspaceJumpCoordinateUI could be it, we'll see if it crashes without AoTD").

## Developer rulings (2026-10-12)

| Topic | Ruling |
| --- | --- |
| Relation to the jump | An upgrade of the same ability, not a new one. Each star's dropdown on the sector map gets a second row that opens the in-system map. |
| Another system | Still through the pocket corridor; only the exit lands at the chosen point. |
| Current system | A direct jump, no pocket: charge and lurch as today, rupture collapses, screen fades, Ark arrival at the point. |
| Current system on the picker | Shown once upgraded, with only the map row. |
| Map UI | Ashlib's `SlipSpaceJumpCoordinateUI`; Ashlib becomes a required dependency. |
| Unlock | A second right-click item, "Covenant Slipspace Navigation System". Refused (and kept) without the Slipspace Jump. Also in the tester cache, old tester saves included. |
| Old item | "Huragok Slipspace Engram" renamed "Covenant Slipspace Drive Schematics" (same id). |
| No-go points | Star / black-hole zones of **radius × 1.5** (our own, Ashlib's 5 × zones removed); beyond the system's outer radius (no pad); in-system, closer than a tunable minimum to the fleet. Planets are allowed. |
| Never-entered systems | The map row is hidden (`isEnteredByPlayer`); the edge row stays. |
| Costs | In-system: transverse-jump style, 1 LY of fuel, the jump's CR hit, the jump's cooldown. Another system: same as the edge jump. All tunable. |
| Facing on arrival | The travel direction: in-system rupture → point; another system origin → destination (hyperspace). |
| Map Back / Escape | Returns to the sector map; cancelling there ends the jump for free. |
| Moving stars | At arrival, a point that drifted into a star zone is nudged to the zone edge. |
| Overlays | Chosen-point reticle, outer-bound ring, fleet marker (in-system). |
| Row wording | `<system> - plot coordinates`. |

## How it works

```text
press → SlipspaceDestinationDialog → showCampaignEntityPicker(entries)
  entries: hyperspace anchors (edge rows, as before)
         + with the upgrade, one system.createToken(centre) per pointDestinations(origin)
           (the picker lists every entry located in the clicked system → a second dropdown row;
            the current system gets only this row)
  edge row confirmed  → ability.onDestinationPicked(system)            (unchanged path)
  plot row confirmed  → pendingPointSystem; the picker closes itself
host.advance (next frame) → showCustomVisualDialog(SlipspacePointDialog)
  SlipspacePointMap (subclass of Ashlib's SlipSpaceJumpCoordinateUI): click → pointRejectReason
  Confirm → host.onPointDialogClosed(system, point) → next frame: onDestinationPicked(system, point)
  Back / Escape → the sector map again
ability: charge → lurch → commit
  current system → commitBlink: fuel (1 LY) + CR, nudge, collapse, Ark exit armed at the point,
                   SlipspaceTransitionScript Leg.BLINK (hold, fade, place, slide out) → cooldown
  another system → as before; prepareSlipspaceRoute(..., exitPoint) stores the point in the pocket's
                   memory; commitExit lands at it (nudged), facing origin → destination
```

## Files

New:

- `src/.../campaign/abilities/SlipspacePointDialog.kt` — `SlipspacePointDialog` (the custom visual
  dialog delegate; 90 % of the screen on each side, in UI pixels, since 2026-10-17 at the
  developer's request; was 1000 × 640) and `SlipspacePointPanel` (title, map, info lines,
  Confirm / Back; Back has Escape). The panel layout is relative, so the map takes the extra space.
- `src/.../campaign/abilities/SlipspacePointMap.kt` — `SlipspacePointMap` (Ashlib map subclass:
  our zones, click rules, overlays) and `MapGlyph` (fixed-size reticle / fleet marker).
- `src/.../campaign/items/SlipspaceNavigationSystemPlugin.kt` — the upgrade item.

Changed:

- `src/.../campaign/abilities/SlipspaceJumpRules.kt` — `NAVIGATION_ITEM_ID`, `NAVIGATION_FLAG`
  (character memory), tunables `BLINK_FUEL_LY` 1, `BLINK_CR_MULT` 1, `BLINK_COOLDOWN_MULT` 1,
  `BLINK_MIN_DISTANCE` 2000, `STAR_EXCLUSION_MULT` 1.5; `hasNavigationUpgrade`,
  `grantUpgradeToPlayer`, `pointDestinations`, `blinkFuelCost`, `systemCenter`,
  `pointBoundsRadius`, `starZones`, `pointRejectReason`, `nudgeOutOfStarZones`, `travelFacing`;
  `isJumpTarget` lifted out of `destinations` (same result); `nonReadyShips` / `supplyCost` /
  `applyCrHit` take a CR multiplier (default 1).
- `src/.../campaign/abilities/SlipspaceJump.kt` — saved `precisePoint`, phase `TRANSITION_BLINK`,
  `onDestinationPicked(dest, point)`, `commitBlink`, `blinkFacing`, `exitArrival` (shared by
  `commitExit` and the TRANSITION_BACK resume), blink resume, cooldown × `BLINK_COOLDOWN_MULT`,
  tooltip line when upgraded.
- `src/.../campaign/abilities/SlipspaceDestinationDialog.kt` — rows (edge / point), deferred map
  open (5 tries), `onPointDialogClosed`, `resolve`.
- `src/.../campaign/abilities/SlipspaceTransitionScript.kt` — `Leg.BLINK`: the collapse hold, no
  music restart, no `reportFleetJumped`.
- `src/.../campaign/systems/SlipspaceSystem.kt` — `SLIPSPACE_ROUTE_POINT_KEY`,
  `prepareSlipspaceRoute(..., exitPoint)` (always sets or unsets it), `slipspaceRoutePoint`,
  `clearSlipspaceRoutePoint`.
- `src/.../campaign/entities/SlipspaceExitDialogPlugin.kt` — "…at the plotted coordinates".
- `src/.../campaign/items/HuragokSlipspaceEngramPlugin.kt` — KDoc (class name kept).
- `src/.../campaign/TesterCache.kt` — the Navigation System in new caches; old caches topped up
  once (`$covenant_remnants_tester_cache_navigation_added`).
- `src/.../commands/CovenantGiveAll.kt` — also installs the upgrade.
- `data/campaign/special_items.csv` — Schematics rename + text; the new item row.
- `data/console/commands.csv` — `CovenantGiveAll` help.
- `mod_info.json` — `ashlib` dependency. `build.gradle.kts` — `compileOnly ../Ashlib_/jars/ashlib.jar`.
- Docs: `CLAUDE.md`, `README.md`, `GRADLE_README.md`, `CHANGELOG.md`, `planning/ARCHITECTURE.md`,
  `planning/PLAN.md`, `planning/TODO.md`, [campaign-slipspace-jump.md](campaign-slipspace-jump.md),
  [tester-cache.md](tester-cache.md).

## Findings that shaped the code

Vanilla (starsector-knowledge, 0.98a-RC8, obf `ui.newui.java` `CampaignEntityPickerDialog`):

- The dropdown of a clicked star lists **every** entry whose `getContainingLocation()` is that
  system, or that is its hyperspace anchor, sorted by display name (case-sensitive `compareTo`). A
  `system.createToken(x, y)` token is such an entry: no `addEntity`, no visibility filter. A name that
  is a prefix of another sorts first, so the edge row (`Corvus star system`) stays above the plot
  row (`Corvus star system - plot coordinates`).
- Star icons come from the entries' systems, so the current system shows with only its token.
- Confirm calls `pickedEntity` and then `dismiss(0)`; only `dismiss(1)` (cancel) calls
  `cancelledEntityPicking`.
- `showCustomVisualDialog` called from inside `pickedEntity` is **ignored**: the dialog's
  "sub-dialog open" field is cleared only after the picker dismisses. Hence the next-frame open from
  the host's `advance`. Ashlib's template gets around it with a reflection call into an Ashes of the
  Domain helper; not used here.
- `showCustomVisualDialog` asks the delegate for its panel plugin, then calls `init` synchronously;
  Escape is not bound by default (the Back button takes it as a shortcut).
- `Sector.setCurrentLocation(same)` is a no-op and `switchLocations` already skips remove / add for
  the same location, so the blink reuses the transition script.
- `BaseThemeGenerator.getOuterRadius` measures terrain and jump points from `system.center` and
  throws without one; `pointBoundsRadius` guards that (fallback `ARRIVAL_FALLBACK`).

Ashlib 2.2.3 (jar, checked against upstream sources with `javap`):

- `SlipSpaceJumpCoordinateUI(width, height, system)`: the two floats are the map panel's pixel size.
  Map space is the system's world space shifted by 26000 and Y-flipped.
- Its `afterFinalize` builds `RestrictedArea(star radius × 5)` zones into a package-private list
  that `clearUI` clears, so the override calls super and then removes the drawn zones
  (`removeComponentOfClass(RestrictedArea)`); ours are new `RestrictedArea(radius × 1.5)`.
- Its `onClick` applies those 5 × zones, so ours never calls super.
- The map builds itself (and calls `afterFinalize`) inside its constructor, before Kotlin properties
  exist: the override touches none of ours. Overlays are added in `init` after the super call.
- `PlanetRenderResizableComponent` checks `isModEnabled("aotd_vok")` before touching Ashes of the
  Domain classes; expected safe without AotD, to confirm in game.
- `MapMainComponent.advance` logs the mouse position every 3 s while hovered (accepted).
- Ashlib is GPL-3.0 upstream (github.com/Kaysaar/AshLib); Covenant links to it and ships none of
  its code.

## Decisions taken during the build (to confirm)

Not covered by the rulings; each is easy to change.

1. Item id `covenant_remnants_slipspace_navigation`; price 100000 and stack 1000 like the
   Schematics; placeholder icon `graphics/icons/cargo/hyperspace_data.png`.
2. Draft text everywhere: both item descriptions and messages, the ability tooltip line, the
   picker's selected text (`<system> - plot coordinates - X LY` / `, in-system`) and info line, the
   map title and info lines, the reject reasons (`Beyond the outer limit of the system.`, `Too close
   to <star>.`, `Too close to the fleet.`), the exit-dialog line.
3. The picker's confirm button keeps "Open rupture" on every row (the picker takes one label).
4. The in-system jump skips `reportFleetJumped` (no location changed).
5. As at the edge, the Ark tube's mouth sits at the point and the fleet ends one fleet radius past it.
6. Bounds are measured from `system.center`; `ARRIVAL_FALLBACK` (6000 su) when a system reports no
   outer radius.
7. The upgrade flag is never revoked; no console toggle besides `CovenantGiveAll`.
8. ~~No startup guard for Ashlib (the launcher enforces declared dependencies)~~ — replaced
   2026-10-17 (developer ruling): `CovenantRemnantsPlugin.onApplicationLoad` throws "This mod
   requires Ashlib to work." when Ashlib is not enabled, like the MagicLib check, because the
   launcher let the game start without it. No version pin, and Ashlib stays out of
   `release.ps1`'s pinned local dependencies (those are our own mods).
9. The map's Confirm has no keyboard shortcut (the template uses G).
10. A refused click keeps the previously plotted point (and says why).
11. Overlay colours: reticle green (positive highlight), outer ring amber, fleet marker in the
    faction colour, zones in Ashlib's red look.
12. The plot row's token sits at the system centre (only the selection marker uses its position).
13. If the system map fails to build (an exception or a missing class inside Ashlib), the dialog
    shows "The system map could not be opened." and logs the error; Back still works.
14. The plotted point is also cleared from the pocket's memory at landing (belt and braces: every
    route write sets or unsets it anyway).

## Verification

- `./gradlew.bat buildMod` builds clean (2026-10-12); the jar carries `SlipspacePoint*`, `MapGlyph`
  and `SlipspaceNavigationSystemPlugin`.
- Code review of the diff (2026-10-12), two fixes: `applyCrHit` now rolls low-CR damage with the
  same multiplier it takes CR with (mattered only with `BLINK_CR_MULT` ≠ 1); the map dialog counts
  as opened only once its contents are built, so a failed build is retried and reported. The
  reviewer could not tell from bytecode whether the Back button's Escape shortcut consumes the key
  (if not, the host dialog's Escape = Cancel would also fire): checklist item 5.
- First in-game try (developer, 2026-10-17): the map showed "The system map could not be opened."
  The log had `NoClassDefFoundError: ashlib/.../SlipSpaceJumpCoordinateUI`: Ashlib was not in
  `enabled_mods.json` and the launcher had started the game anyway. The fallback worked (message,
  logged error, no crash). Fix: enable Ashlib; the startup guard (decision 8) now catches it at load.
- In-game checklist (pending; Ashlib enabled, Ashes of the Domain disabled):
  1. Boot: without Ashlib the game stops at load with "This mod requires Ashlib to work."; with
     it, `starsector.log` has no `NoClassDefFoundError`, no
     `OpenGL error`.
  2. Items: the Schematics show the new name and still teach the jump. The Navigation System is
     refused and kept without the jump, installs (consumed) with it, and a second copy says
     "already installed" and stays. `CovenantGiveAll` grants both. The tester cache holds the new
     item in a new save, and an old tester save gets one copy on load.
  3. Picker without the upgrade: exactly as before.
  4. Picker with the upgrade: the current star has one row, `<system> - plot coordinates`; visited
     systems show the edge row first, then the plot row; never-entered systems the edge row only;
     fuel per row; confirm greyed when fuel is short.
  5. Map: opens right after confirming a plot row; planets and stars render (Ashes of the Domain
     off); no red 5 × rings; our 1.5 × zones, the amber outer ring, the reticle and (in-system) the
     fleet marker show; refused clicks (zone, beyond the ring, too close to the fleet) give the
     disabled sound and a red reason; a valid click shows the coordinates and enables Confirm; Back
     and Escape return to the sector map (Escape must not also cancel the host dialog); cancelling
     there leaves the ability idle, no cooldown, pause state restored.
  6. In-system jump: charge and lurch as today; 1 LY of fuel and the CR hit taken; collapse, fade, no
     music restart; the fleet slides out of an Ark tube at the point, facing away from where it
     started; the cooldown starts.
  7. Other system: the corridor as today; the exit dialog says "at the plotted coordinates"; the
     fleet lands at the point facing origin → destination; a later edge jump to the same system
     lands at the edge again.
  8. Binary system: a point next to an orbiting star is nudged out of its zone at arrival.
  9. Save / load: mid-charge (free cancel), mid in-system jump, in the pocket after a plotted pick,
     mid-exit.
  10. UI scale 100 % and 150 %: the map dialog covers about 90 % of the screen (centred), the map
      fills it above the info line and buttons.

## Open items

- The in-game checklist above.
- Tuning: the five new tunables, overlay colours, dialog layout.
- Art and final text for both items.
- The granting quest (both items are candidate rewards).
- Risks: systems larger than Ashlib's ±26000 su map are clipped; an Ashlib update that changes
  `SlipSpaceJumpCoordinateUI` (constructor, `afterFinalize`, `onClick`) breaks the map (no version
  pin).

# Changelog

All notable changes to Covenant Remnants, newest version first.

## Unreleased

### Breaking changes

- Changed Covenant Remnants to require the new LemonLib release, which now provides the Kotlin runtime, so update LemonLib along with this mod or the game will not start.
- Removed LazyLib from Covenant Remnants' declared dependencies, so the mod no longer lists it in the launcher. You still need LazyLib installed because MagicLib requires it.
- Added Ashlib as a required dependency for the new slipspace system map, so install and enable Ashlib (it also needs LazyLib) or the game will not start.

### New features

- Added the Covenant Slipspace Navigation System, a right-click item that upgrades Slipspace Jump so you can plot the exact exit point on a system map in any star system you have visited.
- Added in-system jumps to the upgraded Slipspace Jump, so you can reappear at any plotted point of your current system for one light-year's worth of fuel.
- Added in-combat hull repair to the Huragok Crew hullmod, using elite Combat Endurance's numbers and stacking with the skill, so Huragok-crewed ships patch themselves up mid-fight.
- Added the Capacitor Discharge hullmod, which boosts energy weapon damage by up to 15% for 5 seconds after an active vent, so a well-timed vent sets up your next strike.
- Added the Target Lattice hullmod, which raises your weapons' damage by up to 20% while you keep hitting the same enemy ship, so focused fire pays off.
- Added the Overcharged Emitters hullmod, which speeds up energy weapon fire by up to 20% above half flux, so running hot hits harder at the risk of longer overloads.

### Behavioral changes

- Changed Covenant shield regeneration to slow down once flux passes half capacity and stop at full flux, so pushing a Covenant ship's flux up now delays its shield recovery.
- Changed the Covenant Shield hullmod description to list the flux level where regeneration starts slowing, so you can see the falloff before taking a ship into battle.
- Changed the Fleet Slipspace Jump AI to count less shield regeneration when its ship is at high flux, so Covenant ships are less likely to strand themselves next to enemy heavies while running hot.
- Renamed the Huragok Slipspace Engram to Covenant Slipspace Drive Schematics, so its name says what it teaches; copies you already own keep working.
- Added the Slipspace Navigation System to the Corvus tester cache, existing tester saves included, so testers can try exact-point jumps right away.
- Changed Huragok Crew to grant its max CR bonus only on ships with Covenant Systems, so it no longer raises CR on other hulls.
- Changed Huragok Crew's repair rate bonus to 33%, so it now fully cancels the Covenant Systems repair penalty.
- Changed the Covenant Shield hullmod to reduce maximum flux capacity by 20%, so the shield now costs Covenant ships some flux headroom; the hullmod description lists the penalty.
- Changed every hull that uses the Covenant shield to always carry the Covenant Shield hullmod as built-in, so no shielded ship can end up without it.
- Changed Plasma Capacitors to make energy weapons fire and regenerate ammo 20% faster at 20% more flux per shot (beams pay no extra flux), so it is now a damage hullmod instead of a copy of vanilla's flux hullmods.
- Removed the dock requirement from Plasma Capacitors and listed it under the Weapons refit filter, so you can fit it anywhere and find it next to the other weapon hullmods.
- Added the three new weapon hullmods to the Corvus tester cache, existing tester saves included, so testers can try them right away.

## v0.1.2 — 2026-10-12

### Behavioral changes

- Changed the Covenant shield outline to follow the hull with smoothly rounded corners and a clear gap outside the sprite, so the shield now covers the whole ship instead of cutting into it and no longer shows polygon edges.
- Changed Covenant ships, fighter wings, weapons and hullmods to drop only from defeated Covenant fleets, so they no longer show up in random salvage, derelicts, stations or as random blueprints.

### Bug fixes

- Fixed the star system background turning black after a slipspace exit, so your destination's backdrop now shows without reloading.
- Fixed the music after a slipspace exit staying on the previous system's track, so you now hear the music of the system you arrive in.

## v0.1.1 — 2026-10-10

### Behavioral changes

- Changed the Fleet Slipspace Jump aiming rings to thicken with your UI scale, so they stay as easy to read at 150 % as at 100 %.

### Bug fixes

- Fixed a crash on loading any save made after a slipspace jump, so those saves (including ones that already crashed) load normally again.
- Fixed Covenant shields drawing in the wrong place and shape at any UI scale other than 100 % or any resolution other than 1920 × 1080 (ultrawide included), so they now sit on the hull at every setting.
- Fixed shield-hit ripples warping the wrong part of the screen under UI scaling, so they now bend only the space right behind the shield.

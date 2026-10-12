# Changelog

All notable changes to Covenant Remnants, newest version first.

## Unreleased

### Breaking changes

- Changed Covenant Remnants to require the new LemonLib release, which now provides the Kotlin runtime, so update LemonLib along with this mod or the game will not start.
- Removed LazyLib from Covenant Remnants' declared dependencies, so the mod no longer lists it in the launcher. You still need LazyLib installed because MagicLib requires it.

### Behavioral changes

- Changed Covenant shield regeneration to slow down once flux passes half capacity and stop at full flux, so pushing a Covenant ship's flux up now delays its shield recovery.
- Changed the Covenant Shield hullmod description to list the flux level where regeneration starts slowing, so you can see the falloff before taking a ship into battle.
- Changed the Fleet Slipspace Jump AI to count less shield regeneration when its ship is at high flux, so Covenant ships are less likely to strand themselves next to enemy heavies while running hot.

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

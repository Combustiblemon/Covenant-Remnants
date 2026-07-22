# Covenant Remnants

A [Starsector](https://fractalsoftworks.com/) mod (game version `0.98a`) that brings the **Covenant**
from Halo into the game: ships, weapons, fighters, and items, plus new quests and campaign content.

Covenant ships are **long-range, energy-shielded, plasma-armed elite brawlers** — few but powerful,
slightly above vanilla in strength and cost, and rarer to obtain. They are strong head-on but
vulnerable when swarmed by many small, maneuverable ships. Content is **quest-gated** — earned
through the mod's quests, not bought or salvaged. The mod does **not** add a new faction, and uses
its own **custom OpenGL/GLSL shaders** (not GraphicsLib) for shields, plasma glow, and slipspace.

This is a non-commercial fan work, not affiliated with or endorsed by Microsoft, 343 Industries, or
Bungie.

## Requirements

- Starsector `0.98a`
- [MagicLib](https://fractalsoftworks.com/forum/index.php?topic=13718.0)
- [LazyLib](https://fractalsoftworks.com/forum/index.php?topic=5444.0)

## Documentation

- [CLAUDE.md](CLAUDE.md) — project context, fixed design decisions, and the AI-assistant working rules.
- [planning/DOCUMENTATION.md](planning/DOCUMENTATION.md) — structure, naming, and content conventions.
- [planning/ARCHITECTURE.md](planning/ARCHITECTURE.md) — how the mod is built (layout, entry point, subsystems).
- [planning/PLAN.md](planning/PLAN.md) — roadmap, status, and open questions.
- [GRADLE_README.md](GRADLE_README.md) — full build/toolchain details.

## Contributing

The mod is written in **Kotlin** and built with **Gradle**. You need JDK 8+ and a local Starsector
install (the build references the core jars).

Build steps:

1. Clone the repository into your Starsector `mods/` folder.
2. Open the project in IntelliJ IDEA (Gradle import) or your IDE of choice.
3. Build the mod jar: `./gradlew buildMod` — compiles `src/` into `jars/CovenantRemnants.jar`.
4. Enable "Covenant Remnants" in the launcher and run the game to test.
5. Package a release: `./gradlew packageMod` — assembles the distributable zip.

`./gradlew cleanMod` clears build output. See [GRADLE_README.md](GRADLE_README.md) for prerequisites,
IDE setup, and troubleshooting.

### Code style

- Kotlin for all code, organised by feature under `org.combustiblelemon.covenantremnants` (see
  [planning/ARCHITECTURE.md](planning/ARCHITECTURE.md)).
- For rendering, prefer OpenGL shaders. Simple example:
  `src/org/combustiblelemon/covenantremnants/utils/render/MinimalShader.kt`; more complex:
  `.../utils/render/ShieldShader.kt`.
- New content ids use the `covenant_remnants_` prefix (see [planning/DOCUMENTATION.md](planning/DOCUMENTATION.md)).

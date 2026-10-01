# Project context — target design

What this project is and the fixed decisions that shape it. Structure rules: [planning/DOCUMENTATION.md](planning/DOCUMENTATION.md).
Project architecture: [planning/ARCHITECTURE.md](planning/ARCHITECTURE.md).
Roadmap and status: [planning/PLAN.md](planning/PLAN.md).

## What this is

**Covenant Remnants** is a Starsector mod (game version `0.98a`) that adds the **Covenant** from
Halo to the game: ships, weapons, fighters, and items, plus new quests and campaign content. It is
built to be compatible with the base game and other mods while giving Covenant content a distinct
feel — long-range, energy-shielded, plasma-armed, and visually set apart by custom OpenGL shaders.

The mod is a long-term project with ongoing updates. It balances new content against existing game
mechanics: enhancing the experience without disrupting the core game.

**Explicit non-goals:**

- **No new faction.** Covenant content is added to the game as ships/weapons/items/quests, not as a
  playable or market faction with its own territory, economy, or fleets-on-the-map.
- Not a total conversion. Vanilla remains the reference point.

## Fixed decisions

These are settled. Do not re-open them without asking the developer.

- **Lore fidelity — faithful names.** Use real Covenant designations (CCS-class battlecruiser, CAS,
  ORS, Seraph, Phantom, Banshee, plasma torpedo, energy projector, etc.). This is a non-commercial
  fan work with no affiliation with or endorsement by Microsoft / 343 Industries / Bungie.
- **Balance — slightly above vanilla.** Covenant ships and weapons are more powerful than their
  vanilla counterparts, and correspondingly **more expensive and rarer**. Slightly above, not
  wildly overpowered — vanilla is still the yardstick (see Design references).
- **Acquisition — quest-gated only.** Covenant ships and weapons are obtained through the new
  quests. They are **not** sold on markets and **not** dropped through normal salvage.
- **Combat identity — long-range elite brawlers.** Strong energy shields, plasma weapons, few but
  elite, fast and agile. Strong against whatever is directly in front of them; **weak when
  overwhelmed by large numbers of smaller, more maneuverable ships.** Design ships and weapons to
  express this shape — reward positioning and focus, punish getting swarmed.
- **Weapon mounts — energy + synergy only.** Covenant hulls carry only `ENERGY` and `SYNERGY`
  weapon slots — no ballistic, missile-only, hybrid, composite, or universal mounts. Keeps every
  ship on the plasma/energy identity.
- **Custom shaders, not GraphicsLib.** The look and feel (energy shields, plasma glow, slipspace
  portals) uses the mod's own GLSL shaders. GraphicsLib is **not** a dependency.
- **Kotlin.** Code is Kotlin, compiled to `jars/CovenantRemnants.jar` via Gradle.
- **Canonical id prefix: `covenant_remnants_`.** New content uses the underscore-separated prefix.
  The legacy `covenantremnants_` prefix has been fully migrated out of `data/` and `src/`; never
  reintroduce it (the Kotlin package `covenantremnants` is unaffected).

## Stack

- **Language/build:** Kotlin (JVM, Java 8 target), Gradle Kotlin DSL. See [planning/ARCHITECTURE.md](planning/ARCHITECTURE.md).
- **Declared dependencies (required):** LemonLib, MagicLib, LazyLib.
- **Optional dependencies (ruling 2026-10-01):** LunaLib, Console Commands — compile-only, never
  declared in `mod_info.json`; the mod must run without them (Console Commands code stays isolated
  in `commands/`, loaded only via `data/console/commands.csv`).
- **Rendering:** custom OpenGL/GLSL shaders (`.frag`/`.vert` in `data/shaders/`, Kotlin loaders in
  `src/.../utils/render/`).

## Design references

Design and balance thinking lives in these files — consult them, do not duplicate them here:

- [design_document.md](design_document.md) — the fun-check questions and the rule to use vanilla
  weapons/wings as the reference for stats and ship fits.
- [balance.md](balance.md) — per-feature balance notes and TODOs.

## Working with an AI assistant

The assistant uses `caveman ultra` skill all the time.
The assistant does not hold design authority. It **asks, it does not guess.**

- **Any design decision — ask first.** Feel, numbers, scope, naming, UX, architecture shape, what a
  feature does or does not do: stop and ask the developer. One question at a time if that is
  clearer. A plausible-looking guess that lands in the repo is worse than a blocked task, because it
  silently becomes the design.
- **Ambiguity is a question, not a choice.** If the spec, this file, or the task allows two readings,
  the assistant does not pick the "reasonable default" — it surfaces both readings and waits.
- **No silent decisions.** Do not bury a design call in an implementation and mention it afterwards.
  If something had to be decided to make the code compile, say so before writing it.
- **Bugs are the exception.** Fixing a defect — code that does not do what it was clearly meant to
  do — needs no permission. Diagnose and fix it. If the "bug" turns out to be an unspecified
  behaviour rather than a broken one, it is a design question: ask.
- **Branches are the developer's, manually (ruling 2026-07-20).** The assistant never creates,
  switches, or merges git branches. Commits, when asked for, go on whatever branch is currently
  checked out.
- **Document every change.** Each implementation gets its own file in
  [planning/implementations/](planning/implementations/) — what was built, files touched, key
  decisions, and open items. Keep [planning/TODO.md](planning/TODO.md) current with what is left to
  do (tick items off, add new ones). Do this as part of the work, not after being asked. Conventions:
  [planning/DOCUMENTATION.md](planning/DOCUMENTATION.md).

### Agent model assignment

When spawning subagents (Agent tool), always pass an explicit `model` override matched to
task type, regardless of any model already set in the target agent's own frontmatter:

- **Search / read / investigate** (locating code, reading files, grepping references,
  codebase mapping — no edits) → `sonnet`
- **Write / implement** (edits, new files, refactors) → `opus`
- **Plan / design** (architecture, implementation strategy, no edits) → `opus`,
  unless the task is mostly reading and thinking, in which case → `sonnet`
- **Test / verify** (unit tests, integration tests, QA) → `sonnet`
- The model of the target agent is **not** inherited by subagents. Each subagent gets its own
  model based on the task type, even if the parent agent is already using a different model.
- The main agent must always check the plans and outputs of subagents for correctness and completeness.

A single agent invocation that mixes phases (e.g. explore then write) takes the model of its
primary/heavier phase.

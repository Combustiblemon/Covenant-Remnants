# Implementation — graphics under `graphics/covenant_remnants/` (2026-10-10)

The developer ruled that `graphics/covenant_remnants/` holds all of the mod's graphics. Two folders
sat beside it under `graphics/` and have been moved in:

| Old | New | Files |
| --- | --- | --- |
| `graphics/fx/<effect>/` | `graphics/covenant_remnants/fx/<effect>/` | 77 (energy_projector, plasma_beam, plasma_bombardment, plasma_mortar, plasma_torpedo, shield, slipspace_ark, slipspace_rupture) |
| `graphics/missiles/` | `graphics/covenant_remnants/missiles/` | 4 projectile sprites |

Notes on the move:

- No file names collided. `covenant_remnants/fx/shield/` (holding `noise.png`) and the empty
  `covenant_remnants/missiles/` already existed, and the moved files merged into them.
- All 81 files were moved with `git mv`, so their history follows them.
- `graphics/` now holds only `covenant_remnants/`.

## References updated

The rewrite was scripted. It changed a path only when that path resolves to one of the moved files
or folders, so vanilla references stay untouched:

- `data/config/engine_styles.json` → `graphics/fx/engineglow32b.png`, `engineflame32b.png`;
- `data/config/hull_styles.json` → `graphics/fx/nebula_colorless.png`, `radial_fx.png`;
- the `CORE = "../../starsector-core/graphics/fx/"` constants in three reference render tools.

Runtime references (157 rewrites in all, docs and tools included):

- `data/config/settings.json` — every sprite id path in the `graphics` → `covenant_remnants`
  category. The sprite ids are unchanged, so the Kotlin `getSprite` calls need no change.
- The 8 `.proj` files in `data/weapons/proj/` — `sprite` paths.
- Kotlin: comments only, in `RuptureTexture.kt`, `ShieldTexture.kt` and `TorpedoEffects.kt`. No code
  builds these paths; everything loads through the `settings.json` sprite ids.

Docs and dev tools:

- `planning/` (ARCHITECTURE, DOCUMENTATION, TODO, next-session notes, 14 implementation docs).
- `assets/reference/**` READMEs and texture-generator / render tools. This includes 9 tools that
  build the path with `os.path.join(..., "graphics", "fx" | "missiles", ...)`, so a regenerated
  texture lands in the new place. That folder is gitignored and local only.
- `DOCUMENTATION.md` convention: all mod graphics go under `graphics/covenant_remnants/`, and VFX
  textures under `graphics/covenant_remnants/fx/<effect>/`.

## Verification done

- Every `graphics/...png|jpg` path in `data/` and `src/` resolves, either to a mod file or to a
  vanilla file in `starsector-core/`.
- No old `graphics/fx` / `graphics/missiles` path pointing at our files is left anywhere in the repo.
- `package_mod.sh` ran with BUILD SUCCESSFUL. `Covenant Remnants.zip` still has 271 files, and every
  graphics file sits under `graphics/covenant_remnants/` (fx 77, missiles 4, ships 19, weapons 4,
  backgrounds 1, misc 1). `noise.png` is still excluded.

## In-game checklist

1. No `Sprite ... not found` / missing-texture errors in `starsector.log` at startup.
2. These still look right: the Pulse Laser, Repeater and Autocannon bolts, the torpedo bodies, the
   Mortar and Bombardment shells, and the shield, rupture, Ark portal, projector and beam effects.

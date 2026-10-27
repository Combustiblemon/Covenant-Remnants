# Shield collision pass-through: diagnosis + logging (2026-10-27)

Developer report: other hulls and asteroids go "through" the Covenant shield; shots look fine.
Rulings (developer 2026-10-27): no collision-geometry change (a bounds swap was proposed and
rejected), no fix yet; add logging first so the suspected hull damage shows up in
`starsector.log`.

## Diagnosis (0.98a-RC8 internals, via the starsector-knowledge skill)

Two separate things.

### 1. Visual overlap: the bubble has no collision geometry (accepted, no fix)

- The Covenant shield is a ship system in the phase-cloak slot plus a `DamageTakenModifier`
  (`systems/CovenantShieldsStats.kt`). `ship.getShield()` is null on every Covenant hull
  (`shield type = PHASE`).
- Engine ship-vs-entity collision (obf `combat.E`, collision pass): when a ship has a raised
  `ShieldAPI`, its shield circle (radius, arc) is the collider; otherwise the hull `exactBounds`
  polygon (or the `collisionRadius` circle for entities without bounds, e.g. asteroids). Vanilla
  shields therefore stop ramming at the shield radius. Covenant ships always take the
  bounds-vs-bounds branch, so hulls and asteroids stop at the sprite, 14 world units inside the
  drawn rim (`ShieldOutline.margin`, raised from 4 to 14 on 2026-10-12).
- Projectiles and beams also hit the hull bounds, not the rim; the hit flare drawn at the hit
  point hides it.
- The engine does not phase the ship (only vanilla `PhaseCloakStats` calls `setPhased`; the mod
  never does), so nothing passes through the hull itself.

Rejected option, kept for the record: swap the ship bounds (`BoundsAPI.clear/addSegment`,
`setCollisionRadius`) to the drawn outline while the shield is fully raised. Engine facts that
would constrain it: bounds are cloned per ship (safe to mutate); the broad phase uses
`collisionRadius` circles, so the radius must grow with the polygon; a hit point outside the
armor grid deals zero damage (`Ship.applyDamage` -> armor grid, off-grid returns an empty
result), so unabsorbed rim hits (charge-up sweep gap, break overflow) would be lost; bounds
`isBreakEdge` flags drive hulk break-up, so bounds must be restored before death; and the render
plugin builds the outline from `exactBounds.origSegments`, which `addSegment` also writes, so the
original hull polygon would have to be cached to avoid margin-on-margin growth.

### 2. Collision damage bypasses the shield pool (the suspected real issue)

- The engine's collision response (obf `combat.E`, ~lines 493-549) calls
  `ship.applyDamage(point, new Damage(amount, false, KINETIC|ENERGY), ..., otherEntity)`
  without calling `setParamAboutToApplyDamage` first. `Ship.applyDamage` passes
  `paramAboutToApplyDamage` (null here; it is reset to null after every hit) as the `param` of
  every `DamageTakenModifier`. The API javadoc lists `null` as a valid `param`.
- `ShieldDamageListener.modifyDamageTaken` returns before touching the shield when
  `param === null`. So ramming damage skips the shield pool and lands on armor / hull even with
  the shield fully up. Beams, projectiles, missiles and EMP arcs arrive with a non-null param and
  are absorbed.
- Two other early-outs on the same path: `point === null` and `hitPastShield` (the charge-up
  sweep gap).

## What was built

`src/org/combustiblelemon/covenantremnants/systems/CovenantShieldsStats.kt` only. Logging, no
behaviour change.

- Companion logger `log` (`Global.getLogger(CovenantShieldsStats::class.java)`), info level,
  every line prefixed `[CovenantShield]`.
- `pendingLog: String?` on the stats script: `ShieldDamageListener.noteUnshielded(reason, ...)`
  records a hit that reached the ship with the shield up (`phaseCloak.effectLevel > 0`) but was
  not absorbed; nothing is recorded while the shield is down. Reasons:

  | Path in `modifyDamageTaken` | `reason` |
  | --- | --- |
  | `param === null` early return (collision damage) | `null-param` |
  | `point === null` / no sweep point | `no-point` |
  | `hitPastShield` during the charge-up sweep | `past-sweep` |
  | breaking hit with overflow (adds `absorbed=`, `passedFraction=`) | `break-overflow` |

- `ShieldDamageConverter.reportDamageApplied` (runs right after the hit is applied, same call)
  completes the line with the source and the result split and emits it, then clears
  `pendingLog` (also when the ship is dead, so nothing leaks into the next hit).

Example line:

```text
[CovenantShield] unshielded hit reason=null-param ship=covenant_remnants_cruiser1 "ISS Example" param=null dmg=312.0 type=KINETIC dps=false point=(1234, -56) shieldHit=false cloak=1.00 shieldHP=18000/20000 broken=false source=Asteroid -> hull=41.2 armor=270.8 shields=0.0 emp=0.0
```

`source` is what the engine passed to `applyDamage`: for a collision it is the other entity
(`Ship:<hullId>` or `Asteroid`), for a weapon hit the firing ship. Absorbed hits are not logged
(beam ticks would flood the log); add a second helper behind a constant if ever wanted.

## How to read it

Log: `../../starsector-core/starsector.log`, filter `[CovenantShield]`.

- Collisions with the shield up should show `reason=null-param ... source=Asteroid|Ship:...`
  with non-zero `hull=` / `armor=` while `cloak=1.00` and `shieldHP` is well above zero. That
  confirms collision damage bypasses the shield.
- Weapon hits with the shield up should produce no line, except `past-sweep` during the raise
  and `break-overflow` on the breaking hit.

## Result (developer run, 2026-10-27)

Three collisions on a supercap1 with the shield full (`cloak=1.00`, `shieldHP=20000/20000`), all
`reason=null-param`, all `shields=0.0`, damage landed on armor: a hull ram at 336 raw
(armor 40.3), an asteroid at 1.8 raw (armor 0.2), a second hull ram at 37.7 raw (armor 4.5).
Weapon hits produced no lines (absorbed). Confirmed: collision damage bypasses the shield pool.

## Fix (developer ruling 2026-10-27: route collisions through the shield)

`ShieldDamageListener.modifyDamageTaken`: the `param === null` early return is gone. Collision
damage now takes the same path as projectiles: absorbed into the shield pool at
`shieldDamageMult` (0.3) times `shieldDamageTakenMult`, hit flare at the contact point, overflow
to armor / hull only on the breaking hit. The `null-param` log reason no longer exists; a ram
that breaks the shield shows up as `break-overflow`. The `source` fallback for the floating
damage text is the ship itself (the listener does not receive the other entity).

## Open items

- [x] Read the log in game and confirm (above).
- [x] Fix: collisions routed through the shield pool (above).
- [x] In-game check of the fix (developer 2026-10-27): the post-fix run logged no unabsorbed
  hits; the pre-fix run had three `null-param` rams to armor.
- Remove the logging in a future pass (developer 2026-10-27: keep it for now). Pieces:
  companion `log`, `pendingLog`, `ShieldDamageListener.noteUnshielded` and its four call sites,
  the emit block at the top of `ShieldDamageConverter.reportDamageApplied`.
- The 14-unit visual overlap stays (developer 2026-10-27: leave as is).

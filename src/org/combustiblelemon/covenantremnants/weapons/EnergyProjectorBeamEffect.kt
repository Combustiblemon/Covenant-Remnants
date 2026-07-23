package org.combustiblelemon.covenantremnants.weapons

import com.fs.starfarer.api.combat.BeamAPI
import com.fs.starfarer.api.combat.BeamEffectPlugin
import com.fs.starfarer.api.combat.CombatEngineAPI
import com.fs.starfarer.api.combat.DamageType
import com.fs.starfarer.api.combat.ShipAPI
import org.combustiblelemon.covenantremnants.plugins.EnergyProjectorPierceRenderPlugin
import org.lazywizard.lazylib.MathUtils
import org.lazywizard.lazylib.CollisionUtils
import org.lwjgl.util.vector.Vector2f
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Energy Projector — pierce effect.
 *
 * The projector fires like a Tachyon Lance (a heavy burst beam, not a continuous one). Against a
 * defended target it does ordinary damage, but once the exact point it is burning into has NO shield
 * covering it AND its armor is depleted, two things happen:
 *   1. **Melt** — it applies rapidly-ramping bonus HULL damage the longer the burst holds on exposed
 *      hull (resets when the beam leaves, the point re-shields, or armor recovers).
 *   2. **Pierce-through** — the lance punches through the exposed hull and re-emerges from the
 *      target's far side, continuing as if a fresh beam were fired from that exit point: it hits the
 *      FIRST thing it reaches (shield surface or hull), is blocked by shields, and stops there.
 *
 * Wired as the `beamEffect` of `data/weapons/covenant_remnants_energyprojector.wpn`. Turn-lock while
 * firing is handled separately by [EnergyProjectorEffect] (the weapon's `everyFrameEffect`).
 */
class EnergyProjectorBeamEffect : BeamEffectPlugin {

    private val rampDuration = 1f            // seconds of continuous burn to reach full ramp
    private val rampCap = 4f                 // melt bonus = up to (rampCap - 1) x base beam damage
    private val armorExposedFraction = 0.05f // armor cell counts as "depleted" below this fraction
    private val pierceLength = 800f          // how far past the exit point the lance keeps going
    private val pierceDamageFraction = 0.75f // pierce damage vs base beam damage
    private val covenantShieldId = "covenant_remnants_covenantshield"

    private var rampTime = 0f
    private var lastTarget: ShipAPI? = null
    private var pierceRender: EnergyProjectorPierceRenderPlugin? = null

    override fun advance(amount: Float, engine: CombatEngineAPI, beam: BeamAPI) {
        if (engine.isPaused) return

        val target = beam.damageTarget as? ShipAPI
        if (target == null) {
            rampTime = 0f
            lastTarget = null
            return
        }

        // reset the ramp if the beam moved to a different target
        if (target !== lastTarget) {
            rampTime = 0f
            lastTarget = target
        }

        val point = beam.to
        if (!isExposedAt(target, point)) {
            rampTime = 0f
            return
        }

        // Ramp = real seconds of continuous burn on exposed hull. Accumulate on EVERY exposed frame
        // (in seconds), so ramp speed is identical at 60 / 120 / any fps. Must happen before the
        // damage-tick gate below — beams only report didDamageThisFrame() on their ~0.1s batch ticks,
        // so gating the ramp on it would make it advance per-tick and scale with framerate.
        rampTime = min(rampDuration, rampTime + amount)
        val ramp = 1f + (rampCap - 1f) * (rampTime / rampDuration)

        // Pierce segment geometry: continue the lance line past the target. The visible segment starts
        // at the FAR-side exit surface (not the near entry hit), so the part travelling through the ship
        // body is never drawn — the hull sprite covers it — and the lance only reappears where it punches
        // out the back.
        val from = beam.from
        var dx = point.x - from.x
        var dy = point.y - from.y
        val len = sqrt(dx * dx + dy * dy)
        if (len < 0.0001f) return
        dx /= len; dy /= len
        val exit = farSideExit(target, point, dx, dy)

        // The pierce continues as if a fresh beam were fired from the exit point along the same line:
        // it strikes the FIRST thing it reaches (nearest shield surface or hull surface), is blocked by
        // shields, and stops there — it does not tunnel through to a point behind the target. Recomputed
        // every exposed frame so the visual is blocked live (e.g. an interposing shield stops it now).
        val hit = firstPierceHit(engine, exit, dx, dy, beam.source, target)
        val end = hit?.point ?: Vector2f(exit.x + dx * pierceLength, exit.y + dy * pierceLength)

        // Visual: re-render the real beam along the pierce segment. Refreshed on EVERY exposed frame
        // (before the damage-tick gate) so the pierce tracks the lance continuously — including between
        // the beam's ~0.1s damage ticks and during its burst fade — instead of strobing per tick.
        refreshPierceVisual(engine, beam, exit, end)

        // Beams batch damage into ~0.1s ticks; between ticks (and during the burst's fade-out) they
        // deal no weapon damage. Only apply melt / pierce damage on the frames the beam actually hits.
        if (!beam.didDamageThisFrame()) return

        // The beam's damage this tick. For beams computeDamageDealt uses the beam's own batch duration
        // (dpsDuration), NOT the passed arg, so this is the true per-tick damage — already
        // framerate-independent (fewer, bigger ticks vs more, smaller ticks integrate to the same DPS).
        val frameBase = beam.damage.computeDamageDealt(amount)

        // 1) melt: bonus hull damage this tick = this tick's base beam damage * (ramp - 1)
        val bonus = frameBase * (ramp - 1f)
        if (bonus > 0f) {
            engine.applyDamage(
                target, point, bonus, DamageType.HIGH_EXPLOSIVE, 0f,
                true /*bypassShields*/, false /*dealsSoftFlux*/, beam.source, false /*playSound*/
            )
        }

        // 2) pierce-through damage: energy damage at the exact point the pierce beam first makes
        // contact. bypassShields = false, so a raised shield eats it just like the real beam would.
        if (hit != null) {
            val dmg = frameBase * pierceDamageFraction
            if (dmg > 0f) {
                engine.applyDamage(
                    hit.ship, hit.point, dmg, DamageType.ENERGY, 0f,
                    false /*bypassShields*/, false /*dealsSoftFlux*/, beam.source, false /*playSound*/
                )
            }
        }
    }

    /**
     * The point where the lance leaves the target's far side. Cast from a point beyond the ship back
     * toward the entry hit; the first bounds intersection is the exit surface. Falls back to [entry]
     * for entities without usable bounds (e.g. some fighters).
     */
    private fun farSideExit(target: ShipAPI, entry: Vector2f, dx: Float, dy: Float): Vector2f {
        val reach = target.collisionRadius * 3f
        val beyond = Vector2f(entry.x + dx * reach, entry.y + dy * reach)
        return CollisionUtils.getCollisionPoint(beyond, entry, target) ?: entry
    }

    /** Keep one live render plugin per burst; (re)create it when the previous one has expired. */
    private fun refreshPierceVisual(engine: CombatEngineAPI, beam: BeamAPI, point: Vector2f, end: Vector2f) {
        val rp = pierceRender
        if (rp == null || rp.isExpired) {
            val fresh = EnergyProjectorPierceRenderPlugin()
            engine.addLayeredRenderingPlugin(fresh)
            pierceRender = fresh
            fresh.refresh(beam, point, end)
        } else {
            rp.refresh(beam, point, end)
        }
    }

    private class PierceHit(val ship: ShipAPI, val point: Vector2f, val dist: Float)

    /**
     * First entity the pierce beam reaches on the ray from [exit] along ([dx],[dy]), within
     * [pierceLength]. Considers each ship's raised shield (ray vs shield circle, clamped to its arc)
     * and its hull bounds, whichever the ray touches first, and returns the nearest such contact
     * across all ships. Excludes the pierced [target] and the firing [source]. Null if nothing is hit.
     */
    private fun firstPierceHit(
        engine: CombatEngineAPI, exit: Vector2f, dx: Float, dy: Float,
        source: ShipAPI?, target: ShipAPI
    ): PierceHit? {
        var best: PierceHit? = null
        val end = Vector2f(exit.x + dx * pierceLength, exit.y + dy * pierceLength)

        for (other in engine.ships) {
            if (other === target || other === source) continue
            if (!other.isAlive || other.isHulk || other.isPhased) continue

            var hitPoint: Vector2f? = null

            // Raised shield first — it sits outside the hull, so the beam meets it before the bounds.
            val shield = other.shield
            if (shield != null && shield.isOn) {
                val sp = rayCircleEntry(exit, dx, dy, pierceLength, shield.location, shield.radius)
                if (sp != null && shield.isWithinArc(sp)) hitPoint = sp
            }
            // Otherwise (no shield there / out of arc) the hull bounds.
            if (hitPoint == null) hitPoint = CollisionUtils.getCollisionPoint(exit, end, other)
            if (hitPoint == null) continue

            val d = MathUtils.getDistance(exit, hitPoint)
            if (best == null || d < best.dist) best = PierceHit(other, hitPoint, d)
        }
        return best
    }

    /**
     * Nearest intersection of the ray (unit [dx],[dy] from [origin], length ≤ [maxLen]) with the circle
     * at [center] radius [r], or null if it misses / is out of range. Used for shield hit points.
     */
    private fun rayCircleEntry(
        origin: Vector2f, dx: Float, dy: Float, maxLen: Float, center: Vector2f, r: Float
    ): Vector2f? {
        val ox = origin.x - center.x
        val oy = origin.y - center.y
        val b = ox * dx + oy * dy
        val c = ox * ox + oy * oy - r * r
        val disc = b * b - c
        if (disc < 0f) return null
        val sq = sqrt(disc)
        var t = -b - sq
        if (t < 0f) t = -b + sq   // origin inside the circle: take the exit intersection
        if (t < 0f || t > maxLen) return null
        return Vector2f(origin.x + dx * t, origin.y + dy * t)
    }

    /** True when [point] on [target] has no shield covering it and depleted armor. */
    private fun isExposedAt(target: ShipAPI, point: Vector2f): Boolean {
        val shield = target.shield
        if (shield != null && shield.isOn && shield.isWithinArc(point)) return false
        // Covenant custom shield is delivered as the phase cloak, not getShield()
        val cloak = target.phaseCloak
        if (cloak != null && cloak.id == covenantShieldId && cloak.effectLevel > 0f) return false

        val grid = target.armorGrid ?: return true
        val cell = grid.getCellAtLocation(point) ?: return true
        return grid.getArmorFraction(cell[0], cell[1]) < armorExposedFraction
    }
}

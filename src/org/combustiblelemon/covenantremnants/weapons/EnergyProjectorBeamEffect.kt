package org.combustiblelemon.covenantremnants.weapons

import com.fs.starfarer.api.combat.BeamAPI
import com.fs.starfarer.api.combat.BeamEffectPlugin
import com.fs.starfarer.api.combat.CombatEngineAPI
import com.fs.starfarer.api.combat.DamageType
import com.fs.starfarer.api.combat.ShipAPI
import org.combustiblelemon.covenantremnants.plugins.EnergyProjectorPierceRenderPlugin
import org.combustiblelemon.covenantremnants.utils.getPerpendicularIntersectionPoint
import org.lazywizard.lazylib.MathUtils
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
 *   2. **Pierce-through** — the lance punches through the exposed hull and continues behind the
 *      target, dealing energy damage to anything caught on the line past the exit point.
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

        // Beams batch damage into ~0.1s ticks; between ticks (and during the burst's fade-out) they
        // deal no weapon damage. Only apply melt / pierce on the frames the beam actually hits.
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

        // 2) pierce-through: keep going past the exit point and hit whatever is behind
        pierceThrough(engine, beam, target, point, frameBase)
    }

    private fun pierceThrough(
        engine: CombatEngineAPI, beam: BeamAPI,
        target: ShipAPI, point: Vector2f, frameBase: Float
    ) {
        val from = beam.from
        var dx = point.x - from.x
        var dy = point.y - from.y
        val len = sqrt(dx * dx + dy * dy)
        if (len < 0.0001f) return
        dx /= len; dy /= len
        val end = Vector2f(point.x + dx * pierceLength, point.y + dy * pierceLength)
        val segment = arrayOf(point, end)

        // visual: draw the lance continuing past the exit point (self-fades when we stop refreshing)
        val rp = pierceRender
        if (rp == null || rp.isExpired) {
            val fresh = EnergyProjectorPierceRenderPlugin()
            engine.addLayeredRenderingPlugin(fresh)
            pierceRender = fresh
            fresh.refresh(point, end, beam.width, beam.coreColor, beam.fringeColor)
        } else {
            rp.refresh(point, end, beam.width, beam.coreColor, beam.fringeColor)
        }

        val dmg = frameBase * pierceDamageFraction
        if (dmg <= 0f) return

        for (other in engine.ships) {
            if (other === target || other === beam.source) continue
            if (!other.isAlive || other.isHulk) continue
            val hit = getPerpendicularIntersectionPoint(segment, other.location) ?: continue
            if (MathUtils.getDistance(hit, other.location) <= other.collisionRadius) {
                engine.applyDamage(
                    other, hit, dmg, DamageType.ENERGY, 0f,
                    false /*bypassShields*/, false /*dealsSoftFlux*/, beam.source, false /*playSound*/
                )
            }
        }
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

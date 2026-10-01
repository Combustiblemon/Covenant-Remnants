package org.combustiblelemon.covenantremnants.ai

import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.util.IntervalUtil
import org.combustiblelemon.covenantremnants.systems.CovenantShieldsStats
import org.combustiblelemon.covenantremnants.systems.FortressShield

/**
 * Drives the vanilla Fortress Shield system on Covenant hulls. Vanilla's FORTRESS_SHIELD AI
 * idles on ships without a vanilla shield, so [CovenantShieldsAI] (AI-controlled ships only)
 * runs this instead.
 *
 * Toggles on an estimate of the raw damage that will hit the ship within
 * [FortressShield.AI_LOOKAHEAD] seconds, converted to shield HP; thresholds live in
 * [FortressShield].
 */
class FortressShieldAI(
    private val ship: ShipAPI,
    private val system: ShipSystemAPI,
    private val engine: CombatEngineAPI
) {
    // Same check cadence as vanilla's Fortress AI.
    private val interval = IntervalUtil(0.25f, 0.5f)
    private var sinceLastCheck = 0f
    private var onTime = 0f
    private var lowThreatTime = 0f

    fun advance(amount: Float) {
        onTime = if (system.isOn) onTime + amount else 0f
        sinceLastCheck += amount

        interval.advance(amount)
        if (!interval.intervalElapsed()) {
            return
        }

        val elapsed = sinceLastCheck
        sinceLastCheck = 0f

        val stats = ship.phaseCloak?.script as? CovenantShieldsStats ?: return

        // CovenantShieldsStats forces Fortress off while the shield is down.
        if (ship.phaseCloak?.isOn != true || stats.breakCooldownActive) {
            lowThreatTime = 0f
            return
        }

        // Base shield multiplier only, so Fortress's own reduction does not feed back into the
        // OFF test.
        val projected = estimateIncomingRawDamage(FortressShield.AI_LOOKAHEAD) * stats.shieldDamageMult
        val maxShield = stats.maxShieldHealth

        if (!system.isOn) {
            lowThreatTime = 0f

            val threatened = projected > 0f &&
                    (projected >= FortressShield.AI_ON_FRACTION * maxShield || projected >= stats.shieldHealth)

            if (threatened && !system.isActive && system.canBeActivated() &&
                !ship.fluxTracker.isOverloadedOrVenting
            ) {
                ship.giveCommand(ShipCommand.USE_SYSTEM, null, 0)
            }
        } else {
            lowThreatTime =
                if (projected < FortressShield.AI_OFF_FRACTION * maxShield) lowThreatTime + elapsed else 0f

            if (onTime >= FortressShield.AI_MIN_ON_TIME && lowThreatTime >= FortressShield.AI_OFF_HOLD) {
                ship.giveCommand(ShipCommand.USE_SYSTEM, null, 0)
                lowThreatTime = 0f
            }
        }
    }

    /**
     * Raw damage (no damage-type multipliers — the Covenant shield ignores them) expected to hit
     * the ship within [lookahead] seconds: projectiles and unguided missiles on a colliding path,
     * guided missiles targeting the ship and within reach, and beams currently on the ship.
     */
    private fun estimateIncomingRawDamage(lookahead: Float): Float {
        val location = ship.location
        val velocity = ship.velocity
        val radius = ship.collisionRadius
        val scanRangeSq = FortressShield.AI_SCAN_RANGE * FortressShield.AI_SCAN_RANGE

        var total = 0f

        for (projectile in engine.projectiles) {
            if (projectile.owner == ship.owner || projectile.isFading || projectile.isExpired ||
                projectile.didDamage() || projectile.collisionClass == CollisionClass.NONE
            ) {
                continue
            }

            val dx = projectile.location.x - location.x
            val dy = projectile.location.y - location.y
            val distanceSq = dx * dx + dy * dy
            if (distanceSq > scanRangeSq) {
                continue
            }

            if (projectile is MissileAPI) {
                if (projectile.isFizzling) {
                    continue
                }

                if ((projectile.missileAI as? GuidedMissileAI)?.target === ship) {
                    val reach = projectile.maxSpeed * lookahead + radius
                    if (distanceSq <= reach * reach) {
                        total += projectile.damageAmount
                    }
                    continue
                }
            }

            if (passesWithin(
                    dx, dy,
                    projectile.velocity.x - velocity.x, projectile.velocity.y - velocity.y,
                    radius, lookahead
                )
            ) {
                total += projectile.damageAmount
            }
        }

        for (beam in engine.beams) {
            if (beam.damageTarget !== ship) {
                continue
            }

            val source = beam.source ?: continue
            if (source.owner == ship.owner) {
                continue
            }

            // beam.damage.damage is zeroed between damage ticks; rebuild the DPS from its parts.
            val damage = beam.damage ?: continue
            total += damage.baseDamage * damage.modifier.modifiedValue * beam.brightness * lookahead
        }

        return total
    }

    /**
     * Whether a point at relative position (dx, dy) moving with relative velocity (vx, vy) comes
     * within [radius] of the origin during the next [lookahead] seconds.
     */
    private fun passesWithin(dx: Float, dy: Float, vx: Float, vy: Float, radius: Float, lookahead: Float): Boolean {
        val speedSq = vx * vx + vy * vy
        val closestTime = if (speedSq > 0f) (-(dx * vx + dy * vy) / speedSq).coerceIn(0f, lookahead) else 0f
        val cx = dx + vx * closestTime
        val cy = dy + vy * closestTime
        return cx * cx + cy * cy <= radius * radius
    }
}

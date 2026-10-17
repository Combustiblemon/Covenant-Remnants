package org.combustiblelemon.covenantremnants.hullmods

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.BaseHullMod
import com.fs.starfarer.api.combat.CombatEntityAPI
import com.fs.starfarer.api.combat.DamageAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.listeners.AdvanceableListener
import com.fs.starfarer.api.combat.listeners.DamageDealtModifier
import org.lwjgl.util.vector.Vector2f
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Target Lattice — sustained fire on one enemy ship builds a damage bonus against it.
 *
 *  - The lock is the last enemy ship hit (modules count as their station); fighters and missiles are
 *    ignored. Hitting a different ship moves the lock and resets the bonus.
 *  - The bonus grows for [BUILD_WINDOW] seconds after each hit, holds until [HOLD_TIME], then drains.
 *    Slow guns therefore build slower than steady fire. Shield hits count.
 *  - Swarms are the counter: constant target switching never builds the bonus.
 */
class TargetLattice : BaseHullMod() {
    companion object {
        const val ID = "covenant_remnants_target_lattice"

        const val BUILD_PER_SECOND = 0.05f
        const val MAX_BONUS = 0.20f
        const val BUILD_WINDOW = 1f
        const val HOLD_TIME = 3f
        const val DRAIN_PER_SECOND = 0.10f

        private const val ICON = "graphics/icons/hullsys/targeting_feed.png"
    }

    override fun applyEffectsAfterShipCreation(ship: ShipAPI?, id: String?) {
        ship?.addListener(Lattice(ship))
    }

    override fun getDescriptionParam(index: Int, hullSize: ShipAPI.HullSize?): String? {
        return when (index) {
            0 -> "${(BUILD_PER_SECOND * 100).roundToInt()}%"
            1 -> "${(MAX_BONUS * 100).roundToInt()}%"
            2 -> "${HOLD_TIME.roundToInt()}"
            3 -> "${(DRAIN_PER_SECOND * 100).roundToInt()}%"
            else -> null
        }
    }

    class Lattice(private val ship: ShipAPI) : AdvanceableListener, DamageDealtModifier {
        private var lockedTarget: ShipAPI? = null
        private var bonus = 0f
        private var sinceHit = Float.MAX_VALUE
        private val statusKey = Any()

        override fun modifyDamageDealt(
            param: Any?, target: CombatEntityAPI?, damage: DamageAPI?, point: Vector2f?, shieldHit: Boolean
        ): String? {
            val hit = target as? ShipAPI ?: return null
            if (hit.isFighter || hit.isHulk || hit.owner == ship.owner) return null

            val root = hit.parentStation ?: hit
            if (root !== lockedTarget) {
                lockedTarget = root
                bonus = 0f
            }
            sinceHit = 0f

            if (bonus <= 0f || damage == null) return null
            damage.modifier.modifyPercent(ID, bonus * 100f)
            return ID
        }

        override fun advance(amount: Float) {
            if (ship.isHulk) return

            sinceHit += amount
            bonus = when {
                sinceHit <= BUILD_WINDOW -> min(MAX_BONUS, bonus + BUILD_PER_SECOND * amount)
                sinceHit <= HOLD_TIME -> bonus
                else -> max(0f, bonus - DRAIN_PER_SECOND * amount)
            }

            val engine = Global.getCombatEngine()
            if (bonus > 0f && ship === engine.playerShip) {
                engine.maintainStatusForPlayerShip(
                    statusKey, ICON, "Target Lattice",
                    "+${(bonus * 100).roundToInt()}% damage vs locked target", false
                )
            }
        }
    }
}

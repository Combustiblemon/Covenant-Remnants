package org.combustiblelemon.covenantremnants.hullmods

import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import org.combustiblelemon.lemonlib.geometry.getPercentage

/**
 * Hardened Shield Matrix — Covenant counterpart of vanilla Hardened Shields: the Covenant shield
 * takes less damage. Uses vanilla's shieldDamageTakenMult, which the shield's damage listener
 * already honours (CovenantShieldsStats.ShieldDamageListener).
 */
class HardenedShieldMatrix : CovenantShieldTweak(ID) {
    companion object {
        const val ID = "covenant_remnants_hardened_shield_matrix"

        const val SHIELD_DAMAGE_MULT = 0.8f
    }

    override fun applyEffectsBeforeShipCreation(hullSize: ShipAPI.HullSize?, stats: MutableShipStatsAPI?, id: String?) {
        stats?.shieldDamageTakenMult?.modifyMult(id, SHIELD_DAMAGE_MULT)
    }

    override fun getDescriptionParam(index: Int, hullSize: ShipAPI.HullSize?): String? {
        return when (index) {
            0 -> "${getPercentage(SHIELD_DAMAGE_MULT)}%"
            else -> null
        }
    }
}

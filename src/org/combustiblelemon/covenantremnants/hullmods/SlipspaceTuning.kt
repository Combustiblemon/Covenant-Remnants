package org.combustiblelemon.covenantremnants.hullmods

import com.fs.starfarer.api.combat.BaseHullMod
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import org.combustiblelemon.lemonlib.geometry.getPercentage

/**
 * Slipspace Tuning — tunes the Covenant slipspace drive.
 *
 *  - Reduces the drive's ship-system cooldown (engine-applied via systemCooldownBonus).
 *  - Loosens the flux ceiling required to jump (read by [org.combustiblelemon.covenantremnants.systems.SlipspaceDrive]).
 *
 * Only installable on hulls that actually mount the slipspace drive system.
 */
class SlipspaceTuning : BaseHullMod() {
    companion object {
        const val ID = "covenant_remnants_slipspace_tuning"

        private const val SLIPSPACE_SYSTEM_ID = "covenant_remnants_slipspacedrive"

        // System cooldown multiplier (<1 shortens cooldown). 0.7f == -30%.
        const val SYSTEM_COOLDOWN_MULT = 0.7f

        // Max flux fraction at which the drive may still fire. Base gate is 0.7; this raises it.
        const val FLUX_GATE = 0.85f
    }

    override fun getUnapplicableReason(ship: ShipAPI?): String? {
        if (ship == null) {
            return "This hullmod is only applicable to ships."
        }
        if (ship.hullSpec.shipSystemId != SLIPSPACE_SYSTEM_ID) {
            return "Requires a ship equipped with the slipspace drive."
        }
        return null
    }

    override fun isApplicableToShip(ship: ShipAPI?): Boolean {
        return ship != null && ship.hullSpec.shipSystemId == SLIPSPACE_SYSTEM_ID
    }

    override fun applyEffectsBeforeShipCreation(hullSize: ShipAPI.HullSize?, stats: MutableShipStatsAPI?, id: String?) {
        if (stats == null) {
            return
        }
        stats.systemCooldownBonus.modifyMult(id, SYSTEM_COOLDOWN_MULT)
    }

    override fun getDescriptionParam(index: Int, hullSize: ShipAPI.HullSize?): String? {
        return when (index) {
            0 -> "${getPercentage(SYSTEM_COOLDOWN_MULT)}%"
            1 -> "${(FLUX_GATE * 100).toInt()}%"
            else -> null
        }
    }
}

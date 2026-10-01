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
 * Installable on any ship carrying the Slipspace Drive hullmod ([SlipspaceDriveHullmod]), built-in
 * or not. Both combat effects apply only when the ship's system is the slipspace drive; on other
 * ships the mod has no combat effect (a campaign slipspace-jump bonus is planned — see TODO.md).
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
        if (!hasSlipspaceDrive(ship)) {
            return "Requires a ship with a slipspace drive."
        }
        return null
    }

    override fun isApplicableToShip(ship: ShipAPI?): Boolean {
        return ship != null && hasSlipspaceDrive(ship)
    }

    override fun applyEffectsBeforeShipCreation(hullSize: ShipAPI.HullSize?, stats: MutableShipStatsAPI?, id: String?) {
        if (stats == null) {
            return
        }
        // Cooldown cut targets the drive only — never another ship system
        if (stats.variant?.hullSpec?.shipSystemId != SLIPSPACE_SYSTEM_ID) {
            return
        }
        stats.systemCooldownBonus.modifyMult(id, SYSTEM_COOLDOWN_MULT)
    }

    private fun hasSlipspaceDrive(ship: ShipAPI): Boolean {
        return ship.variant?.hasHullMod(SlipspaceDriveHullmod.ID) == true
    }

    override fun getDescriptionParam(index: Int, hullSize: ShipAPI.HullSize?): String? {
        return when (index) {
            0 -> "${getPercentage(SYSTEM_COOLDOWN_MULT)}%"
            1 -> "${(FLUX_GATE * 100).toInt()}%"
            else -> null
        }
    }
}

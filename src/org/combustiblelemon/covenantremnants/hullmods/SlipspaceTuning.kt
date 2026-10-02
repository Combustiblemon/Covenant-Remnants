package org.combustiblelemon.covenantremnants.hullmods

import com.fs.starfarer.api.combat.BaseHullMod
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import org.combustiblelemon.lemonlib.geometry.getPercentage

/**
 * Slipspace Tuning — tunes the Covenant slipspace drive.
 *
 *  - Speeds up the drive's charge regeneration (engine-applied via systemRegenBonus; the blink
 *    stores charges and has no cooldown, so a cooldown cut would do nothing).
 *
 * Installable on any ship carrying the Slipspace Drive hullmod ([SlipspaceDriveHullmod]), built-in
 * or not. The combat effect applies only when the ship's system is the slipspace drive; on other
 * ships the mod has no combat effect (a campaign slipspace-jump bonus is planned — see TODO.md).
 */
class SlipspaceTuning : BaseHullMod() {
    companion object {
        const val ID = "covenant_remnants_slipspace_tuning"

        private const val SLIPSPACE_SYSTEM_ID = "covenant_remnants_slipspacedrive"

        // Charge regeneration multiplier (>1 faster). 1.3f == +30%: a charge every ~7.7 s instead of 10 s.
        const val SYSTEM_REGEN_MULT = 1.3f
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
        // Regen boost targets the drive only — never another ship system
        if (stats.variant?.hullSpec?.shipSystemId != SLIPSPACE_SYSTEM_ID) {
            return
        }
        stats.systemRegenBonus.modifyMult(id, SYSTEM_REGEN_MULT)
    }

    private fun hasSlipspaceDrive(ship: ShipAPI): Boolean {
        return ship.variant?.hasHullMod(SlipspaceDriveHullmod.ID) == true
    }

    override fun getDescriptionParam(index: Int, hullSize: ShipAPI.HullSize?): String? {
        return when (index) {
            0 -> "${getPercentage(SYSTEM_REGEN_MULT)}%"
            else -> null
        }
    }
}

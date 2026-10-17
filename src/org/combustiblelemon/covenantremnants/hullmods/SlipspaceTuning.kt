package org.combustiblelemon.covenantremnants.hullmods

import com.fs.starfarer.api.combat.BaseHullMod
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.Misc
import org.combustiblelemon.covenantremnants.campaign.abilities.SlipspaceJumpRules
import org.combustiblelemon.covenantremnants.utils.CodexLore
import org.combustiblelemon.lemonlib.geometry.getPercentage

/**
 * Slipspace Tuning — tunes the Covenant slipspace drive.
 *
 *  - Combat: speeds up the drive's charge regeneration (engine-applied via systemRegenBonus; the
 *    blink stores charges and has no cooldown, so a cooldown cut would do nothing). Applies only
 *    when the ship's system is the slipspace drive.
 *  - Campaign (Slipspace Jump ability, developer 2026-10-07): each ship carrying Tuning speeds up
 *    the transit along the slipspace corridor; each S-modded Tuning also shortens the jump's
 *    charge-up and cooldown. Numbers in [SlipspaceJumpRules] (placeholders).
 *
 * Installable on any ship carrying the Slipspace Drive hullmod ([SlipspaceDriveHullmod]), built-in
 * or not.
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

    override fun addPostDescriptionSection(
        tooltip: TooltipMakerAPI?, hullSize: ShipAPI.HullSize?, ship: ShipAPI?, width: Float, isForModSpec: Boolean
    ) {
        CodexLore.addHullmodLore(tooltip, ID)
    }

    override fun getDescriptionParam(index: Int, hullSize: ShipAPI.HullSize?): String? {
        return when (index) {
            0 -> "${getPercentage(SYSTEM_REGEN_MULT)}%"
            1 -> "${Math.round(SlipspaceJumpRules.tuningTransitPerShip.value * 100f)}%"
            2 -> "${Math.round((SlipspaceJumpRules.tuningTransitMaxMult.value - 1f) * 100f)}%"
            else -> null
        }
    }

    override fun getSModDescriptionParam(index: Int, hullSize: ShipAPI.HullSize?): String? {
        return when (index) {
            0 -> "${Math.round(SlipspaceJumpRules.tuningSModPerShip.value * 100f)}%"
            1 -> "${Math.round((1f - SlipspaceJumpRules.tuningSModMinMult.value) * 100f)}%"
            else -> null
        }
    }
}

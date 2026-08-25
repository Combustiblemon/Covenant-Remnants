package org.combustiblelemon.covenantremnants.hullmods

import com.fs.starfarer.api.combat.BaseHullMod
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import org.combustiblelemon.lemonlib.geometry.getPercentage

class HuragokCrew : BaseHullMod() {
    companion object {
        const val ID = "covenant_remnants_huragok_crew"

        // maxCombatReadiness is a 0-1 fraction, so 0.20f == +20 percentage points of max CR.
        const val MAX_CR_BONUS = 0.20f
        const val REPAIR_PER_DAY_MULT = 1.25f
    }

    override fun applyEffectsBeforeShipCreation(hullSize: ShipAPI.HullSize?, stats: MutableShipStatsAPI?, id: String?) {
        if (stats == null) {
            return
        }

        stats.maxCombatReadiness.modifyFlat(id, MAX_CR_BONUS)
        stats.repairRatePercentPerDay.modifyMult(id, REPAIR_PER_DAY_MULT)
    }

    override fun getDescriptionParam(index: Int, hullSize: ShipAPI.HullSize?): String? {
        return when (index) {
            0 -> "${(MAX_CR_BONUS * 100).toInt()}%"
            1 -> "${getPercentage(REPAIR_PER_DAY_MULT)}%"
            2 -> "Covenant Systems"
            else -> {
                ""
            }
        }

    }
}

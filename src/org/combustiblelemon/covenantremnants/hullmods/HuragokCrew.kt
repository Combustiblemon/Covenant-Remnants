package org.combustiblelemon.covenantremnants.hullmods

import com.fs.starfarer.api.combat.BaseHullMod
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import org.combustiblelemon.covenantremnants.utils.getPercentage
import org.combustiblelemon.covenantremnants.utils.getUnapplicableReasonForHullmod
import org.magiclib.util.MagicIncompatibleHullmods

class HuragokCrew : BaseHullMod() {
    companion object {
        private val BLOCKED_HULLMODS: MutableSet<String> = HashSet(2)

//        init {
//            BLOCKED_HULLMODS.add("heavyarmor")
//            BLOCKED_HULLMODS.add("solar_shielding")
//        }

        const val ID = "covenant_remnants_huragok_crew"
        const val MAX_COMBAT_READINESS_MULT = 20f
        const val REPAIR_PER_DAY_MULT = 1.25f
    }

    override fun getUnapplicableReason(ship: ShipAPI?): String? {
        if (ship == null) {
            return "This hullmod is only applicable to ships."
        }

        val hullmodId = ship.variant.hullMods.find { BLOCKED_HULLMODS.contains(it) }
        if (hullmodId != null) {
            return getUnapplicableReasonForHullmod(hullmodId)
        }

        return null
    }

    override fun isApplicableToShip(ship: ShipAPI?): Boolean {
        if (ship == null) {
            return false
        }

        return !ship.variant.hullMods.any { hullmod -> BLOCKED_HULLMODS.contains(hullmod) }
    }

    override fun applyEffectsBeforeShipCreation(hullSize: ShipAPI.HullSize?, stats: MutableShipStatsAPI?, id: String?) {
        if (stats == null) {
            return
        }

        stats.maxCombatReadiness.modifyFlat(id, MAX_COMBAT_READINESS_MULT)
        stats.repairRatePercentPerDay.modifyMult(id, REPAIR_PER_DAY_MULT)
    }

    override fun applyEffectsAfterShipCreation(ship: ShipAPI?, id: String?) {
        if (ship == null) {
            return
        }

        for (tmp in BLOCKED_HULLMODS) {
            if (ship.variant.hullMods.contains(tmp)) {
                MagicIncompatibleHullmods.removeHullmodWithWarning(
                    ship.variant,
                    tmp,
                    this.spec.id
                )
            }
        }
    }

    override fun getDescriptionParam(index: Int, hullSize: ShipAPI.HullSize?): String? {
        return when (index) {
            0 -> "${MAX_COMBAT_READINESS_MULT.toInt()}%"
            1 -> "${getPercentage(REPAIR_PER_DAY_MULT)}%"
            2 -> "Covenant Systems"
            else -> {
                ""
            }
        }

    }
}

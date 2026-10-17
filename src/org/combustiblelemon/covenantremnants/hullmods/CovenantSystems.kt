package org.combustiblelemon.covenantremnants.hullmods

import com.fs.starfarer.api.combat.BaseHullMod
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipAPI.HullSize
import com.fs.starfarer.api.ui.TooltipMakerAPI
import org.combustiblelemon.covenantremnants.utils.CodexLore
import org.combustiblelemon.lemonlib.geometry.getPercentage
import org.combustiblelemon.lemonlib.hullmods.HullmodBlocker
import org.combustiblelemon.lemonlib.hullmods.isApplicableGiven
import org.combustiblelemon.lemonlib.hullmods.unapplicableReasonFor

class CovenantSystems : BaseHullMod() {
    private enum class ENERGY_WEAPON_RANGE_MULT(val value: Float) {
        CAPITAL_SHIP(1.8f),
        CRUISER(1.6f),
        DESTROYER(1.4f),
        FRIGATE(1.2f),
        FIGHTER(1.2f)
    }

    private val ENERGY_WEAPON_FLUX_MULT = 0.8f

    companion object {
        const val ID = "covenant_remnants_covenantsystems"
        const val DISPLAY_NAME = "Covenant Systems"

        // maxCombatReadiness is a 0-1 fraction, so 0.20f == -20 percentage points of max CR.
        // Huragok Crew derives its offset from these two.
        const val MAX_CR_PENALTY = 0.20f
        const val REPAIR_PER_DAY_MULT = 0.75f

        // Hullmod id -> fallback display name, used when the spec is missing at runtime.
        val INCOMPATIBLE_HULLMODS: Map<String, String> = mapOf(
            "targetingunit" to "Integrated Targeting Unit",
            "dedicated_targeting_core" to "Dedicated Targeting Core",
        )

        val BLOCKER = HullmodBlocker(ID, DISPLAY_NAME)
    }

    override fun isApplicableToShip(ship: ShipAPI?): Boolean {
        return isApplicableGiven(ship, ID, INCOMPATIBLE_HULLMODS)
    }

    override fun getUnapplicableReason(ship: ShipAPI?): String? {
        return unapplicableReasonFor(ship, INCOMPATIBLE_HULLMODS)
    }

    override fun applyEffectsBeforeShipCreation(hullSize: HullSize, stats: MutableShipStatsAPI, id: String) {
        val rangeMult: Float = when (hullSize) {
            HullSize.CAPITAL_SHIP -> ENERGY_WEAPON_RANGE_MULT.CAPITAL_SHIP.value
            HullSize.CRUISER -> ENERGY_WEAPON_RANGE_MULT.CRUISER.value
            HullSize.DESTROYER -> ENERGY_WEAPON_RANGE_MULT.DESTROYER.value
            HullSize.FRIGATE -> ENERGY_WEAPON_RANGE_MULT.FRIGATE.value
            HullSize.FIGHTER -> ENERGY_WEAPON_RANGE_MULT.FIGHTER.value
            else -> {
                1f
            }
        }
        stats.energyWeaponRangeBonus.modifyMult(ID, rangeMult)
        stats.energyWeaponFluxCostMod.modifyMult(ID, ENERGY_WEAPON_FLUX_MULT)

        // Standalone penalty: always applied. Huragok Crew offsets it when installed.
        stats.maxCombatReadiness.modifyFlat(ID, -MAX_CR_PENALTY)
        stats.repairRatePercentPerDay.modifyMult(ID, REPAIR_PER_DAY_MULT)
    }

    override fun addPostDescriptionSection(
        tooltip: TooltipMakerAPI?, hullSize: HullSize?, ship: ShipAPI?, width: Float, isForModSpec: Boolean
    ) {
        CodexLore.addHullmodLore(tooltip, ID)
    }

    override fun getDescriptionParam(index: Int, hullSize: HullSize): String {
        return when (index) {
            0 -> "${(MAX_CR_PENALTY * 100).toInt()}%"
            1 -> "${getPercentage(REPAIR_PER_DAY_MULT)}%"
            2 -> "${getPercentage(ENERGY_WEAPON_RANGE_MULT.FRIGATE.value)}%"
            3 -> "${getPercentage(ENERGY_WEAPON_RANGE_MULT.DESTROYER.value)}%"
            4 -> "${getPercentage(ENERGY_WEAPON_RANGE_MULT.CRUISER.value)}%"
            5 -> "${getPercentage(ENERGY_WEAPON_RANGE_MULT.CAPITAL_SHIP.value)}%"
            6 -> "${getPercentage(ENERGY_WEAPON_FLUX_MULT)}%"
            else -> {
                ""
            }
        }
    }
}

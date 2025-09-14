package org.combustiblelemon.covenantremnants.hullmods

import com.fs.starfarer.api.combat.BaseHullMod
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipAPI.HullSize
import org.combustiblelemon.covenantremnants.utils.getPercentage
import org.magiclib.util.MagicIncompatibleHullmods

class CovenantSystems : BaseHullMod() {
    private enum class ENERGY_WEAPON_RANGE_MULT(val value: Float) {
        CAPITAL_SHIP(1.8f),
        CRUISER(1.6f),
        DESTROYER(1.4f),
        FRIGATE(1.2f),
        FIGHTER(1.2f)

    }

    private val ENERGY_WEAPON_FLUX_MULT = 0.8f
    private val MAX_COMBAT_READINESS_MULT = 20f
    private val REPAIR_PER_DAY_MULT = 0.75f

    companion object {
        private val BLOCKED_HULLMODS: MutableSet<String> = HashSet(2)

        init {
            BLOCKED_HULLMODS.add("dedicated_targeting_core")
            BLOCKED_HULLMODS.add("targetingunit")
        }

        const val ID = "covenant_remnants_covenantsystems"
    }

    override fun isApplicableToShip(ship: ShipAPI): Boolean {
        return true
    }

    override fun applyEffectsAfterShipCreation(ship: ShipAPI, id: String) {
        for (tmp in BLOCKED_HULLMODS) {
            if (ship.variant.hullMods.contains(tmp)) {
                MagicIncompatibleHullmods.removeHullmodWithWarning(
                    ship.variant,
                    tmp,
                    ID
                )
            }
        }
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

        if (stats.variant != null && stats.variant.hullMods.contains(HuragokCrew.ID)) {
            stats.maxCombatReadiness.modifyFlat(ID, MAX_COMBAT_READINESS_MULT)
            stats.repairRatePercentPerDay.modifyMult(ID, REPAIR_PER_DAY_MULT)
        }
    }

    override fun getDescriptionParam(index: Int, hullSize: HullSize): String {
        return when (index) {
            0 -> "${MAX_COMBAT_READINESS_MULT.toInt()}%"
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

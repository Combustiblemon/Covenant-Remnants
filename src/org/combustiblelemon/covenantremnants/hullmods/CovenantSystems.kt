package org.combustiblelemon.covenantremnants.hullmods

import com.fs.starfarer.api.combat.BaseHullMod
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipAPI.HullSize
import com.fs.starfarer.api.util.Misc
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

    companion object {
        private val BLOCKED_HULLMODS: MutableSet<String> = HashSet(2)

        init {
            BLOCKED_HULLMODS.add("dedicated_targeting_core")
            BLOCKED_HULLMODS.add("targetingunit")
        }
    }

    override fun isApplicableToShip(ship: ShipAPI): Boolean {
        return false
    }

    override fun applyEffectsAfterShipCreation(ship: ShipAPI, id: String) {
        for (tmp in BLOCKED_HULLMODS) {
            if (ship.variant.hullMods.contains(tmp)) {
                MagicIncompatibleHullmods.removeHullmodWithWarning(
                    ship.variant,
                    tmp,
                    "covenant_remnants_covenantsystems"
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
        stats.energyWeaponRangeBonus.modifyMult(id, rangeMult)
        stats.energyWeaponFluxCostMod.modifyMult(id, ENERGY_WEAPON_FLUX_MULT)
        stats.variant.addTag("ship_unique_signature")
    }

    private fun convertToPercent(number: Float): Float {
        return ((number - 1.0) * 100.0).toFloat()
    }

    override fun getDescriptionParam(index: Int, hullSize: HullSize): String {
        val value = when (index) {
            0 -> Misc.getRoundedValue(convertToPercent(ENERGY_WEAPON_RANGE_MULT.FRIGATE.value)).toString()
            1 -> Misc.getRoundedValue(convertToPercent(ENERGY_WEAPON_RANGE_MULT.DESTROYER.value)).toString()
            2 -> Misc.getRoundedValue(convertToPercent(ENERGY_WEAPON_RANGE_MULT.CRUISER.value)).toString()
            3 -> Misc.getRoundedValue(convertToPercent(ENERGY_WEAPON_RANGE_MULT.CAPITAL_SHIP.value)).toString()
            4 -> Misc.getRoundedValue(convertToPercent(ENERGY_WEAPON_FLUX_MULT) * -1).toString()
            else -> {
                ""
            }
        }

        return "$value%"
    }

}

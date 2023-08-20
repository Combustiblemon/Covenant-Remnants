package org.combustiblelemon.covenantremnants.hullmods

import com.fs.starfarer.api.combat.BaseHullMod
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipAPI.HullSize
import org.magiclib.util.MagicIncompatibleHullmods

class CovenantSystems : BaseHullMod() {
    private enum class ENERGY_WEAPON_RANGE_MULT(val value: Float) {
        CAPITAL_SHIP(1.8f),
        CRUISER(1.6f),
        DESTROYER(1.5f),
        FRIGATE(1.3f),
        FIGHTER(1.3f)

    }

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
                MagicIncompatibleHullmods.removeHullmodWithWarning(ship.variant, tmp, "covenant_remnants_covenantsystems")
            }
        }
    }

    override fun applyEffectsBeforeShipCreation(hullSize: HullSize, stats: MutableShipStatsAPI, id: String) {
        val ENERGY_WEAPON_FLUX_MULT = 0.80f
        val rangeMult:Float = when (hullSize) {
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
        stats.entity
    }

    override fun getDescriptionParam(index: Int, hullSize: HullSize): String {
//		if (index == 0) return "" + (int) ((Float) mag.get(HullSize.FRIGATE) * 100f);
//		if (index == 1) return "" + (int) ((Float) mag.get(HullSize.DESTROYER) * 100f);
//		if (index == 2) return "" + (int) ((Float) mag.get(HullSize.CRUISER) * 100f);
//		if (index == 3) return "" + (int) ((Float) mag.get(HullSize.CAPITAL_SHIP) * 100f);
        //if (index == 0) return Misc.getRoundedValue((Float) mag.get(HullSize.FRIGATE) + 1f);
        //if (index == 1) return Misc.getRoundedValue((Float) mag.get(HullSize.DESTROYER) + 1f);
        //if (index == 2) return Misc.getRoundedValue((Float) mag.get(HullSize.CRUISER) + 1f);
        //if (index == 3) return Misc.getRoundedValue((Float) mag.get(HullSize.CAPITAL_SHIP) + 1f);
//		if (index == 0) return Misc.getRoundedValue(ARMOR_BONUS);
//		if (index == 1) return "" + (int) Math.round((1f - HANDLING_MULT) * 100f) + "%"; // + Strings.X;
//		if (index == 2) return "" + (int) Math.round((CAPACITY_MULT - 1f) * 100f) + "%";
        return ""
    }


}

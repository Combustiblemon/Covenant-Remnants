package org.combustiblelemon.covenantremnants.hullmods

import com.fs.starfarer.api.combat.BaseHullMod
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI
import org.combustiblelemon.covenantremnants.utils.CodexLore
import org.combustiblelemon.lemonlib.geometry.getPercentage

/**
 * Plasma Capacitors — energy weapons fire faster but cost more flux per shot.
 *
 *  - Rate of fire and ammo regen up by the same factor, so ammo-fed energy weapons keep the gain.
 *  - Flux per shot up, so flux per second rises by both factors: trades Covenant Systems'
 *    flux efficiency back for damage per second.
 *  - Beams are exempt from the flux cost: continuous beams gain nothing from rate of fire.
 *    Burst beams still get the shorter cooldown.
 */
class PlasmaCapacitors : BaseHullMod() {
    companion object {
        const val ID = "covenant_remnants_plasma_capacitors"

        const val ENERGY_ROF_MULT = 1.2f
        const val ENERGY_FLUX_COST_MULT = 1.2f
    }

    override fun applyEffectsBeforeShipCreation(hullSize: ShipAPI.HullSize?, stats: MutableShipStatsAPI?, id: String?) {
        if (stats == null) {
            return
        }
        stats.energyRoFMult.modifyMult(id, ENERGY_ROF_MULT)
        stats.energyAmmoRegenMult.modifyMult(id, ENERGY_ROF_MULT)
        stats.energyWeaponFluxCostMod.modifyMult(id, ENERGY_FLUX_COST_MULT)
        // Beam flux = energy flux mod x beam flux mult, so the inverse here cancels the cost for beams.
        stats.beamWeaponFluxCostMult.modifyMult(id, 1f / ENERGY_FLUX_COST_MULT)
    }

    override fun addPostDescriptionSection(
        tooltip: TooltipMakerAPI?, hullSize: ShipAPI.HullSize?, ship: ShipAPI?, width: Float, isForModSpec: Boolean
    ) {
        CodexLore.addHullmodLore(tooltip, ID)
    }

    override fun getDescriptionParam(index: Int, hullSize: ShipAPI.HullSize?): String? {
        return when (index) {
            0 -> "${getPercentage(ENERGY_ROF_MULT)}%"
            1 -> "${getPercentage(ENERGY_FLUX_COST_MULT)}%"
            else -> null
        }
    }
}

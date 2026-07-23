package org.combustiblelemon.covenantremnants.hullmods

import com.fs.starfarer.api.combat.BaseHullMod
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import org.combustiblelemon.covenantremnants.utils.getPercentage

/**
 * Plasma Capacitors — energy-weapon flux sustain with a volatility downside.
 *
 *  - Larger flux capacity and faster venting: sustain plasma fire longer, recover quicker.
 *  - Downside: a bigger, hotter flux bank takes longer to clear an overload, so getting
 *    flux-swamped punishes harder (reinforces the "weak when overwhelmed" identity).
 */
class PlasmaCapacitors : BaseHullMod() {
    companion object {
        const val ID = "covenant_remnants_plasma_capacitors"

        const val FLUX_CAPACITY_MULT = 1.15f
        const val VENT_RATE_MULT = 1.25f
        const val OVERLOAD_TIME_PERCENT = 30f
    }

    override fun applyEffectsBeforeShipCreation(hullSize: ShipAPI.HullSize?, stats: MutableShipStatsAPI?, id: String?) {
        if (stats == null) {
            return
        }
        stats.fluxCapacity.modifyMult(id, FLUX_CAPACITY_MULT)
        stats.ventRateMult.modifyMult(id, VENT_RATE_MULT)
        stats.overloadTimeMod.modifyPercent(id, OVERLOAD_TIME_PERCENT)
    }

    override fun getDescriptionParam(index: Int, hullSize: ShipAPI.HullSize?): String? {
        return when (index) {
            0 -> "${getPercentage(FLUX_CAPACITY_MULT)}%"
            1 -> "${getPercentage(VENT_RATE_MULT)}%"
            2 -> "${OVERLOAD_TIME_PERCENT.toInt()}%"
            else -> null
        }
    }
}

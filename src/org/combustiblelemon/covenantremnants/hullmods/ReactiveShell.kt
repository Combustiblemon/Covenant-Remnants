package org.combustiblelemon.covenantremnants.hullmods

import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import org.combustiblelemon.covenantremnants.systems.CovenantShieldModifiers
import org.combustiblelemon.lemonlib.geometry.getPercentage

/**
 * Reactive Shell — Covenant counterpart of vanilla Shield Conversion - Omni: a smaller, faster
 * shield for ships that dip in and out. Incompatible with Deep Shield Reservoir (opposite build,
 * developer 2026-10-17).
 */
class ReactiveShell : CovenantShieldTweak(ID, mapOf(DeepShieldReservoir.ID to DeepShieldReservoir.DISPLAY_NAME)) {
    companion object {
        const val ID = "covenant_remnants_reactive_shell"
        const val DISPLAY_NAME = "Reactive Shell"

        const val REGEN_MULT = 1.5f
        const val REGEN_DELAY_MULT = 0.5f
        const val CAPACITY_MULT = 0.7f
    }

    override fun applyEffectsBeforeShipCreation(hullSize: ShipAPI.HullSize?, stats: MutableShipStatsAPI?, id: String?) {
        val dynamic = stats?.dynamic ?: return
        dynamic.getStat(CovenantShieldModifiers.REGEN_MULT).modifyMult(id, REGEN_MULT)
        dynamic.getStat(CovenantShieldModifiers.REGEN_DELAY_MULT).modifyMult(id, REGEN_DELAY_MULT)
        dynamic.getStat(CovenantShieldModifiers.CAPACITY_MULT).modifyMult(id, CAPACITY_MULT)
    }

    override fun getDescriptionParam(index: Int, hullSize: ShipAPI.HullSize?): String? {
        return when (index) {
            0 -> "${getPercentage(REGEN_MULT)}%"
            1 -> "${getPercentage(REGEN_DELAY_MULT)}%"
            2 -> "${getPercentage(CAPACITY_MULT)}%"
            3 -> DeepShieldReservoir.DISPLAY_NAME
            else -> null
        }
    }
}

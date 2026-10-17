package org.combustiblelemon.covenantremnants.hullmods

import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import org.combustiblelemon.covenantremnants.systems.CovenantShieldModifiers
import org.combustiblelemon.lemonlib.geometry.getPercentage

/**
 * Deep Shield Reservoir — Covenant counterpart of vanilla Extended Shields. Arc means nothing on a
 * full bubble, so it deepens the shield pool instead; the deeper pool waits longer before it
 * starts refilling after a hit (developer 2026-10-17). Incompatible with Reactive Shell.
 */
class DeepShieldReservoir : CovenantShieldTweak(ID, mapOf(ReactiveShell.ID to ReactiveShell.DISPLAY_NAME)) {
    companion object {
        const val ID = "covenant_remnants_deep_shield_reservoir"
        const val DISPLAY_NAME = "Deep Shield Reservoir"

        const val CAPACITY_MULT = 1.3f
        const val REGEN_DELAY_MULT = 1.5f
    }

    override fun applyEffectsBeforeShipCreation(hullSize: ShipAPI.HullSize?, stats: MutableShipStatsAPI?, id: String?) {
        val dynamic = stats?.dynamic ?: return
        dynamic.getStat(CovenantShieldModifiers.CAPACITY_MULT).modifyMult(id, CAPACITY_MULT)
        dynamic.getStat(CovenantShieldModifiers.REGEN_DELAY_MULT).modifyMult(id, REGEN_DELAY_MULT)
    }

    override fun getDescriptionParam(index: Int, hullSize: ShipAPI.HullSize?): String? {
        return when (index) {
            0 -> "${getPercentage(CAPACITY_MULT)}%"
            1 -> "${getPercentage(REGEN_DELAY_MULT)}%"
            2 -> ReactiveShell.DISPLAY_NAME
            else -> null
        }
    }
}

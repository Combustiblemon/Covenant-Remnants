package org.combustiblelemon.covenantremnants.hullmods

import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import org.combustiblelemon.covenantremnants.systems.CovenantShieldModifiers
import org.combustiblelemon.lemonlib.geometry.getPercentage
import kotlin.math.roundToInt

/**
 * Accelerated Emitters — Covenant counterpart of vanilla Accelerated Shields. The shield has no
 * turn rate, so it raises faster and resumes regenerating sooner after a hit.
 *
 * The raise time reaches the engine through CovenantShieldsStats.getInOverride (charge-up of the
 * Covenant shield system, read once per ship at creation).
 */
class AcceleratedEmitters : CovenantShieldTweak(ID) {
    companion object {
        const val ID = "covenant_remnants_accelerated_emitters"

        const val RAISE_TIME_MULT = 0.5f
        const val REGEN_DELAY_MULT = 2f / 3f
    }

    override fun applyEffectsBeforeShipCreation(hullSize: ShipAPI.HullSize?, stats: MutableShipStatsAPI?, id: String?) {
        val dynamic = stats?.dynamic ?: return
        dynamic.getStat(CovenantShieldModifiers.RAISE_TIME_MULT).modifyMult(id, RAISE_TIME_MULT)
        dynamic.getStat(CovenantShieldModifiers.REGEN_DELAY_MULT).modifyMult(id, REGEN_DELAY_MULT)
    }

    override fun getDescriptionParam(index: Int, hullSize: ShipAPI.HullSize?): String? {
        return when (index) {
            // Rate of raising, as vanilla words it: half the time = +100% rate.
            0 -> "${((1f / RAISE_TIME_MULT - 1f) * 100).roundToInt()}%"
            1 -> "${getPercentage(REGEN_DELAY_MULT)}%"
            else -> null
        }
    }
}

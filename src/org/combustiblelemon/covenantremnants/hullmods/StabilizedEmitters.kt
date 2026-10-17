package org.combustiblelemon.covenantremnants.hullmods

import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import org.combustiblelemon.covenantremnants.systems.CovenantShieldModifiers
import org.combustiblelemon.covenantremnants.systems.CovenantShieldsStats
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Stabilized Emitters — Covenant counterpart of vanilla Stabilized Shields. The Covenant shield has
 * no flux upkeep, so this eases its flux falloff instead: regen keeps its full rate to a higher
 * flux level and never stops at maximum flux.
 */
class StabilizedEmitters : CovenantShieldTweak(ID) {
    companion object {
        const val ID = "covenant_remnants_stabilized_emitters"

        // Added to the falloff bonus: full rate up to 75% flux, 50% at maximum flux.
        const val FLUX_FALLOFF_BONUS = 0.5f
    }

    override fun applyEffectsBeforeShipCreation(hullSize: ShipAPI.HullSize?, stats: MutableShipStatsAPI?, id: String?) {
        stats?.dynamic?.getMod(CovenantShieldModifiers.FLUX_FALLOFF_BONUS)?.modifyFlat(id, FLUX_FALLOFF_BONUS)
    }

    override fun getDescriptionParam(index: Int, hullSize: ShipAPI.HullSize?): String? {
        return when (index) {
            0 -> percent(min(CovenantShieldsStats.fluxRegenKnee(FLUX_FALLOFF_BONUS), 1f))
            1 -> percent(min(CovenantShieldsStats.fluxRegenKnee(), 1f))
            2 -> percent(CovenantShieldsStats.fluxRegenMultAt(1f, FLUX_FALLOFF_BONUS))
            3 -> percent(CovenantShieldsStats.fluxRegenMultAt(1f))
            else -> null
        }
    }

    private fun percent(fraction: Float): String = "${(fraction * 100).roundToInt()}%"
}

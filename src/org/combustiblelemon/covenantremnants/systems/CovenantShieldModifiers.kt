package org.combustiblelemon.covenantremnants.systems

import com.fs.starfarer.api.combat.MutableShipStatsAPI
import org.combustiblelemon.covenantremnants.utils.ShieldData
import kotlin.math.roundToInt

/**
 * Per-ship modifiers on the Covenant shield, carried as vanilla dynamic stats on the ship's
 * MutableShipStatsAPI. The shield-tuning hullmods set them before ship creation. Readers:
 *  - [CovenantShieldsStats] applies them to the hull's shield_data.csv row when the shield starts,
 *    and returns the modified raise time from its charge-up override;
 *  - the Covenant Shield hullmod tooltip shows the result.
 *
 * Multipliers are MutableStats with base 1 (modifyMult). The flux falloff bonus is a StatBonus on
 * base 0 (modifyFlat) that adds to [CovenantShieldsStats.FLUX_REGEN_BONUS].
 */
object CovenantShieldModifiers {
    const val CAPACITY_MULT = "covenant_remnants_shield_capacity_mult"
    const val REGEN_MULT = "covenant_remnants_shield_regen_mult"
    const val REGEN_DELAY_MULT = "covenant_remnants_shield_regen_delay_mult"
    const val RAISE_TIME_MULT = "covenant_remnants_shield_raise_time_mult"
    const val FLUX_FALLOFF_BONUS = "covenant_remnants_shield_flux_falloff_bonus"

    /**
     * [base] (a shield_data.csv row) with this ship's capacity, regen and regen delay modifiers.
     * Capacity is rounded to whole points, so 2400 x 1.3 shows as 3120, not 3119.
     */
    fun effective(base: ShieldData, stats: MutableShipStatsAPI?): ShieldData {
        val dynamic = stats?.dynamic ?: return base
        return base.copy(
            shieldHealth = (base.shieldHealth * dynamic.getValue(CAPACITY_MULT)).roundToInt().toFloat(),
            shieldRegenRate = base.shieldRegenRate * dynamic.getValue(REGEN_MULT),
            shieldRegenDelay = base.shieldRegenDelay * dynamic.getValue(REGEN_DELAY_MULT),
        )
    }

    fun raiseTimeMult(stats: MutableShipStatsAPI?): Float = stats?.dynamic?.getValue(RAISE_TIME_MULT) ?: 1f

    fun fluxFalloffBonus(stats: MutableShipStatsAPI?): Float =
        stats?.dynamic?.getMod(FLUX_FALLOFF_BONUS)?.computeEffective(0f) ?: 0f
}

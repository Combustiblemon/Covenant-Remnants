package org.combustiblelemon.covenantremnants.hullmods

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.BaseHullMod
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI
import org.combustiblelemon.covenantremnants.utils.CodexLore
import kotlin.math.roundToInt

/**
 * Overcharged Emitters — energy weapons fire faster the hotter the ship runs.
 *
 *  - No bonus below [FLUX_THRESHOLD]; rate of fire rises linearly to [MAX_ROF_BONUS] at full flux.
 *  - Downside: longer overloads. High flux also slows Covenant shield regen, so riding the bonus
 *    has its own cost (developer 2026-10-17).
 *  - Stacks with Plasma Capacitors.
 */
class OverchargedEmitters : BaseHullMod() {
    companion object {
        const val ID = "covenant_remnants_overcharged_emitters"

        const val FLUX_THRESHOLD = 0.5f
        const val MAX_ROF_BONUS = 0.20f
        const val OVERLOAD_TIME_PERCENT = 15f

        private const val ICON = "graphics/icons/hullsys/ammo_feeder.png"
    }

    override fun applyEffectsBeforeShipCreation(hullSize: ShipAPI.HullSize?, stats: MutableShipStatsAPI?, id: String?) {
        stats?.overloadTimeMod?.modifyPercent(id, OVERLOAD_TIME_PERCENT)
    }

    override fun advanceInCombat(ship: ShipAPI?, amount: Float) {
        if (ship == null) return
        val stats = ship.mutableStats

        val t = ((ship.fluxLevel - FLUX_THRESHOLD) / (1f - FLUX_THRESHOLD)).coerceIn(0f, 1f)
        val bonus = MAX_ROF_BONUS * t
        if (bonus <= 0f || ship.isHulk) {
            stats.energyRoFMult.unmodify(ID)
            return
        }
        stats.energyRoFMult.modifyMult(ID, 1f + bonus)

        val engine = Global.getCombatEngine()
        if (ship === engine.playerShip) {
            engine.maintainStatusForPlayerShip(
                ID, ICON, "Overcharged Emitters", "+${(bonus * 100).roundToInt()}% energy weapon rate of fire", false
            )
        }
    }

    override fun addPostDescriptionSection(
        tooltip: TooltipMakerAPI?, hullSize: ShipAPI.HullSize?, ship: ShipAPI?, width: Float, isForModSpec: Boolean
    ) {
        CodexLore.addHullmodLore(tooltip, ID)
    }

    override fun getDescriptionParam(index: Int, hullSize: ShipAPI.HullSize?): String? {
        return when (index) {
            0 -> "${(FLUX_THRESHOLD * 100).roundToInt()}%"
            1 -> "${(MAX_ROF_BONUS * 100).roundToInt()}%"
            2 -> "${OVERLOAD_TIME_PERCENT.roundToInt()}%"
            else -> null
        }
    }
}

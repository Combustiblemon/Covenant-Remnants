package org.combustiblelemon.covenantremnants.hullmods

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.BaseHullMod
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.listeners.AdvanceableListener
import com.fs.starfarer.api.impl.campaign.skills.CombatEndurance
import com.fs.starfarer.api.util.Misc
import org.combustiblelemon.lemonlib.geometry.getPercentage
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class HuragokCrew : BaseHullMod() {
    companion object {
        const val ID = "covenant_remnants_huragok_crew"

        // Exact offsets of the Covenant Systems penalty. The CR bonus only applies when Systems is installed.
        const val MAX_CR_BONUS = CovenantSystems.MAX_CR_PENALTY
        const val REPAIR_PER_DAY_MULT = 1f / CovenantSystems.REPAIR_PER_DAY_MULT

        private const val REPAIRED_KEY_PREFIX = "covenant_remnants_huragok_regen_"
    }

    override fun applyEffectsBeforeShipCreation(hullSize: ShipAPI.HullSize?, stats: MutableShipStatsAPI?, id: String?) {
        if (stats == null) {
            return
        }

        stats.repairRatePercentPerDay.modifyMult(id, REPAIR_PER_DAY_MULT)

        if (stats.variant?.hasHullMod(CovenantSystems.ID) == true) {
            stats.maxCombatReadiness.modifyFlat(id, MAX_CR_BONUS)
        }
    }

    override fun applyEffectsAfterShipCreation(ship: ShipAPI?, id: String?) {
        ship?.addListener(HullRegen(ship))
    }

    override fun getDescriptionParam(index: Int, hullSize: ShipAPI.HullSize?): String? {
        return when (index) {
            0 -> "${getPercentage(REPAIR_PER_DAY_MULT)}%"
            1 -> "${(MAX_CR_BONUS * 100).toInt()}%"
            2 -> CovenantSystems.DISPLAY_NAME
            3 -> "${Misc.getRoundedValueMaxOneAfterDecimal(CombatEndurance.REGEN_RATE * 100f)}%"
            4 -> "${CombatEndurance.TOTAL_REGEN_MAX_POINTS.roundToInt()}"
            5 -> "${(CombatEndurance.TOTAL_REGEN_MAX_HULL_FRACTION * 100f).roundToInt()}%"
            else -> {
                ""
            }
        }

    }

    /**
     * In-combat hull repair with the numbers of vanilla's elite Combat Endurance
     * ([CombatEndurance.CombatEnduranceRegen]). Keeps its own repaired total, so it stacks with the skill.
     * The total is stored per ship in the engine's custom data, so a redeploy in the same battle does not reset it.
     */
    class HullRegen(private val ship: ShipAPI) : AdvanceableListener {
        private var inited = false
        private var limit = 0f
        private var repaired = 0f
        private lateinit var repairedKey: String

        private fun init() {
            inited = true
            limit = max(
                CombatEndurance.TOTAL_REGEN_MAX_POINTS,
                CombatEndurance.TOTAL_REGEN_MAX_HULL_FRACTION * ship.maxHitpoints
            )
            repairedKey = "$REPAIRED_KEY_PREFIX${ship.id}_repaired"
            repaired = max(repaired, Global.getCombatEngine().customData[repairedKey] as? Float ?: 0f)
        }

        override fun advance(amount: Float) {
            if (!inited) {
                init()
            }

            if (repaired >= limit) return
            if (ship.hullLevel >= CombatEndurance.MAX_REGEN_LEVEL) return
            if (ship.isHulk) return

            val maxHull = ship.maxHitpoints
            val maxPoints = maxHull * CombatEndurance.MAX_REGEN_LEVEL
            val repairAmount = min(
                min(limit - repaired, maxHull * CombatEndurance.REGEN_RATE * amount),
                maxPoints - ship.hitpoints
            )

            if (repairAmount > 0f) {
                ship.hitpoints += repairAmount
                repaired += repairAmount
                Global.getCombatEngine().customData[repairedKey] = repaired
            }
        }
    }
}

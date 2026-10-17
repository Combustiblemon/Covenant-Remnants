package org.combustiblelemon.covenantremnants.hullmods

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.BaseHullMod
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.listeners.AdvanceableListener
import com.fs.starfarer.api.ui.TooltipMakerAPI
import org.combustiblelemon.covenantremnants.utils.CodexLore
import org.combustiblelemon.lemonlib.geometry.getPercentage
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Capacitor Discharge — finishing an active vent dumps the stored charge into the energy weapons.
 *
 *  - Energy weapon damage up for a few seconds after each completed vent, scaled by the flux level
 *    when the vent began, so a vent at near-zero flux gives next to nothing.
 *  - Downside: slower active venting.
 */
class CapacitorDischarge : BaseHullMod() {
    companion object {
        const val ID = "covenant_remnants_capacitor_discharge"

        const val MAX_DAMAGE_BONUS = 0.15f
        const val BUFF_DURATION = 5f
        const val VENT_RATE_MULT = 0.8f

        private const val ICON = "graphics/icons/hullsys/high_energy_focus.png"
    }

    override fun applyEffectsBeforeShipCreation(hullSize: ShipAPI.HullSize?, stats: MutableShipStatsAPI?, id: String?) {
        stats?.ventRateMult?.modifyMult(id, VENT_RATE_MULT)
    }

    override fun applyEffectsAfterShipCreation(ship: ShipAPI?, id: String?) {
        ship?.addListener(Discharge(ship))
    }

    override fun addPostDescriptionSection(
        tooltip: TooltipMakerAPI?, hullSize: ShipAPI.HullSize?, ship: ShipAPI?, width: Float, isForModSpec: Boolean
    ) {
        CodexLore.addHullmodLore(tooltip, ID)
    }

    override fun getDescriptionParam(index: Int, hullSize: ShipAPI.HullSize?): String? {
        return when (index) {
            0 -> "${(MAX_DAMAGE_BONUS * 100).roundToInt()}%"
            1 -> "${BUFF_DURATION.roundToInt()}"
            2 -> "${getPercentage(VENT_RATE_MULT)}%"
            else -> null
        }
    }

    class Discharge(private val ship: ShipAPI) : AdvanceableListener {
        private var wasVenting = false
        private var lastFluxLevel = 0f
        private var ventStartLevel = 0f
        private var damageBonus = 0f
        private var remaining = 0f
        private val statusKey = Any()

        override fun advance(amount: Float) {
            val stats = ship.mutableStats
            if (ship.isHulk) {
                stats.energyWeaponDamageMult.unmodify(ID)
                return
            }

            val venting = ship.fluxTracker.isVenting
            if (venting && !wasVenting) {
                // The vent may already have drained a frame's worth, so take the higher of the two.
                ventStartLevel = max(lastFluxLevel, ship.fluxLevel)
            } else if (!venting && wasVenting) {
                damageBonus = MAX_DAMAGE_BONUS * ventStartLevel
                remaining = BUFF_DURATION
            }
            wasVenting = venting
            lastFluxLevel = ship.fluxLevel

            if (remaining <= 0f) {
                return
            }
            remaining -= amount
            if (remaining <= 0f || damageBonus <= 0f) {
                remaining = 0f
                stats.energyWeaponDamageMult.unmodify(ID)
                return
            }
            stats.energyWeaponDamageMult.modifyMult(ID, 1f + damageBonus)

            val engine = Global.getCombatEngine()
            if (ship === engine.playerShip) {
                engine.maintainStatusForPlayerShip(
                    statusKey, ICON, "Capacitor Discharge",
                    "+${(damageBonus * 100).roundToInt()}% energy weapon damage (${ceil(remaining).toInt()} s)", false
                )
            }
        }
    }
}

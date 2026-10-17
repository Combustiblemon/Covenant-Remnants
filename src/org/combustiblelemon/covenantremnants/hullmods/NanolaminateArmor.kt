package org.combustiblelemon.covenantremnants.hullmods

import com.fs.starfarer.api.combat.BaseHullMod
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI
import org.combustiblelemon.covenantremnants.utils.CodexLore
import org.combustiblelemon.lemonlib.geometry.getPercentage
import org.combustiblelemon.lemonlib.hullmods.HullmodBlocker
import org.combustiblelemon.lemonlib.hullmods.isApplicableGiven
import org.combustiblelemon.lemonlib.hullmods.unapplicableReasonFor

class NanolaminateArmor : BaseHullMod() {
    companion object {
        const val ID = "covenant_remnants_nanolaminate_armor"
        const val DISPLAY_NAME = "Nanolaminate Armor"

        // Vanilla hullmod id -> fallback display name, used when the spec is missing at runtime.
        val INCOMPATIBLE_HULLMODS: Map<String, String> = mapOf(
            "heavyarmor" to "Heavy Armor",
            "solar_shielding" to "Solar Shielding",
        )

        val BLOCKER = HullmodBlocker(ID, DISPLAY_NAME)

        const val ENERGY_DAMAGE_MULT = 0.8f
        const val FRAGMENTATION_DAMAGE_MULT = 1.6f
        const val HIGH_EXPLOSIVE_DAMAGE_MULT = 0.8f
        const val KINETIC_DAMAGE_MULT = 1.6f
        const val ARMOR_BONUS_MULT = 0.8f
        const val MAX_SPEED_MULT = 1.2f
        const val ACCELERATION_MULT = 1.2f
    }

    override fun getUnapplicableReason(ship: ShipAPI?): String? {
        return unapplicableReasonFor(ship, INCOMPATIBLE_HULLMODS)
    }

    override fun isApplicableToShip(ship: ShipAPI?): Boolean {
        return isApplicableGiven(ship, ID, INCOMPATIBLE_HULLMODS)
    }

    override fun applyEffectsBeforeShipCreation(hullSize: ShipAPI.HullSize?, stats: MutableShipStatsAPI?, id: String?) {
        if (stats == null) {
            return
        }

        stats.energyDamageTakenMult.modifyMult(id, ENERGY_DAMAGE_MULT)
        stats.fragmentationDamageTakenMult.modifyMult(id, FRAGMENTATION_DAMAGE_MULT)
        stats.highExplosiveDamageTakenMult.modifyMult(id, HIGH_EXPLOSIVE_DAMAGE_MULT)
        stats.kineticDamageTakenMult.modifyMult(id, KINETIC_DAMAGE_MULT)
        stats.armorBonus.modifyMult(id, ARMOR_BONUS_MULT)
        stats.maxSpeed.modifyMult(id, MAX_SPEED_MULT)
        stats.acceleration.modifyMult(id, ACCELERATION_MULT)
    }

    override fun addPostDescriptionSection(
        tooltip: TooltipMakerAPI?, hullSize: ShipAPI.HullSize?, ship: ShipAPI?, width: Float, isForModSpec: Boolean
    ) {
        CodexLore.addHullmodLore(tooltip, ID)
    }

    override fun getDescriptionParam(index: Int, hullSize: ShipAPI.HullSize?): String? {
        when (index) {
            0 -> return "${getPercentage(ENERGY_DAMAGE_MULT)}%"
            1 -> return "${getPercentage(FRAGMENTATION_DAMAGE_MULT)}%"
            2 -> return "${getPercentage(HIGH_EXPLOSIVE_DAMAGE_MULT)}%"
            3 -> return "${getPercentage(KINETIC_DAMAGE_MULT)}%"
            4 -> return "${getPercentage(ARMOR_BONUS_MULT)}%"
            5 -> return "${getPercentage(MAX_SPEED_MULT)}%"
            6 -> return "${getPercentage(ACCELERATION_MULT)}%"
        }

        return null
    }
}

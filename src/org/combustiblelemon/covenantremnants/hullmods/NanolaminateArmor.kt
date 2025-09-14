package org.combustiblelemon.covenantremnants.hullmods

import com.fs.starfarer.api.combat.BaseHullMod
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import org.combustiblelemon.covenantremnants.utils.getPercentage
import org.combustiblelemon.covenantremnants.utils.getUnapplicableReasonForHullmod
import org.magiclib.util.MagicIncompatibleHullmods

class NanolaminateArmor : BaseHullMod() {
    companion object {
        private val BLOCKED_HULLMODS: MutableSet<String> = HashSet(2)

        init {
            BLOCKED_HULLMODS.add("heavyarmor")
            BLOCKED_HULLMODS.add("solar_shielding")
        }

        const val ENERGY_DAMAGE_MULT = 0.8f
        const val FRAGMENTATION_DAMAGE_MULT = 1.5f
        const val HIGH_EXPLOSIVE_DAMAGE_MULT = 0.8f
        const val KINETIC_DAMAGE_MULT = 1.5f
        const val ARMOR_BONUS_MULT = 0.8f
        const val MAX_SPEED_MULT = 1.2f
        const val ACCELERATION_MULT = 1.2f
    }

    override fun getUnapplicableReason(ship: ShipAPI?): String? {
        if (ship == null) {
            return "This hullmod is only applicable to ships."
        }

        val hullmodId = ship.variant.hullMods.find { BLOCKED_HULLMODS.contains(it) }
        if (hullmodId != null) {
            return getUnapplicableReasonForHullmod(hullmodId)
        }

        return null
    }

    override fun isApplicableToShip(ship: ShipAPI?): Boolean {
        if (ship == null) {
            return false
        }

        return !ship.variant.hullMods.any { hullmod -> BLOCKED_HULLMODS.contains(hullmod) }
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

    override fun applyEffectsAfterShipCreation(ship: ShipAPI?, id: String?) {
        if (ship == null) {
            return
        }

        for (tmp in BLOCKED_HULLMODS) {
            if (ship.variant.hullMods.contains(tmp)) {
                MagicIncompatibleHullmods.removeHullmodWithWarning(
                    ship.variant,
                    tmp,
                    this.spec.id
                )
            }
        }
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

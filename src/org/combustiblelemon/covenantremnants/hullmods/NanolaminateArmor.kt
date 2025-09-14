package org.combustiblelemon.covenantremnants.hullmods

import com.fs.starfarer.api.combat.BaseHullMod
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.impl.campaign.ids.Stats
import org.magiclib.util.MagicIncompatibleHullmods

class NanolaminateArmor : BaseHullMod() {
    companion object {
        private val BLOCKED_HULLMODS: MutableSet<String> = HashSet(2)

        init {
            BLOCKED_HULLMODS.add("heavyarmor")
            BLOCKED_HULLMODS.add("polarized_armor")
        }
    }

    override fun getUnapplicableReason(ship: ShipAPI?): String? {
        if (ship == null) {
            return "This hullmod is only applicable to ships."
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

        stats.energyDamageTakenMult.modifyMult(this.spec.id, 0.8f)
        stats.fragmentationDamageTakenMult.modifyMult(this.spec.id, 1.1f)
        stats.highExplosiveDamageTakenMult.modifyMult(this.spec.id, 1.1f)
        stats.kineticDamageTakenMult.modifyMult(this.spec.id, 1.1f)
        stats.armorBonus.modifyMult(this.spec.id, 0.8f)
        stats.maxSpeed.modifyMult(this.spec.id, 1.2f)
        stats.acceleration.modifyMult(this.spec.id, 1.2f)

        if (isSMod(stats)) {
            stats.dynamic.getStat(Stats.CORONA_EFFECT_MULT).modifyMult(this.spec.id, 0.25f)
        }
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

    override fun advanceInCampaign(member: FleetMemberAPI?, amount: Float) {

    }

    override fun advanceInCombat(ship: ShipAPI?, amount: Float) {
    }
}
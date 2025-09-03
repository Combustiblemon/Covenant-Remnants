package org.combustiblelemon.covenantremnants.hullmods

import com.fs.starfarer.api.combat.BaseHullMod
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.loading.HullModSpecAPI

class CovenantShield : BaseHullMod() {
    override fun init(hullModSpec: HullModSpecAPI?) {
        super.init(hullModSpec)
    }

    override fun getUnapplicableReason(ship: ShipAPI?): String? {
        if (ship == null) {
            return "This hullmod is only applicable to ships."
        }

        if (ship.parentStation != null) {
            return "This hullmod is not applicable to modules."
        }

        return null
    }

    override fun isApplicableToShip(ship: ShipAPI?): Boolean {
        if (ship == null) {
            return false
        }

        if (ship.parentStation != null) {
            return false
        }

        return true
    }

    override fun applyEffectsBeforeShipCreation(hullSize: ShipAPI.HullSize?, stats: MutableShipStatsAPI?, id: String?) {
        if (stats?.variant == null) {
            return
        }

        val variant = stats.variant

        if (variant.hullSpec.shipDefenseId == "covenant_remnants_covenantshield") {
            return
        }

        val managerHullmodId =
            variant.hullMods.find { it.startsWith("covenant_remnants_hullmodmanager", true) }

        if (managerHullmodId == null) {
            variant.hullMods.add("covenant_remnants_hullmodmanager")
        }
    }

    override fun applyEffectsAfterShipCreation(ship: ShipAPI?, id: String?) {

    }

    override fun advanceInCampaign(member: FleetMemberAPI?, amount: Float) {

    }

    override fun advanceInCombat(ship: ShipAPI?, amount: Float) {
    }
}

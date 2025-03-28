package org.combustiblelemon.covenantremnants.hullmods

import com.fs.starfarer.api.combat.BaseHullMod
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipAPI.HullSize

class TestHull : BaseHullMod() {
    override fun isApplicableToShip(ship: ShipAPI): Boolean {
        return false
    }

    override fun applyEffectsAfterShipCreation(ship: ShipAPI, id: String) {

    }

    override fun applyEffectsBeforeShipCreation(hullSize: HullSize, stats: MutableShipStatsAPI, id: String) {
    }

    override fun getDescriptionParam(index: Int, hullSize: HullSize): String {
        return ""
    }

}

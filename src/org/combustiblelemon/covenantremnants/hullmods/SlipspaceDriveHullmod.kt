package org.combustiblelemon.covenantremnants.hullmods

import com.fs.starfarer.api.combat.BaseHullMod
import com.fs.starfarer.api.combat.ShipAPI

/**
 * Slipspace Drive — flavour / marker hullmod. Has NO mechanical effect.
 *
 * Built into Covenant combat hulls so campaign scripts can detect a slipspace-capable ship via
 * `variant.hasHullMod(ID)`. Not manually installable; built-in application bypasses [isApplicableToShip].
 *
 * Not to be confused with the slipspace drive *ship system*
 * ([org.combustiblelemon.covenantremnants.systems.SlipspaceDrive], id `covenant_remnants_slipspacedrive`).
 */
class SlipspaceDriveHullmod : BaseHullMod() {
    companion object {
        const val ID = "covenant_remnants_slipspace_drive"
    }

    override fun isApplicableToShip(ship: ShipAPI?): Boolean = false

    override fun getUnapplicableReason(ship: ShipAPI?): String? =
        "Integral Covenant technology; cannot be installed."
}

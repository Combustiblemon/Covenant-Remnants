package org.combustiblelemon.covenantremnants.hullmods

import com.fs.starfarer.api.combat.BaseHullMod
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI
import org.combustiblelemon.covenantremnants.utils.CodexLore
import org.combustiblelemon.lemonlib.hullmods.isApplicableGiven
import org.combustiblelemon.lemonlib.hullmods.unapplicableReasonFor

/**
 * Base for the hullmods that tune the Covenant shield (Covenant counterparts of vanilla's shield
 * hullmods, which the Covenant shield blocks). Installable only where that shield exists: Covenant
 * hulls, or any ship with the Covenant Shield hullmod. Removing the shield makes these
 * unapplicable, so the refit screen strips them.
 *
 * [incompatible]: hullmod id -> fallback display name of the tweaks this one can't share a ship with.
 */
abstract class CovenantShieldTweak(
    private val ownId: String,
    private val incompatible: Map<String, String> = emptyMap(),
) : BaseHullMod() {
    override fun isApplicableToShip(ship: ShipAPI?): Boolean {
        val variant = ship?.variant ?: return true
        return CovenantShield.hasCovenantShield(variant) && isApplicableGiven(ship, ownId, incompatible)
    }

    override fun getUnapplicableReason(ship: ShipAPI?): String? {
        val variant = ship?.variant ?: return null
        if (!CovenantShield.hasCovenantShield(variant)) {
            return "Requires the ${CovenantShield.DISPLAY_NAME}"
        }
        return unapplicableReasonFor(ship, incompatible)
    }

    override fun addPostDescriptionSection(
        tooltip: TooltipMakerAPI?, hullSize: ShipAPI.HullSize?, ship: ShipAPI?, width: Float, isForModSpec: Boolean
    ) {
        CodexLore.addHullmodLore(tooltip, ownId)
    }
}

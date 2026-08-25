package org.combustiblelemon.covenantremnants.hullmods

import com.fs.starfarer.api.combat.BaseHullMod
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import org.combustiblelemon.covenantremnants.utils.generateDefaultShieldData
import org.combustiblelemon.covenantremnants.utils.shieldData
import org.combustiblelemon.lemonlib.hullmods.HullmodBlocker
import org.combustiblelemon.lemonlib.hullmods.isApplicableGiven
import org.combustiblelemon.lemonlib.hullmods.unapplicableReasonFor

class CovenantShield : BaseHullMod() {
    companion object {
        const val ID = "covenant_remnants_covenantshieldhullmod"
        const val DISPLAY_NAME = "Covenant Shield"
        const val DEFENSE_ID = "covenant_remnants_covenantshield"
        const val MAKESHIFT_SHIELD_GENERATOR = "frontshield"

        // Vanilla hullmod id -> fallback display name, used when the spec is missing at runtime.
        val INCOMPATIBLE_HULLMODS: Map<String, String> = mapOf(
            "advancedshieldemitter" to "Accelerated Shields",
            "extendedshieldemitter" to "Extended Shields",
            "hardenedshieldemitter" to "Hardened Shields",
            "stabilizedshieldemitter" to "Stabilized Shields",
            "frontemitter" to "Shield Conversion - Front",
            "adaptiveshields" to "Shield Conversion - Omni",
            "shield_shunt" to "Shield Shunt",
            "adaptive_coils" to "Adaptive Phase Coils",
            "phase_anchor" to "Phase Anchor",
            MAKESHIFT_SHIELD_GENERATOR to "Makeshift Shield Generator",
        )

        // Covenant hulls carry the shield via hullSpec defense id (ship_data.csv), not only via
        // the hullmod, so presence must check both.
        private val HAS_COVENANT_SHIELD = { variant: com.fs.starfarer.api.combat.ShipVariantAPI ->
            variant.hasHullMod(ID) || variant.hullSpec?.shipDefenseId == DEFENSE_ID
        }

        val BLOCKER = HullmodBlocker(ID, DISPLAY_NAME, presentOn = HAS_COVENANT_SHIELD)

        // A shield generator conflicting with a shield reads better as a state than as a mod clash.
        val MAKESHIFT_BLOCKER = HullmodBlocker(
            ID, DISPLAY_NAME,
            reasonOverride = "This ship already has a shield system",
            presentOn = HAS_COVENANT_SHIELD,
        )
    }

    override fun getUnapplicableReason(ship: ShipAPI?): String? {
        if (ship == null) {
            return null
        }

        if (ship.parentStation != null) {
            return "This hullmod is not applicable to modules."
        }

        return unapplicableReasonFor(ship, INCOMPATIBLE_HULLMODS)
    }

    override fun isApplicableToShip(ship: ShipAPI?): Boolean {
        if (ship == null) {
            return true
        }

        if (ship.parentStation != null) {
            return false
        }

        return isApplicableGiven(ship, ID, INCOMPATIBLE_HULLMODS)
    }

    override fun applyEffectsBeforeShipCreation(hullSize: ShipAPI.HullSize?, stats: MutableShipStatsAPI?, id: String?) {
        if (stats?.variant == null) {
            return
        }

        val variant = stats.variant

        if (variant.hullSpec.shipDefenseId == DEFENSE_ID) {
            return
        }

        val managerHullmodId =
            variant.hullMods.find { it.startsWith(HullmodManager.ID, true) }

        if (managerHullmodId == null) {
            variant.hullMods.add(HullmodManager.ID)
        }
    }

    override fun getDescriptionParam(index: Int, hullSize: ShipAPI.HullSize?, ship: ShipAPI?): String? {
        val data = ship?.hullSpec?.let { shieldData[it.baseHullId] ?: shieldData[it.hullId] }
            ?: generateDefaultShieldData()
        return when (index) {
            0 -> "${data.shieldHealth.toInt()}"
            1 -> "${data.shieldRegenRate.toInt()}"
            2 -> "${data.shieldRegenDelay.formatSeconds()}"
            3 -> "${data.brokenShieldCooldown.formatSeconds()}"
            else -> null
        }
    }

    private fun Float.formatSeconds(): String =
        if (this == this.toInt().toFloat()) "${this.toInt()}" else "$this"
}

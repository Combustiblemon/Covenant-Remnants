package org.combustiblelemon.covenantremnants.hullmods

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.BaseHullMod
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipVariantAPI
import org.combustiblelemon.covenantremnants.systems.CovenantShieldModifiers
import com.fs.starfarer.api.ui.TooltipMakerAPI
import org.combustiblelemon.covenantremnants.systems.CovenantShieldsStats
import org.combustiblelemon.covenantremnants.utils.CodexLore
import org.combustiblelemon.covenantremnants.utils.ShieldData
import org.combustiblelemon.covenantremnants.utils.generateDefaultShieldData
import org.combustiblelemon.covenantremnants.utils.shieldData
import org.combustiblelemon.lemonlib.geometry.getPercentage
import org.combustiblelemon.lemonlib.hullmods.HullmodBlocker
import org.combustiblelemon.lemonlib.hullmods.isApplicableGiven
import org.combustiblelemon.lemonlib.hullmods.unapplicableReasonFor
import kotlin.math.min
import kotlin.math.roundToInt

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
        fun hasCovenantShield(variant: ShipVariantAPI): Boolean =
            variant.hasHullMod(ID) || variant.hullSpec?.shipDefenseId == DEFENSE_ID

        private val HAS_COVENANT_SHIELD = ::hasCovenantShield

        // Price of the shield: the emitters draw on the flux grid.
        const val MAX_FLUX_MULT = 0.8f

        val BLOCKER = HullmodBlocker(ID, DISPLAY_NAME, presentOn = HAS_COVENANT_SHIELD)

        /**
         * Adds this hullmod as built-in to every hull spec whose defense is the Covenant shield, so the
         * ship-editor dropping it from `builtInMods` (happened twice) or another mod's hull using the
         * system can't leave a shielded ship without it. Call once from onApplicationLoad.
         */
        fun ensureBuiltInOnShieldHulls() {
            for (spec in Global.getSettings().allShipHullSpecs) {
                if (spec.shipDefenseId == DEFENSE_ID && !spec.isBuiltInMod(ID)) {
                    spec.addBuiltInMod(ID)
                    Global.getLogger(CovenantShield::class.java).info("Added $ID as built-in to ${spec.hullId}")
                }
            }
        }

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

        stats.fluxCapacity.modifyMult(id, MAX_FLUX_MULT)

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

    override fun addPostDescriptionSection(
        tooltip: TooltipMakerAPI?, hullSize: ShipAPI.HullSize?, ship: ShipAPI?, width: Float, isForModSpec: Boolean
    ) {
        CodexLore.addHullmodLore(tooltip, ID)
    }

    override fun getDescriptionParam(index: Int, hullSize: ShipAPI.HullSize?, ship: ShipAPI?): String? {
        // No ship (Codex page, modspec tooltip): the per-hull values as a range across the Covenant
        // hulls, instead of the placeholder default row (which read 3000 / 1 / 1 / 1).
        if (ship == null) {
            when (index) {
                0 -> return hullRange { "${it.shieldHealth.roundToInt()}" to it.shieldHealth }
                1 -> return hullRange { "${it.shieldRegenRate.roundToInt()}" to it.shieldRegenRate }
                2 -> return hullRange { it.shieldRegenDelay.formatSeconds() to it.shieldRegenDelay }
                7 -> return hullRange { it.brokenShieldCooldown.formatSeconds() to it.brokenShieldCooldown }
            }
        }

        // The ship's own numbers: the hull's row with its shield hullmods applied.
        val base = ship?.hullSpec?.let { shieldData[it.baseHullId] ?: shieldData[it.hullId] }
            ?: generateDefaultShieldData()
        val data = CovenantShieldModifiers.effective(base, ship?.mutableStats)
        val falloffBonus = CovenantShieldModifiers.fluxFalloffBonus(ship?.mutableStats)
        return when (index) {
            0 -> "${data.shieldHealth.roundToInt()}"
            1 -> "${data.shieldRegenRate.roundToInt()}"
            2 -> "${data.shieldRegenDelay.formatSeconds()}"
            3 -> "${(CovenantShieldsStats.REGEN_SLOW_MULT * 100).toInt()}%"
            4 -> CovenantShieldsStats.REGEN_SLOW_DURATION.formatSeconds()
            5 -> "${(min(CovenantShieldsStats.fluxRegenKnee(falloffBonus), 1f) * 100).roundToInt()}%"
            6 -> "${(CovenantShieldsStats.fluxRegenMultAt(1f, falloffBonus) * 100).roundToInt()}%"
            7 -> "${data.brokenShieldCooldown.formatSeconds()}"
            8 -> "${getPercentage(MAX_FLUX_MULT)}%"
            else -> null
        }
    }

    /** "low to high" over every shield_data.csv row; [entry] gives (display text, sort value). */
    private fun hullRange(entry: (ShieldData) -> Pair<String, Float>): String {
        val values = shieldData.values.map(entry)
        val low = values.minByOrNull { it.second } ?: return "?"
        val high = values.maxByOrNull { it.second } ?: return "?"
        return if (low.first == high.first) low.first else "${low.first} to ${high.first}"
    }

    // Rounded to 2 decimals first: modified regen delays carry float noise (1.5 * 2/3 = 1.0000001).
    private fun Float.formatSeconds(): String {
        val rounded = (this * 100f).roundToInt() / 100f
        return if (rounded == rounded.toInt().toFloat()) "${rounded.toInt()}" else "$rounded"
    }
}

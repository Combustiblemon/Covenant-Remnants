package org.combustiblelemon.covenantremnants.hullmods

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.HullModEffect
import com.fs.starfarer.api.combat.ShipAPI
import org.combustiblelemon.covenantremnants.utils.reflection.ReflectionUtils

/**
 * Wraps a vanilla hullmod's effect so that it reports itself as incompatible with Covenant
 * Systems, vanilla-style (greyed out in the refit picker with an "Incompatible with ..." reason),
 * instead of being removed after the fact. Everything except the two applicability methods is
 * delegated untouched.
 */
class CovenantSystemsIncompatibilityWrapper(val wrapped: HullModEffect) : HullModEffect by wrapped {

    override fun isApplicableToShip(ship: ShipAPI): Boolean {
        if (ship.variant?.hasHullMod(CovenantSystems.ID) == true) return false
        return wrapped.isApplicableToShip(ship)
    }

    override fun getUnapplicableReason(ship: ShipAPI): String? {
        if (ship.variant?.hasHullMod(CovenantSystems.ID) == true) {
            return CovenantSystems.INCOMPATIBLE_REASON
        }
        return wrapped.getUnapplicableReason(ship)
    }
}

/**
 * Installs [CovenantSystemsIncompatibilityWrapper] onto the vanilla hullmods listed in
 * [CovenantSystems.INCOMPATIBLE_HULLMODS] at application load.
 *
 * The spec's effect field is located structurally (value identity first, HullModEffect-assignable
 * declared type as fallback) - never by name - so it survives obfuscation renames. Any failure is
 * logged and skipped; with the old MagicLib removal gone there is no fallback, so an ERROR here
 * means the hullmod stays installable alongside Covenant Systems.
 */
object CovenantIncompatibilityInstaller {
    private val logger = Global.getLogger(CovenantIncompatibilityInstaller::class.java)

    fun install() {
        for (modId in CovenantSystems.INCOMPATIBLE_HULLMODS.keys) {
            try {
                installFor(modId)
            } catch (t: Throwable) {
                logger.error(
                    "Covenant incompatibility wrapper FAILED for '$modId'; " +
                            "it will remain installable alongside Covenant Systems", t
                )
            }
        }
    }

    private fun installFor(modId: String) {
        val spec = Global.getSettings().getHullModSpec(modId)
        if (spec == null) {
            logger.warn("Hullmod '$modId' not found, skipping")
            return
        }
        val effect = spec.effect
        if (effect == null) {
            logger.warn("Hullmod '$modId' has no effect, skipping")
            return
        }
        if (effect is CovenantSystemsIncompatibilityWrapper) {
            logger.info("Hullmod '$modId' already wrapped, skipping")
            return
        }
        if (spec.effect !== effect) {
            logger.error("Hullmod '$modId' getEffect() is not identity-stable; cannot wrap, skipping")
            return
        }

        var fields = ReflectionUtils.findFieldsByValue(spec, effect)
        if (fields.isEmpty()) {
            fields = ReflectionUtils.findFieldsAssignableTo(spec, HullModEffect::class.java)
        }
        if (fields.isEmpty()) {
            logger.error("No field holding the HullModEffect found on ${spec.javaClass.name} for '$modId', skipping")
            return
        }

        val wrapper = CovenantSystemsIncompatibilityWrapper(effect)
        fields.forEach { it.set(spec, wrapper) }
        if (spec.effect !== wrapper) {
            fields.forEach { it.set(spec, effect) }
            logger.error("Wrapper install verification failed for '$modId'; rolled back")
            return
        }
        logger.info("Installed Covenant incompatibility wrapper on '$modId'")
    }
}

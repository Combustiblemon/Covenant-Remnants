package org.combustiblelemon.covenantremnants.hullmods

import org.combustiblelemon.lemonlib.hullmods.HullmodBlocker

/**
 * The mod's hullmod-incompatibility registry (vanilla hullmod id -> Covenant blockers), fed to
 * LemonLib's HullmodIncompatibilityInstaller at application load and re-verified on game load.
 * Each Covenant hullmod owns its blocked list; this only inverts them.
 */
object CovenantIncompatibilities {
    fun registry(): Map<String, List<HullmodBlocker>> {
        val out = LinkedHashMap<String, MutableList<HullmodBlocker>>()
        fun add(blocker: HullmodBlocker, ids: Collection<String>) =
            ids.forEach { out.getOrPut(it) { mutableListOf() }.add(blocker) }

        add(CovenantSystems.BLOCKER, CovenantSystems.INCOMPATIBLE_HULLMODS.keys)
        add(NanolaminateArmor.BLOCKER, NanolaminateArmor.INCOMPATIBLE_HULLMODS.keys)
        add(CovenantShield.BLOCKER, CovenantShield.INCOMPATIBLE_HULLMODS.keys - CovenantShield.MAKESHIFT_SHIELD_GENERATOR)
        add(CovenantShield.MAKESHIFT_BLOCKER, listOf(CovenantShield.MAKESHIFT_SHIELD_GENERATOR))
        return out
    }
}

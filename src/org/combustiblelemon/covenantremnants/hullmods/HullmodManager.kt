package org.combustiblelemon.covenantremnants.hullmods

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.fleet.FleetMemberAPI
import org.combustiblelemon.covenantremnants.utils.reflection.ReflectionUtils

const val TEMP_SHIP_PREFIX = "covenant_remnants_covenant_shield_temp"

class HullmodManager : BaseHullMod() {
    override fun getUnapplicableReason(ship: ShipAPI?): String? {
        return null
    }

    override fun isApplicableToShip(ship: ShipAPI?): Boolean {
        return true
    }

    override fun applyEffectsBeforeShipCreation(hullSize: ShipAPI.HullSize?, stats: MutableShipStatsAPI?, id: String?) {
        if (stats?.variant == null) {
            return
        }

        val variant = stats.variant
        val hullmodId = variant.hullMods.find { it == "covenant_remnants_covenantshieldhullmod" }

        if (hullmodId == null) {
            if (variant.hullSpec.hullId.startsWith(TEMP_SHIP_PREFIX)) {
                val hullId = variant.hullSpec.hullId.split(":")[1]

                val originalHullSpec = Global.getSettings().getHullSpec(hullId)
                if (originalHullSpec != null) {
                    variant.setHullSpecAPI(originalHullSpec)
                }
            }

            return
        }

        val hullIdPrefix = "${TEMP_SHIP_PREFIX}:"
        val hullIdCombined = hullIdPrefix + variant.hullSpec.hullId

        if (variant.hullSpec.hullId.startsWith(hullIdPrefix)) {
            return
        }

        var newSpec: ShipHullSpecAPI? = null

        try {
            newSpec = Global.getSettings().getHullSpec(hullIdCombined)
        } catch (_: RuntimeException) {
            // nothing needed here, getHullSpec crashes if there is nothing found
        }

        if (newSpec == null) {
            val hullSpec = variant.hullSpec

            if (hullSpec.hullId.startsWith(hullIdPrefix)) {
                return
            }

            newSpec = ReflectionUtils.invoke("clone", hullSpec) as ShipHullSpecAPI

            val stringFields = ReflectionUtils.getFieldsOfType(newSpec, newSpec.hullId.javaClass)

            for (field in stringFields) {
                val fieldValue = ReflectionUtils.get(field, newSpec)
                if (fieldValue is String && fieldValue == hullSpec.hullId) {
                    ReflectionUtils.set(field, newSpec, hullIdCombined)
                    break
                }
            }

            newSpec.shipDefenseId = "covenant_remnants_covenantshield"

            if (variant.hullSpec.defenseType != ShieldAPI.ShieldType.PHASE) {
                val shieldSpecField = ReflectionUtils.getFieldsOfType(newSpec, variant.hullSpec.shieldSpec.javaClass)

                if (shieldSpecField.isNotEmpty()) {
                    val field = shieldSpecField[0]
                    val fieldValue = ReflectionUtils.get(field, newSpec)

                    if (fieldValue is ShipHullSpecAPI.ShieldSpecAPI) {
                        val shieldTypeField =
                            ReflectionUtils.getFieldsOfType(fieldValue, variant.hullSpec.shieldType.javaClass)

                        if (shieldTypeField.isNotEmpty()) {
                            val field2 = shieldTypeField[0]
                            val fieldValue2 = ReflectionUtils.get(field2, fieldValue)

                            if (fieldValue2 is ShieldAPI.ShieldType) {
                                ReflectionUtils.set(field2, fieldValue, ShieldAPI.ShieldType.PHASE)
                            }
                        }
                    }
                }
            }
        }

        variant.setHullSpecAPI(newSpec)
    }

    override fun applyEffectsAfterShipCreation(ship: ShipAPI?, id: String?) {
    }

    override fun advanceInCampaign(member: FleetMemberAPI?, amount: Float) {
    }

    override fun advanceInCombat(ship: ShipAPI?, amount: Float) {
    }
}

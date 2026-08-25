package org.combustiblelemon.covenantremnants.hullmods

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.fleet.FleetMemberAPI
import org.combustiblelemon.covenantremnants.CovenantRemnantsPlugin
import org.combustiblelemon.lemonlib.reflection.ReflectionUtils

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
        val hullmodId = variant.hullMods.find { it == CovenantShield.ID }

        if (hullmodId == null) {
            if (variant.hullSpec.hasTag(TEMP_SHIP_PREFIX)) {
                val originalHullSpec = Global.getSettings().getHullSpec(variant.hullSpec.hullId)
                if (originalHullSpec != null) {
                    variant.setHullSpecAPI(originalHullSpec)
                }
            }

            return
        }

        val newSpec = createNewSpec(variant.hullSpec)

        if (newSpec == null) {
            return
        }

        variant.setHullSpecAPI(newSpec)
    }

    companion object {
        const val ID = "covenant_remnants_hullmodmanager"

        fun createNewSpec(hullSpec: ShipHullSpecAPI): ShipHullSpecAPI? {
            if (hullSpec.hasTag(TEMP_SHIP_PREFIX)) {
                return null
            }

            var newSpec: ShipHullSpecAPI? = null

            if (newSpec == null) {
                newSpec = ReflectionUtils.invoke("clone", hullSpec) as ShipHullSpecAPI

                newSpec.addTag(TEMP_SHIP_PREFIX)
                newSpec.shipDefenseId = CovenantShield.DEFENSE_ID

                if (hullSpec.defenseType != ShieldAPI.ShieldType.PHASE) {
                    val shieldSpecField = ReflectionUtils.getFieldsOfType(newSpec, hullSpec.shieldSpec.javaClass)

                    if (shieldSpecField.isNotEmpty()) {
                        val field = shieldSpecField[0]
                        val fieldValue = ReflectionUtils.get(field, newSpec)

                        if (fieldValue is ShipHullSpecAPI.ShieldSpecAPI) {
                            val shieldTypeField =
                                ReflectionUtils.getFieldsOfType(fieldValue, hullSpec.shieldType.javaClass)

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

            return newSpec
        }
    }

    override fun applyEffectsAfterShipCreation(ship: ShipAPI?, id: String?) {
    }

    override fun advanceInCampaign(member: FleetMemberAPI?, amount: Float) {
    }

    override fun advanceInCombat(ship: ShipAPI?, amount: Float) {
    }
}

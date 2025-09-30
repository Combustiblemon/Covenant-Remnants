package org.combustiblelemon.covenantremnants.utils.abilities

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CampaignFleetAPI
import com.fs.starfarer.api.campaign.JumpPointAPI.JumpDestination
import com.fs.starfarer.api.campaign.SectorEntityToken
import com.fs.starfarer.api.campaign.StarSystemAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.impl.campaign.abilities.EmergencyBurnAbility
import com.fs.starfarer.api.impl.campaign.ids.Stats
import com.fs.starfarer.api.util.Misc
import com.fs.starfarer.api.util.Misc.FleetMemberDamageLevel
import org.combustiblelemon.covenantremnants.CovenantRemnantsPlugin
import org.combustiblelemon.covenantremnants.campaign.systems.SLIPSPACE_JUMP_POINT_ENTRANCE
import org.combustiblelemon.covenantremnants.campaign.systems.SLIPSPACE_JUMP_POINT_EXIT
import org.combustiblelemon.covenantremnants.utils.extensions.isInSlipspace
import org.lwjgl.util.vector.Vector2f
import org.magiclib.kotlin.pickHiddenLocationNotNearStar
import java.util.*
import kotlin.math.roundToInt

const val SLIPSPACE_EXIT_TOKEN_SYSTEM = "${CovenantRemnantsPlugin.MOD_ID}_slipspace_exit_target_system"

fun calculateSlipspacePoints(systemName: String) {

    Misc.getDistance(
        Global.getSector().getStarSystem(systemName).location,
        Global.getSector().playerFleet.starSystem.location
    )

    val slipspaceSystem = Global.getSector().getStarSystem("Slipspace")

    slipspaceSystem.getEntityById(SLIPSPACE_JUMP_POINT_ENTRANCE).setLocation(0f, 0f)
    val exitToken = slipspaceSystem.getEntityById(SLIPSPACE_JUMP_POINT_EXIT)
    exitToken.setLocation(0f, 500f)
    exitToken.customData[SLIPSPACE_EXIT_TOKEN_SYSTEM] = systemName
}

private var CR_COST_MULT = 0.1f
private var FUEL_USE_MULT = 1f

private fun getNonReadyShips(fleet: CampaignFleetAPI): MutableList<FleetMemberAPI> {
    val result: MutableList<FleetMemberAPI> = ArrayList()

    //float crCostFleetMult = fleet.getStats().getDynamic().getValue(Stats.EMERGENCY_BURN_CR_MULT);
    //float crCostFleetMult = 1f;

    val crCostFleetMult: Float = fleet.stats.dynamic.getValue(Stats.DIRECT_JUMP_CR_MULT)
    for (member in fleet.fleetData.membersListCopy) {
        //if (member.isMothballed()) continue;
        val crLoss = member.deployCost * CR_COST_MULT * crCostFleetMult
        if ((member.repairTracker.cr * 100).roundToInt() < (crLoss * 100).roundToInt()) {
            result.add(member)
        }
    }

    return result
}

private fun getSlipspaceJumpDestination(): SectorEntityToken {
    val exit = getSlipspaceExit()

    val systemName = exit.customData.getOrDefault(SLIPSPACE_EXIT_TOKEN_SYSTEM, null) as String?

    if (systemName == null) {
        throw Exception("Fatal: Slipspace exit system was null.")
    }

    val destinationSystem = Global.getSector().getStarSystem(systemName)

    val dest = destinationSystem.pickHiddenLocationNotNearStar(Random(12345), 100f).orbit.computeCurrentLocation()

    return destinationSystem.createToken(dest.x, dest.y)
}

private fun getSlipspaceExit(): SectorEntityToken {
    return Global.getSector().getStarSystem("Slipspace").getEntityById(SLIPSPACE_JUMP_POINT_EXIT)
}

private fun getSlipspaceEntrance(): SectorEntityToken {
    return Global.getSector().getStarSystem("Slipspace").getEntityById(SLIPSPACE_JUMP_POINT_ENTRANCE)
}

fun initiateSlipspaceJump(fleet: CampaignFleetAPI, systemName: String) {
    if (fleet.isInSlipspace) {
        val exit = getSlipspaceJumpDestination()
        val loc = Misc.getPointAtRadius(exit.location, exit.radius + 200f + fleet.radius)
        val token = exit.containingLocation.createToken(loc.x, loc.y)

        val dest = JumpDestination(token, null)
        Global.getSector().doHyperspaceTransition(fleet, fleet, dest)
    } else {
        calculateSlipspacePoints(systemName)

        val crCostFleetMult: Float = fleet.stats.dynamic.getValue(Stats.DIRECT_JUMP_CR_MULT)
        if (crCostFleetMult > 0) {
            for (member in getNonReadyShips(fleet)) {
                if (Math.random().toFloat() < EmergencyBurnAbility.ACTIVATION_DAMAGE_PROB) {
                    Misc.applyDamage(
                        member, null, FleetMemberDamageLevel.LOW, false, null, null,
                        true, null, member.shipName + " suffers damage from Slipspace Jump activation"
                    )
                }
            }
            for (member in fleet.fleetData.membersListCopy) {
                val crLoss = member.deployCost * CR_COST_MULT * crCostFleetMult
                member.repairTracker.applyCREvent(-crLoss, "Slipspace jump")
            }
        }

        val cost: Float = fleet.logistics.fuelCostPerLightYear * FUEL_USE_MULT
        fleet.cargo.removeFuel(cost)

        val system = fleet.containingLocation as StarSystemAPI

        val offset = Vector2f.sub(fleet.location, system.center.location, Vector2f())
        val maxInSystem = 20000f
        val maxInHyper = 2000f
        var f = offset.length() / maxInSystem
        //if (f > 1) f = 1;
        if (f > 0.5f) f = 0.5f

        val angle = Misc.getAngleInDegreesStrict(offset)

        val destOffset = Misc.getUnitVectorAtDegreeAngle(angle)
        destOffset.scale(f * maxInHyper)

        Vector2f.add(system.location, destOffset, destOffset)
        val token = getSlipspaceEntrance()

        val dest = JumpDestination(token, null)
        Global.getSector().doHyperspaceTransition(fleet, fleet, dest)
    }
}
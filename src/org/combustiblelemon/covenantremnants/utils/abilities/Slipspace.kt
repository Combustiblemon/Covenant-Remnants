package org.combustiblelemon.covenantremnants.utils.abilities

import com.fs.graphics.util.Fader
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
import com.fs.starfarer.campaign.BaseLocation
import com.fs.starfarer.campaign.BaseScript
import com.fs.starfarer.campaign.CampaignEngine.TransitionState
import com.fs.starfarer.campaign.JumpPoint
import com.fs.starfarer.campaign.fleet.CampaignFleet
import com.fs.starfarer.util.IntervalTracker
import org.combustiblelemon.covenantremnants.CovenantRemnantsPlugin
import org.combustiblelemon.covenantremnants.campaign.systems.SLIPSPACE_JUMP_POINT_ENTRANCE
import org.combustiblelemon.covenantremnants.campaign.systems.SLIPSPACE_JUMP_POINT_EXIT
import org.combustiblelemon.covenantremnants.utils.extensions.isInSlipspace
import org.lwjgl.util.vector.Vector2f
import org.magiclib.kotlin.pickHiddenLocationNotNearStar
import java.util.*
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

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

//        val dest = JumpDestination(token, null)
//        Global.getSector().doHyperspaceTransition(fleet, fleet, dest)
        doSlipspaceTransition(JumpDestination(token, ""), fleet)

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

//        val dest = JumpDestination(token, null)
//        Global.getSector().doHyperspaceTransition(fleet, fleet, dest)
        doSlipspaceTransition(JumpDestination(token, ""), fleet)
    }
}

fun doSlipspaceTransition(destination: JumpDestination, fleet: CampaignFleetAPI, warpOutDuration: Float = 3f) {
//    fleet.containingLocation.removeEntity(fleet)
//    destination.containingLocation.addEntity(fleet)
//    Global.getSector().currentLocation = destination.containingLocation
//    fleet.setLocation(
//        destination.location.x,
//        destination.location.y
//    )
//    fleet.setNoEngaging(2.0f)
//    fleet.clearAssignments()
//    fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, destination, 1f)

    val campaignFleet = fleet as CampaignFleet
    val isPlayerFleet = fleet.isPlayerFleet
    val sector = Global.getSector()

    sector.addScript(object : BaseScript() {
        var untilCanWarpOut: Float = warpOutDuration
        var done: Boolean = false
        var elapsedWarpingOut: Float = 0.0f
        var elapsedWarpingIn: Float = 0.0f
        private var state: TransitionState = TransitionState.START
        private var tracker: IntervalTracker = IntervalTracker(0.05f, 0.2f)
        private var elapsedForMove = 0f
        private var warpRateMult = 1.0f
        private var fader: Fader = Fader(0.0f, 0.25f, 0.25f)

        override fun isDone(): Boolean {
            if (this.done) {
                campaignFleet.setInJumpTransition(false)
                campaignFleet.isInJumpTransitionWarpOutStage = false
                campaignFleet.jumpDestination = null
            }

            return this.done
        }

        private val pitch: Float
            get() = 1.0f + (Math.random().toFloat() - 0.5f) * 0.1f

        override fun advance(amount: Float) {
            // val fleetViews: MutableCollection<CampaignFleetMemberView> = campaignFleet.fleetView.views
            campaignFleet.setNoEngaging(30.0f)

            val combatEngine = Global.getCombatEngine()

            when (this.state) {
                TransitionState.START -> {
                    combatEngine.addFloatingText(
                        combatEngine.playerShip.location,
                        "Slipspace jump start",
                        40f,
                        Misc.getTextColor(),
                        combatEngine.playerShip,
                        0f, 0f
                    )



                    if (destination.destination is JumpPoint) {
                        (destination.destination as JumpPoint).open()
                    }

                    campaignFleet.stats.addTemporaryModMult(
                        0.1f,
                        "going_through_jump_point",
                        "Going through jump-point",
                        10.0f,
                        campaignFleet.stats.accelerationMult
                    )

                    this.warpRateMult = 1.0f
                    this.state = TransitionState.MOVING_TO_POINT
                }

                TransitionState.MOVING_TO_POINT -> {
                    combatEngine.addFloatingText(
                        combatEngine.playerShip.location,
                        "Slipspace moving to point",
                        40f,
                        Misc.getTextColor(),
                        combatEngine.playerShip,
                        0f, 0f
                    )

                    this.untilCanWarpOut -= amount
                    campaignFleet.setMoveDestinationOverride(fleet.location.x, fleet.location.y)
                    var distanceTo = Misc.getDistance(fleet.location, campaignFleet.location)
                    distanceTo -= fleet.radius * 0.5f
                    var radius: Float = campaignFleet.radius
                    if (sector.isInFastAdvance) {
                        radius = 1000.0f
                    } else if (!campaignFleet.isInCurrentLocation) {
                        radius = 500.0f
                    }

                    if (distanceTo <= radius && this.untilCanWarpOut <= 0.0f) {
                        this.state = TransitionState.WARPING_OUT
                        this.tracker.forceIntervalElapsed()
                    }

                    this.elapsedForMove += amount
                    if ((this.elapsedForMove > 2.0f + warpOutDuration && !sector.isInFastAdvance || this.elapsedForMove > 30.0f) && this.state == TransitionState.MOVING_TO_POINT) {
                        this.state = TransitionState.ABORT
                    }
                }

                TransitionState.WARPING_OUT -> {
                    combatEngine.addFloatingText(
                        combatEngine.playerShip.location,
                        "Slipspace jump warping out",
                        40f,
                        Misc.getTextColor(),
                        combatEngine.playerShip,
                        0f, 0f
                    )

                    this.elapsedWarpingOut += amount
                    campaignFleet.setMoveDestinationOverride(fleet.location.x, fleet.location.y)
                    campaignFleet.isInJumpTransitionWarpOutStage = true

                    if (this.elapsedWarpingOut > 5.0f) {
                        this.state = TransitionState.SWITCHING_LOCATIONS
                    }
                }

                TransitionState.FADE_OUT -> {
                    combatEngine.addFloatingText(
                        combatEngine.playerShip.location,
                        "Slipspace jump fade out",
                        40f,
                        Misc.getTextColor(),
                        combatEngine.playerShip,
                        0f, 0f
                    )

                    campaignFleet.setMoveDestinationOverride(fleet.location.x, fleet.location.y)

                    this.fader.advance(amount)

                    if (this.fader.isFadedIn) {
                        this.state = TransitionState.SWITCHING_LOCATIONS
                    }
                }

                TransitionState.SWITCHING_LOCATIONS -> {
                    combatEngine.addFloatingText(
                        combatEngine.playerShip.location,
                        "Slipspace jump switching locations",
                        40f,
                        Misc.getTextColor(),
                        combatEngine.playerShip,
                        0f, 0f
                    )

                    val destinationToken: SectorEntityToken = destination.destination
                    val destinationLocation = destinationToken.containingLocation
                    val currentLocation: BaseLocation = campaignFleet.containingLocation
                    if (isPlayerFleet) {
                        Global.getSector().currentLocation = destinationLocation
                    }

                    currentLocation.removeEntity(campaignFleet)
                    destinationLocation.addEntity(campaignFleet)
                    val angle = Math.random().toFloat() * 360.0f
                    val distance: Float = destination.minDistFromToken + Math.random()
                        .toFloat() * (destination.maxDistFromToken - destination.minDistFromToken)
                    val offsetX = cos(Math.toRadians(angle.toDouble())).toFloat() * distance
                    val offsetY = sin(Math.toRadians(angle.toDouble())).toFloat() * distance
                    campaignFleet.orbit = null
                    campaignFleet.setLocation(
                        destinationToken.location.x + offsetX,
                        destinationToken.location.y + offsetY
                    )
                    campaignFleet.setMoveDestinationOverride(
                        destinationToken.location.x + offsetX,
                        destinationToken.location.y + offsetY
                    )
                    if (isPlayerFleet) {
                        this.state = TransitionState.FADE_IN
                        this.fader.fadeOut()
                    } else {
                        this.state = TransitionState.WARPING_IN
                        this.tracker.forceIntervalElapsed()
                    }
                }

                TransitionState.FADE_IN -> {
                    combatEngine.addFloatingText(
                        combatEngine.playerShip.location,
                        "Slipspace jump fade in",
                        40f,
                        Misc.getTextColor(),
                        combatEngine.playerShip,
                        0f, 0f
                    )

                    this.fader.advance(amount)

                    if (this.fader.isFadedOut) {
                        this.state = TransitionState.WARPING_IN
                        this.tracker.forceIntervalElapsed()
                    }
                }

                TransitionState.WARPING_IN -> {
                    combatEngine.addFloatingText(
                        combatEngine.playerShip.location,
                        "Slipspace jump warp in",
                        40f,
                        Misc.getTextColor(),
                        combatEngine.playerShip,
                        0f, 0f
                    )


                    this.elapsedWarpingIn += amount


                    if (this.elapsedWarpingIn > 5.0f) {
                        this.warpRateMult = 1.0f
                        this.state = TransitionState.FINISH
                    } else {
                        campaignFleet.setMoveDestinationOverride(campaignFleet.location.x, campaignFleet.location.y)
                        this.tracker.advance(amount * this.warpRateMult)
                        if (this.tracker.intervalElapsed()) {
                            this.warpRateMult *= 1.1f
                        }
                    }
                }

                TransitionState.FINISH -> {
                    combatEngine.addFloatingText(
                        combatEngine.playerShip.location,
                        "Slipspace jump finish",
                        40f,
                        Misc.getTextColor(),
                        combatEngine.playerShip,
                        0f, 0f
                    )


                    sector.viewport.alphaMult = 1.0f
                    campaignFleet.setNoEngaging(1.0f)
                    if (campaignFleet.getMoveDestinationSetWhileInLocation() == null || !campaignFleet.getMoveDestinationSetWhileInLocation()
                            .isCurrentLocation
                    ) {
                        campaignFleet.setMoveDestinationOverride(campaignFleet.location.x, campaignFleet.location.y)
                    }

                    this.done = true
                    sector.reportFleetJumped(campaignFleet, fleet, destination)
                }

                TransitionState.ABORT -> {
                    combatEngine.addFloatingText(
                        combatEngine.playerShip.location,
                        "Slipspace jump abort",
                        40f,
                        Misc.getTextColor(),
                        combatEngine.playerShip,
                        0f, 0f
                    )

                    sector.viewport.alphaMult = 1.0f
                    campaignFleet.setMoveDestinationOverride(campaignFleet.location.x, campaignFleet.location.y)
                    campaignFleet.isInJumpTransitionWarpOutStage = false
                    this.done = true
                }
            }
        }
    })
}

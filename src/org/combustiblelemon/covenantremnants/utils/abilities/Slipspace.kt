package org.combustiblelemon.covenantremnants.utils.abilities

import com.fs.graphics.util.Fader
import com.fs.starfarer.D.M
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CampaignFleetAPI
import com.fs.starfarer.api.campaign.JumpPointAPI.JumpDestination
import com.fs.starfarer.api.campaign.OrbitAPI
import com.fs.starfarer.api.campaign.SectorEntityToken
import com.fs.starfarer.api.campaign.StarSystemAPI
import com.fs.starfarer.api.combat.ShipAPI.HullSize
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.impl.campaign.abilities.EmergencyBurnAbility
import com.fs.starfarer.api.impl.campaign.ids.Stats
import com.fs.starfarer.api.util.Misc
import com.fs.starfarer.api.util.Misc.FleetMemberDamageLevel
import com.fs.starfarer.campaign.BaseLocation
import com.fs.starfarer.campaign.BaseScript
import com.fs.starfarer.campaign.CampaignEngine
import com.fs.starfarer.campaign.CampaignEngine.TransitionState
import com.fs.starfarer.campaign.JumpPoint
import com.fs.starfarer.campaign.fleet.CampaignFleet
import com.fs.starfarer.campaign.fleet.CampaignFleetMemberView
import com.fs.starfarer.prototype.Utils
import com.fs.starfarer.util.IntervalTracker
import org.combustiblelemon.covenantremnants.CovenantRemnantsPlugin
import org.combustiblelemon.covenantremnants.campaign.systems.SLIPSPACE_JUMP_POINT_ENTRANCE
import org.combustiblelemon.covenantremnants.campaign.systems.SLIPSPACE_JUMP_POINT_EXIT
import org.combustiblelemon.covenantremnants.utils.extensions.isInSlipspace
import org.lwjgl.util.vector.Vector2f
import org.magiclib.kotlin.pickHiddenLocationNotNearStar
import java.awt.Color
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

    val var3 = destination
    val var4 = warpOutDuration
    val campaignFleet = fleet as CampaignFleet
    val var6 = fleet.isPlayerFleet
    val sector = Global.getSector()

    sector.addScript(object : BaseScript() {
        var untilCanWarpOut: Float = warpOutDuration
        var done: Boolean = false
        var elapsedWarpingOut: Float = 0.0f
        var elapsedWarpingIn: Float = 0.0f
        private var state: TransitionState = TransitionState.START
        private var warping: MutableSet<CampaignFleetMemberView> = HashSet<CampaignFleetMemberView>()
        private var tracker: IntervalTracker = IntervalTracker(0.05f, 0.2f)
        private var elapsedForMove = 0f
        private var warpRateMult = 1.0f
        private var fader: Fader = Fader(0.0f, 0.25f, 0.25f)

        override fun isDone(): Boolean {
            if (this.done) {
                campaignFleet.setInJumpTransition(false)
                campaignFleet.isInJumpTransitionWarpOutStage = false
                campaignFleet.jumpDestination = null as JumpDestination?
            }

            return this.done
        }

        private fun getColorForView(var1: CampaignFleetMemberView): Color? {
            return var1.member.hullSpec.hullStyle.Ôo0000()
        }

        private fun getVolumeForView(var1: CampaignFleetMemberView): Float {
            val var2x = var1.member.hullSpec.hullSize
            when (var2x) {
                HullSize.FIGHTER -> {
                    return 0.5f
                }

                HullSize.FRIGATE -> {
                    return 0.6f
                }

                HullSize.DESTROYER -> {
                    return 0.7f
                }

                HullSize.CRUISER -> {
                    return 0.8f
                }

                HullSize.CAPITAL_SHIP -> {
                    return 1.0f
                }

                else -> {
                    return 1.0f
                }
            }
        }

        private val pitch: Float
            get() = 1.0f + (Math.random().toFloat() - 0.5f) * 0.1f

        override fun advance(var1: Float) {
            val var2x: MutableCollection<CampaignFleetMemberView> = campaignFleet.fleetView.views
            campaignFleet.setNoEngaging(30.0f)
            if (this.state == TransitionState.START) {
                if (var3.destination is JumpPoint) {
                    (var3.destination as JumpPoint).open()
                }

                campaignFleet.stats.addTemporaryModMult(
                    0.1f,
                    "going_through_jump_point",
                    "Going through jump-point",
                    10.0f,
                    campaignFleet.stats.accelerationMult
                )
            }

            if (this.state == TransitionState.START) {
                this.warpRateMult = 1.0f
                this.warping.clear()
                this.state = TransitionState.MOVING_TO_POINT
            }

            if (this.state == TransitionState.MOVING_TO_POINT) {
                this.untilCanWarpOut -= var1
                campaignFleet.setMoveDestinationOverride(fleet.location.x, fleet.location.y)
                var var3x = Utils.Ø00000(fleet.location, campaignFleet.location)
                var3x -= fleet.radius * 0.5f
                var var4x: Float = campaignFleet.radius
                if (sector.isInFastAdvance) {
                    var4x = 1000.0f
                } else if (!campaignFleet.isInCurrentLocation) {
                    var4x = 500.0f
                }

                if (var3x <= var4x && this.untilCanWarpOut <= 0.0f) {
                    this.state = TransitionState.WARPING_OUT
                    this.tracker.forceIntervalElapsed()
                }

                this.elapsedForMove += var1
                if ((this.elapsedForMove > 2.0f + var4 && !sector.isInFastAdvance || this.elapsedForMove > 30.0f) && this.state == TransitionState.MOVING_TO_POINT) {
                    this.state = TransitionState.ABORT
                }
            }

            if (this.state == TransitionState.WARPING_OUT) {
                this.elapsedWarpingOut += var1
                campaignFleet.setMoveDestinationOverride(fleet.location.x, fleet.location.y)

                campaignFleet.isInJumpTransitionWarpOutStage = true
                if (this.warping.size >= var2x.size) {
                    var var11 = true

                    for (var18 in this.warping) {
                        if (var18.isJittering && var2x.contains(var18)) {
                            var11 = false
                        } else {
                            var18.extraAlphaMult = 0.0f
                        }
                    }

                    if (var11 || this.elapsedWarpingOut > 5.0f) {
                        this.warpRateMult = 1.0f
                        this.warping.clear()
                        if (var6) {
                            this.state = TransitionState.FADE_OUT
                            this.fader.fadeIn()
                            CampaignEngine.getInstance().campaignUI.showNoise(0.5f, 0.25f, 1.5f)
                        } else {
                            this.state = TransitionState.SWITCHING_LOCATIONS
                        }
                    }
                } else {
                    this.tracker.advance(var1 * this.warpRateMult)
                    if (this.tracker.intervalElapsed()) {
                        for (var12 in var2x) {
                            if (!this.warping.contains(var12)) {
                                if (this.warping.isEmpty() && CampaignEngine.getInstance()
                                        .currentLocation === campaignFleet.containingLocation
                                ) {
                                    M.o00000(
                                        "warp_out_start",
                                        1.0f,
                                        1.0f,
                                        campaignFleet.location,
                                        campaignFleet.velocity
                                    )
                                }

                                this.warping.add(var12)
                                this.warpRateMult *= 1.1f
                                if (CampaignEngine.getInstance()
                                        .currentLocation === campaignFleet.containingLocation
                                ) {
                                    var12.setJitter(1.0f, 0.0f, this.getColorForView(var12), 7, 15.0f)
                                    M.o00000(
                                        "ship_warp_out",
                                        this.pitch,
                                        this.getVolumeForView(var12),
                                        var12.absoluteLocation,
                                        var12.absoluteVelocity
                                    )
                                }
                                break
                            }
                        }
                    }
                }
            }

            if (this.state == TransitionState.FADE_OUT) {
                campaignFleet.setMoveDestinationOverride(fleet.location.x, fleet.location.y)

                this.fader.advance(var1)


                if (this.fader.isFadedIn) {
                    this.state = TransitionState.SWITCHING_LOCATIONS
                }
            }

            if (this.state == TransitionState.SWITCHING_LOCATIONS) {
                val var13: SectorEntityToken = var3.destination
                val var20 = var13.containingLocation
                val var25: BaseLocation = campaignFleet.containingLocation
                if (var6) {
                    Global.getSector().currentLocation = var20
                }

                var25.removeEntity(campaignFleet)
                var20.addEntity(campaignFleet)
                campaignFleet.indicator.Ó00000().forceOut()
                campaignFleet.indicator.Ó00000().fadeIn()
                val var6x = Math.random().toFloat() * 360.0f
                val var7: Float = var3.minDistFromToken + Math.random()
                    .toFloat() * (var3.maxDistFromToken - var3.minDistFromToken)
                val var8 = cos(Math.toRadians(var6x.toDouble())).toFloat() * var7
                val var9 = sin(Math.toRadians(var6x.toDouble())).toFloat() * var7
                campaignFleet.orbit = null as OrbitAPI?
                campaignFleet.setLocation(var13.location.x + var8, var13.location.y + var9)
                campaignFleet.setMoveDestinationOverride(var13.location.x + var8, var13.location.y + var9)
                if (var6) {
                    this.state = TransitionState.FADE_IN
                    this.fader.fadeOut()
                } else {
                    this.state = TransitionState.WARPING_IN
                    this.tracker.forceIntervalElapsed()
                }
            }

            if (this.state == TransitionState.FADE_IN) {
                this.fader.advance(var1)


                if (this.fader.isFadedOut) {
                    this.state = TransitionState.WARPING_IN
                    this.tracker.forceIntervalElapsed()
                }
            }

            if (this.state == TransitionState.WARPING_IN) {
                this.elapsedWarpingIn += var1
                if (this.warping.size >= var2x.size) {
                    var var14 = true

                    for (var21 in this.warping) {
                        if (var21.isJittering && var2x.contains(var21)) {
                            var14 = false
                            break
                        }
                    }

                    if (var14 || this.elapsedWarpingIn > 5.0f) {
                        this.warpRateMult = 1.0f
                        this.warping.clear()
                        this.state = TransitionState.FINISH
                    }
                } else {
                    campaignFleet.setMoveDestinationOverride(campaignFleet.location.x, campaignFleet.location.y)
                    this.tracker.advance(var1 * this.warpRateMult)
                    if (this.tracker.intervalElapsed()) {
                        for (var15 in var2x) {
                            if (!this.warping.contains(var15)) {
                                if (this.warping.isEmpty() && CampaignEngine.getInstance()
                                        .currentLocation === campaignFleet.containingLocation
                                ) {
                                    M.o00000(
                                        "warp_in_start",
                                        1.0f,
                                        1.0f,
                                        campaignFleet.location,
                                        campaignFleet.velocity
                                    )
                                }

                                this.warping.add(var15)
                                this.warpRateMult *= 1.1f
                                if (CampaignEngine.getInstance()
                                        .currentLocation === campaignFleet.containingLocation
                                ) {
                                    var15.setJitter(0.0f, 1.0f, this.getColorForView(var15), 7, 15.0f)
                                    M.o00000(
                                        "ship_warp_in",
                                        this.pitch,
                                        this.getVolumeForView(var15),
                                        var15.absoluteLocation,
                                        var15.absoluteVelocity
                                    )
                                }

                                var15.extraAlphaMult = 1.0f
                                break
                            }
                        }
                    }
                }
            }

            if (this.state == TransitionState.FINISH) {
                for (var16 in var2x) {
                    var16.extraAlphaMult = 1.0f
                }

                sector.viewport.alphaMult = 1.0f
                campaignFleet.setNoEngaging(1.0f)
                if (campaignFleet.getMoveDestinationSetWhileInLocation() == null || !campaignFleet.getMoveDestinationSetWhileInLocation()
                        .isCurrentLocation
                ) {
                    campaignFleet.setMoveDestinationOverride(campaignFleet.location.x, campaignFleet.location.y)
                }

                this.done = true
                sector.reportFleetJumped(campaignFleet, fleet, var3)
            }

            if (this.state == TransitionState.ABORT) {
                for (var17 in var2x) {
                    var17.extraAlphaMult = 1.0f
                }

                sector.viewport.alphaMult = 1.0f
                campaignFleet.setMoveDestinationOverride(campaignFleet.location.x, campaignFleet.location.y)
                campaignFleet.isInJumpTransitionWarpOutStage = false
                campaignFleet.indicator.Ó00000().fadeIn()
                this.done = true
            }
        }
    })
}

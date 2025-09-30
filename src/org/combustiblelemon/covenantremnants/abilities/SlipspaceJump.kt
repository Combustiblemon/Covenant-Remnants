package org.combustiblelemon.covenantremnants.abilities

import com.fs.starfarer.api.EveryFrameScript
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.BattleAPI
import com.fs.starfarer.api.campaign.CampaignFleetAPI
import com.fs.starfarer.api.campaign.StarSystemAPI
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.impl.campaign.abilities.BaseDurationAbility
import com.fs.starfarer.api.impl.campaign.ids.Pings
import com.fs.starfarer.api.impl.campaign.ids.Stats
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin
import com.fs.starfarer.api.impl.campaign.tutorial.TutorialMissionIntel
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.Misc
import com.fs.starfarer.campaign.CampaignEntity
import org.combustiblelemon.covenantremnants.utils.abilities.initiateSlipspaceJump
import org.combustiblelemon.covenantremnants.utils.extensions.isInSlipspace
import java.awt.Color
import kotlin.math.max

class SlipspaceJump : BaseDurationAbility() {

    var CR_COST_MULT = 0.1f
    var FUEL_USE_MULT = 1f

    var primed: Boolean? = null
    var ping: EveryFrameScript? = null
    var exitPoint: CampaignEntity? = null
    var entryPoint: CampaignEntity? = null

    override fun activateImpl() {
        val fleet: CampaignFleetAPI? = getFleet()
        if (fleet == null) return

        if (fleet.isInHyperspaceTransition) return

        if (!fleet.isInHyperspace &&
            fleet.containingLocation is StarSystemAPI
        ) {
            ping = Global.getSector().addPing(fleet, Pings.TRANSVERSE_JUMP)
            primed = true
        } else {
            deactivate()
        }
    }

    override fun deactivate() {
        if (ping != null) {
            Global.getSector().removeScript(ping)
            ping = null
        }
        super.deactivate()
    }

    override fun applyEffect(amount: Float, level: Float) {
        val fleet: CampaignFleetAPI? = getFleet()
        if (fleet == null) return

        if (level > 0 && level < 1 && amount > 0) {
            val activateSeconds: Float = activationDays * Global.getSector().clock.secondsPerDay
            val speed = fleet.velocity.length()
            val acc = max(speed, 200f) / activateSeconds + fleet.acceleration
            var ds = acc * amount
            if (ds > speed) ds = speed
            val dv = Misc.getUnitVectorAtDegreeAngle(Misc.getAngleInDegrees(fleet.velocity))
            dv.scale(ds)
            fleet.setVelocity(fleet.velocity.x - dv.x, fleet.velocity.y - dv.y)
            return
        }

        if (level == 1f && primed != null) {
            initiateSlipspaceJump(fleet, "Askonia")
            primed = null
        }
    }

    override fun getActivationText(): String? {
        return super.getActivationText()
    }

    override fun deactivateImpl() {
        cleanupImpl()
    }

    override fun cleanupImpl() {
        val fleet: CampaignFleetAPI? = getFleet()
        if (fleet == null) return
    }

    override fun isUsable(): Boolean {
        if (!super.isUsable()) {
            return false
        }

        if (getFleet() == null) {
            return false
        }

        val fleet: CampaignFleetAPI = getFleet()

        if (fleet.isInHyperspaceTransition) {
            return false
        }

        if (TutorialMissionIntel.isTutorialInProgress()) {
            return false
        }

        if (fleet.isInHyperspace) {
            return false
        }

        if (!fleet.isInSlipspace && computeFuelCost() <= getFleet().cargo.fuel) {
            return true
        }

        return fleet.isInSlipspace
    }

    override fun createTooltip(tooltip: TooltipMakerAPI, expanded: Boolean) {
        val fleet: CampaignFleetAPI? = getFleet()
        if (fleet == null) return

        Misc.getGrayColor()
        val highlight = Misc.getHighlightColor()
        Global.getSettings().getColor("progressBarFuelColor")
        val bad = Misc.getNegativeHighlightColor()

        if (!Global.CODEX_TOOLTIP_MODE) {
            tooltip.addTitle("Slipspace Jump")
        } else {
            tooltip.addSpacer(-10f)
        }

        val pad = 10f


        tooltip.addPara(
            "Jump into hyperspace without the use of a jump-point, or " +
                    "jump into a star system across the hyperspace boundary near a nascent gravity well, " +
                    "emerging near the entity corresponding to the gravity well.", pad
        )


        if (Global.CODEX_TOOLTIP_MODE) {
            var years = "year's"
            if (FUEL_USE_MULT != 1f) years = "years'"
            tooltip.addPara(
                ("Jumping into hyperspace consumes %s light " + years + " worth of fuel and reduces the combat readiness "
                        + "of all ships by %s of a combat deployment. " +
                        "Jumping into a star system is free."), pad,
                highlight,
                "" + Misc.getRoundedValue(FUEL_USE_MULT),
                "" + Math.round(CR_COST_MULT * 100f) + "%"
            )

            tooltip.addPara(
                "Ships with insufficient combat readiness may suffer damage when the ability is activated.",
                pad
            )
        } else {
            val fuelCost: Float = computeFuelCost()
            val supplyCost: Float = computeSupplyCost()

            if (supplyCost > 0) {
                tooltip.addPara(
                    "Jumping into hyperspace consumes %s fuel and slightly reduces the combat readiness" +
                            " of all ships, costing up to %s supplies to recover. Jumping into a star system is free.",
                    pad,
                    highlight,
                    Misc.getRoundedValueMaxOneAfterDecimal(fuelCost),
                    Misc.getRoundedValueMaxOneAfterDecimal(supplyCost)
                )
            } else {
                tooltip.addPara(
                    "Jumping into hyperspace consumes %s fuel. Jumping into a star system is free.", pad,
                    highlight,
                    Misc.getRoundedValueMaxOneAfterDecimal(fuelCost)
                )
            }


            if (TutorialMissionIntel.isTutorialInProgress()) {
                tooltip.addPara("Can not be used right now.", bad, pad)
            }

            if (!fleet.isInHyperspace) {
                if (fuelCost > fleet.cargo.fuel) {
                    tooltip.addPara("Not enough fuel.", bad, pad)
                }

                val nonReady: MutableList<FleetMemberAPI> = getNonReadyShips()
                if (!nonReady.isEmpty()) {
                    //tooltip.addPara("Not all ships have enough combat readiness to initiate an emergency burn. Ships that require higher CR:", pad);
                    tooltip.addPara(
                        "Some ships don't have enough combat readiness to safely initiate a transverse jump " +
                                "and may suffer damage if the ability is activated:", pad,
                        Misc.getNegativeHighlightColor(), "may suffer damage"
                    )
                    var j = 0
                    val max = 4
                    var initPad = 5f
                    for (member in nonReady) {
                        if (j >= max) {
                            if (nonReady.size > max + 1) {
                                tooltip.addPara(BaseIntelPlugin.INDENT + "... and several other ships", initPad)
                                break
                            }
                        }
                        var str: String? = ""
                        if (!member.isFighterWing) {
                            str += member.shipName + ", "
                            str += member.hullSpec.hullNameWithDashClass
                        } else {
                            str += member.variant.fullDesignationWithHullName
                        }

                        tooltip.addPara(BaseIntelPlugin.INDENT + str, initPad)
                        initPad = 0f
                        j++
                    }
                }
            }

            if (fleet.isInHyperspace) {
                tooltip.addPara("Connot be used in Hyperspace.", bad, pad)
            }
        }

        addIncompatibleToTooltip(tooltip, expanded)
    }

    override fun hasTooltip(): Boolean {
        return true
    }

    override fun fleetLeftBattle(battle: BattleAPI?, engagedInHostilities: Boolean) {
        if (engagedInHostilities) {
            deactivate()
        }
    }

    override fun fleetOpenedMarket(market: MarketAPI?) {
        deactivate()
    }

    fun getNonReadyShips(): MutableList<FleetMemberAPI> {
        val result: MutableList<FleetMemberAPI> = ArrayList<FleetMemberAPI>()
        val fleet: CampaignFleetAPI? = getFleet()
        if (fleet == null) return result

        //float crCostFleetMult = fleet.getStats().getDynamic().getValue(Stats.EMERGENCY_BURN_CR_MULT);
        //float crCostFleetMult = 1f;
        val crCostFleetMult: Float = getCRCostMult(fleet)
        for (member in fleet.fleetData.membersListCopy) {
            //if (member.isMothballed()) continue;
            val crLoss = member.deployCost * CR_COST_MULT * crCostFleetMult
            if (Math.round(member.repairTracker.cr * 100) < Math.round(crLoss * 100)) {
                result.add(member)
            }
        }
        return result
    }

    fun computeFuelCost(): Float {
        val fleet: CampaignFleetAPI? = getFleet()
        if (fleet == null) return 0f

        val cost = fleet.logistics.fuelCostPerLightYear * FUEL_USE_MULT
        return cost
    }

    fun getCRCostMult(fleet: CampaignFleetAPI): Float {
        var crCostFleetMult = fleet.stats.dynamic.getValue(Stats.DIRECT_JUMP_CR_MULT)
        val key = "\$makeTranverseJumpCostMoreCROnce"
        if (fleet.memoryWithoutUpdate.contains(key)) {
            crCostFleetMult = 20f
        }
        return crCostFleetMult
    }

    fun computeSupplyCost(): Float {
        val fleet: CampaignFleetAPI? = getFleet()
        if (fleet == null) return 0f

        //float crCostFleetMult = fleet.getStats().getDynamic().getValue(Stats.EMERGENCY_BURN_CR_MULT);
        //float crCostFleetMult = 1f;
        val crCostFleetMult = getCRCostMult(fleet)

        var cost = 0f
        for (member in fleet.fleetData.membersListCopy) {
            cost += member.deploymentCostSupplies * CR_COST_MULT * crCostFleetMult
        }
        return cost
    }

    fun showAlarm(): Boolean {
        if (getFleet() != null && getFleet().isInHyperspace) return false
        return !getNonReadyShips().isEmpty() && !isOnCooldown && !isActiveOrInProgress && isUsable()
    }

    //	@Override
    //	public boolean isUsable() {
    //		return super.isUsable() &&
    //					getFleet() != null &&
    //					//getNonReadyShips().isEmpty() &&
    //					(getFleet().isAIMode() || computeFuelCost() <= getFleet().getCargo().getFuel());
    //	}
    override fun getCooldownFraction(): Float {
        if (showAlarm()) {
            return 0f
        }
        return super.getCooldownFraction()
    }

    override fun showCooldownIndicator(): Boolean {
        return super.showCooldownIndicator()
    }

    override fun isOnCooldown(): Boolean {
        return super.getCooldownFraction() < 1f
    }

    override fun getCooldownColor(): Color? {
        if (showAlarm()) {
            val color = Misc.getNegativeHighlightColor()
            return Misc.scaleAlpha(color, Global.getSector().campaignUI.sharedFader.brightness * 0.5f)
        }
        return super.getCooldownColor()
    }

    override fun isCooldownRenderingAdditive(): Boolean {
        return showAlarm()
    }
}
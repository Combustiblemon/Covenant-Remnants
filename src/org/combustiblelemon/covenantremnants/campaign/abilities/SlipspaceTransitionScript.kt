package org.combustiblelemon.covenantremnants.campaign.abilities

import com.fs.starfarer.api.EveryFrameScript
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CampaignFleetAPI
import com.fs.starfarer.api.campaign.JumpPointAPI.JumpDestination
import com.fs.starfarer.api.campaign.SectorEntityToken
import com.fs.starfarer.api.util.FaderUtil
import com.fs.starfarer.campaign.fleet.CampaignFleet
import com.fs.starfarer.campaign.fleet.CampaignFleetMemberView
import java.awt.Color
import kotlin.math.max
import kotlin.math.min

/**
 * The custom location switch of the campaign Slipspace Jump (developer ruling 2026-10-07: fully
 * custom, not the engine's `doHyperspaceTransition`). Runs as a transient sector-level script,
 * because switching a fleet's location from inside that fleet's own advance (where abilities run)
 * would mutate the location being iterated.
 *
 * States: HOLD (fleet already hidden inside the portal; the collapse plays) → FADE_OUT (player
 * only: screen to black) → SWITCH (remove / add the fleet, current location, place at the target
 * token) → FADE_IN → WARP_IN (hold, then the member views fade back in with jitter) → FINISH.
 * Mirrors the engine's own transition where it matters (`setInJumpTransition`, `setNoEngaging`,
 * `reportFleetJumped`); the obf `CampaignFleet` / `CampaignFleetMemberView` are `DoNotObfuscate`.
 */
class SlipspaceTransitionScript private constructor(
    private val fleet: CampaignFleetAPI,
    val leg: Leg,
    private val target: SectorEntityToken,
    private val ability: SlipspaceJump,
    private val arrivalFacing: Float?,
    startState: State
) : EveryFrameScript {

    enum class Leg { OUT, BACK }

    private enum class State { HOLD, FADE_OUT, SWITCH, FADE_IN, WARP_IN, FINISH }

    companion object {
        private val JITTER_COLOR = Color(150, 90, 255, 200)

        /** Starts a full transition (hold → fades → switch → warp-in). */
        fun start(fleet: CampaignFleetAPI, leg: Leg, target: SectorEntityToken, ability: SlipspaceJump, arrivalFacing: Float?): SlipspaceTransitionScript {
            val script = SlipspaceTransitionScript(fleet, leg, target, ability, arrivalFacing, State.HOLD)
            script.begin()
            Global.getSector().addTransientScript(script)
            return script
        }

        /** After a load mid-transition: no fades, switch on the next engine frame and finish. */
        fun resumeAtSwitch(fleet: CampaignFleetAPI, leg: Leg, target: SectorEntityToken, ability: SlipspaceJump, arrivalFacing: Float?): SlipspaceTransitionScript {
            val script = SlipspaceTransitionScript(fleet, leg, target, ability, arrivalFacing, State.SWITCH)
            script.begin()
            Global.getSector().addTransientScript(script)
            return script
        }

        fun setViewsAlpha(fleet: CampaignFleetAPI, alpha: Float) {
            for (view in fleet.views) (view as? CampaignFleetMemberView)?.setExtraAlphaMult(alpha)
        }

        fun clearTransitionFlags(fleet: CampaignFleetAPI) {
            val obf = fleet as? CampaignFleet ?: return
            obf.setInJumpTransition(false)
            obf.jumpDestination = null
        }

        /** The campaign noise overlay the engine plays on its own transitions; obf-only, so optional. */
        private fun showNoise() {
            try {
                val ui = Global.getSector().campaignUI
                val method = ui.javaClass.getMethod(
                    "showNoise", java.lang.Float.TYPE, java.lang.Float.TYPE, java.lang.Float.TYPE
                )
                method.invoke(ui, 0.5f, 0.25f, 1.5f)
            } catch (_: Throwable) {
                // no noise, no harm
            }
        }
    }

    private var state = startState
    private val fader = FaderUtil(0f, SlipspaceJumpRules.fadeSeconds.value, SlipspaceJumpRules.fadeSeconds.value)
    private val isPlayer = fleet.isPlayerFleet
    private var hold = 0f
    private var warpIn = 0f
    private var jittered = false
    private var done = false

    private fun begin() {
        val obf = fleet as? CampaignFleet
        obf?.setInJumpTransition(true)
        obf?.jumpDestination = JumpDestination(target, null)
        fleet.fadeOutIndicator()
        setViewsAlpha(fleet, 0f)
    }

    override fun isDone(): Boolean = done

    override fun runWhilePaused(): Boolean = false

    override fun advance(amount: Float) {
        if (done) return
        fleet.setNoEngaging(30f)
        fleet.setMoveDestinationOverride(fleet.location.x, fleet.location.y)
        fleet.setVelocity(0f, 0f)
        if (isPlayer) Global.getSector().campaignUI.setDisallowPlayerInteractionsForOneFrame()

        when (state) {
            State.HOLD -> {
                hold += amount
                val holdFor = if (leg == Leg.OUT) SlipspaceJumpRules.warpOutHoldSeconds.value else 0f
                if (hold >= holdFor) {
                    if (isPlayer) {
                        state = State.FADE_OUT
                        fader.fadeIn()
                        showNoise()
                    } else {
                        state = State.SWITCH
                    }
                }
            }

            State.FADE_OUT -> {
                fader.advance(amount)
                Global.getSector().viewport.alphaMult = 1f - fader.brightness
                if (fader.isFadedIn) state = State.SWITCH
            }

            State.SWITCH -> {
                switchLocations()
                ability.onTransitionSwitched(leg)
                if (isPlayer) {
                    state = State.FADE_IN
                    fader.fadeOut()
                } else {
                    state = State.WARP_IN
                }
            }

            State.FADE_IN -> {
                fader.advance(amount)
                Global.getSector().viewport.alphaMult = 1f - fader.brightness
                if (fader.isFadedOut) state = State.WARP_IN
            }

            State.WARP_IN -> {
                warpIn += amount
                val holdFor = if (leg == Leg.BACK) SlipspaceJumpRules.warpInHoldSeconds.value else 0f
                val t = max(0f, min(1f, (warpIn - holdFor) / SlipspaceJumpRules.warpInSeconds.value))
                if (t > 0f && !jittered) {
                    jittered = true
                    for (view in fleet.views) view.setJitter(0f, SlipspaceJumpRules.warpInSeconds.value, JITTER_COLOR, 7, 15f)
                }
                setViewsAlpha(fleet, t)
                if (t >= 1f) state = State.FINISH
            }

            State.FINISH -> {
                setViewsAlpha(fleet, 1f)
                Global.getSector().viewport.alphaMult = 1f
                fleet.setNoEngaging(1f)
                fleet.setMoveDestinationOverride(fleet.location.x, fleet.location.y)
                clearTransitionFlags(fleet)
                fleet.fadeInIndicator()
                done = true
                Global.getSector().reportFleetJumped(fleet, fleet, JumpDestination(target, null))
                ability.onTransitionFinished(leg)
            }
        }
    }

    private fun switchLocations() {
        val destination = target.containingLocation
        val from = fleet.containingLocation
        if (isPlayer) Global.getSector().currentLocation = destination
        if (from !== destination) {
            from?.removeEntity(fleet)
            destination.addEntity(fleet)
        }
        fleet.orbit = null
        fleet.setLocation(target.location.x, target.location.y)
        fleet.setMoveDestinationOverride(target.location.x, target.location.y)
        fleet.setVelocity(0f, 0f)
        if (arrivalFacing != null) fleet.facing = arrivalFacing
    }
}

package org.combustiblelemon.covenantremnants.campaign.abilities

import com.fs.starfarer.api.EveryFrameScript
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CampaignFleetAPI
import com.fs.starfarer.api.campaign.JumpPointAPI.JumpDestination
import com.fs.starfarer.api.campaign.SectorEntityToken
import com.fs.starfarer.api.util.FaderUtil
import com.fs.starfarer.api.util.Misc
import com.fs.starfarer.campaign.fleet.CampaignFleet
import com.fs.starfarer.campaign.fleet.CampaignFleetMemberView
import org.lwjgl.util.vector.Vector2f
import java.awt.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * The custom location switch of the campaign Slipspace Jump (developer ruling 2026-10-07: fully
 * custom, not the engine's `doHyperspaceTransition`). Runs as a transient sector-level script,
 * because switching a fleet's location from inside that fleet's own advance (where abilities run)
 * would mutate the location being iterated.
 *
 * States: HOLD (fleet already hidden inside the portal) → FADE_OUT (player only: screen to black)
 * → SWITCH (remove / add the fleet, current location, place at the arrival plan's start) →
 * FADE_IN → WARP_IN → FINISH. With an [ArrivalPlan] (the Ark look, developer ruling 2026-10-08)
 * WARP_IN waits `ARK_ARRIVAL_DELAY`, fires the ability's arrival burst, then slides the fleet out
 * of the tube from the plan's start to its end, the ships showing as they pass the mouth; without
 * one the member views fade back in in place with jitter.
 * Mirrors the engine's own transition where it matters (`setInJumpTransition`, `setNoEngaging`,
 * `reportFleetJumped`); the obf `CampaignFleet` / `CampaignFleetMemberView` are `DoNotObfuscate`.
 */
class SlipspaceTransitionScript private constructor(
    private val fleet: CampaignFleetAPI,
    val leg: Leg,
    private val target: SectorEntityToken,
    private val ability: SlipspaceJump,
    private val plan: ArrivalPlan?,
    startState: State
) : EveryFrameScript {

    enum class Leg { OUT, BACK }

    /**
     * How the fleet arrives: placed at [from] (inside the tube) facing [facing], sliding to [to]
     * over [seconds]; [mouth] is where the ships start to show.
     */
    class ArrivalPlan(val from: Vector2f, val to: Vector2f, val mouth: Vector2f, val facing: Float, val seconds: Float)

    private enum class State { HOLD, FADE_OUT, SWITCH, FADE_IN, WARP_IN, FINISH }

    companion object {
        private val JITTER_COLOR = Color(150, 90, 255, 200)

        /** Starts a full transition (hold → fades → switch → arrival). */
        fun start(fleet: CampaignFleetAPI, leg: Leg, target: SectorEntityToken, ability: SlipspaceJump, plan: ArrivalPlan?): SlipspaceTransitionScript {
            val script = SlipspaceTransitionScript(fleet, leg, target, ability, plan, State.HOLD)
            script.begin()
            Global.getSector().addTransientScript(script)
            return script
        }

        /** After a load mid-transition: no fades, switch on the next engine frame and arrive. */
        fun resumeAtSwitch(fleet: CampaignFleetAPI, leg: Leg, target: SectorEntityToken, ability: SlipspaceJump, plan: ArrivalPlan?): SlipspaceTransitionScript {
            val script = SlipspaceTransitionScript(fleet, leg, target, ability, plan, State.SWITCH)
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
    private var burstFired = false
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
        if (state != State.WARP_IN || plan == null) fleet.setMoveDestinationOverride(fleet.location.x, fleet.location.y)
        fleet.setVelocity(0f, 0f)
        if (isPlayer) Global.getSector().campaignUI.setDisallowPlayerInteractionsForOneFrame()

        when (state) {
            State.HOLD -> {
                hold += amount
                val holdFor = if (leg == Leg.OUT) SlipspaceJumpRules.warpOutHoldSeconds.value else SlipspaceJumpRules.arkExitHold.value
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
                val musicToken = if (isPlayer && leg == Leg.BACK) musicStateToken() else null
                switchLocations()
                // The engine restarts the music only when the music state token changes. This jump
                // goes system to system with no hyperspace in between, so two systems with different
                // music (a core one and a non-core one) can share CAMPAIGN_SYSTEM; the previous
                // system's track then kept playing on arrival (2026-10-11). Same token: restart, which
                // makes the engine pick the track for the new system, as arriving from hyperspace does.
                if (musicToken != null && musicStateToken() === musicToken) {
                    Global.getSoundPlayer().restartCurrentMusic()
                }
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
                if (plan != null) slideOut(plan) else fadeInPlace()
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

    /** Legacy arrival (no plan): the member views fade back in in place with jitter. */
    private fun fadeInPlace() {
        val holdFor = if (leg == Leg.BACK) SlipspaceJumpRules.warpInHoldSeconds.value else 0f
        val t = max(0f, min(1f, (warpIn - holdFor) / SlipspaceJumpRules.warpInSeconds.value))
        if (t > 0f && !jittered) {
            jittered = true
            for (view in fleet.views) view.setJitter(0f, SlipspaceJumpRules.warpInSeconds.value, JITTER_COLOR, 7, 15f)
        }
        setViewsAlpha(fleet, t)
        if (t >= 1f) state = State.FINISH
    }

    /**
     * Ark arrival: the burst fires after the delay (its pre-roll, `ARK_PREROLL`, charges up),
     * then the fleet slides out of the tube from the opening.
     */
    private fun slideOut(plan: ArrivalPlan) {
        val delay = SlipspaceJumpRules.arkArrivalDelay.value
        if (warpIn < delay) {
            fleet.setLocation(plan.from.x, plan.from.y)
            fleet.setMoveDestinationOverride(plan.from.x, plan.from.y)
            setViewsAlpha(fleet, 0f)
            return
        }
        if (!burstFired) {
            burstFired = true
            ability.onArrivalBurst(leg, plan.mouth)
        }
        val u = ((warpIn - delay - SlipspaceJumpRules.arkPreroll.value) / plan.seconds).coerceIn(0f, 1f)
        val ease = max(SlipspaceJumpRules.arkSlideEase.value, 1f)
        val p = 1f - (1f - u).pow(ease)
        val x = plan.from.x + (plan.to.x - plan.from.x) * p
        val y = plan.from.y + (plan.to.y - plan.from.y) * p
        fleet.setLocation(x, y)
        fleet.setMoveDestinationOverride(x, y)
        fleet.facing = plan.facing

        // Ships show as the fleet's centre passes the mouth.
        val unit = Misc.getUnitVectorAtDegreeAngle(plan.facing)
        val past = (x - plan.mouth.x) * unit.x + (y - plan.mouth.y) * unit.y
        val r = max(fleet.radius, 10f)
        val alpha = ((past + SlipspaceJumpRules.arkRevealBehind.value * r) / max(SlipspaceJumpRules.arkRevealSpan.value * r, 1f)).coerceIn(0f, 1f)
        if (alpha > 0f && !jittered) {
            jittered = true
            if (SlipspaceJumpRules.arkSlideJitter.isOn) {
                for (view in fleet.views) view.setJitter(0f, plan.seconds * 0.5f, JITTER_COLOR, 7, 15f)
            }
        }
        setViewsAlpha(fleet, alpha)
        if (u >= 1f) state = State.FINISH
    }

    /** The music plugin's state for the player's current location (identity-comparable). */
    private fun musicStateToken(): Any? = try {
        Misc.getMusicPlayerPlugin()?.stateTokenForCampaignLocation
    } catch (_: Throwable) {
        null
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
        val at = plan?.from ?: target.location
        fleet.setLocation(at.x, at.y)
        fleet.setMoveDestinationOverride(at.x, at.y)
        fleet.setVelocity(0f, 0f)
        if (plan != null) fleet.facing = plan.facing
    }
}

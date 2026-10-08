package org.combustiblelemon.covenantremnants.campaign.abilities

import com.fs.starfarer.api.EveryFrameScript
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.BattleAPI
import com.fs.starfarer.api.campaign.CampaignEngineLayers
import com.fs.starfarer.api.campaign.CampaignFleetAPI
import com.fs.starfarer.api.campaign.LocationAPI
import com.fs.starfarer.api.campaign.SectorEntityToken
import com.fs.starfarer.api.campaign.StarSystemAPI
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.combat.ViewportAPI
import com.fs.starfarer.api.impl.campaign.abilities.BaseDurationAbility
import com.fs.starfarer.api.impl.campaign.ids.Abilities
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin
import com.fs.starfarer.api.impl.campaign.tutorial.TutorialMissionIntel
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.Misc
import org.combustiblelemon.covenantremnants.campaign.abilities.SlipspaceTransitionScript.Leg
import org.combustiblelemon.covenantremnants.campaign.systems.SLIPSPACE_POCKET_TAG
import org.combustiblelemon.covenantremnants.campaign.systems.SlipspacePaths
import org.combustiblelemon.covenantremnants.campaign.systems.ensureSlipspaceSystem
import org.combustiblelemon.covenantremnants.campaign.systems.prepareSlipspaceRoute
import org.combustiblelemon.covenantremnants.campaign.systems.slipspaceEntrance
import org.combustiblelemon.covenantremnants.campaign.systems.slipspaceRouteDestination
import org.combustiblelemon.covenantremnants.campaign.systems.slipspaceRouteOrigin
import org.combustiblelemon.covenantremnants.utils.extensions.isInSlipspace
import org.combustiblelemon.covenantremnants.utils.render.rupture.RuptureEffect
import org.lwjgl.util.vector.Vector2f
import java.awt.Color
import java.util.EnumSet
import kotlin.math.max
import kotlin.math.min

/**
 * Campaign Slipspace Jump (developer rulings 2026-10-07, see
 * planning/implementations/campaign-slipspace-jump.md).
 *
 * Press → sector map picker ([SlipspaceDestinationDialog]) → CHARGING (the ability's level ramp:
 * controls locked, fleet braked, Reach rupture charging ahead of the fleet) → LURCH (rupture
 * opens, the fleet slides into it) → COMMIT (fuel + CR, point of no return) → TRANSITION_OUT
 * ([SlipspaceTransitionScript]: collapse, fade, switch into the pocket) → IN_POCKET (the path's
 * current carries the fleet to the exit) → EXITING (exit dialog → lurch into the exit) →
 * TRANSITION_BACK (arrive at the destination's edge facing the origin under the exit flash) →
 * IDLE with the cooldown started at landing.
 *
 * Save model: XStream bypasses constructors and old saves predate these fields, so every saved
 * field is nullable or zero-safe, transients are lazily rebuilt, and the prototype's fields are
 * kept (removing them would break saves written with the ability unlocked at start).
 */
class SlipspaceJump : BaseDurationAbility() {

    enum class Phase { IDLE, PICKING, CHARGING, LURCH, TRANSITION_OUT, IN_POCKET, EXITING, TRANSITION_BACK }

    companion object {
        private val LAYERS: EnumSet<CampaignEngineLayers> = EnumSet.of(CampaignEngineLayers.ABOVE)
        private const val TOOLTIP_MAX_SHIPS = 4

        /** Movement abilities held off while the jump owns the fleet. Nothing else is touched (transponder etc.). */
        private val BLOCKED_ABILITIES = listOf(Abilities.SUSTAINED_BURN, Abilities.EMERGENCY_BURN, Abilities.TRANSVERSE_JUMP)
    }

    // --- Legacy prototype fields: kept for save compatibility, unused -------------------------
    @Suppress("unused", "PropertyName")
    var CR_COST_MULT = 0.1f
    @Suppress("unused", "PropertyName")
    var FUEL_USE_MULT = 1f
    @Suppress("unused")
    var primed: Boolean? = null
    @Suppress("unused")
    var ping: EveryFrameScript? = null

    // --- Saved state ----------------------------------------------------------------------------
    var phase: Phase? = null
    var originId: String? = null
    var destId: String? = null
    /** Charge-time multiplier frozen at activation (S-mod Tuning); ≤ 0 means 1 (old saves). */
    var chargeMult = 0f
    /** Total of the running cooldown, days (cooldown × Tuning), so the bar fraction is right. */
    var cooldownTotalDays = 0f
    var portalX = 0f
    var portalY = 0f
    var lurchFromX = 0f
    var lurchFromY = 0f
    var lurchElapsed = 0f
    var wasPausedBeforeDialog = false

    // --- Transient --------------------------------------------------------------------------------
    @Transient
    private var visuals: SlipspaceJumpVisuals? = null
    @Transient
    private var entryEffect: RuptureEffect? = null
    @Transient
    private var exitEffect: RuptureEffect? = null
    @Transient
    private var chargeComplete = false
    @Transient
    private var resumed = false
    @Transient
    private var pickingFramesWithoutDialog = 0

    private val currentPhase: Phase get() = phase ?: Phase.IDLE

    private fun vis(): SlipspaceJumpVisuals = visuals ?: SlipspaceJumpVisuals().also { visuals = it }

    private fun chargeMultSafe(): Float = if (chargeMult <= 0f) 1f else chargeMult

    // =============================================================================================
    // BaseDurationAbility hooks
    // =============================================================================================

    override fun getActivationDays(): Float = spec.activationDays * chargeMultSafe() * SlipspaceJumpRules.chargeTimeMult.value

    override fun getTotalDurationDays(): Float = getActivationDays() + spec.durationDays + spec.deactivationDays

    override fun getCooldownDays(): Float = if (cooldownTotalDays > 0f) cooldownTotalDays else spec.deactivationCooldown

    override fun isUsable(): Boolean {
        return when (currentPhase) {
            Phase.IDLE -> {
                val fleet = fleet ?: return false
                super.isUsable() &&
                    !TutorialMissionIntel.isTutorialInProgress() &&
                    SlipspaceJumpRules.blockReason(fleet) == null
            }
            // The button is the cancel while charging / lurching.
            Phase.CHARGING, Phase.LURCH -> true
            else -> false
        }
    }

    override fun pressButton() {
        when (currentPhase) {
            Phase.IDLE -> if (isUsable()) beginPicking()
            Phase.CHARGING, Phase.LURCH -> cancel()
            else -> {}
        }
    }

    override fun showActiveIndicator(): Boolean = when (currentPhase) {
        Phase.LURCH, Phase.TRANSITION_OUT, Phase.EXITING, Phase.TRANSITION_BACK -> true
        else -> false
    }

    override fun getActivationText(): String? = null

    override fun activateImpl() {
        val fleet = fleet ?: return
        if (phase != Phase.CHARGING) return
        chargeComplete = false

        // Portal ahead along the motion (the facing if still), far enough out that the braking
        // fleet does not drift into it.
        val speed = fleet.velocity.length()
        val dir = if (speed > 1f) {
            Misc.getUnitVectorAtDegreeAngle(Misc.getAngleInDegrees(fleet.velocity))
        } else {
            Misc.getUnitVectorAtDegreeAngle(fleet.facing)
        }
        val drift = speed * SlipspaceJumpRules.brakeSeconds.value * 0.5f
        val ahead = max(SlipspaceJumpRules.portalAheadRadii.value * fleet.radius, drift + fleet.radius)
        portalX = fleet.location.x + dir.x * ahead
        portalY = fleet.location.y + dir.y * ahead

        val effect = RuptureEffect.entry(Vector2f(portalX, portalY), SlipspaceJumpRules.ruptureRadius(fleet))
        entryEffect = vis().add(effect, fleet.containingLocation)
        Global.getSector().campaignUI.clearLaidInCourse()
    }

    override fun applyEffect(amount: Float, level: Float) {
        val fleet = fleet ?: return
        if (phase != Phase.CHARGING || !turnedOn) return
        lockControls(fleet)
        brake(fleet, amount)
        turnToward(fleet, portalX, portalY, amount)
        if (level >= 1f) chargeComplete = true
        entryEffect?.let {
            it.chargeLevel = level
            if (level > 0f) vis().playChargeHum(it, level)
        }
    }

    override fun deactivateImpl() {
        if (phase != Phase.CHARGING) return
        if (chargeComplete) {
            // Natural end of the charge: the cooldown starts at landing, not here.
            cooldownLeft = 0f
            beginLurch()
        } else {
            // Interrupted from outside (incompatible ability, engine): a free cancel.
            cancel()
        }
    }

    override fun cleanupImpl() {
        visuals?.abortAll()
    }

    override fun advance(amount: Float) {
        super.advance(amount)
        val fleet = fleet ?: return
        if (!resumed) {
            resumed = true
            resumeAfterLoad(fleet)
        }
        visuals?.advance(amount)
        reconcile(fleet)
        when (currentPhase) {
            Phase.LURCH -> advanceLurch(fleet, amount)
            Phase.EXITING -> advanceExiting(fleet, amount)
            Phase.TRANSITION_OUT, Phase.TRANSITION_BACK -> lockControls(fleet)
            else -> {}
        }
    }

    override fun fleetJoinedBattle(battle: BattleAPI?) {
        if (currentPhase == Phase.CHARGING || currentPhase == Phase.LURCH) cancel()
    }

    override fun fleetLeftBattle(battle: BattleAPI?, engagedInHostilities: Boolean) {
        if (engagedInHostilities && (currentPhase == Phase.CHARGING || currentPhase == Phase.LURCH)) cancel()
    }

    override fun fleetOpenedMarket(market: MarketAPI?) {
        if (currentPhase == Phase.CHARGING || currentPhase == Phase.LURCH) cancel()
    }

    /**
     * Keeps the phase honest against where the fleet actually is: a console teleport, a respawn or
     * `RegenerateSlipspace` can move the fleet out of the pocket (IN_POCKET → IDLE), a prototype
     * save can have it inside with no phase (IDLE → IN_POCKET), and a dialog closed from outside
     * can leave PICKING hanging.
     */
    private fun reconcile(fleet: CampaignFleetAPI) {
        when (currentPhase) {
            Phase.IN_POCKET -> if (!fleet.isInSlipspace) {
                phase = Phase.IDLE
                originId = null
                destId = null
            }
            Phase.IDLE -> if (fleet.isInSlipspace) phase = Phase.IN_POCKET
            Phase.PICKING -> {
                if (Global.getSector().campaignUI.isShowingDialog) {
                    pickingFramesWithoutDialog = 0
                } else if (++pickingFramesWithoutDialog > 3) {
                    pickingFramesWithoutDialog = 0
                    phase = Phase.IDLE
                }
            }
            else -> {}
        }
    }

    // =============================================================================================
    // Picking
    // =============================================================================================

    private fun beginPicking() {
        val fleet = fleet ?: return
        val ui = Global.getSector().campaignUI
        if (ui.isShowingDialog) return
        wasPausedBeforeDialog = Global.getSector().isPaused
        phase = Phase.PICKING
        val shown = ui.showInteractionDialog(SlipspaceDestinationDialog(this), fleet)
        if (!shown) phase = Phase.IDLE
    }

    /** From the destination dialog: a system was picked; the charge starts. */
    fun onDestinationPicked(dest: StarSystemAPI) {
        val fleet = fleet
        val origin = fleet?.containingLocation as? StarSystemAPI
        if (fleet == null || origin == null || phase != Phase.PICKING) {
            phase = Phase.IDLE
            return
        }
        originId = origin.id
        destId = dest.id
        chargeMult = SlipspaceJumpRules.chargeMult(fleet)
        phase = Phase.CHARGING
        activate()
        if (!turnedOn) {
            phase = Phase.IDLE
            originId = null
            destId = null
        }
    }

    /** From the destination dialog: cancelled; nothing was charged. */
    fun onPickingCancelled() {
        if (phase == Phase.PICKING) phase = Phase.IDLE
    }

    // =============================================================================================
    // Charge → lurch → commit
    // =============================================================================================

    /** Free cancel while charging or lurching (button press, battle, market, interrupt). */
    fun cancel() {
        val was = currentPhase
        if (was != Phase.PICKING && was != Phase.CHARGING && was != Phase.LURCH) return
        phase = Phase.IDLE
        originId = null
        destId = null
        entryEffect?.abort()
        entryEffect = null
        if (turnedOn) deactivate()
        cooldownLeft = 0f
    }

    private fun beginLurch() {
        val fleet = fleet ?: return
        phase = Phase.LURCH
        lurchFromX = fleet.location.x
        lurchFromY = fleet.location.y
        lurchElapsed = 0f
        entryEffect?.startFade()
        vis().playOneShot(SlipspaceJumpRules.SOUND_OPEN, Vector2f(portalX, portalY))
    }

    private fun advanceLurch(fleet: CampaignFleetAPI, amount: Float) {
        lockControls(fleet)
        lurchElapsed += amount
        val t = min(1f, lurchElapsed / SlipspaceJumpRules.lurchSeconds.value)
        moveAlongLurch(fleet, t, portalX, portalY)
        if (t >= 1f) commit(fleet)
    }

    private fun commit(fleet: CampaignFleetAPI) {
        val sector = Global.getSector()
        val origin = originId?.let { sector.getStarSystem(it) }
        val dest = destId?.let { sector.getStarSystem(it) }
        if (origin == null || dest == null) {
            cancel()
            return
        }
        val pocket = ensureSlipspaceSystem(sector)
        val entrance = slipspaceEntrance(pocket)
        if (entrance == null) {
            cancel()
            return
        }
        val fuel = SlipspaceJumpRules.fuelCost(fleet, origin, dest)
        if (fuel > fleet.cargo.fuel) {
            fleet.addFloatingText("Not enough fuel for the jump", Misc.getNegativeHighlightColor(), 1f)
            cancel()
            return
        }
        // Point of no return: costs, then the route.
        fleet.cargo.removeFuel(fuel.toFloat())
        SlipspaceJumpRules.applyCrHit(fleet)
        prepareSlipspaceRoute(pocket, origin.id, dest.id, SlipspaceJumpRules.transitBurnMult(fleet))
        val target = pocket.createToken(entrance.location.x, entrance.location.y)

        entryEffect?.startCollapse()
        vis().playOneShot(SlipspaceJumpRules.SOUND_COLLAPSE, Vector2f(portalX, portalY))
        phase = Phase.TRANSITION_OUT
        SlipspaceTransitionScript.start(fleet, Leg.OUT, target, this, pathStartFacing(origin.id, dest.id))
    }

    /** Heading of the pocket path at its start, so the fleet arrives pointed down the corridor. */
    private fun pathStartFacing(originId: String, destId: String): Float? {
        val points = SlipspacePaths.buildPolyline(SlipspacePaths.recordFor(originId, destId))
        if (points.size < 2) return null
        return Misc.getAngleInDegrees(points[0], points[1])
    }

    // =============================================================================================
    // Pocket → exit
    // =============================================================================================

    /** The exit portal may be used. */
    fun canExit(): Boolean = currentPhase == Phase.IN_POCKET && fleet?.isInHyperspaceTransition == false

    /** From the exit dialog: lurch into the exit, then the transition home. */
    fun beginExit(portal: SectorEntityToken) {
        val fleet = fleet ?: return
        if (!canExit()) return
        phase = Phase.EXITING
        lurchFromX = fleet.location.x
        lurchFromY = fleet.location.y
        portalX = portal.location.x
        portalY = portal.location.y
        lurchElapsed = 0f
        vis().playOneShot(SlipspaceJumpRules.SOUND_OPEN, Vector2f(portalX, portalY))
    }

    private fun advanceExiting(fleet: CampaignFleetAPI, amount: Float) {
        lockControls(fleet)
        lurchElapsed += amount
        val t = min(1f, lurchElapsed / SlipspaceJumpRules.exitLurchSeconds.value)
        moveAlongLurch(fleet, t, portalX, portalY)
        SlipspaceTransitionScript.setViewsAlpha(fleet, 1f - t)
        if (t >= 1f) commitExit(fleet)
    }

    private fun commitExit(fleet: CampaignFleetAPI) {
        val sector = Global.getSector()
        val pocket = fleet.containingLocation as? StarSystemAPI
        val routeDest = pocket?.let { slipspaceRouteDestination(it) }
        val routeOrigin = pocket?.let { slipspaceRouteOrigin(it) }
        val dest = routeDest ?: destId?.let { sector.getStarSystem(it) } ?: routeOrigin
            ?: originId?.let { sector.getStarSystem(it) }
            ?: sector.starSystems.firstOrNull { !it.hasTag(SLIPSPACE_POCKET_TAG) }
        if (dest == null) {
            // Nowhere to go: stay in the pocket.
            SlipspaceTransitionScript.setViewsAlpha(fleet, 1f)
            phase = Phase.IN_POCKET
            return
        }
        val origin = routeOrigin ?: originId?.let { sector.getStarSystem(it) } ?: dest
        val point = SlipspaceJumpRules.arrivalPoint(origin, dest)
        val target = dest.createToken(point.x, point.y)

        val effect = RuptureEffect.exit(Vector2f(point), SlipspaceJumpRules.ruptureRadius(fleet))
        effect.chargeLevel = 1f
        exitEffect = vis().add(effect, dest)

        // Face the system centre on arrival.
        val center = dest.center?.location
        val facing = if (center != null) Misc.getAngleInDegrees(point, center) else null

        phase = Phase.TRANSITION_BACK
        SlipspaceTransitionScript.start(fleet, Leg.BACK, target, this, facing)
    }

    // =============================================================================================
    // Transition callbacks
    // =============================================================================================

    fun onTransitionSwitched(leg: Leg) {
        if (leg == Leg.BACK) {
            exitEffect?.let {
                it.startDissolve()
                vis().playOneShot(SlipspaceJumpRules.SOUND_ARRIVAL, it.center)
            }
        }
    }

    fun onTransitionFinished(leg: Leg) {
        when (leg) {
            Leg.OUT -> {
                phase = Phase.IN_POCKET
                entryEffect = null
            }
            Leg.BACK -> finishJump()
        }
    }

    private fun finishJump() {
        phase = Phase.IDLE
        exitEffect = null
        originId = null
        destId = null
        val fleet = fleet
        val mult = if (fleet != null) SlipspaceJumpRules.cooldownMult(fleet) else 1f
        cooldownTotalDays = spec.deactivationCooldown * mult * SlipspaceJumpRules.cooldownMult.value
        cooldownLeft = cooldownTotalDays
    }

    // =============================================================================================
    // Resume after a load mid-flight
    // =============================================================================================

    private fun resumeAfterLoad(fleet: CampaignFleetAPI) {
        when (currentPhase) {
            Phase.IDLE, Phase.IN_POCKET -> {}
            Phase.PICKING, Phase.CHARGING, Phase.LURCH -> {
                // The charge visuals are gone; a free cancel.
                phase = Phase.CHARGING
                cancel()
            }
            Phase.TRANSITION_OUT -> {
                val sector = Global.getSector()
                val pocket = ensureSlipspaceSystem(sector)
                val entrance = slipspaceEntrance(pocket)
                if (fleet.containingLocation === pocket) {
                    restore(fleet)
                    phase = Phase.IN_POCKET
                } else if (entrance == null) {
                    restore(fleet)
                    phase = Phase.IDLE
                    originId = null
                    destId = null
                } else {
                    val target = pocket.createToken(entrance.location.x, entrance.location.y)
                    val o = originId
                    val d = destId
                    val facing = if (o != null && d != null) pathStartFacing(o, d) else null
                    SlipspaceTransitionScript.resumeAtSwitch(fleet, Leg.OUT, target, this, facing)
                }
            }
            Phase.EXITING -> {
                restore(fleet)
                phase = Phase.IN_POCKET
            }
            Phase.TRANSITION_BACK -> {
                val pocket = fleet.containingLocation as? StarSystemAPI
                if (pocket != null && pocket.hasTag(SLIPSPACE_POCKET_TAG)) {
                    val sector = Global.getSector()
                    val dest = slipspaceRouteDestination(pocket) ?: destId?.let { sector.getStarSystem(it) }
                    if (dest == null) {
                        restore(fleet)
                        phase = Phase.IN_POCKET
                    } else {
                        val origin = slipspaceRouteOrigin(pocket) ?: originId?.let { sector.getStarSystem(it) } ?: dest
                        val point = SlipspaceJumpRules.arrivalPoint(origin, dest)
                        val target = dest.createToken(point.x, point.y)
                        SlipspaceTransitionScript.resumeAtSwitch(fleet, Leg.BACK, target, this, null)
                    }
                } else {
                    restore(fleet)
                    finishJump()
                }
            }
        }
    }

    private fun restore(fleet: CampaignFleetAPI) {
        SlipspaceTransitionScript.setViewsAlpha(fleet, 1f)
        SlipspaceTransitionScript.clearTransitionFlags(fleet)
        Global.getSector().viewport.alphaMult = 1f
        fleet.fadeInIndicator()
    }

    // =============================================================================================
    // Helpers
    // =============================================================================================

    /** Takes the controls for this frame: no clicks, no course, no other abilities. */
    private fun lockControls(fleet: CampaignFleetAPI) {
        Global.getSector().campaignUI.setDisallowPlayerInteractionsForOneFrame()
        fleet.setMoveDestinationOverride(fleet.location.x, fleet.location.y)
        fleet.interactionTarget = null
        for (id in BLOCKED_ABILITIES) {
            val other = fleet.getAbility(id) ?: continue
            if (other.isActive) other.deactivate()
            other.forceDisable()
        }
    }

    /** Bleeds the fleet's velocity off over [SlipspaceJumpRules.brakeSeconds.value]. */
    private fun brake(fleet: CampaignFleetAPI, amount: Float) {
        if (amount <= 0f) return
        val v = fleet.velocity
        val speed = v.length()
        if (speed <= 0.01f) {
            fleet.setVelocity(0f, 0f)
            return
        }
        val decel = max(speed, 200f) / SlipspaceJumpRules.brakeSeconds.value + fleet.acceleration
        var ds = decel * amount
        if (ds > speed) ds = speed
        fleet.setVelocity(v.x - v.x / speed * ds, v.y - v.y / speed * ds)
    }

    /** Turns the fleet toward ([toX], [toY]) at [SlipspaceJumpRules.turnRate] degrees per second. */
    private fun turnToward(fleet: CampaignFleetAPI, toX: Float, toY: Float, amount: Float) {
        if (amount <= 0f) return
        val dx = toX - fleet.location.x
        val dy = toY - fleet.location.y
        if (dx * dx + dy * dy < 1f) return
        val target = Misc.getAngleInDegrees(fleet.location, Vector2f(toX, toY))
        var diff = ((target - fleet.facing) % 360f + 540f) % 360f - 180f // shortest signed turn
        val step = SlipspaceJumpRules.turnRate.value * amount
        if (diff > step) diff = step
        if (diff < -step) diff = -step
        fleet.facing = ((fleet.facing + diff) % 360f + 360f) % 360f
    }

    /** Eased (t²) slide from the lurch start to ([toX], [toY]); the fleet is placed, not steered. */
    private fun moveAlongLurch(fleet: CampaignFleetAPI, t: Float, toX: Float, toY: Float) {
        val eased = t * t
        val x = lurchFromX + (toX - lurchFromX) * eased
        val y = lurchFromY + (toY - lurchFromY) * eased
        val dx = toX - lurchFromX
        val dy = toY - lurchFromY
        if (dx * dx + dy * dy > 1f) fleet.facing = Misc.getAngleInDegrees(Vector2f(lurchFromX, lurchFromY), Vector2f(toX, toY))
        fleet.setLocation(x, y)
        fleet.setVelocity(0f, 0f)
        fleet.setMoveDestinationOverride(x, y)
    }

    // =============================================================================================
    // Rendering
    // =============================================================================================

    override fun getActiveLayers(): EnumSet<CampaignEngineLayers> = LAYERS

    override fun render(layer: CampaignEngineLayers?, viewport: ViewportAPI?) {
        if (viewport == null || layer != CampaignEngineLayers.ABOVE) return
        val v = visuals ?: return
        val location: LocationAPI? = fleet?.containingLocation
        v.render(viewport, location)
    }

    // =============================================================================================
    // Tooltip
    // =============================================================================================

    override fun hasTooltip(): Boolean = true

    override fun createTooltip(tooltip: TooltipMakerAPI, expanded: Boolean) {
        val fleet = fleet ?: return
        val highlight = Misc.getHighlightColor()
        val bad = Misc.getNegativeHighlightColor()
        val pad = 10f

        if (!Global.CODEX_TOOLTIP_MODE) {
            tooltip.addTitle("Slipspace Jump")
        } else {
            tooltip.addSpacer(-10f)
        }

        tooltip.addPara(
            "Opens a slipspace rupture ahead of the fleet and carries it through a slipspace " +
                "corridor to another star system, emerging at that system's edge. The destination " +
                "is chosen on the sector map; the fleet is committed once it enters the rupture.",
            pad
        )

        tooltip.addPara(
            "Costs %s fuel per light-year and reduces the combat readiness of all ships by %s " +
                "of a combat deployment. The drive needs %s days to cool down after a jump.",
            pad, highlight,
            Misc.getRoundedValueMaxOneAfterDecimal(fleet.logistics.fuelCostPerLightYear * SlipspaceJumpRules.fuelPerLyMult.value),
            "" + Math.round(SlipspaceJumpRules.crCostMult.value * 100f) + "%",
            Misc.getRoundedValueMaxOneAfterDecimal(spec.deactivationCooldown)
        )

        tooltip.addPara(
            "Every cruiser and capital ship must carry a slipspace drive; smaller ships ride " +
                "along. A fleet of destroyers and frigates alone needs a drive on each of them.",
            pad
        )

        val tuned = SlipspaceJumpRules.tunedCount(fleet)
        val sModded = SlipspaceJumpRules.sModTunedCount(fleet)
        if (tuned > 0 || sModded > 0) {
            tooltip.addPara(
                "Slipspace Tuning: transit speed %s" + (if (sModded > 0) ", charge-up and cooldown %s." else "."),
                pad, highlight,
                "x" + Misc.getRoundedValueMaxOneAfterDecimal(SlipspaceJumpRules.transitBurnMult(fleet)),
                "x" + Misc.getRoundedValueMaxOneAfterDecimal(SlipspaceJumpRules.chargeMult(fleet))
            )
        }

        if (Global.CODEX_TOOLTIP_MODE) {
            tooltip.addPara("Ships with insufficient combat readiness may suffer damage when the jump is activated.", pad)
            return
        }

        when (currentPhase) {
            Phase.CHARGING, Phase.LURCH -> tooltip.addPara("Press again to abort the jump.", highlight, pad)
            Phase.IN_POCKET -> tooltip.addPara("In slipspace: follow the current to the exit.", highlight, pad)
            Phase.TRANSITION_OUT, Phase.TRANSITION_BACK, Phase.EXITING -> tooltip.addPara("Jumping...", highlight, pad)
            else -> {}
        }

        if (TutorialMissionIntel.isTutorialInProgress()) {
            tooltip.addPara("Can not be used right now.", bad, pad)
        }

        if (currentPhase == Phase.IDLE) {
            val reason = SlipspaceJumpRules.blockReason(fleet)
            if (reason != null) tooltip.addPara(reason, bad, pad)

            val missing = SlipspaceJumpRules.missingDriveShips(fleet)
            if (missing.isNotEmpty()) listShips(tooltip, "Ships without a slipspace drive:", missing)

            val nonReady = SlipspaceJumpRules.nonReadyShips(fleet)
            if (nonReady.isNotEmpty()) {
                tooltip.addPara(
                    "Some ships don't have enough combat readiness to safely initiate a slipspace " +
                        "jump and may suffer damage if the ability is activated:",
                    pad, bad, "may suffer damage"
                )
                listShips(tooltip, null, nonReady)
            }
        }

        addIncompatibleToTooltip(tooltip, expanded)
    }

    private fun listShips(tooltip: TooltipMakerAPI, header: String?, members: List<com.fs.starfarer.api.fleet.FleetMemberAPI>) {
        val pad = 10f
        if (header != null) tooltip.addPara(header, pad)
        var initPad = 5f
        for ((j, member) in members.withIndex()) {
            if (j >= TOOLTIP_MAX_SHIPS && members.size > TOOLTIP_MAX_SHIPS + 1) {
                tooltip.addPara(BaseIntelPlugin.INDENT + "... and " + (members.size - j) + " other ships", initPad)
                break
            }
            val str = if (!member.isFighterWing) {
                member.shipName + ", " + member.hullSpec.hullNameWithDashClass
            } else {
                member.variant.fullDesignationWithHullName
            }
            tooltip.addPara(BaseIntelPlugin.INDENT + str, initPad)
            initPad = 0f
        }
    }

    // --- Cooldown bar styling (alarm colour while blocked by low CR, like the transverse jump) ---

    private fun showAlarm(): Boolean {
        val fleet = fleet ?: return false
        if (currentPhase != Phase.IDLE) return false
        return SlipspaceJumpRules.nonReadyShips(fleet).isNotEmpty() && !isOnCooldown && isUsable
    }

    override fun getCooldownFraction(): Float = if (showAlarm()) 0f else super.getCooldownFraction()

    override fun isOnCooldown(): Boolean = super.getCooldownFraction() < 1f

    override fun getCooldownColor(): Color? {
        if (showAlarm()) {
            val color = Misc.getNegativeHighlightColor()
            return Misc.scaleAlpha(color, Global.getSector().campaignUI.sharedFader.brightness * 0.5f)
        }
        return super.getCooldownColor()
    }

    override fun isCooldownRenderingAdditive(): Boolean = showAlarm()
}

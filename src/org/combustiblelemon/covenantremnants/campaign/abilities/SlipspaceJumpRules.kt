package org.combustiblelemon.covenantremnants.campaign.abilities

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CampaignFleetAPI
import com.fs.starfarer.api.campaign.PersistentUIDataAPI.AbilitySlotAPI
import com.fs.starfarer.api.campaign.PlanetAPI
import com.fs.starfarer.api.campaign.SectorEntityToken
import com.fs.starfarer.api.campaign.StarSystemAPI
import com.fs.starfarer.api.combat.ShipAPI.HullSize
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.impl.campaign.abilities.EmergencyBurnAbility
import com.fs.starfarer.api.impl.campaign.ids.Stats
import com.fs.starfarer.api.impl.campaign.ids.Tags
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator
import com.fs.starfarer.api.util.Misc
import com.fs.starfarer.api.util.Misc.FleetMemberDamageLevel
import org.combustiblelemon.covenantremnants.campaign.systems.SLIPSPACE_POCKET_TAG
import org.combustiblelemon.covenantremnants.hullmods.SlipspaceDriveHullmod
import org.combustiblelemon.covenantremnants.hullmods.SlipspaceTuning
import org.combustiblelemon.covenantremnants.utils.render.rupture.ArkVariant
import org.lwjgl.util.vector.Vector2f
import java.awt.Color
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Rules and numbers of the campaign Slipspace Jump (gating, costs, destinations, Tuning bonus).
 * Every number is a live-tunable placeholder until the tuning pass (developer 2026-10-02: tuning
 * waits until all gameplay systems are done): `SlipspaceJumpTune` (console) lists / sets / resets
 * them, same as `FleetJumpTune` for the combat jump. Values reset on game restart.
 */
object SlipspaceJumpRules {
    const val ABILITY_ID = "covenant_remnants_slipspace_jump"

    /** The Covenant Slipspace Navigation System item: installs the navigation upgrade. */
    const val NAVIGATION_ITEM_ID = "covenant_remnants_slipspace_navigation"

    /** Character memory flag of the navigation upgrade (exact exit points, in-system jumps); never expires. */
    const val NAVIGATION_FLAG = "\$covenant_remnants_slipspace_navigation"

    const val POCKET_BACKGROUND = "graphics/covenant_remnants/backgrounds/slipspace_bg.jpg"

    /** One live-tunable constant (console `SlipspaceJumpTune`). */
    class Tunable internal constructor(val name: String, val default: Float, val description: String) {
        var value: Float = default

        val isOn: Boolean get() = value >= 0.5f

        fun reset() {
            value = default
        }
    }

    private val registry = LinkedHashMap<String, Tunable>()

    private fun tune(name: String, default: Float, description: String): Tunable =
        Tunable(name, default, description).also { registry[name] = it }

    /** An RGB colour as three tunables `NAME_R`, `NAME_G`, `NAME_B` (0..1 each). */
    class TunableRgb internal constructor(val r: Tunable, val g: Tunable, val b: Tunable) {
        /** Writes the colour × [scale] into [out] (first three floats) and returns it. */
        fun into(out: FloatArray, scale: Float = 1f): FloatArray {
            out[0] = r.value * scale
            out[1] = g.value * scale
            out[2] = b.value * scale
            return out
        }
    }

    private fun tuneRgb(name: String, r: Float, g: Float, b: Float, description: String): TunableRgb =
        TunableRgb(tune("${name}_R", r, "$description, red"), tune("${name}_G", g, "$description, green"), tune("${name}_B", b, "$description, blue"))

    val tunables: Collection<Tunable> get() = registry.values

    fun tunable(name: String): Tunable? = registry[name.uppercase()]

    fun resetAll() {
        registry.values.forEach { it.reset() }
    }

    // --- Dev overrides ----------------------------------------------------------------------------

    /** 1 = ignore the slipspace-drive hullmod rule (any fleet may jump). Testing only. */
    val noShipRestriction = tune("NO_SHIP_RESTRICTION", 0f, "1 = ignore the slipspace-drive hullmod rule (any fleet may jump)")

    /** 1 = no fuel and no CR cost. Testing only. */
    val free = tune("FREE", 0f, "1 = no fuel cost, no CR hit")

    // --- Costs (vanilla transverse-jump analogs) --------------------------------------------------

    /** Fuel per light-year of hyperspace distance, as a multiple of the fleet's fuel/LY. */
    val fuelPerLyMult = tune("FUEL_PER_LY_MULT", 1f, "Fuel per light-year, × the fleet's fuel/LY")

    /** CR lost by every ship, as a fraction of its deployment cost (FractureJump: 0.1). */
    val crCostMult = tune("CR_COST_MULT", 0.1f, "CR lost per ship, fraction of its deployment cost")

    /** Multiplier on the csv charge-up time (activationDays 0.4 d). */
    val chargeTimeMult = tune("CHARGE_TIME_MULT", 1f, "× the charge-up time (csv activationDays)")

    /** Multiplier on the csv cooldown (deactivationCooldown 0.5 d). */
    val cooldownMult = tune("COOLDOWN_MULT", 1f, "× the cooldown (csv deactivationCooldown)")

    // --- Timings (seconds, real time) -----------------------------------------------------------

    val brakeSeconds = tune("BRAKE", 0.5f, "Velocity bled off over this long at the start of the charge, s")
    val lurchSeconds = tune("LURCH", 2f, "Lurch from the hold point into the portal, s")
    val exitLurchSeconds = tune("EXIT_LURCH", 1f, "Lurch into the pocket exit, s")
    val warpOutHoldSeconds = tune("WARP_OUT_HOLD", 0.6f, "Hold after the fleet vanishes (collapse shows) before the screen fades, s")
    val warpInHoldSeconds = tune("WARP_IN_HOLD", 0.2f, "Hold at the destination before the exit flash reveals the fleet, s")
    val fadeSeconds = tune("FADE", 0.25f, "Screen fade in / out around the location switch, s")
    val warpInSeconds = tune("WARP_IN", 1.2f, "Fleet member views fade back in over this long after arriving, s")

    // --- Portal geometry --------------------------------------------------------------------------

    val portalAheadRadii = tune("PORTAL_AHEAD", 2f, "Portal centre ahead of the fleet, in fleet radii")
    val turnRate = tune("TURN_RATE", 120f, "Turn toward the portal while charging, degrees/s")
    val ruptureRadiusMult = tune("RUPTURE_RADIUS_MULT", 1.5f, "Rupture radius = fleet radius × this")
    val ruptureRadiusMin = tune("RUPTURE_RADIUS_MIN", 40f, "Rupture radius floor, su")

    // --- Pocket path ------------------------------------------------------------------------------

    val pathLengthMin = tune("PATH_LENGTH_MIN", 6000f, "Shortest pocket path, su (new pairs only)")
    val pathLengthMax = tune("PATH_LENGTH_MAX", 14000f, "Longest pocket path, su (new pairs only)")
    val pathPointSpacing = tune("PATH_SPACING", 100f, "Path point spacing, su")
    val pathWindMaxDegrees = tune("PATH_WIND", 75f, "Heading-wobble budget of the three sine waves, degrees (< 90)")

    val streamWidth = tune("STREAM_WIDTH", 768f, "Path width, su")
    val streamEdgeWidth = tune("STREAM_EDGE", 256f, "Path edge width, su")
    val streamBurnLevel = tune("STREAM_BURN", 30f, "Current strength, burn level")
    val streamAccelMult = tune("STREAM_ACCEL", 1f, "Current acceleration multiplier")
    val streamTaper = tune("STREAM_TAPER", 1500f, "Path narrows toward each end over this length, su")
    val streamEndWidthFrac = tune("STREAM_END_WIDTH", 0.3f, "Path width at the ends, fraction of STREAM_WIDTH")
    val STREAM_MIN_COLOR = Color(0.45f, 0.35f, 0.9f, 0.12f)
    val STREAM_MAX_COLOR = Color(0.55f, 0.65f, 1f, 0.35f)

    val pathPushAccel = tune("PATH_PUSH", 150f, "Push-back toward the path when outside it, su/s² per fleet radius of excess")
    val pathClampRadii = tune("PATH_CLAMP_RADII", 2f, "Hard clamp: at most this many fleet radii outside the path edge (2 = one fleet length)")

    // --- Arrival ----------------------------------------------------------------------------------

    val arrivalEdgePad = tune("ARRIVAL_PAD", 500f, "Arrival distance beyond the destination's outer radius, su")
    val arrivalFallbackRadius = tune("ARRIVAL_FALLBACK", 6000f, "Outer radius used when a system reports none, su")

    // --- Navigation upgrade: exact exit points and in-system jumps (rulings 2026-10-12) ----------

    val blinkFuelLy = tune("BLINK_FUEL_LY", 1f, "In-system jump fuel, in light-years of the fleet's fuel/LY")
    val blinkCrMult = tune("BLINK_CR_MULT", 1f, "In-system jump CR hit, × the jump's")
    val blinkCooldownMult = tune("BLINK_COOLDOWN_MULT", 1f, "In-system jump cooldown, × the jump's")
    val blinkMinDistance = tune("BLINK_MIN_DISTANCE", 2000f, "In-system jump: closest exit point to the fleet, su")
    val starExclusionMult = tune("STAR_EXCLUSION_MULT", 1.5f, "No exit points within this many radii of a star or black hole")

    // --- Slipspace Tuning campaign bonus ----------------------------------------------------------

    val tuningTransitPerShip = tune("TUNING_TRANSIT_PER_SHIP", 0.10f, "Transit speed bonus per ship with Tuning")
    val tuningTransitMaxMult = tune("TUNING_TRANSIT_MAX", 1.5f, "Transit speed multiplier cap")
    val tuningSModPerShip = tune("TUNING_SMOD_PER_SHIP", 0.10f, "Charge-up and cooldown cut per S-modded Tuning")
    val tuningSModMinMult = tune("TUNING_SMOD_MIN", 0.5f, "Charge-up and cooldown multiplier floor")

    // --- Ark portals (Halo 3 The Ark slipspace exit; utils/render/rupture/ArkTimeline) ------------
    // Sizes in Halo units × u, where u = R × ARK_MOUTH_RADII / (ARK_TUBE_SCALE1_CRUISER × mesh mouth
    // radius) and R = the fleet's Ark radius (bursts) or the portal entity's idle radius.

    val arkRadiusMult = tune("ARK_RADIUS_MULT", 1.5f, "Ark burst radius R = fleet radius × this")
    val arkRadiusMin = tune("ARK_RADIUS_MIN", 40f, "Ark burst radius floor, su")
    val arkPortalRadiusMult = tune("ARK_PORTAL_RADIUS_MULT", 2f, "Standing portal idle radius = entity radius × this")
    val arkMouthRadii = tune("ARK_MOUTH_RADII", 1f, "Full cruiser tube mouth radius, in R")
    val arkTubeLengthMult = tune("ARK_TUBE_LENGTH_MULT", 1.33f, "Tube length stretch along its axis (1.33 ≈ one fleet length at the end scale)")
    val arkViewTilt = tune("ARK_VIEW_TILT", 35f, "View tilt of the tube and rings, degrees (0 = edge-on)")
    val arkTubeFlip = tune("ARK_TUBE_FLIP", 0f, "1 = the tube extends ahead of the mouth instead of behind it")
    val arkTail = tune("ARK_TAIL", 0.2f, "Fade-out after a burst's life, s")

    val arkCruiserSeconds = tune("ARK_CRUISER_SECONDS", 1.5f, "Cruiser burst life, s")
    val arkCarrierSeconds = tune("ARK_CARRIER_SECONDS", 3.75f, "Carrier burst life, s")
    val arkLensSeconds = tune("ARK_LENS_SECONDS", 0.2f, "Carrier opening lens flare, s")
    val arkCloseAt = tune("ARK_CLOSE_AT", 3.65f, "Carrier snap-closed flash time, s")
    val arkCloseFlashSeconds = tune("ARK_CLOSE_FLASH_SECONDS", 0.15f, "Carrier close flash life, s")
    val arkCloseDistortionSeconds = tune("ARK_CLOSE_DISTORTION_SECONDS", 0.5f, "Carrier close distortion life, s")

    val arkTubeScale0Cruiser = tune("ARK_TUBE_SCALE0_CRUISER", 0.75f, "Cruiser tube scale at start")
    val arkTubeScale1Cruiser = tune("ARK_TUBE_SCALE1_CRUISER", 1.75f, "Cruiser tube scale at end")
    val arkTubeScale0Carrier = tune("ARK_TUBE_SCALE0_CARRIER", 2.75f, "Carrier tube scale at start")
    val arkTubeScale1Carrier = tune("ARK_TUBE_SCALE1_CARRIER", 1.75f, "Carrier tube scale at end")
    val arkTubeSpin0Cruiser = tune("ARK_TUBE_SPIN0_CRUISER", 0.15f, "Cruiser tube spin at start, turns/s")
    val arkTubeSpin1Cruiser = tune("ARK_TUBE_SPIN1_CRUISER", -0.2f, "Cruiser tube spin at end, turns/s")
    val arkTubeSpin0Carrier = tune("ARK_TUBE_SPIN0_CARRIER", 0.5f, "Carrier tube spin at start, turns/s")
    val arkTubeSpin1Carrier = tune("ARK_TUBE_SPIN1_CARRIER", -0.2f, "Carrier tube spin at end, turns/s")
    val arkTubeIntensity = tune("ARK_TUBE_INTENSITY", 5f, "Tube HDR intensity")
    val arkBlackPointEnd = tune("ARK_BLACK_POINT_END", 0.25f, "Tube alpha black point at the end of a burst")

    val arkRingSizeCruiser = tune("ARK_RING_SIZE_CRUISER", 0.3f, "Cruiser ring radius at start, Halo units")
    val arkRingSizeCarrier = tune("ARK_RING_SIZE_CARRIER", 1.25f, "Carrier ring radius at start, Halo units")
    val arkRingSizeEnd = tune("ARK_RING_SIZE_END", 0.005f, "Ring radius at the end, Halo units")
    val arkRingIntensityCruiser = tune("ARK_RING_INTENSITY_CRUISER", 1.5f, "Cruiser ring intensity")
    val arkRingIntensityCarrier = tune("ARK_RING_INTENSITY_CARRIER", 2f, "Carrier ring intensity")
    val arkLateX = tune("ARK_LATE_X", 0.1944f, "Halo transition easing handle x")
    val arkLateY = tune("ARK_LATE_Y", 0.8111f, "Halo transition easing handle y")
    val arkLateMirror = tune("ARK_LATE_MIRROR", 1f, "1 = the eased values hold then change late; 0 = change early")

    val arkDistScale0Cruiser = tune("ARK_DIST_SCALE0_CRUISER", 0.75f, "Cruiser distortion ring scale at start")
    val arkDistScale0Carrier = tune("ARK_DIST_SCALE0_CARRIER", 2f, "Carrier distortion ring scale at start")
    val arkDistScale1 = tune("ARK_DIST_SCALE1", 80f, "Distortion ring scale at the end (Halo: 80)")
    val arkDistortionMaxRadii = tune("ARK_DISTORTION_MAX_RADII", 6f, "Distortion ring radius at full scale, in R")
    val arkDistortionStrength = tune("ARK_DISTORTION_STRENGTH", 1f, "Distortion ring strength")

    val arkFlareRate = tune("ARK_FLARE_RATE", 10f, "Flashes per second during a burst")
    val arkFlareLife = tune("ARK_FLARE_LIFE", 0.35f, "Flash life, s")
    val arkFlareSize0Cruiser = tune("ARK_FLARE_SIZE0_CRUISER", 0.5f, "Cruiser flash radius at start, Halo units")
    val arkFlareSize1Cruiser = tune("ARK_FLARE_SIZE1_CRUISER", 1f, "Cruiser flash radius at end, Halo units")
    val arkFlareSize0Carrier = tune("ARK_FLARE_SIZE0_CARRIER", 2f, "Carrier flash radius at start, Halo units")
    val arkFlareSize1Carrier = tune("ARK_FLARE_SIZE1_CARRIER", 3.5f, "Carrier flash radius at end, Halo units")
    val arkFlareSpinCruiser = tune("ARK_FLARE_SPIN_CRUISER", 0.35f, "Cruiser flash spin range, turns/s")
    val arkFlareSpinCarrier = tune("ARK_FLARE_SPIN_CARRIER", 0.15f, "Carrier flash spin range, turns/s")
    val arkFlareIntensity = tune("ARK_FLARE_INTENSITY", 1f, "Flash intensity")

    val arkLensRadiusMin = tune("ARK_LENS_RADIUS_MIN", 4f, "Carrier lens flare radius min, Halo units")
    val arkLensRadiusMax = tune("ARK_LENS_RADIUS_MAX", 8f, "Carrier lens flare radius max, Halo units")
    val arkLensBrightness = tune("ARK_LENS_BRIGHTNESS", 1f, "Carrier lens flare brightness")
    val arkCloseSize0 = tune("ARK_CLOSE_SIZE0", 1f, "Carrier close flash radius at start, Halo units")
    val arkCloseSize1 = tune("ARK_CLOSE_SIZE1", 1.75f, "Carrier close flash radius at end, Halo units")

    val arkIdleIntensity = tune("ARK_IDLE_INTENSITY", 0.25f, "Standing portal: tube + ring intensity, × the burst's")
    val arkIdleRing = tune("ARK_IDLE_RING", 1f, "Standing portal: ring size, × the mouth's inner lip")
    val arkIdleSpin = tune("ARK_IDLE_SPIN", 0.05f, "Standing portal: tube spin, turns/s")
    val arkIdleBlackPoint = tune("ARK_IDLE_BLACK_POINT", 0.15f, "Standing portal: tube alpha black point")
    val arkIdleFlashRate = tune("ARK_IDLE_FLASH_RATE", 1f, "Standing portal: flicker flashes per second")
    val arkIdleFlashScale = tune("ARK_IDLE_FLASH_SCALE", 0.5f, "Standing portal: flicker flash size, × the burst's")
    val arkIdleBreath = tune("ARK_IDLE_BREATH", 0.15f, "Standing portal: tube + ring breathing, ± fraction")
    val arkIdleFadeIn = tune("ARK_IDLE_FADE_IN", 1f, "Standing portal: idle look fades back in after a burst over, s")

    val arkArrivalDelay = tune("ARK_ARRIVAL_DELAY", 0.1f, "After the screen fade-in, before the burst and the slide, s")
    val arkSlideFraction = tune("ARK_SLIDE_FRACTION", 0.9f, "Slide-out duration, × the burst life")
    val arkSlideDepth = tune("ARK_SLIDE_DEPTH", 1.5f, "Slide starts this far inside the tube, fleet radii (keep < PATH_CLAMP_RADII)")
    val arkSlidePast = tune("ARK_SLIDE_PAST", 1f, "Slide ends this far past the mouth, fleet radii")
    val arkSlideEase = tune("ARK_SLIDE_EASE", 2f, "Slide ease-out exponent")
    val arkRevealBehind = tune("ARK_REVEAL_BEHIND", 0.5f, "Ships start showing this far before the mouth, fleet radii")
    val arkRevealSpan = tune("ARK_REVEAL_SPAN", 1f, "Ships are fully shown this far after the reveal start, fleet radii")
    val arkSlideJitter = tune("ARK_SLIDE_JITTER", 1f, "1 = warp-in jitter on the ships as they show")
    val arkExitHold = tune("ARK_EXIT_HOLD", 0.3f, "Hold after the fleet enters the pocket exit before the fade, s")
    val arkForceVariant = tune("ARK_FORCE_VARIANT", 0f, "0 = by largest hull, 1 = always cruiser, 2 = always carrier")

    // --- Ark visual pass (developer rulings 2026-10-08; planning/implementations/ark-portal-visuals-pass.md)
    // R = Ark radius, M = mouth radius, L = tube length. Every value is a placeholder.

    // Tube: procedural streaks, colour, depth
    val arkTubeBitmapMix = tune("ARK_TUBE_BITMAP_MIX", 0.35f, "Tube: Halo bitmap share of the streaks (0 = procedural only)")
    val arkTubeStreakIntensity = tune("ARK_TUBE_STREAK_INTENSITY", 1f, "Tube: procedural streak brightness, × tube intensity")
    val arkTubeStreakU = tune("ARK_TUBE_STREAK_U", 6f, "Tube: streak noise repeats around (rounded)")
    val arkTubeStreakV = tune("ARK_TUBE_STREAK_V", 0.6f, "Tube: streak noise scale along (smaller = longer filaments)")
    val arkTubeStreakSharp = tune("ARK_TUBE_STREAK_SHARP", 4f, "Tube: filament sharpening exponent")
    val arkTubeStreakGain = tune("ARK_TUBE_STREAK_GAIN", 0.55f, "Tube: octave falloff")
    val arkTubeFlow = tune("ARK_TUBE_FLOW", 0.8f, "Tube: burst streak flow, noise widths/s (+ = out of the mouth)")
    val arkTubeHotSpan = tune("ARK_TUBE_HOT_SPAN", 0.15f, "Tube: white-hot depth from the mouth, × L")
    val arkTubeVioletFrom = tune("ARK_TUBE_VIOLET_FROM", 0.45f, "Tube: violet falloff fully in by this depth, × L")
    val arkTubeDepthFade = tune("ARK_TUBE_DEPTH_FADE", 1.5f, "Tube: brightness e-folds over L")
    val arkTubeBackDim = tune("ARK_TUBE_BACK_DIM", 0.45f, "Tube: back wall brightness (1 = no depth shading)")
    val arkTubeBackMouth = tune("ARK_TUBE_BACK_MOUTH", 1f, "Tube: back wall at full brightness seen through the mouth (0..1)")
    val arkTubeHotIntensity = tune("ARK_TUBE_HOT_INTENSITY", 2f, "Tube: white-hot layer brightness")
    val arkTubeDeepIntensity = tune("ARK_TUBE_DEEP_INTENSITY", 1f, "Tube: violet layer brightness")
    val arkWhiteHot = tuneRgb("ARK_WHITE_HOT", 0.92f, 0.95f, 1f, "White-hot core colour")
    val arkVioletDeep = tuneRgb("ARK_VIOLET_DEEP", 0.42f, 0.24f, 1f, "Deep tube violet colour")

    // Tube: lightning on the wall
    val arkArcIntensity = tune("ARK_TUBE_ARC_INTENSITY", 1.5f, "Tube: burst lightning brightness")
    val arkArcU = tune("ARK_TUBE_ARC_U", 3f, "Tube: lightning noise scale around (rounded)")
    val arkArcV = tune("ARK_TUBE_ARC_V", 1.2f, "Tube: lightning noise scale along")
    val arkArcSpeed = tune("ARK_TUBE_ARC_SPEED", 0.6f, "Tube: lightning crawl, /s")
    val arkArcGate = tune("ARK_TUBE_ARC_GATE", 0.55f, "Tube: lightning gate threshold (higher = rarer)")
    val arkArcReach = tune("ARK_TUBE_ARC_REACH", 0.5f, "Tube: lightning fades out by this depth, × L")
    val arkArcPaletteV = tune("ARK_TUBE_ARC_PALETTE_V", 0.5f, "Tube: lightning palette row (0..1)")

    // Mouth: rim, inner glow, light pool
    val arkRimIntensity = tune("ARK_RIM_INTENSITY", 2f, "Mouth rim light brightness")
    val arkRimWidth = tune("ARK_RIM_WIDTH", 0.015f, "Mouth rim core width, × M")
    val arkMouthGlowIntensity = tune("ARK_MOUTH_GLOW_INTENSITY", 1.5f, "Inner mouth glow brightness")
    val arkMouthGlowRadii = tune("ARK_MOUTH_GLOW_RADII", 1.2f, "Inner mouth glow size, × M")
    val arkPoolIntensity = tune("ARK_POOL_INTENSITY", 0.25f, "Background light pool brightness")
    val arkPoolRadii = tune("ARK_POOL_RADII", 4f, "Background light pool size, × R")
    val arkPoolOffset = tune("ARK_POOL_OFFSET", 0.5f, "Light pool shift toward the tube body, × L")

    // Mouth window: the interior as a look into slipspace (developer 2026-10-08: not solid white)
    val arkWindowAlpha = tune("ARK_WINDOW_ALPHA", 0.9f, "Mouth window opacity during a burst (0 = off)")
    val arkWindowRadius = tune("ARK_WINDOW_RADIUS", 0.92f, "Mouth window radius, × M")
    val arkWindowEdge = tune("ARK_WINDOW_EDGE", 0.2f, "Mouth window edge softness, fraction of its radius")
    val arkWindowVeinIntensity = tune("ARK_WINDOW_VEIN_INTENSITY", 1.5f, "Mouth window plasma vein brightness")
    val arkWindowVeinScale = tune("ARK_WINDOW_VEIN_SCALE", 2f, "Mouth window plasma vein scale (noise repeats across)")
    val arkWindowSpeed = tune("ARK_WINDOW_SPEED", 0.25f, "Mouth window plasma drift, noise widths/s")
    val arkWindowIdle = tune("ARK_WINDOW_IDLE", 0.7f, "Standing portal: mouth window opacity, × ARK_WINDOW_ALPHA")
    val arkWindowBase = tuneRgb("ARK_WINDOW_BASE", 0.05f, 0.02f, 0.12f, "Mouth window base colour (near-black violet)")

    // Ring: analytic ring with chromatic edge, second ring
    val arkRingBitmap = tune("ARK_RING_BITMAP", 0f, "1 = main ring uses the Halo glow_hard bitmap instead of the analytic ring")
    val arkRingPeak = tune("ARK_RING_PEAK", 0.85f, "Ring line radius, × the Halo half-size (glow_hard edge)")
    val arkRingFill = tune("ARK_RING_FILL", 0.15f, "Ring interior fill (glow_hard is a filled disc; low since the mouth window covers the middle)")
    val arkRingCoreWidth = tune("ARK_RING_CORE_WIDTH", 0.015f, "Ring white core width, × half-size")
    val arkRingBodyWidth = tune("ARK_RING_BODY_WIDTH", 4f, "Ring body width, × core width")
    val arkRingCoreIntensity = tune("ARK_RING_CORE_INTENSITY", 3f, "Ring white core brightness")
    val arkRingFringeOffset = tune("ARK_RING_FRINGE_OFFSET", 0.04f, "Ring chromatic fringe offset, × half-size")
    val arkRingFringeWidth = tune("ARK_RING_FRINGE_WIDTH", 0.025f, "Ring chromatic fringe width, × half-size")
    val arkRingFringeIntensity = tune("ARK_RING_FRINGE_INTENSITY", 1f, "Ring chromatic fringe brightness")
    val arkRingFringeIn = tuneRgb("ARK_RING_FRINGE_IN", 0.35f, 0.85f, 1f, "Ring inner fringe colour")
    val arkRingFringeOut = tuneRgb("ARK_RING_FRINGE_OUT", 0.6f, 0.3f, 1f, "Ring outer fringe colour")
    val arkRing2Speed = tune("ARK_RING2_SPEED", 1.8f, "Second ring collapses this many times faster")
    val arkRing2Size = tune("ARK_RING2_SIZE", 0.8f, "Second ring size, × the main ring")
    val arkRing2Intensity = tune("ARK_RING2_INTENSITY", 0.8f, "Second ring brightness")
    val arkRing2Width = tune("ARK_RING2_WIDTH", 0.5f, "Second ring body width, × the main ring's core")

    // Flashes, sparks, glitter, anamorphic streaks
    val arkFlashHaloSize = tune("ARK_FLASH_HALO_SIZE", 2.2f, "Flash halo size, × the flash")
    val arkFlashHaloIntensity = tune("ARK_FLASH_HALO_INTENSITY", 0.35f, "Flash halo brightness")
    val arkSparkCount = tune("ARK_SPARK_COUNT", 24f, "Sparks at the opening")
    val arkSparkRate = tune("ARK_SPARK_RATE", 20f, "Sparks per second while streaming")
    val arkSparkSeconds = tune("ARK_SPARK_SECONDS", 0.5f, "Spark streaming time after the opening, s")
    val arkSparkSpeed = tune("ARK_SPARK_SPEED", 3f, "Spark speed, R/s")
    val arkSparkSpread = tune("ARK_SPARK_SPREAD", 0.35f, "Spark radial spread (0 = straight out)")
    val arkSparkDrag = tune("ARK_SPARK_DRAG", 0.6f, "Spark drag time constant, s")
    val arkSparkLife = tune("ARK_SPARK_LIFE", 0.7f, "Spark life, s")
    val arkSparkSize = tune("ARK_SPARK_SIZE", 0.04f, "Spark width, × R")
    val arkSparkStretch = tune("ARK_SPARK_STRETCH", 6f, "Spark streak length, × width")
    val arkSparkIntensity = tune("ARK_SPARK_INTENSITY", 4f, "Spark brightness")
    val arkGlitterPerFlash = tune("ARK_GLITTER_PER_FLASH", 1.5f, "Glitter twinkles spawned per flash")
    val arkGlitterLife = tune("ARK_GLITTER_LIFE", 2.5f, "Glitter life, s")
    val arkGlitterSize = tune("ARK_GLITTER_SIZE", 0.05f, "Glitter size, × R")
    val arkGlitterIntensity = tune("ARK_GLITTER_INTENSITY", 8f, "Glitter brightness")
    val arkStreakChance = tune("ARK_STREAK_CHANCE", 0.5f, "Anamorphic streak chance per carrier flash")
    val arkStreakLength = tune("ARK_STREAK_LENGTH", 3f, "Anamorphic streak half-length, × R")
    val arkStreakAspect = tune("ARK_STREAK_ASPECT", 0.06f, "Anamorphic streak width, × length")
    val arkStreakIntensity = tune("ARK_STREAK_INTENSITY", 1.5f, "Anamorphic streak brightness")
    val arkStreakAlongFacing = tune("ARK_STREAK_ALONG_FACING", 0f, "0 = anamorphic streaks screen-horizontal, 1 = along the facing")

    // Refraction front, black-body dim
    val arkRefractStrength = tune("ARK_REFRACT_STRENGTH", 2.5f, "Thin refraction ring push")
    val arkRefractWidth = tune("ARK_REFRACT_WIDTH", 0.04f, "Thin refraction ring width, × its radius")
    val arkRefractSeconds = tune("ARK_REFRACT_SECONDS", 0.6f, "Thin refraction ring life, s")
    val arkRefractMaxRadii = tune("ARK_REFRACT_MAX_RADII", 4f, "Thin refraction ring radius at the end, × R")
    val arkDimStrength = tune("ARK_DIM_STRENGTH", 0.8f, "Opening black-body dim darkness (0 = off)")
    val arkDimSeconds = tune("ARK_DIM_SECONDS", 0.35f, "Opening dim life, s")
    val arkDimRadius = tune("ARK_DIM_RADIUS", 0.9f, "Opening dim radius, × M")
    val arkDimSoft = tune("ARK_DIM_SOFT", 35f, "Opening dim edge softness, degrees")

    // Carrier extras
    val arkStarRadius = tune("ARK_STAR_RADIUS", 6f, "Carrier opening star flare radius, Halo units")
    val arkStarBrightness = tune("ARK_STAR_BRIGHTNESS", 2f, "Carrier opening star flare brightness")
    val arkShockSeconds = tune("ARK_SHOCK_SECONDS", 0.8f, "Carrier close shockwave ring life, s")
    val arkShockRadii = tune("ARK_SHOCK_RADII", 5f, "Carrier close shockwave radius at the end, × R")
    val arkShockIntensity = tune("ARK_SHOCK_INTENSITY", 2f, "Carrier close shockwave brightness")
    val arkShockWidth = tune("ARK_SHOCK_WIDTH", 0.02f, "Carrier close shockwave line width, × radius")
    val arkRemnantSeconds = tune("ARK_REMNANT_SECONDS", 2.5f, "Carrier afterglow remnant ring life, s")
    val arkRemnantIntensity = tune("ARK_REMNANT_INTENSITY", 0.4f, "Carrier afterglow remnant ring brightness")
    val arkRemnantGrowth = tune("ARK_REMNANT_GROWTH", 1.3f, "Carrier afterglow remnant ring growth, × start size")

    // Phases: pre-roll, afterglow, exposure
    val arkPreroll = tune("ARK_PREROLL", 0f, "Charge-up before the opening, s (arrival slide / exit lurch wait for it)")
    val arkPrerollLight = tune("ARK_PREROLL_LIGHT", 1.2f, "Pre-roll glow brightness reached at the opening")
    val arkPrerollRippleRate = tune("ARK_PREROLL_RIPPLE_RATE", 8f, "Pre-roll heat-haze ripples per second")
    val arkPrerollTwinkles = tune("ARK_PREROLL_TWINKLES", 16f, "Pre-roll converging twinkles")
    val arkPrerollGatherRadii = tune("ARK_PREROLL_GATHER_RADII", 2.5f, "Pre-roll gather radius, × R")
    val arkPrerollRim = tune("ARK_PREROLL_RIM", 0.4f, "Pre-roll rim brightness reached at the opening, × ARK_RIM_INTENSITY")
    val arkAfterglow = tune("ARK_AFTERGLOW", 1.5f, "Afterglow after the burst, s")
    val arkHazeCount = tune("ARK_HAZE_COUNT", 4f, "Afterglow haze sprites")
    val arkHazeSize = tune("ARK_HAZE_SIZE", 2.5f, "Afterglow haze size, × R")
    val arkHazeIntensity = tune("ARK_HAZE_INTENSITY", 0.3f, "Afterglow haze brightness")
    val arkExposureMult = tune("ARK_EXPOSURE_MULT", 1f, "Multiplier on every Ark colour (exposure)")

    // Idle portal life
    val arkIdleFlow = tune("ARK_IDLE_FLOW", 0.15f, "Standing portal: streak flow, noise widths/s")
    val arkIdleRim = tune("ARK_IDLE_RIM", 0.35f, "Standing portal: rim brightness, × ARK_RIM_INTENSITY")
    val arkIdleRimPulse = tune("ARK_IDLE_RIM_PULSE", 0.3f, "Standing portal: rim pulse, ± fraction")
    val arkIdleArcRate = tune("ARK_IDLE_ARC_RATE", 0.3f, "Standing portal: lightning burst rate, /s")
    val arkIdleArcIntensity = tune("ARK_IDLE_ARC_INTENSITY", 0.8f, "Standing portal: lightning brightness")
    val arkIdleTwinkleRate = tune("ARK_IDLE_TWINKLE_RATE", 2f, "Standing portal: drifting twinkles per second")
    val arkIdlePool = tune("ARK_IDLE_POOL", 0.3f, "Standing portal: light pool brightness, × the burst's")

    // --- Sounds (shared with the combat Fleet Slipspace Jump) ------------------------------------

    const val SOUND_CHARGE_LOOP = "covenant_remnants_fleet_jump_charge_loop"
    const val SOUND_OPEN = "covenant_remnants_fleet_jump_open"
    const val SOUND_COLLAPSE = "covenant_remnants_fleet_jump_collapse"
    const val SOUND_ARRIVAL = "covenant_remnants_fleet_jump_arrival"

    // =========================================================================================
    // Gating
    // =========================================================================================

    /** Non-fighter members (mothballed and civilian included — developer ruling 2026-10-07). */
    fun shipsThatCount(fleet: CampaignFleetAPI): List<FleetMemberAPI> =
        fleet.fleetData.membersListCopy.filter { !it.isFighterWing }

    /**
     * Ships that must carry the Slipspace Drive hullmod but do not. Rule: if the fleet has any
     * cruiser or capital, every cruiser and capital needs the drive (smaller ships ride along);
     * otherwise every destroyer and frigate needs it. Empty when `NO_SHIP_RESTRICTION` is on.
     */
    fun missingDriveShips(fleet: CampaignFleetAPI): List<FleetMemberAPI> {
        if (noShipRestriction.isOn) return emptyList()
        val ships = shipsThatCount(fleet)
        val big = ships.filter { it.hullSpec.hullSize == HullSize.CRUISER || it.hullSpec.hullSize == HullSize.CAPITAL_SHIP }
        val pool = if (big.isNotEmpty()) big else ships
        return pool.filter { !it.variant.hasHullMod(SlipspaceDriveHullmod.ID) }
    }

    /** Why the jump cannot start from here, or null when it can (fuel is checked separately). */
    fun blockReason(fleet: CampaignFleetAPI): String? {
        val location = fleet.containingLocation
        if (location == null || fleet.isInHyperspace) return "Cannot be used in hyperspace."
        if (location.hasTag(SLIPSPACE_POCKET_TAG)) return "Already in slipspace — follow the current to the exit."
        if (location !is StarSystemAPI) return "Can only be used inside a star system."
        if (fleet.isInHyperspaceTransition) return "Cannot be used while jumping."
        if (shipsThatCount(fleet).isEmpty()) return "No ships."
        if (missingDriveShips(fleet).isNotEmpty()) return "Not every ship that must carry a slipspace drive has one."
        return null
    }

    // =========================================================================================
    // Destinations and costs
    // =========================================================================================

    /** A system the jump can lead to: not the pocket, not hidden, not deep space, with a hyperspace anchor. */
    private fun isJumpTarget(system: StarSystemAPI): Boolean {
        if (system.hasTag(SLIPSPACE_POCKET_TAG)) return false
        if (system.hasTag(Tags.THEME_HIDDEN)) return false
        if (system.isDeepSpace) return false
        val anchor = system.hyperspaceAnchor ?: return false
        return Misc.getStarSystemForAnchor(anchor) != null
    }

    /** Hyperspace anchors of every system the picker offers from [origin] (all systems, fuel-gated at confirm). */
    fun destinations(origin: StarSystemAPI): List<SectorEntityToken> =
        Global.getSector().starSystems.filter { it !== origin && isJumpTarget(it) }.map { it.hyperspaceAnchor }

    /**
     * Systems where the navigation upgrade lets the player plot the exit point: [origin] itself
     * (the in-system jump) and every jump target the player has entered (developer ruling
     * 2026-10-12: never-entered systems offer the edge jump only). Empty without the upgrade.
     */
    fun pointDestinations(origin: StarSystemAPI): List<StarSystemAPI> {
        if (!hasNavigationUpgrade()) return emptyList()
        return Global.getSector().starSystems.filter { isJumpTarget(it) && (it === origin || it.isEnteredByPlayer) }
    }

    fun distanceLY(origin: StarSystemAPI, dest: StarSystemAPI): Float =
        Misc.getDistanceLY(origin.location, dest.location)

    fun fuelCost(fleet: CampaignFleetAPI, origin: StarSystemAPI, dest: StarSystemAPI): Int {
        if (free.isOn) return 0
        val perLY = fleet.logistics.fuelCostPerLightYear * fuelPerLyMult.value
        return ceil(distanceLY(origin, dest) * perLY).toInt()
    }

    /** Fuel-range ring on the picker map: how many LY one unit of fuel/LY buys (0 hides the ring). */
    fun fuelRangeMult(): Float = if (free.isOn || fuelPerLyMult.value <= 0f) 0f else 1f / fuelPerLyMult.value

    fun crCostMult(fleet: CampaignFleetAPI): Float =
        if (free.isOn) 0f else fleet.stats.dynamic.getValue(Stats.DIRECT_JUMP_CR_MULT)

    /**
     * Ships whose CR is below what the jump takes — they risk damage on activation.
     * [jumpMult] scales the hit (the in-system jump's `BLINK_CR_MULT`).
     */
    fun nonReadyShips(fleet: CampaignFleetAPI, jumpMult: Float = 1f): List<FleetMemberAPI> {
        val mult = crCostMult(fleet) * jumpMult
        if (mult <= 0f) return emptyList()
        return fleet.fleetData.membersListCopy.filter { member ->
            val crLoss = member.deployCost * crCostMult.value * mult
            (member.repairTracker.cr * 100f).roundToInt() < (crLoss * 100f).roundToInt()
        }
    }

    /** Supplies it will take to recover the CR hit. */
    fun supplyCost(fleet: CampaignFleetAPI, jumpMult: Float = 1f): Float {
        val mult = crCostMult(fleet) * jumpMult
        return fleet.fleetData.membersListCopy.sumOf { (it.deploymentCostSupplies * crCostMult.value * mult).toDouble() }.toFloat()
    }

    /** Applies the CR hit and the low-CR damage roll (transverse-jump style); [jumpMult] as [nonReadyShips]. */
    fun applyCrHit(fleet: CampaignFleetAPI, jumpMult: Float = 1f) {
        val mult = crCostMult(fleet) * jumpMult
        if (mult <= 0f) return
        for (member in nonReadyShips(fleet, jumpMult)) {
            if (Math.random().toFloat() < EmergencyBurnAbility.ACTIVATION_DAMAGE_PROB) {
                Misc.applyDamage(
                    member, null, FleetMemberDamageLevel.LOW, false, null, null,
                    true, null, member.shipName + " suffers damage from slipspace jump activation"
                )
            }
        }
        for (member in fleet.fleetData.membersListCopy) {
            val crLoss = member.deployCost * crCostMult.value * mult
            member.repairTracker.applyCREvent(-crLoss, "Slipspace jump")
        }
    }

    // =========================================================================================
    // Navigation upgrade: exact exit points and in-system jumps (rulings 2026-10-12)
    // =========================================================================================

    fun hasNavigationUpgrade(): Boolean =
        Global.getSector()?.characterData?.memoryWithoutUpdate?.getBoolean(NAVIGATION_FLAG) == true

    /**
     * Installs the navigation upgrade. Used by the Navigation System item and `CovenantGiveAll`
     * now, the granting quest later. Never revoked.
     * @return false if it was already installed.
     */
    fun grantUpgradeToPlayer(): Boolean {
        val memory = Global.getSector().characterData.memoryWithoutUpdate
        if (memory.getBoolean(NAVIGATION_FLAG)) return false
        memory.set(NAVIGATION_FLAG, true)
        return true
    }

    /** Fuel of an in-system jump: a flat [blinkFuelLy] light-years' worth (transverse-jump style). */
    fun blinkFuelCost(fleet: CampaignFleetAPI): Int {
        if (free.isOn) return 0
        return ceil(fleet.logistics.fuelCostPerLightYear * blinkFuelLy.value).toInt()
    }

    /** Where [system]'s exit-point limit is measured from: its centre entity (the world origin without one). */
    fun systemCenter(system: StarSystemAPI): Vector2f = system.center?.location?.let { Vector2f(it) } ?: Vector2f(0f, 0f)

    /** How far from [systemCenter] an exit point may be: the system's outer radius, no pad (ruling 2026-10-12). */
    fun pointBoundsRadius(system: StarSystemAPI): Float {
        // getOuterRadius measures terrain and jump points from the centre entity and fails without one.
        val outer = if (system.center != null) BaseThemeGenerator.getOuterRadius(system) else 0f
        return if (outer > 0f) outer else arrivalFallbackRadius.value
    }

    /** Stars and black holes of [system] with their no-exit radius ([starExclusionMult] × body radius). */
    fun starZones(system: StarSystemAPI): List<Pair<PlanetAPI, Float>> =
        system.planets.filter { it.isStar || it.isBlackHole }.map { it to it.radius * starExclusionMult.value }

    /**
     * Why [point] in [system] cannot be an exit point, or null when it can: beyond the system's
     * outer radius, inside a star / black-hole zone, or (in-system jump, [fleetPos] given) closer to
     * the fleet than [blinkMinDistance].
     */
    fun pointRejectReason(system: StarSystemAPI, point: Vector2f, fleetPos: Vector2f?): String? {
        if (Misc.getDistance(systemCenter(system), point) > pointBoundsRadius(system)) {
            return "Beyond the outer limit of the system."
        }
        for ((body, radius) in starZones(system)) {
            if (Misc.getDistance(body.location, point) < radius) return "Too close to ${body.name}."
        }
        if (fleetPos != null && Misc.getDistance(fleetPos, point) < blinkMinDistance.value) {
            return "Too close to the fleet."
        }
        return null
    }

    /**
     * [point] pushed radially out of any star / black-hole zone it has ended up in (stars of binary
     * systems move between the pick and the arrival; developer ruling 2026-10-12: nudge, not cancel).
     */
    fun nudgeOutOfStarZones(system: StarSystemAPI, point: Vector2f): Vector2f {
        val result = Vector2f(point)
        val zones = starZones(system)
        // A few passes: in a close binary, leaving one zone can land inside the other.
        repeat(4) {
            var moved = false
            for ((body, radius) in zones) {
                val dx = result.x - body.location.x
                val dy = result.y - body.location.y
                val d = sqrt(dx * dx + dy * dy)
                if (d >= radius) continue
                val edge = radius * 1.01f
                if (d < 1f) {
                    result.set(body.location.x + edge, body.location.y)
                } else {
                    result.set(body.location.x + dx / d * edge, body.location.y + dy / d * edge)
                }
                moved = true
            }
            if (!moved) return result
        }
        return result
    }

    /** Facing of a precise arrival from another system: the travel direction, origin → destination in hyperspace. */
    fun travelFacing(origin: StarSystemAPI, dest: StarSystemAPI): Float {
        if (Misc.getDistance(origin.location, dest.location) < 1f) return 0f
        return Misc.getAngleInDegrees(origin.location, dest.location)
    }

    // =========================================================================================
    // Slipspace Tuning campaign bonus
    // =========================================================================================

    fun tunedCount(fleet: CampaignFleetAPI): Int =
        shipsThatCount(fleet).count { it.variant.hasHullMod(SlipspaceTuning.ID) }

    fun sModTunedCount(fleet: CampaignFleetAPI): Int =
        shipsThatCount(fleet).count { it.variant.sMods.contains(SlipspaceTuning.ID) || it.variant.sModdedBuiltIns.contains(SlipspaceTuning.ID) }

    /** Multiplier on the pocket current's burn level. */
    fun transitBurnMult(fleet: CampaignFleetAPI): Float =
        min(1f + tuningTransitPerShip.value * tunedCount(fleet), tuningTransitMaxMult.value)

    /** Multiplier on the charge-up time (S-mod Tuning). */
    fun chargeMult(fleet: CampaignFleetAPI): Float =
        max(1f - tuningSModPerShip.value * sModTunedCount(fleet), tuningSModMinMult.value)

    /** Multiplier on the cooldown (S-mod Tuning). */
    fun cooldownMult(fleet: CampaignFleetAPI): Float = chargeMult(fleet)

    // =========================================================================================
    // Geometry
    // =========================================================================================

    /** Rupture radius for a fleet. */
    fun ruptureRadius(fleet: CampaignFleetAPI): Float = max(fleet.radius * ruptureRadiusMult.value, ruptureRadiusMin.value)

    /** Ark exit burst radius for a fleet. */
    fun arkRadius(fleet: CampaignFleetAPI): Float = max(fleet.radius * arkRadiusMult.value, arkRadiusMin.value)

    /** Ark variant for a fleet: carrier when any capital ship is aboard (developer ruling 2026-10-08), unless forced. */
    fun arkVariant(fleet: CampaignFleetAPI): ArkVariant {
        when (arkForceVariant.value.roundToInt()) {
            1 -> return ArkVariant.CRUISER
            2 -> return ArkVariant.CARRIER
        }
        val capital = shipsThatCount(fleet).any { it.hullSpec.hullSize == HullSize.CAPITAL_SHIP }
        return if (capital) ArkVariant.CARRIER else ArkVariant.CRUISER
    }

    fun arkSeconds(variant: ArkVariant): Float =
        if (variant == ArkVariant.CARRIER) arkCarrierSeconds.value else arkCruiserSeconds.value

    /** The slide-out of an arrival: from inside the tube to past the mouth, over most of the burst. */
    fun arrivalPlan(mouth: Vector2f, facingDegrees: Float, fleet: CampaignFleetAPI): SlipspaceTransitionScript.ArrivalPlan {
        val unit = Misc.getUnitVectorAtDegreeAngle(facingDegrees)
        val r = max(fleet.radius, 10f)
        val from = Vector2f(mouth.x - unit.x * arkSlideDepth.value * r, mouth.y - unit.y * arkSlideDepth.value * r)
        val to = Vector2f(mouth.x + unit.x * arkSlidePast.value * r, mouth.y + unit.y * arkSlidePast.value * r)
        val seconds = max(arkSeconds(arkVariant(fleet)) * arkSlideFraction.value, 0.1f)
        return SlipspaceTransitionScript.ArrivalPlan(from, to, Vector2f(mouth), facingDegrees, seconds)
    }

    /**
     * The arrival point in [dest]: on its outer edge, on the side that faces [origin]
     * (direction from the destination's hyperspace position toward the origin's).
     */
    fun arrivalPoint(origin: StarSystemAPI, dest: StarSystemAPI): Vector2f {
        var outer = BaseThemeGenerator.getOuterRadius(dest)
        if (outer <= 0f) outer = arrivalFallbackRadius.value
        val dir = Vector2f.sub(origin.location, dest.location, Vector2f())
        val angle = if (dir.lengthSquared() < 1f) 0f else Misc.getAngleInDegrees(dir)
        val unit = Misc.getUnitVectorAtDegreeAngle(angle)
        unit.scale(outer + arrivalEdgePad.value)
        val center = dest.center?.location ?: Vector2f(0f, 0f)
        return Vector2f.add(center, unit, Vector2f())
    }

    // =========================================================================================
    // Grant
    // =========================================================================================

    /**
     * Gives the player the ability (character data + fleet + a free ability-bar slot), like the
     * vanilla `AddAbility` rule command. Used by `CovenantGiveAll` now and the granting quest later.
     * @return false if the player already had it.
     */
    fun grantToPlayer(): Boolean {
        val sector = Global.getSector()
        val fleet = sector.playerFleet
        val had = fleet.hasAbility(ABILITY_ID)
        sector.characterData.addAbility(ABILITY_ID)
        if (!fleet.hasAbility(ABILITY_ID)) fleet.addAbility(ABILITY_ID)
        if (!had) assignToFreeSlot()
        sector.characterData.memoryWithoutUpdate.set("\$ability:$ABILITY_ID", true, 0f)
        return !had
    }

    private fun assignToFreeSlot() {
        val slots = Global.getSector().uiData.abilitySlotsAPI
        val currBar = slots.currBarIndex
        try {
            for (bar in 0 until 5) {
                slots.currBarIndex = bar
                val barSlots: List<AbilitySlotAPI> = slots.currSlotsCopy
                if (barSlots.any { it.abilityId == ABILITY_ID }) return
            }
            outer@ for (bar in 0 until 5) {
                slots.currBarIndex = bar
                for (slot in slots.currSlotsCopy) {
                    if (slot.abilityId == null) {
                        slot.abilityId = ABILITY_ID
                        break@outer
                    }
                }
            }
        } finally {
            slots.currBarIndex = currBar
        }
    }
}

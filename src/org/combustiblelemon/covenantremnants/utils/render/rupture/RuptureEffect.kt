package org.combustiblelemon.covenantremnants.utils.render.rupture

import org.combustiblelemon.covenantremnants.settings.CovenantSettings
import org.combustiblelemon.covenantremnants.systems.FleetSlipspaceJump
import org.lwjgl.util.vector.Vector2f
import java.util.Random
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Visual timeline of one slipspace rupture, remade from the Halo: Reach slipspace bomb: the end of
 * m45 (cinematic `045lf_jorge`, effects `slipspace_secondary` and `slipspace_event`), driven by
 * Reach's own curves ([ReachCurves]), textures ([RuptureTexture]) and timings. The decoded effect
 * and how it maps onto the jump are in planning/implementations/fleet-slipspace-jump.md.
 *
 * Reach units are converted so the rupture body (the `slipspace_sphere` mesh, 625 Reach units
 * across its radius) is the grab radius.
 *
 * Entry rupture (around the carrier):
 * - CHARGE: [chargeLevel] (the charge-up) drives `slipspace_secondary`'s 3 s, stretched over the
 *   charge: the charge stream (sphere-warped plasma sprites), heat-haze rings, the star flare, a
 *   flickering blue glow, and (CHARGE_RING, off by default) a thin violet ring at the grab radius
 *   (not in Reach). Reach's arming flare is left out (developer decision).
 * - OPEN (the fade): `slipspace_event` 0..2 s, held at 2 s if the fade lasts longer. The glow sphere
 *   bursts to the grab radius going salmon to blue-violet; the black body grows and fades in inside
 *   it on the same curves, BLACK_DELAY s behind (developer 2026-10-02; in Reach together), and
 *   shrinks with the white at the collapse (pure
 *   black, developer decision; Reach's angle fade keeps it off the limb, so the glow stays a white
 *   rim around the darkness; it multiplies the glow in HDR like Reach, so the burst stays white
 *   while the black is thin); the lightning web runs over the black; a violet light glow.
 * - COLLAPSE (from the jump): `slipspace_event` from 2 s. 150 sparks on the rim; the spheres shrink
 *   to nothing by 3 s; at 2.5 s the burst (blast shell, plasma shell, plasma clouds and flames,
 *   twinkles, fiery smoke going salmon to violet, the haze); the collapse flare from 2.6 s.
 *
 * Charge core (not in Reach, developer 2026-10-02): a white sphere at the centre of the charge,
 * the size of the star flare's bright core (so the flare's spokes reach past it), growing in from
 * a point over the charge, breathing (size and brightness pulse),
 * limb-darkened and kept below saturation so the charge energy and the heat-haze rings show over
 * it, with a soft bloom that builds with the charge. At the opening it holds until the glow sphere
 * outgrows it, then fades out.
 *
 * Exit rupture (at the destination):
 * - SEED: the same charge (white core included), building toward the jump through [chargeLevel].
 * - FLASH (at the jump): the glow sphere bursts to the grab radius and fades over DISSOLVE, with
 *   twinkles and a star flare; no black sphere (developer decision).
 *
 * Blink portal ([Kind.BLINK], the Slipspace Drive): the Halo 3: ODST carrier-jump portal
 * (c100_intro `slipspace_sphere.effect`, build event only): a deep-blue glow sphere with a bright
 * blue-white origin core, the outer plasma web tinted white to blue, a soft blue light and the
 * blue build lens flare, all growing linearly over the build; [close] shrinks it away. No collapse,
 * no explosion (developer rulings 2026-10-02). See planning/implementations/slipspace-drive-blink.md.
 *
 * Ark exit ([Kind.ARK], the campaign jump's pocket portals and destination arrival): the Halo 3
 * "The Ark" slipspace exit, see [ArkTimeline]. ARMED draws nothing until [burst]; a persistent
 * portal idles ([Phase.IDLE]) between bursts; a one-shot one ends after its burst. [facing] is the
 * direction a fleet leaves the tube.
 *
 * [abort] fades whatever is showing out through [master].
 */
class RuptureEffect private constructor(val kind: Kind, center: Vector2f, val radiusWorld: Float) {
    enum class Kind { ENTRY, EXIT, BLINK, ARK }

    enum class Phase { CHARGE, OPEN, COLLAPSE, SEED, FLASH, BUILD, CLOSING, ARMED, IDLE, BURST, ABORTING, DONE }

    companion object {
        private var seedCounter = 0

        // The rupture body in Reach units: slipspace_sphere mesh radius 0.5 x size 125 x scale 10.
        private const val BODY_RADIUS_REACH = 625f
        private const val SPHERE_MESH_RADIUS = 0.5f
        private const val SHELL_MESH_RADIUS = 0.3f
        private const val BODY_SIZE = 125f
        private const val SHELL_SIZE = 130f

        // slipspace_event clock, seconds
        private const val SPHERE_LIFE = 3f
        private const val COLLAPSE_AT = 2f
        private const val BURST_AT = 2.5f
        private const val BURST_EMIT_DURATION = 5.38f
        private const val SHELL_LIFE = 6f
        private const val COLLAPSE_FLARE_AT = 2.6f
        private const val COLLAPSE_FLARE_DURATION = 1f
        private const val RUPTURE_LIGHT_AT = 0.37f
        private const val RUPTURE_LIGHT_DURATION = 5f

        // Light radii (Reach attenuation start/end distances)
        private const val RUPTURE_LIGHT_START = 710f
        private const val RUPTURE_LIGHT_END = 825f
        private const val CHARGE_LIGHT_START = 200f
        private const val CHARGE_LIGHT_END = 600f

        // Not in the tags: stand-ins for Reach's HDR bloom/lighting and for event cut-offs.
        private const val GLOW_HALO = 0.45f
        private const val GLOW_HALO_WIDTH = 0.08f
        private const val LIGHT_GLOW = 0.25f
        private const val RING_INTENSITY = 2f
        private const val SPARK_STRETCH = 6f
        private const val FLAME_ASPECT = 2f
        private const val TWINKLE_DRAG = 1.5f
        private const val SHELL_FADE_FROM = 0.85f
        private const val COLLAPSE_FLARE_TAIL = 0.4f
        private const val CHARGE_FADE = 0.3f
        // Exit flash: the first EXIT_GLOW_SPAN s of the glow curve (0 -> full size) played in
        // EXIT_EXPAND s, so the arrivals are covered quickly.
        private const val EXIT_EXPAND = 0.3f
        private const val EXIT_GLOW_SPAN = 0.75f
        private const val EXIT_FLARE_DURATION = 1.5f
        private const val EXIT_STAR_BRIGHTNESS = 5f
        private const val ABORT_DURATION = 0.5f

        // Charge core: white sphere at the centre of the charge (developer 2026-10-02).
        // Core radius = the star flare's half-size × this: the bright core of star_flare.png (measured:
        // saturated out to ~0.11 of the half-size at the flare's charge brightness; the spokes fade
        // out at ~0.3-0.35). ≈ 12-18 su at R 1000.
        private const val CORE_FLARE_FRACTION = 0.11f
        private const val CORE_INTENSITY_MIN = 1.2f    // HDR white before tone mapping; 1.9 ≈ 0.85 at the centre,
        private const val CORE_INTENSITY_MAX = 1.9f    // so the additive charge energy still shows on top
        private const val CORE_HALO_MIN = 0.2f
        private const val CORE_HALO_MAX = 0.6f
        private const val CORE_HALO_WIDTH = 0.5f       // in core radii
        private const val CORE_BREATH_PERIOD = 1.6f    // seconds per breath
        private const val CORE_BREATH_SIZE = 0.08f     // radius pulse, ± fraction
        private const val CORE_BREATH_BRIGHTNESS = 0.15f // brightness pulse, ± fraction
        private const val CORE_SWALLOW_FADE = 0.15f    // fade once the opening glow sphere outgrows it

        // Colours and intensities from the tags
        private val RING_VIOLET = floatArrayOf(0.55f, 0.35f, 1f)
        // slipspace_sphere_multiply.particle angle fade, degrees from edge-on
        private const val BLACK_ANGLE_FADE_CUTOFF = 10f
        private const val BLACK_ANGLE_FADE_RANGE = 25f
        private val STAR_FLARE_COLOR = floatArrayOf(0.2f, 0.2457f, 1f)
        private const val STAR_FLARE_MIN = 2.3f
        private const val STAR_FLARE_MAX = 7.3f
        private val CHARGE_LIGHT_BRIGHT = floatArrayOf(0.2863f, 0.3843f, 1f)
        private val CHARGE_LIGHT_DARK = floatArrayOf(0.0431f, 0.0392f, 0.0902f)
        private val RUPTURE_LIGHT_COLOR = floatArrayOf(0.4863f, 0.5059f, 1f)
        private const val CHARGE_INTENSITY = 7f
        private val SPARK_COLOR = floatArrayOf(0.7176f, 0.702f, 1f)
        private const val SPARK_INTENSITY = 4f
        private val BLAST_TINT = floatArrayOf(0.5765f, 0.5216f, 1f)
        private const val BURST_PLASMA_INTENSITY = 0.5f
        private val FLAME_COLOR_A = floatArrayOf(1f, 1f, 1f)
        private val FLAME_COLOR_B = floatArrayOf(1f, 0.7451f, 0.6784f)
        private val TWINKLE_COLOR_A = floatArrayOf(0.4078f, 0.4824f, 1f)
        private val TWINKLE_COLOR_B = floatArrayOf(0.5608f, 0.3922f, 1f)
        private const val TWINKLE_INTENSITY_MIN = 9.7f
        private const val TWINKLE_INTENSITY_MAX = 12f
        private const val HAZE_ALPHA = 0.15f
        private const val PLASMA_SHELL_ALPHA = 0.75f

        // Mesh texcoord offsets around the pole (fitted from the meshes' vertices)
        private const val SPHERE_U_OFFSET = -0.1575f
        private const val SHELL_U_OFFSET = 0.75f

        // slipspace_sphere_plasma.particle and plasma_sphere.particle texture transforms
        private val WEB_XFORM_A = NoiseTransform(7f, 6f, 1f, 6f, 2f)
        private val WEB_XFORM_B = NoiseTransform(3f, 6f, 1f, 6f, 1f)
        private val SHELL_XFORM_A = NoiseTransform(2f, 20f, 1f, 10f, 1f)
        private val SHELL_XFORM_B = NoiseTransform(8f)

        fun entry(center: Vector2f, radiusWorld: Float): RuptureEffect =
            RuptureEffect(Kind.ENTRY, center, radiusWorld).also { it.phase = Phase.CHARGE }

        fun exit(center: Vector2f, radiusWorld: Float): RuptureEffect =
            RuptureEffect(Kind.EXIT, center, radiusWorld).also { it.phase = Phase.SEED }

        /** A blink portal of radius [radiusWorld] opening over [buildDuration] seconds. */
        fun blink(center: Vector2f, radiusWorld: Float, buildDuration: Float): RuptureEffect =
            RuptureEffect(Kind.BLINK, center, radiusWorld).also {
                it.phase = Phase.BUILD
                it.blinkBuild = buildDuration
            }

        /**
         * An Ark slipspace exit with its mouth at [center], fleets leaving along [facingDegrees];
         * [radiusWorld] sizes the idle look (and bursts that give no radius). [persistent] = a
         * standing portal that idles between bursts; otherwise it stays ARMED (invisible) until
         * [burst] and ends after it.
         */
        fun ark(center: Vector2f, facingDegrees: Float, radiusWorld: Float, persistent: Boolean): RuptureEffect =
            RuptureEffect(Kind.ARK, center, radiusWorld).also {
                it.facing = facingDegrees
                it.ark = ArkTimeline(it, persistent)
                it.phase = if (persistent) Phase.IDLE else Phase.ARMED
            }

        // --- Blink portal (ODST c100_intro slipspace_sphere, build event). Colours from the tags;
        // intensities and proportions are placeholders (ODST values are HDR for its own tone map).
        private val WHITE = floatArrayOf(1f, 1f, 1f)
        private val ODST_GLOW_COLOR = floatArrayOf(0.055f, 0.192f, 0.976f)     // slipspace_sphere_glow #0e31f9
        private val ODST_ORIGIN_COLOR = floatArrayOf(0.180f, 0.188f, 1f)       // slipspace_sphere_origin #2e30ff
        private val ODST_PLASMA_TINT = floatArrayOf(0.176f, 0.278f, 0.859f)    // outer plasma tint end #2d47db
        private val ODST_SOFT_GLOW = floatArrayOf(0.141f, 0.227f, 0.835f)      // glow_soft tint #243ad5
        private val ODST_FLARE_COLOR = floatArrayOf(0.2745f, 0.3608f, 0.9804f) // slipspace_build.lens_flare
        private const val BLINK_CLOSE = 0.3f
        private const val BLINK_GLOW_INTENSITY = 2.5f
        private const val BLINK_GLOW_HALO = 0.6f
        private const val BLINK_GLOW_HALO_WIDTH = 0.15f
        private const val BLINK_ORIGIN_RADIUS = 0.35f      // in portal radii
        private const val BLINK_ORIGIN_INTENSITY = 6f
        private const val BLINK_PLASMA_RADIUS = 1.12f      // outer plasma web, in portal radii
        private const val BLINK_LIGHT_RADIUS = 2.2f        // soft glow reach, in portal radii
        private const val BLINK_LIGHT_INTENSITY = 0.9f
        private const val BLINK_FLARE_RADIUS = 1.6f        // lens flare at full size, in portal radii (700 -> 200 in ODST)
        private const val BLINK_FLARE_BRIGHTNESS = 2.5f

        private fun ramp(t: Float, start: Float, end: Float): Float =
            if (end <= start) (if (t >= end) 1f else 0f) else ((t - start) / (end - start)).coerceIn(0f, 1f)

        private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

        private fun lerpColor(out: FloatArray, a: FloatArray, b: FloatArray, t: Float) {
            for (i in 0 until 3) out[i] = lerp(a[i], b[i], t)
        }

        private fun hash(n: Int): Float {
            var h = n * 374761393 + 668265263
            h = (h xor (h ushr 13)) * 1274126177
            return ((h xor (h ushr 16)) and 0x7fffffff) / 2147483647f
        }

        /** Smooth value noise 0..1 over time (Reach's periodic "noise" functions). */
        private fun noise(t: Float, salt: Int): Float {
            val i = floor(t).toInt()
            val f = t - i
            val u = f * f * (3f - 2f * f)
            return lerp(hash(i * 31 + salt), hash((i + 1) * 31 + salt), u)
        }
    }

    val center = Vector2f(center)
    val seed: Float = (seedCounter++ % 997) * 0.731f
    private val random = Random(System.nanoTime() xor (seedCounter * 7919L))
    private val salt = seedCounter * 101

    /** World units per Reach unit. */
    private val unit = radiusWorld / BODY_RADIUS_REACH

    var phase: Phase = Phase.CHARGE
        private set
    private var phaseTime = 0f
    private var time = 0f

    /** CHARGE / SEED input 0..1, set by the jump session every frame. */
    var chargeLevel = 0f

    /** ARK: the direction a fleet leaves the tube, degrees (the tube extends the other way). */
    var facing = 0f

    private var ark: ArkTimeline? = null

    /** ARK: the variant of the running or last burst. */
    val arkVariant: ArkVariant? get() = ark?.variant

    /** ARK: true while a burst plays. */
    val isBursting: Boolean get() = ark?.bursting == true

    /** BLINK: build duration (s) and current size as a fraction of the full radius. */
    private var blinkBuild = 1f
    var openness = 0f
        private set

    /** Whole-effect opacity: 1 normally, fades to 0 when aborted. [RuptureRenderer] may scale it for one draw. */
    var master = 1f
        internal set

    // --- Draw lists (read by RuptureRenderer) -------------------------------------------------

    internal val sprites = ArrayList<RuptureSprite>()

    internal val light = SphereDraw(SphereMode.LIGHT)
    internal val shell = SphereDraw(SphereMode.SHELL).apply {
        alphaMap = RuptureTexture.BEAM
        uOffset = SHELL_U_OFFSET
        BLAST_TINT.copyInto(color)
    }
    internal val plasmaShell = SphereDraw(SphereMode.PLASMA_SHELL).apply {
        noiseA = RuptureTexture.NOISE_512_A
        noiseB = RuptureTexture.NOISE_512_B
        palette = RuptureTexture.SHELL_PALETTE
        xformA = SHELL_XFORM_A
        xformB = SHELL_XFORM_B
        uOffset = SHELL_U_OFFSET
    }
    /** The white charge core (white by default), behind the opening glow sphere that swallows it. */
    internal val core = SphereDraw(SphereMode.CORE)
    internal val glow = SphereDraw(SphereMode.GLOW)
    /** Blink portal only: the bright origin core over the glow body. */
    internal val origin = SphereDraw(SphereMode.GLOW)
    internal val black = SphereDraw(SphereMode.BLACK)
    internal val web = SphereDraw(SphereMode.WEB).apply {
        noiseA = RuptureTexture.SPHERE_NOISE_A
        noiseB = RuptureTexture.SPHERE_NOISE_B
        palette = RuptureTexture.SPHERE_WEB_PALETTE
        alphaMap = RuptureTexture.SPHERE_POLE_MASK
        xformA = WEB_XFORM_A
        xformB = WEB_XFORM_B
        uOffset = SPHERE_U_OFFSET
    }
    internal val ring = SphereDraw(SphereMode.RING).apply { RING_VIOLET.copyInto(color) }
    /** ARK: the background light pool, the opening's black-body dim (elliptical) and the inner mouth glow (elliptical). */
    internal val arkPool = SphereDraw(SphereMode.LIGHT)
    internal val arkDim = SphereDraw(SphereMode.BLACK)
    internal val arkGlow = SphereDraw(SphereMode.LIGHT)

    /** Back to front: lights and shells, charge core, glow, origin core, black body, web, ring; then the Ark pool, dim, mouth glow. */
    internal val spheres = listOf(light, shell, plasmaShell, core, glow, origin, black, web, ring, arkPool, arkDim, arkGlow)

    internal val starFlare = FlareDraw(SpriteMaterial.FLARE_STAR)
    internal val collapseFlare = FlareDraw(SpriteMaterial.FLARE_GLOW)
    /** ARK: the carrier's opening lens flare and star flare. */
    internal val arkLens = FlareDraw(SpriteMaterial.ARK_FLARE)
    internal val arkStar = FlareDraw(SpriteMaterial.FLARE_STAR)
    internal val flares = listOf(starFlare, collapseFlare, arkLens, arkStar)

    /** ARK: the slipspace tube mesh. */
    internal val tube = TubeDraw()

    /** ARK: the analytic ring style (RuptureSpriteShader.RING_STYLE_FLOATS), written each frame by the timeline. */
    internal val arkRingStyle = FloatArray(RING_STYLE_FLOATS)

    /** ARK: called once when a burst opens (after its pre-roll); the owner plays the sound. Transient. */
    var onArkOpen: (() -> Unit)? = null

    // --- Emitter state ------------------------------------------------------------------------

    private var chargeStarted = false
    private var chargeAccumulator = 0f
    private var distortionAccumulator = 0f
    private var sparksSpawned = false
    private var burstStarted = false
    private var burstPlasmaAccumulator = 0f
    private var flameAccumulator = 0f
    private var twinkleAccumulator = 0f
    private var lingerAccumulator = 0f
    private var exitTwinklesStarted = false
    private var coreSwallowedAt = -1f

    private val scratch = FloatArray(4)

    val isDone: Boolean get() = phase == Phase.DONE

    // --- Transitions --------------------------------------------------------------------------

    fun startFade() {
        if (phase == Phase.CHARGE) switchTo(Phase.OPEN)
    }

    fun startCollapse() {
        if (phase == Phase.CHARGE || phase == Phase.OPEN) switchTo(Phase.COLLAPSE)
    }

    fun startDissolve() {
        if (phase == Phase.SEED) switchTo(Phase.FLASH)
    }

    fun abort() {
        if (phase != Phase.DONE && phase != Phase.ABORTING) switchTo(Phase.ABORTING)
    }

    /** BLINK: shrink the portal away (from wherever the build is). */
    fun close() {
        if (phase == Phase.BUILD) switchTo(Phase.CLOSING)
    }

    /**
     * ARK: play the exit burst of [variant], scaled for a fleet of radius [radiusWorld] (this
     * effect's radius by default). From ARMED, IDLE or mid-burst (restarts).
     */
    fun burst(variant: ArkVariant, radiusWorld: Float = this.radiusWorld) {
        val timeline = ark ?: return
        if (phase == Phase.DONE || phase == Phase.ABORTING) return
        timeline.burst(variant, radiusWorld)
        switchTo(Phase.BURST)
    }

    internal fun switchTo(next: Phase) {
        phase = next
        phaseTime = 0f
    }

    // --- Timeline -----------------------------------------------------------------------------

    fun advance(deltaTime: Float) {
        if (phase == Phase.DONE) return
        phaseTime += deltaTime
        time += deltaTime

        // An abort keeps the last frame's spheres and flares and fades them through master.
        if (phase != Phase.ABORTING) clearDraws()
        moveSprites(deltaTime)

        when (phase) {
            Phase.CHARGE, Phase.SEED -> charge(deltaTime)
            Phase.OPEN -> open()
            Phase.COLLAPSE -> collapse(deltaTime)
            Phase.FLASH -> flash(deltaTime)
            Phase.BUILD -> build()
            Phase.CLOSING -> closing()
            Phase.ARMED -> {}
            Phase.IDLE, Phase.BURST -> {
                val timeline = ark
                if (timeline == null) {
                    switchTo(Phase.DONE)
                } else {
                    val finished = timeline.advance(deltaTime)
                    if (finished) switchTo(Phase.DONE) else if (!timeline.bursting && phase == Phase.BURST) switchTo(Phase.IDLE)
                }
            }
            Phase.ABORTING -> aborting()
            Phase.DONE -> {}
        }
        refreshSprites()
    }

    private fun clearDraws() {
        for (sphere in spheres) sphere.active = false
        for (flare in flares) flare.active = false
        tube.active = false
    }

    private fun moveSprites(dt: Float) {
        val iterator = sprites.iterator()
        while (iterator.hasNext()) {
            val s = iterator.next()
            s.age += dt
            if (s.isDead) {
                iterator.remove()
                continue
            }
            if (s.drag > 0f) {
                val keep = exp(-dt / s.drag)
                s.vx *= keep
                s.vy *= keep
            }
            s.x += s.vx * dt
            s.y += s.vy * dt
        }
    }

    // --- Charge (slipspace_secondary) ---------------------------------------------------------

    private fun charge(dt: Float) {
        val c = chargeLevel.coerceIn(0f, 1f)
        val density = particleDensity()
        if (!chargeStarted) {
            chargeStarted = true
            repeat(count(2f)) { spawnCharge() }
            repeat(count(1f)) { spawnDistortion() }
        }
        chargeAccumulator += ReachCurves.CHARGE_RATE.at(c) * density * dt
        while (chargeAccumulator >= 1f) {
            chargeAccumulator -= 1f
            spawnCharge()
        }
        distortionAccumulator += ReachCurves.DISTORTION_RATE.at(c) * density * dt
        while (distortionAccumulator >= 1f) {
            distortionAccumulator -= 1f
            spawnDistortion()
        }
        chargeExtras(c, 1f)
        chargeCore(c, 1f)
    }

    /** Core radius at charge [c]: the star flare's bright core, grown in (eased out) from a point. */
    private fun coreRadius(c: Float): Float {
        val grow = 1f - (1f - c) * (1f - c)
        return ReachCurves.STAR_FLARE_RADIUS.at(c) * unit * CORE_FLARE_FRACTION * grow
    }

    /** The white sphere at the centre of the charge: grows with [c], brightens, breathes. */
    private fun chargeCore(c: Float, alpha: Float) {
        if (alpha <= 0f) return
        val breath = sin(2f * PI.toFloat() * time / CORE_BREATH_PERIOD + seed)
        core.active = true
        core.radius = coreRadius(c) * (1f + CORE_BREATH_SIZE * breath)
        core.intensity = lerp(CORE_INTENSITY_MIN, CORE_INTENSITY_MAX, c) * (1f + CORE_BREATH_BRIGHTNESS * breath)
        core.alpha = alpha
        core.halo = lerp(CORE_HALO_MIN, CORE_HALO_MAX, c)
        core.haloWidth = CORE_HALO_WIDTH
    }

    /** After the charge: the core holds until the opening glow sphere outgrows it, then fades out. */
    private fun swallowCore() {
        val c = chargeLevel.coerceIn(0f, 1f)
        if (coreSwallowedAt < 0f && glow.active && glow.radius >= coreRadius(c)) {
            coreSwallowedAt = time
        }
        val alpha = if (coreSwallowedAt < 0f) 1f else 1f - (time - coreSwallowedAt) / CORE_SWALLOW_FADE
        chargeCore(c, alpha)
    }

    /** The ring, flares and blue glow of the charge; [fade] dims them as the rupture opens. */
    private fun chargeExtras(c: Float, fade: Float) {
        if (fade <= 0f) return

        if (FleetSlipspaceJump.chargeRing.value >= 0.5f) {
            ring.active = true
            ring.radius = radiusWorld
            ring.intensity = RING_INTENSITY
            ring.alpha = lerp(0.25f, 0.7f, c) * fade
            ring.ringWidth = 0.012f
        }

        // Reach's arming flare (slip_space_secondary.lens_flare: a large flare spiking out to ~1.45
        // grab radii and back) is left out: developer 2026-10-01.

        starFlare.active = true
        starFlare.radius = ReachCurves.STAR_FLARE_RADIUS.at(c) * unit
        STAR_FLARE_COLOR.copyInto(starFlare.color)
        starFlare.brightness = lerp(STAR_FLARE_MIN, STAR_FLARE_MAX, noise(time * 1.5f, salt)) * fade

        light.active = true
        light.radius = CHARGE_LIGHT_END * unit
        light.lightStart = CHARGE_LIGHT_START / CHARGE_LIGHT_END
        lerpColor(light.color, CHARGE_LIGHT_DARK, CHARGE_LIGHT_BRIGHT, noise(time * 7f, salt + 7))
        light.intensity = ReachCurves.CHARGE_LIGHT_INTENSITY.at(c) / 500f * LIGHT_GLOW
        light.alpha = fade
    }

    // --- Open and collapse (slipspace_event) --------------------------------------------------

    private fun open() {
        val t = min(phaseTime, COLLAPSE_AT)
        spheres(t)
        swallowCore()
        ruptureLight(t)
        chargeExtras(1f, 1f - ramp(phaseTime, 0f, CHARGE_FADE))
    }

    private fun collapse(dt: Float) {
        val t = COLLAPSE_AT + phaseTime
        spheres(t)
        ruptureLight(t)
        if (!sparksSpawned) {
            sparksSpawned = true
            repeat(count(150f)) { spawnSpark() }
        }
        if (t >= BURST_AT) burst(dt, t - BURST_AT)
        collapseFlare(t)
        if (t >= BURST_AT + SHELL_LIFE && sprites.isEmpty()) switchTo(Phase.DONE)
    }

    /** The three spheres of slipspace_event's "spheres" event at event time [t]. */
    private fun spheres(t: Float) {
        val x = t / SPHERE_LIFE
        if (x >= 1f) return

        glow.active = true
        glow.radius = SPHERE_MESH_RADIUS * ReachCurves.GLOW_SIZE.at(x) * ReachCurves.GLOW_SCALE.at(x) * unit
        ReachCurves.GLOW_COLOR.at(x, glow.color)
        glow.intensity = ReachCurves.GLOW_INTENSITY.at(x)
        glow.alpha = 1f
        glow.halo = GLOW_HALO
        glow.haloWidth = GLOW_HALO_WIDTH

        // Growth and fade-in delayed behind the white; the min keeps the collapse on time.
        val xb = max(t - FleetSlipspaceJump.blackDelay.value, 0f) / SPHERE_LIFE
        black.active = true
        black.radius = SPHERE_MESH_RADIUS * BODY_SIZE * min(ReachCurves.BLACK_SCALE.at(xb), ReachCurves.BLACK_SCALE.at(x)) * unit
        black.alpha = min(ReachCurves.BLACK_ALPHA.at(xb), ReachCurves.BLACK_ALPHA.at(x))
        black.angleFadeCutoff = BLACK_ANGLE_FADE_CUTOFF
        black.angleFadeRange = BLACK_ANGLE_FADE_RANGE
        for (i in 0..2) black.underHdr[i] = glow.color[i] * glow.intensity
        black.underRadius = if (black.radius > 0f) glow.radius / black.radius else 0f

        web.active = true
        web.radius = SPHERE_MESH_RADIUS * BODY_SIZE * ReachCurves.WEB_SCALE.at(x) * unit
        web.intensity = 1f
        web.alpha = 1f
    }

    private fun ruptureLight(t: Float) {
        val u = (t - RUPTURE_LIGHT_AT) / RUPTURE_LIGHT_DURATION
        if (u < 0f || u >= 1f) return
        light.active = true
        light.radius = RUPTURE_LIGHT_END * unit
        light.lightStart = RUPTURE_LIGHT_START / RUPTURE_LIGHT_END
        RUPTURE_LIGHT_COLOR.copyInto(light.color)
        light.intensity = ReachCurves.RUPTURE_LIGHT_INTENSITY.at(u) / 3000f * LIGHT_GLOW
        light.alpha = 1f
    }

    /** slipspace_event's "collapse_burst", [b] seconds in. */
    private fun burst(dt: Float, b: Float) {
        if (!burstStarted) {
            burstStarted = true
            repeat(count(10f)) { spawnBurstPlasma() }
            repeat(count(50f)) { spawnFlame() }
            repeat(count(120f)) { spawnTwinkle(linger = false) }
            repeat(count(30f)) { spawnSmoke() }
            spawnHaze()
        }

        val a = b / BURST_EMIT_DURATION
        if (a < 1f) {
            val density = particleDensity()
            burstPlasmaAccumulator += 7f * density * dt
            while (burstPlasmaAccumulator >= 1f) {
                burstPlasmaAccumulator -= 1f
                spawnBurstPlasma()
            }
            flameAccumulator += 15f * density * dt
            while (flameAccumulator >= 1f) {
                flameAccumulator -= 1f
                spawnFlame()
            }
            emitTwinkles(dt, a, density)
        }

        val s = b / SHELL_LIFE
        if (s < 1f) {
            val remnant = FleetSlipspaceJump.remnantScale.value
            val fade = 1f - ramp(s, SHELL_FADE_FROM, 1f)

            shell.active = true
            shell.radius = SHELL_MESH_RADIUS * SHELL_SIZE * ReachCurves.BLAST_SCALE.at(s) * unit * remnant
            shell.intensity = 1f
            shell.alpha = ReachCurves.BLAST_ALPHA.at(s) * fade

            plasmaShell.active = true
            plasmaShell.radius = SHELL_MESH_RADIUS * SHELL_SIZE * ReachCurves.PLASMA_SHELL_SCALE.at(s) * unit * remnant
            plasmaShell.intensity = 1f
            plasmaShell.alpha = PLASMA_SHELL_ALPHA * fade
        }
    }

    private fun emitTwinkles(dt: Float, a: Float, density: Float) {
        twinkleAccumulator += ReachCurves.TWINKLE_RATE.at(a) * density * dt
        while (twinkleAccumulator >= 1f) {
            twinkleAccumulator -= 1f
            spawnTwinkle(linger = false)
        }
        if (kind == Kind.EXIT) return
        lingerAccumulator += ReachCurves.TWINKLE_LINGER_RATE.at(a) * density * dt
        while (lingerAccumulator >= 1f) {
            lingerAccumulator -= 1f
            spawnTwinkle(linger = true)
        }
    }

    /** slip_space_collapse.lens_flare: 1 s from 2.6 s, then a short fade (Reach cut it off). */
    private fun collapseFlare(t: Float) {
        val u = (t - COLLAPSE_FLARE_AT) / COLLAPSE_FLARE_DURATION
        val tailEnd = 1f + COLLAPSE_FLARE_TAIL / COLLAPSE_FLARE_DURATION
        if (u < 0f || u >= tailEnd) return
        val clamped = min(u, 1f)
        collapseFlare.active = true
        collapseFlare.radius = ReachCurves.COLLAPSE_FLARE_RADIUS.at(clamped) * unit * FleetSlipspaceJump.remnantScale.value
        ReachCurves.COLLAPSE_FLARE_COLOR.at(clamped, collapseFlare.color)
        collapseFlare.brightness = ReachCurves.COLLAPSE_FLARE_BRIGHTNESS.at(clamped) * (1f - ramp(u, 1f, tailEnd))
    }

    // --- Exit flash ---------------------------------------------------------------------------

    private fun flash(dt: Float) {
        // The seed's ring, flares and glow fade out first; the flash's own star flare overrides.
        chargeExtras(1f, 1f - ramp(phaseTime, 0f, CHARGE_FADE))

        val dissolve = FleetSlipspaceJump.dissolveDuration.value.coerceAtLeast(0.05f)
        val x = min(phaseTime / EXIT_EXPAND, 1f) * EXIT_GLOW_SPAN / SPHERE_LIFE
        val alpha = 1f - ramp(phaseTime, EXIT_EXPAND, EXIT_EXPAND + dissolve)
        if (alpha > 0f) {
            glow.active = true
            glow.radius = SPHERE_MESH_RADIUS * ReachCurves.GLOW_SIZE.at(x) * ReachCurves.GLOW_SCALE.at(x) * unit
            ReachCurves.GLOW_COLOR.at(x, glow.color)
            glow.intensity = ReachCurves.GLOW_INTENSITY.at(x)
            glow.alpha = alpha
            glow.halo = GLOW_HALO
            glow.haloWidth = GLOW_HALO_WIDTH
        }
        swallowCore()

        if (!exitTwinklesStarted) {
            exitTwinklesStarted = true
            repeat(count(120f)) { spawnTwinkle(linger = false) }
        }
        val a = phaseTime / BURST_EMIT_DURATION
        if (a < 1f) emitTwinkles(dt, a, particleDensity())

        val u = phaseTime / EXIT_FLARE_DURATION
        if (u < 1f) {
            starFlare.active = true
            starFlare.radius = ReachCurves.ARM_FLARE_RADIUS.at(u) * unit
            STAR_FLARE_COLOR.copyInto(starFlare.color)
            starFlare.brightness = EXIT_STAR_BRIGHTNESS * ReachCurves.ARM_FLARE_BRIGHTNESS.at(u) / 1.5f * (1f - ramp(u, 0.5f, 1f))
        }

        if (phaseTime >= EXIT_EXPAND + dissolve && sprites.isEmpty()) switchTo(Phase.DONE)
    }

    // --- Blink portal (ODST slipspace_sphere build) -------------------------------------------

    /** The build: every part scales linearly with age, as the ODST particles do over their life. */
    private fun build() {
        openness = ramp(phaseTime, 0f, blinkBuild)
        blinkSpheres(openness, 1f)
    }

    private fun closing() {
        val t = ramp(phaseTime, 0f, BLINK_CLOSE)
        val shrink = (1f - t) * (1f - t)
        blinkSpheres(openness * shrink, 1f - t)
        if (t >= 1f) switchTo(Phase.DONE)
    }

    private fun blinkSpheres(scale: Float, fade: Float) {
        if (scale <= 0.001f || fade <= 0f) return
        val r = radiusWorld * scale

        // glow_soft (tint #243ad5, intensity 1..4) as a soft light around the portal
        light.active = true
        light.radius = r * BLINK_LIGHT_RADIUS
        light.lightStart = 0.3f
        ODST_SOFT_GLOW.copyInto(light.color)
        light.intensity = BLINK_LIGHT_INTENSITY * (0.85f + 0.15f * noise(time * 5f, salt + 3))
        light.alpha = fade

        // slipspace_sphere + slipspace_sphere_glow (#0e31f9, in front of and behind the white core)
        glow.active = true
        glow.radius = r
        lerpColor(glow.color, ODST_GLOW_COLOR, WHITE, 0.2f)
        glow.intensity = BLINK_GLOW_INTENSITY
        glow.alpha = fade
        glow.halo = BLINK_GLOW_HALO
        glow.haloWidth = BLINK_GLOW_HALO_WIDTH

        // slipspace_sphere_origin (#2e30ff): the bright centre
        origin.active = true
        origin.radius = r * BLINK_ORIGIN_RADIUS
        lerpColor(origin.color, ODST_ORIGIN_COLOR, WHITE, 0.5f)
        origin.intensity = BLINK_ORIGIN_INTENSITY
        origin.alpha = fade
        origin.halo = 0.8f
        origin.haloWidth = 0.5f

        // slipspace_sphere_outer_plasma: plasma_clouds01/02 through the plasma palette on the outer
        // sphere (the web look), tinted white -> blue over the build, intensity 1 -> 3
        web.active = true
        web.radius = r * BLINK_PLASMA_RADIUS
        lerpColor(web.color, WHITE, ODST_PLASMA_TINT, scale)
        web.intensity = 1f + 2f * scale
        web.alpha = fade

        // slipspace_build.lens_flare: blue, 700 -> 200 radius over the build
        starFlare.active = true
        starFlare.radius = r * lerp(BLINK_FLARE_RADIUS * 1.5f, BLINK_FLARE_RADIUS, scale)
        ODST_FLARE_COLOR.copyInto(starFlare.color)
        starFlare.brightness = BLINK_FLARE_BRIGHTNESS * fade * (0.85f + 0.15f * noise(time * 6f, salt))
    }

    private fun aborting() {
        master = 1f - ramp(phaseTime, 0f, ABORT_DURATION)
        if (phaseTime >= ABORT_DURATION) {
            sprites.clear()
            switchTo(Phase.DONE)
        }
    }

    // --- Spawning -----------------------------------------------------------------------------

    /** Dev tunable (`FleetJumpTune PARTICLE_DENSITY`) times the player's setting. */
    private fun particleDensity(): Float = FleetSlipspaceJump.particleDensity.value * CovenantSettings.particleDensity

    private fun count(n: Float): Int = max(0, (n * particleDensity()).roundToInt())

    private fun rnd(): Float = random.nextFloat()

    private fun rnd(min: Float, max: Float): Float = lerp(min, max, random.nextFloat())

    /**
     * A point of Reach's "globe" emitter (radius [minRadius]..[maxRadius] Reach units) projected
     * onto the screen plane: scratch[0..1] = offset in world units, scratch[2..3] = the projected
     * direction of the 3D unit vector (length <= 1, used for velocities).
     */
    private fun globe(minRadius: Float, maxRadius: Float) {
        val z = rnd() * 2f - 1f
        val phi = rnd() * 2f * PI.toFloat()
        val planar = sqrt(max(0f, 1f - z * z))
        val dirX = planar * cos(phi)
        val dirY = planar * sin(phi)
        val r = rnd(minRadius, maxRadius) * unit
        scratch[0] = dirX * r
        scratch[1] = dirY * r
        scratch[2] = dirX
        scratch[3] = dirY
    }

    private fun place(s: RuptureSprite, speedReach: Float) {
        s.x = scratch[0]
        s.y = scratch[1]
        s.vx = scratch[2] * speedReach * unit
        s.vy = scratch[3] * speedReach * unit
        sprites.add(s)
    }

    private fun spawnCharge() {
        val s = RuptureSprite(SpriteKind.CHARGE)
        globe(0f, 10f)
        s.life = rnd(0.2f, 0.5f)
        s.random = rnd()
        s.random2 = rnd()
        s.noiseOffset = rnd() * 7f
        place(s, 0f)
    }

    private fun spawnDistortion() {
        val s = RuptureSprite(SpriteKind.DISTORTION)
        globe(0f, 10f)
        val r = rnd()
        s.life = lerp(1.5f, 0.8f, r)
        s.random = r
        place(s, 0f)
    }

    private fun spawnSpark() {
        val s = RuptureSprite(SpriteKind.SPARK)
        globe(600f, 600f)
        s.life = 0.8f
        val inward = atan2(-scratch[1], -scratch[0])
        s.angle = if (scratch[0] * scratch[0] + scratch[1] * scratch[1] > 1e-4f) inward else rnd() * 2f * PI.toFloat()
        place(s, 0f)
    }

    private fun spawnBurstPlasma() {
        val s = RuptureSprite(SpriteKind.BURST_PLASMA)
        globe(0f, 100f)
        s.life = 8f
        s.baseSize = rnd(10f, 13f)
        s.baseAlpha = rnd(0f, 0.65f)
        s.random2 = rnd()
        s.noiseOffset = rnd() * 7f
        place(s, rnd(50f, 200f) * FleetSlipspaceJump.remnantScale.value)
    }

    private fun spawnFlame() {
        val s = RuptureSprite(SpriteKind.PLASMA_FLAME)
        val r = rnd(50f, 75f)
        globe(r, r)
        s.life = rnd(6f, 8f)
        s.baseSize = rnd(5f, 8f)
        s.baseAlpha = rnd(0.5f, 0.75f)
        s.random = rnd()
        s.random2 = rnd()
        s.noiseOffset = rnd() * 7f
        place(s, rnd(10f, 70f) * FleetSlipspaceJump.remnantScale.value)
        s.angle = atan2(s.vy, s.vx)
    }

    private fun spawnTwinkle(linger: Boolean) {
        val s = RuptureSprite(SpriteKind.TWINKLE)
        if (linger) globe(0f, 500f) else globe(0f, 100f)
        s.life = rnd(10f, 15f)
        s.random = rnd()
        s.random2 = rnd()
        s.frame = random.nextInt(4)
        s.phase = rnd() * 2f * PI.toFloat()
        s.drag = TWINKLE_DRAG
        place(s, if (linger) rnd(0f, 50f) else rnd(100f, 500f))
    }

    private fun spawnSmoke() {
        val s = RuptureSprite(SpriteKind.SMOKE)
        globe(0f, 75f)
        s.life = rnd(2f, 2.5f)
        s.baseSize = rnd(7f, 10f)
        place(s, rnd(50f, 250f) * FleetSlipspaceJump.remnantScale.value)
    }

    private fun spawnHaze() {
        val s = RuptureSprite(SpriteKind.HAZE)
        globe(0f, 0f)
        s.life = 6f
        s.baseSize = 110f
        place(s, 0f)
    }

    // --- Per-frame sprite values --------------------------------------------------------------

    private fun refreshSprites() {
        val spriteScale = FleetSlipspaceJump.spriteScale.value
        val remnant = FleetSlipspaceJump.remnantScale.value
        val c = chargeLevel.coerceIn(0f, 1f)
        for (s in sprites) {
            val x = (s.age / s.life).coerceIn(0f, 1f)
            when (s.kind) {
                SpriteKind.CHARGE -> {
                    val size = lerp(ReachCurves.CHARGE_SIZE_R0.at(c), ReachCurves.CHARGE_SIZE_R1.at(c), s.random) *
                            ReachCurves.CHARGE_SCALE.at(x)
                    square(s, size * spriteScale)
                    fill(s.color, CHARGE_INTENSITY)
                    s.alpha = ReachCurves.CHARGE_ALPHA.at(x)
                    s.blackPoint = ReachCurves.CHARGE_BLACK_POINT.at(x)
                    s.paletteV = s.random2
                    s.uvScale = lerp(0.75f, 1.25f, x)
                }
                SpriteKind.DISTORTION -> {
                    val size = lerp(ReachCurves.DISTORTION_SIZE_R0.at(c), ReachCurves.DISTORTION_SIZE_R1.at(c), s.random) *
                            ReachCurves.DISTORTION_SCALE.at(x)
                    square(s, size)
                    s.alpha = ReachCurves.DISTORTION_ALPHA.at(x)
                }
                SpriteKind.SPARK -> {
                    val size = ReachCurves.SPARK_SIZE.at(x) * spriteScale
                    s.halfWidth = 0.5f * size * unit
                    s.halfLength = s.halfWidth * SPARK_STRETCH
                    // Reach centre offset -1: the streak runs from the spark inward
                    s.offsetAlong = s.halfLength
                    for (i in 0 until 3) s.color[i] = SPARK_COLOR[i] * SPARK_INTENSITY
                    s.alpha = ReachCurves.SPARK_ALPHA.at(x)
                }
                SpriteKind.BURST_PLASMA -> {
                    square(s, s.baseSize * ReachCurves.BURST_PLASMA_SCALE.at(x) * spriteScale)
                    fill(s.color, BURST_PLASMA_INTENSITY)
                    s.alpha = s.baseAlpha
                    s.blackPoint = ReachCurves.BURST_PLASMA_BLACK_POINT.at(x)
                    s.paletteV = s.random2
                    s.uvScale = lerp(0.75f, 1.25f, x)
                }
                SpriteKind.PLASMA_FLAME -> {
                    val size = s.baseSize * ReachCurves.PLASMA_VERTICAL_SCALE.at(x) * spriteScale
                    s.halfWidth = 0.5f * size * unit
                    s.halfLength = s.halfWidth * FLAME_ASPECT
                    lerpColor(s.color, FLAME_COLOR_A, FLAME_COLOR_B, s.random2)
                    s.alpha = s.baseAlpha * ReachCurves.PLASMA_VERTICAL_ALPHA.at(x)
                    s.paletteV = s.random
                    s.uvScale = 1f
                }
                SpriteKind.TWINKLE -> {
                    square(s, ReachCurves.TWINKLE_SIZE.at(s.random) * ReachCurves.TWINKLE_SCALE.at(x) * spriteScale)
                    val flicker = 0.5f + 0.5f * cos(2f * PI.toFloat() * 8f * s.age + s.phase)
                    lerpColor(s.color, TWINKLE_COLOR_A, TWINKLE_COLOR_B, s.random2)
                    val intensity = lerp(TWINKLE_INTENSITY_MIN, TWINKLE_INTENSITY_MAX, flicker)
                    for (i in 0 until 3) s.color[i] *= intensity
                    s.alpha = 1f
                }
                SpriteKind.SMOKE -> {
                    square(s, s.baseSize * ReachCurves.SMOKE_SCALE.at(x) * spriteScale)
                    ReachCurves.SMOKE_TINT.at(x, s.color)
                    s.alpha = ReachCurves.SMOKE_ALPHA.at(x)
                    s.blackPoint = 0f
                }
                SpriteKind.HAZE -> {
                    square(s, s.baseSize * ReachCurves.HAZE_SCALE.at(x) * remnant)
                    ReachCurves.HAZE_TINT.at(x, s.color)
                    s.alpha = HAZE_ALPHA
                    s.blackPoint = 0f
                }
                SpriteKind.ARK_RING, SpriteKind.ARK_FLASH, SpriteKind.ARK_CLOSE, SpriteKind.ARK_DISTORTION,
                SpriteKind.ARK_RING_BITMAP, SpriteKind.ARK_RING2, SpriteKind.ARK_RIM, SpriteKind.ARK_SHOCKWAVE,
                SpriteKind.ARK_REMNANT, SpriteKind.ARK_REFRACT, SpriteKind.ARK_RIPPLE, SpriteKind.ARK_FLASH_HALO,
                SpriteKind.ARK_SPARK, SpriteKind.ARK_TWINKLE, SpriteKind.ARK_HAZE, SpriteKind.ARK_STREAK,
                SpriteKind.ARK_WINDOW ->
                    ark?.refresh(s, x)
                // Torpedo sprites (utils/render/torpedo) never live in a rupture
                else -> {}
            }
        }
    }

    /** Square sprite of [sizeReach] Reach units across. */
    private fun square(s: RuptureSprite, sizeReach: Float) {
        s.halfWidth = 0.5f * sizeReach * unit
        s.halfLength = s.halfWidth
    }

    private fun fill(color: FloatArray, value: Float) {
        color[0] = value
        color[1] = value
        color[2] = value
    }
}

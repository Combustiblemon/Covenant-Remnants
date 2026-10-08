package org.combustiblelemon.covenantremnants.utils.render.rupture

import org.combustiblelemon.covenantremnants.campaign.abilities.SlipspaceJumpRules
import java.util.Random
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Which Halo 3 Ark exit plays: the cruisers' 1.5 s rupture or the carrier's 3.75 s one. */
enum class ArkVariant { CRUISER, CARRIER }

/**
 * The Halo 3 "The Ark" slipspace exit (H3EK `fx\cinematics\070la_waypoint_arrival\01\
 * slipspace_rupture.effect` and `slipspace_rupture_carrier.effect`), as a [RuptureEffect] of kind
 * ARK, with the 2026-10-08 visual pass on top (planning/implementations/ark-portal-visuals-pass.md).
 * Decoded data and the mapping onto the top-down view: planning/implementations/ark-slipspace-portals.md.
 * Every number is a [SlipspaceJumpRules] tunable (placeholders until the tuning pass).
 *
 * Burst (all from the mouth, axis = the effect's facing; `burstTime` runs from −ARK_PREROLL):
 * - pre-roll (burstTime < 0): the mouth glow grows, heat-haze ripples gather, twinkles converge,
 *   the rim light forms; nothing of Halo's effect plays yet;
 * - opening (burstTime = 0): the owner's [RuptureEffect.onArkOpen] (sound), the Halo distortion
 *   ring plus a thin refraction front, a brief black-body dim in the mouth, a spark burst,
 *   carrier: lens flare + star flare + anamorphic streak;
 * - tube: the funnel mesh ([ArkTubeMesh]) scaled 0.75→1.75 (cruiser) / 2.75→1.75 (carrier) over
 *   the life, spinning about its axis, streaks flowing out of the mouth, wall lightning, tint
 *   #3155ff × 5 graded white-hot at the mouth / violet deep inside, additive;
 * - rings: the Halo ring (glow_hard, size late(0.3→0.005): holds, then collapses, tint #3175ff) as
 *   an analytic ring with a white core and chromatic fringe, a second thinner ring collapsing
 *   faster, a rim light pinned to the mouth lip;
 * - flashes: `flash_soft` at 10/s, 0.35 s each, randomly rotated / mirrored, tint #333fff, each
 *   with a soft halo and glitter; sparks stream out of the mouth for a moment;
 * - carrier only: at 3.65 s a snap-closed flash plus a second distortion ring, a shockwave ring
 *   and a refraction pulse;
 * - afterglow: haze puffs, the light pool fading, lingering twinkles; carrier: a faint remnant ring.
 *
 * Idle (standing pocket portals; invented, placeholder): a dim held tube and rings that breathe,
 * a slow streak flow, a pulsing rim, occasional lightning bursts, twinkles drifting into the
 * mouth, occasional flicker flashes; fades back in after a burst. Everything advances in
 * [advance] only, so the game pause freezes it.
 */
internal class ArkTimeline(private val effect: RuptureEffect, private val persistent: Boolean) {
    private companion object {
        // Tints from the effect tags (particle tint curves, first colour).
        val TUBE_TINT = floatArrayOf(49f / 255f, 85f / 255f, 1f)      // #3155ff
        val RING_TINT = floatArrayOf(49f / 255f, 117f / 255f, 1f)     // #3175ff
        val FLASH_TINT = floatArrayOf(51f / 255f, 63f / 255f, 1f)     // #333fff
        val LENS_COLOR = floatArrayOf(0.192157f, 0.333333f, 1f)       // slipspace_rupture_carrier.lens_flare
        val WHITE = floatArrayOf(1f, 1f, 1f)
        // Invented: sparks and twinkles (near-white blue, like the Reach rupture's sparks).
        val SPARK_TINT = floatArrayOf(0.72f, 0.75f, 1f)
        val TWINKLE_TINT = floatArrayOf(0.85f, 0.9f, 1f)

        /** Minimum ellipse aspect so an edge-on ring still reads. */
        const val MIN_ASPECT = 0.1f

        /** Analytic ring line radius in sprite-local units (room for the fringe outside it). */
        const val RING_LINE = 0.75f

        fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

        fun ramp(t: Float, start: Float, end: Float): Float =
            if (end <= start) (if (t >= end) 1f else 0f) else ((t - start) / (end - start)).coerceIn(0f, 1f)

        fun smooth(edge0: Float, edge1: Float, x: Float): Float {
            val t = ramp(x, edge0, edge1)
            return t * t * (3f - 2f * t)
        }

        fun hash(n: Int): Float {
            var h = n * 374761393 + 668265263
            h = (h xor (h ushr 13)) * 1274126177
            return ((h xor (h ushr 16)) and 0x7fffffff) / 2147483647f
        }

        /** Smooth value noise 0..1 over time. */
        fun noise(t: Float, salt: Int): Float {
            val i = floor(t).toInt()
            val f = t - i
            val u = f * f * (3f - 2f * f)
            return lerp(hash(i * 31 + salt), hash((i + 1) * 31 + salt), u)
        }

        /**
         * Halo's "transition" mapping with easing handles: a cubic bezier from (0,0) through
         * (0,0) and (ARK_LATE_X, ARK_LATE_Y) to (1,1), solved for x. Mirrored by default so the
         * value holds and then drops late (the ring collapse); `ARK_LATE_MIRROR 0` flips it.
         */
        fun late(x: Float): Float {
            val xin = x.coerceIn(0f, 1f)
            val mirror = SlipspaceJumpRules.arkLateMirror.isOn
            val v = bezier(if (mirror) 1f - xin else xin)
            return if (mirror) 1f - v else v
        }

        private fun bezier(x: Float): Float {
            val x2 = SlipspaceJumpRules.arkLateX.value
            val y2 = SlipspaceJumpRules.arkLateY.value
            // P0 = (0,0), P1 = (0,0), P2 = (x2,y2), P3 = (1,1); bisection on t for the given x.
            var lo = 0f
            var hi = 1f
            repeat(16) {
                val t = (lo + hi) * 0.5f
                val bx = 3f * (1f - t) * t * t * x2 + t * t * t
                if (bx < x) lo = t else hi = t
            }
            val t = (lo + hi) * 0.5f
            return 3f * (1f - t) * t * t * y2 + t * t * t
        }
    }

    private val random = Random()
    private val salt = random.nextInt(1000)
    private val rules = SlipspaceJumpRules

    var variant = ArkVariant.CRUISER
        private set

    /** True while a burst plays (pre-roll, life, tail and afterglow). */
    var bursting = false
        private set

    /** Burst clock: −ARK_PREROLL at [burst], 0 at the opening. */
    private var burstTime = 0f
    private var burstRadius = effect.radiusWorld
    private var spin = 0f
    private var time = 0f
    private var idleFade = if (persistent) 1f else 0f
    private var flashAccumulator = 0f
    private var idleFlashAccumulator = 0f
    private var idleTwinkleAccumulator = 0f
    private var sparkAccumulator = 0f
    private var rippleAccumulator = 0f
    private var lensRadiusUnits = 6f
    private var opened = false
    private var closeSpawned = false
    private var prerollTwinklesSpawned = false
    private var afterglowSpawned = false

    /** CPU-integrated tube phases: streak scroll and lightning crawl (the portal renderer's clock never advances). */
    private var flow = 0f
    private var arcPhase = 0f

    /** The held sprites (persistent in the effect's sprite list while shown). */
    private val ringAnalytic = RuptureSprite(SpriteKind.ARK_RING).apply { life = Float.MAX_VALUE }
    private val ringBitmap = RuptureSprite(SpriteKind.ARK_RING_BITMAP).apply { life = Float.MAX_VALUE }
    private val ring2 = RuptureSprite(SpriteKind.ARK_RING2).apply { life = Float.MAX_VALUE }
    private val rim = RuptureSprite(SpriteKind.ARK_RIM).apply { life = Float.MAX_VALUE }
    private val held = listOf(ringAnalytic, ringBitmap, ring2, rim)

    private val scratch = FloatArray(4)

    // --- Control ----------------------------------------------------------------------------------

    /** Plays a burst of [variant] scaled for a fleet of radius [radiusWorld] (the effect's own radius by default). */
    fun burst(variant: ArkVariant, radiusWorld: Float = effect.radiusWorld) {
        this.variant = variant
        burstRadius = radiusWorld
        bursting = true
        burstTime = -max(rules.arkPreroll.value, 0f)
        opened = false
        closeSpawned = false
        prerollTwinklesSpawned = false
        afterglowSpawned = false
        flashAccumulator = 1f
        sparkAccumulator = 0f
        rippleAccumulator = 0f
        lensRadiusUnits = lerp(rules.arkLensRadiusMin.value, rules.arkLensRadiusMax.value, random.nextFloat())
        if (burstTime >= 0f) open()
    }

    private fun seconds(): Float = rules.arkSeconds(variant)

    private fun preroll(): Float = max(rules.arkPreroll.value, 0f)

    /** Exposure multiplier on every Ark colour. */
    private fun hdr(k: Float): Float = k * rules.arkExposureMult.value

    // --- Timeline -----------------------------------------------------------------------------------

    /** Advances; returns true when a one-shot effect has finished (its sprites gone). */
    fun advance(dt: Float): Boolean {
        time += dt
        if (bursting) {
            burstTime += dt
            if (burstTime < 0f) {
                advancePreroll(dt)
            } else {
                if (!opened) open()
                advanceBurst(dt)
            }
            val end = seconds() + rules.arkTail.value
            if (persistent) {
                if (burstTime >= end && closeSettled()) bursting = false
                return false
            }
            val over = burstTime >= end + rules.arkAfterglow.value
            if (over && closeSettled() && effect.sprites.all { it in held }) {
                bursting = false
                effect.sprites.removeAll(held)
                return true
            }
            return false
        }
        if (persistent) advanceIdle(dt)
        return false
    }

    private fun closeSettled(): Boolean {
        if (variant != ArkVariant.CARRIER) return true
        return burstTime >= rules.arkCloseAt.value + max(rules.arkCloseDistortionSeconds.value, rules.arkShockSeconds.value)
    }

    // --- Geometry helpers -----------------------------------------------------------------------------

    /** World units per Halo unit for a burst (or idle) of radius [radius]: the full cruiser tube mouth = radius × ARK_MOUTH_RADII. */
    private fun unit(radius: Float): Float =
        radius * rules.arkMouthRadii.value / (rules.arkTubeScale1Cruiser.value * ArkTubeMesh.MOUTH_RADIUS)

    /** Clamped ellipse aspect for Halo's "perpendicular to the direction" sprites. */
    private fun aspect(): Float = max(mouthAspect(), MIN_ASPECT)

    /** The exact aspect of the tube mouth (sin of the view tilt). */
    private fun mouthAspect(): Float = sin(Math.toRadians(rules.arkViewTilt.value.toDouble())).toFloat()

    /** World radius of the mouth at tube scale [scale] for Halo unit [u] (independent of the tube draw state). */
    private fun mouthRadius(u: Float, scale: Float): Float = u * scale * ArkTubeMesh.MOUTH_RADIUS

    /** World length of the tube at tube scale [scale]. */
    private fun tubeLength(u: Float, scale: Float): Float = u * scale * rules.arkTubeLengthMult.value * ArkTubeMesh.LENGTH

    private fun facingRadians(): Float = Math.toRadians(effect.facing.toDouble()).toFloat()

    /** Sign of the out-of-the-mouth direction along the facing (the tube sits behind the mouth unless flipped). */
    private fun outSign(): Float = if (rules.arkTubeFlip.isOn) -1f else 1f

    /**
     * A point on the mouth ellipse at angle [theta] (0 = across the facing) and radius [radius]:
     * scratch[0..1] = offset from the centre (world), scratch[2..3] = the unit direction out of
     * the mouth, bent by [spread] × the point's radial direction.
     */
    private fun mouthPoint(theta: Float, radius: Float, spread: Float) {
        val f = facingRadians()
        val fx = cos(f)
        val fy = sin(f)
        val nx = -fy
        val ny = fx
        val t = mouthAspect()
        val c = cos(theta)
        val s = sin(theta)
        scratch[0] = nx * radius * c + fx * radius * s * t
        scratch[1] = ny * radius * c + fy * radius * s * t
        val cosTilt = cos(Math.toRadians(rules.arkViewTilt.value.toDouble())).toFloat()
        var dx = fx * cosTilt * outSign() + (nx * c + fx * s * t) * spread
        var dy = fy * cosTilt * outSign() + (ny * c + fy * s * t) * spread
        val len = sqrt(dx * dx + dy * dy)
        if (len > 1e-4f) {
            dx /= len
            dy /= len
        }
        scratch[2] = dx
        scratch[3] = dy
    }

    private fun rnd(): Float = random.nextFloat()

    private fun rnd(min: Float, max: Float): Float = lerp(min, max, random.nextFloat())

    // --- Pre-roll ---------------------------------------------------------------------------------------

    /** burstTime < 0: the mouth charges up; p = 0 → 1 over the pre-roll. */
    private fun advancePreroll(dt: Float) {
        val pre = preroll()
        val p = (1f + burstTime / max(pre, 0.01f)).coerceIn(0f, 1f)
        val u = unit(burstRadius)
        val carrier = variant == ArkVariant.CARRIER
        val scale0 = if (carrier) rules.arkTubeScale0Carrier.value else rules.arkTubeScale0Cruiser.value
        val mouth = mouthRadius(u, scale0)

        // The idle look fades out as the charge builds (persistent portals).
        idleFade = min(idleFade, 1f - p)
        if (persistent && idleFade > 0f) setIdleLook(dt, idleFade)
        else {
            effect.tube.active = false
            effect.sprites.removeAll(held)
        }

        setGlow(mouth, rules.arkMouthGlowIntensity.value * rules.arkPrerollLight.value * p * p)
        setPool(u, scale0, rules.arkPoolIntensity.value * p)
        setRim(mouth, rules.arkRimIntensity.value * rules.arkPrerollRim.value * p, 1f)

        rippleAccumulator += rules.arkPrerollRippleRate.value * dt
        while (rippleAccumulator >= 1f) {
            rippleAccumulator -= 1f
            val s = RuptureSprite(SpriteKind.ARK_RIPPLE)
            s.life = max(-burstTime, 0.05f)
            s.baseSize = burstRadius * rules.arkPrerollGatherRadii.value
            effect.sprites.add(s)
        }
        if (!prerollTwinklesSpawned) {
            prerollTwinklesSpawned = true
            val count = rules.arkPrerollTwinkles.value.toInt()
            repeat(count) {
                val s = RuptureSprite(SpriteKind.ARK_TWINKLE)
                val angle = rnd() * 2f * PI.toFloat()
                val r = burstRadius * rules.arkPrerollGatherRadii.value * rnd(0.6f, 1f)
                s.x = cos(angle) * r
                s.y = sin(angle) * r
                val travel = max(pre, 0.05f)
                s.vx = -s.x / travel
                s.vy = -s.y / travel
                s.life = travel + 0.3f
                s.baseSize = burstRadius
                s.random = rnd()
                s.random2 = rnd()
                s.frame = random.nextInt(4)
                s.phase = rnd() * 2f * PI.toFloat()
                effect.sprites.add(s)
            }
        }
        effect.arkLens.active = false
        effect.arkStar.active = false
    }

    /** The opening (burstTime crosses 0): Halo's distortion ring, the refraction front, sparks, the carrier flare. */
    private fun open() {
        opened = true
        val carrier = variant == ArkVariant.CARRIER
        spawnDistortion(seconds(), fromClose = false, carrier = carrier)
        spawnRefract(rules.arkRefractSeconds.value)
        val u = unit(burstRadius)
        val scale0 = if (carrier) rules.arkTubeScale0Carrier.value else rules.arkTubeScale0Cruiser.value
        repeat(rules.arkSparkCount.value.toInt()) { spawnSpark(mouthRadius(u, scale0)) }
        if (carrier) spawnStreak(rules.arkLensSeconds.value * 2f)
        effect.onArkOpen?.invoke()
    }

    // --- Burst ----------------------------------------------------------------------------------------

    private fun advanceBurst(dt: Float) {
        val life = seconds()
        val x = (burstTime / life).coerceIn(0f, 1f)
        val tail = rules.arkTail.value
        val fade = 1f - ramp(burstTime, life, life + tail)
        val afterglow = rules.arkAfterglow.value
        val glowFade = 1f - ramp(burstTime, life + tail, life + tail + afterglow)
        val u = unit(burstRadius)
        val carrier = variant == ArkVariant.CARRIER

        // Tube
        val scale0 = if (carrier) rules.arkTubeScale0Carrier.value else rules.arkTubeScale0Cruiser.value
        val scale1 = if (carrier) rules.arkTubeScale1Carrier.value else rules.arkTubeScale1Cruiser.value
        val spin0 = if (carrier) rules.arkTubeSpin0Carrier.value else rules.arkTubeSpin0Cruiser.value
        val spin1 = if (carrier) rules.arkTubeSpin1Carrier.value else rules.arkTubeSpin1Cruiser.value
        val scale = lerp(scale0, scale1, x)
        spin += lerp(spin0, spin1, x) * 2f * PI.toFloat() * dt
        flow += rules.arkTubeFlow.value * dt
        arcPhase += rules.arkArcSpeed.value * dt
        setTube(u * scale, rules.arkTubeIntensity.value, fade, lerp(0f, rules.arkBlackPointEnd.value, x), rules.arkArcIntensity.value)
        val mouth = mouthRadius(u, scale)

        // Rings (glow_hard, perpendicular to the axis): main (holds, then collapses late), second (faster), rim
        val ringSize0 = if (carrier) rules.arkRingSizeCarrier.value else rules.arkRingSizeCruiser.value
        val ringIntensity = if (carrier) rules.arkRingIntensityCarrier.value else rules.arkRingIntensityCruiser.value
        val ringRadius = u * lerp(ringSize0, rules.arkRingSizeEnd.value, late(x))
        setRing(ringRadius, ringIntensity, fade)
        val ring2Radius = u * lerp(ringSize0, rules.arkRingSizeEnd.value, late(min(1f, x * rules.arkRing2Speed.value))) * rules.arkRing2Size.value
        setRing2(ring2Radius, ringIntensity * rules.arkRing2Intensity.value, fade)
        setRim(mouth, rules.arkRimIntensity.value, fade)

        // Light: inner mouth glow (ramps in as the dim fades), background pool, the opening dim
        val dimSeconds = max(rules.arkDimSeconds.value, 0.01f)
        val dimFade = 1f - ramp(burstTime, 0f, dimSeconds)
        setGlow(mouth, rules.arkMouthGlowIntensity.value * (0.3f + 0.7f * (1f - dimFade)) * glowFade)
        setPool(u, scale, rules.arkPoolIntensity.value * glowFade)
        setDim(mouth, rules.arkDimStrength.value * dimFade)

        // Flashes, 10/s for the whole life
        if (burstTime < life) {
            flashAccumulator += rules.arkFlareRate.value * dt
            while (flashAccumulator >= 1f) {
                flashAccumulator -= 1f
                spawnFlash(u, 1f, carrier, glitter = true)
            }
        }

        // Sparks stream out of the mouth for a moment after the opening
        if (burstTime < rules.arkSparkSeconds.value) {
            sparkAccumulator += rules.arkSparkRate.value * dt
            while (sparkAccumulator >= 1f) {
                sparkAccumulator -= 1f
                spawnSpark(mouth)
            }
        }

        // Carrier: lens + star flare at the start, snap-closed flash + distortion + shockwave at the end
        effect.arkLens.active = false
        effect.arkStar.active = false
        if (carrier) {
            val lensSeconds = rules.arkLensSeconds.value
            if (burstTime < lensSeconds) {
                val k = 1f - burstTime / lensSeconds
                effect.arkLens.active = true
                effect.arkLens.radius = lensRadiusUnits * u
                LENS_COLOR.copyInto(effect.arkLens.color)
                effect.arkLens.brightness = hdr(rules.arkLensBrightness.value * k)
                effect.arkStar.active = true
                effect.arkStar.radius = rules.arkStarRadius.value * u
                LENS_COLOR.copyInto(effect.arkStar.color)
                effect.arkStar.brightness = hdr(rules.arkStarBrightness.value * k)
            }
            if (!closeSpawned && burstTime >= rules.arkCloseAt.value) {
                closeSpawned = true
                val close = RuptureSprite(SpriteKind.ARK_CLOSE)
                close.life = rules.arkCloseFlashSeconds.value
                close.baseSize = u
                effect.sprites.add(close)
                spawnDistortion(rules.arkCloseDistortionSeconds.value, fromClose = true, carrier = true)
                spawnRefract(rules.arkRefractSeconds.value)
                val shock = RuptureSprite(SpriteKind.ARK_SHOCKWAVE)
                shock.life = max(rules.arkShockSeconds.value, 0.05f)
                shock.baseSize = burstRadius
                effect.sprites.add(shock)
            }
        }

        // Afterglow: haze puffs and (carrier) a faint remnant ring, once the life and tail are over
        if (!afterglowSpawned && burstTime >= life + tail) {
            afterglowSpawned = true
            repeat(rules.arkHazeCount.value.toInt()) {
                val s = RuptureSprite(SpriteKind.ARK_HAZE)
                s.life = max(afterglow, 0.1f) * rnd(0.7f, 1f)
                s.baseSize = burstRadius * rules.arkHazeSize.value * rnd(0.6f, 1f)
                val angle = rnd() * 2f * PI.toFloat()
                val r = mouth * rnd(0f, 0.6f)
                s.x = cos(angle) * r
                s.y = sin(angle) * r
                mouthPoint(0f, 0f, 0f)
                val drift = burstRadius * 0.15f / max(s.life, 0.1f)
                s.vx = scratch[2] * drift + (rnd() - 0.5f) * drift
                s.vy = scratch[3] * drift + (rnd() - 0.5f) * drift
                s.random = rnd()
                effect.sprites.add(s)
            }
            if (carrier) {
                val remnant = RuptureSprite(SpriteKind.ARK_REMNANT)
                remnant.life = max(rules.arkRemnantSeconds.value, 0.1f)
                remnant.baseSize = mouth
                effect.sprites.add(remnant)
            }
        }
    }

    // --- Idle -------------------------------------------------------------------------------------

    private fun advanceIdle(dt: Float) {
        idleFade = min(1f, idleFade + dt / max(rules.arkIdleFadeIn.value, 0.01f))
        setIdleLook(dt, idleFade)

        val u = unit(effect.radiusWorld)
        val scale = rules.arkTubeScale1Cruiser.value
        idleFlashAccumulator += rules.arkIdleFlashRate.value * dt
        while (idleFlashAccumulator >= 1f) {
            idleFlashAccumulator -= 1f
            spawnFlash(u, rules.arkIdleFlashScale.value, carrier = false, glitter = false)
        }
        idleTwinkleAccumulator += rules.arkIdleTwinkleRate.value * dt
        while (idleTwinkleAccumulator >= 1f) {
            idleTwinkleAccumulator -= 1f
            spawnIdleTwinkle(mouthRadius(u, scale))
        }
    }

    /** The standing portal's held look at opacity [alpha]: breathing tube, rings, rim, lights, flow, gated lightning. */
    private fun setIdleLook(dt: Float, alpha: Float) {
        val u = unit(effect.radiusWorld)
        val breath = 1f + rules.arkIdleBreath.value * (noise(time * 0.7f, salt) * 2f - 1f)
        spin += rules.arkIdleSpin.value * 2f * PI.toFloat() * dt
        flow += rules.arkIdleFlow.value * dt
        arcPhase += rules.arkArcSpeed.value * dt

        val scale = rules.arkTubeScale1Cruiser.value * breath
        val arcGate = rules.arkArcGate.value
        val arc = rules.arkIdleArcIntensity.value * smooth(arcGate, 1f, noise(time * rules.arkIdleArcRate.value, salt + 3))
        setTube(u * scale, rules.arkTubeIntensity.value * rules.arkIdleIntensity.value, alpha, rules.arkIdleBlackPoint.value, arc)
        val mouth = mouthRadius(u, scale)

        // Ring at the mouth's inner lip
        val ringSize = rules.arkRingSizeCruiser.value * (rules.arkTubeScale1Cruiser.value / max(rules.arkTubeScale0Cruiser.value, 0.01f)) *
            rules.arkIdleRing.value * breath
        setRing(u * ringSize, rules.arkRingIntensityCruiser.value * rules.arkIdleIntensity.value, alpha)
        effect.sprites.remove(ring2)
        val pulse = 1f + rules.arkIdleRimPulse.value * (noise(time * 1.3f, salt + 5) * 2f - 1f)
        setRim(mouth, rules.arkRimIntensity.value * rules.arkIdleRim.value * pulse, alpha)

        setGlow(mouth, rules.arkMouthGlowIntensity.value * rules.arkIdleIntensity.value * breath * alpha)
        setPool(u, scale, rules.arkPoolIntensity.value * rules.arkIdlePool.value * alpha)
        effect.arkDim.active = false
        effect.arkLens.active = false
        effect.arkStar.active = false
    }

    // --- Draw setters -------------------------------------------------------------------------------

    private fun setTube(meshUnit: Float, intensity: Float, alpha: Float, blackPoint: Float, arcIntensity: Float) {
        val tube = effect.tube
        if (alpha <= 0f || meshUnit <= 0f) {
            tube.active = false
            return
        }
        tube.active = true
        tube.across = meshUnit
        tube.along = meshUnit * rules.arkTubeLengthMult.value
        tube.spin = spin
        tube.tilt = Math.toRadians(rules.arkViewTilt.value.toDouble()).toFloat()
        tube.flip = rules.arkTubeFlip.isOn
        val k = hdr(intensity)
        for (i in 0 until 3) tube.color[i] = TUBE_TINT[i] * k
        rules.arkWhiteHot.into(tube.hotColor, k * rules.arkTubeHotIntensity.value)
        rules.arkVioletDeep.into(tube.deepColor, k * rules.arkTubeDeepIntensity.value)
        tube.alpha = alpha
        tube.blackPoint = blackPoint

        tube.bitmapMix = rules.arkTubeBitmapMix.value
        tube.streak[0] = rules.arkTubeStreakU.value
        tube.streak[1] = rules.arkTubeStreakV.value
        tube.streak[2] = rules.arkTubeStreakSharp.value
        tube.streak[3] = rules.arkTubeStreakGain.value
        tube.streakGain = rules.arkTubeStreakIntensity.value
        tube.flow = flow
        tube.hotSpan[0] = rules.arkTubeHotSpan.value
        tube.hotSpan[1] = rules.arkTubeVioletFrom.value
        tube.depthFade = rules.arkTubeDepthFade.value
        tube.backDim = rules.arkTubeBackDim.value
        tube.backMouth = rules.arkTubeBackMouth.value

        tube.arcIntensity = hdr(arcIntensity)
        tube.arcPhase = arcPhase
        tube.arcScale[0] = rules.arkArcU.value
        tube.arcScale[1] = rules.arkArcV.value
        tube.arcGate = rules.arkArcGate.value
        tube.arcReach = rules.arkArcReach.value
        tube.paletteV = rules.arkArcPaletteV.value
    }

    /** Writes the analytic ring style (shared by every ANALYTIC_RING sprite of this effect). */
    private fun writeRingStyle() {
        val style = effect.arkRingStyle
        val core = hdr(rules.arkRingCoreIntensity.value)
        style[0] = core
        style[1] = core
        style[2] = core
        style[3] = rules.arkRingFringeIn.r.value
        style[4] = rules.arkRingFringeIn.g.value
        style[5] = rules.arkRingFringeIn.b.value
        style[6] = rules.arkRingFringeOut.r.value
        style[7] = rules.arkRingFringeOut.g.value
        style[8] = rules.arkRingFringeOut.b.value
        style[9] = rules.arkRingBodyWidth.value
        style[10] = rules.arkRingFringeOffset.value
        style[11] = rules.arkRingFringeWidth.value
        style[12] = rules.arkRingFringeIntensity.value
    }

    /**
     * A held analytic ring [s] whose line sits at world radius [radius] (major axis) with ellipse
     * [aspect], core width [coreWidth] (× half-size), interior [fill], HDR [tint] × [intensity].
     */
    private fun setAnalyticRing(s: RuptureSprite, radius: Float, aspect: Float, coreWidth: Float, fill: Float, tint: FloatArray, intensity: Float, alpha: Float) {
        if (radius <= 0.01f || alpha <= 0f || intensity <= 0f) {
            effect.sprites.remove(s)
            return
        }
        if (!effect.sprites.contains(s)) effect.sprites.add(s)
        writeRingStyle()
        s.age = 0f
        s.halfWidth = radius / RING_LINE
        s.halfLength = s.halfWidth * aspect
        s.angle = facingRadians()
        val k = hdr(intensity)
        for (i in 0 until 3) s.color[i] = tint[i] * k
        s.alpha = alpha
        s.shape[0] = aspect
        s.shape[1] = RING_LINE
        s.shape[2] = coreWidth
        s.shape[3] = fill
    }

    /** Halo's ring: the glow_hard disc edge at [radius] (analytic, or the bitmap with `ARK_RING_BITMAP 1`). */
    private fun setRing(radius: Float, intensity: Float, alpha: Float) {
        val bitmap = rules.arkRingBitmap.isOn
        val other = if (bitmap) ringAnalytic else ringBitmap
        effect.sprites.remove(other)
        if (!bitmap) {
            setAnalyticRing(ringAnalytic, radius * rules.arkRingPeak.value, aspect(), rules.arkRingCoreWidth.value, rules.arkRingFill.value, RING_TINT, intensity, alpha)
            return
        }
        val ring = ringBitmap
        if (radius <= 0.01f || alpha <= 0f) {
            effect.sprites.remove(ring)
            return
        }
        if (!effect.sprites.contains(ring)) effect.sprites.add(ring)
        ring.age = 0f
        ring.halfWidth = radius
        ring.halfLength = radius * aspect()
        ring.angle = facingRadians()
        val k = hdr(intensity)
        for (i in 0 until 3) ring.color[i] = RING_TINT[i] * k
        ring.alpha = alpha
        ring.blackPoint = 0f
        ring.uvScale = 1f
        ring.noiseOffset = 0f
    }

    private fun setRing2(radius: Float, intensity: Float, alpha: Float) {
        setAnalyticRing(ring2, radius * rules.arkRingPeak.value, aspect(), rules.arkRingCoreWidth.value * rules.arkRing2Width.value, 0f, RING_TINT, intensity, alpha)
    }

    /** The rim light on the mouth lip (exact mouth ellipse). */
    private fun setRim(mouthRadius: Float, intensity: Float, alpha: Float) {
        setAnalyticRing(rim, mouthRadius, max(mouthAspect(), MIN_ASPECT), rules.arkRimWidth.value, 0f, TUBE_TINT, intensity, alpha)
    }

    /** The inner mouth glow: an elliptical LIGHT sphere over the mouth. */
    private fun setGlow(mouthRadius: Float, intensity: Float) {
        val g = effect.arkGlow
        if (intensity <= 0f || mouthRadius <= 0f) {
            g.active = false
            return
        }
        g.active = true
        g.radius = mouthRadius * rules.arkMouthGlowRadii.value
        g.aspect = max(mouthAspect(), MIN_ASPECT)
        g.axisAngle = facingRadians()
        g.offsetX = 0f
        g.offsetY = 0f
        TUBE_TINT.copyInto(g.color)
        g.intensity = hdr(intensity)
        g.alpha = 1f
        g.lightStart = 0.5f
    }

    /** The background light pool, shifted toward the tube body. */
    private fun setPool(u: Float, scale: Float, intensity: Float) {
        val p = effect.arkPool
        if (intensity <= 0f) {
            p.active = false
            return
        }
        p.active = true
        p.radius = burstRadiusOrIdle() * rules.arkPoolRadii.value
        p.aspect = 1f
        val f = facingRadians()
        val shift = -outSign() * rules.arkPoolOffset.value * tubeLength(u, scale)
        p.offsetX = cos(f) * shift
        p.offsetY = sin(f) * shift
        TUBE_TINT.copyInto(p.color)
        p.intensity = hdr(intensity)
        p.alpha = 1f
        p.lightStart = 0.3f
    }

    private fun burstRadiusOrIdle(): Float = if (bursting) burstRadius else effect.radiusWorld

    /** The opening's black-body dim: an elliptical BLACK sphere inside the mouth. */
    private fun setDim(mouthRadius: Float, strength: Float) {
        val d = effect.arkDim
        if (strength <= 0.001f || mouthRadius <= 0f) {
            d.active = false
            return
        }
        d.active = true
        d.radius = mouthRadius * rules.arkDimRadius.value
        d.aspect = max(mouthAspect(), MIN_ASPECT)
        d.axisAngle = facingRadians()
        d.offsetX = 0f
        d.offsetY = 0f
        d.alpha = strength.coerceIn(0f, 1f)
        d.angleFadeCutoff = 0f
        d.angleFadeRange = max(rules.arkDimSoft.value, 1f)
        d.underRadius = 0f
    }

    // --- Spawning ----------------------------------------------------------------------------------

    /** [carrier] picks the size / spin curves; pinned on the sprite so a restarted burst or the idle look cannot change it mid-flight. */
    private fun spawnFlash(u: Float, sizeMult: Float, carrier: Boolean, glitter: Boolean) {
        val s = RuptureSprite(SpriteKind.ARK_FLASH)
        s.life = rules.arkFlareLife.value
        s.baseSize = u * sizeMult
        s.random = if (random.nextBoolean()) 1f else -1f                                     // mirror
        s.phase = random.nextFloat() * 2f * PI.toFloat()                                    // start rotation
        val spinRange = if (carrier) rules.arkFlareSpinCarrier.value else rules.arkFlareSpinCruiser.value
        s.random2 = (random.nextFloat() * 2f - 1f) * spinRange                              // spin, turns/s
        s.baseAlpha = if (carrier) 1f else 0f                                               // size curve pick
        effect.sprites.add(s)

        val halo = RuptureSprite(SpriteKind.ARK_FLASH_HALO)
        halo.life = s.life
        halo.baseSize = s.baseSize
        halo.baseAlpha = s.baseAlpha
        effect.sprites.add(halo)

        if (glitter) {
            val n = rules.arkGlitterPerFlash.value
            var count = n.toInt()
            if (rnd() < n - count) count++
            repeat(count) { spawnGlitter(u * sizeMult) }
            if (carrier && rnd() < rules.arkStreakChance.value) spawnStreak(s.life)
        }
    }

    private fun spawnGlitter(u: Float) {
        val s = RuptureSprite(SpriteKind.ARK_TWINKLE)
        val scale = u * rules.arkTubeScale1Cruiser.value
        mouthPoint(rnd() * 2f * PI.toFloat(), mouthRadius(u, rules.arkTubeScale1Cruiser.value) * rnd(0f, 0.9f), 0.5f)
        s.x = scratch[0]
        s.y = scratch[1]
        val speed = scale * rnd(0.1f, 0.4f)
        s.vx = scratch[2] * speed
        s.vy = scratch[3] * speed
        s.drag = 1.5f
        s.life = rules.arkGlitterLife.value * rnd(0.6f, 1f)
        s.baseSize = burstRadiusOrIdle()
        s.random = rnd()
        s.random2 = rnd()
        s.frame = random.nextInt(4)
        s.phase = rnd() * 2f * PI.toFloat()
        effect.sprites.add(s)
    }

    private fun spawnIdleTwinkle(mouthRadius: Float) {
        val s = RuptureSprite(SpriteKind.ARK_TWINKLE)
        val angle = rnd() * 2f * PI.toFloat()
        val r = mouthRadius * rnd(1.5f, 2.5f)
        s.x = cos(angle) * r
        s.y = sin(angle) * r
        s.life = rules.arkGlitterLife.value * rnd(0.8f, 1.2f)
        s.vx = -s.x / s.life * 0.8f
        s.vy = -s.y / s.life * 0.8f
        s.baseSize = effect.radiusWorld
        s.random = rnd()
        s.random2 = rnd()
        s.frame = random.nextInt(4)
        s.phase = rnd() * 2f * PI.toFloat()
        effect.sprites.add(s)
    }

    private fun spawnSpark(mouthRadius: Float) {
        val s = RuptureSprite(SpriteKind.ARK_SPARK)
        mouthPoint(rnd() * 2f * PI.toFloat(), mouthRadius * rnd(0.3f, 1f), rules.arkSparkSpread.value * rnd())
        s.x = scratch[0]
        s.y = scratch[1]
        val speed = burstRadius * rules.arkSparkSpeed.value * rnd(0.6f, 1.2f)
        s.vx = scratch[2] * speed
        s.vy = scratch[3] * speed
        s.drag = rules.arkSparkDrag.value
        s.life = rules.arkSparkLife.value * rnd(0.6f, 1.2f)
        s.baseSize = burstRadius
        s.random = rnd()
        effect.sprites.add(s)
    }

    private fun spawnStreak(life: Float) {
        val s = RuptureSprite(SpriteKind.ARK_STREAK)
        s.life = max(life, 0.05f)
        s.baseSize = burstRadius
        s.random = rnd()
        effect.sprites.add(s)
    }

    private fun spawnDistortion(life: Float, fromClose: Boolean, carrier: Boolean) {
        val s = RuptureSprite(SpriteKind.ARK_DISTORTION)
        s.life = max(life, 0.05f)
        s.random = if (fromClose) 1f else 0f
        s.baseAlpha = if (carrier) 1f else 0f   // variant pinned at spawn
        s.baseSize = burstRadius
        effect.sprites.add(s)
    }

    private fun spawnRefract(life: Float) {
        val s = RuptureSprite(SpriteKind.ARK_REFRACT)
        s.life = max(life, 0.05f)
        s.baseSize = burstRadius
        effect.sprites.add(s)
    }

    // --- Per-frame sprite values (called from RuptureEffect.refreshSprites) -----------------------

    fun refresh(s: RuptureSprite, x: Float) {
        when (s.kind) {
            SpriteKind.ARK_RING, SpriteKind.ARK_RING_BITMAP, SpriteKind.ARK_RING2, SpriteKind.ARK_RIM -> {}  // held: set in advance
            SpriteKind.ARK_FLASH -> refreshFlash(s, x, 1f, 1f)
            SpriteKind.ARK_FLASH_HALO -> refreshFlash(s, x, rules.arkFlashHaloSize.value, rules.arkFlashHaloIntensity.value)
            SpriteKind.ARK_CLOSE -> {
                val radius = s.baseSize * lerp(rules.arkCloseSize0.value, rules.arkCloseSize1.value, x)
                s.halfWidth = radius
                s.halfLength = radius
                s.angle = 0f
                val k = hdr(rules.arkFlareIntensity.value)
                for (i in 0 until 3) s.color[i] = lerp(WHITE[i], TUBE_TINT[i], x) * k
                s.alpha = 1f
                s.uvScale = 1f
                s.noiseOffset = lerp(-1f, 1f, x) * PI.toFloat()
            }
            SpriteKind.ARK_DISTORTION -> {
                val carrier = s.baseAlpha > 0.5f || s.random > 0.5f   // pinned at spawn; the close ring is always the carrier's
                val scale0 = if (carrier) rules.arkDistScale0Carrier.value else rules.arkDistScale0Cruiser.value
                val scale1 = max(rules.arkDistScale1.value, scale0 + 0.01f)
                val radius = rules.arkDistortionMaxRadii.value * s.baseSize * lerp(scale0, scale1, x) / scale1
                s.halfWidth = radius
                s.halfLength = radius
                s.angle = 0f
                s.alpha = rules.arkDistortionStrength.value * (1f - x)
            }
            SpriteKind.ARK_RIPPLE -> {
                // Pre-roll heat haze gathering on the mouth: shrinks from the gather radius to the centre.
                val radius = s.baseSize * (1f - x) + s.baseSize * 0.05f
                s.halfWidth = radius
                s.halfLength = radius
                s.angle = 0f
                s.alpha = rules.arkDistortionStrength.value * 0.6f * x
            }
            SpriteKind.ARK_REFRACT -> {
                val ease = 1f - (1f - x) * (1f - x)
                val radius = s.baseSize * lerp(0.2f, rules.arkRefractMaxRadii.value, ease)
                s.halfWidth = radius
                s.halfLength = radius
                s.angle = 0f
                s.alpha = rules.arkRefractStrength.value * (1f - x)
                s.shape[0] = 1f
                s.shape[1] = RING_LINE
                s.shape[2] = max(rules.arkRefractWidth.value * RING_LINE, 0.002f)
                s.shape[3] = 1f
            }
            SpriteKind.ARK_SPARK -> {
                s.halfWidth = 0.5f * s.baseSize * rules.arkSparkSize.value
                s.halfLength = s.halfWidth * rules.arkSparkStretch.value
                s.angle = atan2(s.vy, s.vx)
                s.offsetAlong = -s.halfLength   // the streak trails behind the spark
                val k = hdr(rules.arkSparkIntensity.value)
                for (i in 0 until 3) s.color[i] = SPARK_TINT[i] * k
                s.alpha = 1f - x * x
                s.blackPoint = 0f
            }
            SpriteKind.ARK_TWINKLE -> {
                val size = s.baseSize * rules.arkGlitterSize.value * lerp(0.6f, 1.2f, s.random) * (1f - 0.5f * x)
                s.halfWidth = size
                s.halfLength = size
                s.angle = 0f
                val flicker = 0.5f + 0.5f * cos(2f * PI.toFloat() * 8f * s.age + s.phase)
                val k = hdr(rules.arkGlitterIntensity.value * lerp(0.4f, 1f, flicker))
                for (i in 0 until 3) s.color[i] = lerp(TWINKLE_TINT[i], WHITE[i], s.random2) * k
                s.alpha = 1f - ramp(x, 0.7f, 1f)
                s.blackPoint = 0f
            }
            SpriteKind.ARK_HAZE -> {
                val size = s.baseSize * lerp(0.6f, 1.2f, x)
                s.halfWidth = size
                s.halfLength = size
                s.angle = s.random * 2f * PI.toFloat()
                val k = hdr(rules.arkHazeIntensity.value)
                for (i in 0 until 3) s.color[i] = TUBE_TINT[i] * k
                s.alpha = (1f - x) * smooth(0f, 0.15f, x)
                s.blackPoint = 0f
            }
            SpriteKind.ARK_STREAK -> {
                val length = s.baseSize * rules.arkStreakLength.value * (1f - 0.3f * x)
                s.halfLength = length
                s.halfWidth = length * rules.arkStreakAspect.value
                s.angle = if (rules.arkStreakAlongFacing.isOn) facingRadians() else 0f
                val k = hdr(rules.arkStreakIntensity.value)
                for (i in 0 until 3) s.color[i] = lerp(FLASH_TINT[i], WHITE[i], 0.5f) * k
                s.alpha = 1f - x
                s.blackPoint = 0f
            }
            SpriteKind.ARK_SHOCKWAVE -> {
                val ease = 1f - (1f - x) * (1f - x)
                val radius = s.baseSize * lerp(0.3f, rules.arkShockRadii.value, ease)
                writeRingStyle()
                s.halfWidth = radius / RING_LINE
                s.halfLength = s.halfWidth
                s.angle = 0f
                val k = hdr(rules.arkShockIntensity.value * (1f - x))
                for (i in 0 until 3) s.color[i] = RING_TINT[i] * k
                s.alpha = 1f
                s.shape[0] = 1f
                s.shape[1] = RING_LINE
                s.shape[2] = max(rules.arkShockWidth.value, 0.002f)
                s.shape[3] = 0f
            }
            SpriteKind.ARK_REMNANT -> {
                val radius = s.baseSize * lerp(1f, rules.arkRemnantGrowth.value, x)
                writeRingStyle()
                s.halfWidth = radius / RING_LINE
                s.halfLength = s.halfWidth * max(mouthAspect(), MIN_ASPECT)
                s.angle = facingRadians()
                val k = hdr(rules.arkRemnantIntensity.value * (1f - x))
                for (i in 0 until 3) s.color[i] = TUBE_TINT[i] * k
                s.alpha = 1f
                s.shape[0] = max(mouthAspect(), MIN_ASPECT)
                s.shape[1] = RING_LINE
                s.shape[2] = rules.arkRingCoreWidth.value
                s.shape[3] = 0f
            }
            else -> {}
        }
    }

    /** The Halo flash (`flash_soft`, random rotation / mirror) and its halo ([sizeMult], [intensityMult]). */
    private fun refreshFlash(s: RuptureSprite, x: Float, sizeMult: Float, intensityMult: Float) {
        val carrier = s.baseAlpha > 0.5f
        val size0 = if (carrier) rules.arkFlareSize0Carrier.value else rules.arkFlareSize0Cruiser.value
        val size1 = if (carrier) rules.arkFlareSize1Carrier.value else rules.arkFlareSize1Cruiser.value
        val radius = s.baseSize * lerp(size0, size1, late(x)) * sizeMult
        s.halfWidth = radius
        s.halfLength = radius * aspect()
        s.angle = facingRadians()
        val k = hdr(rules.arkFlareIntensity.value * intensityMult)
        for (i in 0 until 3) s.color[i] = FLASH_TINT[i] * k
        s.alpha = 1f - ramp(x, 0.8f, 1f)
        s.uvScale = s.random
        s.noiseOffset = s.phase + s.random2 * 2f * PI.toFloat() * s.age
        s.blackPoint = 0f
    }
}

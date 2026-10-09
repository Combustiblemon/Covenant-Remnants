package org.combustiblelemon.covenantremnants.utils.render.torpedo

import com.fs.starfarer.api.combat.DamagingProjectileAPI
import org.combustiblelemon.covenantremnants.utils.render.rupture.RIBBON_STRIDE
import org.combustiblelemon.covenantremnants.utils.render.rupture.ReachColorCurve
import org.combustiblelemon.covenantremnants.utils.render.rupture.ReachCurve
import org.combustiblelemon.covenantremnants.utils.render.rupture.RuptureSprite
import org.combustiblelemon.covenantremnants.utils.render.rupture.SpriteKind
import org.lwjgl.util.vector.Vector2f
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The Halo: Reach Wraith mortar round's curves (`objects\vehicles\covenant\wraith\fx\weapon\mortar\`),
 * 9 samples over the input 0..1, from the official editing kit's tags. Extraction and decoding:
 * `planning/implementations/plasma-mortar.md`, data in `assets/reference/plasma-mortar/reach-data/`.
 */
internal object WraithCurves {
    // --- projectile.light_volume_system (input: profile position, 0 = tail, 1 = head) ---
    val BODY_THICKNESS = ReachCurve(floatArrayOf(0.5f, 0.521f, 0.541f, 0.557f, 0.572f, 0.586f, 0.594f, 0.598f, 0.6f))
    val BODY_INTENSITY = ReachCurve(floatArrayOf(1f, 1.001f, 1.011f, 1.042f, 1.105f, 1.246f, 1.424f, 1.672f, 2f))

    // --- projectile.contrail_system contrail_0 (input: profile age) ---
    val TRAIL_COLOR = ReachColorCurve(floatArrayOf(
        1f, 1f, 1f, 0.91f, 0.949f, 1f, 0.828f, 0.906f, 1f,
        0.757f, 0.875f, 1f, 0.694f, 0.847f, 1f, 0.635f, 0.82f, 1f,
        0.604f, 0.808f, 1f, 0.588f, 0.8f, 1f, 0.58f, 0.796f, 1f
    ))
    val TRAIL_ALPHA = ReachCurve(floatArrayOf(0f, 0.992f, 0.999f, 1f, 0.976f, 0.706f, 0.392f, 0.117f, 0f))

    // --- projectile.effect plasma_trail (particle plasma_trail.particle; input: particle age) ---
    val PUFF_COLOR = ReachColorCurve(floatArrayOf(
        1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f,
        1f, 1f, 1f, 0.977f, 0.984f, 0.996f, 0.847f, 0.906f, 0.98f,
        0.675f, 0.812f, 0.969f, 0.471f, 0.722f, 0.957f, 0.341f, 0.678f, 0.949f
    ))
    val PUFF_ALPHA = ReachCurve(floatArrayOf(0f, 0.59f, 1f, 1f, 1f, 1f, 0.809f, 0.366f, 0f))
    val PUFF_SCALE = ReachCurve(floatArrayOf(0.5f, 0.589f, 0.672f, 0.75f, 0.819f, 0.893f, 0.941f, 0.977f, 1f))
    val PUFF_BLACK_POINT_HOT = ReachCurve(floatArrayOf(0f, 0f, 0.007f, 0.035f, 0.095f, 0.234f, 0.413f, 0.664f, 1f))
    val PUFF_BLACK_POINT_DIM = ReachCurve(floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0.061f, 0.172f, 0.34f))

    // --- projectile.effect plasma_blue_medium (input: particle age) ---
    val PLASMA_ALPHA = ReachCurve(floatArrayOf(0f, 0.627f, 0.952f, 1f, 1f, 1f, 0.969f, 0.672f, 0f))
    val PLASMA_SCALE = ReachCurve(floatArrayOf(0.75f, 0.856f, 0.892f, 0.917f, 0.937f, 0.958f, 0.973f, 0.987f, 1f))
}

/**
 * One Plasma Mortar round in flight, the Halo: Reach Wraith mortar round rebuilt from its tags
 * (developer 2026-10-09: "recreate the wraith projectile"; the engine round itself is invisible):
 *
 * - `projectile.light_volume_system`: a short cyan-blue light-volume body (2 WU, brighter toward the head),
 * - `projectile.lens_flare`: a `star_flare`, `projectile.light`: a soft blue light around the round,
 * - `projectile.contrail_system`: a `flaming_trail` ribbon through the `wraith_mortar_contrail` palette,
 * - `projectile.effect` particles left behind: `plasma_trail` puffs, `spark_ember` sparkles and
 *   `plasma_blue_medium` plasma.
 *
 * Reach's round flies at ~15 WU/s; ours several times faster. Everything left behind is emitted per
 * distance travelled (Reach rate / Reach speed) and lives for its Reach life scaled by the speed
 * ratio, so the tail keeps Reach's length behind the round instead of stretching into a spear.
 */
internal class WraithFlight(val projectile: DamagingProjectileAPI, val unit: Float) {
    private companion object {
        const val REACH_SPEED = 15f          // wraith_mortar_round: 20 -> 10 WU/s
        const val MIN_TIME_SCALE = 0.03f
        const val MAX_TIME_SCALE = 1f
        const val MAX_EMIT_PER_FRAME = 32

        // projectile.light_volume_system: 2 WU long from 1.5 behind, 4 profiles per WU, (0.42,0.70,1.0).
        const val BODY_LENGTH = 2f
        const val BODY_OFFSET = -1.5f
        const val BODY_DENSITY = 4f
        val BODY_COLOR = floatArrayOf(0.416f, 0.698f, 0.996f)

        // projectile.lens_flare: star_flare, radius curve 0 -> 1.5 (its peak used), (0.22,0.30,1.0).
        const val STAR_RADIUS = 1.5f
        val STAR_COLOR = floatArrayOf(0.22f, 0.303f, 1f)

        // projectile.light: (0.60,0.63,1.0), attenuation to 4 WU. Intensity is not in the dump: placeholder.
        const val LIGHT_RADIUS = 4f
        val LIGHT_COLOR = floatArrayOf(0.596f, 0.631f, 1f)
        const val LIGHT_INTENSITY = 0.35f

        // contrail_0: 60 profiles/s, 0.5 s, size 0.65, uv tiling 0.5 along, no scroll.
        const val TRAIL_RATE = 60f
        const val TRAIL_LIFE = 0.5f
        const val TRAIL_SIZE = 0.65f
        const val TRAIL_U_PER_UNIT = 0.5f
        const val TRAIL_CAPACITY = 96

        // plasma_trail: 150/s, tube radius 0.2-0.35, 0.35-0.5 s, size 0.35-0.5, intensity 2.
        const val PUFF_RATE = 150f
        const val PUFF_INTENSITY = 2f

        // spark_ember: 100/s, globe 0.5, 0.5-0.85 s, intensity flickering 3.6-16, white. Size 0.02 in the
        // tag reads sub-pixel here: floored to 0.25 (placeholder).
        const val EMBER_RATE = 100f
        const val EMBER_SIZE = 0.25f
        const val EMBER_INTENSITY_MIN = 3.6f
        const val EMBER_INTENSITY_MAX = 16f
        const val EMBER_FLICKER_RATE = 12f

        // plasma_blue_medium: 60/s, globe 0.25, 0.25-0.35 s, size 0.65-0.75, intensity 2.
        const val PLASMA_RATE = 60f
        const val PLASMA_INTENSITY = 2f

        // Not in the tags: the body fades with the engine round when it fizzles out at max range.
        const val FADE_TIME = 0.3f
    }

    private val profiles = (BODY_LENGTH * BODY_DENSITY).toInt()
    val body = Array(profiles) { RuptureSprite(SpriteKind.TORPEDO_BODY) }
    val light = RuptureSprite(SpriteKind.TORPEDO_BODY)
    val star = RuptureSprite(SpriteKind.TORPEDO_STAR)
    val puffs = ArrayList<RuptureSprite>()
    val embers = ArrayList<RuptureSprite>()
    val plasma = ArrayList<RuptureSprite>()

    private val random = Random(System.nanoTime() xor (projectile.hashCode().toLong() * 7919L))
    private val salt = random.nextInt(10000)
    private val color = FloatArray(3)

    private val trailX = FloatArray(TRAIL_CAPACITY)
    private val trailY = FloatArray(TRAIL_CAPACITY)
    private val trailAge = FloatArray(TRAIL_CAPACITY)
    private val trailLife = FloatArray(TRAIL_CAPACITY)
    private val trailU = FloatArray(TRAIL_CAPACITY)
    private var trailStart = 0
    private var trailCount = 0

    private var travelled = 0f
    private val last = Vector2f(projectile.location)
    private var trailDue = 0f
    private var puffDue = 0f
    private var emberDue = 0f
    private var plasmaDue = 0f
    private var fade = 1f
    private var time = 0f

    /** False once the round is gone (hit or expired); what it left behind then ages out. */
    var flying = true
        private set

    /** Distance the round has been followed, in su. */
    val distance: Float get() = travelled * unit

    /** Seconds since the round was fired. */
    val age: Float get() = time

    /** Set by the owner once the engine reports the round in play (new rounds may not be yet). */
    var seenInPlay = false

    val isDone: Boolean
        get() = !flying && trailCount == 0 && puffs.isEmpty() && embers.isEmpty() && plasma.isEmpty()

    fun end() {
        flying = false
        for (s in body) s.alpha = 0f
        light.alpha = 0f
        star.alpha = 0f
    }

    fun advance(dt: Float) {
        time += dt
        ageTrail(dt)
        ageParticles(puffs, dt)
        ageParticles(embers, dt)
        ageParticles(plasma, dt)
        if (!flying) return

        val location = projectile.location
        val dx = location.x - last.x
        val dy = location.y - last.y
        val moved = sqrt(dx * dx + dy * dy)
        val reachDistance = moved / unit
        travelled += reachDistance
        // Reach seconds the same Reach distance takes; the speed ratio scales every left-behind life.
        val reachDt = if (moved > 0.01f) reachDistance / REACH_SPEED else dt
        val timeScale = if (dt > 0f) (dt / reachDt).coerceIn(MIN_TIME_SCALE, MAX_TIME_SCALE) else 1f

        if (projectile.isFading) fade = max(0f, fade - dt / FADE_TIME)
        val emitting = fade > 0f
        if (emitting) {
            trailDue += TRAIL_RATE * reachDt
            puffDue += PUFF_RATE * reachDt
            emberDue += EMBER_RATE * reachDt
            plasmaDue += PLASMA_RATE * reachDt
            trailDue = emitTrail(trailDue, timeScale, location)
            puffDue = emitParticles(puffDue, timeScale, location, Emit.PUFF)
            emberDue = emitParticles(emberDue, timeScale, location, Emit.EMBER)
            plasmaDue = emitParticles(plasmaDue, timeScale, location, Emit.PLASMA)
        }
        last.set(location)
        updateRound()
    }

    private enum class Emit { PUFF, EMBER, PLASMA }

    private fun rand(min: Float, max: Float): Float = min + (max - min) * random.nextFloat()

    /** Emits floor([due]) trail points spread along last -> [location]; returns the remainder. */
    private fun emitTrail(due: Float, timeScale: Float, location: Vector2f): Float {
        val count = min(due.toInt(), MAX_EMIT_PER_FRAME)
        for (i in 0 until count) {
            val f = (i + 1f) / count
            val index: Int
            if (trailCount == TRAIL_CAPACITY) {
                index = trailStart
                trailStart = (trailStart + 1) % TRAIL_CAPACITY
            } else {
                index = (trailStart + trailCount) % TRAIL_CAPACITY
                trailCount++
            }
            trailX[index] = last.x + (location.x - last.x) * f
            trailY[index] = last.y + (location.y - last.y) * f
            trailAge[index] = 0f
            trailLife[index] = TRAIL_LIFE * timeScale
            trailU[index] = travelled * TRAIL_U_PER_UNIT
        }
        return if (due.toInt() > MAX_EMIT_PER_FRAME) 0f else due - count
    }

    private fun emitParticles(due: Float, timeScale: Float, location: Vector2f, kind: Emit): Float {
        val count = min(due.toInt(), MAX_EMIT_PER_FRAME)
        for (i in 0 until count) {
            val f = (i + random.nextFloat()) / count
            val x = last.x + (location.x - last.x) * f
            val y = last.y + (location.y - last.y) * f
            when (kind) {
                Emit.PUFF -> {
                    val s = place(SpriteKind.WRAITH_PLASMA_TRAIL, x, y, rand(0.2f, 0.35f))
                    s.life = rand(0.35f, 0.5f) * timeScale
                    s.baseSize = rand(0.35f, 0.5f) * unit
                    puffs.add(s)
                }
                Emit.EMBER -> {
                    val s = place(SpriteKind.SPARK, x, y, sqrt(random.nextFloat()) * 0.5f)
                    s.life = rand(0.5f, 0.85f) * timeScale
                    s.baseSize = EMBER_SIZE * unit
                    embers.add(s)
                }
                Emit.PLASMA -> {
                    val s = place(SpriteKind.TORPEDO_PLASMA, x, y, sqrt(random.nextFloat()) * 0.25f)
                    s.life = rand(0.25f, 0.35f) * timeScale
                    s.baseSize = rand(0.65f, 0.75f) * unit
                    plasma.add(s)
                }
            }
        }
        return if (due.toInt() > MAX_EMIT_PER_FRAME) 0f else due - count
    }

    /** A sprite at ([x], [y]) pushed out to [radius] Reach units in a random direction. */
    private fun place(kind: SpriteKind, x: Float, y: Float, radius: Float): RuptureSprite {
        val s = RuptureSprite(kind)
        val heading = rand(0f, 2f * PI.toFloat())
        s.x = x + cos(heading) * radius * unit
        s.y = y + sin(heading) * radius * unit
        s.random = random.nextFloat()
        s.random2 = random.nextFloat()
        s.noiseOffset = random.nextFloat()
        s.angle = rand(0f, 2f * PI.toFloat())
        s.phase = rand(-0.5f, 0.5f)
        return s
    }

    private fun ageTrail(dt: Float) {
        for (i in 0 until trailCount) trailAge[(trailStart + i) % TRAIL_CAPACITY] += dt
        while (trailCount > 0 && trailAge[trailStart] >= trailLife[trailStart]) {
            trailStart = (trailStart + 1) % TRAIL_CAPACITY
            trailCount--
        }
    }

    private fun ageParticles(list: ArrayList<RuptureSprite>, dt: Float) {
        val iterator = list.iterator()
        while (iterator.hasNext()) {
            val s = iterator.next()
            s.age += dt
            if (s.age >= s.life) {
                iterator.remove()
                continue
            }
            s.angle += s.phase * dt
            updateParticle(s, s.age / s.life)
        }
    }

    private fun updateParticle(s: RuptureSprite, x: Float) {
        when (s.kind) {
            SpriteKind.WRAITH_PLASMA_TRAIL -> {
                s.halfLength = s.baseSize * 0.5f * WraithCurves.PUFF_SCALE.at(x)
                s.halfWidth = s.halfLength
                WraithCurves.PUFF_COLOR.at(x, color)
                for (c in 0..2) s.color[c] = color[c] * PUFF_INTENSITY
                s.alpha = WraithCurves.PUFF_ALPHA.at(x)
                val dim = WraithCurves.PUFF_BLACK_POINT_DIM.at(x)
                s.blackPoint = dim + (WraithCurves.PUFF_BLACK_POINT_HOT.at(x) - dim) * s.random
            }
            SpriteKind.SPARK -> {
                s.halfLength = s.baseSize * 0.5f
                s.halfWidth = s.halfLength
                val n = TorpedoLook.noise(time * EMBER_FLICKER_RATE + s.random2 * 50f, salt)
                val intensity = EMBER_INTENSITY_MIN + (EMBER_INTENSITY_MAX - EMBER_INTENSITY_MIN) * n
                for (c in 0..2) s.color[c] = intensity
                s.alpha = 1f - x
            }
            SpriteKind.TORPEDO_PLASMA -> {
                s.halfLength = s.baseSize * 0.5f * WraithCurves.PLASMA_SCALE.at(x)
                s.halfWidth = s.halfLength
                TorpedoCurves.PLASMA_COLOR.at(x, color)
                for (c in 0..2) s.color[c] = color[c] * PLASMA_INTENSITY
                s.alpha = WraithCurves.PLASMA_ALPHA.at(x)
                s.blackPoint = TorpedoCurves.PLASMA_BLACK_POINT.at(x)
                s.paletteV = s.random
                s.uvScale = 0.75f + 0.5f * x
            }
            else -> {}
        }
    }

    private fun updateRound() {
        val location = projectile.location
        val velocity = projectile.velocity
        val speed = sqrt(velocity.x * velocity.x + velocity.y * velocity.y)
        val dirX = if (speed > 0.01f) velocity.x / speed else cos(Math.toRadians(projectile.facing.toDouble()).toFloat())
        val dirY = if (speed > 0.01f) velocity.y / speed else sin(Math.toRadians(projectile.facing.toDouble()).toFloat())
        for (i in 0 until profiles) {
            val p = (i + 0.5f) / profiles
            val along = (BODY_OFFSET + p * BODY_LENGTH) * unit
            val s = body[i]
            s.x = location.x + dirX * along
            s.y = location.y + dirY * along
            s.angle = 0f
            s.halfLength = WraithCurves.BODY_THICKNESS.at(p) * unit
            s.halfWidth = s.halfLength
            val intensity = WraithCurves.BODY_INTENSITY.at(p)
            for (c in 0..2) s.color[c] = BODY_COLOR[c] * intensity
            s.alpha = fade
        }
        light.x = location.x
        light.y = location.y
        light.angle = 0f
        light.halfLength = LIGHT_RADIUS * unit
        light.halfWidth = light.halfLength
        for (c in 0..2) light.color[c] = LIGHT_COLOR[c] * LIGHT_INTENSITY
        light.alpha = fade

        star.x = location.x
        star.y = location.y
        star.angle = 0f
        star.halfLength = STAR_RADIUS * unit
        star.halfWidth = star.halfLength
        STAR_COLOR.copyInto(star.color)
        star.alpha = fade
    }

    val maxTrailPoints: Int get() = TRAIL_CAPACITY + 1

    /**
     * Writes the contrail into [out] (RIBBON_STRIDE floats per point, head first): the round itself
     * while it flies, then every live point newest to oldest. Returns the point count.
     */
    fun writeTrail(out: FloatArray): Int {
        var n = 0
        if (flying && fade > 0f) {
            val location = projectile.location
            n = writeTrailPoint(out, n, location.x, location.y, 0f, travelled * TRAIL_U_PER_UNIT, fade)
        }
        for (k in trailCount - 1 downTo 0) {
            val i = (trailStart + k) % TRAIL_CAPACITY
            n = writeTrailPoint(out, n, trailX[i], trailY[i], trailAge[i] / trailLife[i], trailU[i], 1f)
        }
        return n
    }

    private fun writeTrailPoint(out: FloatArray, n: Int, x: Float, y: Float, a: Float, u: Float, alphaMult: Float): Int {
        val o = n * RIBBON_STRIDE
        out[o] = x
        out[o + 1] = y
        out[o + 2] = TRAIL_SIZE * 0.5f * unit
        out[o + 3] = u
        WraithCurves.TRAIL_COLOR.at(a, color)
        out[o + 4] = color[0]
        out[o + 5] = color[1]
        out[o + 6] = color[2]
        out[o + 7] = WraithCurves.TRAIL_ALPHA.at(a) * alphaMult
        out[o + 8] = 0f
        return n + 1
    }
}

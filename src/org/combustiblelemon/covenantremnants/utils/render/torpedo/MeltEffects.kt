package org.combustiblelemon.covenantremnants.utils.render.torpedo

import com.fs.starfarer.api.combat.CombatEntityAPI
import org.combustiblelemon.covenantremnants.utils.render.rupture.RuptureSprite
import org.combustiblelemon.covenantremnants.utils.render.rupture.SpriteKind
import org.lwjgl.util.vector.Vector2f
import java.util.Random
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The molten patch a plasma torpedo leaves on the hull it hit (launcher polish, developer
 * 2026-10-09): a glow stuck to the hull, orange-white at impact cooling to blue-violet over the
 * burn ([COOL_TIME], the melt's duration), then fading; a white core for the first moments; blue
 * plasma flickering in as it cools; embers thrown off while it is hot. Not from the Reach tags:
 * every value here is a placeholder for the in-game look. Existing materials only (the torpedo
 * body glow, plasma and spark).
 *
 * The patch keeps its offset in [anchor]'s frame (vanilla DisintegratorEffect pattern), so it moves
 * and turns with the ship, wreck included; [detach] freezes it where it is (anchor out of play).
 */
internal class MeltPatch(
    private val anchor: CombatEntityAPI,
    centerX: Float,
    centerY: Float,
    val radius: Float,
    inHull: ((Vector2f) -> Boolean)? = null
) {
    companion object {
        const val COOL_TIME = 2f
        const val FADE_TIME = 0.5f
        private const val FADE_IN = 0.05f

        // Glow: radius x (1 -> 1 - GLOW_SHRINK) while it cools.
        private val HOT_COLOR = floatArrayOf(1f, 0.78f, 0.5f)
        private const val HOT_INTENSITY = 2f
        private val COOL_COLOR = floatArrayOf(0.42f, 0.38f, 1f)
        private const val COOL_INTENSITY = 0.9f
        private const val GLOW_ALPHA = 0.85f
        private const val GLOW_SHRINK = 0.25f

        // White core, gone after CORE_TIME.
        private const val CORE_TIME = 0.5f
        private const val CORE_RADIUS = 0.35f
        private val CORE_COLOR = floatArrayOf(1f, 0.95f, 0.85f)
        private const val CORE_INTENSITY = 2.5f

        // Plasma flickers (CannonFlight wisp formulas), fixed on the hull within PLASMA_SPREAD x radius.
        private const val PLASMA_COUNT = 4
        private const val PLASMA_START = 0.8f
        private const val PLASMA_RAMP = 0.3f
        private const val PLASMA_SPREAD = 0.5f
        private const val PLASMA_SIZE = 0.3f
        private const val PLASMA_FLICKER = 5f
        private const val PLASMA_NOISE_RATE = 7.5f
        private const val PLASMA_ALPHA = 0.75f

        // Embers: EMBER_RATE per second at impact, fewer as it cools; world space, sizes in su.
        private const val EMBER_RATE = 12f
        private const val EMBER_SPREAD = 0.6f
        private const val EMBER_SPEED_MIN = 15f
        private const val EMBER_SPEED_MAX = 50f
        private const val EMBER_DRAG = 2f
        private const val EMBER_LIFE_MIN = 0.3f
        private const val EMBER_LIFE_MAX = 0.8f
        private const val EMBER_SIZE_MIN = 4f
        private const val EMBER_SIZE_MAX = 8f
        private val EMBER_COLOR = floatArrayOf(1f, 0.55f, 0.2f)
        private const val EMBER_INTENSITY = 6f
        private const val EMBER_MOTION_BLUR = 0.03f

        // Placement (ruling): centre INWARD x melt radius into the hull along the torpedo's travel,
        // glow radius capped at the target's collision radius. Plasma spots are picked inside the hull
        // (PLASMA_TRIES random spots, else the centre), like the melt cells.
        private const val INWARD = 1f / 3f
        private const val PLASMA_TRIES = 8

        private var counter = 0

        /**
         * The patch for a hit at [hit] (world) by a torpedo travelling at [travelAngle] degrees;
         * [inHull] tells whether a world point lies on the target's hull.
         */
        fun forHit(
            anchor: CombatEntityAPI, hit: Vector2f, travelAngle: Float, meltRadius: Float,
            inHull: ((Vector2f) -> Boolean)? = null
        ): MeltPatch {
            val a = Math.toRadians(travelAngle.toDouble())
            val inward = meltRadius * INWARD
            val x = hit.x + cos(a).toFloat() * inward
            val y = hit.y + sin(a).toFloat() * inward
            val radius = if (anchor.collisionRadius > 0f) min(meltRadius, anchor.collisionRadius) else meltRadius
            return MeltPatch(anchor, x, y, radius, inHull)
        }
    }

    val sprites = ArrayList<RuptureSprite>()
    private val glow = RuptureSprite(SpriteKind.MELT_GLOW)
    private val core = RuptureSprite(SpriteKind.MELT_CORE)
    private val plasma = Array(PLASMA_COUNT) { RuptureSprite(SpriteKind.MELT_PLASMA) }
    private val embers = ArrayList<RuptureSprite>()

    private val random = Random(System.nanoTime() xor (counter++ * 7919L))
    private val salt = random.nextInt(10000)
    private val color = FloatArray(3)

    // Offsets in the anchor's frame (rotated by -facing at the hit).
    private val facing0 = anchor.facing
    private val localX: Float
    private val localY: Float
    private val plasmaLocalX = FloatArray(PLASMA_COUNT)
    private val plasmaLocalY = FloatArray(PLASMA_COUNT)
    private var attached = true
    private var poseFacing = anchor.facing
    private val poseLocation = Vector2f(anchor.location)

    /** World centre of the patch this frame (the floating numbers go here). */
    val center = Vector2f(centerX, centerY)

    var age = 0f
        private set
    private var emberDebt = 0f

    val isDone: Boolean get() = age >= COOL_TIME + FADE_TIME && embers.isEmpty()

    init {
        val r = rotate(centerX - anchor.location.x, centerY - anchor.location.y, -facing0)
        localX = r.x
        localY = r.y
        val spot = Vector2f()
        for (i in 0 until PLASMA_COUNT) {
            plasmaLocalX[i] = localX
            plasmaLocalY[i] = localY
            for (attempt in 0 until PLASMA_TRIES) {
                val heading = random.nextFloat() * 2f * PI.toFloat()
                val distance = sqrt(random.nextFloat()) * PLASMA_SPREAD * radius
                spot.set(centerX + cos(heading) * distance, centerY + sin(heading) * distance)
                if (inHull != null && !inHull(spot)) continue
                val l = rotate(spot.x - anchor.location.x, spot.y - anchor.location.y, -facing0)
                plasmaLocalX[i] = l.x
                plasmaLocalY[i] = l.y
                break
            }
            val p = plasma[i]
            p.random = random.nextFloat()
            p.random2 = random.nextFloat()
            p.angle = random.nextFloat() * 2f * PI.toFloat()
            p.phase = (random.nextFloat() - 0.5f) * 2f
        }
        glow.angle = random.nextFloat() * 2f * PI.toFloat()
        core.angle = random.nextFloat() * 2f * PI.toFloat()
    }

    /** Freezes the patch at its last pose (the anchor left play); it keeps cooling there. */
    fun detach() {
        attached = false
    }

    fun advance(dt: Float) {
        age += dt
        if (attached) {
            poseLocation.set(anchor.location)
            poseFacing = anchor.facing
        }
        val world = toWorld(localX, localY)
        center.set(world)

        val x = min(1f, age / COOL_TIME)
        val cool = x * x * (3f - 2f * x)
        val fade = when {
            age < FADE_IN -> age / FADE_IN
            age < COOL_TIME -> 1f
            else -> max(0f, 1f - (age - COOL_TIME) / FADE_TIME)
        }

        glow.x = world.x
        glow.y = world.y
        glow.halfLength = radius * (1f - GLOW_SHRINK * cool)
        glow.halfWidth = glow.halfLength
        val intensity = HOT_INTENSITY + (COOL_INTENSITY - HOT_INTENSITY) * cool
        for (c in 0..2) glow.color[c] = (HOT_COLOR[c] + (COOL_COLOR[c] - HOT_COLOR[c]) * cool) * intensity
        glow.alpha = GLOW_ALPHA * fade

        core.x = world.x
        core.y = world.y
        core.halfLength = radius * CORE_RADIUS * (1f + 0.3f * min(1f, age / CORE_TIME))
        core.halfWidth = core.halfLength
        for (c in 0..2) core.color[c] = CORE_COLOR[c] * CORE_INTENSITY
        core.alpha = max(0f, 1f - age / CORE_TIME) * min(1f, fade)

        val plasmaIn = ((age - PLASMA_START) / PLASMA_RAMP).coerceIn(0f, 1f)
        for (i in 0 until PLASMA_COUNT) {
            val p = plasma[i]
            val at = toWorld(plasmaLocalX[i], plasmaLocalY[i])
            p.x = at.x
            p.y = at.y
            p.angle += p.phase * dt
            val n = TorpedoLook.noise(age * PLASMA_NOISE_RATE + p.random2 * 50f, salt + i * 13)
            p.halfLength = radius * PLASMA_SIZE * (0.8f + 0.4f * n)
            p.halfWidth = p.halfLength
            TorpedoCurves.PLASMA_COLOR.at(0.5f + 0.5f * sin(age * 2f + i), color)
            val flicker = PLASMA_FLICKER * max(0f, -0.25f + 1.25f * n)
            for (c in 0..2) p.color[c] = color[c] * flicker
            p.alpha = PLASMA_ALPHA * plasmaIn * fade
            p.blackPoint = 0.15f
            p.paletteV = p.random
            p.uvScale = 0.9f
        }

        if (age < COOL_TIME) {
            emberDebt += EMBER_RATE * (1f - cool) * dt
            while (emberDebt >= 1f) {
                emberDebt -= 1f
                spawnEmber(world)
            }
        }
        val iterator = embers.iterator()
        while (iterator.hasNext()) {
            val e = iterator.next()
            e.age += dt
            if (e.age >= e.life) {
                iterator.remove()
                continue
            }
            e.x += e.vx * dt
            e.y += e.vy * dt
            val keep = exp(-e.drag * dt)
            e.vx *= keep
            e.vy *= keep
            // As the torpedo sparks: the streak runs along u, so turn the sprite a quarter.
            val speed = sqrt(e.vx * e.vx + e.vy * e.vy)
            e.angle = atan2(e.vy, e.vx) + PI.toFloat() * 0.5f
            e.halfLength = e.baseSize * 0.25f
            e.halfWidth = e.baseSize * 0.5f + speed * EMBER_MOTION_BLUR
            for (c in 0..2) e.color[c] = EMBER_COLOR[c] * EMBER_INTENSITY
            e.alpha = 1f - e.age / e.life
        }

        sprites.clear()
        sprites.add(glow)
        sprites.add(core)
        sprites.addAll(plasma)
        sprites.addAll(embers)
    }

    private fun spawnEmber(at: Vector2f) {
        val e = RuptureSprite(SpriteKind.MELT_EMBER)
        val heading = random.nextFloat() * 2f * PI.toFloat()
        val distance = sqrt(random.nextFloat()) * EMBER_SPREAD * radius
        e.x = at.x + cos(heading) * distance
        e.y = at.y + sin(heading) * distance
        val speed = EMBER_SPEED_MIN + (EMBER_SPEED_MAX - EMBER_SPEED_MIN) * random.nextFloat()
        val v = if (attached) anchor.velocity else null
        e.vx = cos(heading) * speed + (v?.x ?: 0f)
        e.vy = sin(heading) * speed + (v?.y ?: 0f)
        e.drag = EMBER_DRAG
        e.life = EMBER_LIFE_MIN + (EMBER_LIFE_MAX - EMBER_LIFE_MIN) * random.nextFloat()
        e.baseSize = EMBER_SIZE_MIN + (EMBER_SIZE_MAX - EMBER_SIZE_MIN) * random.nextFloat()
        embers.add(e)
    }

    private fun toWorld(lx: Float, ly: Float): Vector2f {
        val r = rotate(lx, ly, poseFacing)
        r.x += poseLocation.x
        r.y += poseLocation.y
        return r
    }

    private fun rotate(x: Float, y: Float, degrees: Float): Vector2f {
        val a = Math.toRadians(degrees.toDouble())
        val c = cos(a).toFloat()
        val s = sin(a).toFloat()
        return Vector2f(x * c - y * s, x * s + y * c)
    }
}

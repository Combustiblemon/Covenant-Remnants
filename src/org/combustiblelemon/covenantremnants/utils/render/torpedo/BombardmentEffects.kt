package org.combustiblelemon.covenantremnants.utils.render.torpedo

import com.fs.starfarer.api.combat.DamagingProjectileAPI
import org.combustiblelemon.covenantremnants.utils.render.rupture.RIBBON_STRIDE
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
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The Halo: Reach Covenant AA gun (`objects\vehicles\covenant\turrets\bfg\`), remade for the Plasma
 * Bombardment (developer rulings 2026-10-09): the round ([BombardFlight]), the muzzle charge
 * ([BombardCharge]) and the firing flash / `covenant_explosion_artillery_huge` impact
 * ([ArtilleryBurst]). Curves in [BombardCurves]; extraction in
 * `planning/implementations/plasma-bombardment.md`, data in `assets/reference/plasma-bombardment/reach-data/`.
 */
internal object BombardLook {
    /**
     * Reach lights are scene lights, not sprites: their glow is drawn at the tag intensity x this
     * (placeholder). The round's light (intensity 5) then sits at 0.35, the Wraith round's value.
     */
    const val LIGHT_SCALE = 0.07f

    fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
}

/** A contrail's points in a ring buffer: position, age, life and texture u per point. */
internal class BombardContrail(private val capacity: Int) {
    private val px = FloatArray(capacity)
    private val py = FloatArray(capacity)
    private val age = FloatArray(capacity)
    private val life = FloatArray(capacity)
    private val u = FloatArray(capacity)
    private var start = 0
    var count = 0
        private set

    fun add(x: Float, y: Float, pointLife: Float, pointU: Float) {
        val index: Int
        if (count == capacity) {
            index = start
            start = (start + 1) % capacity
        } else {
            index = (start + count) % capacity
            count++
        }
        px[index] = x
        py[index] = y
        age[index] = 0f
        life[index] = pointLife
        u[index] = pointU
    }

    fun age(dt: Float) {
        for (k in 0 until count) age[(start + k) % capacity] += dt
        while (count > 0 && age[start] >= life[start]) {
            start = (start + 1) % capacity
            count--
        }
    }

    /** Index of the [k]-th newest point (0 = newest). */
    fun newest(k: Int): Int = (start + count - 1 - k) % capacity
    fun x(i: Int) = px[i]
    fun y(i: Int) = py[i]
    fun progress(i: Int) = age[i] / life[i]
    fun u(i: Int) = u[i]
}

/**
 * One Plasma Bombardment round in flight, the Reach AA gun's `bfg_round` rebuilt from its tags (the
 * engine round itself is invisible, as for the Plasma Mortar):
 *
 * - `projectile.light_volume_system`: a 5.5 WU light-volume body trailing the round, blue with a
 *   peach-white head (each profile stretched along the flight by its 2 WU `profile_length`),
 * - `projectile.lens_flare` (`anamorphic_flare`), `projectile_star.lens_flare` (`star_flare`) and the
 *   Wraith mortar's star flare that `projectile.effect` adds, `projectile.light` as a soft glow,
 * - `projectile.contrail_system`: a `hunter_beam` ribbon through `energy_trail_blue` and a
 *   `wispy_trail` ribbon going peach to indigo.
 *
 * Reach's round flies 200 WU/s, so its contrails are 100 and 200 WU long. Developer ruling
 * 2026-10-09 ("short tail"): Reach widths, colours and curves, but each contrail is [TRAIL_LENGTH] /
 * [WISPY_LENGTH] su behind the round, whatever its speed (each point lives length / speed).
 */
internal class BombardFlight(val projectile: DamagingProjectileAPI, val unit: Float) {
    private companion object {
        // projectile.light_volume_system: 5.5 WU from 5 behind the round, 1.5 profiles per WU, each 2 WU long.
        const val BODY_LENGTH = 5.5f
        const val BODY_OFFSET = -5f
        const val BODY_DENSITY = 1.5f
        const val BODY_PROFILE_LENGTH = 2f

        // projectile.lens_flare: anamorphic_flare, radius curve 5, brightness curve 0.05, (0.52,0.63,1).
        const val ANAMORPHIC_RADIUS = 5f
        const val ANAMORPHIC_ASPECT = 0.25f
        const val ANAMORPHIC_BRIGHTNESS = 0.05f
        val ANAMORPHIC_COLOR = floatArrayOf(0.5176f, 0.6271f, 1f)

        // projectile_star.lens_flare: star_flare, radius curve 7, brightness curve 0.5, (0.16,0.33,1).
        const val STAR_RADIUS = 7f
        const val STAR_BRIGHTNESS = 0.5f
        val STAR_COLOR = floatArrayOf(0.162f, 0.3324f, 1f)

        // projectile.effect: the Wraith mortar's star flare (radius 0 -> 1.5, brightness 0 -> 3; peak used).
        const val WRAITH_STAR_RADIUS = 1.5f
        const val WRAITH_STAR_BRIGHTNESS = 3f
        val WRAITH_STAR_COLOR = floatArrayOf(0.2196f, 0.3028f, 1f)

        // projectile.light: sphere to 4 WU, (0.60,0.63,1), intensity 5.
        const val LIGHT_RADIUS = 4f
        val LIGHT_COLOR = floatArrayOf(0.5961f, 0.6314f, 1f)
        const val LIGHT_INTENSITY = 5f

        // Contrails: hunter_beam (0.5 s, uv tiling 0.1 / WU, scrolling 2 / s, colour (0.68,0.73,1)) and
        // soft_contrail (1 s, wispy_trail, tiling 0.025, scrolling -0.35). Lengths in su per the "short
        // tail" ruling (~120 / ~200 su; Reach: 100 / 200 WU), whatever the round scale; a point every
        // TRAIL_SPACING WU (Reach emits 30 / 15 per second, too sparse for the curves on a short tail).
        const val TRAIL_LENGTH = 120f
        const val WISPY_LENGTH = 200f
        const val TRAIL_SPACING = 0.75f
        const val TRAIL_U_PER_UNIT = 0.1f
        const val TRAIL_SCROLL = 2f
        const val WISPY_U_PER_UNIT = 0.025f
        const val WISPY_SCROLL = -0.35f
        val TRAIL_COLOR = floatArrayOf(0.6824f, 0.7255f, 1f)
        const val TRAIL_CAPACITY = 48
        const val MAX_EMIT_PER_FRAME = 16

        // Not in the tags: the body fades with the engine round when it runs out of range (no
        // detonation), and a floor on the speed the tail life is worked out from.
        const val FADE_TIME = 0.3f
        const val MIN_SPEED = 50f
    }

    private val profiles = (BODY_LENGTH * BODY_DENSITY).toInt()
    val body = Array(profiles) { RuptureSprite(SpriteKind.TORPEDO_BODY) }
    val light = RuptureSprite(SpriteKind.TORPEDO_BODY)
    val star = RuptureSprite(SpriteKind.TORPEDO_STAR)
    val wraithStar = RuptureSprite(SpriteKind.TORPEDO_STAR)
    val anamorphic = RuptureSprite(SpriteKind.BOMBARD_ANAMORPHIC)

    private val hunter = BombardContrail(TRAIL_CAPACITY)
    private val wispy = BombardContrail(TRAIL_CAPACITY)
    private val color = FloatArray(3)
    private var travelled = 0f
    private val last = Vector2f(projectile.location)
    private var due = 0f
    private var fade = 1f
    private var time = 0f

    /** False once the round is gone (hit or expired); its contrails then age out. */
    var flying = true
        private set

    /** Distance the round has been followed, in su. */
    val distance: Float get() = travelled * unit

    /** Seconds since the round was picked up. */
    val age: Float get() = time

    /** Set by the owner once the engine reports the round in play (new rounds may not be yet). */
    var seenInPlay = false

    val isDone: Boolean get() = !flying && hunter.count == 0 && wispy.count == 0

    fun end() {
        flying = false
        for (s in body) s.alpha = 0f
        light.alpha = 0f
        star.alpha = 0f
        wraithStar.alpha = 0f
        anamorphic.alpha = 0f
    }

    /** The round's own sprites (body, light, flares) while it flies. */
    fun addSprites(out: MutableList<RuptureSprite>) {
        if (!flying) return
        out.add(light)
        out.addAll(body)
        out.add(star)
        out.add(wraithStar)
        out.add(anamorphic)
    }

    fun advance(dt: Float) {
        time += dt
        hunter.age(dt)
        wispy.age(dt)
        if (!flying) return

        val location = projectile.location
        val dx = location.x - last.x
        val dy = location.y - last.y
        val moved = sqrt(dx * dx + dy * dy)
        val reachMoved = moved / unit
        travelled += reachMoved

        if (projectile.isFading) fade = max(0f, fade - dt / FADE_TIME)
        if (fade > 0f && reachMoved > 0f) {
            val velocity = projectile.velocity
            val speed = max(sqrt(velocity.x * velocity.x + velocity.y * velocity.y), MIN_SPEED)
            val hunterLife = TRAIL_LENGTH / speed
            val wispyLife = WISPY_LENGTH / speed
            due += reachMoved / TRAIL_SPACING
            val count = min(due.toInt(), MAX_EMIT_PER_FRAME)
            for (i in 0 until count) {
                val f = (i + 1f) / count
                val x = last.x + dx * f
                val y = last.y + dy * f
                val at = travelled - (1f - f) * reachMoved
                hunter.add(x, y, hunterLife, at * TRAIL_U_PER_UNIT)
                wispy.add(x, y, wispyLife, at * WISPY_U_PER_UNIT)
            }
            due = if (due.toInt() > MAX_EMIT_PER_FRAME) 0f else due - count
        }
        last.set(location)
        updateRound()
    }

    private fun updateRound() {
        val location = projectile.location
        val velocity = projectile.velocity
        val speed = sqrt(velocity.x * velocity.x + velocity.y * velocity.y)
        val heading = if (speed > 0.01f) atan2(velocity.y, velocity.x) else Math.toRadians(projectile.facing.toDouble()).toFloat()
        val dirX = cos(heading)
        val dirY = sin(heading)
        for (i in 0 until profiles) {
            val p = (i + 0.5f) / profiles
            val along = (BODY_OFFSET + p * BODY_LENGTH) * unit
            val s = body[i]
            s.x = location.x + dirX * along
            s.y = location.y + dirY * along
            s.angle = heading
            s.halfLength = BODY_PROFILE_LENGTH * 0.5f * unit
            s.halfWidth = BombardCurves.BODY_THICKNESS.at(p) * unit
            BombardCurves.BODY_COLOR.at(p, color)
            val intensity = BombardCurves.BODY_INTENSITY.at(p)
            for (c in 0..2) s.color[c] = color[c] * intensity
            s.alpha = fade
        }
        place(light, location, LIGHT_RADIUS, LIGHT_COLOR, LIGHT_INTENSITY * BombardLook.LIGHT_SCALE)
        place(star, location, STAR_RADIUS, STAR_COLOR, STAR_BRIGHTNESS)
        place(wraithStar, location, WRAITH_STAR_RADIUS, WRAITH_STAR_COLOR, WRAITH_STAR_BRIGHTNESS)
        place(anamorphic, location, ANAMORPHIC_RADIUS, ANAMORPHIC_COLOR, ANAMORPHIC_BRIGHTNESS)
        anamorphic.halfWidth = anamorphic.halfLength * ANAMORPHIC_ASPECT
    }

    private fun place(s: RuptureSprite, location: Vector2f, radius: Float, tint: FloatArray, brightness: Float) {
        s.x = location.x
        s.y = location.y
        s.angle = 0f
        s.halfLength = radius * unit
        s.halfWidth = s.halfLength
        for (c in 0..2) s.color[c] = tint[c] * brightness
        s.alpha = fade
    }

    val maxTrailPoints: Int get() = TRAIL_CAPACITY + 1

    /** The hunter_beam contrail into [out] (RIBBON_STRIDE floats per point, head first); returns the point count. */
    fun writeTrail(out: FloatArray, time: Float): Int = writeContrail(out, time, false)

    /** The wispy soft_contrail into [out], same layout. */
    fun writeWispy(out: FloatArray, time: Float): Int = writeContrail(out, time, true)

    private fun writeContrail(out: FloatArray, time: Float, soft: Boolean): Int {
        val trail = if (soft) wispy else hunter
        val tiling = if (soft) WISPY_U_PER_UNIT else TRAIL_U_PER_UNIT
        val scroll = (if (soft) WISPY_SCROLL else TRAIL_SCROLL) * time
        var n = 0
        if (flying && fade > 0f) {
            val location = projectile.location
            n = writePoint(out, n, location.x, location.y, 0f, travelled * tiling + scroll, fade, soft)
        }
        for (k in 0 until trail.count) {
            val i = trail.newest(k)
            n = writePoint(out, n, trail.x(i), trail.y(i), trail.progress(i), trail.u(i) + scroll, 1f, soft)
        }
        return n
    }

    private fun writePoint(out: FloatArray, n: Int, x: Float, y: Float, a: Float, u: Float, alphaMult: Float, soft: Boolean): Int {
        val o = n * RIBBON_STRIDE
        out[o] = x
        out[o + 1] = y
        out[o + 3] = u
        if (soft) {
            out[o + 2] = BombardCurves.WISPY_SIZE.at(a) * 0.5f * unit
            BombardCurves.WISPY_COLOR.at(a, color)
            val intensity = BombardCurves.WISPY_INTENSITY.at(a)
            for (c in 0..2) out[o + 4 + c] = color[c] * intensity
            out[o + 7] = BombardCurves.WISPY_ALPHA.at(a) * alphaMult
            out[o + 8] = BombardCurves.WISPY_BLACK_POINT.at(a)
        } else {
            out[o + 2] = BombardCurves.TRAIL_SIZE.at(a) * 0.5f * unit
            val intensity = BombardCurves.TRAIL_INTENSITY.at(a)
            for (c in 0..2) out[o + 4 + c] = TRAIL_COLOR[c] * intensity
            out[o + 7] = BombardCurves.TRAIL_ALPHA.at(a) * alphaMult
            out[o + 8] = BombardCurves.TRAIL_BLACK_POINT.at(a)
        }
        return n + 1
    }
}

/**
 * The AA gun's charge at one Plasma Bombardment's muzzle (`charging.effect`, its `charge_particles`
 * event at `fx_muzzle_charge`), driven by the weapon's charge level 0..1 over its 1 s chargeup (Reach
 * charges 2 s): `core_charge.light_volume_system` glows and `core_charge.light` swelling with the
 * charge, `plasma_blue_medium` plasma gathering along the barrel, `core_charge_plasma` bursts and
 * `plasma_bolt` lightning swinging from sideways to along the barrel. The `core_charge` event (inside
 * the turret body) is not built. Fades when the charge ends (the shell leaves or the charge stops).
 */
internal class BombardCharge(private val unit: Float) {
    private companion object {
        // plasma_blue_medium: cylinder along the barrel (axis scale 1.5), radius 0-0.5, 0.75 / 0.35 s,
        // size 0.35 x scale (charge), intensity 2.
        const val PLASMA_SIZE = 0.35f
        const val PLASMA_INTENSITY = 2f
        const val PLASMA_HALF_LENGTH = 0.75f
        const val PLASMA_RADIUS = 0.5f

        // core_charge_plasma: 8 / s, 0.25 s.
        const val CORE_RATE = 8f
        const val CORE_LIFE = 0.25f

        // plasma_bolt: tube radius 0.1, direction 90 -> 0 deg off the barrel (read here as over the
        // charge), +-5 deg, intensity 5.
        const val BOLT_RADIUS = 0.1f
        const val BOLT_SWING = 90f
        const val BOLT_SPREAD = 5f
        const val BOLT_INTENSITY = 5f

        // core_charge.light_volume_system outer_glow: 2 profiles / WU over its length, intensity 1.5.
        const val GLOW_PROFILES = 2
        const val GLOW_INTENSITY = 1.5f

        // core_charge.light: sphere to 1.5 WU, (0.74,0.77,1), intensity 25 (scaled by the charge here).
        const val LIGHT_RADIUS = 1.5f
        val LIGHT_COLOR = floatArrayOf(0.7412f, 0.7686f, 1f)
        const val LIGHT_INTENSITY = 25f

        // Not in the tags: glow fade when the charge ends, and how long a charge lives unfed.
        const val FADE_TIME = 0.15f
        const val UNFED_TIMEOUT = 0.1f
        const val MAX_EMIT_PER_FRAME = 8

        var counter = 0
    }

    /** A particle with its position in the muzzle's frame (along / across, Reach units). */
    private class Local(val sprite: RuptureSprite, val along: Float, val across: Float, val turn: Float)

    val sprites = ArrayList<RuptureSprite>()
    private val glows = Array(GLOW_PROFILES) { RuptureSprite(SpriteKind.BOMBARD_CHARGE_GLOW) }
    private val light = RuptureSprite(SpriteKind.BOMBARD_CHARGE_GLOW)
    private val locals = ArrayList<Local>()
    private val worlds = ArrayList<RuptureSprite>()

    private val random = Random(System.nanoTime() xor (counter++ * 7919L))
    private val color = FloatArray(3)
    private val muzzle = Vector2f()
    private var facing = 0f
    private var level = 0f
    private var charging = false
    private var unfed = 0f
    private var glowLevel = 0f
    private var plasmaDue = 0f
    private var coreDue = 0f
    private var boltDue = 0f

    val isDone: Boolean get() = !charging && glowLevel <= 0f && locals.isEmpty() && worlds.isEmpty()

    /** This frame's state: [chargeLevel] 0..1, [isCharging] (chargeup running), the muzzle and its facing in degrees. */
    fun feed(chargeLevel: Float, isCharging: Boolean, x: Float, y: Float, facingDegrees: Float) {
        level = chargeLevel
        charging = isCharging
        muzzle.set(x, y)
        facing = Math.toRadians(facingDegrees.toDouble()).toFloat()
        unfed = 0f
    }

    private fun rand(min: Float, max: Float): Float = min + (max - min) * random.nextFloat()

    fun advance(dt: Float) {
        unfed += dt
        if (unfed > UNFED_TIMEOUT) charging = false
        val c = if (charging) level.coerceIn(0f, 1f) else 0f
        glowLevel = if (charging) c else max(0f, glowLevel - dt / FADE_TIME)

        if (charging) {
            plasmaDue = emit(plasmaDue + BombardCurves.CHARGE_PLASMA_RATE.at(c) * dt) { spawnPlasma() }
            coreDue = emit(coreDue + CORE_RATE * dt) { spawnCore(c) }
            boltDue = emit(boltDue + BombardCurves.CHARGE_BOLT_RATE.at(c) * dt) { spawnBolt(c) }
        } else {
            plasmaDue = 0f
            coreDue = 0f
            boltDue = 0f
        }

        val cosF = cos(facing)
        val sinF = sin(facing)
        val localIterator = locals.iterator()
        while (localIterator.hasNext()) {
            val p = localIterator.next()
            val s = p.sprite
            s.age += dt
            if (s.age >= s.life) {
                localIterator.remove()
                continue
            }
            s.x = muzzle.x + (cosF * p.along - sinF * p.across) * unit
            s.y = muzzle.y + (sinF * p.along + cosF * p.across) * unit
            s.angle = facing + p.turn
            update(s, s.age / s.life, glowLevel)
        }
        val worldIterator = worlds.iterator()
        while (worldIterator.hasNext()) {
            val s = worldIterator.next()
            s.age += dt
            if (s.age >= s.life) {
                worldIterator.remove()
                continue
            }
            s.angle += s.phase * dt
            update(s, s.age / s.life, glowLevel)
        }
        updateGlows(glowLevel, cosF, sinF)

        sprites.clear()
        for (p in locals) sprites.add(p.sprite)
        sprites.addAll(worlds)
        if (glowLevel > 0f) {
            sprites.add(light)
            sprites.addAll(glows)
        }
    }

    /** Emits floor([due]) particles with [spawn]; returns the remainder. */
    private inline fun emit(due: Float, spawn: () -> Unit): Float {
        val count = min(due.toInt(), MAX_EMIT_PER_FRAME)
        repeat(count) { spawn() }
        return if (due.toInt() > MAX_EMIT_PER_FRAME) 0f else due - count
    }

    private fun newSprite(kind: SpriteKind, life: Float): RuptureSprite {
        val s = RuptureSprite(kind)
        s.life = life
        s.random = random.nextFloat()
        s.random2 = random.nextFloat()
        s.noiseOffset = random.nextFloat()
        return s
    }

    private fun spawnPlasma() {
        val s = newSprite(SpriteKind.BOMBARD_CHARGE_PLASMA, BombardLook.lerp(0.75f, 0.35f, random.nextFloat()))
        val across = rand(-PLASMA_RADIUS, PLASMA_RADIUS)
        locals.add(Local(s, rand(-PLASMA_HALF_LENGTH, PLASMA_HALF_LENGTH), across, rand(0f, 2f * PI.toFloat())))
    }

    private fun spawnCore(c: Float) {
        val s = newSprite(SpriteKind.BOMBARD_CHARGE_CORE, CORE_LIFE)
        s.x = muzzle.x
        s.y = muzzle.y
        s.baseSize = BombardCurves.CHARGE_CORE_SIZE.at(c) * unit
        s.angle = rand(0f, 2f * PI.toFloat())
        s.phase = rand(0f, 0.15f) * 2f * PI.toFloat()
        worlds.add(s)
    }

    private fun spawnBolt(c: Float) {
        val s = newSprite(SpriteKind.BOMBARD_CHARGE_BOLT, BombardCurves.CHARGE_BOLT_LIFE.at(c))
        s.baseSize = BombardCurves.CHARGE_BOLT_SIZE.at(c) * unit
        s.baseAlpha = BombardCurves.CHARGE_BOLT_ALPHA.at(c)
        val side = if (random.nextBoolean()) 1f else -1f
        val turn = Math.toRadians((side * BOLT_SWING * (1f - c) + rand(-BOLT_SPREAD, BOLT_SPREAD)).toDouble()).toFloat()
        locals.add(Local(s, 0f, rand(-BOLT_RADIUS, BOLT_RADIUS), turn))
    }

    private fun update(s: RuptureSprite, x: Float, c: Float) {
        when (s.kind) {
            SpriteKind.BOMBARD_CHARGE_PLASMA -> {
                // particle scale follows the live charge (input: effect a scale)
                s.halfLength = PLASMA_SIZE * 0.5f * BombardCurves.CHARGE_PLASMA_SCALE.at(c) * unit
                s.halfWidth = s.halfLength
                BombardCurves.PLASMA_COLOR.at(x, color)
                for (k in 0..2) s.color[k] = color[k] * PLASMA_INTENSITY
                s.alpha = BombardCurves.PLASMA_ALPHA.at(x)
                s.blackPoint = BombardCurves.CHARGE_PLASMA_BLACK_POINT.at(x)
                s.paletteV = s.random
                s.uvScale = 0.75f + 0.5f * x
            }
            SpriteKind.BOMBARD_CHARGE_CORE -> {
                s.halfLength = s.baseSize * 0.5f * BombardCurves.CHARGE_CORE_SCALE.at(x)
                s.halfWidth = s.halfLength
                BombardCurves.CORE_COLOR.at(x, color)
                val intensity = BombardCurves.CORE_INTENSITY.at(x)
                for (k in 0..2) s.color[k] = color[k] * intensity
                s.alpha = 1f
                s.paletteV = s.random
                s.uvScale = 0.75f + 0.5f * x
            }
            SpriteKind.BOMBARD_CHARGE_BOLT -> {
                s.halfLength = s.baseSize * 0.5f
                s.halfWidth = s.halfLength
                BombardCurves.BOLT_COLOR.at(x, color)
                for (k in 0..2) s.color[k] = color[k] * BOLT_INTENSITY
                s.alpha = s.baseAlpha * BombardCurves.BOLT_ALPHA.at(x)
                s.blackPoint = BombardCurves.CHARGE_BOLT_BLACK_POINT.at(x)
                s.paletteV = s.random
                s.uvScale = 0.75f + 0.5f * x
            }
            else -> {}
        }
    }

    private fun updateGlows(c: Float, cosF: Float, sinF: Float) {
        val length = BombardCurves.CHARGE_GLOW_LENGTH.at(c)
        val thickness = BombardCurves.CHARGE_GLOW_THICKNESS.at(c)
        BombardCurves.CHARGE_GLOW_COLOR.at(c, color)
        for (i in glows.indices) {
            val s = glows[i]
            val along = (i + 0.5f) / glows.size * length * unit
            s.x = muzzle.x + cosF * along
            s.y = muzzle.y + sinF * along
            s.angle = 0f
            s.halfLength = thickness * unit
            s.halfWidth = s.halfLength
            for (k in 0..2) s.color[k] = color[k] * GLOW_INTENSITY
            s.alpha = if (c > 0f) 1f else 0f
        }
        light.x = muzzle.x
        light.y = muzzle.y
        light.angle = 0f
        light.halfLength = LIGHT_RADIUS * unit
        light.halfWidth = light.halfLength
        for (k in 0..2) light.color[k] = LIGHT_COLOR[k] * LIGHT_INTENSITY * BombardLook.LIGHT_SCALE * c
        light.alpha = if (c > 0f) 1f else 0f
    }
}

/**
 * A one-shot burst of the Reach AA gun: the firing flash ([muzzle], `firing.effect` flash, lens flare
 * and light volume events) or the round's detonation ([impact],
 * `fx\fx_library\explosions\covenant_explosion_artillery_huge`). No smoke (developer preference), no
 * metal chips / debris models, no screen effects; the core firing and the side vents (inside the
 * turret body) are not built. Sprites carry world positions; a negative age is the event's delay;
 * `phase` holds a sprite's spin (rad/s).
 */
internal class ArtilleryBurst private constructor(private val unit: Float) {
    val sprites = ArrayList<RuptureSprite>()
    val isDone: Boolean get() = sprites.isEmpty()

    private val random = Random(System.nanoTime() xor (counter++ * 7919L))
    private val color = FloatArray(3)
    private val color2 = FloatArray(3)
    private var originX = 0f
    private var originY = 0f
    private var dirX = 1f
    private var dirY = 0f

    companion object {
        private var counter = 0

        // --- firing.effect ---
        // muzzle_flash_long_soft (Ghost): 1, 0.125 s, size 5, intensity 2, along the barrel.
        private const val MUZZLE_LIFE = 0.125f
        private const val MUZZLE_SIZE = 5f
        private const val MUZZLE_INTENSITY = 2f
        // plasma_pulse: 5-7, tube radius 1, life 0.75 / 0.35 s, speed 30 / 0 WU/s, size 2.5 / 0.5.
        private const val PULSE_MIN = 5
        private const val PULSE_MAX = 7
        // flash_large: 1, 0.15 s, size 8, (0.77,0.75,1).
        private const val FLASH_LIFE = 0.15f
        private const val FLASH_SIZE = 8f
        private val FLASH_COLOR = floatArrayOf(0.7686f, 0.7529f, 1f)
        // plasma_gas at the muzzle: 25 / 10, tube radius 1, cone 20 deg, life 0.85 / 0.5 s, speed 30 / 2, size 1.5 / 0.85.
        private const val GAS_MIN = 10
        private const val GAS_MAX = 25
        private const val GAS_CONE = 20f
        // lens_flare event (0.25 s): big_glow (0.15,0.13,1) and anamorphic (0.25,0.26,1); curves read back to front.
        private const val MUZZLE_FLARE_LIFE = 0.25f
        private val BIG_GLOW_COLOR = floatArrayOf(0.1535f, 0.1255f, 1f)
        private val MUZZLE_ANAMORPHIC_COLOR = floatArrayOf(0.2471f, 0.2584f, 1f)
        private const val ANAMORPHIC_ASPECT = 0.25f
        // light_volume event (0.95 s): muzzle_flash.light_volume_system, 1.5 profiles / WU over 10 -> 20 WU from 0.5.
        private const val MUZZLE_GLOW_LIFE = 0.95f
        private const val MUZZLE_GLOW_PROFILES = 24
        private const val MUZZLE_GLOW_OFFSET = 0.5f
        // firing.light: sphere to 12 WU, 0.5 s.
        private const val FIRING_LIGHT_LIFE = 0.5f
        private const val FIRING_LIGHT_RADIUS = 12f

        // --- covenant_explosion_artillery_huge.effect ---
        // flash: explosive_flash, 0.2 s, size 4.
        private const val BLAST_FLASH_LIFE = 0.2f
        private const val BLAST_FLASH_SIZE = 4f
        // fireball (0.05 s delay): 10 fireball_covenant_artillery_huge, life 0.75-1.25 s, size 1.5 / 0.9, spin 0.2.
        private const val FIREBALL_DELAY = 0.05f
        private const val FIREBALLS = 10
        // ... and 10 plasma_gas: radius 0.35, speed 8-12, life 1-1.8 s, size 1-1.5, scale 0.5 -> 2, white -> (0.51,0.57,1).
        private const val BLAST_GASES = 10
        private val BLAST_GAS_TINT = floatArrayOf(0.5098f, 0.5686f, 1f)
        // plasma (0.15 s delay): 6 plasma_blue_huge, life 3 / 1 s, speed 8 / 0, size 1.25 / 0.85, alpha 0.5, intensity 2.
        private const val HUGE_DELAY = 0.15f
        private const val HUGES = 6
        private const val HUGE_ALPHA = 0.5f
        private const val HUGE_INTENSITY = 2f
        // newticles (0.05 s): sparks_huge (3000 / s: 150; explosion_covenant_spark, sphere, 8-10 WU/s,
        // 2.5-4 s, drag 1.5, fade from 75 %) and sparks_long_huge (100 / s: 5; explosion_covenant_spark_long,
        // 12 WU/s, 0.5-0.75 s, drag 2, motion blur 5). Blue (0.18,0.31,1) x 15. Their sizes (0.015-0.03 WU)
        // are sub-pixel here: floored (placeholder), as is the huge spark count. [SPARKS] is the default
        // count; the Plasma Cannon spawns fewer (PlasmaCannonOnHit.IMPACT_SPARKS).
        const val SPARKS = 60
        private const val LONG_SPARKS = 5
        private const val SPARK_SIZE = 0.2f
        private const val LONG_SPARK_SIZE = 0.3f
        private val SPARK_COLOR = floatArrayOf(0.1812f, 0.3091f, 1f)
        private const val SPARK_INTENSITY = 15f
        private const val SPARK_BLUR = 0.02f
        private const val LONG_SPARK_BLUR = 0.1f
        // lens_flare (0.3 s): huge_glow (0.30,0.34,1), read back to front.
        private const val HUGE_GLOW_LIFE = 0.3f
        private val HUGE_GLOW_COLOR = floatArrayOf(0.298f, 0.3416f, 1f)
        // covenant_explosion_huge.light: sphere to 4 WU, 2 s (the default; the Plasma Cannon's is shorter).
        const val BLAST_LIGHT_LIFE = 2f
        private const val BLAST_LIGHT_RADIUS = 4f

        // Not in the tags (physics templates): velocity falls off as exp(-drag t). Placeholders.
        private const val PULSE_DRAG = 2f
        private const val GAS_DRAG = 2f
        private const val FIREBALL_DRAG = 3f
        private const val HUGE_DRAG = 2.5f

        fun muzzle(location: Vector2f, facing: Float, unit: Float): ArtilleryBurst {
            val burst = ArtilleryBurst(unit)
            burst.spawnMuzzle(location.x, location.y, Math.toRadians(facing.toDouble()).toFloat())
            return burst
        }

        /**
         * The detonation at [location], [unit] su per Reach unit, with [sparks] `sparks_huge` sparks and a
         * blast light living [lightLife] s (defaults: the Plasma Bombardment's).
         */
        fun impact(location: Vector2f, unit: Float, sparks: Int = SPARKS, lightLife: Float = BLAST_LIGHT_LIFE): ArtilleryBurst {
            val burst = ArtilleryBurst(unit)
            burst.spawnImpact(location.x, location.y, sparks, lightLife)
            return burst
        }
    }

    private fun rand(min: Float, max: Float): Float = min + (max - min) * random.nextFloat()

    private fun spawn(kind: SpriteKind, x: Float, y: Float, life: Float, size: Float, delay: Float = 0f): RuptureSprite {
        val s = RuptureSprite(kind)
        s.x = x
        s.y = y
        s.life = life
        s.age = -delay
        s.baseSize = size
        s.random = random.nextFloat()
        s.random2 = random.nextFloat()
        s.noiseOffset = random.nextFloat()
        s.angle = rand(0f, 2f * PI.toFloat())
        sprites.add(s)
        return s
    }

    /** A particle on a disc of [radius] Reach units, flying outward at [speed] units/s. */
    private fun spawnOutward(
        kind: SpriteKind, x: Float, y: Float, radius: Float, speed: Float, drag: Float, life: Float,
        size: Float, spin: Float, delay: Float = 0f
    ): RuptureSprite {
        val heading = rand(0f, 2f * PI.toFloat())
        val r = sqrt(random.nextFloat()) * radius * unit
        val s = spawn(kind, x + cos(heading) * r, y + sin(heading) * r, life, size * unit, delay)
        s.vx = cos(heading) * speed * unit
        s.vy = sin(heading) * speed * unit
        s.drag = drag
        s.phase = rand(-spin, spin) * 2f * PI.toFloat()
        return s
    }

    private fun spawnMuzzle(x: Float, y: Float, heading: Float) {
        originX = x
        originY = y
        dirX = cos(heading)
        dirY = sin(heading)
        val acrossX = -dirY
        val acrossY = dirX

        spawn(SpriteKind.BOMBARD_MUZZLE, x, y, MUZZLE_LIFE, MUZZLE_SIZE * unit).angle = heading
        repeat(PULSE_MIN + random.nextInt(PULSE_MAX - PULSE_MIN + 1)) {
            val c = random.nextFloat()
            val r = rand(-1f, 1f) * unit
            val s = spawn(
                SpriteKind.BOMBARD_PULSE, x + acrossX * r, y + acrossY * r,
                BombardLook.lerp(0.75f, 0.35f, c), BombardLook.lerp(2.5f, 0.5f, c) * unit
            )
            val speed = BombardLook.lerp(30f, 0f, c) * unit
            s.vx = dirX * speed
            s.vy = dirY * speed
            s.drag = PULSE_DRAG
            s.angle = heading
        }
        spawn(SpriteKind.BOMBARD_FLASH, x, y, FLASH_LIFE, FLASH_SIZE * unit)
        repeat(BombardLook.lerp(GAS_MAX.toFloat(), GAS_MIN.toFloat(), random.nextFloat()).roundToInt()) {
            val c = random.nextFloat()
            val r = rand(-1f, 1f) * unit
            val s = spawn(
                SpriteKind.BOMBARD_GAS, x + acrossX * r, y + acrossY * r,
                BombardLook.lerp(0.85f, 0.5f, c), BombardLook.lerp(1.5f, 0.85f, c) * unit
            )
            val angle = heading + Math.toRadians(rand(-GAS_CONE, GAS_CONE).toDouble()).toFloat()
            val speed = BombardLook.lerp(30f, 2f, c) * unit
            s.vx = cos(angle) * speed
            s.vy = sin(angle) * speed
            s.drag = GAS_DRAG
            s.phase = rand(-0.1f, 0.1f) * 2f * PI.toFloat()
        }
        repeat(MUZZLE_GLOW_PROFILES) { i ->
            val s = spawn(SpriteKind.BOMBARD_MUZZLE_GLOW, x, y, MUZZLE_GLOW_LIFE, 0f)
            s.random2 = (i + 0.5f) / MUZZLE_GLOW_PROFILES
            s.angle = 0f
        }
        spawn(SpriteKind.BOMBARD_BIG_GLOW, x, y, MUZZLE_FLARE_LIFE, 0f).angle = 0f
        spawn(SpriteKind.BOMBARD_ANAMORPHIC, x, y, MUZZLE_FLARE_LIFE, 0f).angle = 0f
        spawn(SpriteKind.BOMBARD_LIGHT, x, y, FIRING_LIGHT_LIFE, 0f).angle = 0f
    }

    private fun spawnImpact(x: Float, y: Float, sparks: Int, lightLife: Float) {
        spawn(SpriteKind.ARTILLERY_FLASH, x, y, BLAST_FLASH_LIFE, BLAST_FLASH_SIZE * unit)
        spawn(SpriteKind.ARTILLERY_LIGHT, x, y, lightLife, 0f).angle = 0f
        spawn(SpriteKind.ARTILLERY_GLOW, x, y, HUGE_GLOW_LIFE, 0f).angle = 0f
        repeat(FIREBALLS) {
            val c = random.nextFloat()
            spawnOutward(
                SpriteKind.ARTILLERY_FIREBALL, x, y, BombardCurves.FIREBALL_RADIUS.at(c), BombardCurves.FIREBALL_SPEED.at(c),
                FIREBALL_DRAG, BombardLook.lerp(0.75f, 1.25f, c), BombardLook.lerp(1.5f, 0.9f, c), 0.2f, FIREBALL_DELAY
            )
        }
        repeat(BLAST_GASES) {
            spawnOutward(
                SpriteKind.ARTILLERY_GAS, x, y, 0.35f, rand(8f, 12f), GAS_DRAG, rand(1f, 1.8f), rand(1f, 1.5f), 0.1f,
                FIREBALL_DELAY
            )
        }
        repeat(HUGES) {
            val c = random.nextFloat()
            spawnOutward(
                SpriteKind.ARTILLERY_PLASMA, x, y, BombardCurves.HUGE_RADIUS.at(c), BombardLook.lerp(8f, 0f, c),
                HUGE_DRAG, BombardLook.lerp(3f, 1f, c), BombardLook.lerp(1.25f, 0.85f, c), 0.1f, HUGE_DELAY
            )
        }
        repeat(sparks) {
            spawnOutward(SpriteKind.ARTILLERY_SPARK, x, y, 2f, rand(8f, 10f), 1.5f, rand(2.5f, 4f), SPARK_SIZE, 0f)
        }
        repeat(LONG_SPARKS) {
            spawnOutward(SpriteKind.ARTILLERY_SPARK_LONG, x, y, 1f, 12f, 2f, rand(0.5f, 0.75f), LONG_SPARK_SIZE, 0f)
        }
    }

    fun advance(dt: Float) {
        val iterator = sprites.iterator()
        while (iterator.hasNext()) {
            val s = iterator.next()
            s.age += dt
            if (s.age >= s.life) {
                iterator.remove()
                continue
            }
            if (s.age < 0f) {
                s.alpha = 0f
                continue
            }
            s.x += s.vx * dt
            s.y += s.vy * dt
            if (s.drag > 0f) {
                val keep = exp(-s.drag * dt)
                s.vx *= keep
                s.vy *= keep
            }
            s.angle += s.phase * dt
            update(s, s.age / s.life)
        }
    }

    private fun round(s: RuptureSprite, radius: Float) {
        s.halfLength = radius
        s.halfWidth = radius
    }

    private fun update(s: RuptureSprite, x: Float) {
        when (s.kind) {
            // --- firing ---
            SpriteKind.BOMBARD_MUZZLE -> {
                round(s, s.baseSize * 0.5f * BombardCurves.MUZZLE_SCALE.at(x))
                // Reach centres it on the muzzle (inside the barrel there); here its base sits at the muzzle.
                s.offsetAlong = s.halfLength
                BombardCurves.MUZZLE_TINT.at(x, color)
                for (c in 0..2) s.color[c] = color[c] * MUZZLE_INTENSITY
                s.alpha = BombardCurves.MUZZLE_ALPHA.at(x)
                s.paletteV = 0.5f
            }
            SpriteKind.BOMBARD_PULSE -> {
                round(s, s.baseSize * 0.5f * BombardCurves.PULSE_SCALE.at(x))
                BombardCurves.PULSE_COLOR.at(x, color)
                val intensity = BombardCurves.PULSE_INTENSITY.at(x)
                for (c in 0..2) s.color[c] = color[c] * intensity
                s.alpha = BombardCurves.PULSE_ALPHA.at(x)
                s.blackPoint = BombardCurves.PULSE_BLACK_POINT.at(x)
                s.paletteV = s.random
                s.uvScale = 0.75f + 0.5f * x
            }
            SpriteKind.BOMBARD_FLASH -> {
                round(s, s.baseSize * 0.5f * BombardCurves.FLASH_SCALE.at(x))
                FLASH_COLOR.copyInto(s.color)
                s.alpha = BombardCurves.FLASH_ALPHA.at(x)
            }
            SpriteKind.BOMBARD_GAS -> {
                round(s, s.baseSize * 0.5f * BombardCurves.GAS_SCALE.at(x))
                BombardCurves.GAS_COLOR.at(x, color)
                color.copyInto(s.color)
                val dim = BombardCurves.GAS_ALPHA_DIM.at(x)
                s.alpha = (dim + (BombardCurves.GAS_ALPHA_HOT.at(x) - dim) * s.random2) * BombardCurves.GAS_PARTICLE_ALPHA.at(x)
                val dimPoint = BombardCurves.GAS_BLACK_POINT_DIM.at(x)
                s.blackPoint = dimPoint + (BombardCurves.GAS_BLACK_POINT_HOT.at(x) - dimPoint) * s.random2
            }
            SpriteKind.BOMBARD_MUZZLE_GLOW -> {
                val p = s.random2
                val along = (MUZZLE_GLOW_OFFSET + p * BombardCurves.MUZZLE_GLOW_LENGTH.at(x)) * unit
                s.x = originX + dirX * along
                s.y = originY + dirY * along
                val start = BombardCurves.MUZZLE_GLOW_THICKNESS_START.at(p)
                round(s, (start + (BombardCurves.MUZZLE_GLOW_THICKNESS_END.at(p) - start) * x) * unit)
                BombardCurves.MUZZLE_GLOW_COLOR_START.at(p, color)
                BombardCurves.MUZZLE_GLOW_COLOR_END.at(p, color2)
                val intensity = BombardCurves.MUZZLE_GLOW_INTENSITY.at(x)
                for (c in 0..2) s.color[c] = (color[c] + (color2[c] - color[c]) * x) * intensity
                s.alpha = BombardCurves.MUZZLE_GLOW_ALPHA.at(p)
            }
            SpriteKind.BOMBARD_BIG_GLOW -> {
                val u = 1f - x
                round(s, BombardCurves.BIG_GLOW_RADIUS.at(u) * unit)
                val brightness = BombardCurves.BIG_GLOW_BRIGHTNESS.at(u)
                for (c in 0..2) s.color[c] = BIG_GLOW_COLOR[c] * brightness
                s.alpha = 1f
            }
            SpriteKind.BOMBARD_ANAMORPHIC -> {
                val u = 1f - x
                s.halfLength = BombardCurves.MUZZLE_ANAMORPHIC_RADIUS.at(u) * unit
                s.halfWidth = s.halfLength * ANAMORPHIC_ASPECT
                val brightness = BombardCurves.MUZZLE_ANAMORPHIC_BRIGHTNESS.at(u)
                for (c in 0..2) s.color[c] = MUZZLE_ANAMORPHIC_COLOR[c] * brightness
                s.alpha = 1f
            }
            SpriteKind.BOMBARD_LIGHT -> {
                round(s, FIRING_LIGHT_RADIUS * unit)
                BombardCurves.FIRING_LIGHT_COLOR.at(x, color)
                val intensity = BombardCurves.FIRING_LIGHT_INTENSITY.at(x) * BombardLook.LIGHT_SCALE
                for (c in 0..2) s.color[c] = color[c] * intensity
                s.alpha = 1f
            }
            // --- impact ---
            SpriteKind.ARTILLERY_FLASH -> {
                round(s, s.baseSize * 0.5f * BombardCurves.BLAST_FLASH_SCALE.at(x))
                BombardCurves.BLAST_FLASH_TINT.at(x, color)
                val intensity = BombardCurves.EXPLOSIVE_FLASH_INTENSITY.at(x)
                for (c in 0..2) s.color[c] = color[c] * intensity
                s.alpha = BombardCurves.BLAST_FLASH_ALPHA.at(x)
            }
            SpriteKind.ARTILLERY_FIREBALL -> {
                round(s, s.baseSize * 0.5f * BombardCurves.FIREBALL_SCALE.at(x))
                val hot = BombardCurves.FIREBALL_INTENSITY_HOT.at(x)
                val intensity = hot + (BombardCurves.FIREBALL_INTENSITY_DIM.at(x) - hot) * s.random2
                for (c in 0..2) s.color[c] = intensity
                s.alpha = BombardCurves.FIREBALL_ALPHA.at(x) * BombardCurves.FIREBALL_PARTICLE_ALPHA.at(x)
                s.blackPoint = BombardCurves.FIREBALL_BLACK_POINT.at(x)
                val fast = BombardCurves.FIREBALL_PALETTE_FAST.at(x)
                s.paletteV = fast + (BombardCurves.FIREBALL_PALETTE_SLOW.at(x) - fast) * s.random2
                s.uvScale = 0.35f + 0.5f * x
            }
            SpriteKind.ARTILLERY_GAS -> {
                round(s, s.baseSize * 0.5f * BombardLook.lerp(0.5f, 2f, x))
                BombardCurves.GAS_COLOR.at(x, color)
                for (c in 0..2) s.color[c] = color[c] * BombardLook.lerp(1f, BLAST_GAS_TINT[c], x)
                s.alpha = BombardCurves.BLAST_GAS_ALPHA.at(x) * BombardCurves.GAS_PARTICLE_ALPHA.at(x)
                s.blackPoint = BombardCurves.BLAST_GAS_BLACK_POINT.at(x)
            }
            SpriteKind.ARTILLERY_PLASMA -> {
                round(s, s.baseSize * 0.5f * BombardCurves.HUGE_SCALE.at(x))
                BombardCurves.HUGE_COLOR.at(x, color)
                for (c in 0..2) s.color[c] = color[c] * HUGE_INTENSITY
                s.alpha = HUGE_ALPHA * BombardCurves.HUGE_ALPHA.at(x)
                val hot = BombardCurves.HUGE_BLACK_POINT_HOT.at(x)
                s.blackPoint = hot + (BombardCurves.HUGE_BLACK_POINT_DIM.at(x) - hot) * s.random2
                s.paletteV = s.random
                s.uvScale = 0.75f + 0.5f * x
            }
            SpriteKind.ARTILLERY_SPARK, SpriteKind.ARTILLERY_SPARK_LONG -> {
                // The streak runs along the texture's u, laid across the sprite's angle (see TorpedoBurst).
                val speed = sqrt(s.vx * s.vx + s.vy * s.vy)
                val blur = if (s.kind == SpriteKind.ARTILLERY_SPARK_LONG) LONG_SPARK_BLUR else SPARK_BLUR
                s.angle = atan2(s.vy, s.vx) + PI.toFloat() * 0.5f
                s.halfLength = s.baseSize * 0.25f
                s.halfWidth = s.baseSize * 0.5f + speed * blur
                for (c in 0..2) s.color[c] = SPARK_COLOR[c] * SPARK_INTENSITY
                s.alpha = if (s.kind == SpriteKind.ARTILLERY_SPARK_LONG) 1f - x else min(1f, (1f - x) / 0.25f)
            }
            SpriteKind.ARTILLERY_GLOW -> {
                val u = 1f - x
                round(s, BombardCurves.HUGE_GLOW_RADIUS.at(u) * unit)
                val brightness = BombardCurves.HUGE_GLOW_BRIGHTNESS.at(u)
                for (c in 0..2) s.color[c] = HUGE_GLOW_COLOR[c] * brightness
                s.alpha = 1f
            }
            SpriteKind.ARTILLERY_LIGHT -> {
                round(s, BLAST_LIGHT_RADIUS * unit)
                BombardCurves.BLAST_LIGHT_COLOR.at(x, color)
                val intensity = BombardCurves.BLAST_LIGHT_INTENSITY.at(x) * BombardLook.LIGHT_SCALE
                for (c in 0..2) s.color[c] = color[c] * intensity
                s.alpha = 1f
            }
            else -> {}
        }
    }
}

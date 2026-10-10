package org.combustiblelemon.covenantremnants.utils.render.torpedo

import com.fs.starfarer.api.combat.DamagingProjectileAPI
import com.fs.starfarer.api.combat.MissileAPI
import com.fs.starfarer.api.combat.WeaponAPI
import org.combustiblelemon.covenantremnants.utils.render.rupture.RIBBON_STRIDE
import org.combustiblelemon.covenantremnants.utils.render.rupture.RuptureSprite
import org.combustiblelemon.covenantremnants.utils.render.rupture.SpriteKind
import org.lwjgl.util.vector.Vector2f
import java.util.Random
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The Halo: Reach space Phantom plasma torpedo (m45), remade for the three plasma torpedoes
 * (developer 2026-10-02: all three, scaled by weapon size). Reach units are converted with
 * [unitFor]; timings, sizes, colours and curves come from the tags ([TorpedoCurves],
 * `assets/reference/plasma-torpedo/reach-data/spec.md`).
 */
internal object TorpedoLook {
    fun unitFor(size: WeaponAPI.WeaponSize): Float = TorpedoSize.forWeapon(size).unit

    private fun hash(n: Int): Float {
        var h = n * 374761393 + 668265263
        h = (h xor (h ushr 13)) * 1274126177
        return ((h xor (h ushr 16)) and 0x7fffffff) / 2147483647f
    }

    /** Smooth value noise 0..1 over [t] (Reach's periodic "wander" functions). */
    fun noise(t: Float, salt: Int): Float {
        val i = floor(t).toInt()
        val f = t - i
        val u = f * f * (3f - 2f * f)
        val a = hash(i * 31 + salt)
        return a + (hash((i + 1) * 31 + salt) - a) * u
    }
}

/**
 * The three plasma torpedoes by launcher size (launcher polish, developer 2026-10-09): [unit] =
 * Starsector units per Reach unit for the look (placeholders, 2026-10-02; heavy 8 -> 12 on
 * 2026-10-10), [meltRadius] = the armor melt radius in su, [pitch] = the fire / impact sound pitch
 * (the fire pitch lives in sounds.json), [style] = the size's look, [loopId] = its in-flight hum.
 */
internal enum class TorpedoSize(
    val specId: String,
    val unit: Float,
    val meltRadius: Float,
    val pitch: Float,
    val style: TorpedoStyle,
    val loopId: String
) {
    LIGHT("covenant_remnants_plasmatorpedo_light", 3f, 50f, 1.15f, TorpedoStyle.LIGHT, LOOP),
    MEDIUM("covenant_remnants_plasmatorpedo", 5f, 75f, 1f, TorpedoStyle.REACH, LOOP),
    HEAVY("covenant_remnants_plasmatorpedo_heavy", 12f, 125f, 0.85f, TorpedoStyle.HEAVY, LOOP_HEAVY);

    companion object {
        fun forSpec(id: String?): TorpedoSize? = entries.firstOrNull { it.specId == id }

        fun forWeapon(size: WeaponAPI.WeaponSize): TorpedoSize = when (size) {
            WeaponAPI.WeaponSize.SMALL -> LIGHT
            WeaponAPI.WeaponSize.MEDIUM -> MEDIUM
            WeaponAPI.WeaponSize.LARGE -> HEAVY
        }
    }
}

// In-flight hums (sounds.json): the Reach space Phantom torpedo loop; the heavy's louder and deeper.
private const val LOOP = "covenant_remnants_plasma_torpedo_loop"
private const val LOOP_HEAVY = "covenant_remnants_plasma_torpedo_loop_heavy"

/**
 * Recolouring of the Reach torpedo look per size (developer 2026-10-10: colour + shape per size,
 * not red). Only cool hues move: full weight 180-320 deg, fading to none at 160 / 340, so the warm
 * Reach accents (salmon body tail, orange trail flash and sparks) and whites stay; a moved hue never
 * passes [HUE_MIN] / [HUE_MAX] (pinks would wrap into salmon, blues into green). The palette PNGs
 * `*_light` / `*_heavy` in graphics/fx/plasma_torpedo were made offline with the same function.
 */
internal object TorpedoHue {
    private const val BAND_START = 160f
    private const val BAND_FULL_START = 180f
    private const val BAND_FULL_END = 320f
    private const val BAND_END = 340f
    private const val HUE_MIN = 185f
    private const val HUE_MAX = 325f

    private fun band(hue: Float): Float {
        val rise = ((hue - BAND_START) / (BAND_FULL_START - BAND_START)).coerceIn(0f, 1f)
        val fall = ((BAND_END - hue) / (BAND_END - BAND_FULL_END)).coerceIn(0f, 1f)
        return rise * fall
    }

    /** Shifts the cool hue of [color] (linear RGB, HDR allowed) by [degrees], in place. */
    fun shift(color: FloatArray, degrees: Float) {
        if (degrees == 0f) return
        val r = color[0]
        val g = color[1]
        val b = color[2]
        val value = max(r, max(g, b))
        val delta = value - min(r, min(g, b))
        if (value <= 0f || delta <= 0f) return
        var hue = when (value) {
            r -> 60f * (((g - b) / delta) % 6f)
            g -> 60f * ((b - r) / delta + 2f)
            else -> 60f * ((r - g) / delta + 4f)
        }
        if (hue < 0f) hue += 360f
        val weight = band(hue)
        if (weight <= 0f) return
        var moved = hue + degrees * weight
        moved = if (degrees > 0f) min(moved, max(hue, HUE_MAX)) else max(moved, min(hue, HUE_MIN))
        moved = ((moved % 360f) + 360f) % 360f
        val saturation = delta / value
        val sector = moved / 60f
        val i = floor(sector).toInt() % 6
        val f = sector - floor(sector)
        val p = value * (1f - saturation)
        val q = value * (1f - saturation * f)
        val t = value * (1f - saturation * (1f - f))
        when (i) {
            0 -> { color[0] = value; color[1] = t; color[2] = p }
            1 -> { color[0] = q; color[1] = value; color[2] = p }
            2 -> { color[0] = p; color[1] = value; color[2] = t }
            3 -> { color[0] = p; color[1] = q; color[2] = value }
            4 -> { color[0] = t; color[1] = p; color[2] = value }
            else -> { color[0] = value; color[1] = p; color[2] = q }
        }
    }

    /** Blends [color] toward white at its own brightness (its brightest channel) by [amount] 0..1. */
    fun whiten(color: FloatArray, amount: Float) {
        if (amount <= 0f) return
        val value = max(color[0], max(color[1], color[2]))
        for (c in 0..2) color[c] += (value - color[c]) * amount
    }
}

/**
 * One torpedo size's look on top of the Reach torpedo (developer 2026-10-10): [hueShift] for
 * [TorpedoHue], [headWhiten] = how far the head (star flare, body front, trail head) goes white,
 * [intensity] = flight glow multiplier, [trailWidth] / [trailLife] / [bodyThickness] = shape
 * multipliers, [halo] = the heavy's breathing glow, and the sprite kinds of its palettized parts.
 * [REACH] is the unchanged 2026-10-02 look (medium torpedo, Plasma Cannon splash). Placeholders.
 */
internal class TorpedoStyle(
    val hueShift: Float,
    val headWhiten: Float,
    val intensity: Float,
    val trailWidth: Float,
    val trailLife: Float,
    val bodyThickness: Float,
    val halo: Boolean,
    val muzzleRoundKind: SpriteKind,
    val fireballKind: SpriteKind,
    val plasmaKind: SpriteKind
) {
    /** Recolours [color] in place: the hue shift, then [whiten] toward white. */
    fun tint(color: FloatArray, whiten: Float = 0f) {
        TorpedoHue.shift(color, hueShift)
        TorpedoHue.whiten(color, whiten)
    }

    companion object {
        val REACH = TorpedoStyle(
            0f, 0f, 1f, 1f, 1f, 1f, false,
            SpriteKind.TORPEDO_MUZZLE_ROUND, SpriteKind.TORPEDO_FIREBALL, SpriteKind.TORPEDO_PLASMA
        )

        /** Light: a small cyan-blue dart, thin short trail. */
        val LIGHT = TorpedoStyle(
            -40f, 0f, 1f, 0.6f, 0.5f, 0.7f, false,
            SpriteKind.TORPEDO_MUZZLE_ROUND_LIGHT, SpriteKind.TORPEDO_FIREBALL_LIGHT, SpriteKind.TORPEDO_PLASMA_LIGHT
        )

        /** Heavy: white-hot head, deep magenta-violet body / trail / halo, brighter. */
        val HEAVY = TorpedoStyle(
            60f, 0.6f, 1.3f, 1f, 1f, 1f, true,
            SpriteKind.TORPEDO_MUZZLE_ROUND_HEAVY, SpriteKind.TORPEDO_FIREBALL_HEAVY, SpriteKind.TORPEDO_PLASMA_HEAVY
        )
    }
}

/**
 * One torpedo in flight: `projectile.light_volume_system` (the hot_core body: glows along the
 * travel axis), `projectile.lens_flare` (a star flare) and `projectile.contrail_system` (a ribbon).
 * The body and flare follow the missile; the trail outlives it until its last point has aged out.
 * The [size]'s [TorpedoStyle] recolours and reshapes it (2026-10-10); the heavy adds a breathing
 * [halo] around the head.
 */
internal class TorpedoFlight(val missile: MissileAPI, val size: TorpedoSize) {
    private companion object {
        // hot_core: 8 Reach units long, starting 6.7 behind the projectile, 3 profiles per unit.
        const val BODY_LENGTH = 8f
        const val BODY_OFFSET = -6.7f
        const val BODY_DENSITY = 3f

        // projectile.lens_flare: star_flare, radius 3, blue-violet tint.
        const val STAR_RADIUS = 3f
        val STAR_COLOR = floatArrayOf(0.494f, 0.549f, 1f)

        // contrail_0: 15 profiles/s, 1 s life, uv tiling 0.085 along its length, scrolling 0.5/s.
        const val TRAIL_LIFE = 1f
        const val TRAIL_RATE = 15f
        const val TRAIL_U_PER_UNIT = 0.085f
        const val TRAIL_SCROLL = 0.5f

        // Not in the tags: a new trail point at least every TRAIL_MAX_SPACING Reach units too, so
        // the ribbon stays smooth through turns at Starsector speeds; and the body/flare fade when
        // the missile fizzles or fades out (Reach detonates at max range instead).
        const val TRAIL_MAX_SPACING = 2f
        const val FADE_TIME = 0.5f
        const val TRAIL_CAPACITY = 128

        // Heavy halo (not in the tags, developer 2026-10-10): a soft glow at the head, HALO_RADIUS Reach
        // units, breathing +-HALO_BREATH at HALO_RATE Hz, the star colour shifted to the size's hue.
        // Placeholders.
        const val HALO_RADIUS = 5f
        const val HALO_BREATH = 0.25f
        const val HALO_RATE = 2f
        const val HALO_INTENSITY = 1f
        const val HALO_ALPHA = 0.7f
    }

    val unit = size.unit
    val style = size.style
    private val trailLife = TRAIL_LIFE * style.trailLife

    private val profiles = (BODY_LENGTH * BODY_DENSITY).toInt()
    val body = Array(profiles) { RuptureSprite(SpriteKind.TORPEDO_BODY) }
    val star = RuptureSprite(SpriteKind.TORPEDO_STAR)
    val halo: RuptureSprite? = if (style.halo) RuptureSprite(SpriteKind.TORPEDO_HALO) else null

    private val trailX = FloatArray(TRAIL_CAPACITY)
    private val trailY = FloatArray(TRAIL_CAPACITY)
    private val trailAge = FloatArray(TRAIL_CAPACITY)
    private val trailU = FloatArray(TRAIL_CAPACITY)
    private var trailStart = 0
    private var trailCount = 0
    private var sinceEmit = 0f
    private var travelled = 0f
    private val lastPosition = Vector2f(missile.location)
    private val lastEmit = Vector2f(missile.location)
    private val color = FloatArray(3)

    /** False once the missile is gone (hit, shot down or faded); the trail then just ages out. */
    var flying = true
        private set
    private var fade = 1f
    private var emitting = true

    /** Impact sound pitch factor of the launcher size ([TorpedoSize.pitch]). */
    val pitch: Float get() = size.pitch

    /** Where the torpedo struck (its on-hit point); the impact burst is drawn there when known. */
    var hitPoint: Vector2f? = null

    /** Seconds since launch, and whether the missile was ever seen in play (a fresh one may not be yet). */
    var age = 0f
        private set
    var seenInPlay = false

    val isDone: Boolean get() = !flying && trailCount == 0

    fun end() {
        flying = false
        emitting = false
        for (s in body) s.alpha = 0f
        star.alpha = 0f
        halo?.alpha = 0f
    }

    fun advance(dt: Float) {
        age += dt
        for (i in 0 until trailCount) trailAge[(trailStart + i) % TRAIL_CAPACITY] += dt
        while (trailCount > 0 && trailAge[trailStart] >= trailLife) {
            trailStart = (trailStart + 1) % TRAIL_CAPACITY
            trailCount--
        }
        if (!flying) return

        val location = missile.location
        travelled += distance(location.x, location.y, lastPosition.x, lastPosition.y) / unit
        lastPosition.set(location)

        if (missile.isFizzling || missile.isFading) {
            emitting = false
            fade = max(0f, fade - dt / FADE_TIME)
        }
        if (emitting) {
            sinceEmit += dt
            val spacing = distance(location.x, location.y, lastEmit.x, lastEmit.y) / unit
            if (sinceEmit >= 1f / TRAIL_RATE || spacing >= TRAIL_MAX_SPACING) {
                sinceEmit = 0f
                lastEmit.set(location)
                emitTrailPoint(location.x, location.y)
            }
        }
        updateBody()
    }

    private fun emitTrailPoint(x: Float, y: Float) {
        val index: Int
        if (trailCount == TRAIL_CAPACITY) {
            index = trailStart
            trailStart = (trailStart + 1) % TRAIL_CAPACITY
        } else {
            index = (trailStart + trailCount) % TRAIL_CAPACITY
            trailCount++
        }
        trailX[index] = x
        trailY[index] = y
        trailAge[index] = 0f
        trailU[index] = travelled * TRAIL_U_PER_UNIT
    }

    private fun updateBody() {
        val facing = Math.toRadians(missile.facing.toDouble()).toFloat()
        val dirX = cos(facing)
        val dirY = sin(facing)
        val location = missile.location
        for (i in 0 until profiles) {
            val p = (i + 0.5f) / profiles
            val along = (BODY_OFFSET + p * BODY_LENGTH) * unit
            val s = body[i]
            s.x = location.x + dirX * along
            s.y = location.y + dirY * along
            s.angle = facing
            s.halfLength = TorpedoCurves.BODY_THICKNESS.at(p) * unit * style.bodyThickness
            s.halfWidth = s.halfLength
            TorpedoCurves.BODY_COLOR.at(p, color)
            style.tint(color, style.headWhiten * p * p)
            val intensity = TorpedoCurves.BODY_INTENSITY.at(p) * style.intensity
            for (c in 0..2) s.color[c] = color[c] * intensity
            s.alpha = TorpedoCurves.BODY_ALPHA.at(p) * fade
        }
        star.x = location.x
        star.y = location.y
        star.angle = 0f
        star.halfLength = STAR_RADIUS * unit
        star.halfWidth = star.halfLength
        STAR_COLOR.copyInto(color)
        style.tint(color, style.headWhiten)
        for (c in 0..2) star.color[c] = color[c] * style.intensity
        star.alpha = fade
        halo?.let { h ->
            h.x = location.x
            h.y = location.y
            h.angle = 0f
            h.halfLength = HALO_RADIUS * unit * (1f + HALO_BREATH * sin(2f * PI.toFloat() * HALO_RATE * age))
            h.halfWidth = h.halfLength
            STAR_COLOR.copyInto(color)
            style.tint(color)
            for (c in 0..2) h.color[c] = color[c] * HALO_INTENSITY
            h.alpha = HALO_ALPHA * fade
        }
    }

    /**
     * Writes the trail into [out] (RIBBON_STRIDE floats per point, head first): the missile itself
     * while it still emits, then every live point newest to oldest. Returns the point count.
     */
    fun writeTrail(out: FloatArray, time: Float): Int {
        var n = 0
        if (emitting) {
            val location = missile.location
            n = writeTrailPoint(out, n, location.x, location.y, 0f, travelled * TRAIL_U_PER_UNIT, time)
        }
        for (k in trailCount - 1 downTo 0) {
            val i = (trailStart + k) % TRAIL_CAPACITY
            n = writeTrailPoint(out, n, trailX[i], trailY[i], trailAge[i], trailU[i], time)
        }
        return n
    }

    private fun writeTrailPoint(out: FloatArray, n: Int, x: Float, y: Float, age: Float, u: Float, time: Float): Int {
        val a = min(1f, age / trailLife)
        val o = n * RIBBON_STRIDE
        out[o] = x
        out[o + 1] = y
        out[o + 2] = TorpedoCurves.TRAIL_SIZE.at(a) * 0.5f * unit * style.trailWidth
        out[o + 3] = u + TRAIL_SCROLL * time
        TorpedoCurves.TRAIL_COLOR.at(a, color)
        style.tint(color, style.headWhiten * (1f - a) * (1f - a))
        val intensity = TorpedoCurves.TRAIL_INTENSITY.at(a) * style.intensity
        out[o + 4] = color[0] * intensity
        out[o + 5] = color[1] * intensity
        out[o + 6] = color[2] * intensity
        out[o + 7] = TorpedoCurves.TRAIL_ALPHA.at(a)
        out[o + 8] = TorpedoCurves.TRAIL_BLACK_POINT.at(a)
        return n + 1
    }

    val maxTrailPoints: Int get() = TRAIL_CAPACITY + 1

    private fun distance(ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = ax - bx
        val dy = ay - by
        return sqrt(dx * dx + dy * dy)
    }
}

/**
 * A one-shot particle burst: the muzzle (`firing.effect`) or the impact (`impact.effect`). Sprites
 * carry world positions; `phase` holds a sprite's spin (rad/s), `random2` its second random. The
 * [style] recolours it per torpedo size (2026-10-10; sparks stay orange).
 */
internal class TorpedoBurst private constructor(private val unit: Float, private val style: TorpedoStyle) {
    val sprites = ArrayList<RuptureSprite>()
    val isDone: Boolean get() = sprites.isEmpty()

    private val random = Random(System.nanoTime() xor (counter++ * 7919L))
    private val salt = counter * 101
    private val color = FloatArray(3)

    companion object {
        private var counter = 0

        // muzzle_flash_round: 0.025-0.15 s, 1-3 across, intensity 1.5 (one per sheet frame here).
        private const val MUZZLE_ROUNDS = 2
        private const val MUZZLE_ROUND_INTENSITY = 1.5f
        private const val MUZZLE_ROUND_CONE = 45f

        // flash_large: 0.15 s, 3 across, light violet.
        private val MUZZLE_FLASH_COLOR = floatArrayOf(0.678f, 0.604f, 0.996f)

        // Impact systems: 15 particles each (fireball_space_covenant, plasma_blue_space_large,
        // fireball_smoke). Their physics template's second parameter is read as drag (velocity
        // falls off as exp(-drag t)); Reach does not name it.
        private const val IMPACT_PARTICLES = 15
        private const val PLASMA_FLICKER = 7.5f

        // spark_orange_space cheap particles: orange × 20, 45 Reach units/s, 0.15-1.2 s. Reach
        // spawns 200-3600/s over its 0.1 s event; IMPACT_SPARKS is a placeholder count.
        private const val IMPACT_SPARKS = 40
        private val SPARK_COLOR = floatArrayOf(0.815f, 0.284f, 0.082f)
        private const val SPARK_INTENSITY = 20f
        private const val SPARK_MOTION_BLUR = 0.03f

        // impact.lens_flare / impact_anamorphic.lens_flare: 0.8 s. Their curves are read back to
        // front (time left), so the flare starts small, white-hot and bright and swells while it
        // cools to blue and fades - read forwards it would spike at the very end.
        private const val FLARE_LIFE = 0.8f
        private const val ANAMORPHIC_RADIUS = 20f
        private const val ANAMORPHIC_ASPECT = 0.25f
        private val ANAMORPHIC_COLOR = floatArrayOf(0.169f, 0.194f, 1f)

        fun muzzle(location: Vector2f, facing: Float, unit: Float, style: TorpedoStyle = TorpedoStyle.REACH): TorpedoBurst {
            val burst = TorpedoBurst(unit, style)
            burst.spawnMuzzle(location.x, location.y, facing)
            return burst
        }

        /** The impact; [smoke] false leaves out the dark smoke (the Plasma Cannon splash, developer 2026-10-09). */
        fun impact(
            location: Vector2f, unit: Float, smoke: Boolean = true, style: TorpedoStyle = TorpedoStyle.REACH
        ): TorpedoBurst {
            val burst = TorpedoBurst(unit, style)
            burst.spawnImpact(location.x, location.y, smoke)
            return burst
        }
    }

    private fun rand(min: Float, max: Float): Float = min + (max - min) * random.nextFloat()

    private fun spawn(kind: SpriteKind, x: Float, y: Float, life: Float, size: Float): RuptureSprite {
        val s = RuptureSprite(kind)
        s.x = x
        s.y = y
        s.life = life
        s.baseSize = size
        s.random = random.nextFloat()
        s.random2 = random.nextFloat()
        s.noiseOffset = random.nextFloat()
        s.angle = rand(0f, 2f * PI.toFloat())
        sprites.add(s)
        return s
    }

    /** A particle on a disc of [radius] Reach units, flying outward at [speedMin]..[speedMax] units/s. */
    private fun spawnOutward(
        kind: SpriteKind, x: Float, y: Float, radius: Float, speedMin: Float, speedMax: Float,
        drag: Float, life: Float, size: Float, spin: Float
    ): RuptureSprite {
        val heading = rand(0f, 2f * PI.toFloat())
        val r = sqrt(random.nextFloat()) * radius * unit
        val s = spawn(kind, x + cos(heading) * r, y + sin(heading) * r, life, size * unit)
        val speed = rand(speedMin, speedMax) * unit
        s.vx = cos(heading) * speed
        s.vy = sin(heading) * speed
        s.drag = drag
        s.phase = rand(-spin, spin) * 2f * PI.toFloat()
        return s
    }

    private fun spawnMuzzle(x: Float, y: Float, facing: Float) {
        repeat(MUZZLE_ROUNDS) { frame ->
            val s = spawn(style.muzzleRoundKind, x, y, rand(0.025f, 0.15f), rand(1f, 3f) * unit)
            s.frame = frame
            s.angle = Math.toRadians((facing + rand(-MUZZLE_ROUND_CONE, MUZZLE_ROUND_CONE)).toDouble()).toFloat()
        }
        val flash = spawn(SpriteKind.TORPEDO_FLASH, x, y, 0.15f, 3f * unit)
        flash.angle = Math.toRadians(facing.toDouble()).toFloat()
    }

    private fun spawnImpact(x: Float, y: Float, smoke: Boolean) {
        if (smoke) repeat(IMPACT_PARTICLES) {
            spawnOutward(SpriteKind.TORPEDO_SMOKE, x, y, 6f, 12f, 22f, 2f, rand(1.5f, 2f), rand(3.75f, 5f), 0.05f)
        }
        repeat(IMPACT_PARTICLES) {
            spawnOutward(style.fireballKind, x, y, 3f, 5f, 10f, 3f, rand(1.2f, 2f), rand(1.75f, 2.25f), 0.1f)
        }
        repeat(IMPACT_PARTICLES) {
            spawnOutward(style.plasmaKind, x, y, 3f, 5f, 10f, 2.5f, rand(1.25f, 1.8f), rand(2.5f, 3.5f), 0.07f)
        }
        repeat(IMPACT_SPARKS) {
            // cheap particle sizes 0.01-0.03 × its particle scale modifier 15
            spawnOutward(SpriteKind.TORPEDO_SPARK, x, y, 1.5f, 0f, 45f, 0.5f, rand(0.15f, 1.2f), rand(0.15f, 0.45f), 0f)
        }
        spawn(SpriteKind.TORPEDO_FLARE_SIMPLE, x, y, FLARE_LIFE, 0f).angle = 0f
        spawn(SpriteKind.TORPEDO_FLARE_ANAMORPHIC, x, y, FLARE_LIFE, 0f).angle = 0f
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

    private fun update(s: RuptureSprite, x: Float) {
        when (s.kind) {
            SpriteKind.TORPEDO_MUZZLE_ROUND, SpriteKind.TORPEDO_MUZZLE_ROUND_LIGHT, SpriteKind.TORPEDO_MUZZLE_ROUND_HEAVY -> {
                s.halfLength = s.baseSize * 0.5f * x
                s.halfWidth = s.halfLength
                TorpedoCurves.MUZZLE_ROUND_TINT.at(x, color)
                style.tint(color)
                for (c in 0..2) s.color[c] = color[c] * MUZZLE_ROUND_INTENSITY
                s.alpha = TorpedoCurves.MUZZLE_ROUND_ALPHA.at(x)
                val dim = TorpedoCurves.MUZZLE_ROUND_BLACK_POINT_DIM.at(x)
                s.blackPoint = dim + (TorpedoCurves.MUZZLE_ROUND_BLACK_POINT_HOT.at(x) - dim) * s.random
                s.paletteV = 0.5f
            }
            SpriteKind.TORPEDO_FLASH -> {
                s.halfLength = s.baseSize * 0.5f * (1f - 0.5f * x)
                s.halfWidth = s.halfLength
                MUZZLE_FLASH_COLOR.copyInto(s.color)
                style.tint(s.color)
                s.alpha = TorpedoCurves.MUZZLE_FLASH_ALPHA.at(x)
            }
            SpriteKind.TORPEDO_FIREBALL, SpriteKind.TORPEDO_FIREBALL_LIGHT, SpriteKind.TORPEDO_FIREBALL_HEAVY -> {
                val small = TorpedoCurves.FIREBALL_SCALE_B.at(x)
                val scale = small + (TorpedoCurves.FIREBALL_SCALE_A.at(x) - small) * s.random
                s.halfLength = s.baseSize * 0.5f * scale
                s.halfWidth = s.halfLength
                TorpedoCurves.FIREBALL_TINT.at(x, color)
                style.tint(color)
                val intensity = TorpedoCurves.FIREBALL_INTENSITY.at(x)
                for (c in 0..2) s.color[c] = color[c] * intensity
                s.alpha = TorpedoCurves.FIREBALL_ALPHA.at(x)
                s.blackPoint = TorpedoCurves.FIREBALL_BLACK_POINT.at(x)
                val slow = TorpedoCurves.FIREBALL_PALETTE_B.at(x)
                s.paletteV = slow + (TorpedoCurves.FIREBALL_PALETTE_A.at(x) - slow) * s.random2
                s.uvScale = 0.2f + 1f * x
            }
            SpriteKind.TORPEDO_PLASMA, SpriteKind.TORPEDO_PLASMA_LIGHT, SpriteKind.TORPEDO_PLASMA_HEAVY -> {
                s.halfLength = s.baseSize * 0.5f * TorpedoCurves.PLASMA_SCALE.at(x)
                s.halfWidth = s.halfLength
                TorpedoCurves.PLASMA_COLOR.at(x, color)
                style.tint(color)
                val wander = -0.25f + 1.25f * TorpedoLook.noise(s.age + s.random2 * 50f, salt)
                val intensity = PLASMA_FLICKER * max(0f, wander)
                for (c in 0..2) s.color[c] = color[c] * intensity
                s.alpha = TorpedoCurves.PLASMA_ALPHA.at(x)
                s.blackPoint = TorpedoCurves.PLASMA_BLACK_POINT.at(x)
                s.paletteV = s.random
                s.uvScale = 0.75f + 0.5f * x
            }
            SpriteKind.TORPEDO_SMOKE -> {
                s.halfLength = s.baseSize * 0.5f * TorpedoCurves.SMOKE_SCALE.at(x)
                s.halfWidth = s.halfLength
                // dark grey (0.26) to near black (0.055), per particle
                val shade = 0.26f + (0.055f - 0.26f) * s.random2
                s.color[0] = shade
                s.color[1] = shade
                s.color[2] = shade * 1.05f
                s.alpha = TorpedoCurves.SMOKE_ALPHA.at(x)
                s.blackPoint = TorpedoCurves.SMOKE_BLACK_POINT.at(x)
            }
            SpriteKind.TORPEDO_SPARK -> {
                // The streak runs along the texture's u, which the sprite shader lays across the
                // sprite's angle: turn the sprite a quarter so the streak follows the velocity.
                val speed = sqrt(s.vx * s.vx + s.vy * s.vy)
                s.angle = atan2(s.vy, s.vx) + PI.toFloat() * 0.5f
                s.halfLength = s.baseSize * 0.25f
                s.halfWidth = s.baseSize * 0.5f + speed * SPARK_MOTION_BLUR
                for (c in 0..2) s.color[c] = SPARK_COLOR[c] * SPARK_INTENSITY
                s.alpha = 1f - x
            }
            SpriteKind.TORPEDO_FLARE_SIMPLE -> {
                val u = 1f - x
                s.halfLength = TorpedoCurves.FLARE_RADIUS.at(u) * unit
                s.halfWidth = s.halfLength
                TorpedoCurves.FLARE_COLOR.at(u, color)
                style.tint(color)
                val brightness = TorpedoCurves.FLARE_BRIGHTNESS.at(u)
                for (c in 0..2) s.color[c] = color[c] * brightness
                s.alpha = 1f
            }
            SpriteKind.TORPEDO_FLARE_ANAMORPHIC -> {
                val u = 1f - x
                s.halfLength = ANAMORPHIC_RADIUS * unit
                s.halfWidth = s.halfLength * ANAMORPHIC_ASPECT
                val brightness = TorpedoCurves.ANAMORPHIC_BRIGHTNESS.at(u)
                ANAMORPHIC_COLOR.copyInto(color)
                style.tint(color)
                for (c in 0..2) s.color[c] = color[c] * brightness
                s.alpha = 1f
            }
            else -> {}
        }
    }
}

/**
 * Look of a [CannonFlight] aura: radii in Reach units (x the flight's unit), colours linear RGB.
 * [CANNON] is the Plasma Cannon (Reach corvette ball turret). Every value is a placeholder until
 * tuning. (The Plasma Mortar used a second preset until 2026-10-09; it now has its own Reach
 * Wraith round, [WraithFlight].)
 */
internal class AuraStyle(
    val glowOuterRadius: Float,
    val glowInnerRadius: Float,
    val glowOuterColor: FloatArray,
    val glowInnerColor: FloatArray,
    val glowOuterIntensity: Float,
    val glowInnerIntensity: Float,
    val glowBreath: Float,
    val glowBreathRate: Float,
    val starRadius: Float,
    val starColor: FloatArray,
    val starAlpha: Float,
    val starSpin: Float,
    val wisps: Int,
    val wispRadius: Float,
    val wispRadiusWobble: Float,
    val wispSize: Float,
    val wispOrbitMin: Float,
    val wispOrbitMax: Float,
    val wispFlicker: Float,
    val wispNoiseRate: Float,
    val wispAlpha: Float,
    val fadeTime: Float
) {
    companion object {
        /**
         * Plasma Cannon (developer 2026-10-09: "energy stuff around the projectile", aura only).
         * projectile.light_volume_system: blue (0.40,0.47,0.99) body, peach (1,0.65,0.46) tip;
         * projectile.lens_flare: star_flare (0.16,0.33,1), radius 0.15 read as 2.5 units here.
         * The orbiting wisps are not in the tags.
         */
        val CANNON = AuraStyle(
            glowOuterRadius = 3.5f, glowInnerRadius = 1.8f,
            glowOuterColor = floatArrayOf(0.404f, 0.471f, 0.992f), glowInnerColor = floatArrayOf(1f, 0.75f, 0.55f),
            glowOuterIntensity = 1.3f, glowInnerIntensity = 1.6f, glowBreath = 0.15f, glowBreathRate = 6f,
            starRadius = 2.5f, starColor = floatArrayOf(0.162f, 0.332f, 1f), starAlpha = 0.8f, starSpin = 0.6f,
            wisps = 6, wispRadius = 2.2f, wispRadiusWobble = 0.8f, wispSize = 2.6f,
            wispOrbitMin = 1.5f, wispOrbitMax = 2.6f, wispFlicker = 5f, wispNoiseRate = 7.5f, wispAlpha = 0.75f,
            fadeTime = 0.25f
        )

    }
}

/**
 * An energy aura around an engine-drawn plasma round (the `.proj` PLASMA ball is the hot core):
 * two glow spheres (body + heart), a `star_flare`, and a ring of flickering plasma wisps orbiting the
 * ball, all shaped by [style]. Sizes in Reach units x [unit]. Fades out over the style's fade time
 * once the round has hit or expired. Used by the Plasma Cannon.
 */
internal class CannonFlight(
    val projectile: DamagingProjectileAPI,
    val unit: Float,
    private val style: AuraStyle = AuraStyle.CANNON
) {
    val glowOuter = RuptureSprite(SpriteKind.TORPEDO_BODY)
    val glowInner = RuptureSprite(SpriteKind.TORPEDO_BODY)
    val star = RuptureSprite(SpriteKind.TORPEDO_STAR)
    val wisps = Array(style.wisps) { RuptureSprite(SpriteKind.TORPEDO_PLASMA) }

    private val random = Random(System.nanoTime() xor (projectile.hashCode().toLong() * 7919L))
    private val salt = random.nextInt(10000)
    private val wispBase = FloatArray(style.wisps) { it * 2f * PI.toFloat() / style.wisps + random.nextFloat() * 0.5f }
    private val wispOmega = FloatArray(style.wisps) {
        (style.wispOrbitMin + random.nextFloat() * (style.wispOrbitMax - style.wispOrbitMin)) *
            (if (random.nextBoolean()) 1f else -1f)
    }
    private val color = FloatArray(3)
    private var time = random.nextFloat() * 100f
    private var fade = 1f
    private val location = Vector2f(projectile.location)

    /** False once the round is gone; the aura then fades out where it was. */
    var flying = true
        private set

    val isDone: Boolean get() = !flying && fade <= 0f

    init {
        for (w in wisps) {
            w.random = random.nextFloat()
            w.random2 = random.nextFloat()
            w.noiseOffset = random.nextFloat()
            w.angle = random.nextFloat() * 2f * PI.toFloat()
            w.phase = (random.nextFloat() - 0.5f) * 2f
        }
    }

    fun end() {
        flying = false
    }

    fun advance(dt: Float) {
        time += dt
        if (flying) location.set(projectile.location) else fade = max(0f, fade - dt / style.fadeTime)
        val x = location.x
        val y = location.y

        val breath = 1f + style.glowBreath * (TorpedoLook.noise(time * style.glowBreathRate, salt) * 2f - 1f)
        glowOuter.x = x
        glowOuter.y = y
        glowOuter.halfLength = style.glowOuterRadius * unit * breath
        glowOuter.halfWidth = glowOuter.halfLength
        for (c in 0..2) glowOuter.color[c] = style.glowOuterColor[c] * style.glowOuterIntensity
        glowOuter.alpha = fade

        glowInner.x = x
        glowInner.y = y
        glowInner.halfLength = style.glowInnerRadius * unit * (2f - breath)
        glowInner.halfWidth = glowInner.halfLength
        for (c in 0..2) glowInner.color[c] = style.glowInnerColor[c] * style.glowInnerIntensity
        glowInner.alpha = fade

        star.x = x
        star.y = y
        star.angle += style.starSpin * dt
        star.halfLength = style.starRadius * unit
        star.halfWidth = star.halfLength
        style.starColor.copyInto(star.color)
        star.alpha = style.starAlpha * fade

        for (i in wisps.indices) {
            val w = wisps[i]
            val n = TorpedoLook.noise(time * style.wispNoiseRate + w.random2 * 50f, salt + i * 13)
            val orbit = wispBase[i] + time * wispOmega[i]
            val radius = (style.wispRadius + style.wispRadiusWobble * (n - 0.5f)) * unit
            w.x = x + cos(orbit) * radius
            w.y = y + sin(orbit) * radius
            w.angle += w.phase * dt
            w.halfLength = style.wispSize * 0.5f * unit * (0.8f + 0.4f * n)
            w.halfWidth = w.halfLength
            TorpedoCurves.PLASMA_COLOR.at(0.5f + 0.5f * sin(time * 2f + i), color)
            val intensity = style.wispFlicker * max(0f, -0.25f + 1.25f * n)
            for (c in 0..2) w.color[c] = color[c] * intensity
            w.alpha = style.wispAlpha * fade
            w.blackPoint = 0.15f
            w.paletteV = w.random
            w.uvScale = 0.9f
        }
    }
}

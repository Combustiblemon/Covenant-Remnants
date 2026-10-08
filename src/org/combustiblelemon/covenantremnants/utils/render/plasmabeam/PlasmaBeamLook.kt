package org.combustiblelemon.covenantremnants.utils.render.plasmabeam

import com.fs.starfarer.api.combat.BeamAPI
import com.fs.starfarer.api.combat.WeaponAPI
import org.combustiblelemon.covenantremnants.utils.render.rupture.RuptureSprite
import org.combustiblelemon.covenantremnants.utils.render.rupture.SpriteKind
import org.combustiblelemon.covenantremnants.utils.render.torpedo.TorpedoLook
import org.lwjgl.util.vector.Vector2f
import java.util.Random
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * One Plasma Beam Emitter's Halo: Reach focus rifle look, over the engine's own beam (vanilla
 * textures, violet fringe / peach core from the `.wpn`): the violet plasma sheath, the peach
 * centre, the peach-to-blue pulse tubes (one per Reach round, 30 a second, 0.2 s each), the
 * muzzle (blue plasma tongues, gold embers, flash, light-volume cone, flickering light) and the
 * impact (flash, gold plasma licks, sparks, orange lens flare, peach light; Reach re-fires its
 * impact event every 0.1 s while the beam holds).
 *
 * The beam's endpoints come from the weapon's BeamEffectPlugin ([snapshot], called from
 * `PlasmaBeamEmitterEffect.advance`), where `getTo()` is the collision point (see the Energy
 * Projector's in-game fixes). No snapshot in a frame = the beam is gone.
 *
 * Scale: [UNIT] Starsector units per Reach world unit, chosen so Reach's 0.25 WU sheath is the
 * 20 su engine beam (proposal accepted 2026-10-08, to be checked in game). Sprites carry world
 * positions; `phase` holds a sprite's spin (rad/s), `random2` its second random.
 */
internal class PlasmaBeamLook(val weapon: WeaponAPI) {
    companion object {
        /** Starsector units per Reach world unit (the 0.25 WU sheath = the 20 su engine beam). */
        const val UNIT = 80f

        /** The energy_trail band covers about half the profile bitmap's height. */
        private const val PROFILE_FILL = 0.5f
        /** Reach's tiny sparks (0.005 WU = 0.4 su) need a floor to show at all (placeholder). */
        private const val MIN_SPRITE_SIZE = 2f

        // --- beam (projectile_3p.beam_system) ---
        private const val SHEATH_THICKNESS = 0.25f
        private const val CORE_THICKNESS = 0.035f
        /** The last part of the beam where the centre's intensity climbs to 4 (placeholder: 12 % at 2.5). */
        private const val CORE_TIP_FRACTION = 0.12f
        private const val CORE_TIP_INTENSITY = 2.5f
        private const val PULSE_RATE = 30f
        private const val PULSE_LIFE = 0.2f
        /** ~6 pulses overlap at any time; at Reach's intensity 2 each they white the beam out (placeholder gain). */
        private const val PULSE_GAIN = 0.35f

        // --- muzzle (firing.effect 3rd_person, firing_3p light volume / light) ---
        private const val FLASH_RATE = 30f
        private const val FLASH_LIFE = 0.085f
        private const val FLASH_SIZE_MIN = 0.3f
        private const val FLASH_SIZE_MAX = 0.4f
        private const val FLASH_OFFSET = 0.05f
        private const val FLASH_SPREAD = 45f
        private val FLASH_TINT = floatArrayOf(1f, 0.6471f, 0.4392f)
        private const val EMBER_RATE = 200f
        private const val EMBER_LIFE_MIN = 0.1f
        private const val EMBER_LIFE_MAX = 0.3f
        private const val EMBER_SPEED = 2f
        private const val EMBER_CONE = 30f
        private const val EMBER_SIZE_MIN = 0.005f
        private const val EMBER_SIZE_MAX = 0.0075f
        private const val EMBER_INTENSITY_MIN = 4f
        private const val EMBER_INTENSITY_MAX = 16f
        private const val EMBER_FREQ = 2f
        private const val MUZZLE_PLASMA_RATE = 100f
        private const val MUZZLE_PLASMA_LIFE_MIN = 0.1f
        private const val MUZZLE_PLASMA_LIFE_MAX = 0.2f
        private const val MUZZLE_PLASMA_SIZE_MIN = 0.15f
        private const val MUZZLE_PLASMA_SIZE_MAX = 0.25f
        private const val MUZZLE_PLASMA_CONE = 80f
        private const val MUZZLE_PLASMA_INTENSITY = 2f
        private const val MUZZLE_PLASMA_SPIN = 0.1f
        private const val LIGHT_VOLUME_LENGTH = 0.35f
        private const val LIGHT_VOLUME_OFFSET = 0.035f
        private const val LIGHT_VOLUME_SEGMENTS = 6
        /** Reach "brightness ratio 3" on the light volume, read as a gain (placeholder). */
        private const val LIGHT_VOLUME_GAIN = 1.5f
        private val LIGHT_VOLUME_COLOR = floatArrayOf(0.6588f, 0.6941f, 1f)
        /** firing_3p.light: a sphere light to 2.5 WU; drawn as a soft glow of this radius (placeholder gain). */
        private const val MUZZLE_LIGHT_RADIUS = 1f
        private const val MUZZLE_LIGHT_GAIN = 0.3f
        private const val MUZZLE_LIGHT_FREQ = 2f
        private val MUZZLE_LIGHT_COLOR = floatArrayOf(0.5922f, 0.5569f, 1f)

        // --- impact (impact.effect, re-fired every 0.1 s while hitting) ---
        private const val IMPACT_CYCLE = 0.1f
        private const val IMPACT_FLASH_SIZE_MIN = 0.2f
        private const val IMPACT_FLASH_SIZE_MAX = 0.25f
        private const val IMPACT_FLASH_LIFE_MIN = 0.1f
        private const val IMPACT_FLASH_LIFE_MAX = 0.15f
        private val IMPACT_FLASH_TINT = floatArrayOf(0.9843f, 0.7608f, 0.4745f)
        private const val IMPACT_PLASMA_COUNT = 5
        private const val IMPACT_PLASMA_LIFE_MIN = 0.15f
        private const val IMPACT_PLASMA_LIFE_MAX = 0.2f
        private const val IMPACT_PLASMA_SIZE_MIN = 0.1f
        private const val IMPACT_PLASMA_SIZE_MAX = 0.15f
        private const val IMPACT_PLASMA_SPEED = 0.1f
        private const val IMPACT_PLASMA_INTENSITY = 2f
        /** spark_long's emitter functions did not decode (placeholders: count, life, speed). */
        private const val SPARK_LONG_COUNT = 6
        private const val SPARK_LONG_LIFE = 0.3f
        private const val SPARK_LONG_SPEED = 1.5f
        private const val SPARK_LONG_CONE = 45f
        private const val SPARK_LONG_SIZE = 0.0075f
        private val SPARK_LONG_TINT = floatArrayOf(0.9725f, 0.3451f, 0.1804f)
        private const val IMPACT_FLARE_RADIUS = 0.3f
        private const val IMPACT_FLARE_BRIGHTNESS = 2f
        private const val IMPACT_FLARE_MODULATION = 0.2f
        private const val IMPACT_FLARE_NOISE_MIN = -0.5f
        private const val IMPACT_FLARE_NOISE_MAX = 2f
        private const val IMPACT_FLARE_FREQ = 5f
        private val IMPACT_FLARE_COLOR = floatArrayOf(1f, 0.3673f, 0.1059f)
        private const val IMPACT_LIGHT_RADIUS = 0.5f
        /** impact.cheap_light intensity 10 is a point light's; as a glow sprite it gets this gain (placeholder). */
        private const val IMPACT_LIGHT_GAIN = 0.3f
        private val IMPACT_LIGHT_COLOR = floatArrayOf(1f, 0.651f, 0.3647f)

        /** The hit point has to jump this far (su) for the impact sound to play again. */
        private const val NEW_HIT_DISTANCE = 120f
        /** Fade of the persistent sprites once the beam (or the hit) is gone. */
        private const val RELEASE_FADE = 0.1f

        private var counter = 0
    }

    val sprites = ArrayList<RuptureSprite>()

    private val random = Random(System.nanoTime() xor (counter++ * 7919L))
    private val salt = counter * 131
    private val color = FloatArray(3)

    private var hasBeam = false
    private var beamAge = 0f
    private var fade = 1f
    private var hit = false
    private var hitAge = -1f
    private var cycleAge = 0f
    private val from = Vector2f()
    private val to = Vector2f()
    private val lastHit = Vector2f()
    private var facing = 0f
    private var length = 0f

    private var snapshotFresh = false
    private var snapshotBrightness = 0f
    private var snapshotHit = false
    private val snapshotFrom = Vector2f()
    private val snapshotTo = Vector2f()

    private var pulseAccumulator = 0f
    private var flashAccumulator = 0f
    private var emberAccumulator = 0f
    private var muzzlePlasmaAccumulator = 0f

    // Persistent sprites (life MAX while the beam / hit lives, released over RELEASE_FADE).
    private var sheath: RuptureSprite? = null
    private var sheath2: RuptureSprite? = null
    private var core: RuptureSprite? = null
    private var coreTip: RuptureSprite? = null
    private val lightVolume = arrayOfNulls<RuptureSprite>(LIGHT_VOLUME_SEGMENTS)
    private var muzzleLight: RuptureSprite? = null
    private var impactFlare: RuptureSprite? = null
    private var impactLight: RuptureSprite? = null

    /** True once the weapon is idle and every sprite has aged out. */
    val isDone: Boolean get() = !hasBeam && sprites.isEmpty()

    /** True on the frame the beam appears / disappears: the fire in / out sounds. */
    var startedThisFrame = false
        private set
    var endedThisFrame = false
        private set

    /** True on the frame the beam first touches something (or a new target): the impact sound. */
    var impactThisFrame = false
        private set
    val hitPoint: Vector2f get() = to
    val muzzle: Vector2f get() = from

    /** Called from the beam effect each frame the beam lives, with the collision-resolved endpoints. */
    fun snapshot(beam: BeamAPI) {
        snapshotFrom.set(beam.from)
        snapshotTo.set(beam.to)
        snapshotBrightness = beam.brightness
        snapshotHit = beam.damageTarget != null
        snapshotFresh = true
    }

    fun advance(dt: Float) {
        impactThisFrame = false
        startedThisFrame = false
        endedThisFrame = false
        if (snapshotFresh) {
            snapshotFresh = false
            track(snapshotFrom, snapshotTo, snapshotBrightness, snapshotHit, dt)
        } else if (hasBeam) {
            endBeam()
        }
        age(dt)
    }

    private fun track(beamFrom: Vector2f, beamTo: Vector2f, brightness: Float, hitting: Boolean, dt: Float) {
        if (!hasBeam) {
            hasBeam = true
            startedThisFrame = true
            beamAge = 0f
            pulseAccumulator = 1f
            flashAccumulator = 1f
        } else {
            beamAge += dt
        }
        from.set(beamFrom)
        to.set(beamTo)
        val dx = to.x - from.x
        val dy = to.y - from.y
        length = sqrt(dx * dx + dy * dy)
        facing = atan2(dy, dx)
        fade = brightness.coerceIn(0f, 1f)
        hit = hitting

        updateBeam(dt)
        updateMuzzle(dt)
        updateImpact(dt)
    }

    private fun endBeam() {
        hasBeam = false
        endedThisFrame = true
        hit = false
        hitAge = -1f
        release(sheath); sheath = null
        release(sheath2); sheath2 = null
        release(core); core = null
        release(coreTip); coreTip = null
        for (i in lightVolume.indices) { release(lightVolume[i]); lightVolume[i] = null }
        release(muzzleLight); muzzleLight = null
        releaseImpact()
    }

    private fun releaseImpact() {
        release(impactFlare); impactFlare = null
        release(impactLight); impactLight = null
    }

    private fun release(s: RuptureSprite?) {
        if (s == null || s.life != Float.MAX_VALUE) return
        s.baseAlpha = s.alpha
        s.life = s.age + RELEASE_FADE
    }

    // --- beam: the sheath layers, the centre, the pulses (one per Reach round) ---

    private fun updateBeam(dt: Float) {
        if (length < 1f) return
        val halfLength = length * 0.5f

        val a = sheath ?: spawn(SpriteKind.PLASMA_BEAM_SHEATH, 0f, 0f, Float.MAX_VALUE, 0f).also { sheath = it }
        alongBeam(a, halfLength, SHEATH_THICKNESS * UNIT * 0.5f / PROFILE_FILL)
        for (c in 0..2) a.color[c] = 1f
        a.alpha = fade
        a.paletteV = 0.5f
        a.uvScale = 1f
        a.noiseOffset = 0f

        val b = sheath2 ?: spawn(SpriteKind.PLASMA_BEAM_SHEATH_2, 0f, 0f, Float.MAX_VALUE, 0f).also { sheath2 = it }
        alongBeam(b, halfLength, SHEATH_THICKNESS * UNIT * 0.5f / PROFILE_FILL)
        for (c in 0..2) b.color[c] = 1f
        b.alpha = fade
        b.paletteV = 0.5f
        b.uvScale = 1f
        b.noiseOffset = 0f

        val c = core ?: spawn(SpriteKind.PLASMA_BEAM_CORE, 0f, 0f, Float.MAX_VALUE, 0f).also { core = it }
        alongBeam(c, halfLength, CORE_THICKNESS * UNIT * 0.5f / PROFILE_FILL)
        PlasmaBeamCurves.CORE_COLOR.at(0.5f, color)
        color.copyInto(c.color)
        c.alpha = fade

        // the centre's far-end climb (Reach intensity 1 -> 4 over the last ~10 %): a short, brighter copy
        val tip = coreTip ?: spawn(SpriteKind.PLASMA_BEAM_CORE, 0f, 0f, Float.MAX_VALUE, 0f).also { coreTip = it }
        val tipHalf = length * CORE_TIP_FRACTION * 0.5f
        tip.x = from.x
        tip.y = from.y
        tip.angle = facing
        tip.offsetAlong = length - tipHalf
        tip.halfLength = tipHalf
        tip.halfWidth = CORE_THICKNESS * UNIT * 0.5f / PROFILE_FILL
        PlasmaBeamCurves.CORE_COLOR.at(1f, color)
        for (ch in 0..2) tip.color[ch] = color[ch] * (CORE_TIP_INTENSITY - 1f)
        tip.alpha = fade

        pulseAccumulator += PULSE_RATE * dt
        while (pulseAccumulator >= 1f) {
            pulseAccumulator -= 1f
            spawn(SpriteKind.PLASMA_BEAM_PULSE, from.x, from.y, PULSE_LIFE, 0f)
        }
    }

    /** Lays [s] from the muzzle along the beam: a quad of [halfLength] by [halfWidth]. */
    private fun alongBeam(s: RuptureSprite, halfLength: Float, halfWidth: Float) {
        s.x = from.x
        s.y = from.y
        s.angle = facing
        s.offsetAlong = halfLength
        s.halfLength = halfLength
        s.halfWidth = halfWidth
    }

    // --- muzzle: flash, embers, plasma tongues, the light-volume cone, the flickering light ---

    private fun updateMuzzle(dt: Float) {
        flashAccumulator += FLASH_RATE * dt
        while (flashAccumulator >= 1f) {
            flashAccumulator -= 1f
            val s = spawn(
                SpriteKind.PLASMA_BEAM_MUZZLE_FLASH,
                from.x + cos(facing) * FLASH_OFFSET * UNIT, from.y + sin(facing) * FLASH_OFFSET * UNIT,
                FLASH_LIFE, rand(FLASH_SIZE_MIN, FLASH_SIZE_MAX) * UNIT
            )
            s.angle = facing + rand(-FLASH_SPREAD, FLASH_SPREAD) * DEG
        }

        emberAccumulator += EMBER_RATE * dt
        while (emberAccumulator >= 1f) {
            emberAccumulator -= 1f
            val heading = facing + rand(-EMBER_CONE, EMBER_CONE) * DEG
            val s = spawn(
                SpriteKind.PLASMA_BEAM_EMBER, from.x, from.y, rand(EMBER_LIFE_MIN, EMBER_LIFE_MAX),
                max(MIN_SPRITE_SIZE, rand(EMBER_SIZE_MIN, EMBER_SIZE_MAX) * UNIT)
            )
            s.vx = cos(heading) * EMBER_SPEED * UNIT
            s.vy = sin(heading) * EMBER_SPEED * UNIT
        }

        muzzlePlasmaAccumulator += MUZZLE_PLASMA_RATE * dt
        while (muzzlePlasmaAccumulator >= 1f) {
            muzzlePlasmaAccumulator -= 1f
            val s = spawn(
                SpriteKind.PLASMA_BEAM_MUZZLE_PLASMA, from.x, from.y,
                rand(MUZZLE_PLASMA_LIFE_MIN, MUZZLE_PLASMA_LIFE_MAX),
                rand(MUZZLE_PLASMA_SIZE_MIN, MUZZLE_PLASMA_SIZE_MAX) * UNIT
            )
            s.angle = facing + rand(-MUZZLE_PLASMA_CONE, MUZZLE_PLASMA_CONE) * DEG
            s.phase = rand(-MUZZLE_PLASMA_SPIN, MUZZLE_PLASMA_SPIN) * 2f * PI.toFloat()
            s.paletteV = 0.5f
            s.uvScale = 1f
        }

        // the light-volume cone: glow sprites along the first LIGHT_VOLUME_LENGTH of the beam
        for (i in 0 until LIGHT_VOLUME_SEGMENTS) {
            val x = (i + 0.5f) / LIGHT_VOLUME_SEGMENTS
            val along = (LIGHT_VOLUME_OFFSET + LIGHT_VOLUME_LENGTH * x) * UNIT
            val s = lightVolume[i] ?: spawn(SpriteKind.PLASMA_BEAM_LIGHT, 0f, 0f, Float.MAX_VALUE, 0f).also { lightVolume[i] = it }
            s.x = from.x + cos(facing) * along
            s.y = from.y + sin(facing) * along
            s.angle = facing
            s.offsetAlong = 0f
            val half = PlasmaBeamCurves.LIGHT_VOLUME_THICKNESS.at(x) * UNIT * 0.5f
            s.halfLength = half
            s.halfWidth = half
            val intensity = PlasmaBeamCurves.LIGHT_VOLUME_INTENSITY.at(x) * LIGHT_VOLUME_GAIN
            for (c in 0..2) s.color[c] = LIGHT_VOLUME_COLOR[c] * intensity
            s.alpha = fade
        }

        val light = muzzleLight ?: spawn(SpriteKind.PLASMA_BEAM_LIGHT, 0f, 0f, Float.MAX_VALUE, 0f).also { muzzleLight = it }
        light.x = from.x
        light.y = from.y
        light.angle = 0f
        light.offsetAlong = 0f
        light.halfLength = MUZZLE_LIGHT_RADIUS * UNIT
        light.halfWidth = light.halfLength
        val flicker = TorpedoLook.noise(beamAge * MUZZLE_LIGHT_FREQ, salt) * MUZZLE_LIGHT_GAIN
        for (c in 0..2) light.color[c] = MUZZLE_LIGHT_COLOR[c] * flicker
        light.alpha = fade
    }

    // --- impact: Reach's 0.1 s impact event re-fired while the beam holds ---

    private fun updateImpact(dt: Float) {
        if (!hit) {
            hitAge = -1f
            releaseImpact()
            return
        }
        val fresh = hitAge < 0f || distance(to.x, to.y, lastHit.x, lastHit.y) > NEW_HIT_DISTANCE
        if (fresh) {
            if (hitAge < 0f) cycleAge = IMPACT_CYCLE
            hitAge = 0f
            lastHit.set(to)
            impactThisFrame = true
        } else {
            hitAge += dt
        }

        cycleAge += dt
        if (cycleAge >= IMPACT_CYCLE) {
            cycleAge -= IMPACT_CYCLE
            spawnImpactCycle()
        }

        val flare = impactFlare ?: spawn(SpriteKind.PLASMA_BEAM_IMPACT_FLARE, 0f, 0f, Float.MAX_VALUE, 0f).also { impactFlare = it }
        flare.x = to.x
        flare.y = to.y
        flare.angle = 0f
        flare.offsetAlong = 0f
        flare.halfLength = IMPACT_FLARE_RADIUS * UNIT
        flare.halfWidth = flare.halfLength
        val noise = IMPACT_FLARE_NOISE_MIN + (IMPACT_FLARE_NOISE_MAX - IMPACT_FLARE_NOISE_MIN) * TorpedoLook.noise(hitAge * IMPACT_FLARE_FREQ + 1f, salt + 7)
        val b = max(0f, IMPACT_FLARE_BRIGHTNESS * (1f - IMPACT_FLARE_MODULATION + IMPACT_FLARE_MODULATION * noise))
        for (c in 0..2) flare.color[c] = IMPACT_FLARE_COLOR[c] * b
        flare.alpha = fade

        val light = impactLight ?: spawn(SpriteKind.PLASMA_BEAM_IMPACT_LIGHT, 0f, 0f, Float.MAX_VALUE, 0f).also { impactLight = it }
        light.x = to.x
        light.y = to.y
        light.angle = 0f
        light.offsetAlong = 0f
        light.halfLength = IMPACT_LIGHT_RADIUS * UNIT
        light.halfWidth = light.halfLength
        val lb = PlasmaBeamCurves.IMPACT_LIGHT.at(cycleAge / IMPACT_CYCLE) * IMPACT_LIGHT_GAIN
        for (c in 0..2) light.color[c] = IMPACT_LIGHT_COLOR[c] * lb
        light.alpha = fade
    }

    private fun spawnImpactCycle() {
        val back = facing + PI.toFloat()   // the surface normal, read as "back toward the shooter"
        val flash = spawn(
            SpriteKind.PLASMA_BEAM_IMPACT_FLASH,
            to.x + cos(back) * FLASH_OFFSET * UNIT, to.y + sin(back) * FLASH_OFFSET * UNIT,
            rand(IMPACT_FLASH_LIFE_MIN, IMPACT_FLASH_LIFE_MAX), rand(IMPACT_FLASH_SIZE_MIN, IMPACT_FLASH_SIZE_MAX) * UNIT
        )
        flash.angle = back + rand(-FLASH_SPREAD, FLASH_SPREAD) * DEG

        for (i in 0 until IMPACT_PLASMA_COUNT) {
            val heading = rand(0f, 2f * PI.toFloat())
            val s = spawn(
                SpriteKind.PLASMA_BEAM_IMPACT_PLASMA, to.x, to.y,
                rand(IMPACT_PLASMA_LIFE_MIN, IMPACT_PLASMA_LIFE_MAX),
                rand(IMPACT_PLASMA_SIZE_MIN, IMPACT_PLASMA_SIZE_MAX) * UNIT
            )
            s.angle = heading
            s.vx = cos(heading) * IMPACT_PLASMA_SPEED * UNIT
            s.vy = sin(heading) * IMPACT_PLASMA_SPEED * UNIT
            s.phase = rand(-MUZZLE_PLASMA_SPIN, MUZZLE_PLASMA_SPIN) * 2f * PI.toFloat()
            s.paletteV = 0.5f
            s.uvScale = 1f
        }

        for (i in 0 until SPARK_LONG_COUNT) {
            val heading = back + rand(-SPARK_LONG_CONE, SPARK_LONG_CONE) * DEG
            val s = spawn(
                SpriteKind.PLASMA_BEAM_SPARK_LONG, to.x, to.y, SPARK_LONG_LIFE,
                max(MIN_SPRITE_SIZE * 0.5f, SPARK_LONG_SIZE * UNIT)
            )
            s.angle = heading
            s.vx = cos(heading) * SPARK_LONG_SPEED * UNIT
            s.vy = sin(heading) * SPARK_LONG_SPEED * UNIT
        }
    }

    // --- particle bookkeeping ---

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

    private fun age(dt: Float) {
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
            s.angle += s.phase * dt
            update(s, if (s.life == Float.MAX_VALUE) 0f else s.age / s.life)
        }
    }

    private fun update(s: RuptureSprite, x: Float) {
        when (s.kind) {
            SpriteKind.PLASMA_BEAM_PULSE -> {
                // a tube along the live beam, swelling from the centre to the sheath, peach -> blue
                if (hasBeam) alongBeam(s, length * 0.5f, PlasmaBeamCurves.PULSE_THICKNESS.at(x) * UNIT * 0.5f / PROFILE_FILL)
                PlasmaBeamCurves.PULSE_COLOR.at(x, color)
                val intensity = PlasmaBeamCurves.PULSE_INTENSITY.at(x) * PULSE_GAIN
                for (c in 0..2) s.color[c] = color[c] * intensity
                s.blackPoint = PlasmaBeamCurves.PULSE_BLACK_POINT.at(x)
                s.alpha = if (hasBeam) fade else 0f
            }
            SpriteKind.PLASMA_BEAM_MUZZLE_FLASH -> {
                val half = s.baseSize * 0.5f * PlasmaBeamCurves.FLASH_SCALE.at(x)
                s.halfLength = half
                s.halfWidth = half
                FLASH_TINT.copyInto(s.color)
                s.alpha = PlasmaBeamCurves.FLASH_ALPHA.at(x)
            }
            SpriteKind.PLASMA_BEAM_EMBER -> {
                val half = s.baseSize * 0.5f
                s.halfLength = half
                s.halfWidth = half
                PlasmaBeamCurves.EMBER_TINT.at(x, color)
                val intensity = EMBER_INTENSITY_MIN + (EMBER_INTENSITY_MAX - EMBER_INTENSITY_MIN) * TorpedoLook.noise(s.age * EMBER_FREQ + s.random * 10f, salt)
                for (c in 0..2) s.color[c] = color[c] * intensity
                s.alpha = PlasmaBeamCurves.EMBER_ALPHA.at(x)
            }
            SpriteKind.PLASMA_BEAM_MUZZLE_PLASMA -> {
                // a flame tongue standing on the muzzle, tip (the bitmap's top) outward
                val half = s.baseSize * 0.5f * PlasmaBeamCurves.MUZZLE_PLASMA_SCALE.at(x)
                s.halfLength = half
                s.halfWidth = half
                s.offsetAlong = half
                PlasmaBeamCurves.MUZZLE_PLASMA_COLOR.at(x, color)
                for (c in 0..2) s.color[c] = color[c] * MUZZLE_PLASMA_INTENSITY
                s.alpha = PlasmaBeamCurves.PLASMA_ALPHA.at(x)
                val a = PlasmaBeamCurves.MUZZLE_PLASMA_BLACK_POINT_A.at(x)
                val b = PlasmaBeamCurves.MUZZLE_PLASMA_BLACK_POINT_B.at(x)
                s.blackPoint = a + (b - a) * s.random2
            }
            SpriteKind.PLASMA_BEAM_IMPACT_FLASH -> {
                val half = s.baseSize * 0.5f * PlasmaBeamCurves.FLASH_SCALE.at(x)
                s.halfLength = half
                s.halfWidth = half
                val intensity = PlasmaBeamCurves.IMPACT_FLASH_INTENSITY.at(x)
                for (c in 0..2) s.color[c] = IMPACT_FLASH_TINT[c] * intensity
                s.alpha = PlasmaBeamCurves.IMPACT_FLASH_ALPHA.at(x)
            }
            SpriteKind.PLASMA_BEAM_IMPACT_PLASMA -> {
                val half = s.baseSize * 0.5f * PlasmaBeamCurves.IMPACT_PLASMA_SCALE.at(x)
                s.halfLength = half
                s.halfWidth = half * max(0.05f, PlasmaBeamCurves.IMPACT_PLASMA_ASPECT.at(x))
                s.offsetAlong = half
                PlasmaBeamCurves.IMPACT_PLASMA_COLOR.at(x, color)
                for (c in 0..2) s.color[c] = color[c] * IMPACT_PLASMA_INTENSITY
                s.alpha = PlasmaBeamCurves.PLASMA_ALPHA.at(x)
                val a = PlasmaBeamCurves.IMPACT_PLASMA_BLACK_POINT_A.at(x)
                val b = PlasmaBeamCurves.IMPACT_PLASMA_BLACK_POINT_B.at(x)
                s.blackPoint = a + (b - a) * s.random2
            }
            SpriteKind.PLASMA_BEAM_SPARK_LONG -> {
                // a streak stretched along its flight
                val width = s.baseSize * PlasmaBeamCurves.SPARK_LONG_SCALE.at(x)
                s.halfWidth = width * 0.5f
                s.halfLength = width * 0.5f * PlasmaBeamCurves.SPARK_LONG_ASPECT.at(x)
                val intensity = PlasmaBeamCurves.SPARK_LONG_INTENSITY.at(x)
                for (c in 0..2) s.color[c] = SPARK_LONG_TINT[c] * intensity
                s.alpha = PlasmaBeamCurves.SPARK_LONG_ALPHA.at(x)
            }
            SpriteKind.PLASMA_BEAM_SHEATH, SpriteKind.PLASMA_BEAM_SHEATH_2, SpriteKind.PLASMA_BEAM_CORE,
            SpriteKind.PLASMA_BEAM_LIGHT, SpriteKind.PLASMA_BEAM_IMPACT_FLARE, SpriteKind.PLASMA_BEAM_IMPACT_LIGHT -> {
                // set while the beam / hit lives; after release, fade out
                if (s.life != Float.MAX_VALUE) s.alpha = s.baseAlpha * ((s.life - s.age) / RELEASE_FADE).coerceIn(0f, 1f)
            }
            else -> {}
        }
    }

    private fun distance(ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = ax - bx
        val dy = ay - by
        return sqrt(dx * dx + dy * dy)
    }
}

private const val DEG = (PI / 180.0).toFloat()

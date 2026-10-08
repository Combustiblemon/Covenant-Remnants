package org.combustiblelemon.covenantremnants.utils.render.projector

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
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * One Energy Projector's Halo: Reach CCS glassing look, over the engine's own beam (which carries
 * the Reach plasma textures through the `.wpn` `textureType` pair): the charge at the muzzle, the
 * flicker glow and far-end flare along the beam, and the impact (bubble, half-dome glow, plasma
 * tongues, cloud ring) at the hit point. Driven every frame from the weapon's `everyFrameEffect`
 * ([advance]); the live beam comes from [WeaponAPI.getBeams].
 *
 * The beam's endpoints come from the weapon's BeamEffectPlugin ([snapshot], called from
 * `EnergyProjectorBeamEffect.advance`): there `getTo()` is the collision point. Read from the
 * weapon's everyFrame hook instead, `to` is still the full-range ray end (the engine shortens it
 * after the beam effect runs), which put the impact in the middle of ships.
 *
 * Phases (from the beam's brightness): no beam = idle; brightness rising = **charge** (the weapon's
 * 2 s chargeup, Reach's 2 s charge); brightness 1 = **beam**; brightness falling = **fade**. Reach
 * timings are kept (developer ruling 2026-10-08); sizes use [UNIT] Starsector units per Reach unit.
 * Sprites carry world positions; `phase` holds a sprite's spin (rad/s), `random2` its second random.
 */
internal class ProjectorEffect(val weapon: WeaponAPI) {
    companion object {
        /** Starsector units per Reach world unit (ruling: 0.5, the 30-unit beam is 15 wide). */
        const val UNIT = 0.5f

        // The weapon's chargeup (weapon_data.csv); Reach's charge event is squeezed into it.
        private const val CHARGEUP = 2f

        // Reach event clocks the curves run over (seconds).
        private const val CHARGE_FLARE_EVENT = 11f
        private const val CHARGE_LIGHT_RISE = 0.25f   // of the m70 light's 25 s curve: the rise lands at full charge
        private const val FLICKER_LIFE = 16.75f
        private const val FAR_FLARE_LIFE = 1.25f
        private const val BUBBLE_LIFE = 1.25f
        private const val HALF_GLOW_LIFE = 1.5f
        private const val BURST_EVENT = 6.8f
        private const val VAPOR_EVENT = 7f
        private const val VAPOR_DELAY = 1f          // Reach: vaporization starts 1 s after the beam

        // Emitters (per second) and lifespans.
        private const val EMP_RATE = 3f
        private const val EMP_LIFE = 1f
        private const val EMP_INTENSITY = 3f
        private const val FLASH_RATE = 1.5f
        private const val FLASH_LIFE = 1f
        private const val BURST_RATE = 2f
        private const val CLOUD_LIFE = 8f
        private const val CLOUD_SPEED = 75f          // Reach units/s
        private const val CLOUD_DRAG = 0.25f         // physics template's second parameter, read as drag
        private const val BLUE_LIFE = 3f
        private const val BLUE_LIFE_SPREAD = 1.5f
        /** Placeholder: the cloud ring's Reach rates halved (hundreds of live sprites per projector otherwise). */
        private const val VAPOR_RATE_SCALE = 0.5f

        // Sizes (Reach units, as diameters like the torpedo) and brightness.
        private const val CHARGE_FLARE_RADIUS = 150f
        private const val CHARGE_FLARE_BRIGHTNESS = 0.5f * 4f   // Reach 0.5 with tint power 16: placeholder gain
        private val CHARGE_FLARE_COLOR = floatArrayOf(0.0627f, 0.1788f, 1f)
        private const val CHARGE_LIGHT_RADIUS = 80f
        private const val CHARGE_LIGHT_BRIGHTNESS = 2.5f
        private val CHARGE_LIGHT_COLOR = floatArrayOf(0.298f, 0.3833f, 1f)
        private val FLASH_COLOR = floatArrayOf(0.6745f, 0.7559f, 1f)
        private const val BEAM_FLASH_SIZE = 4f
        private const val BEAM_EMP_SIZE = 7f
        private const val FLICKER_SIZE = 225f
        private const val FLICKER_FREQ = 4f
        private const val FLICKER_MIN = -0.2f
        private const val FLICKER_MAX = 1.15f
        private const val FAR_FLARE_RADIUS = 300f
        private const val FAR_FLARE_MODULATION = 0.9f
        /** Placeholder cap on the far flare's HDR brightness (Reach peaks at 50 x 0.3). */
        private const val FAR_FLARE_CAP = 5f
        private val FAR_FLARE_COLOR = floatArrayOf(0.4259f, 0.3922f, 1f)
        private const val BUBBLE_SIZE = 45f
        private const val BUBBLE_INTENSITY = 7f
        private const val HALF_GLOW_SIZE = 300f
        private const val BURST_INTENSITY = 1.75f
        private const val BURST_DRIFT = -1f
        private const val CLOUD_INTENSITY = 1f
        private val CLOUD_TINT = floatArrayOf(0.5294f, 0.549f, 0.9059f)
        private const val CLOUD_SPIN = 0.15f
        private const val BLUE_INTENSITY = 0.7f
        private val BLUE_TINT = floatArrayOf(0.4176f, 0.3804f, 1f)
        private const val BLUE_SPIN = 0.05f

        /** A beam that is this bright counts as fully charged. */
        private const val FULL = 0.995f
        /** The hit point has to jump this far (su) for the impact one-shots to fire again. */
        private const val NEW_HIT_DISTANCE = 120f
        /** Fade of the persistent sprites (charge flare / light, flicker, far flare) once the beam is gone. */
        private const val RELEASE_FADE = 0.4f

        private var counter = 0
    }

    val sprites = ArrayList<RuptureSprite>()

    private val random = Random(System.nanoTime() xor (counter++ * 7919L))
    private val salt = counter * 131
    private val color = FloatArray(3)

    private var hasBeam = false
    private var full = false
    private var chargeAge = 0f
    private var beamAge = 0f
    private var fade = 1f
    private var hitAge = -1f
    private var hitPlayed = false
    private val from = Vector2f()
    private val to = Vector2f()
    private val lastHit = Vector2f()
    private var facing = 0f
    private var hit = false

    private var snapshotFresh = false
    private var snapshotBrightness = 0f
    private var snapshotHit = false
    private val snapshotFrom = Vector2f()
    private val snapshotTo = Vector2f()

    private var empAccumulator = 0f
    private var flashAccumulator = 0f
    private var burstAccumulator = 0f
    private var cloudAccumulator = 0f
    private var blueAccumulator = 0f

    private var chargeFlare: RuptureSprite? = null
    private var chargeLight: RuptureSprite? = null
    private var flicker: RuptureSprite? = null
    private var farFlare: RuptureSprite? = null
    /** The impact one-shots: pinned to the live hit point while the beam keeps hitting (a moving
     *  target or a sweeping beam would otherwise leave them behind; Reach's target is static ground). */
    private var bubble: RuptureSprite? = null
    private var halfGlow: RuptureSprite? = null

    /** True once the weapon is idle and every sprite has aged out. */
    val isDone: Boolean get() = !hasBeam && sprites.isEmpty()

    /** True on the frame the beam first touches something (or a new target): play the impact sound. */
    var impactThisFrame = false
        private set
    val hitPoint: Vector2f get() = to

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
        if (snapshotFresh) {
            snapshotFresh = false
            track(snapshotFrom, snapshotTo, snapshotBrightness, snapshotHit, dt)
        } else if (hasBeam) {
            endBeam()
        }
        age(dt)
    }

    private fun track(beamFrom: Vector2f, beamTo: Vector2f, brightness: Float, hitting: Boolean, dt: Float) {
        hasBeam = true
        from.set(beamFrom)
        to.set(beamTo)
        facing = atan2(to.y - from.y, to.x - from.x)
        if (!full) {
            chargeAge += dt
            if (brightness >= FULL) {
                full = true
                beamAge = 0f
            }
            fade = 1f
        } else {
            beamAge += dt
            fade = min(1f, brightness / FULL)
        }
        hit = hitting

        updateCharge(dt)
        if (full) {
            updateBeam(dt)
            updateImpact(dt)
        }
    }

    private fun endBeam() {
        hasBeam = false
        full = false
        chargeAge = 0f
        beamAge = 0f
        hitAge = -1f
        hitPlayed = false
        hit = false
        // the persistent sprites fade out over RELEASE_FADE
        chargeFlare?.let { it.life = it.age + RELEASE_FADE }
        chargeLight?.let { it.life = it.age + RELEASE_FADE }
        flicker?.let { it.life = it.age + RELEASE_FADE }
        farFlare?.let { it.life = it.age + RELEASE_FADE }
        chargeFlare = null
        chargeLight = null
        flicker = null
        farFlare = null
    }

    // --- charge (Reach: emp rings + flashes from the muzzle, the charge flare and light) ---

    private fun updateCharge(dt: Float) {
        // Reach's 3.5 s charge event compressed into the weapon's 2 s chargeup, then its "beam"
        // event sizes while firing.
        val x = if (full) 1f else min(1f, chargeAge / CHARGEUP)
        val empSize = if (full) BEAM_EMP_SIZE else ProjectorCurves.EMP_SIZE.at(x)
        val flashSize = if (full) BEAM_FLASH_SIZE else ProjectorCurves.FLASH_SIZE.at(x)

        empAccumulator += EMP_RATE * dt
        while (empAccumulator >= 1f) {
            empAccumulator -= 1f
            val s = spawn(SpriteKind.PROJECTOR_EMP, from.x, from.y, EMP_LIFE, empSize * UNIT)
            s.paletteV = 0.5f
        }
        flashAccumulator += FLASH_RATE * dt
        while (flashAccumulator >= 1f) {
            flashAccumulator -= 1f
            spawn(SpriteKind.PROJECTOR_FLASH, from.x, from.y, FLASH_LIFE, flashSize * UNIT)
        }

        val flare = chargeFlare ?: spawn(SpriteKind.PROJECTOR_CHARGE_FLARE, from.x, from.y, Float.MAX_VALUE, CHARGE_FLARE_RADIUS * 2f * UNIT).also { chargeFlare = it }
        flare.x = from.x
        flare.y = from.y
        // the flare's 11 s brightness curve: its rise over the 2 s chargeup, then held at full
        val flareX = if (full) 0.4f else min(0.4f, chargeAge / CHARGEUP * 0.4f)
        val b = ProjectorCurves.CHARGE_FLARE_BRIGHTNESS.at(flareX) * CHARGE_FLARE_BRIGHTNESS * fade
        for (c in 0..2) flare.color[c] = CHARGE_FLARE_COLOR[c] * b
        flare.alpha = 1f
        flare.angle = chargeAge * 0.25f * 2f * PI.toFloat() / CHARGE_FLARE_EVENT  // age rotation 0 -> 0.25 turn

        val light = chargeLight ?: spawn(SpriteKind.PROJECTOR_CHARGE_LIGHT, from.x, from.y, Float.MAX_VALUE, CHARGE_LIGHT_RADIUS * 2f * UNIT).also { chargeLight = it }
        light.x = from.x
        light.y = from.y
        val lightX = if (full) CHARGE_LIGHT_RISE else min(CHARGE_LIGHT_RISE, chargeAge / CHARGEUP * CHARGE_LIGHT_RISE)
        val lb = ProjectorCurves.CHARGE_LIGHT.at(lightX / CHARGE_LIGHT_RISE) * CHARGE_LIGHT_BRIGHTNESS * fade
        for (c in 0..2) light.color[c] = CHARGE_LIGHT_COLOR[c] * lb
        light.alpha = 1f
        light.angle = 0f
    }

    // --- beam (Reach: the flicker glow at the origin, the blue-violet flare at the far end) ---

    private fun updateBeam(dt: Float) {
        val glow = flicker ?: spawn(SpriteKind.PROJECTOR_FLICKER, from.x, from.y, Float.MAX_VALUE, FLICKER_SIZE * UNIT).also { flicker = it }
        val x = min(1f, beamAge / FLICKER_LIFE)
        val half = FLICKER_SIZE * UNIT * 0.5f * ProjectorCurves.FLICKER_SCALE.at(x)
        // apex (the bitmap's top) at the muzzle, the rays fanning out along the beam
        glow.x = from.x
        glow.y = from.y
        glow.angle = facing + PI.toFloat()
        glow.offsetAlong = -half
        glow.halfLength = half
        glow.halfWidth = half
        ProjectorCurves.FLICKER_TINT.at(x, color)
        val wander = FLICKER_MIN + (FLICKER_MAX - FLICKER_MIN) * TorpedoLook.noise(beamAge * FLICKER_FREQ + 1.2f, salt)
        val intensity = 1f + max(0f, wander)
        for (c in 0..2) glow.color[c] = color[c] * intensity
        glow.alpha = ProjectorCurves.FLICKER_ALPHA.at(x) * fade

        if (hit) {
            val flare = farFlare ?: spawn(SpriteKind.PROJECTOR_FAR_FLARE, to.x, to.y, Float.MAX_VALUE, FAR_FLARE_RADIUS * 2f * UNIT).also { farFlare = it }
            flare.x = to.x
            flare.y = to.y
            flare.angle = 0f
            val fx = min(1f, (if (hitAge < 0f) 0f else hitAge) / FAR_FLARE_LIFE)
            val b = min(FAR_FLARE_CAP, ProjectorCurves.FAR_FLARE_BRIGHTNESS.at(fx) * FAR_FLARE_MODULATION * ProjectorCurves.FAR_FLARE_AGE.at(fx)) * fade
            for (c in 0..2) flare.color[c] = FAR_FLARE_COLOR[c] * b
            flare.alpha = if (b > 0f) 1f else 0f
        } else {
            farFlare?.alpha = 0f
        }
    }

    // --- impact (Reach beam_impact + vaporization events at the hit point) ---

    private fun updateImpact(dt: Float) {
        if (!hit) {
            hitAge = -1f
            return
        }
        val fresh = hitAge < 0f || distance(to.x, to.y, lastHit.x, lastHit.y) > NEW_HIT_DISTANCE
        if (fresh) {
            hitAge = 0f
            lastHit.set(to)
            impactThisFrame = true
            spawnOneShots()
            burstAccumulator = 1f  // particle starting count 1
            cloudAccumulator = 0f
            blueAccumulator = 0f
        } else {
            hitAge += dt
            for (s in arrayOf(bubble, halfGlow)) {
                if (s == null || s.isDead) continue
                s.x = to.x
                s.y = to.y
                s.angle = facing + PI.toFloat()
            }
        }

        // plasma tongues on a tube around the hit, 2/s
        val bx = min(1f, hitAge / BURST_EVENT)
        burstAccumulator += BURST_RATE * dt
        while (burstAccumulator >= 1f) {
            burstAccumulator -= 1f
            val radius = ProjectorCurves.BURST_RADIUS.at(bx) * UNIT
            val heading = rand(0f, 2f * PI.toFloat())
            val s = spawn(
                SpriteKind.PROJECTOR_BURST, to.x + cos(heading) * radius, to.y + sin(heading) * radius,
                rand(4f, 6f), 0f
            )
            s.baseSize = ProjectorCurves.BURST_SIZE.at(s.random2) * UNIT
            s.vx = -cos(heading) * BURST_DRIFT * UNIT
            s.vy = -sin(heading) * BURST_DRIFT * UNIT
            s.paletteV = 0.5f
        }

        // the cloud ring, from 1 s in
        val vaporAge = hitAge - VAPOR_DELAY
        if (vaporAge >= 0f) {
            val vx = min(1f, vaporAge / VAPOR_EVENT)
            val radius = ProjectorCurves.CLOUD_RADIUS.at(vx)
            cloudAccumulator += ProjectorCurves.CLOUD_RATE.at(vx) * VAPOR_RATE_SCALE * dt
            while (cloudAccumulator >= 1f) {
                cloudAccumulator -= 1f
                val s = spawnOutward(SpriteKind.PROJECTOR_CLOUD, to.x, to.y, radius, CLOUD_SPEED, CLOUD_DRAG, CLOUD_LIFE, 0f, CLOUD_SPIN)
                s.baseSize = (15f + 15f * s.random2) * UNIT
                s.paletteV = 0.5f
            }
            blueAccumulator += ProjectorCurves.BLUE_RATE.at(vx) * VAPOR_RATE_SCALE * dt
            while (blueAccumulator >= 1f) {
                blueAccumulator -= 1f
                val s = spawnOutward(
                    SpriteKind.PROJECTOR_BLUE, to.x, to.y, radius, CLOUD_SPEED, CLOUD_DRAG,
                    BLUE_LIFE + rand(-BLUE_LIFE_SPREAD, BLUE_LIFE_SPREAD), 0f, BLUE_SPIN
                )
                s.baseSize = (10f + 5f * s.random2) * UNIT
                s.paletteV = s.random
                s.uvScale = 1f
            }
        }
    }

    private fun spawnOneShots() {
        val b = spawn(SpriteKind.PROJECTOR_BUBBLE, to.x, to.y, BUBBLE_LIFE, BUBBLE_SIZE * UNIT)
        b.angle = facing + PI.toFloat()   // the dome's top toward the beam's source
        bubble = b
        val g = spawn(SpriteKind.PROJECTOR_HALF_GLOW, to.x, to.y, HALF_GLOW_LIFE, HALF_GLOW_SIZE * UNIT)
        g.angle = facing + PI.toFloat()
        halfGlow = g
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

    /** A particle on a disc of [radius] Reach units flying outward at [speed] units/s with [drag]. */
    private fun spawnOutward(
        kind: SpriteKind, x: Float, y: Float, radius: Float, speed: Float, drag: Float,
        life: Float, size: Float, spin: Float
    ): RuptureSprite {
        val heading = rand(0f, 2f * PI.toFloat())
        val r = sqrt(random.nextFloat()) * radius * UNIT
        val s = spawn(kind, x + cos(heading) * r, y + sin(heading) * r, life, size)
        s.vx = cos(heading) * speed * UNIT
        s.vy = sin(heading) * speed * UNIT
        s.drag = drag
        s.phase = rand(-spin, spin) * 2f * PI.toFloat()
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
            if (s.drag > 0f) {
                val keep = exp(-s.drag * dt)
                s.vx *= keep
                s.vy *= keep
            }
            s.angle += s.phase * dt
            update(s, if (s.life == Float.MAX_VALUE) 0f else s.age / s.life)
        }
    }

    private fun update(s: RuptureSprite, x: Float) {
        when (s.kind) {
            SpriteKind.PROJECTOR_EMP -> {
                val half = s.baseSize * 0.5f * ProjectorCurves.EMP_SCALE.at(x)
                s.halfLength = half
                s.halfWidth = half
                for (c in 0..2) s.color[c] = EMP_INTENSITY
                s.alpha = ProjectorCurves.EMP_ALPHA.at(x)
                s.paletteV = 0.5f
                s.uvScale = 1f
            }
            SpriteKind.PROJECTOR_FLASH -> {
                val half = s.baseSize * 0.5f * ProjectorCurves.EMP_SCALE.at(x)
                s.halfLength = half
                s.halfWidth = half
                val intensity = ProjectorCurves.FLASH_INTENSITY.at(x)
                for (c in 0..2) s.color[c] = FLASH_COLOR[c] * intensity
                s.alpha = ProjectorCurves.FLASH_ALPHA.at(x)
            }
            SpriteKind.PROJECTOR_CHARGE_FLARE, SpriteKind.PROJECTOR_CHARGE_LIGHT, SpriteKind.PROJECTOR_FAR_FLARE -> {
                s.halfLength = s.baseSize * 0.5f
                s.halfWidth = s.halfLength
                // colour is set by the phase updates while the beam lives; after release, fade out
                if (s.life != Float.MAX_VALUE) s.alpha = ((s.life - s.age) / RELEASE_FADE).coerceIn(0f, 1f)
            }
            SpriteKind.PROJECTOR_FLICKER -> {
                if (s.life != Float.MAX_VALUE) s.alpha = ((s.life - s.age) / RELEASE_FADE).coerceIn(0f, 1f) * ProjectorCurves.FLICKER_ALPHA.at(0f)
            }
            SpriteKind.PROJECTOR_BUBBLE -> {
                val half = s.baseSize * 0.5f * ProjectorCurves.BUBBLE_SCALE.at(x)
                s.halfLength = half
                s.halfWidth = half
                s.offsetAlong = half * 0.5f   // the bitmap is the dome's upper half: sit its base on the hit
                ProjectorCurves.BUBBLE_TINT.at(x, color)
                for (c in 0..2) s.color[c] = color[c] * BUBBLE_INTENSITY
                s.alpha = ProjectorCurves.BUBBLE_ALPHA.at(x)
                s.blackPoint = ProjectorCurves.BUBBLE_BLACK_POINT.at(x)
                s.paletteV = x
            }
            SpriteKind.PROJECTOR_HALF_GLOW -> {
                val half = s.baseSize * 0.5f * ProjectorCurves.HALF_GLOW_SCALE.at(x)
                s.halfLength = half
                s.halfWidth = half
                s.offsetAlong = half * 0.5f
                ProjectorCurves.HALF_GLOW_TINT.at(x, color)
                color.copyInto(s.color)
                s.alpha = ProjectorCurves.HALF_GLOW_ALPHA.at(x)
            }
            SpriteKind.PROJECTOR_BURST -> {
                // a tongue standing on the hit plane, its tip (the bitmap's top) toward the beam's source
                s.angle = facing + PI.toFloat()
                val scale = ProjectorCurves.BURST_SCALE.at(x)
                s.halfLength = s.baseSize * scale            // tall: the bitmap is 1:2
                s.halfWidth = s.baseSize * 0.5f * scale
                s.offsetAlong = s.halfLength * 0.6f
                for (c in 0..2) s.color[c] = BURST_INTENSITY
                s.alpha = ProjectorCurves.BURST_ALPHA.at(x)
                s.blackPoint = ProjectorCurves.BURST_BLACK_POINT.at(x)
                s.uvScale = 1f
            }
            SpriteKind.PROJECTOR_CLOUD -> {
                val half = s.baseSize * 0.5f * ProjectorCurves.CLOUD_SCALE.at(x)
                s.halfLength = half
                s.halfWidth = half
                for (c in 0..2) s.color[c] = CLOUD_TINT[c] * CLOUD_INTENSITY
                s.alpha = ProjectorCurves.CLOUD_ALPHA.at(x)
                s.blackPoint = ProjectorCurves.CLOUD_BLACK_POINT.at(x)
            }
            SpriteKind.PROJECTOR_BLUE -> {
                val half = s.baseSize * 0.5f * ProjectorCurves.BLUE_SCALE.at(x)
                s.halfLength = half
                s.halfWidth = half
                for (c in 0..2) s.color[c] = BLUE_TINT[c] * BLUE_INTENSITY
                s.alpha = 1f
                s.blackPoint = ProjectorCurves.BLUE_BLACK_POINT.at(x)
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

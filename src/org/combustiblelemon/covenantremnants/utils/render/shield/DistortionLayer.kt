package org.combustiblelemon.covenantremnants.utils.render.shield

import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ViewportAPI
import org.combustiblelemon.covenantremnants.settings.CovenantSettings
import org.combustiblelemon.lemonlib.render.composition.ShaderLayer
import org.lwjgl.opengl.GL13.GL_TEXTURE0
import org.lwjgl.opengl.GL13.glActiveTexture
import org.lwjgl.opengl.GL20.glGetUniformLocation
import org.lwjgl.opengl.GL20.glUniform1i
import org.lwjgl.opengl.GL20.glUniform2f
import org.lwjgl.opengl.GL20.glUniform4f
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Refraction rings expanding from each shield hit (`distortion.glsl`). A ring's bend strength and
 * how far it travels across the shield both scale with the hit's damage level (developer decision
 * 2026-10-02). Needs a framebuffer copy under the shield's bounding box ([ShieldScreenCopy], texture
 * unit [TEXTURE_UNIT]), taken in [uploadUniforms] only while a ring is running.
 *
 * Waves are stored ship-local so they stay on the hull while it moves and turns. [ship] is null
 * only for the template instance the shared [ShieldProgram] is assembled from.
 */
class DistortionLayer(private val ship: ShipAPI?) : ShaderLayer {
    companion object {
        const val TEXTURE_UNIT = 2
        const val MAX_WAVES = 8

        /** Weakest ring any hit makes (level floor; level is the damage level 0..1). */
        const val MIN_LEVEL = 0.25f

        /** Travel distance as a fraction of the body radius, at level 0 and 1 (>1 crosses the whole shield). */
        const val TRAVEL_MIN = 0.25f
        const val TRAVEL_MAX = 1.2f

        /** Time the ring takes to travel its distance, at level 0 and 1 (s). */
        const val DURATION_MIN = 0.35f
        const val DURATION_MAX = 0.8f

        /** Peak background displacement, world units, at level 0 and 1. */
        const val AMPLITUDE_MIN = 3f
        const val AMPLITUDE_MAX = 12f

        /** Ring half-width, world units, at level 0 and 1. */
        const val WIDTH_MIN = 20f
        const val WIDTH_MAX = 50f
    }

    override val layerName = "distortion"
    override val moduleFile = "data/shaders/shield/distortion.glsl"
    override val stubSource = """
        vec2 distortion_offset(vec2 pixelPos) { return vec2(0.0); }
        vec4 distortion_compose(vec4 color, vec2 pixelPos, vec2 offset) { return color; }
    """.trimIndent()

    private class Wave(val localX: Float, val localY: Float, val level: Float, var age: Float) {
        val duration get() = DURATION_MIN + (DURATION_MAX - DURATION_MIN) * level
    }

    private val waves = ArrayList<Wave>(MAX_WAVES)

    // Set by the owner every frame (screen units: virtual px under UI scaling).
    private var bodyRadiusPx = 1f
    private var copyX = 0f
    private var copyY = 0f
    private var copyW = 0f
    private var copyH = 0f

    val hasActiveWaves get() = waves.isNotEmpty()

    fun setBodyRadius(radiusPx: Float) {
        bodyRadiusPx = radiusPx.coerceAtLeast(1f)
    }

    /** The shield's screen bounding box: the framebuffer region to copy before drawing. */
    fun setCopyRegion(x: Float, y: Float, w: Float, h: Float) {
        copyX = x; copyY = y; copyW = w; copyH = h
    }

    /** A hit at a world position; [level] is the damage level 0..1. */
    fun addWave(worldX: Float, worldY: Float, level: Float) {
        // Player setting off = no rings, so no screen copy (rings already running finish)
        if (!CovenantSettings.shieldDistortion) return
        val ship = ship ?: return
        val dx = worldX - ship.location.x
        val dy = worldY - ship.location.y
        val rad = Math.toRadians(-ship.facing.toDouble())
        val c = cos(rad).toFloat()
        val s = sin(rad).toFloat()
        if (waves.size >= MAX_WAVES) {
            waves.removeAt(waves.indices.maxByOrNull { waves[it].age / waves[it].duration } ?: 0)
        }
        waves.add(Wave(dx * c - dy * s, dx * s + dy * c, max(MIN_LEVEL, level).coerceAtMost(1f), 0f))
    }

    fun clear() {
        waves.clear()
    }

    override fun advance(deltaTime: Float) {
        for (wave in waves) wave.age += deltaTime
        waves.removeAll { it.age >= it.duration }
    }

    override fun uploadUniforms(program: Int, viewport: ViewportAPI) {
        val ship = ship ?: return
        val count = minOf(waves.size, MAX_WAVES)
        glUniform1i(glGetUniformLocation(program, "distortionWaveCount"), count)
        if (count == 0) return

        // Framebuffer under the shield, copied now so it includes everything drawn so far
        glActiveTexture(GL_TEXTURE0 + TEXTURE_UNIT)
        ShieldScreenCopy.copyRegion(
            copyX, copyY, copyW, copyH,
            viewport.visibleWidth / viewport.viewMult, viewport.visibleHeight / viewport.viewMult
        )
        glActiveTexture(GL_TEXTURE0)
        glUniform1i(glGetUniformLocation(program, "distortionScreen"), TEXTURE_UNIT)
        val vp = ShieldScreenCopy.viewport
        glUniform4f(glGetUniformLocation(program, "distortionViewport"), vp[0], vp[1], vp[2], vp[3])

        val rad = Math.toRadians(ship.facing.toDouble())
        val c = cos(rad).toFloat()
        val s = sin(rad).toFloat()
        val pxPerWorld = 1f / viewport.viewMult
        for (i in 0 until count) {
            val wave = waves[i]
            val worldX = ship.location.x + wave.localX * c - wave.localY * s
            val worldY = ship.location.y + wave.localX * s + wave.localY * c
            val progress = (wave.age / wave.duration).coerceIn(0f, 1f)
            val travel = bodyRadiusPx * (TRAVEL_MIN + (TRAVEL_MAX - TRAVEL_MIN) * wave.level)
            val halfWidth = (WIDTH_MIN + (WIDTH_MAX - WIDTH_MIN) * wave.level) * pxPerWorld
            val amplitude = (AMPLITUDE_MIN + (AMPLITUDE_MAX - AMPLITUDE_MIN) * wave.level) * pxPerWorld
            glUniform4f(
                glGetUniformLocation(program, "distortionWaves[$i]"),
                viewport.convertWorldXtoScreenX(worldX),
                viewport.convertWorldYtoScreenY(worldY),
                travel * progress,
                max(halfWidth, 1f)
            )
            glUniform2f(glGetUniformLocation(program, "distortionWaveParams[$i]"), amplitude, 1f - progress)
        }
    }
}

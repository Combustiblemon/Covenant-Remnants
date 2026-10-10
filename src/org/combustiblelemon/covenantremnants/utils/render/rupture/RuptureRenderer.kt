package org.combustiblelemon.covenantremnants.utils.render.rupture

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.ViewportAPI
import org.combustiblelemon.covenantremnants.settings.CovenantSettings
import org.combustiblelemon.covenantremnants.systems.FleetSlipspaceJump
import org.combustiblelemon.lemonlib.render.Shader
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11.GL_ALL_ATTRIB_BITS
import org.lwjgl.opengl.GL11.GL_BLEND
import org.lwjgl.opengl.GL11.GL_CULL_FACE
import org.lwjgl.opengl.GL11.GL_DEPTH_TEST
import org.lwjgl.opengl.GL11.GL_LINEAR
import org.lwjgl.opengl.GL11.GL_RGBA
import org.lwjgl.opengl.GL11.GL_TEXTURE_2D
import org.lwjgl.opengl.GL11.GL_TEXTURE_BINDING_2D
import org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER
import org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER
import org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S
import org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T
import org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE
import org.lwjgl.opengl.GL11.GL_VIEWPORT
import org.lwjgl.opengl.GL11.glBindTexture
import org.lwjgl.opengl.GL11.glCopyTexSubImage2D
import org.lwjgl.opengl.GL11.glDeleteTextures
import org.lwjgl.opengl.GL11.glDisable
import org.lwjgl.opengl.GL11.glEnable
import org.lwjgl.opengl.GL11.glGenTextures
import org.lwjgl.opengl.GL11.glGetInteger
import org.lwjgl.opengl.GL11.glPopAttrib
import org.lwjgl.opengl.GL11.glPushAttrib
import org.lwjgl.opengl.GL11.glTexImage2D
import org.lwjgl.opengl.GL11.glTexParameteri
import org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE
import org.lwjgl.opengl.GL13.GL_ACTIVE_TEXTURE
import org.lwjgl.opengl.GL13.GL_TEXTURE0
import org.lwjgl.opengl.GL13.glActiveTexture
import org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER
import org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER_BINDING
import org.lwjgl.opengl.GL15.glBindBuffer
import org.lwjgl.opengl.GL20.GL_CURRENT_PROGRAM
import org.lwjgl.opengl.GL20.glUseProgram
import org.lwjgl.opengl.GL30.GL_VERTEX_ARRAY_BINDING
import org.lwjgl.opengl.GL30.glBindVertexArray
import java.nio.ByteBuffer

/**
 * Slipspace rupture renderer (Fleet Slipspace Jump), remade from the Halo: Reach slipspace bomb.
 * Draws every live [RuptureEffect] with two programs: analytic spheres
 * ([RuptureSphereShader], ruptureSphere.vert/.frag) and batched sprites ([RuptureSpriteShader],
 * ruptureSprite.vert/.frag), plus a copy of the screen for the charge's heat-haze rings.
 *
 * Order: per rupture back to front the spheres (light glows and burst shells, charge core, glow
 * sphere, black body, web, ring); then the heat haze (all ruptures, over a fresh screen copy, so
 * the rings ripple over the spheres — developer 2026-10-02); then per rupture the additive sprites,
 * lens flares, twinkles. The programs are shared ([RupturePrograms], compiled at game start).
 */
class RuptureRenderer : Shader {
    private companion object {
        val ADDITIVE_SPRITES = listOf(
            SpriteMaterial.SMOKE,
            SpriteMaterial.BURST_PLASMA,
            SpriteMaterial.PLASMA_FLAME,
            SpriteMaterial.CHARGE_PLASMA,
            SpriteMaterial.SPARK,
            SpriteMaterial.ARK_GLOW,
            SpriteMaterial.ARK_RING,
            SpriteMaterial.ARK_RING_ANALYTIC,
            SpriteMaterial.ARK_FLASH,
            SpriteMaterial.ARK_STREAK
        )

        /** Materials that read the screen copy (one copy per frame, after the spheres and the tube). */
        val SCREEN_SPRITES = listOf(SpriteMaterial.DISTORTION, SpriteMaterial.ARK_REFRACT)
        const val TEXTURE_UNITS = 5
    }

    /**
     * Which part of each effect a [render] call draws. FULL (combat) draws everything. The
     * campaign draws the Ark effects in two passes around the fleets (developer 2026-10-08, so a
     * fleet reads as coming out of the middle of the tube): BELOW = the light pool, dim, mouth
     * glow, the tube's back wall, the heat haze and the far half of the mouth-plane sprites;
     * ABOVE = the front wall, the near half, the particles, flares and twinkles. Non-Ark effects
     * draw whole in ABOVE and not at all in BELOW.
     */
    enum class Pass { FULL, BELOW, ABOVE }

    var pass = Pass.FULL

    private val SPLIT: (RuptureSprite) -> Boolean = { it.split }
    private val WHOLE: (RuptureSprite) -> Boolean = { !it.split }

    /** Effects to draw on the next [render]; the owner keeps the list current. */
    val effects = ArrayList<RuptureEffect>()

    /**
     * Whole-frame alpha, applied on top of every effect's `master`. The campaign ability renderer
     * ignores the viewport's alpha (the screen fade), so the campaign owner sets this from it;
     * combat leaves it at 1.
     */
    var alphaMult = 1f

    private var time = 0f

    private var screenCopy = 0
    private var screenCopyWidth = 0
    private var screenCopyHeight = 0
    private val viewportBuffer = BufferUtils.createIntBuffer(16)
    private val viewportPx = IntArray(4)

    fun advance(amount: Float) {
        time += amount
    }

    override fun render(viewport: ViewportAPI) {
        if (effects.isEmpty()) return
        if (alphaMult <= 0f) return
        if (alphaMult < 1f) {
            // Scale every effect's master for this draw, then put it back.
            val saved = FloatArray(effects.size) { effects[it].master }
            try {
                for (i in effects.indices) effects[i].master = saved[i] * alphaMult
                renderAll(viewport)
            } finally {
                for (i in effects.indices) effects[i].master = saved[i]
            }
            return
        }
        renderAll(viewport)
    }

    private fun renderAll(viewport: ViewportAPI) {
        val view = RuptureView(viewport)
        val exposure = FleetSlipspaceJump.exposure.value
        // Player setting off = no heat haze, which also skips the full-screen copy below
        val distortion = if (CovenantSettings.heatHaze) FleetSlipspaceJump.distortion.value else 0f

        // Capture the GL state before anything binds - including the lazy program construction.
        glPushAttrib(GL_ALL_ATTRIB_BITS)
        val currentProgram = glGetInteger(GL_CURRENT_PROGRAM)
        val currentVao = glGetInteger(GL_VERTEX_ARRAY_BINDING)
        val currentVbo = glGetInteger(GL_ARRAY_BUFFER_BINDING)
        val currentActiveTexture = glGetInteger(GL_ACTIVE_TEXTURE)
        val currentTextures = IntArray(TEXTURE_UNITS) { unit ->
            glActiveTexture(GL_TEXTURE0 + unit)
            glGetInteger(GL_TEXTURE_BINDING_2D)
        }

        try {
            val spheres = RupturePrograms.spheres()
            val sprites = RupturePrograms.sprites()

            glDisable(GL_DEPTH_TEST)
            glDisable(GL_CULL_FACE)
            glEnable(GL_BLEND)

            val pass = pass
            // Effects whose spheres / tube / screen-copy sprites draw in this pass
            fun lower(effect: RuptureEffect): Boolean = pass == Pass.FULL || (pass == Pass.BELOW) == (effect.kind == RuptureEffect.Kind.ARK)
            // Effects whose particles, flares and twinkles draw in this pass
            fun upper(effect: RuptureEffect): Boolean = pass != Pass.BELOW
            val wallSign = when (pass) { Pass.FULL -> 0f; Pass.BELOW -> -1f; Pass.ABOVE -> 1f }

            for (effect in effects) {
                if (!lower(effect)) continue
                for (sphere in effect.spheres) {
                    if (sphere.active) spheres.draw(sphere, effect, view, time, exposure)
                }
            }

            // The Ark tube after the spheres, before the heat haze so the distortion ripples over it
            if (effects.any { it.tube.active }) {
                val tubes = RupturePrograms.tube()
                for (effect in effects) {
                    if (effect.tube.active && (pass == Pass.FULL || effect.kind == RuptureEffect.Kind.ARK)) {
                        tubes.draw(effect.tube, effect, view, exposure, wallSign)
                    }
                }
            }

            // The Ark mouth window (alpha, under the fleets): after the tube so it covers the back wall and the glow
            for (effect in effects) {
                if (effect.kind == RuptureEffect.Kind.ARK && lower(effect)) {
                    sprites.draw(SpriteMaterial.ARK_WINDOW, effect, view, time, exposure)
                }
            }

            // After the spheres and the tube, so the heat-haze rings ripple over the charge core and the tube
            if (distortion > 0f && effects.any { effect -> lower(effect) && effect.sprites.any { it.kind.material.usesScreenCopy } }) {
                copyScreen()
                for (effect in effects) {
                    if (!lower(effect)) continue
                    for (material in SCREEN_SPRITES) {
                        sprites.draw(material, effect, view, time, exposure, distortion, screenCopy, viewportPx)
                    }
                }
            }

            for (effect in effects) {
                if (pass != Pass.FULL && effect.kind == RuptureEffect.Kind.ARK) {
                    // Mouth-plane sprites: the half toward the tube body is the near half (above the fleets).
                    val near = if (effect.tube.flip) 1f else -1f
                    val halfSign = if (pass == Pass.ABOVE) near else -near
                    for (material in ADDITIVE_SPRITES) {
                        sprites.draw(material, effect, view, time, exposure, halfSign = halfSign, filter = SPLIT)
                        if (pass == Pass.ABOVE) sprites.draw(material, effect, view, time, exposure, filter = WHOLE)
                    }
                } else if (upper(effect)) {
                    for (material in ADDITIVE_SPRITES) sprites.draw(material, effect, view, time, exposure)
                }
                if (!upper(effect)) continue
                for (flare in effect.flares) {
                    if (flare.active) sprites.drawFlare(flare, effect, view, time, exposure)
                }
                sprites.draw(SpriteMaterial.TWINKLE, effect, view, time, exposure)
            }
        } finally {
            glUseProgram(currentProgram)
            glBindVertexArray(currentVao)
            glBindBuffer(GL_ARRAY_BUFFER, currentVbo)
            for (unit in TEXTURE_UNITS - 1 downTo 0) {
                glActiveTexture(GL_TEXTURE0 + unit)
                glBindTexture(GL_TEXTURE_2D, currentTextures[unit])
            }
            glActiveTexture(currentActiveTexture)
            glPopAttrib()
        }
    }

    /** Copies the current GL viewport into [screenCopy] (texture unit 4 left bound to it). */
    private fun copyScreen() {
        glGetInteger(GL_VIEWPORT, viewportBuffer)
        for (i in 0 until 4) viewportPx[i] = viewportBuffer.get(i)
        val width = viewportPx[2]
        val height = viewportPx[3]

        glActiveTexture(GL_TEXTURE0 + 4)
        if (screenCopy == 0) screenCopy = glGenTextures()
        glBindTexture(GL_TEXTURE_2D, screenCopy)
        if (width != screenCopyWidth || height != screenCopyHeight) {
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, null as ByteBuffer?)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
            screenCopyWidth = width
            screenCopyHeight = height
        }
        glCopyTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, viewportPx[0], viewportPx[1], width, height)
        glActiveTexture(GL_TEXTURE0)
    }

    override fun cleanup() {
        // The programs are shared and live for the whole game; only the screen copy is ours.
        try {
            if (screenCopy != 0) glDeleteTextures(screenCopy)
        } catch (e: Exception) {
            Global.getLogger(this.javaClass).error("Error during cleanup: ${e.message}")
        }
        screenCopy = 0
        screenCopyWidth = 0
        screenCopyHeight = 0
    }
}

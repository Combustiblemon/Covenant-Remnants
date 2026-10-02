package org.combustiblelemon.covenantremnants.utils.render.rupture

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.ViewportAPI
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
            SpriteMaterial.SPARK
        )
        const val TEXTURE_UNITS = 5
    }

    /** Effects to draw on the next [render]; the owner keeps the list current. */
    val effects = ArrayList<RuptureEffect>()

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
        val view = RuptureView(viewport)
        val exposure = FleetSlipspaceJump.exposure.value
        val distortion = FleetSlipspaceJump.distortion.value

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

            for (effect in effects) {
                for (sphere in effect.spheres) {
                    if (sphere.active) spheres.draw(sphere, effect, view, time, exposure)
                }
            }

            // After the spheres, so the heat-haze rings ripple over the charge core
            if (distortion > 0f && effects.any { effect -> effect.sprites.any { it.kind == SpriteKind.DISTORTION } }) {
                copyScreen()
                for (effect in effects) {
                    sprites.draw(SpriteMaterial.DISTORTION, effect, view, time, exposure, distortion, screenCopy, viewportPx)
                }
            }

            for (effect in effects) {
                for (material in ADDITIVE_SPRITES) sprites.draw(material, effect, view, time, exposure)
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

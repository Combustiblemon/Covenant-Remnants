package org.combustiblelemon.covenantremnants.utils.render.blink

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.ViewportAPI
import org.combustiblelemon.covenantremnants.utils.render.rupture.RuptureView
import org.lwjgl.opengl.GL11.GL_BLEND
import org.lwjgl.opengl.GL11.GL_BLEND_DST
import org.lwjgl.opengl.GL11.GL_BLEND_SRC
import org.lwjgl.opengl.GL11.GL_TEXTURE_2D
import org.lwjgl.opengl.GL11.GL_TEXTURE_BINDING_2D
import org.lwjgl.opengl.GL11.glBindTexture
import org.lwjgl.opengl.GL11.glBlendFunc
import org.lwjgl.opengl.GL11.glDisable
import org.lwjgl.opengl.GL11.glEnable
import org.lwjgl.opengl.GL11.glGetInteger
import org.lwjgl.opengl.GL11.glIsEnabled
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

/**
 * Draws the slipspace drive hull ripples ([BlinkRipple]) with the shared blinkRipple program,
 * compiled once at game start ([warmUp], from the mod plugin) like the shield program. The blink
 * portals are rupture effects drawn by the rupture renderer. Wraps each batch in a GL state
 * save/restore so the engine's fixed-function rendering continues untouched.
 */
object BlinkRenderer {
    private var rippleShader: BlinkRippleShader? = null

    /** Compiles the program now (game start, GL thread). Failures are logged; the draws retry lazily. */
    fun warmUp() {
        if (rippleShader != null) return
        val start = System.nanoTime()
        try {
            ensureShaders()
            Global.getLogger(BlinkRenderer::class.java)
                .info("Covenant slipspace drive ripple program compiled in ${(System.nanoTime() - start) / 1_000_000} ms")
        } catch (e: Exception) {
            Global.getLogger(BlinkRenderer::class.java)
                .warn("Covenant blink program warm-up failed, will compile on first use: ${e.message}")
        }
    }

    private fun ensureShaders() {
        if (rippleShader == null) rippleShader = BlinkRippleShader()
    }

    fun drawRipples(ripples: List<BlinkRipple>, viewport: ViewportAPI, time: Float) {
        if (ripples.isEmpty()) return
        withState {
            ensureShaders()
            val view = RuptureView(viewport)
            val shader = rippleShader ?: return@withState
            for (ripple in ripples) shader.draw(ripple, view, time)
        }
    }

    private inline fun withState(block: () -> Unit) {
        val currentProgram = glGetInteger(GL_CURRENT_PROGRAM)
        val currentVao = glGetInteger(GL_VERTEX_ARRAY_BINDING)
        val currentVbo = glGetInteger(GL_ARRAY_BUFFER_BINDING)
        val currentActiveTexture = glGetInteger(GL_ACTIVE_TEXTURE)
        val currentTexture = glGetInteger(GL_TEXTURE_BINDING_2D)
        val blendEnabled = glIsEnabled(GL_BLEND)
        val blendSrc = glGetInteger(GL_BLEND_SRC)
        val blendDst = glGetInteger(GL_BLEND_DST)
        try {
            glEnable(GL_BLEND)
            glActiveTexture(GL_TEXTURE0)
            block()
        } finally {
            glBindVertexArray(currentVao)
            glBindBuffer(GL_ARRAY_BUFFER, currentVbo)
            glUseProgram(currentProgram)
            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GL_TEXTURE_2D, currentTexture)
            glActiveTexture(currentActiveTexture)
            glBlendFunc(blendSrc, blendDst)
            if (!blendEnabled) glDisable(GL_BLEND)
        }
    }
}

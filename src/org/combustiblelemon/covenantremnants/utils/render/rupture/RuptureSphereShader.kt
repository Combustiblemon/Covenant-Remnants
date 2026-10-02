package org.combustiblelemon.covenantremnants.utils.render.rupture

import org.combustiblelemon.covenantremnants.utils.render.PlainProgram
import com.fs.starfarer.api.combat.ViewportAPI
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11.GL_BLEND
import org.lwjgl.opengl.GL11.GL_FLOAT
import org.lwjgl.opengl.GL11.GL_ONE
import org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA
import org.lwjgl.opengl.GL11.GL_SRC_ALPHA
import org.lwjgl.opengl.GL11.GL_TRIANGLE_FAN
import org.lwjgl.opengl.GL11.glBlendFunc
import org.lwjgl.opengl.GL11.glDrawArrays
import org.lwjgl.opengl.GL11.glEnable
import org.lwjgl.opengl.GL13.GL_TEXTURE0
import org.lwjgl.opengl.GL13.glActiveTexture
import org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER
import org.lwjgl.opengl.GL15.GL_DYNAMIC_DRAW
import org.lwjgl.opengl.GL15.glBindBuffer
import org.lwjgl.opengl.GL15.glBufferData
import org.lwjgl.opengl.GL15.glBufferSubData
import org.lwjgl.opengl.GL15.glDeleteBuffers
import org.lwjgl.opengl.GL15.glGenBuffers
import org.lwjgl.opengl.GL20.glEnableVertexAttribArray
import org.lwjgl.opengl.GL20.glUniform1f
import org.lwjgl.opengl.GL20.glUniform1i
import org.lwjgl.opengl.GL20.glUniform2f
import org.lwjgl.opengl.GL20.glUniform3f
import org.lwjgl.opengl.GL20.glUniform4f
import org.lwjgl.opengl.GL20.glUseProgram
import org.lwjgl.opengl.GL20.glVertexAttribPointer
import org.lwjgl.opengl.GL30.glBindVertexArray
import org.lwjgl.opengl.GL30.glDeleteVertexArrays
import org.lwjgl.opengl.GL30.glGenVertexArrays

/** Screen-space helpers for one frame: world to screen units and NDC. */
internal class RuptureView(private val viewport: ViewportAPI) {
    val screenWidth = viewport.visibleWidth / viewport.viewMult
    val screenHeight = viewport.visibleHeight / viewport.viewMult

    fun screenX(worldX: Float): Float = viewport.convertWorldXtoScreenX(worldX)

    fun screenY(worldY: Float): Float = viewport.convertWorldYtoScreenY(worldY)

    fun screenLength(world: Float): Float = world / viewport.viewMult

    fun ndcX(screenX: Float): Float = screenX / screenWidth * 2f - 1f

    fun ndcY(screenY: Float): Float = screenY / screenHeight * 2f - 1f

    fun isOffscreen(x: Float, y: Float, half: Float): Boolean =
        x + half < 0f || x - half > screenWidth || y + half < 0f || y - half > screenHeight
}

/**
 * Draws the rupture's analytic spheres (ruptureSphere.vert/.frag): one screen quad per sphere,
 * blend function by mode. Construct on the GL thread.
 */
internal class RuptureSphereShader {
    private companion object {
        const val VERTEX_FILE = "data/shaders/rupture/ruptureSphere.vert"
        const val FRAGMENT_FILE = "data/shaders/rupture/ruptureSphere.frag"

        // Quad margins beyond the sphere, in radii: where the glow halo and ring glow fade out.
        const val HALO_REACH = 7f
        const val RING_REACH = 26f
    }

    private val program = PlainProgram(VERTEX_FILE, FRAGMENT_FILE)
    private val vao: Int = glGenVertexArrays()
    private val vbo: Int = glGenBuffers()
    private val quad = BufferUtils.createFloatBuffer(8)
    private val xform = FloatArray(4)

    init {
        glBindVertexArray(vao)
        glBindBuffer(GL_ARRAY_BUFFER, vbo)
        glBufferData(GL_ARRAY_BUFFER, 8L * 4L, GL_DYNAMIC_DRAW)
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 2 * 4, 0)
        glEnableVertexAttribArray(0)
        glBindVertexArray(0)
        glBindBuffer(GL_ARRAY_BUFFER, 0)

        glUseProgram(program.id)
        glUniform1i(program.location("noiseA"), 0)
        glUniform1i(program.location("noiseB"), 1)
        glUniform1i(program.location("palette"), 2)
        glUniform1i(program.location("alphaMap"), 3)
        glUseProgram(0)
    }

    fun draw(sphere: SphereDraw, effect: RuptureEffect, view: RuptureView, time: Float, exposure: Float) {
        val radius = view.screenLength(sphere.radius)
        if (radius < 0.5f) return
        val margin = when (sphere.mode) {
            SphereMode.GLOW, SphereMode.CORE -> sphere.haloWidth * HALO_REACH
            SphereMode.RING -> sphere.ringWidth * RING_REACH
            else -> 0f
        }
        val half = radius * (1f + margin) + 2f
        val centerX = view.screenX(effect.center.x)
        val centerY = view.screenY(effect.center.y)
        if (view.isOffscreen(centerX, centerY, half)) return

        val left = view.ndcX(centerX - half)
        val right = view.ndcX(centerX + half)
        val bottom = view.ndcY(centerY - half)
        val top = view.ndcY(centerY + half)
        quad.clear()
        quad.put(left).put(bottom).put(right).put(bottom).put(right).put(top).put(left).put(top)
        quad.flip()

        glUseProgram(program.id)
        glBindVertexArray(vao)
        glBindBuffer(GL_ARRAY_BUFFER, vbo)
        glBufferSubData(GL_ARRAY_BUFFER, 0, quad)

        glEnable(GL_BLEND)
        if (sphere.mode.additive) glBlendFunc(GL_ONE, GL_ONE) else glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)

        glUniform2f(program.location("resolution"), view.screenWidth, view.screenHeight)
        glUniform2f(program.location("center"), centerX, centerY)
        glUniform1f(program.location("radius"), radius)
        glUniform1i(program.location("mode"), sphere.mode.id)
        glUniform3f(program.location("color"), sphere.color[0], sphere.color[1], sphere.color[2])
        glUniform1f(program.location("intensity"), sphere.intensity)
        glUniform1f(program.location("alpha"), sphere.alpha)
        glUniform1f(program.location("exposure"), exposure)
        glUniform1f(program.location("master"), effect.master)
        glUniform1f(program.location("halo"), sphere.halo)
        glUniform1f(program.location("haloWidth"), sphere.haloWidth)
        glUniform1f(program.location("ringWidth"), sphere.ringWidth)
        glUniform1f(program.location("lightStart"), sphere.lightStart)
        glUniform1f(program.location("paletteV"), sphere.paletteV)
        glUniform1f(program.location("uOffset"), sphere.uOffset + effect.seed)
        uploadXform("xformA", sphere.xformA, time)
        uploadXform("xformB", sphere.xformB, time)

        bindTexture(0, sphere.noiseA)
        bindTexture(1, sphere.noiseB)
        bindTexture(2, sphere.palette)
        bindTexture(3, sphere.alphaMap)
        glActiveTexture(GL_TEXTURE0)

        glDrawArrays(GL_TRIANGLE_FAN, 0, 4)
    }

    private fun uploadXform(name: String, transform: NoiseTransform?, time: Float) {
        if (transform == null) {
            glUniform4f(program.location(name), 1f, 1f, 0f, 0f)
            return
        }
        transform.write(time, xform)
        glUniform4f(program.location(name), xform[0], xform[1], xform[2], xform[3])
    }

    private fun bindTexture(unit: Int, texture: RuptureTexture?) {
        if (texture == null) return
        glActiveTexture(GL_TEXTURE0 + unit)
        texture.bind()
    }

    fun dispose() {
        glDeleteBuffers(vbo)
        glDeleteVertexArrays(vao)
        program.dispose()
    }
}

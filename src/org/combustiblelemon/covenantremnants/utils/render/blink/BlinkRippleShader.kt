package org.combustiblelemon.covenantremnants.utils.render.blink

import org.combustiblelemon.covenantremnants.utils.render.PlainProgram
import org.combustiblelemon.covenantremnants.utils.render.rupture.RuptureView
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11.GL_FLOAT
import org.lwjgl.opengl.GL11.GL_ONE
import org.lwjgl.opengl.GL11.GL_TEXTURE_2D
import org.lwjgl.opengl.GL11.GL_TRIANGLE_FAN
import org.lwjgl.opengl.GL11.glBindTexture
import org.lwjgl.opengl.GL11.glBlendFunc
import org.lwjgl.opengl.GL11.glDrawArrays
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
import org.lwjgl.opengl.GL20.glUniform3f
import org.lwjgl.opengl.GL20.glUseProgram
import org.lwjgl.opengl.GL20.glVertexAttribPointer
import org.lwjgl.opengl.GL30.glBindVertexArray
import org.lwjgl.opengl.GL30.glDeleteVertexArrays
import org.lwjgl.opengl.GL30.glGenVertexArrays
import java.nio.FloatBuffer
import kotlin.math.cos
import kotlin.math.sin

/**
 * Draws one [BlinkRipple] with blinkRipple.vert/.frag over the ship's own sprite: the quad is the
 * sprite's rectangle in the world (same placement the engine uses — the hull centre at the
 * sprite's centre point, the sprite's up axis along the ship's facing), the fragment shader masks
 * by the sprite's alpha and sweeps the ODST ripple (plasma puffs, outward spark streaks, bow
 * flash) along the hull. Additive. Construct on the GL thread; the caller saves and restores GL
 * state.
 */
internal class BlinkRippleShader {
    private companion object {
        const val VERTEX_FILE = "data/shaders/blink/blinkRipple.vert"
        const val FRAGMENT_FILE = "data/shaders/blink/blinkRipple.frag"

        // aPos 2, aUv 2, aLocal 2
        const val FLOATS_PER_VERTEX = 6
        const val VERTICES = 4

        // Look (placeholders — see planning/implementations/slipspace-drive-blink.md). Colours
        // after ODST: radiation_plasma through the plasma_clouds palette (violet-blue) and
        // radiation_energy's tint #2141e3 through a fire palette (blue-white).
        const val FRONT_WIDTH = 0.07f   // wavefront half-width, hull lengths
        const val TAIL = 0.3f           // glow left behind the front, e-folding hull lengths
        val PUFF_COLOR = floatArrayOf(0.40f, 0.33f, 1f)
        val SPARK_COLOR = floatArrayOf(0.55f, 0.68f, 1f)

        // Sprite-space corners: x across (0..w), y stern (0) to bow (h)
        val CORNER_X = floatArrayOf(0f, 1f, 1f, 0f)
        val CORNER_Y = floatArrayOf(0f, 0f, 1f, 1f)
    }

    private val program = PlainProgram(VERTEX_FILE, FRAGMENT_FILE)
    private val vao: Int = glGenVertexArrays()
    private val vbo: Int = glGenBuffers()
    private val buffer: FloatBuffer = BufferUtils.createFloatBuffer(FLOATS_PER_VERTEX * VERTICES)

    init {
        glBindVertexArray(vao)
        glBindBuffer(GL_ARRAY_BUFFER, vbo)
        glBufferData(GL_ARRAY_BUFFER, (FLOATS_PER_VERTEX * VERTICES * 4).toLong(), GL_DYNAMIC_DRAW)
        val stride = FLOATS_PER_VERTEX * 4
        glVertexAttribPointer(0, 2, GL_FLOAT, false, stride, 0L)
        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 2L * 4)
        glVertexAttribPointer(2, 2, GL_FLOAT, false, stride, 4L * 4)
        for (attribute in 0..2) glEnableVertexAttribArray(attribute)
        glBindVertexArray(0)
        glBindBuffer(GL_ARRAY_BUFFER, 0)

        glUseProgram(program.id)
        glUniform1i(program.location("hull"), 0)
        glUniform1f(program.location("width"), FRONT_WIDTH)
        glUniform1f(program.location("tail"), TAIL)
        glUniform3f(program.location("puffColor"), PUFF_COLOR[0], PUFF_COLOR[1], PUFF_COLOR[2])
        glUniform3f(program.location("sparkColor"), SPARK_COLOR[0], SPARK_COLOR[1], SPARK_COLOR[2])
        glUseProgram(0)
    }

    fun draw(ripple: BlinkRipple, view: RuptureView, time: Float) {
        if (ripple.intensity <= 0f && ripple.flash <= 0f) return
        val ship = ripple.ship
        val sprite = ship.spriteAPI ?: return
        if (!ship.isAlive) return
        val width = sprite.width
        val height = sprite.height
        if (width <= 0f || height <= 0f) return
        // Hull centre inside the sprite (bottom-left origin); the engine falls back to the middle.
        val centerX = if (sprite.centerX < 0f) width / 2f else sprite.centerX
        val centerY = if (sprite.centerY < 0f) height / 2f else sprite.centerY

        val location = ship.location
        val radians = Math.toRadians((ship.facing - 90f).toDouble())
        val cos = cos(radians).toFloat()
        val sin = sin(radians).toFloat()

        val half = view.screenLength(maxOf(width, height))
        val cx = view.screenX(location.x)
        val cy = view.screenY(location.y)
        if (view.isOffscreen(cx, cy, half)) return

        buffer.clear()
        for (i in 0 until VERTICES) {
            val localX = CORNER_X[i] * width - centerX
            val localY = CORNER_Y[i] * height - centerY
            val worldX = location.x + localX * cos - localY * sin
            val worldY = location.y + localX * sin + localY * cos
            buffer.put(view.ndcX(view.screenX(worldX)))
            buffer.put(view.ndcY(view.screenY(worldY)))
            buffer.put(sprite.texX + sprite.texWidth * CORNER_X[i])
            buffer.put(sprite.texY + sprite.texHeight * CORNER_Y[i])
            buffer.put(CORNER_X[i] * 2f - 1f)
            buffer.put(CORNER_Y[i])
        }
        buffer.flip()

        glUseProgram(program.id)
        glUniform1f(program.location("front"), ripple.front)
        glUniform1f(program.location("intensity"), maxOf(ripple.intensity, ripple.flash))
        glUniform1f(program.location("flash"), ripple.flash)
        glUniform1f(program.location("time"), time)

        glActiveTexture(GL_TEXTURE0)
        glBindTexture(GL_TEXTURE_2D, sprite.textureId)

        glBindVertexArray(vao)
        glBindBuffer(GL_ARRAY_BUFFER, vbo)
        glBufferSubData(GL_ARRAY_BUFFER, 0L, buffer)
        glBlendFunc(GL_ONE, GL_ONE)
        glDrawArrays(GL_TRIANGLE_FAN, 0, VERTICES)
    }

    fun dispose() {
        glDeleteBuffers(vbo)
        glDeleteVertexArrays(vao)
        program.dispose()
    }
}

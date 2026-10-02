package org.combustiblelemon.covenantremnants.utils.render.rupture

import org.combustiblelemon.covenantremnants.utils.render.PlainProgram
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11.GL_BLEND
import org.lwjgl.opengl.GL11.GL_FLOAT
import org.lwjgl.opengl.GL11.GL_ONE
import org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA
import org.lwjgl.opengl.GL11.GL_SRC_ALPHA
import org.lwjgl.opengl.GL11.GL_TEXTURE_2D
import org.lwjgl.opengl.GL11.GL_TRIANGLES
import org.lwjgl.opengl.GL11.glBindTexture
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
import org.lwjgl.opengl.GL20.glUniform4f
import org.lwjgl.opengl.GL20.glUseProgram
import org.lwjgl.opengl.GL20.glVertexAttribPointer
import org.lwjgl.opengl.GL30.glBindVertexArray
import org.lwjgl.opengl.GL30.glDeleteVertexArrays
import org.lwjgl.opengl.GL30.glGenVertexArrays
import java.nio.FloatBuffer
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Draws the rupture's sprites (ruptureSprite.vert/.frag) in batches: one draw call per material
 * per effect, the quads built on the CPU each frame. Construct on the GL thread.
 */
internal class RuptureSpriteShader {
    private companion object {
        const val VERTEX_FILE = "data/shaders/rupture/ruptureSprite.vert"
        const val FRAGMENT_FILE = "data/shaders/rupture/ruptureSprite.frag"

        // aPos 2, aUv 2, aLocal 2, aColor 4, aParams 4
        const val FLOATS_PER_VERTEX = 14
        const val VERTICES_PER_SPRITE = 6

        // Heat-haze push at full strength, as a share of the ring's own screen size.
        const val DISTORTION_PUSH = 0.1f

        // Corners of a sprite in local space (two triangles).
        val CORNER_X = floatArrayOf(-1f, 1f, 1f, -1f, 1f, -1f)
        val CORNER_Y = floatArrayOf(-1f, -1f, 1f, -1f, 1f, 1f)
    }

    private val program = PlainProgram(VERTEX_FILE, FRAGMENT_FILE)
    private val vao: Int = glGenVertexArrays()
    private val vbo: Int = glGenBuffers()
    private var capacitySprites = 0
    private var buffer: FloatBuffer = BufferUtils.createFloatBuffer(1)
    private val xform = FloatArray(4)

    init {
        glBindVertexArray(vao)
        glBindBuffer(GL_ARRAY_BUFFER, vbo)
        ensureCapacity(256)
        val stride = FLOATS_PER_VERTEX * 4
        glVertexAttribPointer(0, 2, GL_FLOAT, false, stride, 0L)
        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 2L * 4)
        glVertexAttribPointer(2, 2, GL_FLOAT, false, stride, 4L * 4)
        glVertexAttribPointer(3, 4, GL_FLOAT, false, stride, 6L * 4)
        glVertexAttribPointer(4, 4, GL_FLOAT, false, stride, 10L * 4)
        for (attribute in 0..4) glEnableVertexAttribArray(attribute)
        glBindVertexArray(0)
        glBindBuffer(GL_ARRAY_BUFFER, 0)

        glUseProgram(program.id)
        glUniform1i(program.location("baseMap"), 0)
        glUniform1i(program.location("noiseB"), 1)
        glUniform1i(program.location("palette"), 2)
        glUniform1i(program.location("alphaMap"), 3)
        glUniform1i(program.location("screenCopy"), 4)
        glUseProgram(0)
    }

    /** Grows the CPU and GPU buffers to hold [sprites] sprites; the VBO must be bound. */
    private fun ensureCapacity(sprites: Int) {
        if (sprites <= capacitySprites) return
        capacitySprites = max(sprites, capacitySprites * 2)
        buffer = BufferUtils.createFloatBuffer(capacitySprites * VERTICES_PER_SPRITE * FLOATS_PER_VERTEX)
        glBufferData(GL_ARRAY_BUFFER, buffer.capacity() * 4L, GL_DYNAMIC_DRAW)
    }

    /**
     * Draws every sprite of [effect] that uses [material]. [screenCopy] = the copy texture and
     * GL viewport (x, y, w, h) for DISTORTION.
     */
    fun draw(
        material: SpriteMaterial,
        effect: RuptureEffect,
        view: RuptureView,
        time: Float,
        exposure: Float,
        distortion: Float = 0f,
        screenCopy: Int = 0,
        viewportPx: IntArray? = null
    ) {
        var count = 0
        for (s in effect.sprites) if (s.kind.material == material) count++
        if (count == 0) return

        glBindVertexArray(vao)
        glBindBuffer(GL_ARRAY_BUFFER, vbo)
        ensureCapacity(count)
        buffer.clear()
        var written = 0
        for (s in effect.sprites) {
            if (s.kind.material != material) continue
            if (writeSprite(s, material, effect, view, distortion)) written++
        }
        if (written == 0) return
        buffer.flip()
        glBufferSubData(GL_ARRAY_BUFFER, 0, buffer)

        begin(material, effect.master, time, exposure)
        if (material.mode == MODE_DISTORTION && viewportPx != null) {
            glActiveTexture(GL_TEXTURE0 + 4)
            glBindTexture(GL_TEXTURE_2D, screenCopy)
            glUniform4f(
                program.location("viewportPx"),
                viewportPx[0].toFloat(), viewportPx[1].toFloat(), viewportPx[2].toFloat(), viewportPx[3].toFloat()
            )
            glActiveTexture(GL_TEXTURE0)
        }
        glDrawArrays(GL_TRIANGLES, 0, written * VERTICES_PER_SPRITE)
    }

    /** Draws one lens flare at the rupture center. */
    fun drawFlare(flare: FlareDraw, effect: RuptureEffect, view: RuptureView, time: Float, exposure: Float) {
        val half = view.screenLength(flare.radius)
        if (half < 0.5f || flare.brightness <= 0f) return
        val centerX = view.screenX(effect.center.x)
        val centerY = view.screenY(effect.center.y)
        if (view.isOffscreen(centerX, centerY, half)) return

        glBindVertexArray(vao)
        glBindBuffer(GL_ARRAY_BUFFER, vbo)
        ensureCapacity(1)
        buffer.clear()
        for (corner in 0 until VERTICES_PER_SPRITE) {
            val lx = CORNER_X[corner]
            val ly = CORNER_Y[corner]
            putVertex(
                view.ndcX(centerX + lx * half), view.ndcY(centerY + ly * half),
                lx * 0.5f + 0.5f, 0.5f - ly * 0.5f, lx, ly,
                flare.color[0] * flare.brightness, flare.color[1] * flare.brightness, flare.color[2] * flare.brightness, 1f,
                0f, 0f, 1f, 0f
            )
        }
        buffer.flip()
        glBufferSubData(GL_ARRAY_BUFFER, 0, buffer)
        begin(flare.material, effect.master, time, exposure)
        glDrawArrays(GL_TRIANGLES, 0, VERTICES_PER_SPRITE)
    }

    private fun begin(material: SpriteMaterial, master: Float, time: Float, exposure: Float) {
        glUseProgram(program.id)
        glEnable(GL_BLEND)
        when (material.blend) {
            SpriteMaterial.Blend.ADDITIVE -> glBlendFunc(GL_ONE, GL_ONE)
            SpriteMaterial.Blend.ALPHA -> glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        }
        glUniform1i(program.location("mode"), material.mode)
        glUniform1f(program.location("exposure"), exposure)
        glUniform1f(program.location("master"), master)
        glUniform1f(program.location("warp"), material.warp)
        uploadXform("xformA", material.xformA, time)
        uploadXform("xformB", material.xformB, time)

        bindTexture(0, material.baseMap)
        bindTexture(1, material.noiseB)
        bindTexture(2, material.palette)
        bindTexture(3, material.alphaMap)
        glActiveTexture(GL_TEXTURE0)
    }

    /** Appends one sprite's two triangles; false if it is too small or off screen. */
    private fun writeSprite(
        s: RuptureSprite,
        material: SpriteMaterial,
        effect: RuptureEffect,
        view: RuptureView,
        distortion: Float
    ): Boolean {
        val halfLength = view.screenLength(s.halfLength)
        val halfWidth = view.screenLength(s.halfWidth)
        if (halfLength < 0.25f && halfWidth < 0.25f) return false
        if (s.alpha <= 0f) return false

        val cosA = cos(s.angle)
        val sinA = sin(s.angle)
        val worldX = effect.center.x + s.x + cosA * s.offsetAlong
        val worldY = effect.center.y + s.y + sinA * s.offsetAlong
        val centerX = view.screenX(worldX)
        val centerY = view.screenY(worldY)
        if (view.isOffscreen(centerX, centerY, max(halfLength, halfWidth))) return false

        val cells = material.atlasCells
        val cellU = (s.frame % cells).toFloat() / cells
        val cellV = (s.frame / cells % cells).toFloat() / cells

        val p0: Float
        val p1: Float
        val p2: Float
        val p3: Float
        if (material.mode == MODE_DISTORTION) {
            p0 = distortion * DISTORTION_PUSH * halfWidth / view.screenWidth
            p1 = 0f
            p2 = 1f
            p3 = 0f
        } else {
            p0 = s.paletteV
            p1 = s.blackPoint
            p2 = s.uvScale
            p3 = s.noiseOffset
        }

        for (corner in 0 until VERTICES_PER_SPRITE) {
            val lx = CORNER_X[corner]
            val ly = CORNER_Y[corner]
            // lx runs along the sprite's direction, ly across it
            val x = centerX + cosA * lx * halfLength - sinA * ly * halfWidth
            val y = centerY + sinA * lx * halfLength + cosA * ly * halfWidth
            // Texture "up" (v = 0, the bitmap's top row) points along the direction
            val u = cellU + (ly * 0.5f + 0.5f) / cells
            val v = cellV + (0.5f - lx * 0.5f) / cells
            putVertex(
                view.ndcX(x), view.ndcY(y), u, v, lx, ly,
                s.color[0], s.color[1], s.color[2], s.alpha,
                p0, p1, p2, p3
            )
        }
        return true
    }

    private fun putVertex(
        x: Float, y: Float, u: Float, v: Float, localX: Float, localY: Float,
        r: Float, g: Float, b: Float, a: Float,
        p0: Float, p1: Float, p2: Float, p3: Float
    ) {
        buffer.put(x).put(y).put(u).put(v).put(localX).put(localY)
        buffer.put(r).put(g).put(b).put(a)
        buffer.put(p0).put(p1).put(p2).put(p3)
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

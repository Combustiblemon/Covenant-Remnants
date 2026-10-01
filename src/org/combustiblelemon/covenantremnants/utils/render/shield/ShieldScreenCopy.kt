package org.combustiblelemon.covenantremnants.utils.render.shield

import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11.GL_CLAMP
import org.lwjgl.opengl.GL11.GL_LINEAR
import org.lwjgl.opengl.GL11.GL_RGBA
import org.lwjgl.opengl.GL11.GL_TEXTURE_2D
import org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER
import org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER
import org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S
import org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T
import org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE
import org.lwjgl.opengl.GL11.GL_VIEWPORT
import org.lwjgl.opengl.GL11.glBindTexture
import org.lwjgl.opengl.GL11.glCopyTexSubImage2D
import org.lwjgl.opengl.GL11.glDeleteTextures
import org.lwjgl.opengl.GL11.glGenTextures
import org.lwjgl.opengl.GL11.glGetInteger
import org.lwjgl.opengl.GL11.glTexImage2D
import org.lwjgl.opengl.GL11.glTexParameteri
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min

/**
 * One viewport-sized texture shared by every shield, into which each shield copies the framebuffer
 * under its own bounding box right before it draws (so the copy already contains earlier shields).
 * Same mechanism as the rupture's `RuptureRenderer.copyScreen`, but per region. GL thread only.
 */
internal object ShieldScreenCopy {
    private var texture = 0
    private var width = 0
    private var height = 0
    private val viewportBuffer = BufferUtils.createIntBuffer(16)

    /** GL viewport x, y, width, height after the last [copyRegion]. */
    val viewport = FloatArray(4)

    /**
     * Binds the copy texture to the active unit and copies the framebuffer region [x], [y], [w], [h]
     * (screen px, origin bottom-left, viewport-relative) into the matching place of the texture.
     */
    fun copyRegion(x: Float, y: Float, w: Float, h: Float) {
        glGetInteger(GL_VIEWPORT, viewportBuffer)
        for (i in 0 until 4) viewport[i] = viewportBuffer.get(i).toFloat()
        val vpWidth = viewportBuffer.get(2)
        val vpHeight = viewportBuffer.get(3)

        if (texture == 0) texture = glGenTextures()
        glBindTexture(GL_TEXTURE_2D, texture)
        if (vpWidth != width || vpHeight != height) {
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, vpWidth, vpHeight, 0, GL_RGBA, GL_UNSIGNED_BYTE, null as ByteBuffer?)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP)
            width = vpWidth
            height = vpHeight
        }

        val x0 = max(0, x.toInt())
        val y0 = max(0, y.toInt())
        val x1 = min(vpWidth, (x + w).toInt() + 1)
        val y1 = min(vpHeight, (y + h).toInt() + 1)
        if (x1 <= x0 || y1 <= y0) return
        glCopyTexSubImage2D(
            GL_TEXTURE_2D, 0, x0, y0,
            viewportBuffer.get(0) + x0, viewportBuffer.get(1) + y0, x1 - x0, y1 - y0
        )
    }

    fun dispose() {
        if (texture != 0) glDeleteTextures(texture)
        texture = 0
        width = 0
        height = 0
    }
}

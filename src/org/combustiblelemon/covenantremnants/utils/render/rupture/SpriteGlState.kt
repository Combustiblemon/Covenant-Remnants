package org.combustiblelemon.covenantremnants.utils.render.rupture

import org.lwjgl.opengl.GL11.GL_ALL_ATTRIB_BITS
import org.lwjgl.opengl.GL11.GL_BLEND
import org.lwjgl.opengl.GL11.GL_CULL_FACE
import org.lwjgl.opengl.GL11.GL_DEPTH_TEST
import org.lwjgl.opengl.GL11.GL_TEXTURE_2D
import org.lwjgl.opengl.GL11.GL_TEXTURE_BINDING_2D
import org.lwjgl.opengl.GL11.glBindTexture
import org.lwjgl.opengl.GL11.glDisable
import org.lwjgl.opengl.GL11.glEnable
import org.lwjgl.opengl.GL11.glGetInteger
import org.lwjgl.opengl.GL11.glPopAttrib
import org.lwjgl.opengl.GL11.glPushAttrib
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
 * The GL state wrap every sprite-overlay renderer needs around the engine's fixed-function
 * rendering (torpedoes, Energy Projector, Plasma Beam Emitter): saves the attrib stack, the
 * current program / VAO / VBO and the first [TEXTURE_UNITS] texture bindings, sets the sprite
 * program's expectations (no depth test, no culling, blending on), runs [block], restores
 * everything.
 */
internal object SpriteGlState {
    const val TEXTURE_UNITS = 4

    inline fun draw(block: () -> Unit) {
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
            glDisable(GL_DEPTH_TEST)
            glDisable(GL_CULL_FACE)
            glEnable(GL_BLEND)
            block()
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
}

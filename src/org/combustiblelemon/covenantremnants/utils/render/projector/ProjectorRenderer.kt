package org.combustiblelemon.covenantremnants.utils.render.projector

import com.fs.starfarer.api.combat.ViewportAPI
import org.combustiblelemon.covenantremnants.utils.render.rupture.RupturePrograms
import org.combustiblelemon.covenantremnants.utils.render.rupture.RuptureSprite
import org.combustiblelemon.covenantremnants.utils.render.rupture.RuptureView
import org.combustiblelemon.covenantremnants.utils.render.rupture.SpriteMaterial
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
 * Draws every live [ProjectorEffect] with the shared Reach sprite program ([RupturePrograms.sprites]),
 * wrapping the GL state like the torpedo renderer. Order: the cloud ring and blue plasma (the
 * glassed ground), the plasma tongues, the bubble and half-dome glow, the flicker glow, the
 * charge rings and flashes, then the flares.
 */
internal class ProjectorRenderer {
    private companion object {
        const val TEXTURE_UNITS = 4
        const val EXPOSURE = 1f

        val ORDER = listOf(
            SpriteMaterial.PROJECTOR_CLOUD,
            SpriteMaterial.PROJECTOR_BLUE,
            SpriteMaterial.PROJECTOR_BURST,
            SpriteMaterial.PROJECTOR_BUBBLE,
            SpriteMaterial.PROJECTOR_GLOW_HALF,
            SpriteMaterial.PROJECTOR_EMP,
            SpriteMaterial.PROJECTOR_FLASH,
            SpriteMaterial.PROJECTOR_FLARE
        )
    }

    private val all = ArrayList<RuptureSprite>()

    fun render(viewport: ViewportAPI, effects: Collection<ProjectorEffect>, time: Float) {
        all.clear()
        for (effect in effects) all.addAll(effect.sprites)
        if (all.isEmpty()) return
        val view = RuptureView(viewport)

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
            val sprites = RupturePrograms.sprites()
            glDisable(GL_DEPTH_TEST)
            glDisable(GL_CULL_FACE)
            glEnable(GL_BLEND)
            for (material in ORDER) {
                sprites.drawSprites(material, all, 0f, 0f, 1f, view, time, EXPOSURE)
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
}

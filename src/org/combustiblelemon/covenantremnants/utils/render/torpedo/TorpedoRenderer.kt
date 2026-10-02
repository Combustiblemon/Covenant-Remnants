package org.combustiblelemon.covenantremnants.utils.render.torpedo

import com.fs.starfarer.api.combat.ViewportAPI
import org.combustiblelemon.covenantremnants.utils.render.rupture.RIBBON_STRIDE
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
 * Draws every live torpedo ([TorpedoFlight]) and burst ([TorpedoBurst]) with the shared Reach
 * sprite program ([RupturePrograms.sprites]). Order: trails, impact smoke and fireballs
 * (alpha-blended), impact plasma, torpedo bodies and flares, muzzle flashes, sparks, impact flares.
 */
internal class TorpedoRenderer {
    private companion object {
        const val TEXTURE_UNITS = 4

        // Tone-mapping exposure for Reach's HDR colours (the rupture's default).
        const val EXPOSURE = 1f

        val BURST_ORDER = listOf(
            SpriteMaterial.TORPEDO_SMOKE,
            SpriteMaterial.TORPEDO_FIREBALL,
            SpriteMaterial.TORPEDO_PLASMA
        )
        val FLASH_ORDER = listOf(
            SpriteMaterial.TORPEDO_FLASH,
            SpriteMaterial.TORPEDO_MUZZLE_ROUND,
            SpriteMaterial.TORPEDO_SPARK,
            SpriteMaterial.TORPEDO_FLARE_SIMPLE,
            SpriteMaterial.TORPEDO_FLARE_ANAMORPHIC
        )
    }

    private val burstSprites = ArrayList<RuptureSprite>()
    private val bodySprites = ArrayList<RuptureSprite>()
    private var ribbon = FloatArray(0)

    fun render(viewport: ViewportAPI, flights: List<TorpedoFlight>, bursts: List<TorpedoBurst>, time: Float) {
        if (flights.isEmpty() && bursts.isEmpty()) return
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

            for (flight in flights) {
                val needed = flight.maxTrailPoints * RIBBON_STRIDE
                if (ribbon.size < needed) ribbon = FloatArray(needed)
                val count = flight.writeTrail(ribbon, time)
                sprites.drawRibbon(SpriteMaterial.TORPEDO_TRAIL, ribbon, count, 1f, view, time, EXPOSURE)
            }

            burstSprites.clear()
            for (burst in bursts) burstSprites.addAll(burst.sprites)
            bodySprites.clear()
            for (flight in flights) {
                if (!flight.flying) continue
                bodySprites.addAll(flight.body)
                bodySprites.add(flight.star)
            }

            for (material in BURST_ORDER) {
                sprites.drawSprites(material, burstSprites, 0f, 0f, 1f, view, time, EXPOSURE)
            }
            sprites.drawSprites(SpriteMaterial.TORPEDO_BODY, bodySprites, 0f, 0f, 1f, view, time, EXPOSURE)
            sprites.drawSprites(SpriteMaterial.TORPEDO_STAR, bodySprites, 0f, 0f, 1f, view, time, EXPOSURE)
            for (material in FLASH_ORDER) {
                sprites.drawSprites(material, burstSprites, 0f, 0f, 1f, view, time, EXPOSURE)
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

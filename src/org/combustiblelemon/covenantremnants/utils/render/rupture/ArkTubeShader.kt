package org.combustiblelemon.covenantremnants.utils.render.rupture

import org.combustiblelemon.covenantremnants.utils.render.PlainProgram
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11.GL_BLEND
import org.lwjgl.opengl.GL11.GL_FLOAT
import org.lwjgl.opengl.GL11.GL_ONE
import org.lwjgl.opengl.GL11.GL_TRIANGLES
import org.lwjgl.opengl.GL11.glBlendFunc
import org.lwjgl.opengl.GL11.glDrawArrays
import org.lwjgl.opengl.GL11.glEnable
import org.lwjgl.opengl.GL13.GL_TEXTURE0
import org.lwjgl.opengl.GL13.glActiveTexture
import org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER
import org.lwjgl.opengl.GL15.GL_STATIC_DRAW
import org.lwjgl.opengl.GL15.glBindBuffer
import org.lwjgl.opengl.GL15.glBufferData
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
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Draws the Halo 3 Ark slipspace tube ([ArkTubeMesh], arkTube.vert/.frag): the funnel mesh in an
 * orthographic top-down view with a fixed tilt, spun about its axis, placed with its mouth at the
 * effect's centre and extending behind the mouth along −facing (or ahead when flipped). Streaks,
 * colour grading, depth shading and wall lightning per [TubeDraw]. One static VBO uploaded once;
 * construct on the GL thread (shared through [RupturePrograms]). Uses texture units 0–3.
 */
internal class ArkTubeShader {
    private companion object {
        const val VERTEX_FILE = "data/shaders/rupture/arkTube.vert"
        const val FRAGMENT_FILE = "data/shaders/rupture/arkTube.frag"
        const val STRIDE = ArkTubeMesh.FLOATS_PER_VERTEX * 4
    }

    private val program = PlainProgram(VERTEX_FILE, FRAGMENT_FILE)
    private val vao: Int = glGenVertexArrays()
    private val vbo: Int = glGenBuffers()

    init {
        glBindVertexArray(vao)
        glBindBuffer(GL_ARRAY_BUFFER, vbo)
        val buffer = BufferUtils.createFloatBuffer(ArkTubeMesh.DATA.size)
        buffer.put(ArkTubeMesh.DATA).flip()
        glBufferData(GL_ARRAY_BUFFER, buffer, GL_STATIC_DRAW)
        glVertexAttribPointer(0, 3, GL_FLOAT, false, STRIDE, 0L)
        glVertexAttribPointer(1, 2, GL_FLOAT, false, STRIDE, 3L * 4)
        glEnableVertexAttribArray(0)
        glEnableVertexAttribArray(1)
        glBindVertexArray(0)
        glBindBuffer(GL_ARRAY_BUFFER, 0)

        glUseProgram(program.id)
        glUniform1i(program.location("tubeMap"), 0)
        glUniform1i(program.location("noiseA"), 1)
        glUniform1i(program.location("noiseB"), 2)
        glUniform1i(program.location("palette"), 3)
        glUseProgram(0)
    }

    fun draw(tube: TubeDraw, effect: RuptureEffect, view: RuptureView, exposure: Float) {
        if (!tube.active) return
        val alpha = tube.alpha * effect.master
        if (alpha <= 0f || tube.along <= 0f || tube.across <= 0f) return

        val centerX = view.screenX(effect.center.x)
        val centerY = view.screenY(effect.center.y)
        val reach = max(tube.along * ArkTubeMesh.LENGTH, tube.across * ArkTubeMesh.MOUTH_RADIUS) * 1.5f
        if (view.isOffscreen(centerX, centerY, view.screenLength(reach))) return

        // The tube extends behind the mouth: along = −facing (or +facing when flipped).
        val facing = Math.toRadians(effect.facing.toDouble())
        var dirX = cos(facing).toFloat()
        var dirY = sin(facing).toFloat()
        if (!tube.flip) {
            dirX = -dirX
            dirY = -dirY
        }
        val normalX = -dirY
        val normalY = dirX

        // NDC per world unit on each screen axis.
        val kx = view.screenLength(1f) * 2f / view.screenWidth
        val ky = view.screenLength(1f) * 2f / view.screenHeight

        glUseProgram(program.id)
        glEnable(GL_BLEND)
        glBlendFunc(GL_ONE, GL_ONE)
        glUniform2f(program.location("originNdc"), view.ndcX(centerX), view.ndcY(centerY))
        glUniform2f(program.location("alongNdc"), dirX * kx, dirY * ky)
        glUniform2f(program.location("acrossNdc"), normalX * kx, normalY * ky)
        glUniform2f(program.location("scale"), tube.along, tube.across)
        glUniform2f(program.location("spin"), cos(tube.spin), sin(tube.spin))
        glUniform2f(program.location("tilt"), cos(tube.tilt), sin(tube.tilt))
        glUniform1f(program.location("meshLength"), ArkTubeMesh.LENGTH)
        glUniform1f(program.location("mouthRadius"), ArkTubeMesh.MOUTH_RADIUS)

        glUniform3f(program.location("color"), tube.color[0], tube.color[1], tube.color[2])
        glUniform3f(program.location("hotColor"), tube.hotColor[0], tube.hotColor[1], tube.hotColor[2])
        glUniform3f(program.location("deepColor"), tube.deepColor[0], tube.deepColor[1], tube.deepColor[2])
        glUniform1f(program.location("alpha"), alpha)
        glUniform1f(program.location("blackPoint"), tube.blackPoint)
        glUniform1f(program.location("exposure"), exposure)

        glUniform1f(program.location("bitmapMix"), tube.bitmapMix)
        glUniform4f(program.location("streak"), tube.streak[0], tube.streak[1], tube.streak[2], tube.streak[3])
        glUniform1f(program.location("streakGain"), tube.streakGain)
        glUniform1f(program.location("flow"), tube.flow)
        glUniform2f(program.location("hotSpan"), tube.hotSpan[0], tube.hotSpan[1])
        glUniform1f(program.location("depthFade"), tube.depthFade)
        glUniform1f(program.location("backDim"), tube.backDim)
        glUniform1f(program.location("backMouth"), tube.backMouth)

        glUniform4f(program.location("arc"), tube.arcIntensity, tube.arcPhase, tube.arcScale[0], tube.arcScale[1])
        glUniform1f(program.location("arcGate"), tube.arcGate)
        glUniform1f(program.location("arcReach"), tube.arcReach)
        glUniform1f(program.location("paletteV"), tube.paletteV)

        glActiveTexture(GL_TEXTURE0 + 3)
        RuptureTexture.SPHERE_WEB_PALETTE.bind()
        glActiveTexture(GL_TEXTURE0 + 2)
        RuptureTexture.NOISE_512_B.bind()
        glActiveTexture(GL_TEXTURE0 + 1)
        RuptureTexture.NOISE_512_A.bind()
        glActiveTexture(GL_TEXTURE0)
        RuptureTexture.ARK_TUBE.bind()

        glBindVertexArray(vao)
        glDrawArrays(GL_TRIANGLES, 0, ArkTubeMesh.VERTEX_COUNT)
        glBindVertexArray(0)
    }

    fun dispose() {
        glDeleteBuffers(vbo)
        glDeleteVertexArrays(vao)
        program.dispose()
    }
}

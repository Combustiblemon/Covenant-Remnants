package org.combustiblelemon.covenantremnants.utils.render.shield

import com.fs.starfarer.api.combat.ViewportAPI
import org.lwjgl.opengl.GL20.glGetUniformLocation
import org.lwjgl.opengl.GL20.glUniform1i
import org.lwjgl.opengl.GL20.glUniform2f

/**
 * Debug visualization: bounding-box outline (red), polygon points (green),
 * polygon edges (magenta). The bounding box is pushed by the orchestrator.
 */
class DebugLayer : ShieldLayer {
    override val layerName = "debug"
    override val moduleFile = "data/shaders/shield/debug.glsl"
    override val stubSource = """
        bool debug_check(vec2 pixelPos, out vec4 dbgColor) { dbgColor = vec4(0.0); return false; }
    """.trimIndent()

    var enabled = false

    private var boundingBoxMinX = 0f
    private var boundingBoxMinY = 0f
    private var boundingBoxMaxX = 0f
    private var boundingBoxMaxY = 0f

    fun setBoundingBox(minX: Float, minY: Float, maxX: Float, maxY: Float) {
        boundingBoxMinX = minX
        boundingBoxMinY = minY
        boundingBoxMaxX = maxX
        boundingBoxMaxY = maxY
    }

    override fun uploadUniforms(program: Int, viewport: ViewportAPI) {
        glUniform1i(glGetUniformLocation(program, "debugMode"), if (enabled) 1 else 0)
        glUniform2f(glGetUniformLocation(program, "boundingBoxMin"), boundingBoxMinX, boundingBoxMinY)
        glUniform2f(glGetUniformLocation(program, "boundingBoxMax"), boundingBoxMaxX, boundingBoxMaxY)
    }
}

package org.combustiblelemon.covenantremnants.utils.render.shield

import com.fs.starfarer.api.combat.ViewportAPI
import org.combustiblelemon.lemonlib.render.composition.ShaderLayer
import org.lwjgl.opengl.GL20.glGetUniformLocation
import org.lwjgl.opengl.GL20.glUniform1f
import org.lwjgl.opengl.GL20.glUniform1i

/**
 * Animated honeycomb hex grid over the shield surface.
 */
class HoneycombLayer : ShaderLayer {
    override val layerName = "honeycomb"
    override val moduleFile = "data/shaders/shield/honeycomb.glsl"
    override val stubSource = """
        vec4 honeycomb_apply(vec4 color, vec2 pixelPos) { return color; }
    """.trimIndent()

    private var enabled = true
    private val honeycombSize = 10.0f
    private val edgeWidth = 3.0f

    fun setEnabled(value: Boolean) {
        enabled = value
    }

    override fun uploadUniforms(program: Int, viewport: ViewportAPI) {
        glUniform1f(glGetUniformLocation(program, "honeycombSize"), honeycombSize)
        glUniform1f(glGetUniformLocation(program, "edgeWidth"), edgeWidth)
        glUniform1i(glGetUniformLocation(program, "honeycombEnabled"), if (enabled) 1 else 0)
    }
}

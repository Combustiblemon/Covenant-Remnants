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
    companion object {
        /** Hex grid size, world units. Shared with [FortressLayer] so its lattice overlaps these hexes. */
        const val HEX_SIZE = 10.0f
    }

    override val layerName = "honeycomb"
    override val moduleFile = "data/shaders/shield/honeycomb.glsl"
    override val stubSource = """
        vec4 honeycomb_apply(vec4 color, vec2 pixelPos) { return color; }
    """.trimIndent()

    private var enabled = true
    private val honeycombSize = HEX_SIZE
    private val edgeWidth = 3.0f

    /** Breathing animation amount: 1 = full, 0 = held at full brightness. */
    var breathing: Float = 1f

    fun setEnabled(value: Boolean) {
        enabled = value
    }

    override fun uploadUniforms(program: Int, viewport: ViewportAPI) {
        glUniform1f(glGetUniformLocation(program, "honeycombSize"), honeycombSize)
        glUniform1f(glGetUniformLocation(program, "edgeWidth"), edgeWidth)
        glUniform1i(glGetUniformLocation(program, "honeycombEnabled"), if (enabled) 1 else 0)
        glUniform1f(glGetUniformLocation(program, "honeycombBreathing"), breathing)
    }
}

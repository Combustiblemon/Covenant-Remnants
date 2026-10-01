package org.combustiblelemon.covenantremnants.utils.render.shield

import com.fs.starfarer.api.combat.ViewportAPI
import org.combustiblelemon.lemonlib.render.composition.ShaderLayer
import org.lwjgl.opengl.GL20.glGetUniformLocation
import org.lwjgl.opengl.GL20.glUniform1f
import org.lwjgl.opengl.GL20.glUniform4f

/**
 * Fortress Shield overlay: locked hex lattice + pulsing edge glow, scaled by the Fortress
 * Shield system's effect level ([level]; 0 = off, the module early-outs). The lattice uses the
 * honeycomb's grid and size, so it sits exactly on the honeycomb hexes.
 */
class FortressLayer : ShaderLayer {
    override val layerName = "fortress"
    override val moduleFile = "data/shaders/shield/fortress.glsl"
    override val stubSource = """
        vec4 fortress_apply(vec4 color, vec2 pixelPos) { return color; }
    """.trimIndent()

    var level: Float = 0f

    // Lattice: opaque white, like the honeycomb at the peak of its breathing. Edge glow: magenta.
    private val hexColorRGBA = floatArrayOf(1f, 1f, 1f, 1f)
    private val glowColorRGBA = floatArrayOf(1f, 150f / 255f, 1f, 0.75f)
    private val hexSize = HoneycombLayer.HEX_SIZE
    private val hexEdge = 1.5f
    private val glowWidth = 25f
    private val pulseSpeed = 3f

    override fun uploadUniforms(program: Int, viewport: ViewportAPI) {
        glUniform1f(glGetUniformLocation(program, "fortressLevel"), level)
        glUniform4f(
            glGetUniformLocation(program, "fortressHexColor"),
            hexColorRGBA[0], hexColorRGBA[1], hexColorRGBA[2], hexColorRGBA[3]
        )
        glUniform4f(
            glGetUniformLocation(program, "fortressGlowColor"),
            glowColorRGBA[0], glowColorRGBA[1], glowColorRGBA[2], glowColorRGBA[3]
        )
        glUniform1f(glGetUniformLocation(program, "fortressHexSize"), hexSize)
        glUniform1f(glGetUniformLocation(program, "fortressHexEdge"), hexEdge)
        glUniform1f(glGetUniformLocation(program, "fortressGlowWidth"), glowWidth)
        glUniform1f(glGetUniformLocation(program, "fortressPulseSpeed"), pulseSpeed)
    }
}

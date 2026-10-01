package org.combustiblelemon.covenantremnants.utils.render.shield

import com.fs.starfarer.api.combat.ViewportAPI
import org.combustiblelemon.lemonlib.render.composition.ShaderLayer
import org.lwjgl.opengl.GL20.glGetUniformLocation
import org.lwjgl.opengl.GL20.glUniform4f

/**
 * Solid shield fill colour. (Damage feedback lives in [PlasmaLayer].)
 */
class FillLayer : ShaderLayer {
    override val layerName = "fill"
    override val moduleFile = "data/shaders/shield/fill.glsl"
    override val stubSource = """
        vec4 fill_color() { return vec4(0.0); }
    """.trimIndent()

    private var colorRGBA: FloatArray = floatArrayOf(0.2f, 0.4f, 1.0f, 1f)

    fun setColor(r: Int, g: Int, b: Int, a: Int) {
        colorRGBA = floatArrayOf(
            r / 255f,
            g / 255f,
            b / 255f,
            a / 100f
        )
    }

    override fun uploadUniforms(program: Int, viewport: ViewportAPI) {
        glUniform4f(
            glGetUniformLocation(program, "shieldColor"),
            colorRGBA[0], colorRGBA[1], colorRGBA[2], colorRGBA[3]
        )
    }
}

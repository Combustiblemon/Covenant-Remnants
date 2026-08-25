package org.combustiblelemon.covenantremnants.utils.render.shield

import com.fs.starfarer.api.combat.ViewportAPI
import org.lwjgl.opengl.GL20.glGetUniformLocation
import org.lwjgl.opengl.GL20.glUniform1f
import org.lwjgl.opengl.GL20.glUniform4f

/**
 * Border drawn along the shield polygon edges.
 * The sweep layer also reads borderColor for its edge glow.
 */
class BorderLayer : ShieldLayer {
    override val layerName = "border"
    override val moduleFile = "data/shaders/shield/border.glsl"

    // The stub still declares borderColor because sweep.glsl reads it.
    // Uploads are skipped while disabled, so it stays vec4(0) — sweep edge invisible too.
    override val stubSource = """
        uniform vec4 borderColor;
        vec4 border_apply(vec4 color, vec2 pixelPos) { return color; }
    """.trimIndent()

    private var borderColorRGBA: FloatArray = floatArrayOf(1f, 1f, 1f, 1f)

    /** Exposed for the orchestrator's bounding-box margin calculation. */
    var borderWidth: Float = 10f
        private set

    fun setBorder(r: Int, g: Int, b: Int, a: Int, width: Float) {
        borderColorRGBA = floatArrayOf(
            r / 255f,
            g / 255f,
            b / 255f,
            a / 100f
        )
        borderWidth = width
    }

    override fun uploadUniforms(program: Int, viewport: ViewportAPI) {
        glUniform4f(
            glGetUniformLocation(program, "borderColor"),
            borderColorRGBA[0], borderColorRGBA[1], borderColorRGBA[2], borderColorRGBA[3]
        )
        glUniform1f(glGetUniformLocation(program, "borderWidth"), borderWidth)
    }
}

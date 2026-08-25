package org.combustiblelemon.covenantremnants.utils.render.shield

import com.fs.starfarer.api.combat.ViewportAPI
import org.combustiblelemon.covenantremnants.utils.render.composition.ShaderLayer
import org.lwjgl.opengl.GL20.glGetUniformLocation
import org.lwjgl.opengl.GL20.glUniform1f

/**
 * Activation sweep wipe. Mask-modifying: pixels before the sweep position are
 * culled by an early return hardcoded in base.frag's shieldFrag(); this layer
 * only owns the sweep state and uniforms.
 *
 * sweepPosition is computed on the CPU by the orchestrator during updatePolygon
 * (it depends on the smoothed polygon bounds and phase cloak effect level).
 */
class SweepLayer : ShaderLayer {
    override val layerName = "sweep"
    override val moduleFile = "data/shaders/shield/sweep.glsl"

    // Stub never culls: with sweep disabled the whole shield always renders.
    override val stubSource = """
        bool sweep_isCulled(float pixelShipRelX) { return false; }
        float sweep_shipRelX(vec2 pixelPos) { return 0.0; }
        vec4 sweep_edgeColor(float pixelShipRelX) { return vec4(0.0); }
        vec4 sweep_applyEdge(vec4 color, vec4 sweepEdge) { return color; }
    """.trimIndent()

    var sweepPosition: Float = 0f

    private val sweepEdgeWidth: Float = 10.0f

    override fun uploadUniforms(program: Int, viewport: ViewportAPI) {
        glUniform1f(glGetUniformLocation(program, "sweepPosition"), sweepPosition)
        glUniform1f(glGetUniformLocation(program, "sweepEdgeWidth"), sweepEdgeWidth)
    }
}

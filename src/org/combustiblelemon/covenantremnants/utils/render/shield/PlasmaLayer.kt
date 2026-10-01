package org.combustiblelemon.covenantremnants.utils.render.shield

import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ViewportAPI
import org.combustiblelemon.lemonlib.render.composition.ShaderLayer
import org.lwjgl.opengl.GL13.GL_TEXTURE0
import org.lwjgl.opengl.GL13.GL_TEXTURE1
import org.lwjgl.opengl.GL13.glActiveTexture
import org.lwjgl.opengl.GL20.glGetUniformLocation
import org.lwjgl.opengl.GL20.glUniform1f
import org.lwjgl.opengl.GL20.glUniform1i
import org.lwjgl.opengl.GL20.glUniform2f
import org.lwjgl.opengl.GL20.glUniform3f
import org.lwjgl.opengl.GL20.glUniform4f
import kotlin.math.cos
import kotlin.math.sin

/**
 * Halo: Reach Elite shield look (`plasma.glsl`): rim glow + scrolling plasma filaments that flare on
 * hits and with low HP, the localized hit spots, the collapse flare and the recharge wave. State
 * lives in [effect]; this class only converts it to uniforms and binds the two noise maps.
 * [ship] is null only for the template instance the shared [ShieldProgram] is assembled from.
 */
class PlasmaLayer(private val ship: ShipAPI?) : ShaderLayer {
    override val layerName = "plasma"
    override val moduleFile = "data/shaders/shield/plasma.glsl"
    override val stubSource = """
        vec4 plasma_apply(vec4 color, vec2 pixelPos) { return color; }
    """.trimIndent()

    internal val effect = ShieldImpactEffect()

    // Screen-space body metrics, set by the owner from the polygon every frame.
    private var bodyDepthPx = 1f
    private var bodyRadiusPx = 1f

    fun setBodyMetrics(depthPx: Float, radiusPx: Float) {
        bodyDepthPx = depthPx.coerceAtLeast(1f)
        bodyRadiusPx = radiusPx.coerceAtLeast(1f)
    }

    override fun advance(deltaTime: Float) {
        effect.advance(deltaTime)
    }

    override fun uploadUniforms(program: Int, viewport: ViewportAPI) {
        val ship = ship ?: return
        // Samplers every frame: the program is rebuilt by the ShaderLayers command and unset samplers
        // default to unit 0. The owner saves/restores units 0 and 1 and the active unit.
        glActiveTexture(GL_TEXTURE0)
        ShieldTexture.PLASMA_A.bind()
        glUniform1i(glGetUniformLocation(program, "plasmaNoiseA"), 0)
        glActiveTexture(GL_TEXTURE1)
        ShieldTexture.PLASMA_B.bind()
        glUniform1i(glGetUniformLocation(program, "plasmaNoiseB"), 1)
        glActiveTexture(GL_TEXTURE0)

        val e = effect
        glUniform1f(glGetUniformLocation(program, "plasmaActive"), if (e.active) 1f else 0f)
        glUniform1f(glGetUniformLocation(program, "plasmaBodyFade"), e.bodyFade)
        glUniform1f(glGetUniformLocation(program, "plasmaBodyDepth"), bodyDepthPx)
        glUniform4f(glGetUniformLocation(program, "plasmaEdgeBand"), e.edgeBand[0], e.edgeBand[1], e.edgeBand[2], e.edgeBand[3])
        glUniform4f(glGetUniformLocation(program, "plasmaPlasmaBand"), e.plasmaBand[0], e.plasmaBand[1], e.plasmaBand[2], e.plasmaBand[3])
        glUniform2f(glGetUniformLocation(program, "plasmaSharpness"), e.edgeSharpness, e.centerSharpness)
        // uv per screen px: screen px * viewMult = world units; one repeat = TILE_WORLD / tiling world units
        glUniform1f(glGetUniformLocation(program, "plasmaUvScale"), viewport.viewMult * e.tiling / ShieldImpactEffect.TILE_WORLD)
        glUniform4f(glGetUniformLocation(program, "plasmaOffsets"), e.offsetAx, e.offsetAy, e.offsetBx, e.offsetBy)
        glUniform3f(glGetUniformLocation(program, "plasmaEdgeGlow"), e.edgeGlow[0], e.edgeGlow[1], e.edgeGlow[2])
        glUniform3f(glGetUniformLocation(program, "plasmaCenterColor"), e.centerColor[0], e.centerColor[1], e.centerColor[2])
        glUniform3f(glGetUniformLocation(program, "plasmaEdgeColor"), e.edgeColor[0], e.edgeColor[1], e.edgeColor[2])
        glUniform3f(glGetUniformLocation(program, "plasmaFlash"), e.flash[0], e.flash[1], e.flash[2])

        // Hit spots: ship-local -> world -> screen.
        val rad = Math.toRadians(ship.facing.toDouble())
        val c = cos(rad).toFloat()
        val s = sin(rad).toFloat()
        val count = minOf(e.spots.size, ShieldImpactEffect.MAX_HITS)
        glUniform1i(glGetUniformLocation(program, "plasmaHitCount"), count)
        for (i in 0 until count) {
            val spot = e.spots[i]
            val worldX = ship.location.x + spot.localX * c - spot.localY * s
            val worldY = ship.location.y + spot.localX * s + spot.localY * c
            glUniform4f(
                glGetUniformLocation(program, "plasmaHits[$i]"),
                viewport.convertWorldXtoScreenX(worldX),
                viewport.convertWorldYtoScreenY(worldY),
                (spot.radiusFraction * bodyRadiusPx).coerceAtLeast(1f),
                spot.strength
            )
            glUniform3f(glGetUniformLocation(program, "plasmaHitColors[$i]"), spot.color[0], spot.color[1], spot.color[2])
        }
    }
}

package org.combustiblelemon.covenantremnants.utils.render.shield

import com.fs.starfarer.api.combat.ViewportAPI
import org.combustiblelemon.lemonlib.render.composition.ShaderLayer
import org.lwjgl.opengl.GL20.glGetUniformLocation
import org.lwjgl.opengl.GL20.glUniform1f
import org.lwjgl.opengl.GL20.glUniform1i
import org.lwjgl.opengl.GL20.glUniform2f
import org.lwjgl.opengl.GL20.glUniform4f

/**
 * Impact ripple rings expanding across the shield. Up to 10 concurrent ripples
 * (shader uniform array size).
 */
class RippleLayer : ShaderLayer {
    override val layerName = "ripple"
    override val moduleFile = "data/shaders/shield/ripple.glsl"
    override val stubSource = """
        vec4 ripple_apply(vec4 color, vec2 pixelPos) { return color; }
    """.trimIndent()

    private data class Ripple(
        var center: Pair<Float, Float>,
        var radius: Float,
        var width: Float,
        var color: FloatArray,
        var time: Float,
        var intensity: Float,
        var maxDistance: Float
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Ripple

            if (radius != other.radius) return false
            if (width != other.width) return false
            if (time != other.time) return false
            if (intensity != other.intensity) return false
            if (maxDistance != other.maxDistance) return false
            if (center != other.center) return false
            if (!color.contentEquals(other.color)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = radius.hashCode()
            result = 31 * result + width.hashCode()
            result = 31 * result + time.hashCode()
            result = 31 * result + intensity.hashCode()
            result = 31 * result + maxDistance.hashCode()
            result = 31 * result + center.hashCode()
            result = 31 * result + color.contentHashCode()
            return result
        }
    }

    private val activeRipples = mutableListOf<Ripple>()

    fun startRipple(x: Float, y: Float, width: Float, r: Int, g: Int, b: Int, a: Int, intensity: Float = 1f) {
        val ripple = Ripple(
            center = Pair(x, y),
            radius = 1f,
            width = width,
            color = floatArrayOf(r / 255f, g / 255f, b / 255f, a / 100f),
            time = 0f,
            intensity = intensity.coerceIn(0f, 1f),
            maxDistance = 1000f * intensity.coerceIn(0.2f, 1f)
        )
        activeRipples.add(ripple)
    }

    fun update(deltaTime: Float, speed: Float, viewportScale: Float) {
        activeRipples.removeAll { ripple ->
            ripple.time += deltaTime
            // Scale speed inversely with viewport scale to maintain consistent visual speed
            val visualSpeed = speed / viewportScale
            ripple.radius += visualSpeed * deltaTime
            ripple.radius > ripple.maxDistance
        }
    }

    override fun uploadUniforms(program: Int, viewport: ViewportAPI) {
        glUniform1i(glGetUniformLocation(program, "numRipples"), activeRipples.size)
        for (i in activeRipples.indices.take(10)) {
            val ripple = activeRipples[i]
            glUniform4f(
                glGetUniformLocation(program, "rippleColors[$i]"),
                ripple.color[0], ripple.color[1], ripple.color[2], ripple.color[3]
            )
            // Convert ripple center to screen coordinates
            val rippleScreenX = viewport.convertWorldXtoScreenX(ripple.center.first)
            val rippleScreenY = viewport.convertWorldYtoScreenY(ripple.center.second)
            glUniform2f(
                glGetUniformLocation(program, "rippleCenters[$i]"),
                rippleScreenX, rippleScreenY
            )
            // Convert ripple radius to screen coordinates
            val rippleScreenRadius = ripple.radius / viewport.viewMult
            glUniform1f(glGetUniformLocation(program, "rippleRadii[$i]"), rippleScreenRadius)
            glUniform1f(glGetUniformLocation(program, "rippleIntensities[$i]"), ripple.intensity)
            // Convert ripple width to screen coordinates
            val rippleScreenWidth = ripple.width / viewport.viewMult
            glUniform1f(glGetUniformLocation(program, "rippleWidths[$i]"), rippleScreenWidth)
        }
    }
}

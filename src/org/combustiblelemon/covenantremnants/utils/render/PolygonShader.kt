package org.combustiblelemon.covenantremnants.utils.render

import com.fs.starfarer.api.Global
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL15.*
import org.lwjgl.util.glu.GLU
import org.lwjgl.util.glu.GLUtessellator
import org.lwjgl.util.glu.GLUtessellatorCallbackAdapter
import org.lwjgl.util.vector.Vector2f
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class PolygonShader(
    private var shipDimensions: Array<Vector2f>,
    private var popEffectRadius: Float = 500f,  // Default pop effect radius
    private var polygonPoints: List<Float> = listOf(),
    private var colorRGBA: FloatArray = floatArrayOf(0.2f, 0.4f, 1.0f, 1f),
    private var borderColorRGBA: FloatArray = floatArrayOf(1f, 1f, 1f, 1f),
    private var borderWidth: Float = 10f,
    private var glowIntensity: Float = 0f,
    private var glowWidth: Float = 20f,
) {
    private var tess: GLUtessellator = GLU.gluNewTess()
    private var vbo: Int = 0
    private var scaleX: Float = 1.0f
    private var scaleY: Float = 1.0f
    private var rotation: Float = 0f
    private var centerX: Float = 0f
    private var centerY: Float = 0f
    private var chargeProgress: Float = 1f
    private var shipFacing: Float = 0f

    // Add hex animation properties
    private var hexAnimationTime = 0f
    private var hexSpacing = 10f

    // Hex grid parameters with default values
    private var hexSize = 20f
    private var hexRotation = 45f
    private var hexEdgeWidth = 2f
    private var hexSpacingMultiplier = 1.0f  // 1.0 means edges touch

    private data class Ripple(
        var center: Pair<Float, Float>,
        var radius: Float,
        var width: Float,
        var color: FloatArray,
        var time: Float,
        var intensity: Float,
        var maxDistance: Float
    )

    private val activeRipples = mutableListOf<Ripple>()

    private data class PopWave(
        var time: Float = 0f,
        var maxTime: Float = 0.8f,
        var color: FloatArray = floatArrayOf(0f, 0f, 0f, 0f),
        var darkerColor: FloatArray = floatArrayOf(0f, 0f, 0f, 0f),
        var thickness: Float = 20f,
        var startScale: Float = 0.1f,
        var endScale: Float = 3.0f,
        var intensity: Float = 1.0f,
        var radialLines: List<RadialLine> = listOf()
    )

    private var activePopWave: PopWave? = null

    private val noiseSprite = Global.getSettings().getSprite("covenant_remnants", "shield_noise")

    private data class RadialLine(
        val angle: Float,       // Angle in radians
        val length: Float,      // Length of the line (0-1, will be multiplied by radius)
        val startOffset: Float, // Offset from center (0-1, will be multiplied by radius)
        val thickness: Float,   // Thickness of the line
        val alpha: Float,       // Base alpha/opacity
        val speedMultiplier: Float  // How fast this line expands compared to others
    )

    init {
        init()
        initTessellator()
    }

    private fun init() {
        // Create VBO
        vbo = glGenBuffers()
        glBindBuffer(GL_ARRAY_BUFFER, vbo)
        glVertexPointer(2, GL_FLOAT, 0, 0)
        glEnableClientState(GL_VERTEX_ARRAY)
    }

    private fun initTessellator() {
        tess.gluTessCallback(GLU.GLU_TESS_BEGIN, object : GLUtessellatorCallbackAdapter() {
            override fun begin(type: Int) {
                glBegin(type)
            }
        })

        tess.gluTessCallback(GLU.GLU_TESS_VERTEX, object : GLUtessellatorCallbackAdapter() {
            override fun vertex(data: Any) {
                val vertex = data as DoubleArray
                glVertex2d(vertex[0], vertex[1])
            }
        })

        tess.gluTessCallback(GLU.GLU_TESS_END, object : GLUtessellatorCallbackAdapter() {
            override fun end() {
                glEnd()
            }
        })
    }

    fun updatePolygon(newPoints: List<Float>) {
        polygonPoints = newPoints
        val buffer = BufferUtils.createFloatBuffer(polygonPoints.size)
        buffer.put(polygonPoints.toFloatArray()).flip()

        glBindBuffer(GL_ARRAY_BUFFER, vbo)
        glBufferData(GL_ARRAY_BUFFER, buffer, GL_STATIC_DRAW)
    }

    fun updateColor(r: Int, g: Int, b: Int, a: Int) {
        colorRGBA = floatArrayOf(
            r / 255f,
            g / 255f,
            b / 255f,
            a / 100f
        )
    }

    fun updateScale(x: Float, y: Float) {
        scaleX = x
        scaleY = y
    }

    fun updateRotation(angleRadians: Float) {
        rotation = angleRadians
    }

    fun updateCenter(x: Float, y: Float) {
        centerX = x
        centerY = y
    }

    fun updateBorder(r: Int, g: Int, b: Int, a: Int, width: Float, blend: Float) {
        borderColorRGBA = floatArrayOf(
            r / 255f,
            g / 255f,
            b / 255f,
            a / 100f
        )
        borderWidth = width
    }

    fun updateGlow(intensity: Float, width: Float) {
        glowIntensity = intensity.coerceIn(0f, 1f)
        glowWidth = width
    }

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

    fun updateRipple(deltaTime: Float, speed: Float = 400f) {
        activeRipples.removeAll { ripple ->
            ripple.time += deltaTime
            ripple.radius += speed * deltaTime
            ripple.radius > ripple.maxDistance
        }
    }

    private fun transformPoints(points: List<Float>, outwardOffset: Float): List<Float> {
        val result = mutableListOf<Float>()
        val numPoints = points.size / 2

        for (i in 0 until numPoints) {
            val curr = i * 2
            val next = ((i + 1) % numPoints) * 2
            val prev = ((i - 1 + numPoints) % numPoints) * 2

            // Get current point
            val x = points[curr]
            val y = points[curr + 1]

            // Calculate vectors to previous and next points
            val prevX = points[prev]
            val prevY = points[prev + 1]
            val nextX = points[next]
            val nextY = points[next + 1]

            // Calculate normals for both edges
            val toPrevX = prevX - x
            val toPrevY = prevY - y
            val toNextX = nextX - x
            val toNextY = nextY - y

            // Calculate average normal
            val normalX = -(toPrevY + toNextY)
            val normalY = toPrevX + toNextX

            // Normalize the normal vector
            val length = kotlin.math.sqrt(normalX * normalX + normalY * normalY)

            // Base point position (before any offset)
            var finalX = x
            var finalY = y

            // Apply outward offset if needed and if we have a valid normal
            if (outwardOffset > 0 && length > 0) {
                finalX += (normalX / length) * outwardOffset
                finalY += (normalY / length) * outwardOffset
            }

            // Now apply regular transformations (translate to origin, scale, rotate, translate back)
            val localX = finalX - centerX
            val localY = finalY - centerY

            // Scale in local space
            val scaledX = localX * scaleY
            val scaledY = localY * scaleX

            // Rotate in local space
            val cos = kotlin.math.cos(rotation)
            val sin = kotlin.math.sin(rotation)
            val rotatedX = scaledX * cos - scaledY * sin
            val rotatedY = scaledX * sin + scaledY * cos

            // Translate back to world space
            result.add(rotatedX + centerX)
            result.add(rotatedY + centerY)
        }

        return result
    }

    private fun catmullRomSpline(
        p0: Pair<Float, Float>, p1: Pair<Float, Float>,
        p2: Pair<Float, Float>, p3: Pair<Float, Float>,
        t: Float
    ): Pair<Float, Float> {
        val t2 = t * t
        val t3 = t2 * t

        // Catmull-Rom matrix coefficients
        val a = -0.5f * t3 + t2 - 0.5f * t
        val b = 1.5f * t3 - 2.5f * t2 + 1.0f
        val c = -1.5f * t3 + 2.0f * t2 + 0.5f * t
        val d = 0.5f * t3 - 0.5f * t2

        return Pair(
            a * p0.first + b * p1.first + c * p2.first + d * p3.first,
            a * p0.second + b * p1.second + c * p2.second + d * p3.second
        )
    }

    private fun subdividePointsWithSplines(points: List<Float>, subdivisions: Int = 8): List<Float> {
        val result = mutableListOf<Float>()
        val numPoints = points.size / 2

        if (numPoints < 4) return points.toList() // Not enough points for spline

        // Convert to pairs for easier handling
        val pairs = points.chunked(2) { Pair(it[0], it[1]) }

        for (i in 0 until numPoints) {
            val p0 = pairs[(i - 1 + numPoints) % numPoints]
            val p1 = pairs[i]
            val p2 = pairs[(i + 1) % numPoints]
            val p3 = pairs[(i + 2) % numPoints]

            // Add original point
            result.add(p1.first)
            result.add(p1.second)

            // Add interpolated points
            for (j in 1 until subdivisions) {
                val t = j.toFloat() / subdivisions
                val splinePoint = catmullRomSpline(p0, p1, p2, p3, t)
                result.add(splinePoint.first)
                result.add(splinePoint.second)
            }
        }

        return result
    }

    private fun createBorderStrip(points: List<Float>, offset: Float): List<Float> {
        if (offset <= 0f) {
            return listOf()
        }

        val result = mutableListOf<Float>()
        val numPoints = points.size / 2

        // Create strip by alternating inner and outer points
        for (i in 0..numPoints) {
            val idx = i % numPoints
            val x = points[idx * 2]
            val y = points[idx * 2 + 1]

            val prevIdx = ((idx - 1 + numPoints) % numPoints) * 2
            val nextIdx = ((idx + 1) % numPoints) * 2

            val dx = points[nextIdx] - points[prevIdx]
            val dy = points[nextIdx + 1] - points[prevIdx + 1]

            // Calculate normal (perpendicular vector) and flip direction for outward expansion
            val normalX = dy  // Changed from -dy to dy
            val normalY = -dx // Changed from dx to -dx

            // Normalize the normal vector
            val length = kotlin.math.sqrt(normalX * normalX + normalY * normalY)
            if (length > 0) {
                // Add inner point first (base point)
                result.add(x)
                result.add(y)
                // Then add outer point (base point + offset)
                result.add(x + (normalX / length) * offset)
                result.add(y + (normalY / length) * offset)
            }
        }

        // Add first two points again to close the strip properly
        if (result.size >= 4) {
            result.add(result[0])
            result.add(result[1])
            result.add(result[2])
            result.add(result[3])
        }

        return result
    }

    private fun drawRipple() {
        if (activeRipples.isEmpty()) return

        // Enable stencil testing
        glPushAttrib(GL_ALL_ATTRIB_BITS)

        glEnable(GL_STENCIL_TEST)
        glEnable(GL_BLEND)
        glDisable(GL_TEXTURE_2D)

        glBlendFunc(GL_SRC_ALPHA, GL_ONE)
        glStencilFunc(GL_EQUAL, 1, 1)
        glStencilOp(GL_KEEP, GL_KEEP, GL_KEEP)

        // Draw each active ripple
        for (ripple in activeRipples) {

            glLineWidth(6f)
            glBegin(GL_LINE_LOOP)

            val radius = ripple.radius

            // Calculate distance fade (new fade based on distance traveled)
            val distanceFade = 1.0f - (ripple.radius / ripple.maxDistance)

            // Combine both fades and apply intensity
            val alpha = ripple.color[3] * distanceFade * ripple.intensity

            for (angle in 0..360 step 10) {
                val radian = Math.toRadians(angle.toDouble()).toFloat()
                val x = ripple.center.first + radius * kotlin.math.cos(radian)
                val y = ripple.center.second + radius * kotlin.math.sin(radian)

                glColor4f(ripple.color[0], ripple.color[1], ripple.color[2], alpha)
                glVertex2f(x, y)
            }

            glEnd()
            glLineWidth(1f)
        }

        glPopAttrib()
    }

    fun updateChargeProgress(progress: Float, facing: Float) {
        chargeProgress = progress
        shipFacing = facing
    }

    fun updateHexAnimation(time: Float, size: Float, spacing: Float) {
        hexAnimationTime = time
        hexSize = size
        hexSpacing = spacing
    }

    fun updateHexParameters(
        size: Float? = null,
        rotation: Float? = null,
        edgeWidth: Float? = null,
        spacingMultiplier: Float? = null
    ) {
        size?.let { hexSize = it.coerceAtLeast(1f) }
        rotation?.let { hexRotation = it % 360f }
        edgeWidth?.let { hexEdgeWidth = it.coerceAtLeast(0.5f) }
        spacingMultiplier?.let { hexSpacingMultiplier = it.coerceAtLeast(0.5f) }
    }

    fun updatePopEffectRadius(radius: Float) {
        popEffectRadius = radius
    }

    fun triggerShieldPop(intensity: Float = 1.0f) {
        // Create darker colors for the cloud effect
        val darkColor = floatArrayOf(colorRGBA[0] * 0.4f, colorRGBA[1] * 0.4f, colorRGBA[2] * 0.4f, 1f)
        val darkerColor =
            floatArrayOf(colorRGBA[0] * 0.25f, colorRGBA[1] * 0.25f, colorRGBA[2] * 0.25f, 0.8f)

        // Generate random radial lines
        val lines = mutableListOf<RadialLine>()
        var currentAngle = kotlin.random.Random.nextFloat() * 2f * Math.PI.toFloat() // Random start angle

        // Parameters for the lines
        val minLineLength = 0.2f * (0.7f + intensity * 0.3f)  // Reduced by half from previous value
        val maxLineLength = 0.45f * (0.7f + intensity * 0.3f)  // Reduced by half from previous value
        val minStartOffset = 0.5f  // Lines start 50% away from center
        val maxStartOffset = 0.7f  // Lines start up to 70% away from center
        val baseThickness = 2f + intensity * 2f // Base thickness in pixels
        val minAngularSpacing = Math.toRadians(10.0).toFloat()
        val maxAngularSpacing = Math.toRadians(40.0).toFloat()
        val numLinesMultiplier = 1f + intensity * 0.5f

        // Generate lines with random spacing until we go all the way around
        val startAngle = currentAngle
        while (currentAngle - startAngle < Math.PI * 2) {
            // Random length for this line
            val length = minLineLength + kotlin.random.Random.nextFloat() * (maxLineLength - minLineLength)

            // Random start offset from center
            val startOffset = minStartOffset + kotlin.random.Random.nextFloat() * (maxStartOffset - minStartOffset)

            // Random thickness variation
            val thickness = baseThickness * (0.7f + kotlin.random.Random.nextFloat() * 0.6f)

            // Random alpha
            val alpha = 0.7f + kotlin.random.Random.nextFloat() * 0.3f

            // Random speed multiplier
            val speedMultiplier = 0.85f + kotlin.random.Random.nextFloat() * 0.3f

            lines.add(
                RadialLine(
                    angle = currentAngle,
                    length = length,
                    startOffset = startOffset,
                    thickness = thickness,
                    alpha = alpha,
                    speedMultiplier = speedMultiplier
                )
            )

            // Calculate next angle with random spacing
            val spacing = minAngularSpacing + kotlin.random.Random.nextFloat() * (maxAngularSpacing - minAngularSpacing)
            currentAngle += spacing / numLinesMultiplier
        }

        // Create the pop wave with the radial lines
        activePopWave = PopWave(
            time = 0f,
            maxTime = 0.3f,
            color = darkColor,
            darkerColor = darkerColor,
            thickness = 20f * (0.5f + intensity * 0.5f),
            startScale = 0.1f,
            endScale = 3.0f + intensity * 0.5f,
            intensity = intensity.coerceIn(0.1f, 1.5f),
            radialLines = lines
        )
    }

    fun updateShieldPop(deltaTime: Float) {
        activePopWave?.let { popWave ->
            popWave.time += deltaTime

            // Remove the pop wave if it's completed its animation
            if (popWave.time >= popWave.maxTime) {
                activePopWave = null
            }
        }
    }

    fun isPopWaveActive(): Boolean {
        return activePopWave !== null
    }

    private fun _drawShieldPop(): Boolean {
        val popWave = activePopWave ?: return false

        // Calculate progress of the animation (0.0 to 1.0)
        val progress = (popWave.time / popWave.maxTime).coerceIn(0f, 1f)

        // Calculate current scale based on animation progress with easing
        val easedProgress = 1f - (1f - progress) * (1f - progress)  // Quadratic ease-out
        val currentScale = popWave.startScale + (popWave.endScale - popWave.startScale) * easedProgress

        // Current radius of the wave
        val waveRadius = popEffectRadius * currentScale

        // Calculate alpha based on progress (fade out near the end)
        val fadeStart = 0.4f  // Start fading at 40% of animation
        val alpha = if (progress < fadeStart) {
            popWave.color[3] * popWave.intensity  // Full alpha until fadeStart
        } else {
            popWave.color[3] * popWave.intensity * (1f - (progress - fadeStart) / (1f - fadeStart))
        }

        // Save OpenGL state
        glPushAttrib(GL_ALL_ATTRIB_BITS)

        // Set up stencil to ONLY draw between shield edge and wave radius
        glEnable(GL_STENCIL_TEST)
        glClear(GL_STENCIL_BUFFER_BIT)
        glColorMask(false, false, false, false)

        // First mark the wave circle area with stencil value 2
        glStencilFunc(GL_ALWAYS, 2, 0xFF)
        glStencilOp(GL_REPLACE, GL_REPLACE, GL_REPLACE)

        // Draw circle for the wave radius
        glBegin(GL_TRIANGLE_FAN)
        glVertex2f(centerX, centerY)
        for (i in 0..64) {
            val angle = 2.0 * Math.PI * i / 64
            glVertex2f(
                centerX + waveRadius * kotlin.math.cos(angle).toFloat(),
                centerY + waveRadius * kotlin.math.sin(angle).toFloat()
            )
        }
        glEnd()

        // Now set up stencil to only draw where shield isn't (stencil != 1)
        glStencilFunc(GL_EQUAL, 2, 0xFF)
        glStencilOp(GL_KEEP, GL_KEEP, GL_KEEP)
        glColorMask(true, true, true, true)

        // Set up blending for the noise texture
        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE)

        // Draw the noise texture as a single quad
        glEnable(GL_TEXTURE_2D)
        noiseSprite.bindTexture()

        // Calculate a single large quad that covers the wave area
        val quadSize = waveRadius * 2.2f  // Make it slightly larger than the diameter

        // Base color for the texture
        val r = popWave.darkerColor[0]
        val g = popWave.darkerColor[1]
        val b = popWave.darkerColor[2]
        val a = popWave.darkerColor[3] * alpha

        // Animation parameters
        val timeOffset = popWave.time * 0.5f
        val rotation = timeOffset * 20f  // Rotation angle

        // Draw a single textured quad
        glPushMatrix()
        glTranslatef(centerX, centerY, 0f)
        glRotatef(rotation, 0f, 0f, 1f)

        glBegin(GL_QUADS)
        glColor4f(r, g, b, a)

        // Calculate texture coordinates with animation
        val texScale = 3.0f  // How many times the texture repeats
        val texOffsetX = timeOffset * 0.2f
        val texOffsetY = timeOffset * 0.3f

        // Bottom left
        glTexCoord2f(texOffsetX, texOffsetY)
        glVertex2f(-quadSize / 2, -quadSize / 2)

        // Bottom right
        glTexCoord2f(texOffsetX + texScale, texOffsetY)
        glVertex2f(quadSize / 2, -quadSize / 2)

        // Top right
        glTexCoord2f(texOffsetX + texScale, texOffsetY + texScale)
        glVertex2f(quadSize / 2, quadSize / 2)

        // Top left
        glTexCoord2f(texOffsetX, texOffsetY + texScale)
        glVertex2f(-quadSize / 2, quadSize / 2)

        glEnd()
        glPopMatrix()

        // Disable texturing
        glDisable(GL_TEXTURE_2D)

        // Line fade parameters for radial lines
        val lineFadeStart = 0.3f // Lines start fading later than the main effect
        val lineAlpha = if (progress < lineFadeStart) {
            1.0f
        } else {
            1.0f - ((progress - lineFadeStart) / (1.0f - lineFadeStart))
        }

        // Enable line smoothing for better-looking lines
        glEnable(GL_LINE_SMOOTH)

        // Draw each radial line
        for (line in popWave.radialLines) {
            // Calculate current length based on progress and line's speed multiplier
            val currentLength = waveRadius * line.length * min(1.0f, progress * 3.0f * line.speedMultiplier)

            // Skip if length is too small
            if (currentLength < 5f) continue

            // Calculate start point with offset from center
            val startOffsetDistance = waveRadius * line.startOffset
            val startX = centerX + startOffsetDistance * kotlin.math.cos(line.angle)
            val startY = centerY + startOffsetDistance * kotlin.math.sin(line.angle)

            // Calculate the maximum distance to ensure lines end at least 20 pixels from radius
            val safeDistance = waveRadius - 20f
            val maxEndDistance = max(startOffsetDistance, safeDistance)

            // Calculate end point with safety margin from radius
            val rawEndDistance = startOffsetDistance + currentLength
            val endDistance = min(rawEndDistance, maxEndDistance)

            val endX = centerX + endDistance * kotlin.math.cos(line.angle)
            val endY = centerY + endDistance * kotlin.math.sin(line.angle)

            // Set line width
            glLineWidth(line.thickness)

            // Calculate final alpha with fadeout
            val finalAlpha = line.alpha * lineAlpha

            // Draw the main line only (no cap)
            glBegin(GL_LINES)
            // Start point (slightly transparent)
            glColor4f(1f, 1f, 1f, finalAlpha * 0.3f)
            glVertex2f(startX, startY)

            // End point (full brightness)
            glColor4f(1f, 1f, 1f, finalAlpha)
            glVertex2f(endX, endY)
            glEnd()
        }

        // After drawing both the noise texture and radial lines,
        // temporarily disable the stencil test to ensure the ring is drawn on top
        // of everything, even potentially over the shield edge
        glDisable(GL_STENCIL_TEST)

        // Now draw the main ring on top of everything with a slightly higher alpha
        // to make it more prominent
        val numSegments = 64
        val halfThickness = popWave.thickness * 0.5f

        // Use a higher alpha for the ring to make it stand out better against the noise texture
        val ringAlpha = alpha * 1.2f

        // Enable line smoothing for the ring
        glEnable(GL_LINE_SMOOTH)
        glHint(GL_LINE_SMOOTH_HINT, GL_NICEST)

        // Draw the main ring as a solid line instead of triangle strips for a cleaner look
        glLineWidth(popWave.thickness)
        glBegin(GL_LINE_LOOP)
        glColor4f(popWave.color[0], popWave.color[1], popWave.color[2], ringAlpha)

        for (i in 0..numSegments) {
            val angle = 2.0 * Math.PI * i / numSegments
            val cos = kotlin.math.cos(angle).toFloat()
            val sin = kotlin.math.sin(angle).toFloat()
            glVertex2f(centerX + waveRadius * cos, centerY + waveRadius * sin)
        }
        glEnd()

        // If you still want the fade-out effect at the edges, keep the triangle strips too
        // Inner ring fade
        glBegin(GL_TRIANGLE_STRIP)
        for (i in 0..numSegments) {
            val angle = 2.0 * Math.PI * i / numSegments
            val cos = kotlin.math.cos(angle).toFloat()
            val sin = kotlin.math.sin(angle).toFloat()

            glColor4f(popWave.color[0], popWave.color[1], popWave.color[2], 0f)
            glVertex2f(centerX + (waveRadius - halfThickness) * cos, centerY + (waveRadius - halfThickness) * sin)

            glColor4f(popWave.color[0], popWave.color[1], popWave.color[2], ringAlpha)
            glVertex2f(centerX + waveRadius * cos, centerY + waveRadius * sin)
        }
        glEnd()

        // Outer ring fade
        glBegin(GL_TRIANGLE_STRIP)
        for (i in 0..numSegments) {
            val angle = 2.0 * Math.PI * i / numSegments
            val cos = kotlin.math.cos(angle).toFloat()
            val sin = kotlin.math.sin(angle).toFloat()

            glColor4f(popWave.color[0], popWave.color[1], popWave.color[2], ringAlpha)
            glVertex2f(centerX + waveRadius * cos, centerY + waveRadius * sin)

            glColor4f(popWave.color[0], popWave.color[1], popWave.color[2], 0f)
            glVertex2f(centerX + (waveRadius + halfThickness) * cos, centerY + (waveRadius + halfThickness) * sin)
        }
        glEnd()

        // Restore OpenGL state
        glPopAttrib()

        return true
    }

    fun drawShieldPop(): Boolean {
        val isDrawing = _drawShieldPop()

        cleanup()

        return isDrawing
    }

    fun drawPolygon() {
        if (polygonPoints.isEmpty()) return

        val transformedBase = transformPoints(polygonPoints, 0f)
        val smoothBase = subdividePointsWithSplines(transformedBase)
        val borderStrip = createBorderStrip(smoothBase, borderWidth)

        // Set up basic OpenGL state
        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        glEnable(GL_POLYGON_SMOOTH)
        glEnable(GL_LINE_SMOOTH)
        glHint(GL_POLYGON_SMOOTH_HINT, GL_NICEST)
        glHint(GL_LINE_SMOOTH_HINT, GL_NICEST)
        glDisable(GL_TEXTURE_2D)

        // First pass: Create stencil mask for the shield shape
        glEnable(GL_STENCIL_TEST)
        glClear(GL_STENCIL_BUFFER_BIT)

        glStencilFunc(GL_EQUAL, 1, 1)
        glStencilOp(GL_REPLACE, GL_REPLACE, GL_REPLACE)
        glColorMask(false, false, false, false)

        // Draw the shield shape into the stencil buffer
        tess.gluTessBeginPolygon(null)
        tess.gluTessBeginContour()

        for (i in smoothBase.indices step 2) {
            val vertexArray = doubleArrayOf(
                smoothBase[i].toDouble(),
                smoothBase[i + 1].toDouble(),
                0.0
            )
            tess.gluTessVertex(vertexArray, 0, vertexArray)
        }

        tess.gluTessEndContour()
        tess.gluTessEndPolygon()

        // Second pass: Create stencil mask for the charge animation
        glStencilFunc(GL_ALWAYS, 2, 2)
        glStencilOp(GL_REPLACE, GL_REPLACE, GL_REPLACE)

        // Draw the sweep rectangle with corrected direction
        glBegin(GL_QUADS)
        // Back points (progress-based)
        val facingRad = Math.toRadians(shipFacing.toDouble()).toFloat()
        val sin = kotlin.math.sin(facingRad)
        val cos = kotlin.math.cos(facingRad)

        val difference = abs(shipDimensions[0].x) - abs(shipDimensions[1].x)

        // Base distances (in ship's local space)
        // magic numbers, no clue why it's these specifically. They will need to be changed per ship probably
        val forwardDistance = (shipDimensions[0].x) * scaleX + borderWidth - difference
        val backDistance = -(shipDimensions[1].x) * scaleX + borderWidth + difference
        val sideDistance = shipDimensions[0].y * scaleY + borderWidth + 12f

        val progress = chargeProgress

        // Calculate points in ship's local space, then rotate them
        val edgePoints = arrayOf(
            Vector2f(
                centerX + (-progress * (forwardDistance + backDistance) + backDistance) * cos - (-sideDistance) * sin,
                centerY + (-progress * (forwardDistance + backDistance) + backDistance) * sin + (-sideDistance) * cos
            ),
            Vector2f(
                centerX + (-progress * (forwardDistance + backDistance) + backDistance) * cos - (sideDistance) * sin,
                centerY + (-progress * (forwardDistance + backDistance) + backDistance) * sin + (sideDistance) * cos
            )
        )

        val forwardPoints = arrayOf(
            Vector2f(
                centerX + (-forwardDistance) * cos - (sideDistance) * sin,
                centerY + (-forwardDistance) * sin + (sideDistance) * cos
            ),
            Vector2f(
                centerX + (-forwardDistance) * cos - (-sideDistance) * sin,
                centerY + (-forwardDistance) * sin + (-sideDistance) * cos
            )
        )

        // Draw the actual quad
        glVertex2f(edgePoints[0].x, edgePoints[0].y)
        glVertex2f(edgePoints[1].x, edgePoints[1].y)
        glVertex2f(forwardPoints[0].x, forwardPoints[0].y)
        glVertex2f(forwardPoints[1].x, forwardPoints[1].y)
        glEnd()

        // // Re-enable stencil test for other elements
        glEnable(GL_STENCIL_TEST)
        glStencilFunc(GL_NOTEQUAL, 2, 2)
        glStencilOp(GL_KEEP, GL_KEEP, GL_KEEP)
        glColorMask(true, true, true, true)

        // Draw main shape
        glColor4f(colorRGBA[0], colorRGBA[1], colorRGBA[2], colorRGBA[3])
        tess.gluTessBeginPolygon(null)
        tess.gluTessBeginContour()
        for (i in smoothBase.indices step 2) {
            val vertexArray = doubleArrayOf(
                smoothBase[i].toDouble(),
                smoothBase[i + 1].toDouble(),
                0.0
            )
            tess.gluTessVertex(vertexArray, 0, vertexArray)
        }
        tess.gluTessEndContour()
        tess.gluTessEndPolygon()


        if (borderWidth > 0) {
            // Create border strip with proper width

            // Draw the border
            glColor4f(
                borderColorRGBA[0],
                borderColorRGBA[1],
                borderColorRGBA[2],
                borderColorRGBA[3]
            )

            glBegin(GL_TRIANGLE_STRIP)
            for (i in borderStrip.indices step 2) {
                glVertex2f(borderStrip[i], borderStrip[i + 1])
            }
            glEnd()
        }

        // Draw glow
        if (borderWidth > 0 && glowIntensity > 0) {
            val numLayers = 5
            for (i in numLayers downTo 1) {
                val t = i.toFloat() / numLayers
                val layerWidth = (glowWidth * (1f - t))
                val alpha = borderColorRGBA[3] * t * glowIntensity * 0.5f

                glColor4f(
                    borderColorRGBA[0],
                    borderColorRGBA[1],
                    borderColorRGBA[2],
                    alpha
                )

                val borderStrip = createBorderStrip(smoothBase, layerWidth)

                glBegin(GL_TRIANGLE_STRIP)
                for (i in borderStrip.indices step 2) {
                    glVertex2f(borderStrip[i], borderStrip[i + 1])
                }
                glEnd()
            }
        }

        glStencilFunc(GL_EQUAL, 2, 2)
        glStencilOp(GL_KEEP, GL_KEEP, GL_KEEP)

        drawHexGrid()
        drawRipple()

        cleanUp()
    }

    private fun cleanUp() {
        glDisable(GL_STENCIL_TEST)
        glEnable(GL_TEXTURE_2D)
        glDisable(GL_BLEND)
        glDisable(GL_POLYGON_SMOOTH)
        glDisable(GL_LINE_SMOOTH)
    }

    private fun drawHexGrid() {
        // Save OpenGL state
        glPushAttrib(GL_ALL_ATTRIB_BITS)

        // Set up rendering state
        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE)
        glDisable(GL_TEXTURE_2D)
        glEnable(GL_STENCIL_TEST)
        glStencilFunc(GL_EQUAL, 1, 1)
        glStencilOp(GL_KEEP, GL_KEEP, GL_KEEP)

        // Calculate hex grid dimensions
        val hexWidth = hexSize * 2f
        val hexHeight = hexSize * kotlin.math.sqrt(3.0).toFloat()

        // Calculate spacing with multiplier
        val hexSpacingX = hexWidth * 0.75f * hexSpacingMultiplier
        val hexSpacingY = hexHeight * hexSpacingMultiplier

        val cols = (shipDimensions[0].x * 2 / hexSpacingX).toInt() + 2
        val rows = (shipDimensions[0].y * 2 / hexSpacingY).toInt() + 2

        // Calculate animation offset
        val offsetX = hexAnimationTime % hexSpacingX
        val offsetY = (hexAnimationTime % hexSpacingY) * 0.5f

        // Save the current matrix state
        glPushMatrix()

        // Translate to center, rotate, translate back
        glTranslatef(centerX, centerY, 0f)
        glRotatef(shipFacing, 0f, 0f, 1f)

        // Draw hex grid in local space
        for (row in -rows..rows) {
            for (col in -cols..cols) {
                // Offset every other row to create the hex pattern
                val xOffset = if (row % 2 == 0) 0f else hexSpacingX * 0.5f
                val x = col * hexSpacingX + offsetX + xOffset
                val y = row * hexSpacingY * 0.75f + offsetY

                // Calculate alpha based on animation time
                val alpha = 0.5f * kotlin.math.sin(hexAnimationTime + col * 0.2f + row * 0.3f)

                // Draw hexagon edges with fixed rotation (relative to ship)
                drawRotatedHexEdges(x, y, hexSize, hexEdgeWidth, hexRotation, alpha)
            }
        }

        // Restore the matrix state
        glPopMatrix()

        // Restore OpenGL state
        glPopAttrib()
    }

    private fun drawRotatedHexEdges(
        x: Float,
        y: Float,
        size: Float,
        edgeWidth: Float,
        rotation: Float,
        alpha: Float
    ) {
        // Draw outer edge
        glLineWidth(edgeWidth)
        glBegin(GL_LINE_LOOP)
        glColor4f(0.8f, 0.9f, 1.0f, alpha * 0.3f)
        for (i in 0..6) {
            val angle = 2.0 * Math.PI / 6.0 * i + Math.toRadians(rotation.toDouble())
            val hexX = x + size * kotlin.math.cos(angle).toFloat()
            val hexY = y + size * kotlin.math.sin(angle).toFloat()
            glVertex2f(hexX, hexY)
        }
        glEnd()

        // Draw inner edge with slight offset
        val innerSize = size - edgeWidth
        glBegin(GL_LINE_LOOP)
        glColor4f(0.8f, 0.9f, 1.0f, alpha * 0.3f)
        for (i in 0..6) {
            val angle = 2.0 * Math.PI / 6.0 * i + Math.toRadians(rotation.toDouble())
            val hexX = x + innerSize * kotlin.math.cos(angle).toFloat()
            val hexY = y + innerSize * kotlin.math.sin(angle).toFloat()
            glVertex2f(hexX, hexY)
        }
        glEnd()
    }
    
    fun cleanup() {
        tess.gluDeleteTess()
        glDeleteBuffers(vbo)
    }

    fun drawDebugOverlay() {
        // Calculate the points just like in drawPolygon
        val facingRad = Math.toRadians(shipFacing.toDouble()).toFloat()
        val sin = kotlin.math.sin(facingRad)
        val cos = kotlin.math.cos(facingRad)

        val difference = abs(shipDimensions[0].x) - abs(shipDimensions[1].x)

        // Base distances (in ship's local space)
        // magic numbers, no clue why it's these specifically. They will need to be changed per ship probably
        val forwardDistance = (shipDimensions[0].x) * scaleX + borderWidth - difference
        val backDistance = -(shipDimensions[1].x) * scaleX + borderWidth + difference
        val sideDistance = shipDimensions[0].y * scaleY + borderWidth + 4f

        val progress = chargeProgress

        // Calculate points in ship's local space, then rotate them
        val edgePoints = arrayOf(
            Vector2f(
                centerX + (-progress * (forwardDistance + backDistance) + backDistance) * cos - (-sideDistance) * sin,
                centerY + (-progress * (forwardDistance + backDistance) + backDistance) * sin + (-sideDistance) * cos
            ),
            Vector2f(
                centerX + (-progress * (forwardDistance + backDistance) + backDistance) * cos - (sideDistance) * sin,
                centerY + (-progress * (forwardDistance + backDistance) + backDistance) * sin + (sideDistance) * cos
            )
        )

        val forwardPoints = arrayOf(
            Vector2f(
                centerX + (-forwardDistance) * cos - (sideDistance) * sin,
                centerY + (-forwardDistance) * sin + (sideDistance) * cos
            ),
            Vector2f(
                centerX + (-forwardDistance) * cos - (-sideDistance) * sin,
                centerY + (-forwardDistance) * sin + (-sideDistance) * cos
            )
        )

        // Save all OpenGL state
        glPushAttrib(GL_ALL_ATTRIB_BITS)

        // Reset to simple rendering state
        glDisable(GL_STENCIL_TEST)
        glDisable(GL_DEPTH_TEST)
        glDisable(GL_TEXTURE_2D)
        glDisable(GL_LIGHTING)
        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE)

        // Draw thick white outline
        glLineWidth(4f)
        glBegin(GL_LINE_LOOP)
        glColor4f(1f, 1f, 1f, 1f)
        glVertex2f(edgePoints[0].x, edgePoints[0].y)
        glVertex2f(edgePoints[1].x, edgePoints[1].y)
        glVertex2f(forwardPoints[0].x, forwardPoints[0].y)
        glVertex2f(forwardPoints[1].x, forwardPoints[1].y)
        glEnd()

        // Draw center point
        glPointSize(10f)
        glBegin(GL_POINTS)
        glColor4f(1f, 0f, 0f, 1f)
        glVertex2f(edgePoints[0].x, edgePoints[0].y)
        glColor4f(0f, 1f, 0f, 1f)
        glVertex2f(edgePoints[1].x, edgePoints[1].y)
        glColor4f(0f, 0f, 1f, 1f)
        glVertex2f(forwardPoints[0].x, forwardPoints[0].y)
        glColor4f(0f, 1f, 1f, 1f)
        glVertex2f(forwardPoints[1].x, forwardPoints[1].y)
        glColor4f(1f, 1f, 0f, 1f)
        glVertex2f(centerX, centerY)
        glEnd()

        // Print debug info
//        println("Debug Overlay:")
//        println("Center: ($centerX, $centerY)")
//        println("Ship Facing: $shipFacing")
//        println("Progress: $chargeProgress")
//        println("Edge Points: (${edgePoints[0].x}, ${edgePoints[0].y}), (${edgePoints[1].x}, ${edgePoints[1].y})")
//        println("Forward Points: (${forwardPoints[0].x}, ${forwardPoints[0].y}), (${forwardPoints[1].x}, ${forwardPoints[1].y})")

        glPopAttrib()
    }
}

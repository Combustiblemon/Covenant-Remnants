package org.combustiblelemon.covenantremnants.utils

import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL15.*
import org.lwjgl.util.glu.GLU
import org.lwjgl.util.glu.GLUtessellator
import org.lwjgl.util.glu.GLUtessellatorCallbackAdapter

class PolygonShader(
    private var polygonPoints: List<Float> = listOf(),
    private var colorRGBA: FloatArray = floatArrayOf(0.2f, 0.4f, 1.0f, 1f),
    private var borderColorRGBA: FloatArray = floatArrayOf(1f, 1f, 1f, 1f),
    private var borderWidth: Float = 10f,
    private var glowIntensity: Float = 0f,
    private var glowWidth: Float = 20f,  // New parameter for glow width
    private var rippleCenter: Pair<Float, Float>? = null,
    private var rippleRadius: Float = 0f,
    private var rippleWidth: Float = 10f,
    private var rippleColor: FloatArray = floatArrayOf(1f, 1f, 1f, 0.5f),
    private var rippleTime: Float = 0f
) {
    private var tess: GLUtessellator = GLU.gluNewTess()
    private var vbo: Int = 0
    private var scaleX: Float = 1.0f
    private var scaleY: Float = 1.0f
    private var rotation: Float = 0f
    private var centerX: Float = 0f
    private var centerY: Float = 0f
    private var rippleActive = false

    // Replace single ripple properties with a data class and list
    private data class Ripple(
        var center: Pair<Float, Float>,
        var radius: Float,
        var width: Float,
        var color: FloatArray,
        var time: Float,
        var intensity: Float,  // New parameter (0.0f to 1.0f)
        var maxDistance: Float // New parameter for how far the ripple travels
    )

    private val activeRipples = mutableListOf<Ripple>()

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
            maxDistance = 1000f * intensity.coerceIn(0.2f, 1f) // Minimum travel distance is 20% of max
        )
        activeRipples.add(ripple)
    }

    fun updateRipple(deltaTime: Float, speed: Float = 400f) {
        activeRipples.removeAll { ripple ->
            ripple.time += deltaTime
            ripple.radius += speed * deltaTime
            ripple.radius > ripple.maxDistance  // Remove when reaching max distance
        }
    }

    private fun calculateCenter(points: List<Float>): Pair<Float, Float> {
        var sumX = 0f
        var sumY = 0f
        for (i in points.indices step 2) {
            sumX += points[i]
            sumY += points[i + 1]
        }
        val count = points.size / 2
        return Pair(sumX / count, sumY / count)
    }

    private fun rotatePoint(x: Float, y: Float, centerX: Float, centerY: Float, angle: Float): Pair<Float, Float> {
        val cos = kotlin.math.cos(angle)
        val sin = kotlin.math.sin(angle)

        // Translate point to origin
        val dx = x - centerX
        val dy = y - centerY

        // Rotate
        val rotatedX = dx * cos - dy * sin
        val rotatedY = dx * sin + dy * cos

        // Translate back
        return Pair(
            rotatedX + centerX,
            rotatedY + centerY
        )
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

    private fun subdividePoints(points: List<Float>, subdivisions: Int = 4): List<Float> {
        val result = mutableListOf<Float>()

        for (i in points.indices step 2) {
            val x1 = points[i]
            val y1 = points[i + 1]
            val x2 = points[(i + 2) % points.size]
            val y2 = points[(i + 3) % points.size]

            // Add original point
            result.add(x1)
            result.add(y1)

            // Add interpolated points
            for (j in 1 until subdivisions) {
                val t = j.toFloat() / subdivisions
                result.add(x1 + (x2 - x1) * t)
                result.add(y1 + (y2 - y1) * t)
            }
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
        val result = mutableListOf<Float>()
        val numPoints = points.size / 2

        val scaledOffset = offset * 0.1f

        // Create strip by alternating inner and outer points
        for (i in 0..numPoints) {
            val idx = i % numPoints
            val x = points[idx * 2]
            val y = points[idx * 2 + 1]

            val prevIdx = ((idx - 1 + numPoints) % numPoints) * 2
            val nextIdx = ((idx + 1) % numPoints) * 2

            val dx = points[nextIdx] - points[prevIdx]
            val dy = points[nextIdx + 1] - points[prevIdx + 1]

            // Flip normal direction to go outward
            val normalX = dy  // Removed the negative
            val normalY = -dx // Added the negative

            val length = kotlin.math.sqrt(normalX * normalX + normalY * normalY)
            if (length > 0) {
                // Add inner point first (base point)
                result.add(x)
                result.add(y)
                // Then add outer point (base point + offset)
                result.add(x + (normalX / length) * scaledOffset)
                result.add(y + (normalY / length) * scaledOffset)
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

    private fun drawRipple(points: List<Float>) {
        if (activeRipples.isEmpty()) return

        // Find bounds of the shape
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE

        for (i in points.indices step 2) {
            val x = points[i]
            val y = points[i + 1]
            minX = minX.coerceAtMost(x)
            minY = minY.coerceAtMost(y)
            maxX = maxX.coerceAtLeast(x)
            maxY = maxY.coerceAtLeast(y)
        }

        // Enable stencil testing
        glEnable(GL_STENCIL_TEST)
        glClear(GL_STENCIL_BUFFER_BIT)

        // Create mask
        glStencilFunc(GL_ALWAYS, 1, 1)
        glStencilOp(GL_REPLACE, GL_REPLACE, GL_REPLACE)
        glColorMask(false, false, false, false)

        // Draw shape mask
        tess.gluTessBeginPolygon(null)
        tess.gluTessBeginContour()
        for (i in points.indices step 2) {
            val vertexArray = doubleArrayOf(points[i].toDouble(), points[i + 1].toDouble(), 0.0)
            tess.gluTessVertex(vertexArray, 0, vertexArray)
        }
        tess.gluTessEndContour()
        tess.gluTessEndPolygon()

        // Setup for ripple drawing
        glStencilFunc(GL_EQUAL, 1, 1)
        glStencilOp(GL_KEEP, GL_KEEP, GL_KEEP)
        glColorMask(true, true, true, true)

        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE)

        // Draw each active ripple
        for (ripple in activeRipples) {
            val numRings = 16
            val ringSpacing = ripple.width / numRings

            // Calculate fade based on distance traveled
            val distanceFade = 1f - (ripple.radius / ripple.maxDistance)

            glBegin(GL_TRIANGLE_STRIP)
            for (i in 0..numRings) {
                val radius = ripple.radius + (i - numRings/2) * ringSpacing
                val t = 1.0f - kotlin.math.abs(i - numRings/2) / (numRings/2f)

                // Combine ring fade (t), distance fade, and intensity for final alpha
                val alpha = ripple.color[3] * t * distanceFade * ripple.intensity

                // Draw points around the circle
                for (angle in 0..360 step 10) {
                    val radian = Math.toRadians(angle.toDouble()).toFloat()
                    val x = ripple.center.first + radius * kotlin.math.cos(radian)
                    val y = ripple.center.second + radius * kotlin.math.sin(radian)

                    glColor4f(ripple.color[0], ripple.color[1], ripple.color[2], alpha)
                    glVertex2f(x, y)
                }
            }
            glEnd()
        }

        // Restore states
        glDisable(GL_STENCIL_TEST)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
    }

    fun drawPolygon() {
        if (polygonPoints.isEmpty()) return

        // Enable required OpenGL states
        glEnable(GL_POLYGON_SMOOTH)
        glEnable(GL_LINE_SMOOTH)
        glHint(GL_POLYGON_SMOOTH_HINT, GL_NICEST)
        glHint(GL_LINE_SMOOTH_HINT, GL_NICEST)
        glDisable(GL_TEXTURE_2D)
        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)

        val transformedBase = transformPoints(polygonPoints, 0f)
        val smoothBase = subdividePointsWithSplines(transformedBase)

        // Draw glow layers if intensity > 0
        if (borderWidth > 0 && glowIntensity > 0) {
            val numLayers = 5
            for (i in numLayers downTo 1) {
                val t = i.toFloat() / numLayers
                val layerWidth = borderWidth + (glowWidth * (1f - t))  // Use separate glow width
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

        // Draw main border
        if (borderWidth > 0) {
            glColor4f(
                borderColorRGBA[0],
                borderColorRGBA[1],
                borderColorRGBA[2],
                borderColorRGBA[3]
            )

            val borderStrip = createBorderStrip(smoothBase, borderWidth)

            glBegin(GL_TRIANGLE_STRIP)
            for (i in borderStrip.indices step 2) {
                glVertex2f(borderStrip[i], borderStrip[i + 1])
            }
            glEnd()
        }

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

        // Draw ripple effect on top
        drawRipple(smoothBase)

        // Restore state
        glEnable(GL_TEXTURE_2D)
        glDisable(GL_BLEND)
        glDisable(GL_POLYGON_SMOOTH)
        glDisable(GL_LINE_SMOOTH)
    }

    private fun expandPoints(points: List<Float>, amount: Float): List<Float> {
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
            val toPrevX = points[prev] - x
            val toPrevY = points[prev + 1] - y
            val toNextX = points[next] - x
            val toNextY = points[next + 1] - y

            // Calculate normal by averaging the normals of adjacent edges
            val normalX = -(toPrevY + toNextY)
            val normalY = toPrevX + toNextX

            // Normalize
            val length = kotlin.math.sqrt(normalX * normalX + normalY * normalY)
            if (length > 0) {
                val nx = normalX / length
                val ny = normalY / length

                // Add expanded point
                result.add(x + nx * amount)
                result.add(y + ny * amount)
            } else {
                // If we can't calculate normal, just use original point
                result.add(x)
                result.add(y)
            }
        }

        return result
    }

    fun cleanup() {
        tess.gluDeleteTess()
        glDeleteBuffers(vbo)
    }
}

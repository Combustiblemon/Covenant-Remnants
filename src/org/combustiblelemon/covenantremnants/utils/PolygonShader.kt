package org.combustiblelemon.covenantremnants.utils

import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL15.*
import org.lwjgl.util.glu.GLU
import org.lwjgl.util.glu.GLUtessellator
import org.lwjgl.util.glu.GLUtessellatorCallbackAdapter

class PolygonShader(
    private var polygonPoints: List<Float> = listOf(),
    private var colorRGBA: FloatArray = floatArrayOf(0.2f, 0.4f, 1.0f, 1f)
) {
    private var tess: GLUtessellator = GLU.gluNewTess()
    private var vbo: Int = 0
    private var scaleX: Float = 1.0f
    private var scaleY: Float = 1.0f
    private var rotation: Float = 0f
    private var centerX: Float = 0f
    private var centerY: Float = 0f

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

    fun updateColor(r: Float, g: Float, b: Float, a: Float) {
        colorRGBA = floatArrayOf(r, g, b, a)
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

    private fun transformPoints(points: List<Float>): List<Float> {
        if (scaleX == 1.0f && scaleY == 1.0f && rotation == 0f) return points

        val result = mutableListOf<Float>()

        for (i in points.indices step 2) {
            // Get point
            val x = points[i]
            val y = points[i + 1]

            // Translate to origin
            val localX = x - centerX
            val localY = y - centerY

            // Scale in local space (swap X and Y scales)
            val scaledX = localX * scaleY  // Using scaleY for X
            val scaledY = localY * scaleX  // Using scaleX for Y

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

    private fun catmullRomSpline(p0: Pair<Float, Float>, p1: Pair<Float, Float>,
                                p2: Pair<Float, Float>, p3: Pair<Float, Float>,
                                t: Float): Pair<Float, Float> {
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

    fun drawPolygon() {
        if (polygonPoints.isEmpty()) return

        // Enable smoothing
        glEnable(GL_POLYGON_SMOOTH)
        glEnable(GL_LINE_SMOOTH)
        glHint(GL_POLYGON_SMOOTH_HINT, GL_NICEST)
        glHint(GL_LINE_SMOOTH_HINT, GL_NICEST)

        // Enable blending
        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        glDisable(GL_TEXTURE_2D)

        // Set the color
        glColor4f(colorRGBA[0], colorRGBA[1], colorRGBA[2], colorRGBA[3])

        // Start tessellation
        tess.gluTessBeginPolygon(null)
        tess.gluTessBeginContour()

        // Apply scaling and rotation, then smooth
        val transformedPoints = transformPoints(polygonPoints)
        val smoothPoints = subdividePointsWithSplines(transformedPoints)

        for (i in smoothPoints.indices step 2) {
            val vertexArray = doubleArrayOf(
                smoothPoints[i].toDouble(),
                smoothPoints[i + 1].toDouble(),
                0.0  // z coordinate
            )
            tess.gluTessVertex(vertexArray, 0, vertexArray)
        }

        tess.gluTessEndContour()
        tess.gluTessEndPolygon()

        // Restore state
        glEnable(GL_TEXTURE_2D)
        glDisable(GL_BLEND)
        glDisable(GL_POLYGON_SMOOTH)
        glDisable(GL_LINE_SMOOTH)
    }

    fun cleanup() {
        tess.gluDeleteTess()
        glDeleteBuffers(vbo)
    }
}

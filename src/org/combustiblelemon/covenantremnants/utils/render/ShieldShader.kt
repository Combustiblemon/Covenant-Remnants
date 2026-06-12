package org.combustiblelemon.covenantremnants.utils.render

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ViewportAPI
import org.combustiblelemon.covenantremnants.utils.getShaderCode
import org.combustiblelemon.covenantremnants.utils.rotatePointByDegrees
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL15.*
import org.lwjgl.opengl.GL20.*
import org.lwjgl.opengl.GL30.*
import org.lwjgl.util.vector.Vector2f

enum class SmoothingMethod {
    SIMPLE,   // Uses edge smoothing by repositioning points
    SPLINE,   // Uses Laplacian smoothing for natural shape smoothing
    NONE      // Uses minimal smoothing for very subtle adjustments
}

// TODO: Fix ripple effects to be consistent across zoom levels. Fix ripple radius and width to be consistent across zoom levels. Ripple is much faster zoomed out than zoomed in.

class ShieldShader(
    private val ship: ShipAPI,
    private var popEffectRadius: Float = 500f,  // Default pop effect radius
    private val boundsCount: Int,
    private val smoothingMethod: SmoothingMethod = SmoothingMethod.NONE
) : Shader {
    private var polygonPoints: List<Float> = listOf()
    private var colorRGBA: FloatArray = floatArrayOf(0.2f, 0.4f, 1.0f, 1f)
    private var borderColorRGBA: FloatArray = floatArrayOf(1f, 1f, 1f, 1f)
    private var borderWidth: Float = 10f
    private var glowIntensity: Float = 0f
    private var glowWidth: Float = 20f
    private var vbo: Int = 0
    private var vao: Int = 0
    private var shaderProgram: Int = 0

    // No UBO needed - using direct uniforms instead
    private var scaleX: Float = 1.0f
    private var scaleY: Float = 1.0f
    private var screenWidth: Float = 1920f
    private var screenHeight: Float = 1080f
    private var zoomLevel: Float = 1f
    private var splineSubdivisions: Int = 8
    private var debugMode: Boolean = false
    private var enableSplineSmoothing: Boolean = true
    private var enableBoundingBoxOptimization: Boolean = true // Re-enabled, scissor test disabled

    // Pre-calculated rotation matrix for vertex shader optimization
    private var shipRotationMatrix: FloatArray = floatArrayOf(1f, 0f, 0f, 1f) // Identity matrix

    // Viewport scale for world-to-screen conversion
    private var viewportScaleX: Float = 1.0f
    private var viewportScaleY: Float = 1.0f

    // Pre-calculated values for performance
    private var shieldBoundsMinX: Float = 0f
    private var shieldBoundsMaxX: Float = 0f
    private var effectiveMinX: Float = 0f
    private var effectiveMaxX: Float = 0f
    private var effectiveWidth: Float = 0f

    // Sweep position calculated on CPU
    private var sweepPosition: Float = 0f

    // Bounding box for optimization
    private var boundingBoxMinX: Float = 0f
    private var boundingBoxMaxX: Float = 0f
    private var boundingBoxMinY: Float = 0f
    private var boundingBoxMaxY: Float = 0f
    private var boundingBoxWidth: Float = 0f
    private var boundingBoxHeight: Float = 0f

    // Add animation properties
    private var time = 0f
    private var shieldHPPercent = 1f

    // Honeycomb parameters - hardcoded
    private var honeycombEnabled = true
    private var honeycombSize = 10.0f
    private var edgeWidth = 3.0f

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
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as PopWave

            if (time != other.time) return false
            if (maxTime != other.maxTime) return false
            if (thickness != other.thickness) return false
            if (startScale != other.startScale) return false
            if (endScale != other.endScale) return false
            if (intensity != other.intensity) return false
            if (!color.contentEquals(other.color)) return false
            if (!darkerColor.contentEquals(other.darkerColor)) return false
            if (radialLines != other.radialLines) return false

            return true
        }

        override fun hashCode(): Int {
            var result = time.hashCode()
            result = 31 * result + maxTime.hashCode()
            result = 31 * result + thickness.hashCode()
            result = 31 * result + startScale.hashCode()
            result = 31 * result + endScale.hashCode()
            result = 31 * result + intensity.hashCode()
            result = 31 * result + color.contentHashCode()
            result = 31 * result + darkerColor.contentHashCode()
            result = 31 * result + radialLines.hashCode()
            return result
        }
    }

    private var activePopWave: PopWave? = null

    private data class RadialLine(
        val angle: Float,       // Angle in radians
        val length: Float,      // Radius will multiply the length of the line (0-1)
        val startOffset: Float, // Radius will multiply offset from center (0-1)
        val thickness: Float,   // Thickness of the line
        val alpha: Float,       // Base alpha/opacity
        val speedMultiplier: Float  // How fast this line expands compared to others
    )

    // Shader source code
    private val vertexShaderSource = getShaderCode("data/shaders/shieldShader.vert")
    private val fragmentShaderSource = getShaderCode("data/shaders/shieldShader.frag")

    init {
        init()
        initShaders()
    }

    private fun init() {
        // Create VAO
        vao = glGenVertexArrays()
        glBindVertexArray(vao)

        // Create VBO - initially with full-screen quad, will be updated with bounding box
        // Each vertex now has: NDC coords (2 floats) + world coords (2 floats) = 4 floats per vertex
        val vertices = floatArrayOf(
            // NDC coords (location 0) + world coords (location 1)
            -1.0f, -1.0f, 0.0f, 0.0f,  // Bottom-left
            1.0f, -1.0f, 0.0f, 0.0f,   // Bottom-right
            1.0f, 1.0f, 0.0f, 0.0f,    // Top-right
            -1.0f, 1.0f, 0.0f, 0.0f    // Top-left
        )

        vbo = glGenBuffers()
        glBindBuffer(GL_ARRAY_BUFFER, vbo)

        // Convert float array to FloatBuffer
        val buffer = BufferUtils.createFloatBuffer(vertices.size)
        buffer.put(vertices)
        buffer.flip()

        glBufferData(GL_ARRAY_BUFFER, buffer, GL_DYNAMIC_DRAW) // Changed to DYNAMIC_DRAW for updates

        // Set up vertex attributes
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 4 * 4, 0)        // NDC coordinates
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 4 * 4, 2 * 4)    // World coordinates
        glEnableVertexAttribArray(0)
        glEnableVertexAttribArray(1)

        glBindVertexArray(0)

        // No UBO needed - using direct uniforms instead
    }

    private fun updateQuadVertices() {
        if (boundingBoxWidth <= 0f || boundingBoxHeight <= 0f) {
            // Fallback to full-screen quad if bounding box is invalid
            val vertices = floatArrayOf(
                // NDC coords (location 0) + world coords (location 1)
                -1.0f, -1.0f, 0.0f, 0.0f,  // Bottom-left
                1.0f, -1.0f, 0.0f, 0.0f,   // Bottom-right
                1.0f, 1.0f, 0.0f, 0.0f,    // Top-right
                -1.0f, 1.0f, 0.0f, 0.0f    // Top-left
            )
            updateVBO(vertices)
            return
        }

        // Convert screen coordinates to NDC coordinates
        val left = (boundingBoxMinX / screenWidth) * 2.0f - 1.0f
        val right = (boundingBoxMaxX / screenWidth) * 2.0f - 1.0f
        val bottom = (boundingBoxMinY / screenHeight) * 2.0f - 1.0f
        val top = (boundingBoxMaxY / screenHeight) * 2.0f - 1.0f

        // Use dummy world coordinates (0,0) for all vertices
        // The vertex shader will handle the transformation
        val vertices = floatArrayOf(
            // NDC coords (location 0) + world coords (location 1)
            left, bottom, 0.0f, 0.0f,   // Bottom-left
            right, bottom, 0.0f, 0.0f,  // Bottom-right
            right, top, 0.0f, 0.0f,     // Top-right
            left, top, 0.0f, 0.0f       // Top-left
        )

        updateVBO(vertices)
    }

    private fun updateVBO(vertices: FloatArray) {
        glBindBuffer(GL_ARRAY_BUFFER, vbo)

        // Convert float array to FloatBuffer
        val buffer = BufferUtils.createFloatBuffer(vertices.size)
        buffer.put(vertices)
        buffer.flip()

        // Update the buffer data
        glBufferSubData(GL_ARRAY_BUFFER, 0, buffer)

        glBindBuffer(GL_ARRAY_BUFFER, 0)
    }

    // UBO initialization removed - using direct uniforms instead

    /**
     * Calculate the maximum number of points that could be generated after smoothing
     * This ensures the shader array is large enough to handle all possible smoothed points
     */
    private fun calculateMaxPointsAfterSmoothing(originalPointCount: Int): Int {
        val maxPoints = when (smoothingMethod) {
            SmoothingMethod.SPLINE -> {
                // Spline smoothing: Laplacian smoothing - no new points, just repositioned
                originalPointCount
            }

            SmoothingMethod.SIMPLE -> {
                // Simple smoothing: edge smoothing - no new points, just repositioned
                originalPointCount
            }

            SmoothingMethod.NONE -> {
                // Minimal smoothing: very subtle smoothing - no new points, just repositioned
                originalPointCount
            }
        }

        // Ensure we don't exceed reasonable bounds (safety check)
        val finalResult = minOf(maxPoints, 1000) // Maximum 1000 points for shader array
        return finalResult
    }

    private fun setPolygonPointUniforms(pointCount: Int) {
        // Set polygon points directly as uniforms instead of using UBO
        val actualPointCount = polygonPoints.size / 2

        // Safety check: ensure polygonPoints has even number of elements
        if (polygonPoints.size % 2 != 0) {
            Global.getLogger(this.javaClass).warn("polygonPoints has odd number of elements: ${polygonPoints.size}")
        }

        // Safety check: ensure we don't exceed the shader array size
        val safePointCount = minOf(actualPointCount, pointCount)

        // Set actual polygon points as uniforms (now world coordinates)
        for (i in 0 until safePointCount) {
            val x = polygonPoints[i * 2]
            val y = polygonPoints[i * 2 + 1]
            val uniformLocation = glGetUniformLocation(shaderProgram, "points[$i]")
            glUniform2f(uniformLocation, x, y)
        }

        // Set remaining array elements to zero
        for (i in safePointCount until pointCount) {
            val uniformLocation = glGetUniformLocation(shaderProgram, "points[$i]")
            glUniform2f(uniformLocation, 0.0f, 0.0f)
        }

        // Check for OpenGL errors
        val error = glGetError()
        if (error != GL_NO_ERROR) {
            Global.getLogger(this.javaClass).error("OpenGL error after setting uniforms: $error")
        }
    }

    private fun initShaders() {
        // Create and compile vertex shader
        val vertexShader = glCreateShader(GL_VERTEX_SHADER)
        glShaderSource(vertexShader, vertexShaderSource)
        glCompileShader(vertexShader)

        // Check for vertex shader compilation errors
        if (glGetShaderi(vertexShader, GL_COMPILE_STATUS) == GL_FALSE) {
            val error = glGetShaderInfoLog(vertexShader, 1024)
            throw RuntimeException("Vertex shader compilation failed: $error")
        }

        // Calculate the maximum number of points after smoothing
        val maxPointsAfterSmoothing = calculateMaxPointsAfterSmoothing(boundsCount)

        // Create and compile fragment shader
        val fragmentShader = glCreateShader(GL_FRAGMENT_SHADER)
        glShaderSource(
            fragmentShader,
            fragmentShaderSource.replace("POINT_COUNT_PLACEHOLDER", maxPointsAfterSmoothing.toString())
        )
        glCompileShader(fragmentShader)

        // Check for fragment shader compilation errors
        if (glGetShaderi(fragmentShader, GL_COMPILE_STATUS) == GL_FALSE) {
            val error = glGetShaderInfoLog(fragmentShader, 1024)
            throw RuntimeException("Fragment shader compilation failed: $error")
        }

        // Create shader program
        shaderProgram = glCreateProgram()
        glAttachShader(shaderProgram, vertexShader)
        glAttachShader(shaderProgram, fragmentShader)
        glLinkProgram(shaderProgram)

        // Check for linking errors
        if (glGetProgrami(shaderProgram, GL_LINK_STATUS) == GL_FALSE) {
            val error = glGetProgramInfoLog(shaderProgram, 1024)
            throw RuntimeException("Shader program linking failed: $error")
        }

        // Clean up shaders
        glDeleteShader(vertexShader)
        glDeleteShader(fragmentShader)
    }

    private fun calculateBoundingBox() {
        if (polygonPoints.size < 6) {
            boundingBoxMinX = 0f
            boundingBoxMaxX = 0f
            boundingBoxMinY = 0f
            boundingBoxMaxY = 0f
            boundingBoxWidth = 0f
            boundingBoxHeight = 0f
            return
        }

        // Calculate screen-space bounding box of the shield polygon
        var minX = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var minY = Float.MAX_VALUE
        var maxY = Float.MIN_VALUE

        // polygonPoints now contains screen coordinates
        for (i in polygonPoints.indices step 2) {
            val screenX = polygonPoints[i]
            val screenY = polygonPoints[i + 1]

            minX = minOf(minX, screenX)
            maxX = maxOf(maxX, screenX)
            minY = minOf(minY, screenY)
            maxY = maxOf(maxY, screenY)
        }

        // Add margin for border effects and safety
        val margin = maxOf(borderWidth, glowWidth) + 10f
        boundingBoxMinX = minX - margin
        boundingBoxMaxX = maxX + margin
        boundingBoxMinY = minY - margin
        boundingBoxMaxY = maxY + margin
        boundingBoxWidth = boundingBoxMaxX - boundingBoxMinX
        boundingBoxHeight = boundingBoxMaxY - boundingBoxMinY
    }

    fun updatePolygon(newPoints: List<Vector2f>, viewport: ViewportAPI, scale: Vector2f = Vector2f(1.0f, 1.0f)) {
        scaleX = scale.x
        scaleY = scale.y

        // Get viewport scale for world-to-screen conversion
        viewportScaleX = viewport.viewMult
        viewportScaleY = viewport.viewMult

        // Validate input points
        if (newPoints.size < 3) { // Need at least 3 points (6 coordinates)
            Global.getLogger(this.javaClass).warn("Invalid polygon - need at least 3 points, got ${newPoints.size / 2}")
            polygonPoints = listOf()
            return
        }

        // Store world coordinates directly - transformations will be done in vertex shader
        val worldPoints = mutableListOf<Float>()
        for (segment in newPoints) {
            worldPoints.add(segment.x)
            worldPoints.add(segment.y)
        }

        // Apply smoothing to world coordinates first
        val smoothedWorldPoints = when (smoothingMethod) {
            SmoothingMethod.SPLINE -> if (enableSplineSmoothing) {
                subdividePointsWithSplines(worldPoints, splineSubdivisions)
            } else {
                worldPoints
            }

            SmoothingMethod.SIMPLE -> {
                smoothEdgesSimple(worldPoints, splineSubdivisions)
            }

            SmoothingMethod.NONE -> {
                smoothEdgesMinimal(worldPoints, 2) // Minimal curve smoothing
            }
        }

        // Convert world points to screen coordinates for the fragment shader
        val screenPoints = mutableListOf<Float>()
        var sweepMinX = Float.MAX_VALUE
        var sweepMaxX = Float.MIN_VALUE

        // Get ship center in screen coordinates for consistent calculations
        val shipScreenX = viewport.convertWorldXtoScreenX(ship.location.x)
        val shipScreenY = viewport.convertWorldYtoScreenY(ship.location.y)

        for (i in smoothedWorldPoints.indices step 2) {
            val worldPoint = Vector2f(smoothedWorldPoints[i], smoothedWorldPoints[i + 1])
            val scaledPoint = Vector2f(worldPoint.x * scaleX, worldPoint.y * scaleY)
            val translatedPoint = Vector2f(scaledPoint.x + ship.location.x, scaledPoint.y + ship.location.y)
            val rotatedPoint = rotatePointByDegrees(translatedPoint, ship.location, ship.facing)

            // Convert to screen coordinates using viewport
            val screenX = viewport.convertWorldXtoScreenX(rotatedPoint.x)
            val screenY = viewport.convertWorldYtoScreenY(rotatedPoint.y)

            // Calculate ship-relative coordinates in screen space for sweep bounds
            val screenRelPoint = Vector2f(screenX - shipScreenX, screenY - shipScreenY)
            val facingRad = Math.toRadians(ship.facing.toDouble()).toFloat()
            val cosFacing = kotlin.math.cos(facingRad)
            val sinFacing = kotlin.math.sin(facingRad)
            val shipRelX = screenRelPoint.x * cosFacing + screenRelPoint.y * sinFacing

            sweepMinX = minOf(sweepMinX, shipRelX)
            sweepMaxX = maxOf(sweepMaxX, shipRelX)

            screenPoints.add(screenX)
            screenPoints.add(screenY)
        }

        // Calculate sweep position using the bounds we just calculated
        val sweepWidth = sweepMaxX - sweepMinX
        sweepPosition = sweepMaxX - (ship.phaseCloak?.effectLevel ?: 1f) * sweepWidth

        // Store the screen coordinates for the fragment shader
        polygonPoints = screenPoints

        // Shield bounds are now calculated inline above

        // Calculate bounding box for rendering optimization
        calculateBoundingBox()

        // Update quad vertices to match bounding box
        updateQuadVertices()
    }

    private fun subdividePointsWithSplines(points: List<Float>, subdivisions: Int = 8): List<Float> {
        val result = mutableListOf<Float>()
        val numPoints = points.size / 2

        if (numPoints < 3) return points.toList() // Need at least 3 points for a polygon

        // Convert to pairs for easier handling
        val pairs = points.chunked(2) { Pair(it[0], it[1]) }

        // Apply Laplacian smoothing to existing points (no new points added)
        val smoothedPoints = laplacianSmoothing(pairs, subdivisions)

        // Return smoothed points as flat list
        for (point in smoothedPoints) {
            result.add(point.first)
            result.add(point.second)
        }

        return result
    }

    private fun laplacianSmoothing(points: List<Pair<Float, Float>>, iterations: Int): List<Pair<Float, Float>> {
        if (points.size < 3) return points

        var currentPoints = points.toMutableList()

        for (iteration in 0 until iterations) {
            val smoothedPoints = mutableListOf<Pair<Float, Float>>()

            for (i in currentPoints.indices) {
                val prev = currentPoints[(i - 1 + currentPoints.size) % currentPoints.size]
                val current = currentPoints[i]
                val next = currentPoints[(i + 1) % currentPoints.size]

                // Laplacian smoothing: move point towards the average of its neighbors
                val smoothingFactor = 0.3f // Controls how much smoothing is applied
                val smoothedX = current.first + smoothingFactor * ((prev.first + next.first) / 2f - current.first)
                val smoothedY = current.second + smoothingFactor * ((prev.second + next.second) / 2f - current.second)

                smoothedPoints.add(Pair(smoothedX, smoothedY))
            }

            currentPoints = smoothedPoints
        }

        return currentPoints
    }

    fun updateColor(r: Int, g: Int, b: Int, a: Int) {
        colorRGBA = floatArrayOf(
            r / 255f,
            g / 255f,
            b / 255f,
            a / 100f
        )
    }

    fun updateZoomLevel(zoom: Float) {
        zoomLevel = zoom
    }

    fun updateScreenResolution(width: Float, height: Float) {
        screenWidth = width
        screenHeight = height
        // Recalculate quad vertices when screen resolution changes
        if (boundingBoxWidth > 0f && boundingBoxHeight > 0f) {
            updateQuadVertices()
        }
    }

    fun updateShipPosition() {
        // Don't recalculate bounding box here - it should be calculated with viewport in updatePolygon
        // Just update rotation matrix when ship moves
        updateRotationMatrix()
    }

    private fun updateRotationMatrix() {
        // Calculate rotation matrix for ship facing
        val facingRad = Math.toRadians(ship.facing.toDouble()).toFloat()
        val cosFacing = kotlin.math.cos(facingRad)
        val sinFacing = kotlin.math.sin(facingRad)

        // 2x2 rotation matrix: [cos -sin; sin cos]
        shipRotationMatrix[0] = cosFacing
        shipRotationMatrix[1] = -sinFacing
        shipRotationMatrix[2] = sinFacing
        shipRotationMatrix[3] = cosFacing
    }

    fun setDebugMode(enabled: Boolean) {
        debugMode = enabled
    }

    fun setBoundingBoxOptimization(enabled: Boolean) {
        enableBoundingBoxOptimization = enabled
    }

    fun setSplineSubdivisions(subdivisions: Int) {
        splineSubdivisions = subdivisions.coerceIn(1, 8) // Limit to reasonable range
    }

    fun getSplineSubdivisions(): Int {
        return splineSubdivisions
    }

    fun getSmoothingMethod(): SmoothingMethod {
        return smoothingMethod
    }

    // Alternative smoothing method: simple-edge smoothing by repositioning points
    private fun smoothEdgesSimple(points: List<Float>, subdivisions: Int = 2): List<Float> {
        val result = mutableListOf<Float>()
        val numPoints = points.size / 2

        if (numPoints < 3) return points.toList()

        // Convert to pairs for easier handling
        val pairs = points.chunked(2) { Pair(it[0], it[1]) }

        // Apply simple smoothing by moving points towards edge midpoints
        val smoothedPoints = simpleEdgeSmoothing(pairs, subdivisions)

        // Return smoothed points as a flat list
        for (point in smoothedPoints) {
            result.add(point.first)
            result.add(point.second)
        }

        return result
    }

    private fun simpleEdgeSmoothing(points: List<Pair<Float, Float>>, iterations: Int): List<Pair<Float, Float>> {
        if (points.size < 3) return points

        var currentPoints = points.toMutableList()

        for (iteration in 0 until iterations) {
            val smoothedPoints = mutableListOf<Pair<Float, Float>>()

            for (i in currentPoints.indices) {
                val current = currentPoints[i]
                val next = currentPoints[(i + 1) % currentPoints.size]

                // Move point slightly towards the midpoint of the edge
                val smoothingFactor = 0.2f // Gentle smoothing
                val edgeMidX = (current.first + next.first) / 2f
                val edgeMidY = (current.second + next.second) / 2f

                val smoothedX = current.first + smoothingFactor * (edgeMidX - current.first)
                val smoothedY = current.second + smoothingFactor * (edgeMidY - current.second)

                smoothedPoints.add(Pair(smoothedX, smoothedY))
            }

            currentPoints = smoothedPoints
        }

        return currentPoints
    }

    // Minimal smoothing method that creates very subtle shape smoothing
    private fun smoothEdgesMinimal(points: List<Float>, subdivisions: Int = 2): List<Float> {
        val result = mutableListOf<Float>()
        val numPoints = points.size / 2

        if (numPoints < 3) return points.toList()

        // Convert to pairs for easier handling
        val pairs = points.chunked(2) { Pair(it[0], it[1]) }

        // Apply very subtle smoothing by slightly adjusting point positions
        val smoothedPoints = minimalSmoothing(pairs, subdivisions)

        // Return smoothed points as a flat list
        for (point in smoothedPoints) {
            result.add(point.first)
            result.add(point.second)
        }

        return result
    }

    private fun minimalSmoothing(points: List<Pair<Float, Float>>, iterations: Int): List<Pair<Float, Float>> {
        if (points.size < 3) return points

        var currentPoints = points.toMutableList()

        for (iteration in 0 until iterations) {
            val smoothedPoints = mutableListOf<Pair<Float, Float>>()

            for (i in currentPoints.indices) {
                val prev = currentPoints[(i - 1 + currentPoints.size) % currentPoints.size]
                val current = currentPoints[i]
                val next = currentPoints[(i + 1) % currentPoints.size]

                // Very subtle smoothing: move point slightly towards the average of neighbors
                val smoothingFactor = 0.1f // Very gentle smoothing
                val avgX = (prev.first + next.first) / 2f
                val avgY = (prev.second + next.second) / 2f

                val smoothedX = current.first + smoothingFactor * (avgX - current.first)
                val smoothedY = current.second + smoothingFactor * (avgY - current.second)

                smoothedPoints.add(Pair(smoothedX, smoothedY))
            }

            currentPoints = smoothedPoints
        }

        return currentPoints
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
            // Scale speed inversely with viewport scale to maintain consistent visual speed
            val visualSpeed = speed / viewportScaleX
            ripple.radius += visualSpeed * deltaTime
            ripple.radius > ripple.maxDistance
        }
    }

    fun setHoneycombEnabled(enabled: Boolean) {
        honeycombEnabled = enabled
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

    fun updateTime(amount: Float) {
        this.time += amount
    }

    fun isPopWaveActive(): Boolean {
        return activePopWave !== null
    }

    fun drawShieldPop(): Boolean {
        val isDrawing = activePopWave != null

        return isDrawing
    }

    fun updateShieldHPPercent(percent: Float) {
        shieldHPPercent = percent
    }

    override fun render(viewport: ViewportAPI) {
        if (polygonPoints.isEmpty()) return

        // Update viewport scale for ripple calculations
        viewportScaleX = viewport.viewMult
        viewportScaleY = viewport.viewMult

        // Validate polygon before rendering
        if (polygonPoints.size < 6) {
            Global.getLogger(this.javaClass)
                .warn("Cannot render polygon with ${polygonPoints.size / 2} points (minimum 3 required)")
            return
        }

        // Ensure bounding box is up-to-date before rendering (if optimization is enabled)
        if (enableBoundingBoxOptimization) {
            // Don't recalculate bounding box here - it should already be calculated with viewport in updatePolygon
            // Just update quad vertices if needed
            if (boundingBoxWidth > 0f && boundingBoxHeight > 0f) {
                updateQuadVertices()
            }
        }

        // Ensure rotation matrix is up-to-date
        updateRotationMatrix()

        // Save current OpenGL state
        glPushAttrib(GL_ALL_ATTRIB_BITS)
        glPushMatrix()

        // Save current OpenGL state variables
        val currentProgram = glGetInteger(GL_CURRENT_PROGRAM)
        val currentVAO = glGetInteger(GL_VERTEX_ARRAY_BINDING)
        val currentVBO = glGetInteger(GL_ARRAY_BUFFER_BINDING)
        val currentTexture = glGetInteger(GL_TEXTURE_BINDING_2D)
        val blendEnabled = glIsEnabled(GL_BLEND)
        val depthTestEnabled = glIsEnabled(GL_DEPTH_TEST)
        val stencilTestEnabled = glIsEnabled(GL_STENCIL_TEST)
        val texture2DEnabled = glIsEnabled(GL_TEXTURE_2D)
        val scissorTestEnabled = glIsEnabled(GL_SCISSOR_TEST)

        try {
            // Scissor test disabled for debugging - only using bounding box quad optimization
            // The bounding box optimization is handled by the dynamic quad vertices

            // Use our shader program
            glUseProgram(shaderProgram)

            // Bind VAO
            glBindVertexArray(vao)

            // Set uniforms
            glUniform4f(
                glGetUniformLocation(shaderProgram, "shieldColor"),
                colorRGBA[0], colorRGBA[1], colorRGBA[2], colorRGBA[3]
            )
            glUniform4f(
                glGetUniformLocation(shaderProgram, "borderColor"),
                borderColorRGBA[0], borderColorRGBA[1], borderColorRGBA[2], borderColorRGBA[3]
            )
            glUniform1f(glGetUniformLocation(shaderProgram, "borderWidth"), borderWidth)
            glUniform1f(glGetUniformLocation(shaderProgram, "glowIntensity"), glowIntensity)
            glUniform1f(glGetUniformLocation(shaderProgram, "glowWidth"), glowWidth)
            glUniform1f(glGetUniformLocation(shaderProgram, "chargeProgress"), ship.phaseCloak?.effectLevel ?: 1f)
            glUniform1f(glGetUniformLocation(shaderProgram, "shieldSweepProgress"), ship.phaseCloak?.effectLevel ?: 1f)
            glUniform1f(glGetUniformLocation(shaderProgram, "sweepEdgeWidth"), 10.0f)
            glUniform1f(glGetUniformLocation(shaderProgram, "shieldHPPercent"), shieldHPPercent)
            glUniform1f(glGetUniformLocation(shaderProgram, "shipFacing"), ship.facing)

            // Set sweep position for fragment shader
            glUniform1f(glGetUniformLocation(shaderProgram, "sweepPosition"), sweepPosition)
            // Pass ship location in screen coordinates to match sweep bounds calculation
            val shipScreenX = viewport.convertWorldXtoScreenX(ship.location.x)
            val shipScreenY = viewport.convertWorldYtoScreenY(ship.location.y)
            glUniform2f(glGetUniformLocation(shaderProgram, "center"), shipScreenX, shipScreenY)
            glUniform1f(glGetUniformLocation(shaderProgram, "time"), time)

            // Set vertex shader uniforms for optimization
            glUniform2f(glGetUniformLocation(shaderProgram, "resolution"), screenWidth, screenHeight)
            glUniform2f(glGetUniformLocation(shaderProgram, "scale"), scaleX, scaleY)
            glUniform4f(
                glGetUniformLocation(shaderProgram, "shipRotationMatrix"),
                shipRotationMatrix[0], shipRotationMatrix[1], shipRotationMatrix[2], shipRotationMatrix[3]
            )
            glUniform2f(glGetUniformLocation(shaderProgram, "viewportScale"), viewportScaleX, viewportScaleY)

            // Set honeycomb uniforms
            glUniform1f(glGetUniformLocation(shaderProgram, "honeycombSize"), honeycombSize)
            glUniform1f(glGetUniformLocation(shaderProgram, "edgeWidth"), edgeWidth)

            glUniform1i(glGetUniformLocation(shaderProgram, "honeycombEnabled"), if (honeycombEnabled) 1 else 0)
            // Scale uniform is already set above for vertex shader
            glUniform1f(
                glGetUniformLocation(shaderProgram, "rotation"),
                Math.toRadians(ship.facing.toDouble()).toFloat()
            )

            // Set ripple uniforms
            glUniform1i(glGetUniformLocation(shaderProgram, "numRipples"), activeRipples.size)
            for (i in activeRipples.indices.take(10)) {
                val ripple = activeRipples[i]
                glUniform4f(
                    glGetUniformLocation(shaderProgram, "rippleColors[$i]"),
                    ripple.color[0], ripple.color[1], ripple.color[2], ripple.color[3]
                )
                // Convert ripple center to screen coordinates
                val rippleScreenX = viewport.convertWorldXtoScreenX(ripple.center.first)
                val rippleScreenY = viewport.convertWorldYtoScreenY(ripple.center.second)
                glUniform2f(
                    glGetUniformLocation(shaderProgram, "rippleCenters[$i]"),
                    rippleScreenX, rippleScreenY
                )
                // Convert ripple radius to screen coordinates
                val rippleScreenRadius = ripple.radius / viewport.viewMult
                glUniform1f(glGetUniformLocation(shaderProgram, "rippleRadii[$i]"), rippleScreenRadius)
                glUniform1f(glGetUniformLocation(shaderProgram, "rippleIntensities[$i]"), ripple.intensity)
                // Convert ripple width to screen coordinates
                val rippleScreenWidth = ripple.width / viewport.viewMult
                glUniform1f(glGetUniformLocation(shaderProgram, "rippleWidths[$i]"), rippleScreenWidth)
            }

            // Set pop wave uniforms
            glUniform1i(
                glGetUniformLocation(shaderProgram, "popWaveActive"),
                if (activePopWave != null) 1 else 0
            )
            activePopWave?.let { popWave ->
                glUniform1f(glGetUniformLocation(shaderProgram, "popWaveTime"), popWave.time)
                glUniform1f(glGetUniformLocation(shaderProgram, "popWaveMaxTime"), popWave.maxTime)
                glUniform4f(
                    glGetUniformLocation(shaderProgram, "popWaveColor"),
                    popWave.color[0], popWave.color[1], popWave.color[2], popWave.color[3]
                )
                glUniform4f(
                    glGetUniformLocation(shaderProgram, "popWaveDarkerColor"),
                    popWave.darkerColor[0], popWave.darkerColor[1], popWave.darkerColor[2], popWave.darkerColor[3]
                )
                glUniform1f(glGetUniformLocation(shaderProgram, "popWaveThickness"), popWave.thickness)
                glUniform1f(glGetUniformLocation(shaderProgram, "popWaveStartScale"), popWave.startScale)
                glUniform1f(glGetUniformLocation(shaderProgram, "popWaveEndScale"), popWave.endScale)
                glUniform1f(glGetUniformLocation(shaderProgram, "popWaveIntensity"), popWave.intensity)
                glUniform1f(glGetUniformLocation(shaderProgram, "popEffectRadius"), popEffectRadius)
            }

            // Resolution uniform is already set above for vertex shader

            // Set zoom level uniform
            glUniform1f(glGetUniformLocation(shaderProgram, "zoomLevel"), zoomLevel)

            // Set debug mode uniform
            glUniform1i(glGetUniformLocation(shaderProgram, "debugMode"), if (debugMode) 1 else 0)

            // Set bounding box uniforms for debug visualization
            glUniform2f(glGetUniformLocation(shaderProgram, "boundingBoxMin"), boundingBoxMinX, boundingBoxMinY)
            glUniform2f(glGetUniformLocation(shaderProgram, "boundingBoxMax"), boundingBoxMaxX, boundingBoxMaxY)

            // Convert polygon points to screen coordinates and pass to shader
            // Calculate the maximum number of points the shader can handle after smoothing
            val maxShaderPoints = calculateMaxPointsAfterSmoothing(boundsCount)

            // Set polygon points directly as uniforms
            setPolygonPointUniforms(maxShaderPoints)

            // Set debug polygon uniform
            glUniform1i(glGetUniformLocation(shaderProgram, "debugPolygon"), if (debugMode) 1 else 0)

            // Enable blending for transparency
            glEnable(GL_BLEND)
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)

            // Draw full-screen quad to render the points
            glDrawArrays(GL_TRIANGLE_FAN, 0, 4)

        } finally {
            // Restore OpenGL state variables
            glUseProgram(currentProgram)
            glBindVertexArray(currentVAO)
            glBindBuffer(GL_ARRAY_BUFFER, currentVBO)
            glBindTexture(GL_TEXTURE_2D, currentTexture)

            // Restore scissor test state
            if (scissorTestEnabled) {
                glEnable(GL_SCISSOR_TEST)
            } else {
                glDisable(GL_SCISSOR_TEST)
            }

            // Restore enabled states
            if (blendEnabled) glEnable(GL_BLEND) else glDisable(GL_BLEND)
            if (depthTestEnabled) glEnable(GL_DEPTH_TEST) else glDisable(GL_DEPTH_TEST)
            if (stencilTestEnabled) glEnable(GL_STENCIL_TEST) else glDisable(GL_STENCIL_TEST)
            if (texture2DEnabled) glEnable(GL_TEXTURE_2D) else glDisable(GL_TEXTURE_2D)

            // Restore OpenGL state
            glPopMatrix()
            glPopAttrib()
        }
    }

    override fun cleanup() {
        try {
            // Clean up OpenGL resources
            if (vbo != 0) {
                glDeleteBuffers(vbo)
                vbo = 0
            }
            if (vao != 0) {
                glDeleteVertexArrays(vao)
                vao = 0
            }
            if (shaderProgram != 0) {
                glDeleteProgram(shaderProgram)
                shaderProgram = 0
            }
        } catch (e: Exception) {
            Global.getLogger(this.javaClass).error("Error during cleanup: ${e.message}")
        }
    }
}

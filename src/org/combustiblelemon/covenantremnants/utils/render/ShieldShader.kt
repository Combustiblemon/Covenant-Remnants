package org.combustiblelemon.covenantremnants.utils.render

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ViewportAPI
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL15.*
import org.lwjgl.opengl.GL20.*
import org.lwjgl.opengl.GL30.*
import org.lwjgl.util.vector.Vector2f
import kotlin.math.round
import org.combustiblelemon.covenantremnants.utils.Utils

enum class SmoothingMethod {
    SIMPLE,   // Uses edge smoothing by repositioning points
    SPLINE,   // Uses Laplacian smoothing for natural shape smoothing
    NONE      // Uses minimal smoothing for very subtle adjustments
}

class ShieldShader(
    private val ship: ShipAPI,
    private var shipDimensions: Array<Vector2f>,
    private var popEffectRadius: Float = 500f,  // Default pop effect radius
    private val boundsCount: Int,
    private val smoothingMethod: SmoothingMethod = SmoothingMethod.NONE
) {
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

    // Shader source code
    private val vertexShaderSource = """
        #version 330 core
        layout (location = 0) in vec2 aPos;

        out vec2 TexCoord;

        void main() {
            // Pass through the position directly (already in NDC space)
            gl_Position = vec4(aPos, 0.0, 1.0);
            // Convert NDC to texture coordinates (0 to 1)
            TexCoord = aPos * 0.5 + 0.5;
        }
    """.trimIndent()

    private val fragmentShaderSource = """
        #version 330 core
        out vec4 FragColor;

        in vec2 TexCoord;

        // Uniforms
        uniform vec4 shieldColor;
        uniform vec4 borderColor;
        uniform float borderWidth;
        uniform float glowIntensity;
        uniform float glowWidth;
        uniform float chargeProgress;
        uniform float shipFacing;
        uniform vec2 shipDimensions;
        uniform vec2 center;
        uniform float time;
        uniform float hexSize;
        uniform float hexRotation;
        uniform float hexSpacing;
        uniform float hexEdgeWidth;
        uniform float hexSpacingMultiplier;
        uniform vec2 scale;
        uniform float rotation;

        // Ripple uniforms
        uniform int numRipples;
        uniform vec4 rippleColors[10];
        uniform vec2 rippleCenters[10];
        uniform float rippleRadii[10];
        uniform float rippleIntensities[10];

        // Pop wave uniforms
        uniform bool popWaveActive;
        uniform float popWaveTime;
        uniform float popWaveMaxTime;
        uniform vec4 popWaveColor;
        uniform vec4 popWaveDarkerColor;
        uniform float popWaveThickness;
        uniform float popWaveStartScale;
        uniform float popWaveEndScale;
        uniform float popWaveIntensity;
        uniform float popEffectRadius;

        // Polygon points as individual uniform arrays - no UBO needed
        uniform vec2 points[POINT_COUNT_PLACEHOLDER];

        uniform bool debugPolygon;    // Debug flag for polygon rendering

        uniform vec2 resolution;   // Screen resolution
        uniform float zoomLevel;   // Viewport zoom level (viewMult)
        uniform bool debugMode;    // Debug mode to show polygon outline

                                // Helper function to calculate distance from point to line segment
        float distanceToLine(vec2 point, vec2 lineStart, vec2 lineEnd) {
            vec2 line = lineEnd - lineStart;
            vec2 pointToStart = point - lineStart;
            float t = clamp(dot(pointToStart, line) / dot(line, line), 0.0, 1.0);
            vec2 closest = lineStart + t * line;
            return distance(point, closest);
        }

                // Point-in-polygon test using ray casting algorithm
        bool isPointInPolygon(vec2 point) {
            // Use the fixed array size from POINT_COUNT_PLACEHOLDER
            const int numPoints = POINT_COUNT_PLACEHOLDER;
            if (numPoints < 3) return false;

            bool inside = false;
            int j = numPoints - 1;

            for (int i = 0; i < numPoints; i++) {
                // Check if point is on the same side of the edge as the ray
                if (((points[i].y > point.y) != (points[j].y > point.y)) &&
                    (point.x < (points[j].x - points[i].x) * (point.y - points[i].y) / (points[j].y - points[i].y) + points[i].x)) {
                    inside = !inside;
                }
                j = i;
            }

            return inside;
        }

        vec4 mainShield(vec2 pixelPos) {
            // Calculate shield color with charge progress
            vec4 finalShieldColor = shieldColor;

            // Apply border effect
            float minDistToEdge = 1000.0;
            const int numPoints = POINT_COUNT_PLACEHOLDER;
            int j = numPoints - 1;

            for(int i = 0; i < numPoints; i++) {
                float dist = distanceToLine(pixelPos, points[i].xy, points[j].xy);
                minDistToEdge = min(minDistToEdge, dist);
                j = i;
            }

            // Apply border
            if (minDistToEdge < borderWidth) {
                float borderFactor = 1.0 - (minDistToEdge / borderWidth);
                return mix(finalShieldColor, borderColor, borderFactor);
            } else {
                return finalShieldColor;
            }
        }

        void main() {
            // Convert texture coordinates to screen pixel coordinates
            vec2 pixelPos = TexCoord * resolution;

            // Debug mode: show polygon outline and points
            if(debugMode) {
                const int numPoints = POINT_COUNT_PLACEHOLDER;
                // Show all polygon points as small circles (only valid points)
                for(int i = 0; i < numPoints; i++) {
                    float dist = distance(pixelPos, points[i].xy);
                    if(dist < 2.0) {
                        FragColor = vec4(0.0, 1.0, 0.0, 1.0); // Green dots for points
                        return;
                    }
                }

                // Check if pixel is near any polygon edge (only valid edges)
                float minDist = 1000.0;
                int j = numPoints - 1;

                for(int i = 0; i < numPoints; i++) {
                    float dist = distanceToLine(pixelPos, points[i].xy, points[j].xy);
                    minDist = min(minDist, dist);
                    j = i;
                }

                if(minDist < 2.0) {
                    FragColor = vec4(1.0, 0.0, 1.0, 1.0); // Magenta outline
                    return;
                }
            }

            // Check if the current pixel is inside the polygon
            if(isPointInPolygon(pixelPos)) {
                FragColor = mainShield(pixelPos);
            } else {
                // Outside polygon - discard or make transparent
                discard;
            }
        }
    """.trimIndent()

    init {
        init()
        initShaders()
    }

    private fun init() {
        // Create VAO
        vao = glGenVertexArrays()
        glBindVertexArray(vao)

        // Create VBO with full-screen quad vertices in NDC space (-1 to 1)
        val vertices = floatArrayOf(
            -1.0f, -1.0f,  // Bottom-left
            1.0f, -1.0f,  // Bottom-right
            1.0f, 1.0f,  // Top-right
            -1.0f, 1.0f   // Top-left
        )

        vbo = glGenBuffers()
        glBindBuffer(GL_ARRAY_BUFFER, vbo)

        // Convert float array to FloatBuffer
        val buffer = BufferUtils.createFloatBuffer(vertices.size)
        buffer.put(vertices)
        buffer.flip()

        glBufferData(GL_ARRAY_BUFFER, buffer, GL_STATIC_DRAW)

        // Set up vertex attributes
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 2 * 4, 0)
        glEnableVertexAttribArray(0)

        glBindVertexArray(0)

        // No UBO needed - using direct uniforms instead
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
                Global.getLogger(this.javaClass)
                    .debug("SPLINE calculation: originalPoints=$originalPointCount, using Laplacian smoothing (no new points)")
                originalPointCount
            }

            SmoothingMethod.SIMPLE -> {
                // Simple smoothing: edge smoothing - no new points, just repositioned
                Global.getLogger(this.javaClass)
                    .debug("SIMPLE calculation: originalPoints=$originalPointCount, using edge smoothing (no new points)")
                originalPointCount
            }

            SmoothingMethod.NONE -> {
                // Minimal smoothing: very subtle smoothing - no new points, just repositioned
                Global.getLogger(this.javaClass)
                    .debug("NONE calculation: originalPoints=$originalPointCount, using minimal smoothing (no new points)")
                originalPointCount
            }
        }

        // Ensure we don't exceed reasonable bounds (safety check)
        val finalResult = minOf(maxPoints, 1000) // Maximum 1000 points for shader array
        Global.getLogger(this.javaClass).debug("Final calculation result: $finalResult")
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

        // Set actual polygon points as uniforms
        for (i in 0 until safePointCount) {
            val x = round(polygonPoints[i * 2])
            val y = round(polygonPoints[i * 2 + 1])
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

    fun updatePolygon(newPoints: List<Vector2f>, viewport: ViewportAPI, scale: Vector2f = Vector2f(1.0f, 1.0f) ) {
        scaleX = scale.x
        scaleY = scale.y

        // Validate input points
        if (newPoints.size < 3) { // Need at least 3 points (6 coordinates)
            Global.getLogger(this.javaClass).warn("Invalid polygon - need at least 3 points, got ${newPoints.size / 2}")
            polygonPoints = listOf()
            return
        }

        val vertexData = mutableListOf<Float>()


        for (segment in newPoints) {
            val scaledPoint = Vector2f(segment.x * scale.x, segment.y * scale.y)

            val p1 = Utils.rotatePointByDegrees(
                Vector2f(scaledPoint.x + ship.location.x, scaledPoint.y + ship.location.y),
                ship.location,
                ship.facing
            )

            vertexData.add(viewport.convertWorldXtoScreenX(p1.x))
            vertexData.add(viewport.convertWorldYtoScreenY(p1.y))
        }

        // Apply scaling to the polygon points relative to the ship center
        val scaledPoints = mutableListOf<Float>()

        for (i in vertexData.indices step 2) {
            val x = vertexData[i]
            val y = vertexData[i + 1]

            // Scale the points relative to the ship center
            val scaledX = ship.location.x + (x - ship.location.x)
            val scaledY = ship.location.y + (y - ship.location.y)

            scaledPoints.add(scaledX)
            scaledPoints.add(scaledY)
        }

        // Apply smoothing to create smooth edges (optional)
        val finalPoints = when (smoothingMethod) {
            SmoothingMethod.SPLINE -> if (enableSplineSmoothing) {
                Global.getLogger(this.javaClass)
                    .debug("SPLINE smoothing enabled: splineSubdivisions=$splineSubdivisions")
                subdividePointsWithSplines(scaledPoints, splineSubdivisions)
            } else {
                Global.getLogger(this.javaClass).debug("SPLINE smoothing disabled")
                scaledPoints
            }

            SmoothingMethod.SIMPLE -> {
                Global.getLogger(this.javaClass).debug("SIMPLE smoothing enabled: subdivisions=$splineSubdivisions")
                smoothEdgesSimple(scaledPoints, splineSubdivisions)
            }

            SmoothingMethod.NONE -> {
                Global.getLogger(this.javaClass).debug("NONE smoothing enabled: using minimal curves")
                smoothEdgesMinimal(scaledPoints, 2) // Minimal curve smoothing
            }
        }

        polygonPoints = finalPoints
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

    fun setDebugMode(enabled: Boolean) {
        debugMode = enabled
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

    // Alternative smoothing method: simple edge smoothing by repositioning points
    private fun smoothEdgesSimple(points: List<Float>, subdivisions: Int = 2): List<Float> {
        val result = mutableListOf<Float>()
        val numPoints = points.size / 2

        if (numPoints < 3) return points.toList()

        // Convert to pairs for easier handling
        val pairs = points.chunked(2) { Pair(it[0], it[1]) }

        // Apply simple smoothing by moving points towards edge midpoints
        val smoothedPoints = simpleEdgeSmoothing(pairs, subdivisions)

        // Return smoothed points as flat list
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

        // Return smoothed points as flat list
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
            ripple.radius += speed * deltaTime
            ripple.radius > ripple.maxDistance
        }
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

    fun drawShieldPop(): Boolean {
        val isDrawing = activePopWave != null

        return isDrawing
    }

    fun drawPolygon() {
        if (polygonPoints.isEmpty()) return

        // Validate polygon before rendering
        if (polygonPoints.size < 6) {
            Global.getLogger(this.javaClass)
                .warn("Cannot render polygon with ${polygonPoints.size / 2} points (minimum 3 required)")
            return
        }

        // Save current OpenGL state
        glPushAttrib(GL_ALL_ATTRIB_BITS)
        glPushMatrix()

        // Save current shader program
        val currentProgram = glGetInteger(GL_CURRENT_PROGRAM)

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
        glUniform1f(glGetUniformLocation(shaderProgram, "shipFacing"), ship.facing)
        glUniform2f(
            glGetUniformLocation(shaderProgram, "shipDimensions"),
            shipDimensions[0].x, shipDimensions[0].y
        )
        glUniform2f(glGetUniformLocation(shaderProgram, "center"), ship.location.x, ship.location.y)
        glUniform1f(glGetUniformLocation(shaderProgram, "time"), hexAnimationTime)
        glUniform1f(glGetUniformLocation(shaderProgram, "hexSize"), hexSize)
        glUniform1f(glGetUniformLocation(shaderProgram, "hexRotation"), hexRotation)
        glUniform1f(glGetUniformLocation(shaderProgram, "hexSpacing"), hexSpacing)
        glUniform1f(glGetUniformLocation(shaderProgram, "hexEdgeWidth"), hexEdgeWidth)
        glUniform1f(glGetUniformLocation(shaderProgram, "hexSpacingMultiplier"), hexSpacingMultiplier)
        glUniform2f(glGetUniformLocation(shaderProgram, "scale"), scaleX, scaleY)
        glUniform1f(glGetUniformLocation(shaderProgram, "rotation"), Math.toRadians(ship.facing.toDouble()).toFloat())

        // Set ripple uniforms
        glUniform1i(glGetUniformLocation(shaderProgram, "numRipples"), activeRipples.size)
        for (i in activeRipples.indices.take(10)) {
            val ripple = activeRipples[i]
            glUniform4f(
                glGetUniformLocation(shaderProgram, "rippleColors[$i]"),
                ripple.color[0], ripple.color[1], ripple.color[2], ripple.color[3]
            )
            glUniform2f(
                glGetUniformLocation(shaderProgram, "rippleCenters[$i]"),
                ripple.center.first, ripple.center.second
            )
            glUniform1f(glGetUniformLocation(shaderProgram, "rippleRadii[$i]"), ripple.radius)
            glUniform1f(glGetUniformLocation(shaderProgram, "rippleIntensities[$i]"), ripple.intensity)
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

        // Set resolution uniform
        glUniform2f(glGetUniformLocation(shaderProgram, "resolution"), screenWidth, screenHeight)

        // Set zoom level uniform
        glUniform1f(glGetUniformLocation(shaderProgram, "zoomLevel"), zoomLevel)

        // Set debug mode uniform
        glUniform1i(glGetUniformLocation(shaderProgram, "debugMode"), if (debugMode) 1 else 0)

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

        // Restore previous shader program
        glUseProgram(currentProgram)

        // Restore OpenGL state
        glPopMatrix()
        glPopAttrib()

        cleanUp()
    }

    private fun cleanUp() {
        glDisable(GL_STENCIL_TEST)
        glEnable(GL_TEXTURE_2D)
        glDisable(GL_BLEND)
        glDisable(GL_POLYGON_SMOOTH)
        glDisable(GL_LINE_SMOOTH)
    }

    fun cleanup() {
        glDeleteBuffers(vbo)
        glDeleteVertexArrays(vao)
        // No UBO to delete - using direct uniforms instead
        glDeleteProgram(shaderProgram)
    }
}

package org.combustiblelemon.covenantremnants.utils.render

import com.fs.starfarer.api.Global
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.ARBUniformBufferObject.GL_INVALID_INDEX
import org.lwjgl.opengl.ARBUniformBufferObject.GL_UNIFORM_BUFFER
import org.lwjgl.opengl.ARBUniformBufferObject.glGetUniformBlockIndex
import org.lwjgl.opengl.ARBUniformBufferObject.glUniformBlockBinding
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL15.*
import org.lwjgl.opengl.GL20.*
import org.lwjgl.opengl.GL30.*
import org.lwjgl.util.glu.GLU
import org.lwjgl.util.glu.GLUtessellator
import org.lwjgl.util.glu.GLUtessellatorCallbackAdapter
import org.lwjgl.util.vector.Vector2f
import kotlin.math.abs
import kotlin.math.min

class ShieldShader(
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
    private var vao: Int = 0
    private var shaderProgram: Int = 0
    private var pointsUBO: Int = 0
    private var scaleX: Float = 1.0f
    private var scaleY: Float = 1.0f
    private var rotation: Float = 0f
    private var centerX: Float = 0f
    private var centerY: Float = 0f
    private var chargeProgress: Float = 1f
    private var shipFacing: Float = 0f
    private var screenWidth: Float = 1920f
    private var screenHeight: Float = 1080f
    private var zoomLevel: Float = 1f
    private var splineSubdivisions: Int = 8
    private var debugMode: Boolean = false
    private var enableSplineSmoothing: Boolean = true
    private var smoothingMethod: String = "spline" // "spline", "simple", or "none"

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

                                // Polygon points uniform buffer - size will be set dynamically
        layout(std140) uniform PointsBuffer {
            vec2 points[1000];  // Size will be controlled by pointCount
        };

        uniform int pointCount;    // Number of points to draw
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

                                                        // Function to check if a point is inside a polygon
        bool isPointInPolygon(vec2 point, int pointCount) {
            // Ray casting algorithm - more reliable for complex polygons
            if(pointCount <= 0) {
                return false; // No valid points
            }

            bool inside = false;
            int j = pointCount - 1;

            for(int i = 0; i < pointCount; i++) {
                // Skip NaN points (sentinel values)
                if(points[i].x != points[i].x || points[i].y != points[i].y ||
                   points[j].x != points[j].x || points[j].y != points[j].y) {
                    j = i;
                    continue;
                }

                // Skip if points are too close together (prevents edge artifacts)
                float dx = points[j].x - points[i].x;
                float dy = points[j].y - points[i].y;
                if(abs(dx) < 0.001 && abs(dy) < 0.001) {
                    j = i;
                    continue;
                }

                // Ray casting algorithm: check if ray from point crosses edge
                if(((points[i].y > point.y) != (points[j].y > point.y)) &&
                   (point.x < (points[j].x - points[i].x) * (point.y - points[i].y) / (points[j].y - points[i].y) + points[i].x)) {
                    inside = !inside;
                }
                j = i;
            }

            return inside;
        }

        void main() {
            // Convert texture coordinates to screen pixel coordinates
            vec2 pixelPos = TexCoord * resolution;

                                                                                        // Debug mode: show polygon outline and points
        if(debugMode) {
            if(pointCount <= 0) {
                return; // No valid points
            }

                        // Show all polygon points as small circles (only valid points)
            for(int i = 0; i < pointCount; i++) {
                // CRITICAL: Bounds check to prevent accessing beyond our valid data
                if(i >= pointCount) {
                    break; // Stop if we're beyond our array bounds
                }

                // Skip NaN points (sentinel values) - use a more reliable check
                if(points[i].x != points[i].x || points[i].y != points[i].y) {
                    continue;
                }

                // DEBUG: Check if this point is at (0,0) - this shouldn't happen
                if(abs(points[i].x) < 0.1 && abs(points[i].y) < 0.1) {
                    // If we find a point at (0,0), draw it in red to highlight the problem
                    float dist = distance(pixelPos, points[i]);
                    if(dist < 3.0) {
                        FragColor = vec4(1.0, 0.0, 0.0, 1.0); // Red dot for (0,0) points
                        return;
                    }
                }

                // DEBUG: Print point coordinates for debugging (this will show in console)
                // We can't use println in shaders, but we can use the debug color to identify specific points
                if(i == 0) {
                    // First point - draw in cyan to identify it
                    float dist = distance(pixelPos, points[i]);
                    if(dist < 3.0) {
                        FragColor = vec4(0.0, 1.0, 1.0, 1.0); // Cyan dot for first point
                        return;
                    }
                }

                float dist = distance(pixelPos, points[i]);
                if(dist < 3.0) {
                    FragColor = vec4(0.0, 1.0, 0.0, 1.0); // Green dots for points
                    return;
                }
            }

            // DEBUG: Also check if we're somehow accessing beyond pointCount
            // This would indicate the shader is reading garbage memory
            for(int i = pointCount; i < pointCount + 10; i++) {
                if(i >= 1000) break; // Safety check
                float dist = distance(pixelPos, points[i]);
                if(dist < 3.0) {
                    // If we find points beyond pointCount, draw them in blue
                    FragColor = vec4(0.0, 0.0, 1.0, 1.0); // Blue dots for garbage data
                    return;
                }
            }

                                // Check if pixel is near any polygon edge (only valid edges)
                float minDist = 1000.0;
                int j = pointCount - 1;

                for(int i = 0; i < pointCount; i++) {
                    // Skip NaN points (sentinel values)
                    if(points[i].x != points[i].x || points[i].y != points[i].y ||
                       points[j].x != points[j].x || points[j].y != points[j].y) {
                        j = i;
                        continue;
                    }

                    // Skip if points are too close together (prevents edge artifacts)
                    float dx = points[j].x - points[i].x;
                    float dy = points[j].y - points[i].y;
                    if(abs(dx) < 0.001 && abs(dy) < 0.001) {
                        j = i;
                        continue;
                    }

                    float dist = distanceToLine(pixelPos, points[i], points[j]);
                    minDist = min(minDist, dist);
                    j = i;
                }

                if(minDist < 2.0) {
                    FragColor = vec4(1.0, 0.0, 1.0, 1.0); // Magenta outline
                    return;
                }
            }

                    // Check if the current pixel is inside the polygon
        if(isPointInPolygon(pixelPos, pointCount)) {
            FragColor = shieldColor; // Fill the polygon with shield color
        } else {
            discard; // Don't render outside the polygon
        }
        }
    """.trimIndent()

    init {
        init()
        initTessellator()
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

        // Initialize UBO for polygon points
        initPointsUBO()
    }

    private fun initPointsUBO() {
        pointsUBO = glGenBuffers()
        // Don't allocate buffer yet - wait until we know how many points we have
        glBindBufferBase(GL_UNIFORM_BUFFER, 0, pointsUBO)
        glBindBuffer(GL_UNIFORM_BUFFER, 0)
    }

    private fun updatePointsUBO(pointCount: Int) {
        glBindBuffer(GL_UNIFORM_BUFFER, pointsUBO)

        // Create buffer with the full size the shader expects (1000 points * 2 coordinates)
        val buffer = BufferUtils.createFloatBuffer(1000 * 2) // Full shader array size

        // Add all our actual polygon points
        for (i in 0 until pointCount) {
            buffer.put(polygonPoints[i * 2])
            buffer.put(polygonPoints[i * 2 + 1])
        }

        // Fill the remaining slots with a sentinel value (like NaN) to prevent (0,0) from appearing
        for (i in pointCount until 1000) {
            buffer.put(Float.NaN)
            buffer.put(Float.NaN)
        }

        buffer.flip()

        // Create the UBO buffer with the full size
        glBufferData(GL_UNIFORM_BUFFER, 1000 * 2 * 4L, GL_DYNAMIC_DRAW)

        // Put our actual data directly
        glBufferSubData(GL_UNIFORM_BUFFER, 0, buffer)

        // Bind the UBO to binding point 0 again after creating the buffer
        glBindBufferBase(GL_UNIFORM_BUFFER, 0, pointsUBO)
        glBindBuffer(GL_UNIFORM_BUFFER, 0)

        // Debug: Print what was sent to UBO
        if (debugMode) {
            println("UBO Debug: Sent ${pointCount} points to shader")
            println("First UBO point: (${polygonPoints[0]}, ${polygonPoints[1]})")
            println("Last UBO point: (${polygonPoints[(pointCount - 1) * 2]}, ${polygonPoints[(pointCount - 1) * 2 + 1]})")
            println("UBO buffer size: ${buffer.remaining() * 4} bytes")

            // Print all points to see if any are at (0,0)
            println("All UBO points:")
            for (i in 0 until pointCount) {
//                val x = polygonPoints[i * 2]
//                val y = polygonPoints[i * 2 + 1]
//                if (abs(x) < 0.1 && abs(y) < 0.1) {
//                    println("  Point $i: ($x, $y) - WARNING: This is at (0,0)!")
//                } else {
//                    println("  Point $i: ($x, $y)")
//                }
            }
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

        // Create and compile fragment shader
        val fragmentShader = glCreateShader(GL_FRAGMENT_SHADER)
        glShaderSource(fragmentShader, fragmentShaderSource)
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

        // Bind UBO to shader program
        val blockIndex = glGetUniformBlockIndex(shaderProgram, "PointsBuffer")
        if (blockIndex != GL_INVALID_INDEX) {
            glUniformBlockBinding(shaderProgram, blockIndex, 0)
        }
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

    fun updatePolygon(newPoints: List<Float>, scale: Vector2f) {
        scaleX = scale.x
        scaleY = scale.y

        // Validate input points
        if (newPoints.size < 6) { // Need at least 3 points (6 coordinates)
            println("WARNING: Invalid polygon - need at least 3 points, got ${newPoints.size / 2}")
            polygonPoints = listOf()
            return
        }

        // Apply scaling to the polygon points relative to the ship center
        val scaledPoints = mutableListOf<Float>()

        // Calculate the center of the polygon points
        var centerX = 0f
        var centerY = 0f
        for (i in newPoints.indices step 2) {
            centerX += newPoints[i]
            centerY += newPoints[i + 1]
        }
        centerX /= (newPoints.size / 2)
        centerY /= (newPoints.size / 2)

        for (i in newPoints.indices step 2) {
            val x = newPoints[i]
            val y = newPoints[i + 1]

            // Scale the points relative to the polygon center
            val scaledX = centerX + (x - centerX) * scale.x
            val scaledY = centerY + (y - centerY) * scale.y

            scaledPoints.add(scaledX)
            scaledPoints.add(scaledY)
        }

        // Apply smoothing to create smooth edges (optional)
        val finalPoints = when (smoothingMethod) {
            "spline" -> if (enableSplineSmoothing) subdividePointsWithSplines(
                scaledPoints,
                splineSubdivisions
            ) else scaledPoints

            "simple" -> smoothEdgesSimple(scaledPoints, splineSubdivisions)
            "none" -> scaledPoints
            else -> scaledPoints
        }

        // Clean up duplicate or very close points that could cause rendering issues
        val cleanedPoints = cleanDuplicatePoints(finalPoints)

        // Ensure polygon is closed by adding first point at the end if it's not already there
        val closedPoints = mutableListOf<Float>()
        closedPoints.addAll(cleanedPoints)

        if (cleanedPoints.isNotEmpty()) {
            val firstX = cleanedPoints[0]
            val firstY = cleanedPoints[1]
            val lastX = cleanedPoints[cleanedPoints.size - 2]
            val lastY = cleanedPoints[cleanedPoints.size - 1]

            // Only add closing point if the first and last points are different
            // This prevents duplicate points that could cause rendering issues
            if (abs(firstX - lastX) > 0.001f || abs(firstY - lastY) > 0.001f) {
                closedPoints.add(firstX)
                closedPoints.add(firstY)
            }
        }

        // Debug: Print point counts and first/last points
        if (debugMode) {
            println("Original points: ${newPoints.size / 2}")
            println("Scaled points: ${scaledPoints.size / 2}")
            println("Final points after smoothing: ${finalPoints.size / 2}")
            println("Cleaned points: ${cleanedPoints.size / 2}")
            println("Closed points: ${closedPoints.size / 2}")

            if (closedPoints.isNotEmpty()) {
                println("First point: (${closedPoints[0]}, ${closedPoints[1]})")
                println("Last point: (${closedPoints[closedPoints.size - 2]}, ${closedPoints[closedPoints.size - 1]})")

                // Check if polygon is properly closed
                val firstX = closedPoints[0]
                val firstY = closedPoints[1]
                val lastX = closedPoints[closedPoints.size - 2]
                val lastY = closedPoints[closedPoints.size - 1]

                if (abs(firstX - lastX) < 0.001f && abs(firstY - lastY) < 0.001f) {
                    println("Polygon is properly closed")
                } else {
                    println("WARNING: Polygon is NOT properly closed!")
                    println("  First: ($firstX, $firstY)")
                    println("  Last: ($lastX, $lastY)")
                }
            }
        }

        polygonPoints = closedPoints
    }

    // Catmull-Rom spline interpolation for smooth edges
    private fun catmullRomSpline(
        p0: Pair<Float, Float>, p1: Pair<Float, Float>,
        p2: Pair<Float, Float>, p3: Pair<Float, Float>, t: Float
    ): Pair<Float, Float> {
        // Catmull-Rom matrix coefficients
        val t2 = t * t
        val t3 = t2 * t

        val c0 = -0.5f * t3 + t2 - 0.5f * t
        val c1 = 1.5f * t3 - 2.5f * t2 + 1.0f
        val c2 = -1.5f * t3 + 2.0f * t2 + 0.5f * t
        val c3 = 0.5f * t3 - 0.5f * t2

        val x = c0 * p0.first + c1 * p1.first + c2 * p2.first + c3 * p3.first
        val y = c0 * p0.second + c1 * p1.second + c2 * p2.second + c3 * p3.second

        return Pair(x, y)
    }

    private fun subdividePointsWithSplines(points: List<Float>, subdivisions: Int = 8): List<Float> {
        val result = mutableListOf<Float>()
        val numPoints = points.size / 2

        if (numPoints < 3) return points.toList() // Need at least 3 points for a polygon

        // Convert to pairs for easier handling
        val pairs = points.chunked(2) { Pair(it[0], it[1]) }

        for (i in 0 until numPoints) {
            val p1 = pairs[i]
            val p2 = pairs[(i + 1) % numPoints]

            // Add original point
            result.add(p1.first)
            result.add(p1.second)

            // Add interpolated points along the edge (but not too many)
            val edgeSubdivisions = min(subdivisions, 4) // Limit to prevent over-subdivision
            for (j in 1 until edgeSubdivisions) {
                val t = j.toFloat() / edgeSubdivisions
                // Simple linear interpolation along the edge
                val interpolatedX = p1.first + (p2.first - p1.first) * t
                val interpolatedY = p1.second + (p2.second - p1.second) * t
                result.add(interpolatedX)
                result.add(interpolatedY)
            }
        }

        return result
    }

    fun updateColor(r: Int, g: Int, b: Int, a: Int) {
        colorRGBA = floatArrayOf(
            r / 255f,
            g / 255f,
            b / 255f,
            a / 100f
        )
    }

    fun updateScreenResolution(width: Float, height: Float) {
        screenWidth = width
        screenHeight = height
    }

    fun updateZoomLevel(zoom: Float) {
        zoomLevel = zoom
    }

    fun updateSplineSubdivisions(subdivisions: Int) {
        splineSubdivisions = subdivisions.coerceAtLeast(1)
    }

    fun setDebugMode(enabled: Boolean) {
        debugMode = enabled
    }

    fun printPolygonPoints() {
        println("Polygon points (${polygonPoints.size / 2} points):")
        for (i in polygonPoints.indices step 2) {
            println("  Point ${i / 2}: (${polygonPoints[i]}, ${polygonPoints[i + 1]})")
        }
    }

    fun setSplineSmoothing(enabled: Boolean) {
        enableSplineSmoothing = enabled
    }

    fun setSmoothingMethod(method: String) {
        smoothingMethod = when (method.lowercase()) {
            "spline", "catmull-rom" -> "spline"
            "simple", "linear" -> "simple"
            "none", "off" -> "none"
            else -> "none"
        }
    }

    fun getPointCount(): Int {
        return polygonPoints.size / 2
    }

    fun getMaxShaderPoints(): Int {
        return 1000  // Match the shader uniform array size
    }

        fun debugPolygonPoints() {
        println("=== Polygon Points Debug ===")
        println("Total points: ${polygonPoints.size / 2}")

        if (polygonPoints.isNotEmpty()) {
            for (i in polygonPoints.indices step 2) {
                val x = polygonPoints[i]
                val y = polygonPoints[i + 1]
                val pointIndex = i / 2

                // Highlight the first and last points
                when (pointIndex) {
                    0 -> println("Point ${pointIndex}: ($x, $y) [FIRST]")
                    polygonPoints.size / 2 - 1 -> println("Point ${pointIndex}: ($x, $y) [LAST]")
                    else -> println("Point ${pointIndex}: ($x, $y)")
                }
            }

            // Check polygon closure
            val firstX = polygonPoints[0]
            val firstY = polygonPoints[1]
            val lastX = polygonPoints[polygonPoints.size - 2]
            val lastY = polygonPoints[polygonPoints.size - 1]

            println("Polygon closure check:")
            println("  First point: ($firstX, $firstY)")
            println("  Last point: ($lastX, $lastY)")
            println("  Distance: ${kotlin.math.sqrt((firstX - lastX) * (firstX - lastX) + (firstY - lastY) * (firstY - lastY))}")

            if (abs(firstX - lastX) < 0.001f && abs(firstY - lastY) < 0.001f) {
                println("  ✓ Polygon is properly closed")
            } else {
                println("  ✗ Polygon is NOT properly closed!")
            }

            // Check polygon winding order
            println("Polygon winding order:")
            if (isPolygonCounterClockwise(polygonPoints)) {
                println("  ✓ Polygon is counter-clockwise")
            } else {
                println("  ✗ Polygon is clockwise (should be counter-clockwise)")
            }

            // Check for self-intersections
            println("Self-intersection check:")
            if (hasSelfIntersections(polygonPoints)) {
                println("  ✗ Polygon has self-intersections")
            } else {
                println("  ✓ Polygon has no self-intersections")
            }

            // Calculate polygon area (should be positive for counter-clockwise)
            val area = calculatePolygonArea(polygonPoints)
            println("Polygon area: $area")
        }
        println("=== End Debug ===")
    }

    // Calculate polygon area using shoelace formula
    private fun calculatePolygonArea(points: List<Float>): Float {
        if (points.size < 6) return 0f

        var area = 0f
        val numPoints = points.size / 2

        for (i in 0 until numPoints) {
            val j = (i + 1) % numPoints
            val xi = points[i * 2]
            val yi = points[i * 2 + 1]
            val xj = points[j * 2]
            val yj = points[j * 2 + 1]

            area += (xi * yj - xj * yi)
        }

        return abs(area) / 2f
    }

        // Clean up duplicate or very close points that could cause rendering issues
    private fun cleanDuplicatePoints(points: List<Float>): List<Float> {
        if (points.size < 6) return points.toList()

        val result = mutableListOf<Float>()
        val numPoints = points.size / 2
        val minDistance = 0.5f // Minimum distance between consecutive points

        for (i in 0 until numPoints) {
            val currentX = points[i * 2]
            val currentY = points[i * 2 + 1]

            // Always add the first point
            if (i == 0) {
                result.add(currentX)
                result.add(currentY)
                continue
            }

            // Check distance from previous point
            val prevX = result[result.size - 2]
            val prevY = result[result.size - 1]
            val distance = kotlin.math.sqrt((currentX - prevX) * (currentX - prevX) + (currentY - prevY) * (currentY - prevY))

            // Only add point if it's far enough from the previous one
            if (distance >= minDistance) {
                result.add(currentX)
                result.add(currentY)
            }
        }

        // Ensure we have at least 3 points for a valid polygon
        if (result.size < 6) {
            println("WARNING: After cleaning, polygon has too few points: ${result.size / 2}")
            return points.toList() // Return original if cleaning made it invalid
        }

        // Ensure polygon is counter-clockwise (required for ray casting algorithm)
        if (!isPolygonCounterClockwise(result)) {
            println("WARNING: Polygon is not counter-clockwise, reversing order")
            return result.reversed()
        }

        // Check for self-intersections (simplified check)
        if (hasSelfIntersections(result)) {
            println("WARNING: Polygon has self-intersections, this may cause rendering issues")
        }

        return result
    }

    // Check if polygon vertices are in counter-clockwise order
    private fun isPolygonCounterClockwise(points: List<Float>): Boolean {
        if (points.size < 6) return true

        var sum = 0.0
        val numPoints = points.size / 2

        for (i in 0 until numPoints) {
            val j = (i + 1) % numPoints
            val xi = points[i * 2]
            val yi = points[i * 2 + 1]
            val xj = points[j * 2]
            val yj = points[j * 2 + 1]

            sum += (xj - xi) * (yj + yi)
        }

        return sum < 0
    }

    // Check for self-intersections (simplified check for adjacent edges)
    private fun hasSelfIntersections(points: List<Float>): Boolean {
        if (points.size < 8) return false // Need at least 4 points

        val numPoints = points.size / 2

        for (i in 0 until numPoints) {
            val j = (i + 1) % numPoints
            val k = (i + 2) % numPoints
            val l = (i + 3) % numPoints

            // Check if edge i-j intersects with edge k-l
            if (doLinesIntersect(
                points[i * 2], points[i * 2 + 1],
                points[j * 2], points[j * 2 + 1],
                points[k * 2], points[k * 2 + 1],
                points[l * 2], points[l * 2 + 1]
            )) {
                return true
            }
        }

        return false
    }

    // Check if two line segments intersect
    private fun doLinesIntersect(x1: Float, y1: Float, x2: Float, y2: Float,
                                x3: Float, y3: Float, x4: Float, y4: Float): Boolean {
        val denominator = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4)
        if (abs(denominator) < 0.001f) return false // Lines are parallel

        val t = ((x1 - x3) * (y3 - y4) - (y1 - y3) * (x3 - x4)) / denominator
        val u = -((x1 - x2) * (y1 - y3) - (y1 - y2) * (x1 - x3)) / denominator

        return t >= 0f && t <= 1f && u >= 0f && u <= 1f
    }

    // Alternative smoothing method: simple edge subdivision
    private fun smoothEdgesSimple(points: List<Float>, subdivisions: Int = 2): List<Float> {
        val result = mutableListOf<Float>()
        val numPoints = points.size / 2

        if (numPoints < 3) return points.toList()

        for (i in 0 until numPoints) {
            val p1 = points[i * 2]
            val p1y = points[i * 2 + 1]
            val p2 = points[((i + 1) % numPoints) * 2]
            val p2y = points[((i + 1) % numPoints) * 2 + 1]

            // Add original point
            result.add(p1)
            result.add(p1y)

            // Add midpoint between this point and the next
            if (subdivisions > 1) {
                val midX = (p1 + p2) / 2f
                val midY = (p1y + p2y) / 2f
                result.add(midX)
                result.add(midY)
            }
        }

        return result
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

    fun drawShieldPop(): Boolean {
        val isDrawing = activePopWave != null

        if (isDrawing) {
            // For now, we'll use the shader to render the pop wave
            // The fragment shader handles the pop wave rendering
        }

        cleanup()
        return isDrawing
    }

    fun drawPolygon() {
        if (polygonPoints.isEmpty()) return

        // Validate polygon before rendering
        if (polygonPoints.size < 6) {
            println("WARNING: Cannot render polygon with ${polygonPoints.size / 2} points (minimum 3 required)")
            return
        }

        // Check if polygon is properly closed
        val firstX = polygonPoints[0]
        val firstY = polygonPoints[1]
        val lastX = polygonPoints[polygonPoints.size - 2]
        val lastY = polygonPoints[polygonPoints.size - 1]

        if (abs(firstX - lastX) > 0.001f || abs(firstY - lastY) > 0.001f) {
            println("WARNING: Polygon is not properly closed, skipping render")
            println("  First: ($firstX, $firstY)")
            println("  Last: ($lastX, $lastY)")
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
        glUniform1f(glGetUniformLocation(shaderProgram, "chargeProgress"), chargeProgress)
        glUniform1f(glGetUniformLocation(shaderProgram, "shipFacing"), shipFacing)
        glUniform2f(
            glGetUniformLocation(shaderProgram, "shipDimensions"),
            shipDimensions[0].x, shipDimensions[0].y
        )
        glUniform2f(glGetUniformLocation(shaderProgram, "center"), centerX, centerY)
        glUniform1f(glGetUniformLocation(shaderProgram, "time"), hexAnimationTime)
        glUniform1f(glGetUniformLocation(shaderProgram, "hexSize"), hexSize)
        glUniform1f(glGetUniformLocation(shaderProgram, "hexRotation"), hexRotation)
        glUniform1f(glGetUniformLocation(shaderProgram, "hexSpacing"), hexSpacing)
        glUniform1f(glGetUniformLocation(shaderProgram, "hexEdgeWidth"), hexEdgeWidth)
        glUniform1f(glGetUniformLocation(shaderProgram, "hexSpacingMultiplier"), hexSpacingMultiplier)
        glUniform2f(glGetUniformLocation(shaderProgram, "scale"), scaleX, scaleY)
        glUniform1f(glGetUniformLocation(shaderProgram, "rotation"), rotation)

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
        val maxPoints = 1000  // Increased to match shader uniform array size
        val pointCount = minOf(polygonPoints.size / 2, maxPoints)

        // Warn if we're hitting the limit
        if (polygonPoints.size / 2 > maxPoints) {
            println("WARNING: Polygon has ${polygonPoints.size / 2} points but shader can only handle $maxPoints")
            println("Only the first $maxPoints points will be rendered")
        }

        // Update UBO with polygon points data
        updatePointsUBO(pointCount)

        // Set point count uniform
        glUniform1i(glGetUniformLocation(shaderProgram, "pointCount"), pointCount)

        // Set debug polygon uniform
        glUniform1i(glGetUniformLocation(shaderProgram, "debugPolygon"), if (debugMode) 1 else 0)

        // Debug: Print what uniforms we're setting
        if (debugMode) {
            println("Shader Debug: pointCount = $pointCount")
        }

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
        tess.gluDeleteTess()
        glDeleteBuffers(vbo)
        glDeleteVertexArrays(vao)
        glDeleteBuffers(pointsUBO)
        glDeleteProgram(shaderProgram)
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

        glPopAttrib()
    }
}

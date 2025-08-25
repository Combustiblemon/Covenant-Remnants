package org.combustiblelemon.covenantremnants.utils.render

import com.fs.starfarer.api.Global
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL15.*
import org.lwjgl.opengl.GL20.*
import org.lwjgl.opengl.GL30.*
import org.lwjgl.util.glu.GLU
import org.lwjgl.util.glu.GLUtessellator
import org.lwjgl.util.glu.GLUtessellatorCallbackAdapter
import org.lwjgl.util.vector.Vector2f
import kotlin.math.abs

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

        // Polygon points uniforms
        uniform vec2 points[100];  // Array of points in screen coordinates
        uniform int pointCount;    // Number of points to draw
        uniform vec2 resolution;   // Screen resolution
        uniform float zoomLevel;   // Viewport zoom level (viewMult)

        void main() {
            // Convert texture coordinates to screen pixel coordinates
            vec2 pixelPos = TexCoord * resolution;

            // Draw points as small circles, scaled by zoom level
            float pointRadius = 5.0 / zoomLevel; // Base radius of 5 pixels, scaled by zoom
            for(int i = 0; i < pointCount; i++) {
                float dist = length(pixelPos - points[i]);
                if(dist < pointRadius) {
                    FragColor = shieldColor; // Use shield color for the points
                    return;
                }
            }

            // If not drawing a point, discard the fragment
            discard;
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

    fun updateScreenResolution(width: Float, height: Float) {
        screenWidth = width
        screenHeight = height
    }

    fun updateZoomLevel(zoom: Float) {
        zoomLevel = zoom
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

        // Convert polygon points to screen coordinates and pass to shader
        val maxPoints = 100
        val pointCount = minOf(polygonPoints.size / 2, maxPoints)

        // Set point count uniform
        glUniform1i(glGetUniformLocation(shaderProgram, "pointCount"), pointCount)

        // Convert and set each point
        for (i in 0 until pointCount) {
            val x = polygonPoints[i * 2]
            val y = polygonPoints[i * 2 + 1]

            // Points are already in screen coordinates from getShipVertexData
            // Just pass them directly to the shader
            glUniform2f(glGetUniformLocation(shaderProgram, "points[$i]"), x, y)
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

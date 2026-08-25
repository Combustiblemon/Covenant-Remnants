package org.combustiblelemon.covenantremnants.utils.render

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.ViewportAPI
import org.combustiblelemon.lemonlib.render.Shader
import org.combustiblelemon.lemonlib.render.getShaderCode
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL15.*
import org.lwjgl.opengl.GL20.*
import org.lwjgl.opengl.GL30.*

class SlipspacePortalEntityShader : Shader {
    private var vbo: Int = 0
    private var vao: Int = 0
    private var shaderProgram: Int = 0

    // Position in world coordinates
    private var centerX: Float = 0f
    private var centerY: Float = 0f
    private var radius: Float = 100f

    // Visual effect parameters
    private var time: Float = 0f
    var edgeFadeStart: Float = 0.85f  // Start fading at 85% of radius (last 15%)
    var baseColor: FloatArray = floatArrayOf(25f / 255f, 25f / 255f, 60f / 255f)  // Color(25, 25, 60)
    var vortexColor: FloatArray = floatArrayOf(54f / 255f, 40f / 255f, 103f / 255f)  // Color(54, 40, 103)
    var vortexSpeed: Float = 1.0f  // Rotation speed multiplier
    var vortexArms: Int = 3  // Number of vortex spiral arms

    // Energy flow parameters
    var energyFlowSpeed: Float = 0.5f  // Speed of energy flowing inward
    var energyFlowColor: FloatArray = floatArrayOf(0.7f, 0.8f, 1.0f)  // Light blue/white energy
    var energyFlowIntensity: Float = 0.5f  // Overall intensity of the flow effect
    var energyFlowStreams: Int = 12  // Number of energy streams

    // Animated flow parameters
    var flowStreakSpeed: Float = 0.7f  // Speed of animated flows
    var flowStreakIntensity: Float = 0.6f  // Brightness of flow streaks
    var flowStreakCount: Int = 16  // Number of constant energy flow lines
    var flowStreakWidth: Float = 0.45f  // Width of each traveling highlight
    var flowStreakGap: Float = 0.40f  // Gap between highlights (spacing between them)

    // Outer ring parameters
    var outerRingThickness: Float = 0.2f  // Thickness as fraction of radius (12% = 0.12, 20% bigger than 0.1)
    var outerRingColor: FloatArray = floatArrayOf(1.0f, 1.0f, 1.0f)  // White for debugging
    var outerRingFadeStart: Float = 0.3f  // Start fading at 30% from center of ring

    // Inner edge wave distortion parameters
    var innerEdgeWaveAmplitude: Float = 0.02f  // Amplitude of wave distortion
    var innerEdgeWaveFrequency: Float = 8.0f  // Number of waves around the circle
    var innerEdgeWaveSpeed: Float = 2f  // Speed of wave animation

    // Animation state parameters
    enum class AnimationState {
        CLOSED,     // Simple circle, small radius
        OPENING,    // Transitioning from closed to open
        OPEN,       // Full portal effect
        CLOSING     // Transitioning from open to closed
    }

    private var animationState: AnimationState = AnimationState.CLOSED
    private var animationProgress: Float = 0f  // 0.0 = closed, 1.0 = open
    private var animationSpeed: Float = 1.0f  // Speed of animation (1.0 = 1 second)
    private var closedStateRadius: Float = 0.1f  // Radius when closed (as fraction of full radius)
    private var closedStateColor: FloatArray = floatArrayOf(1.0f, 1.0f, 1.0f)  // White for closed state

    // Vertex shader - passes through position and texture coordinates
    private val vertexShaderSource = getShaderCode("data/shaders/slipspacePortalEntityShader.vert")

    // Fragment shader - renders a vortex portal effect
    private val fragmentShaderSource = getShaderCode("data/shaders/slipspacePortalEntityShader.frag")

    init {
        initBuffers()
        initShaders()
    }

    private fun initBuffers() {
        // Create VAO
        vao = glGenVertexArrays()
        glBindVertexArray(vao)

        // Define a square with position and texture coordinates
        // Position will be updated in render(), texture coordinates are from -1 to 1
        // Format: [posX, posY, texX, texY]
        val vertices = floatArrayOf(
            -1f, -1f, -1f, -1f,  // Bottom-left
            1f, -1f, 1f, -1f,  // Bottom-right
            1f, 1f, 1f, 1f,  // Top-right
            -1f, 1f, -1f, 1f   // Top-left
        )

        // Create VBO
        vbo = glGenBuffers()
        glBindBuffer(GL_ARRAY_BUFFER, vbo)

        // Upload vertex data to GPU
        val buffer = BufferUtils.createFloatBuffer(vertices.size)
        buffer.put(vertices)
        buffer.flip()
        glBufferData(GL_ARRAY_BUFFER, buffer, GL_STATIC_DRAW)

        // Configure vertex attributes
        // Position attribute (location = 0)
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 4 * 4, 0)
        glEnableVertexAttribArray(0)

        // Texture coordinate attribute (location = 1)
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 4 * 4, 2 * 4)
        glEnableVertexAttribArray(1)

        // Unbind
        glBindVertexArray(0)
    }

    private fun initShaders() {
        // Compile vertex shader
        val vertexShader = glCreateShader(GL_VERTEX_SHADER)
        glShaderSource(vertexShader, vertexShaderSource)
        glCompileShader(vertexShader)

        // Check for vertex shader compilation errors
        if (glGetShaderi(vertexShader, GL_COMPILE_STATUS) == GL_FALSE) {
            val error = glGetShaderInfoLog(vertexShader, 1024)
            throw RuntimeException("Vertex shader compilation failed: $error")
        }

        // Compile fragment shader
        val fragmentShader = glCreateShader(GL_FRAGMENT_SHADER)
        glShaderSource(fragmentShader, fragmentShaderSource)
        glCompileShader(fragmentShader)

        // Check for fragment shader compilation errors
        if (glGetShaderi(fragmentShader, GL_COMPILE_STATUS) == GL_FALSE) {
            val error = glGetShaderInfoLog(fragmentShader, 1024)
            throw RuntimeException("Fragment shader compilation failed: $error")
        }

        // Link shader program
        shaderProgram = glCreateProgram()
        glAttachShader(shaderProgram, vertexShader)
        glAttachShader(shaderProgram, fragmentShader)
        glLinkProgram(shaderProgram)

        // Check for linking errors
        if (glGetProgrami(shaderProgram, GL_LINK_STATUS) == GL_FALSE) {
            val error = glGetProgramInfoLog(shaderProgram, 1024)
            throw RuntimeException("Shader program linking failed: $error")
        }

        // Clean up shaders (no longer needed after linking)
        glDeleteShader(vertexShader)
        glDeleteShader(fragmentShader)
    }

    /**
     * Set the center position and radius for the circle
     * @param x Center X position in world coordinates
     * @param y Center Y position in world coordinates
     * @param radius Circle radius in world units
     */
    fun setPosition(x: Float, y: Float, radius: Float) {
        this.centerX = x
        this.centerY = y
        this.radius = radius
    }

    /**
     * Update animation time
     * @param amount Time delta in seconds
     */
    fun updateTime(amount: Float) {
        time += amount

        // Update animation progress if we're animating
        if (animationState == AnimationState.OPENING || animationState == AnimationState.CLOSING) {
            val progressDelta = amount * animationSpeed
            if (animationState == AnimationState.OPENING) {
                animationProgress += progressDelta
                if (animationProgress >= 1.0f) {
                    animationProgress = 1.0f
                    animationState = AnimationState.OPEN
                }
            } else { // CLOSING
                animationProgress -= progressDelta
                if (animationProgress <= 0.0f) {
                    animationProgress = 0.0f
                    animationState = AnimationState.CLOSED
                }
            }
        }
    }

    /**
     * Trigger the portal opening animation
     */
    fun triggerOpening() {
        if (animationState == AnimationState.CLOSED || animationState == AnimationState.CLOSING) {
            animationState = AnimationState.OPENING
        }
    }

    /**
     * Trigger the portal closing animation
     */
    fun triggerClosing() {
        if (animationState == AnimationState.OPEN || animationState == AnimationState.OPENING) {
            animationState = AnimationState.CLOSING
        }
    }

    /**
     * Set the animation speed (1.0 = 1 second for full animation)
     * @param speed Animation speed multiplier
     */
    fun setAnimationSpeed(speed: Float) {
        animationSpeed = speed
    }

    /**
     * Set the closed state radius as a fraction of the full radius
     * @param radiusFraction Fraction of full radius (0.1 = 10% of full radius)
     */
    fun setClosedStateRadius(radiusFraction: Float) {
        closedStateRadius = radiusFraction
    }

    /**
     * Set the closed state color
     * @param r Red component (0.0 to 1.0)
     * @param g Green component (0.0 to 1.0)
     * @param b Blue component (0.0 to 1.0)
     */
    fun setClosedStateColor(r: Float, g: Float, b: Float) {
        closedStateColor[0] = r
        closedStateColor[1] = g
        closedStateColor[2] = b
    }

    /**
     * Get the current animation state
     * @return Current animation state
     */
    fun getAnimationState(): AnimationState {
        return animationState
    }

    /**
     * Get the current animation progress (0.0 = closed, 1.0 = open)
     * @return Animation progress
     */
    fun getAnimationProgress(): Float {
        return animationProgress
    }

    override fun render(viewport: ViewportAPI) {
        // Convert circle's world coordinates to screen coordinates
        val circleScreenX = viewport.convertWorldXtoScreenX(centerX)
        val circleScreenY = viewport.convertWorldYtoScreenY(centerY)

        // Convert radius from world units to screen units, accounting for zoom (viewMult)
        // viewMult is higher when zoomed in, so divide to get proper screen-space radius
        val screenRadius = radius / viewport.viewMult

        // No need to expand the quad since ring is inside the main circle
        val expandedScreenRadius = screenRadius

        // Get actual OpenGL viewport dimensions (not affected by zoom)
        val screenWidth = viewport.visibleWidth / viewport.viewMult
        val screenHeight = viewport.visibleHeight / viewport.viewMult

        // Calculate aspect ratio for proper circle rendering
        val aspectRatio = screenWidth / screenHeight

        // Convert screen coordinates to NDC
        // Screen coordinates from convertWorldXtoScreenX are relative to viewport's lower-left corner
        // Standard NDC conversion: (coord / dimension) * 2 - 1
        val ndcCenterX = (circleScreenX / screenWidth) * 2f - 1f
        val ndcCenterY = (circleScreenY / screenHeight) * 2f - 1f
        val ndcRadiusX = (expandedScreenRadius / screenWidth) * 2f
        val ndcRadiusY = (expandedScreenRadius / screenHeight) * 2f

        val ndcLeft = ndcCenterX - ndcRadiusX
        val ndcRight = ndcCenterX + ndcRadiusX
        val ndcBottom = ndcCenterY - ndcRadiusY
        val ndcTop = ndcCenterY + ndcRadiusY

        // Update vertex buffer with new positions
        val vertices = floatArrayOf(
            ndcLeft, ndcBottom, -1f, -1f,  // Bottom-left
            ndcRight, ndcBottom, 1f, -1f,  // Bottom-right
            ndcRight, ndcTop, 1f, 1f,  // Top-right
            ndcLeft, ndcTop, -1f, 1f   // Top-left
        )

        // Pass aspect ratio to shader for circle correction
        val shaderAspectRatio = aspectRatio

        // Save current OpenGL state
        val currentProgram = glGetInteger(GL_CURRENT_PROGRAM)
        val currentVAO = glGetInteger(GL_VERTEX_ARRAY_BINDING)
        val currentVBO = glGetInteger(GL_ARRAY_BUFFER_BINDING)
        val blendEnabled = glIsEnabled(GL_BLEND)

        try {
            // Update VBO with new vertex data
            glBindBuffer(GL_ARRAY_BUFFER, vbo)
            val buffer = BufferUtils.createFloatBuffer(vertices.size)
            buffer.put(vertices)
            buffer.flip()
            glBufferSubData(GL_ARRAY_BUFFER, 0, buffer)

            // Use our shader program
            glUseProgram(shaderProgram)

            // Set all shader uniforms
            glUniform1f(glGetUniformLocation(shaderProgram, "aspectRatio"), shaderAspectRatio)
            glUniform1f(glGetUniformLocation(shaderProgram, "time"), time)
            glUniform1f(glGetUniformLocation(shaderProgram, "edgeFadeStart"), edgeFadeStart)
            glUniform3f(glGetUniformLocation(shaderProgram, "baseColor"), baseColor[0], baseColor[1], baseColor[2])
            glUniform3f(
                glGetUniformLocation(shaderProgram, "vortexColor"),
                vortexColor[0],
                vortexColor[1],
                vortexColor[2]
            )
            glUniform1f(glGetUniformLocation(shaderProgram, "vortexSpeed"), vortexSpeed)
            glUniform1i(glGetUniformLocation(shaderProgram, "vortexArms"), vortexArms)

            // Energy flow uniforms
            glUniform1f(glGetUniformLocation(shaderProgram, "energyFlowSpeed"), energyFlowSpeed)
            glUniform3f(
                glGetUniformLocation(shaderProgram, "energyFlowColor"),
                energyFlowColor[0],
                energyFlowColor[1],
                energyFlowColor[2]
            )
            glUniform1f(glGetUniformLocation(shaderProgram, "energyFlowIntensity"), energyFlowIntensity)
            glUniform1i(glGetUniformLocation(shaderProgram, "energyFlowStreams"), energyFlowStreams)

            // Animated flow streak uniforms
            glUniform1f(glGetUniformLocation(shaderProgram, "flowStreakSpeed"), flowStreakSpeed)
            glUniform1f(glGetUniformLocation(shaderProgram, "flowStreakIntensity"), flowStreakIntensity)
            glUniform1i(glGetUniformLocation(shaderProgram, "flowStreakCount"), flowStreakCount)
            glUniform1f(glGetUniformLocation(shaderProgram, "flowStreakWidth"), flowStreakWidth)
            glUniform1f(glGetUniformLocation(shaderProgram, "flowStreakGap"), flowStreakGap)

            // Outer ring uniforms
            glUniform1f(glGetUniformLocation(shaderProgram, "outerRingThickness"), outerRingThickness)
            glUniform3f(
                glGetUniformLocation(shaderProgram, "outerRingColor"),
                outerRingColor[0],
                outerRingColor[1],
                outerRingColor[2]
            )
            glUniform1f(glGetUniformLocation(shaderProgram, "outerRingFadeStart"), outerRingFadeStart)

            // Inner edge wave distortion uniforms
            glUniform1f(glGetUniformLocation(shaderProgram, "innerEdgeWaveAmplitude"), innerEdgeWaveAmplitude)
            glUniform1f(glGetUniformLocation(shaderProgram, "innerEdgeWaveFrequency"), innerEdgeWaveFrequency)
            glUniform1f(glGetUniformLocation(shaderProgram, "innerEdgeWaveSpeed"), innerEdgeWaveSpeed)

            // Animation uniforms
            glUniform1f(glGetUniformLocation(shaderProgram, "animationProgress"), animationProgress)
            glUniform1f(glGetUniformLocation(shaderProgram, "closedStateRadius"), closedStateRadius)
            glUniform3f(
                glGetUniformLocation(shaderProgram, "closedStateColor"),
                closedStateColor[0],
                closedStateColor[1],
                closedStateColor[2]
            )

            // Bind VAO
            glBindVertexArray(vao)

            // Enable blending for transparency
            glEnable(GL_BLEND)
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)

            // Draw the square (4 vertices as a triangle fan)
            glDrawArrays(GL_TRIANGLE_FAN, 0, 4)

        } finally {
            // Restore OpenGL state
            glBindBuffer(GL_ARRAY_BUFFER, currentVBO)
            glUseProgram(currentProgram)
            glBindVertexArray(currentVAO)
            if (!blendEnabled) glDisable(GL_BLEND)
        }
    }

    override fun cleanup() {
        try {
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

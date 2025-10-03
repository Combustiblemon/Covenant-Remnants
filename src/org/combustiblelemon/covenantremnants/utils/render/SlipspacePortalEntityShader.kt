package org.combustiblelemon.covenantremnants.utils.render

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.ViewportAPI
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

    // Vertex shader - passes through position and texture coordinates
    private val vertexShaderSource = """
        #version 330 core
        layout (location = 0) in vec2 aPos;
        layout (location = 1) in vec2 aTexCoord;

        out vec2 TexCoord;

        void main() {
            gl_Position = vec4(aPos, 0.0, 1.0);
            TexCoord = aTexCoord;
        }
    """.trimIndent()

    // Fragment shader - renders a vortex portal effect
    private val fragmentShaderSource = """
        #version 330 core
        in vec2 TexCoord;
        out vec4 FragColor;

        uniform float aspectRatio;
        uniform float time;
        uniform float edgeFadeStart;
        uniform vec3 baseColor;
        uniform vec3 vortexColor;
        uniform float vortexSpeed;
        uniform int vortexArms;

        // Calculate edge fade based on distance from center
        float calculateEdgeFade(float dist) {
            if (dist < edgeFadeStart) {
                return 1.0;
            }
            // Smoothly fade from edgeFadeStart to edge (1.0)
            float fadeRange = 1.0 - edgeFadeStart;
            float fadeAmount = (dist - edgeFadeStart) / fadeRange;
            return 1.0 - smoothstep(0.0, 1.0, fadeAmount);
        }

        // Calculate vortex spiral pattern
        float calculateVortexPattern(vec2 coord, float dist) {
            // Calculate angle from center
            float angle = atan(coord.y, coord.x);

            // Create spiral effect by combining angle with distance
            // Multiple arms create the vortex appearance
            float spiral = angle * float(vortexArms) + dist * 10.0 - time * vortexSpeed;

            // Create alternating bright/dark bands using sine wave
            float pattern = sin(spiral) * 0.5 + 0.5;

            // Make pattern fade towards center and edge
            float centerFade = smoothstep(0.0, 0.3, dist);  // Fade in from center
            float edgeFade = 1.0 - smoothstep(0.7, 1.0, dist);  // Fade out at edge

            return pattern * centerFade * edgeFade;
        }

        void main() {
            // Adjust texture coordinates by aspect ratio to maintain circular shape
            // Divide Y by aspect ratio to compensate for wider screens
            vec2 adjustedCoord = vec2(TexCoord.x, TexCoord.y);

            // Calculate distance from center using adjusted coordinates
            float dist = length(adjustedCoord);

            // Discard fragments outside the circle
            if (dist > 1.0) {
                discard;
            }

            // Calculate edge fade
            float edgeFade = calculateEdgeFade(dist);

            // Calculate vortex pattern using adjusted coordinates for circular appearance
            float vortexPattern = calculateVortexPattern(adjustedCoord, dist);

            // Blend base color with vortex color based on pattern
            vec3 finalColor = mix(baseColor, vortexColor, vortexPattern);

            // Apply edge fade to alpha
            float alpha = edgeFade;

            FragColor = vec4(finalColor, alpha);
        }
    """.trimIndent()

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
    }

    override fun render(viewport: ViewportAPI) {
        // Convert circle's world coordinates to screen coordinates
        val circleScreenX = viewport.convertWorldXtoScreenX(centerX)
        val circleScreenY = viewport.convertWorldYtoScreenY(centerY)

        // Convert radius from world units to screen units, accounting for zoom (viewMult)
        // viewMult is higher when zoomed in, so divide to get proper screen-space radius
        val screenRadius = radius / viewport.viewMult

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
        val ndcRadiusX = (screenRadius / screenWidth) * 2f
        val ndcRadiusY = (screenRadius / screenHeight) * 2f

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
            glUniform3f(glGetUniformLocation(shaderProgram, "vortexColor"), vortexColor[0], vortexColor[1], vortexColor[2])
            glUniform1f(glGetUniformLocation(shaderProgram, "vortexSpeed"), vortexSpeed)
            glUniform1i(glGetUniformLocation(shaderProgram, "vortexArms"), vortexArms)

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

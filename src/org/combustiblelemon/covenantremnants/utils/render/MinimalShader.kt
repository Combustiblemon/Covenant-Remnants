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

/**
 * Minimal shader example that renders a white square in the center of the screen.
 * This demonstrates the bare minimum needed to run a shader in OpenGL.
 */
class MinimalShader : Shader {
    private var vbo: Int = 0
    private var vao: Int = 0
    private var shaderProgram: Int = 0

    // Simple vertex shader - passes through NDC coordinates
    private val vertexShaderSource = getShaderCode("data/shaders/minimalShader.vert")

    // Simple fragment shader - outputs white color
    private val fragmentShaderSource = getShaderCode("data/shaders/minimalShader.frag")

    init {
        initBuffers()
        initShaders()
    }

    private fun initBuffers() {
        // Create VAO
        vao = glGenVertexArrays()
        glBindVertexArray(vao)

        // Define a square in NDC coordinates (centered, 0.5 units wide/tall)
        // NDC range: -1 to 1, so this creates a square from -0.25 to 0.25 on both axes
        val vertices = floatArrayOf(
            -0.25f, -0.25f,  // Bottom-left
            0.25f, -0.25f,  // Bottom-right
            0.25f, 0.25f,  // Top-right
            -0.25f, 0.25f   // Top-left
        )

        // Create VBO
        vbo = glGenBuffers()
        glBindBuffer(GL_ARRAY_BUFFER, vbo)

        // Upload vertex data to GPU
        val buffer = BufferUtils.createFloatBuffer(vertices.size)
        buffer.put(vertices)
        buffer.flip()
        glBufferData(GL_ARRAY_BUFFER, buffer, GL_STATIC_DRAW)

        // Configure vertex attribute (position)
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 2 * 4, 0)
        glEnableVertexAttribArray(0)

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

    override fun render(viewport: ViewportAPI) {
        // Viewport parameter is not needed for this simple shader
        // Save current OpenGL state
        val currentProgram = glGetInteger(GL_CURRENT_PROGRAM)
        val currentVAO = glGetInteger(GL_VERTEX_ARRAY_BINDING)
        val blendEnabled = glIsEnabled(GL_BLEND)

        try {
            // Use our shader program
            glUseProgram(shaderProgram)

            // Bind VAO
            glBindVertexArray(vao)

            // Enable blending for transparency
            glEnable(GL_BLEND)
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)

            // Draw the square (4 vertices as a triangle fan)
            glDrawArrays(GL_TRIANGLE_FAN, 0, 4)

        } finally {
            // Restore OpenGL state
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

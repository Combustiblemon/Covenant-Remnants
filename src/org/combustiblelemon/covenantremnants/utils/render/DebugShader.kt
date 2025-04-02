package org.combustiblelemon.covenantremnants.utils.render

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ViewportAPI
import org.combustiblelemon.covenantremnants.utils.Utils
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL15.*
import org.lwjgl.opengl.GL20.*
import org.lwjgl.opengl.GL30.*
import org.lwjgl.util.vector.Matrix4f

class DebugShader(private val ship: ShipAPI) {
    companion object {
        private const val VERTEX_SHADER = """
            #version 330 core
            layout (location = 0) in vec2 aPos;
            layout (location = 1) in vec4 aColor;

            uniform mat4 transform;

            out vec4 Color;

            void main() {
                gl_Position = transform * vec4(aPos, 0.0, 1.0);
                Color = aColor;
            }
        """

        private const val FRAGMENT_SHADER = """
            #version 330 core
            in vec4 Color;
            out vec4 FragColor;

            void main() {
                FragColor = Color;
            }
        """
    }

    private var shaderProgram: Int = 0
    private var vao: Int = 0
    private var vbo: Int = 0
    private var ebo: Int = 0

    init {
        initShaders()
        initBuffers()
    }

    private fun initShaders() {
        val vertexShader = loadShader(VERTEX_SHADER, GL_VERTEX_SHADER)
        val fragmentShader = loadShader(FRAGMENT_SHADER, GL_FRAGMENT_SHADER)

        shaderProgram = glCreateProgram()
        glAttachShader(shaderProgram, vertexShader)
        glAttachShader(shaderProgram, fragmentShader)
        glLinkProgram(shaderProgram)

        if (glGetProgrami(shaderProgram, GL_LINK_STATUS) == GL_FALSE) {
            val logLength = glGetProgrami(shaderProgram, GL_INFO_LOG_LENGTH)
            val log = glGetProgramInfoLog(shaderProgram, logLength)
            throw RuntimeException("Shader program linking failed: $log")
        }

        glDeleteShader(vertexShader)
        glDeleteShader(fragmentShader)
    }

    private fun loadShader(source: String, type: Int): Int {
        val shader = glCreateShader(type)
        glShaderSource(shader, source)
        glCompileShader(shader)

        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            val logLength = glGetShaderi(shader, GL_INFO_LOG_LENGTH)
            val log = glGetShaderInfoLog(shader, logLength)
            throw RuntimeException("Shader compilation failed: $log")
        }

        return shader
    }

    private fun initBuffers() {
        // Create VAO
        vao = glGenVertexArrays()
        glBindVertexArray(vao)

        // Create VBO
        vbo = glGenBuffers()
        glBindBuffer(GL_ARRAY_BUFFER, vbo)
        glBufferData(GL_ARRAY_BUFFER, 0, GL_STATIC_DRAW)

        // Create EBO
        ebo = glGenBuffers()
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo)
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, 0, GL_STATIC_DRAW)

        // Set up vertex attributes
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 6 * 4, 0)
        glEnableVertexAttribArray(0)
        glVertexAttribPointer(1, 4, GL_FLOAT, false, 6 * 4, 2 * 4)
        glEnableVertexAttribArray(1)

        glBindVertexArray(0)
    }

    fun drawDebugSquare(viewport: ViewportAPI) {
        // Save current OpenGL state
        glPushAttrib(GL_ALL_ATTRIB_BITS)
        glPushMatrix()

        // Save current shader program
        val currentProgram = glGetInteger(GL_CURRENT_PROGRAM)

        // Enable blending
        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)

        // Use our shader program
        glUseProgram(shaderProgram)

        // Set up transform matrix
        val transform = Matrix4f()
        transform.setIdentity()
        val transformBuffer = BufferUtils.createFloatBuffer(16)
        transform.store(transformBuffer)
        transformBuffer.flip()
        val transformLocation = glGetUniformLocation(shaderProgram, "transform")
        glUniformMatrix4(transformLocation, false, transformBuffer)

        val dimensions = Utils.getShipDimensions(ship)

        val shaderDims = Utils.convertWorldPointToNDC(ship.location)

        println(
            "screen coords " + viewport.convertWorldXtoScreenX(ship.location.x) + "/" + Global.getSettings().screenWidthPixels + " " + viewport.convertWorldYtoScreenY(
                ship.location.y
            ) + "/" + Global.getSettings().screenHeightPixels
        )
        println("" + shaderDims)

        // Create vertices for a white square using normalized device coordinates
        val vertices = floatArrayOf(
            // Position (2 floats) | Color (4 floats)
            shaderDims.x - 0.1f, shaderDims.y - 0.1f, 1.0f, 1.0f, 1.0f, 1.0f,  // Bottom-left
            shaderDims.x + 0.1f, shaderDims.y - 0.1f, 1.0f, 1.0f, 1.0f, 1.0f,   // Bottom-right
            shaderDims.x + 0.1f, shaderDims.y + 0.1f, 1.0f, 1.0f, 1.0f, 1.0f,    // Top-right
            shaderDims.x - 0.1f, shaderDims.y + 0.1f, 1.0f, 1.0f, 1.0f, 1.0f    // Top-left
        )

        // Create indices for the square
        val indices = intArrayOf(
            0, 1, 2,  // First triangle
            0, 2, 3   // Second triangle
        )

        // Update VBO
        glBindBuffer(GL_ARRAY_BUFFER, vbo)
        val vertexBuffer = BufferUtils.createFloatBuffer(vertices.size)
        vertexBuffer.put(vertices).flip()
        glBufferData(GL_ARRAY_BUFFER, vertexBuffer, GL_STATIC_DRAW)

        // Update EBO
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo)
        val indexBuffer = BufferUtils.createIntBuffer(indices.size)
        indexBuffer.put(indices).flip()
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indexBuffer, GL_STATIC_DRAW)

        // Draw the square
        glBindVertexArray(vao)
        glDrawElements(GL_TRIANGLES, indices.size, GL_UNSIGNED_INT, 0)
        glBindVertexArray(0)

        // Restore previous shader program
        glUseProgram(currentProgram)

        // Restore OpenGL state
        glPopMatrix()
        glPopAttrib()
    }

    fun cleanup() {
        glDeleteVertexArrays(vao)
        glDeleteBuffers(vbo)
        glDeleteBuffers(ebo)
        glDeleteProgram(shaderProgram)
    }
}

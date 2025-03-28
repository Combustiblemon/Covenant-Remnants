package org.combustiblelemon.covenantremnants.utils

import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL20
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PolygonShader(
    private var polygonPoints: List<Float> = listOf(),
    private var colorRGBA: FloatArray = floatArrayOf(0.2f, 0.4f, 1.0f, 0.5f)
) {
    private var programId: Int = 0
    private var vertexShaderId: Int = 0
    private var fragmentShaderId: Int = 0
    private var vertexBuffer: ByteBuffer? = null

    // Shader source code with dynamic color support
    private val vertexShaderSource = """
        #version 120
        uniform mat4 projectionMatrix;
        uniform mat4 modelViewMatrix;
        attribute vec3 position;

        void main() {
            gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
        }
    """.trimIndent()

    private val fragmentShaderSource = """
        #version 120
        uniform vec4 customColor;
        
        void main() {
            gl_FragColor = customColor;
        }
    """.trimIndent()

    // Shader compilation method
    private fun compileShader(source: String, type: Int): Int {
        val shaderId = GL20.glCreateShader(type)
        GL20.glShaderSource(shaderId, source)
        GL20.glCompileShader(shaderId)

        if (GL20.glGetShaderi(shaderId, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            val infoLogLength = GL20.glGetShaderi(shaderId, GL20.GL_INFO_LOG_LENGTH)
            val infoLog = ByteBuffer.allocateDirect(infoLogLength)
                .order(ByteOrder.nativeOrder())
            GL20.glGetShaderInfoLog(shaderId, null, infoLog)
            throw RuntimeException("Shader compilation failed: ${String(infoLog.array())}")
        }

        return shaderId
    }

    // Initialize shader program
    fun init() {
        // Compile shaders
        vertexShaderId = compileShader(vertexShaderSource, GL20.GL_VERTEX_SHADER)
        fragmentShaderId = compileShader(fragmentShaderSource, GL20.GL_FRAGMENT_SHADER)

        // Create program
        programId = GL20.glCreateProgram()
        GL20.glAttachShader(programId, vertexShaderId)
        GL20.glAttachShader(programId, fragmentShaderId)
        GL20.glLinkProgram(programId)

        // Check linking status
        if (GL20.glGetProgrami(programId, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            val infoLogLength = GL20.glGetProgrami(programId, GL20.GL_INFO_LOG_LENGTH)
            val infoLog = ByteBuffer.allocateDirect(infoLogLength)
                .order(ByteOrder.nativeOrder())
            GL20.glGetProgramInfoLog(programId, null, infoLog)
            throw RuntimeException("Shader program linking failed: ${String(infoLog.array())}")
        }

        // Prepare vertex buffer
        prepareVertexBuffer()
    }

    // Prepare vertex buffer for the polygon
    private fun prepareVertexBuffer() {
        // Allocate a new byte buffer
        vertexBuffer = BufferUtils.createByteBuffer(polygonPoints.size * 4) // 4 bytes per float

        // Create a float view of the byte buffer
        val floatBuffer = vertexBuffer?.asFloatBuffer()

        // Put polygon points into the float buffer
        floatBuffer?.put(polygonPoints.toFloatArray())
        floatBuffer?.flip()
    }

    // Update polygon points
    fun updatePolygon(newPoints: List<Float>) {
        polygonPoints = newPoints
        prepareVertexBuffer()
    }

    // Update color
    fun updateColor(r: Float, g: Float, b: Float, a: Float) {
        colorRGBA = floatArrayOf(r, g, b, a)
    }

    // Draw the polygon
    fun drawPolygon() {
        // Ensure we have points to draw
        if (polygonPoints.isEmpty()) return

        // Use the shader program
        GL20.glUseProgram(programId)

        // Set custom color uniform
        val colorUniformLocation = GL20.glGetUniformLocation(programId, "customColor")
        GL20.glUniform4f(
            colorUniformLocation,
            colorRGBA[0],
            colorRGBA[1],
            colorRGBA[2],
            colorRGBA[3]
        )

        // Get position attribute location
        val positionAttributeLocation = GL20.glGetAttribLocation(programId, "position")

        // Enable blending for transparency
        GL11.glEnable(GL11.GL_BLEND)
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)

        // Enable vertex attribute array
        GL20.glEnableVertexAttribArray(positionAttributeLocation)

        // Specify vertex attribute pointer
        GL20.glVertexAttribPointer(
            positionAttributeLocation,
            3,              // 3 components per vertex (x, y, z)
            GL11.GL_FLOAT,  // Data type
            false,          // Normalized
            0,              // Stride (tightly packed)
            vertexBuffer!!  // Vertex buffer
        )

        // Draw the polygon
        GL11.glBegin(GL11.GL_POLYGON)
        for (i in 0 until polygonPoints.size / 3) {
            GL11.glVertex3f(
                polygonPoints[i * 3],
                polygonPoints[i * 3 + 1],
                polygonPoints[i * 3 + 2]
            )
        }
        GL11.glEnd()

        // Disable vertex attribute array
        GL20.glDisableVertexAttribArray(positionAttributeLocation)

        // Unbind shader program
        GL20.glUseProgram(0)
    }

    // Cleanup method
    fun cleanup() {
        // Clear vertex buffer
        vertexBuffer?.clear()

        // Delete shader resources
        GL20.glDetachShader(programId, vertexShaderId)
        GL20.glDetachShader(programId, fragmentShaderId)
        GL20.glDeleteShader(vertexShaderId)
        GL20.glDeleteShader(fragmentShaderId)
        GL20.glDeleteProgram(programId)
    }
}
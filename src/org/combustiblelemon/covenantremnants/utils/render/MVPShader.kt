package org.combustiblelemon.covenantremnants.utils.render

import com.fs.starfarer.api.combat.ViewportAPI
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL15.*
import org.lwjgl.opengl.GL20.*
import org.lwjgl.opengl.GL30.*
import org.lwjgl.opengl.GL31.GL_UNIFORM_BUFFER
import org.lwjgl.util.vector.Vector2f

class MVPShader {
    companion object {
        private const val VERTEX_SHADER = """
            #version 330 core
            layout (location = 0) in vec2 aPos;

            out vec2 TexCoord;

            void main() {
                // Pass through the position directly (already in NDC space)
                gl_Position = vec4(aPos, 0.0, 1.0);
                // Convert NDC to texture coordinates (0 to 1)
                TexCoord = aPos * 0.5 + 0.5;
            }
        """

        private const val FRAGMENT_SHADER = """
            #version 330 core
            in vec2 TexCoord;
            out vec4 FragColor;

            uniform vec2 resolution;
            uniform vec2 points[100];  // Array of points in screen coordinates
            uniform int pointCount;    // Number of points to draw

            void main() {
                // Convert texture coordinates to screen pixel coordinates
                vec2 pixelPos = TexCoord * resolution;

                // Debug: Draw points as small circles to verify they're in the right place
                for(int i = 0; i < pointCount; i++) {
                    float dist = length(pixelPos - points[i]);
                    if(dist < 5.0) {
                        FragColor = vec4(1.0, 0.0, 0.0, 1.0); // Red for points
                        return;
                    }
                }

                // Draw circle (half screen size)
                vec2 center = resolution * 0.5;
                float radius = min(resolution.x, resolution.y) * 0.25;
                float circleDist = length(pixelPos - center);
                float circleAlpha = smoothstep(radius, radius - 1.0, circleDist);

                FragColor = vec4(1.0, 1.0, 1.0, circleAlpha * 0.5);
            }
        """
    }

    private var shaderProgram: Int = 0
    private var vao: Int = 0
    private var vbo: Int = 0
    private var ebo: Int = 0
    private var pointsBuffer: Int = 0
    private var pointCount: Int = 0
    private val maxPoints = 100

    init {
        initShaders()
        initBuffers()
        initPointsBuffer()
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

    private fun initPointsBuffer() {
        pointsBuffer = glGenBuffers()
        glBindBuffer(GL_UNIFORM_BUFFER, pointsBuffer)

        // Create initial buffer with zeros
        val initialBuffer = BufferUtils.createFloatBuffer(maxPoints * 2)
        glBufferData(GL_UNIFORM_BUFFER, maxPoints * 2 * 4L, GL_DYNAMIC_DRAW) // 4 bytes per float
        glBindBuffer(GL_UNIFORM_BUFFER, 0)
    }

    fun convertWorldPointsToScreen(worldPoints: Array<Vector2f>, viewport: ViewportAPI): FloatArray {
        val screenPoints = FloatArray(worldPoints.size * 2)
        val viewMult = viewport.viewMult

        for (i in worldPoints.indices) {
            val worldPoint = worldPoints[i]
            // Convert world coordinates to screen pixel coordinates, accounting for zoom
            val screenX = viewport.convertWorldXtoScreenX(worldPoint.x)
            val screenY = viewport.convertWorldYtoScreenY(worldPoint.y)

            screenPoints[i * 2] = screenX * viewMult
            screenPoints[i * 2 + 1] = screenY * viewMult
        }

        return screenPoints
    }

    fun drawShader(viewport: ViewportAPI, worldPoints: Array<Vector2f>) {
        // Save current OpenGL state
        glPushAttrib(GL_ALL_ATTRIB_BITS)
        glPushMatrix()

        // Save current shader program
        val currentProgram = glGetInteger(GL_CURRENT_PROGRAM)

        // Use our shader program
        glUseProgram(shaderProgram)

        // Convert world points to screen coordinates
        val screenPoints = convertWorldPointsToScreen(worldPoints, viewport)
        pointCount = minOf(worldPoints.size, maxPoints)

        // Set uniforms directly instead of using uniform buffer
        val resolutionLoc = glGetUniformLocation(shaderProgram, "resolution")
        glUniform2f(resolutionLoc, viewport.visibleWidth.toFloat(), viewport.visibleHeight.toFloat())

        val pointCountLoc = glGetUniformLocation(shaderProgram, "pointCount")
        glUniform1i(pointCountLoc, pointCount)

        // Set points array directly
        for (i in 0 until pointCount) {
            val pointLoc = glGetUniformLocation(shaderProgram, "points[$i]")
            glUniform2f(pointLoc, screenPoints[i * 2], screenPoints[i * 2 + 1])
        }

        // Enable blending for transparency
        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)

        // Draw full-screen quad
        glBindVertexArray(vao)
        glDrawArrays(GL_TRIANGLE_FAN, 0, 4)
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
        glDeleteBuffers(pointsBuffer)
        glDeleteProgram(shaderProgram)
    }
}

package org.combustiblelemon.covenantremnants.utils.render

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.ViewportAPI
import com.fs.starfarer.api.graphics.SpriteAPI
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL20
import org.lwjgl.util.vector.Vector2f

class SkeletalAnimationTest(
    private val sprite: SpriteAPI
) {
    // Shader program and uniforms
    private var shaderProgram = 0
    private var positionAttribute = 0
    private var texCoordAttribute = 0
    private var transformUniform = 0
    private var colorUniform = 0
    private var textureUniform = 0
    private var bonePositionsUniform = 0
    private var screenSizeUniform = 0
    private var vbo = 0

    private val scaling = Vector2f(0.5f, 0.89f)

    private val vertexShaderSource = """
        #version 330
        in vec2 position;
        in vec2 texCoord;
        uniform mat4 transform;
        uniform vec4 color;
        uniform vec2 screenSize;  // Screen dimensions in pixels
        out vec4 vColor;
        out vec2 vTexCoord;
        out vec2 vPosition;  // NDC coordinates (-1 to 1)
        out vec2 vPixelPos;  // Screen pixel coordinates (0 to screenSize)

        void main() {
            // Apply transform to position
            vec4 transformedPos = transform * vec4(position, 0.0, 1.0);
            gl_Position = transformedPos;

            // Transform texture coordinates to make texture twice as wide
            vTexCoord = texCoord * vec2(0.5, 0.89);  // Scale x by 0.5 to make texture twice as wide

            vColor = color;
            vPosition = transformedPos.xy;  // NDC coordinates (-1 to 1)

            // Convert NDC to screen pixel coordinates
            vPixelPos = (transformedPos.xy * 0.5 + 0.5) * screenSize;
        }
    """.trimIndent()

    private val fragmentShaderSource = """
        #version 330
        in vec4 vColor;
        in vec2 vTexCoord;  // Texture coordinates (0 to 1)
        in vec2 vPosition;  // NDC coordinates (-1 to 1)
        in vec2 vPixelPos;  // Screen pixel coordinates (0 to screenSize)
        uniform sampler2D texture;
        uniform vec2 bonePositions[5];
        out vec4 fragColor;

        void main() {
            vec2 texScaling = vec2(0.5, 0.89);

            // Sample texture using texture coordinates
            vec4 texColor = texture2D(texture, vTexCoord);

            // Calculate distance from bottom of quad using texture coordinates
            // vTexCoord.y ranges from 0 (top) to 1 (bottom)
            // Account for the vertical scaling
            float distanceFromQuadBottom = (1.0 - vTexCoord.y / texScaling.y) * textureSize(texture, 0).y;
            float distanceFromQuadTop = (vTexCoord.y / texScaling.y) * textureSize(texture, 0).y;

            // Draw white lines at top and bottom
            if (abs(distanceFromQuadBottom - 1.0) < 1.0 || abs(distanceFromQuadTop - 1.0) < 1.0) {
                fragColor = vec4(1.0, 1.0, 1.0, 1.0);
            } else {
                fragColor = texColor;
            }
        }
    """.trimIndent()

    init {
        // Create shader program
        val vertexShader = GL20.glCreateShader(GL20.GL_VERTEX_SHADER)
        GL20.glShaderSource(vertexShader, vertexShaderSource)
        GL20.glCompileShader(vertexShader)

        val fragmentShader = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER)
        GL20.glShaderSource(fragmentShader, fragmentShaderSource)
        GL20.glCompileShader(fragmentShader)

        shaderProgram = GL20.glCreateProgram()
        GL20.glAttachShader(shaderProgram, vertexShader)
        GL20.glAttachShader(shaderProgram, fragmentShader)
        GL20.glLinkProgram(shaderProgram)

        // Get uniform locations
        positionAttribute = GL20.glGetAttribLocation(shaderProgram, "position")
        texCoordAttribute = GL20.glGetAttribLocation(shaderProgram, "texCoord")
        transformUniform = GL20.glGetUniformLocation(shaderProgram, "transform")
        colorUniform = GL20.glGetUniformLocation(shaderProgram, "color")
        textureUniform = GL20.glGetUniformLocation(shaderProgram, "texture")
        bonePositionsUniform = GL20.glGetUniformLocation(shaderProgram, "bonePositions")
        screenSizeUniform = GL20.glGetUniformLocation(shaderProgram, "screenSize")

        // Create VBO
        vbo = GL15.glGenBuffers()
    }

    fun render(viewport: ViewportAPI) {
        GL20.glUseProgram(shaderProgram)

        // Enable blending
        GL11.glEnable(GL11.GL_BLEND)
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)

        // Bind texture
        GL20.glUniform1i(textureUniform, 0)
        sprite.bindTexture()

        // Get screen dimensions
        val screenWidth = Global.getSettings().screenWidthPixels.toFloat()
        val screenHeight = Global.getSettings().screenHeightPixels.toFloat()

        // Set screen size uniform
        GL20.glUniform2f(screenSizeUniform, screenWidth, screenHeight)

        val aspectRatio = sprite.width / sprite.height

        val zoom = viewport.viewMult

        // Calculate scale to fit in NDC space (-1 to 1) while maintaining aspect ratio
        val scale = 1f / zoom // Base scale
        val scaleX = scale * aspectRatio  // Scale for width
        val scaleY = scale // Scale for height

        // Create transformation matrix to center the sprite in NDC space
        val transform = floatArrayOf(
            scaleX, 0f, 0f, 0f,  // Scale X
            0f, scaleY, 0f, 0f,  // Scale Y
            0f, 0f, 1f, 0f,     // No Z translation
            0f, 0f, 0f, 1f       // No W translation
        )

        val transformBuffer = BufferUtils.createFloatBuffer(16)
        transformBuffer.put(transform)
        transformBuffer.flip()

        // Create bone positions in NDC space (-1 to 1)
        val bonePositions = listOf(
            Vector2f(0f, -1f), // Top
            Vector2f(0f, -0.5f),
            Vector2f(0f, 0f),  // Center
            Vector2f(0f, 0.5f),
            Vector2f(0f, 1f)   // Bottom
        )

        // Set bone positions uniform
        for (i in bonePositions.indices) {
            GL20.glUniform2f(
                GL20.glGetUniformLocation(shaderProgram, "bonePositions[$i]"),
                bonePositions[i].x, bonePositions[i].y
            )
        }

        // Create vertices for the sprite quad in NDC space (-1 to 1)
        val height = 1f
        val width = 1f  // Scale width based on aspect ratio
        val vertexData = floatArrayOf(
            // Position (x,y) and Texture Coordinates (u,v)
            -width, -height, 0f, 0f,  // Bottom-left
            width, -height, 1f, 0f,   // Bottom-right
            -width, height, 0f, 1f,   // Top-left
            width, height, 1f, 1f     // Top-right
        )

        val vertexBuffer = BufferUtils.createFloatBuffer(vertexData.size)
        vertexBuffer.put(vertexData)
        vertexBuffer.flip()

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo)
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertexBuffer, GL15.GL_STATIC_DRAW)

        GL20.glEnableVertexAttribArray(positionAttribute)
        GL20.glEnableVertexAttribArray(texCoordAttribute)

        val stride = 4 * 4 // 4 floats * 4 bytes per float
        GL20.glVertexAttribPointer(positionAttribute, 2, GL11.GL_FLOAT, false, stride, 0L)
        GL20.glVertexAttribPointer(texCoordAttribute, 2, GL11.GL_FLOAT, false, stride, 8L)

        // Draw the sprite with bone lines
        GL20.glUniform4f(colorUniform, 1f, 1f, 1f, 1f)
        GL20.glUniformMatrix4(transformUniform, false, transformBuffer)
        GL11.glDrawArrays(GL11.GL_TRIANGLE_STRIP, 0, 4)

        GL20.glDisableVertexAttribArray(positionAttribute)
        GL20.glDisableVertexAttribArray(texCoordAttribute)
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0)

        GL20.glUseProgram(0)
    }
}

package org.combustiblelemon.covenantremnants.utils.render

import com.fs.starfarer.api.graphics.SpriteAPI
import org.lwjgl.util.vector.Vector2f

// Simplified Bone class without weight information
data class Bone(
    val pos: Vector2f,
    val rotation: Float,
    val length: Float
)

class Animation(
    val sprite: SpriteAPI,
    val animationSize: Vector2f,
    val defaultBonePositions: List<Bone>,
    val duration: Float,
    val loop: Boolean = true,
    val loopBackwards: Boolean = false,
    val keyframes: List<Pair<Float, List<Bone>>> = emptyList()
) {
    // Mesh handler for this animation
    private var meshHandler: AnimationMesh? = null

    // Validate that all keyframes have the same number of bones as default positions
    init {
        require(keyframes.all { it.second.size == defaultBonePositions.size }) {
            "All keyframes must have the same number of bones as default positions"
        }
        require(keyframes.isNotEmpty()) {
            "Animation must have at least one keyframe"
        }
        require(duration > 0f) {
            "Animation duration must be positive"
        }
    }

    /**
     * Create and return a mesh handler for this animation
     * @param gridRows Number of rows in the vertex grid
     * @param gridCols Number of columns in the vertex grid
     * @param customVertices Optional custom vertices (if not provided, a grid will be created)
     * @return The created AnimationMesh instance
     */
    fun createMesh(
        gridRows: Int = 20,
        gridCols: Int = 8,
        customVertices: List<Vertex>? = null
    ): AnimationMesh {
        val vertices = customVertices ?: AnimationMesh.createVertexGrid(sprite, gridRows, gridCols)

        // Create the mesh handler
        val mesh = AnimationMesh(
            sprite = sprite,
            originBones = defaultBonePositions,
            vertices = vertices,
            gridRows = gridRows,
            gridCols = gridCols
        )

        // Store a reference to the created mesh
        meshHandler = mesh

        return mesh
    }

    /**
     * Get the active mesh handler or create one if it doesn't exist
     */
    fun getMesh(): AnimationMesh {
        return meshHandler ?: createMesh()
    }

    /**
     * Generate a mesh for the current animation state at the given time
     * @param time Current animation time
     * @return Float array with vertex data
     */
    fun generateMesh(time: Float): FloatArray {
        val currentBones = getBonePositions(time)
        return getMesh().generateWeightedMesh(currentBones)
    }

    // Get bone positions at a specific time
    fun getBonePositions(time: Float): List<Bone> {
        if (keyframes.size == 1) return keyframes[0].second

        // Sort keyframes to ensure they're in chronological order
        val sortedKeyframes = keyframes.sortedBy { it.first }
        val firstKeyframe = sortedKeyframes.first()
        val lastKeyframeTime = sortedKeyframes.last().first

        val normalizedTime = if (loop) {
            if (loopBackwards) {
                // For loopBackwards, we want a smooth transition from end to start
                // First normalize to 0-1 range over the full cycle
                val fullCycleDuration = lastKeyframeTime * 2
                val cyclePosition = (time % fullCycleDuration) / fullCycleDuration

                // Convert to actual time value
                if (cyclePosition <= 0.5f) {
                    // First half of cycle - going forward
                    cyclePosition * 2 * lastKeyframeTime
                } else {
                    // Second half of cycle - going backward
                    (1f - cyclePosition) * 2 * lastKeyframeTime
                }
            } else {
                // Standard looping - just wrap around duration
                time % duration
            }
        } else {
            time.coerceIn(0f, duration)
        }

        // If we're at or below the first keyframe time, return the first keyframe
        if (normalizedTime <= firstKeyframe.first) {
            return firstKeyframe.second
        }

        // If we're at or above the last keyframe time, return the last keyframe
        if (normalizedTime >= lastKeyframeTime) {
            return sortedKeyframes.last().second
        }

        // Find the keyframes to interpolate between
        val (prevFrame, nextFrame) = findKeyframes(normalizedTime)

        // Calculate interpolation factor
        val t = (normalizedTime - prevFrame.first) / (nextFrame.first - prevFrame.first)

        // Interpolate between keyframes
        return interpolateBones(prevFrame.second, nextFrame.second, t)
    }

    // Find the keyframes to interpolate between
    private fun findKeyframes(time: Float): Pair<Pair<Float, List<Bone>>, Pair<Float, List<Bone>>> {
        val sortedKeyframes = keyframes.sortedBy { it.first }

        // Find the first keyframe after the current time
        val nextIndex = sortedKeyframes.indexOfFirst { it.first > time }

        return when {
            nextIndex == 0 -> {
                // Before first keyframe
                if (loop) {
                    sortedKeyframes.last() to sortedKeyframes[0]
                } else {
                    sortedKeyframes[0] to sortedKeyframes[0]
                }
            }

            nextIndex == -1 -> {
                // After last keyframe
                if (loop) {
                    sortedKeyframes.last() to sortedKeyframes[0]
                } else {
                    sortedKeyframes.last() to sortedKeyframes.last()
                }
            }

            else -> {
                // Between two keyframes
                sortedKeyframes[nextIndex - 1] to sortedKeyframes[nextIndex]
            }
        }
    }

    // Interpolate between two sets of bone positions
    private fun interpolateBones(
        start: List<Bone>,
        end: List<Bone>,
        t: Float
    ): List<Bone> {
        return start.zip(end).map { (startBone, endBone) ->
            // Calculate the magnitude of change for position
            val posDiff = Vector2f(
                endBone.pos.x - startBone.pos.x,
                endBone.pos.y - startBone.pos.y
            )
            val posMagnitude = Math.sqrt((posDiff.x * posDiff.x + posDiff.y * posDiff.y).toDouble()).toFloat()

            // Calculate the magnitude of rotation change
            val rotDiff = endBone.rotation - startBone.rotation
            val rotMagnitude = Math.abs(
                if (Math.abs(rotDiff) > 180f) {
                    if (rotDiff > 0f) rotDiff - 360f else rotDiff + 360f
                } else rotDiff
            )

            // Calculate the magnitude of length change
            val lengthDiff = endBone.length - startBone.length
            val lengthMagnitude = Math.abs(lengthDiff)

            // Apply magnitude-based smoothing
            val maxMagnitude = Math.max(Math.max(posMagnitude, rotMagnitude), lengthMagnitude)
            val smoothFactor = when {
                maxMagnitude < 1f -> 1f // No smoothing for very small changes
                maxMagnitude < 5f -> 0.8f // Light smoothing for small changes
                maxMagnitude < 15f -> 0.6f // Medium smoothing for moderate changes
                else -> 0.4f // Strong smoothing for large changes
            }

            // Apply smooth interpolation based on magnitude
            val smoothT = smoothStep(t) * smoothFactor + t * (1f - smoothFactor)

            // Interpolate position with magnitude-based smoothing
            val pos = Vector2f(
                startBone.pos.x + posDiff.x * smoothT,
                startBone.pos.y + posDiff.y * smoothT
            )

            // Interpolate rotation with magnitude-based smoothing
            val rotation = if (Math.abs(rotDiff) > 180f) {
                if (rotDiff > 0f) {
                    startBone.rotation + (rotDiff - 360f) * smoothT
                } else {
                    startBone.rotation + (rotDiff + 360f) * smoothT
                }
            } else {
                startBone.rotation + rotDiff * smoothT
            }

            // Interpolate length with magnitude-based smoothing
            val length = startBone.length + lengthDiff * smoothT

            Bone(pos, rotation, length)
        }
    }

    // Smooth interpolation function (cubic ease-in-out)
    private fun smoothStep(t: Float): Float {
        return t * t * (3f - 2f * t)
    }

    // Get the current animation progress (0 to 1)
    fun getProgress(time: Float): Float {
        return if (loop) {
            val cycleTime = time % (duration * 2)
            if (loopBackwards && cycleTime > duration) {
                (duration * 2 - cycleTime) / duration
            } else {
                (cycleTime % duration) / duration
            }
        } else {
            (time.coerceIn(0f, duration)) / duration
        }
    }

    // Check if animation is complete (only relevant for non-looping animations)
    fun isComplete(time: Float): Boolean {
        return !loop && time >= duration
    }

    // Create a reversed version of this animation
    fun reversed(): Animation {
        return Animation(
            sprite = sprite,
            animationSize = animationSize,
            defaultBonePositions = defaultBonePositions,
            duration = duration,
            loop = loop,
            loopBackwards = loopBackwards,
            keyframes = keyframes.map { (time, bones) ->
                Pair(duration - time, bones)
            }.sortedBy { it.first }
        )
    }

    // Create a mirrored version of this animation (flips x coordinates and rotation)
    fun mirrored(): Animation {
        return Animation(
            sprite = sprite,
            animationSize = animationSize,
            defaultBonePositions = defaultBonePositions.map {
                Bone(Vector2f(-it.pos.x, it.pos.y), -it.rotation, it.length)
            },
            duration = duration,
            loop = loop,
            loopBackwards = loopBackwards,
            keyframes = keyframes.map { (time, bones) ->
                Pair(time, bones.map {
                    Bone(Vector2f(-it.pos.x, it.pos.y), -it.rotation, it.length)
                })
            }
        )
    }

    companion object {
        // Smooth interpolation function (cubic ease-in-out)
        private fun smoothStep(t: Float): Float {
            return t * t * (3f - 2f * t)
        }

        // Create a simple idle animation that slightly moves bones up and down
        fun createIdleAnimation(
            sprite: SpriteAPI,
            animationSize: Vector2f,
            defaultPositions: List<Bone>,
            duration: Float = 4.0f,
            amplitude: Float = 5.0f,
            loop: Boolean = true
        ): Animation {
            // Create keyframes for a gentle up and down motion
            val keyframes = mutableListOf<Pair<Float, List<Bone>>>()

            // Start position (default)
            keyframes.add(0.0f to defaultPositions)

            // Halfway point (offset upward)
            val halfwayBones = defaultPositions.map { bone ->
                Bone(
                    Vector2f(bone.pos.x, bone.pos.y - amplitude), // Move up
                    bone.rotation,
                    bone.length
                )
            }
            keyframes.add(duration / 2 to halfwayBones)

            // End position (back to default)
            keyframes.add(duration to defaultPositions)

            return Animation(
                sprite = sprite,
                animationSize = animationSize,
                defaultBonePositions = defaultPositions,
                duration = duration,
                loop = loop,
                loopBackwards = false,
                keyframes = keyframes
            )
        }

        // Create a bending animation that curves the bones
        fun createBendAnimation(
            sprite: SpriteAPI,
            animationSize: Vector2f,
            defaultPositions: List<Bone>,
            duration: Float = 2.0f,
            maxBend: Float = 45.0f,
            maxOffset: Float = 30.0f,
            loop: Boolean = true,
            loopBackwards: Boolean = false
        ): Animation {
            // Create keyframes for a bending motion
            val keyframes = mutableListOf<Pair<Float, List<Bone>>>()

            // Start position (default)
            keyframes.add(0.0f to defaultPositions)

            // Calculate the middle keyframe with bend
            // We'll bend the bones progressively more as we go up the chain
            val middleBones = defaultPositions.mapIndexed { index, bone ->
                val bendFactor = index.toFloat() / defaultPositions.size
                val bendAngle = maxBend * bendFactor
                val xOffset = maxOffset * bendFactor

                // Calculate new position with offset
                Bone(
                    Vector2f(bone.pos.x + xOffset, bone.pos.y),
                    bone.rotation + bendAngle,
                    bone.length
                )
            }
            keyframes.add(duration / 2 to middleBones)

            // End position (back to default)
            keyframes.add(duration to defaultPositions)

            return Animation(
                sprite = sprite,
                animationSize = animationSize,
                defaultBonePositions = defaultPositions,
                duration = duration,
                loop = loop,
                loopBackwards = loopBackwards,
                keyframes = keyframes
            )
        }
    }

    /**
     * Shader management for animation rendering
     */
    class AnimationShader {
        // Shader program and uniforms
        private var shaderProgram = 0
        private var positionAttribute = 0
        private var texCoordAttribute = 0
        private var triangleIdAttribute = 0
        private var transformUniform = 0
        private var colorUniform = 0
        private var textureUniform = 0
        private var bonePositionsUniform = 0
        private var boneLengthsUniform = 0
        private var screenSizeUniform = 0
        private var vbo = 0

        // Vertex shader source code
        private val vertexShaderSource = """
            #version 330
            in vec2 position;
            in vec2 texCoord;
            in float triangleID;
            uniform mat4 transform;
            uniform vec4 color;
            uniform vec2 screenSize;
            uniform vec2 bonePositions[5];
            uniform float boneLengths[5];
            out vec4 vColor;
            out vec2 vTexCoord;
            out vec2 vPosition;
            out vec2 vPixelPos;
            out float vTriangleId;

            void main() {
                // Start with the original position
                vec2 finalPos = position;

                // Track total weight applied
                float totalWeight = 0.0;

                // Find which bone segment this vertex belongs to based on its y texture coordinate
                float verticalPos = texCoord.y; // 0 to 1 from top to bottom

                // Initialize weighted position
                vec2 weightedPos = vec2(0.0, 0.0);

                // Apply weighted transformations from multiple bones
                // We'll consider influence from all bones with distance-based falloff
                for (int i = 0; i < 4; i++) { // Process each segment (between pairs of bones)
                    // Get bone positions for current segment
                    vec2 startBone = bonePositions[i];
                    vec2 endBone = bonePositions[i + 1];

                    // Calculate segment influence based on vertical position
                    // Strongest near the segment, falloff with distance
                    float segmentSize = 0.25; // Each segment is 1/4 of the sprite
                    float segmentCenter = (float(i) + 0.5) * segmentSize;
                    float distanceToSegment = abs(verticalPos - segmentCenter);

                    // Calculate influence weight based on distance to segment
                    // Higher weight when closer to segment
                    float segmentInfluence = max(0.0, 1.0 - distanceToSegment * 4.0);

                    if (segmentInfluence > 0.0) {
                        // Calculate precise position within this segment (0-1)
                        float segmentStart = float(i) * segmentSize;
                        float segmentProgress = clamp((verticalPos - segmentStart) / segmentSize, 0.0, 1.0);

                        // Calculate bone direction vector (from start to end)
                        vec2 boneDir = normalize(endBone - startBone);

                        // Get perpendicular vector to bone direction (for sprite width)
                        vec2 perpDir = vec2(-boneDir.y, boneDir.x);

                        // Convert from texture space to bone space
                        float distFromCenter = position.x; // Already in -0.5 to 0.5 range

                        // Calculate the exact point along the bone based on segment progress
                        vec2 bonePoint = mix(startBone, endBone, segmentProgress);

                        // Apply perpendicular offset based on the x position to maintain width
                        vec2 transformedPos = bonePoint + perpDir * distFromCenter * 0.5;

                        // Add weighted contribution to final position
                        weightedPos += transformedPos * segmentInfluence;
                        totalWeight += segmentInfluence;
                    }
                }

                // Apply weighted position if we have any influence
                if (totalWeight > 0.0) {
                    finalPos = weightedPos / totalWeight;
                }

                // Pass through triangle ID
                vTriangleId = triangleID;

                // Apply final transform
                vec4 transformedPos = transform * vec4(finalPos, 0.0, 1.0);
                gl_Position = transformedPos;

                // Pass through texture coordinates
                vTexCoord = texCoord;
                vColor = color;
                vPosition = finalPos;
                vPixelPos = (transformedPos.xy * 0.5 + 0.5) * screenSize;
            }
        """.trimIndent()

        // Fragment shader source code
        private val fragmentShaderSource = """
            #version 330
            in vec4 vColor;
            in vec2 vTexCoord;
            in vec2 vPosition;
            in vec2 vPixelPos;
            in float vTriangleId;
            uniform sampler2D texture;
            uniform vec2 bonePositions[5];
            uniform float boneLengths[5];
            uniform vec2 screenSize;
            out vec4 fragColor;

            // Function to get a distinct color for each triangle ID
            vec3 getTriangleColor(float id) {
                // Modulo to make sure we cycle through colors
                float modId = mod(id, 12.0);

                if (modId < 1.0) return vec3(1.0, 0.0, 0.0); // Red
                else if (modId < 2.0) return vec3(0.0, 1.0, 0.0); // Green
                else if (modId < 3.0) return vec3(0.0, 0.0, 1.0); // Blue
                else if (modId < 4.0) return vec3(1.0, 1.0, 0.0); // Yellow
                else if (modId < 5.0) return vec3(0.0, 1.0, 1.0); // Cyan
                else if (modId < 6.0) return vec3(1.0, 0.0, 1.0); // Magenta
                else if (modId < 7.0) return vec3(0.5, 0.0, 0.0); // Dark Red
                else if (modId < 8.0) return vec3(0.0, 0.5, 0.0); // Dark Green
                else if (modId < 9.0) return vec3(0.0, 0.0, 0.5); // Dark Blue
                else if (modId < 10.0) return vec3(0.5, 0.5, 0.0); // Olive
                else if (modId < 11.0) return vec3(0.5, 0.0, 0.5); // Purple
                else return vec3(0.0, 0.5, 0.5); // Teal
            }

            void main() {
                // Sample texture at the passed texture coordinates
                vec4 texColor = texture2D(texture, vTexCoord);

                // Get a color based on triangle ID
                vec3 triangleColor = getTriangleColor(vTriangleId);

                // Apply texture alpha
                fragColor = vec4(triangleColor, texColor.a);
            }
        """.trimIndent()

        init {
            // Create shader program
            val vertexShader = org.lwjgl.opengl.GL20.glCreateShader(org.lwjgl.opengl.GL20.GL_VERTEX_SHADER)
            org.lwjgl.opengl.GL20.glShaderSource(vertexShader, vertexShaderSource)
            org.lwjgl.opengl.GL20.glCompileShader(vertexShader)

            val fragmentShader = org.lwjgl.opengl.GL20.glCreateShader(org.lwjgl.opengl.GL20.GL_FRAGMENT_SHADER)
            org.lwjgl.opengl.GL20.glShaderSource(fragmentShader, fragmentShaderSource)
            org.lwjgl.opengl.GL20.glCompileShader(fragmentShader)

            shaderProgram = org.lwjgl.opengl.GL20.glCreateProgram()
            org.lwjgl.opengl.GL20.glAttachShader(shaderProgram, vertexShader)
            org.lwjgl.opengl.GL20.glAttachShader(shaderProgram, fragmentShader)
            org.lwjgl.opengl.GL20.glLinkProgram(shaderProgram)

            // Get uniform locations
            positionAttribute = org.lwjgl.opengl.GL20.glGetAttribLocation(shaderProgram, "position")
            texCoordAttribute = org.lwjgl.opengl.GL20.glGetAttribLocation(shaderProgram, "texCoord")
            triangleIdAttribute = org.lwjgl.opengl.GL20.glGetAttribLocation(shaderProgram, "triangleID")
            transformUniform = org.lwjgl.opengl.GL20.glGetUniformLocation(shaderProgram, "transform")
            colorUniform = org.lwjgl.opengl.GL20.glGetUniformLocation(shaderProgram, "color")
            textureUniform = org.lwjgl.opengl.GL20.glGetUniformLocation(shaderProgram, "texture")
            bonePositionsUniform = org.lwjgl.opengl.GL20.glGetUniformLocation(shaderProgram, "bonePositions")
            boneLengthsUniform = org.lwjgl.opengl.GL20.glGetUniformLocation(shaderProgram, "boneLengths")
            screenSizeUniform = org.lwjgl.opengl.GL20.glGetUniformLocation(shaderProgram, "screenSize")

            // Create VBO
            vbo = org.lwjgl.opengl.GL15.glGenBuffers()
        }

        /**
         * Render the animation with the current bones state
         */
        fun render(
            sprite: SpriteAPI,
            viewport: com.fs.starfarer.api.combat.ViewportAPI,
            meshData: FloatArray,
            bones: List<Bone>
        ) {
            org.lwjgl.opengl.GL20.glUseProgram(shaderProgram)

            // Enable blending
            org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_BLEND)
            org.lwjgl.opengl.GL11.glBlendFunc(
                org.lwjgl.opengl.GL11.GL_SRC_ALPHA,
                org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA
            )

            // Bind texture
            org.lwjgl.opengl.GL20.glUniform1i(textureUniform, 0)
            sprite.bindTexture()

            // Get screen dimensions
            val screenWidth = com.fs.starfarer.api.Global.getSettings().screenWidthPixels.toFloat()
            val screenHeight = com.fs.starfarer.api.Global.getSettings().screenHeightPixels.toFloat()

            // Set screen size uniform
            org.lwjgl.opengl.GL20.glUniform2f(screenSizeUniform, screenWidth, screenHeight)

            val spriteAspectRatio = sprite.width.toFloat() / sprite.height.toFloat()
            val zoom = viewport.viewMult.toFloat()

            // Calculate scale to fit in NDC space (-1 to 1) while maintaining aspect ratio
            val baseScale = 1f / zoom
            val scaleX = baseScale
            val scaleY = baseScale

            // Create transformation matrix (identity matrix - no transformations)
            val transform = floatArrayOf(
                scaleX, 0f, 0f, 0f,     // Scale X
                0f, scaleY, 0f, 0f,     // Scale Y
                0f, 0f, 1f, 0f,         // No Z scale
                0f, 0f, 0f, 1f          // No translation
            )

            val transformBuffer = org.lwjgl.BufferUtils.createFloatBuffer(16)
            transformBuffer.put(transform)
            transformBuffer.flip()

            // Convert bone positions to NDC space (-1 to 1) and scale by zoom
            val bonePositions = bones.map { bone ->
                // Convert bone positions from sprite space to NDC space
                // Bone positions are relative to the sprite's top-left corner
                val x = (bone.pos.x / sprite.width - 0.5f) * scaleX
                val y = (bone.pos.y / sprite.height - 0.5f) * scaleY
                Vector2f(x, y)
            }

            // Set bone positions uniform
            bonePositions.forEachIndexed { index, it ->
                org.lwjgl.opengl.GL20.glUniform2f(
                    org.lwjgl.opengl.GL20.glGetUniformLocation(shaderProgram, "bonePositions[$index]"),
                    it.x, it.y
                )
            }

            // Set bone lengths uniform (scaled to NDC space)
            bones.forEachIndexed { index, it ->
                org.lwjgl.opengl.GL20.glUniform1f(
                    org.lwjgl.opengl.GL20.glGetUniformLocation(shaderProgram, "boneLengths[$index]"),
                    (it.length / sprite.height) * scaleY  // Scale length relative to sprite height
                )
            }

            // Use the current mesh
            val vertexBuffer = org.lwjgl.BufferUtils.createFloatBuffer(meshData.size)
            vertexBuffer.put(meshData)
            vertexBuffer.flip()

            org.lwjgl.opengl.GL15.glBindBuffer(org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER, vbo)
            org.lwjgl.opengl.GL15.glBufferData(
                org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER,
                vertexBuffer,
                org.lwjgl.opengl.GL15.GL_STATIC_DRAW
            )

            // Enable vertex attributes
            org.lwjgl.opengl.GL20.glEnableVertexAttribArray(positionAttribute)
            org.lwjgl.opengl.GL20.glEnableVertexAttribArray(texCoordAttribute)
            org.lwjgl.opengl.GL20.glEnableVertexAttribArray(triangleIdAttribute)

            // Each vertex has 5 floats: x, y, u, v, triangleID
            val stride = 5 * 4 // 5 floats * 4 bytes per float
            org.lwjgl.opengl.GL20.glVertexAttribPointer(
                positionAttribute,
                2,
                org.lwjgl.opengl.GL11.GL_FLOAT,
                false,
                stride,
                0L
            )
            org.lwjgl.opengl.GL20.glVertexAttribPointer(
                texCoordAttribute,
                2,
                org.lwjgl.opengl.GL11.GL_FLOAT,
                false,
                stride,
                8L
            )
            org.lwjgl.opengl.GL20.glVertexAttribPointer(
                triangleIdAttribute,
                1,
                org.lwjgl.opengl.GL11.GL_FLOAT,
                false,
                stride,
                16L
            )

            // Draw the sprite with bone lines
            org.lwjgl.opengl.GL20.glUniform4f(colorUniform, 1f, 1f, 1f, 1f)
            org.lwjgl.opengl.GL20.glUniformMatrix4(transformUniform, false, transformBuffer)
            org.lwjgl.opengl.GL11.glDrawArrays(
                org.lwjgl.opengl.GL11.GL_TRIANGLES,
                0,
                meshData.size / 5
            ) // Draw all vertices

            // Disable attributes
            org.lwjgl.opengl.GL20.glDisableVertexAttribArray(positionAttribute)
            org.lwjgl.opengl.GL20.glDisableVertexAttribArray(texCoordAttribute)
            org.lwjgl.opengl.GL20.glDisableVertexAttribArray(triangleIdAttribute)
            org.lwjgl.opengl.GL15.glBindBuffer(org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER, 0)

            org.lwjgl.opengl.GL20.glUseProgram(0)
        }

        /**
         * Clean up OpenGL resources
         */
        fun dispose() {
            if (shaderProgram != 0) {
                org.lwjgl.opengl.GL20.glDeleteProgram(shaderProgram)
            }
            if (vbo != 0) {
                org.lwjgl.opengl.GL15.glDeleteBuffers(vbo)
            }
        }
    }
}

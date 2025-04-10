package org.combustiblelemon.covenantremnants.utils.render

import com.fs.starfarer.api.graphics.SpriteAPI
import org.lwjgl.util.vector.Vector2f

/**
 * Represents a bone influence on a vertex
 */
data class BoneWeight(
    val boneIndex: Int,   // Index of the bone in the bones list
    val weight: Float     // Weight of influence (0.0 to 1.0)
)

/**
 * Represents a vertex in the mesh with position and bone influences
 */
data class Vertex(
    val position: Vector2f,      // Position in sprite space
    val boneInfluences: List<BoneWeight> = emptyList() // Bone influences provided by animator
)

/**
 * Handles the conversion of skeletal animation data into renderable mesh data
 */
class AnimationMesh(
    val sprite: SpriteAPI,
    val originBones: List<Bone>,
    val vertices: List<Vertex>, // Vertex data provided by animator
    val gridRows: Int = 20,     // Default mesh density
    val gridCols: Int = 8
) {
    // Cache the sprite dimensions
    private val spriteWidth = sprite.width.toFloat()
    private val spriteHeight = sprite.height.toFloat()
    private val aspectRatio = spriteWidth / spriteHeight

    /**
     * Calculate texture coordinates for a vertex based on its position
     */
    private fun calculateTexCoord(position: Vector2f): Vector2f {
        return Vector2f(
            position.x / spriteWidth,
            position.y / spriteHeight
        )
    }

    /**
     * Generate a weighted mesh using current bones and pre-configured vertices
     * Returns a FloatArray with vertex data in the format: x, y, u, v, triangleID
     */
    fun generateWeightedMesh(bones: List<Bone>): FloatArray {
        val scaledWidth = aspectRatio * 0.5f

        // The result will be a set of triangles in GL_TRIANGLES format
        val result = mutableListOf<Float>()

        // Process each vertex
        for (vertex in vertices) {
            // Calculate deformed position based on bone influences
            val deformedPos = calculateDeformedPosition(vertex, bones)

            // Calculate texture coordinates
            val texCoord = calculateTexCoord(vertex.position)

            // Position in NDC space (-0.5 to 0.5 range)
            val normalizedX = texCoord.x
            val normalizedY = texCoord.y
            val x = (normalizedX - 0.5f) * scaledWidth * 2.0f
            val y = (normalizedY - 0.5f)

            // Determine triangle ID based on the most influential bone
            val primaryBone = vertex.boneInfluences
                .maxByOrNull { it.weight }?.boneIndex ?: 0
            val triangleId = primaryBone.toFloat()

            // Add vertex to the result
            result.add(x)                // x position
            result.add(y)                // y position
            result.add(texCoord.x)       // u texture coordinate
            result.add(texCoord.y)       // v texture coordinate
            result.add(triangleId)       // triangle ID
        }

        // Create triangles from the grid vertices
        val indices = mutableListOf<Int>()
        for (row in 0 until gridRows - 1) {
            for (col in 0 until gridCols - 1) {
                val topLeft = row * gridCols + col
                val topRight = topLeft + 1
                val bottomLeft = (row + 1) * gridCols + col
                val bottomRight = bottomLeft + 1

                // First triangle (top-left, bottom-left, top-right)
                indices.add(topLeft)
                indices.add(bottomLeft)
                indices.add(topRight)

                // Second triangle (bottom-left, bottom-right, top-right)
                indices.add(bottomLeft)
                indices.add(bottomRight)
                indices.add(topRight)
            }
        }

        // Convert indices to vertices
        val triangles = mutableListOf<Float>()
        for (index in indices) {
            // Each vertex has 5 components (x, y, u, v, triangleId)
            val vertexOffset = index * 5
            for (i in 0 until 5) {
                triangles.add(result[vertexOffset + i])
            }
        }

        return triangles.toFloatArray()
    }

    /**
     * Calculate the deformed position of a vertex based on bone influences
     */
    private fun calculateDeformedPosition(vertex: Vertex, bones: List<Bone>): Vector2f {
        // If no bone influences, return original position
        if (vertex.boneInfluences.isEmpty()) {
            return vertex.position
        }

        val deformedPos = Vector2f(0f, 0f)
        var totalWeight = 0f

        // Apply each bone influence
        for (influence in vertex.boneInfluences) {
            val boneIndex = influence.boneIndex
            val weight = influence.weight

            // Skip invalid bone indices
            if (boneIndex < 0 || boneIndex >= bones.size) continue

            val bone = bones[boneIndex]

            // Apply bone transformation to the vertex position
            // Simple offset calculation - more complex deformation would go here
            val offsetX = bone.pos.x - originBones[boneIndex].pos.x
            val offsetY = bone.pos.y - originBones[boneIndex].pos.y

            // Add weighted contribution
            deformedPos.x += (vertex.position.x + offsetX) * weight
            deformedPos.y += (vertex.position.y + offsetY) * weight
            totalWeight += weight
        }

        // Normalize by total weight
        if (totalWeight > 0) {
            deformedPos.x /= totalWeight
            deformedPos.y /= totalWeight
        } else {
            // Fallback to original position
            return vertex.position
        }

        return deformedPos
    }

    /**
     * Generate a simple quad mesh with the specified bones
     * This is a simpler alternative to the weighted mesh
     */
    fun generateQuadMesh(bones: List<Bone>): FloatArray {
        // Each bone will create one horizontal sector
        val rows = bones.size - 1  // Number of sectors (bones - 1 since we connect between bones)
        val scaledWidth = aspectRatio * 0.5f  // Scale the width for proper aspect ratio

        // The result will be a set of triangles in GL_TRIANGLES format
        val result = mutableListOf<Float>()

        // Helper function to add a vertex to the result
        fun addVertex(x: Float, y: Float, u: Float, v: Float, triangleId: Float) {
            result.add(x)            // x position
            result.add(y)            // y position
            result.add(u)            // u texture coordinate
            result.add(v)            // v texture coordinate
            result.add(triangleId)   // triangle ID
        }

        // Normalize bone positions to 0-1 range for texture coords
        val normalizedBonePositions = bones.map { it.pos.y / spriteHeight }

        // Create triangles for each sector between bones
        for (i in 0 until rows) {
            val sectorId = i.toFloat()

            // Calculate normalized y positions for this segment
            val y1 = normalizedBonePositions[i]
            val y2 = normalizedBonePositions[i + 1]

            // Calculate y positions in shader space (-0.5 to 0.5)
            val y1Shader = (y1 - 0.5f)
            val y2Shader = (y2 - 0.5f)

            // Left edge x coordinate - use proper width based on aspect ratio
            val x1 = -scaledWidth
            // Right edge x coordinate
            val x2 = scaledWidth

            // Texture coordinates
            val u1 = 0f
            val u2 = 1f
            val v1 = y1
            val v2 = y2

            // First triangle (bottom-left, top-left, top-right)
            addVertex(x1, y2Shader, u1, v2, sectorId)
            addVertex(x1, y1Shader, u1, v1, sectorId)
            addVertex(x2, y1Shader, u2, v1, sectorId)

            // Second triangle (bottom-left, top-right, bottom-right)
            addVertex(x1, y2Shader, u1, v2, sectorId)
            addVertex(x2, y1Shader, u2, v1, sectorId)
            addVertex(x2, y2Shader, u2, v2, sectorId)
        }

        return result.toFloatArray()
    }

    /**
     * Creates a uniform grid of vertices for a sprite
     */
    companion object {
        /**
         * Creates a uniform grid of vertices for a sprite
         */
        fun createVertexGrid(
            sprite: SpriteAPI,
            rows: Int,
            cols: Int
        ): List<Vertex> {
            val vertices = mutableListOf<Vertex>()
            val width = sprite.width.toFloat()
            val height = sprite.height.toFloat()

            for (row in 0 until rows) {
                for (col in 0 until cols) {
                    val normalizedX = col / (cols - 1f)
                    val normalizedY = row / (rows - 1f)

                    val position = Vector2f(
                        normalizedX * width,
                        normalizedY * height
                    )

                    vertices.add(Vertex(position))
                }
            }

            return vertices
        }
    }
}

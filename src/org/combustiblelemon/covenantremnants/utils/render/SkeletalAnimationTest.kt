package org.combustiblelemon.covenantremnants.utils.render

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.ViewportAPI
import com.fs.starfarer.api.graphics.SpriteAPI
import org.lwjgl.util.vector.Vector2f

class SkeletalAnimationTest(
    private val sprite: SpriteAPI
) {
    // Use the AnimationShader from the Animation class
    private val shader = Animation.AnimationShader()
    private var animationTime = 0f

    // Define default bones
    private val defaultBones = listOf(
        Bone(Vector2f(0f, 7f), -90f, 40f),
        Bone(Vector2f(0f, 47f), -90f, 40f),
        Bone(Vector2f(0f, 87f), -90f, 40f),
        Bone(Vector2f(0f, 127f), -90f, 40f),
        Bone(Vector2f(0f, 167f), -90f, 40f)
    )

    // Create animation with proper bone chain
    private val animation = Animation.createBendAnimation(
        sprite,
        Vector2f(119f, 100f),
        defaultBones,
        duration = 2.0f,
        maxBend = 80.0f,
        maxOffset = 50.0f,
        loopBackwards = true
    )

    // Create mesh using the Animation class

    private var initialMesh: FloatArray? = null

    init {
        animation.createMesh(
            gridRows = 20,
            gridCols = 2,
            // Vector2( 0, 175.528 ), Vector2( 0, 133.934 ), Vector2( 0, 93.7154 ), Vector2( 0, 93.7154 ), Vector2( 0, 93.7154 ), Vector2( 0, 51.7778 ), Vector2( 0, 0 ), Vector2( 0, 0 ), Vector2( 35, 0 ), Vector2( 35, 53.1528 ), Vector2( 35, 95.4341 ), Vector2( 35, 136.684 ), Vector2( 35, 177.934 ), Vector2( 35, 224 ), Vector2( 0, 224 )
            listOf(Vertex(Vector2f(), listOf(BoneWeight(0, 1f))))
        )
        // Generate initial mesh based on default bone positions
        initialMesh = animation.generateMesh(0f)
    }

    // Advance the animation by the given time delta
    fun advance(deltaTime: Float) {
        if (Global.getCombatEngine().isPaused) {
            return
        }

        // Use a consistent animation speed
        val animationSpeed = 0.75f  // Slightly faster but still visible
        animationTime += deltaTime * animationSpeed

        // Simple time cycling to ensure smooth transitions
        if (animationTime > animation.duration) {
            animationTime %= animation.duration
        }
    }

    fun render(viewport: ViewportAPI) {
        // Update animation time
        advance(Global.getCombatEngine().elapsedInLastFrame)

        // Generate mesh with current bone positions
        val meshData = animation.generateMesh(animationTime)
        val currentBones = animation.getBonePositions(animationTime)

        // Use the shader from Animation class to render
        shader.render(sprite, viewport, meshData, currentBones)
    }

    // Clean up resources when no longer needed
    fun dispose() {
        shader.dispose()
    }
}

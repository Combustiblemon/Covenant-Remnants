# Skeletal Animation System

This system provides skeletal animation for Starsector game sprites. It allows for efficient, smooth deformation of sprites using bone-based animations.

## Core Components

The system has been split into three main classes to improve separation of concerns:

### Animation Class

The `Animation` class handles the animation timeline and keyframes:

- Manages keyframe data and interpolation
- Provides animation timing (loops, playback, etc.)
- Calculates bone positions at any point in time
- Handles animation transformations like mirroring or reversing
- Creates and manages the `AnimationMesh` instance for vertex generation

```kotlin
// Example: Create a simple animation
val animation = Animation(
    sprite = mySprite,
    animationSize = Vector2f(120f, 200f),
    defaultBonePositions = listOfBones,
    duration = 2.0f,
    loop = true,
    loopBackwards = false,
    keyframes = listOf(
        0.0f to startBones,
        1.0f to endBones,
        2.0f to startBones
    )
)

// Create a mesh for this animation (automatically stored in the animation)
val mesh = animation.createMesh(
    gridRows = 20,
    gridCols = 8
)

// Get mesh data for the current animation time (using the stored mesh)
val meshData = animation.generateMesh(currentTime)
```

### AnimationShader Class

The `AnimationShader` class handles the rendering of skeletal animations:

- Part of the `Animation` class (access via `Animation.AnimationShader()`)
- Manages shader programs and attributes for skeletal deformation
- Renders meshes with bone-based transformations
- Applies debug visualization of bone segments

```kotlin
// Example: Create a shader and render an animation
val shader = Animation.AnimationShader()

// In render loop
fun render(viewport: ViewportAPI) {
    // Generate mesh for current frame, including bone positions
    val meshData = animation.generateMesh(currentTime)
    val currentBones = animation.getBonePositions(currentTime)

    // Render using the shader
    shader.render(sprite, viewport, meshData, currentBones)
}

// When done
fun dispose() {
    shader.dispose() // Clean up OpenGL resources
}
```

### Vertex Class

The `Vertex` class represents a point in the mesh with position and bone influences:

- Contains the position in sprite space (x, y coordinates)
- Contains a list of bone influences provided by the animator
- Texture coordinates are calculated automatically based on sprite dimensions

```kotlin
// Example: Create a vertex with bone influences
val vertex = Vertex(
    position = Vector2f(50f, 75f),  // Position in sprite space
    boneInfluences = listOf(
        BoneWeight(0, 0.7f),  // 70% influenced by bone 0
        BoneWeight(1, 0.3f)   // 30% influenced by bone 1
    )
)
```

### BoneWeight Class

The `BoneWeight` class represents how much influence a bone has on a vertex:

```kotlin
data class BoneWeight(
    val boneIndex: Int,   // Index of the bone in the bones list
    val weight: Float     // Weight of influence (0.0 to 1.0)
)
```

### AnimationMesh Class

The `AnimationMesh` class handles mesh generation and rendering:

- Created and managed by the `Animation` class
- Creates vertex data for rendering from a list of predefined vertices
- Uses bone influences provided by the animator (not calculated internally)
- Generates triangle meshes from bones
- Handles mesh deformation based on bone positions and vertex influences
- Calculates texture coordinates from vertex positions

```kotlin
// This class is now managed by the Animation class:
// To create a mesh:
val mesh = animation.createMesh(
    gridRows = 20,
    gridCols = 8,
    customVertices = myCustomVertices // Optional
)

// To generate mesh data for the current frame:
val meshData = animation.generateMesh(currentTime)
```

## Mesh Grid Parameters and Custom Vertices

The animation system creates a triangular mesh to deform the sprite smoothly during animation. Two key parameters control this process:

### Grid Parameters

- **gridRows** (default: 20): Number of horizontal divisions in the mesh. More rows allow for smoother vertical bending.
- **gridCols** (default: 8): Number of vertical divisions in the mesh. More columns allow for smoother horizontal stretching.

These parameters create a mesh with:

- (gridRows × gridCols) vertices
- (gridRows-1) × (gridCols-1) × 2 triangles

Higher values create smoother animations but require more processing power.

### Custom Vertices

For advanced usage, you can provide custom vertices instead of using the automatically generated grid:

```kotlin
// Create custom vertices (must have exactly gridRows × gridCols elements)
val customVertices = mutableListOf<Vertex>()

// Creating a 4×3 grid of custom vertices
val gridRows = 4
val gridCols = 3

for (row in 0 until gridRows) {
    for (col in 0 until gridCols) {
        // Calculate position in sprite space
        val posX = col / (gridCols - 1f) * sprite.width
        val posY = row / (gridRows - 1f) * sprite.height

        // Create custom vertex with specific position and bone influences
        customVertices.add(Vertex(
            position = Vector2f(posX, posY),
            boneInfluences = listOf(
                BoneWeight(0, 0.7f), // Primary bone influence
                BoneWeight(1, 0.3f)  // Secondary bone influence
            )
        ))
    }
}

// Create mesh with custom vertices
animation.createMesh(gridRows, gridCols, customVertices)
```

When using custom vertices:

1. The `customVertices` list must contain exactly `gridRows` × `gridCols` vertices
2. Vertices should be ordered in row-major format (row by row)
3. The `gridRows` and `gridCols` parameters are still required for triangulation
4. The system will not generate an automatic grid; your vertices will be used directly
5. Texture coordinates are automatically calculated from vertex positions based on sprite dimensions

Custom vertices are most useful for:

- Precise control over bone influences
- Non-uniform vertex distribution (more vertices in areas that need more detail)
- Complex deformation behaviors for character animation

## Bone Structure

Bones represent the skeleton that drives the animation:

```kotlin
data class Bone(
    val pos: Vector2f,      // Position in sprite space
    val rotation: Float,    // Rotation in degrees (absolute, not relative)
    val length: Float       // Length in pixels
)
```

## Usage Example

```kotlin
// 1. Define bones
val bones = listOf(
    Bone(Vector2f(0f, 0f), -90f, 40f),
    Bone(Vector2f(0f, 40f), -90f, 40f),
    Bone(Vector2f(0f, 80f), -90f, 40f)
)

// 2. Create animation
val animation = Animation.createBendAnimation(
    sprite = mySprite,
    animationSize = Vector2f(120f, 200f),
    defaultPositions = bones,
    duration = 1.5f,
    maxBend = 45.0f,
    loopBackwards = true
)

// 3. Create mesh using the animation
// You can provide custom vertices if needed
val customVertices = createCustomVerticesWithBoneInfluences()
animation.createMesh(
    gridRows = 20,
    gridCols = 8,
    customVertices = customVertices // Optional
)

// 4. Create shader for rendering
val shader = Animation.AnimationShader()

// 5. In your render loop:
fun render(time: Float, viewport: ViewportAPI) {
    // Generate mesh data for current frame
    val meshData = animation.generateMesh(time)
    val currentBones = animation.getBonePositions(time)

    // Render using the shader
    shader.render(mySprite, viewport, meshData, currentBones)
}

// 6. Clean up resources when done
fun dispose() {
    shader.dispose()
}
```

## Helper Functions

The system provides several helper functions:

### Animation Creation

- `Animation.createBendAnimation()` - Creates an animation with bending motion
- `Animation.createIdleAnimation()` - Creates a simple idle animation with up and down motion

### Mesh Creation

- `Animation.createMesh()` - Creates and stores an AnimationMesh instance in the Animation
- `Animation.generateMesh()` - Generates mesh data for the current animation time
- `AnimationMesh.createVertexGrid()` - Creates a uniform grid of vertices without bone influences

## Implementation Notes

- The rotation angle of bones is absolute relative to the sprite, not relative to parent bones.
- Bone weights/influences are now provided directly by the animator, rather than being calculated internally.
- Texture coordinates are automatically calculated from vertex positions using the sprite dimensions.
- The vertex grid is created separately from bone influences, allowing custom weighting schemes.
- For best results, use 4-5 bones for most sprites, with more complex sprites potentially using more.
- The mesh density (gridRows/gridCols) should be tuned based on the sprite dimensions and detail level.
- The shader provides debug visualization using colors to show bone segment influence on mesh triangles.

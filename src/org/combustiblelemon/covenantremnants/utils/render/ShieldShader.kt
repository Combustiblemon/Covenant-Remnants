package org.combustiblelemon.covenantremnants.utils.render

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ViewportAPI
import org.combustiblelemon.lemonlib.render.Shader
import org.combustiblelemon.lemonlib.render.composition.ComposedShaderProgram
import org.combustiblelemon.lemonlib.render.composition.ShaderLayer
import org.combustiblelemon.covenantremnants.utils.render.shield.BorderLayer
import org.combustiblelemon.covenantremnants.utils.render.shield.DebugLayer
import org.combustiblelemon.covenantremnants.utils.render.shield.FillLayer
import org.combustiblelemon.covenantremnants.utils.render.shield.HoneycombLayer
import org.combustiblelemon.lemonlib.geometry.PolygonSmoothing
import org.combustiblelemon.covenantremnants.utils.render.shield.RippleLayer
import org.combustiblelemon.lemonlib.geometry.SmoothingMethod
import org.combustiblelemon.covenantremnants.utils.render.shield.SweepLayer
import org.combustiblelemon.lemonlib.geometry.rotatePointByDegrees
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL15.*
import org.lwjgl.opengl.GL20.*
import org.lwjgl.opengl.GL30.*
import org.lwjgl.util.vector.Vector2f

// TODO: Fix ripple effects to be consistent across zoom levels. Fix ripple radius and width to be consistent across zoom levels. Ripple is much faster zoomed out than zoomed in.

/**
 * Covenant shield renderer, built on LemonLib's shader composition system
 * (see LemonLib/docs/shader-composition/). Each visual layer (fill, border, sweep,
 * ripples, honeycomb, debug) is a [ShaderLayer] owning its GLSL module +
 * uniforms; [ComposedShaderProgram] assembles them into a single program.
 *
 * This class owns the cross-cutting pieces: polygon points + smoothing, sweep
 * position calculation, bounding-box quad, GL state handling, and the shared
 * uniforms.
 */
class ShieldShader(
    private val ship: ShipAPI,
    private val boundsCount: Int,
    private val smoothingMethod: SmoothingMethod = SmoothingMethod.NONE
) : Shader {
    private var polygonPoints: List<Float> = listOf()
    private var vbo: Int = 0
    private var vao: Int = 0

    private var scaleX: Float = 1.0f
    private var scaleY: Float = 1.0f
    private var screenWidth: Float = 1920f
    private var screenHeight: Float = 1080f
    private var splineSubdivisions: Int = 8
    private var enableBoundingBoxOptimization: Boolean = true // Re-enabled, scissor test disabled

    // Pre-calculated rotation matrix for vertex shader optimization
    private var shipRotationMatrix: FloatArray = floatArrayOf(1f, 0f, 0f, 1f) // Identity matrix

    // Viewport scale for world-to-screen conversion
    private var viewportScaleX: Float = 1.0f
    private var viewportScaleY: Float = 1.0f

    // Bounding box for optimization
    private var boundingBoxMinX: Float = 0f
    private var boundingBoxMaxX: Float = 0f
    private var boundingBoxMinY: Float = 0f
    private var boundingBoxMaxY: Float = 0f
    private var boundingBoxWidth: Float = 0f
    private var boundingBoxHeight: Float = 0f

    private var time = 0f

    // Layers — list order is both the GLSL concatenation order and the
    // uniform-upload order. Cross-module reads (sweep reads borderColor)
    // may only point backwards in this list.
    private val fillLayer = FillLayer()
    private val borderLayer = BorderLayer()
    private val sweepLayer = SweepLayer()
    private val rippleLayer = RippleLayer()
    private val honeycombLayer = HoneycombLayer()
    private val debugLayer = DebugLayer()

    private val layers: List<ShaderLayer> =
        listOf(fillLayer, borderLayer, sweepLayer, rippleLayer, honeycombLayer, debugLayer)

    private val program = ComposedShaderProgram(
        systemName = "shield",
        vertexShaderFile = "data/shaders/shield/shield.vert",
        baseFragmentFile = "data/shaders/shield/base.frag",
        layers = layers,
        placeholders = mapOf(
            "POINT_COUNT_PLACEHOLDER" to calculateMaxPointsAfterSmoothing(boundsCount).toString()
        )
    )

    init {
        initQuad()
    }

    private fun initQuad() {
        // Create VAO
        vao = glGenVertexArrays()
        glBindVertexArray(vao)

        // Create VBO - initially with full-screen quad, will be updated with bounding box
        // Each vertex is just NDC coords (2 floats)
        val vertices = floatArrayOf(
            -1.0f, -1.0f,  // Bottom-left
            1.0f, -1.0f,   // Bottom-right
            1.0f, 1.0f,    // Top-right
            -1.0f, 1.0f    // Top-left
        )

        vbo = glGenBuffers()
        glBindBuffer(GL_ARRAY_BUFFER, vbo)

        // Convert float array to FloatBuffer
        val buffer = BufferUtils.createFloatBuffer(vertices.size)
        buffer.put(vertices)
        buffer.flip()

        glBufferData(GL_ARRAY_BUFFER, buffer, GL_DYNAMIC_DRAW) // DYNAMIC_DRAW for updates

        // Set up vertex attributes
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 2 * 4, 0) // NDC coordinates
        glEnableVertexAttribArray(0)

        glBindVertexArray(0)
    }

    private fun updateQuadVertices() {
        if (boundingBoxWidth <= 0f || boundingBoxHeight <= 0f) {
            // Fallback to full-screen quad if bounding box is invalid
            val vertices = floatArrayOf(
                -1.0f, -1.0f,  // Bottom-left
                1.0f, -1.0f,   // Bottom-right
                1.0f, 1.0f,    // Top-right
                -1.0f, 1.0f    // Top-left
            )
            updateVBO(vertices)
            return
        }

        // Convert screen coordinates to NDC coordinates
        val left = (boundingBoxMinX / screenWidth) * 2.0f - 1.0f
        val right = (boundingBoxMaxX / screenWidth) * 2.0f - 1.0f
        val bottom = (boundingBoxMinY / screenHeight) * 2.0f - 1.0f
        val top = (boundingBoxMaxY / screenHeight) * 2.0f - 1.0f

        val vertices = floatArrayOf(
            left, bottom,   // Bottom-left
            right, bottom,  // Bottom-right
            right, top,     // Top-right
            left, top       // Top-left
        )

        updateVBO(vertices)
    }

    private fun updateVBO(vertices: FloatArray) {
        glBindBuffer(GL_ARRAY_BUFFER, vbo)

        // Convert float array to FloatBuffer
        val buffer = BufferUtils.createFloatBuffer(vertices.size)
        buffer.put(vertices)
        buffer.flip()

        // Update the buffer data
        glBufferSubData(GL_ARRAY_BUFFER, 0, buffer)

        glBindBuffer(GL_ARRAY_BUFFER, 0)
    }

    /**
     * Calculate the maximum number of points that could be generated after smoothing.
     * All current smoothing methods only reposition points, so this is the original
     * count capped at the shader array limit.
     */
    private fun calculateMaxPointsAfterSmoothing(originalPointCount: Int): Int {
        return minOf(originalPointCount, 1000) // Maximum 1000 points for shader array
    }

    private fun setPolygonPointUniforms(pointCount: Int) {
        // Set polygon points directly as uniforms instead of using UBO
        val actualPointCount = polygonPoints.size / 2

        // Safety check: ensure polygonPoints has even number of elements
        if (polygonPoints.size % 2 != 0) {
            Global.getLogger(this.javaClass).warn("polygonPoints has odd number of elements: ${polygonPoints.size}")
        }

        // Safety check: ensure we don't exceed the shader array size
        val safePointCount = minOf(actualPointCount, pointCount)

        // Set actual polygon points as uniforms (screen coordinates)
        for (i in 0 until safePointCount) {
            val x = polygonPoints[i * 2]
            val y = polygonPoints[i * 2 + 1]
            val uniformLocation = glGetUniformLocation(program.programId, "points[$i]")
            glUniform2f(uniformLocation, x, y)
        }

        // Set remaining array elements to zero
        for (i in safePointCount until pointCount) {
            val uniformLocation = glGetUniformLocation(program.programId, "points[$i]")
            glUniform2f(uniformLocation, 0.0f, 0.0f)
        }

        // Check for OpenGL errors
        val error = glGetError()
        if (error != GL_NO_ERROR) {
            Global.getLogger(this.javaClass).error("OpenGL error after setting uniforms: $error")
        }
    }

    private fun calculateBoundingBox() {
        if (polygonPoints.size < 6) {
            boundingBoxMinX = 0f
            boundingBoxMaxX = 0f
            boundingBoxMinY = 0f
            boundingBoxMaxY = 0f
            boundingBoxWidth = 0f
            boundingBoxHeight = 0f
            debugLayer.setBoundingBox(0f, 0f, 0f, 0f)
            return
        }

        // Calculate screen-space bounding box of the shield polygon
        var minX = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var minY = Float.MAX_VALUE
        var maxY = Float.MIN_VALUE

        // polygonPoints contains screen coordinates
        for (i in polygonPoints.indices step 2) {
            val screenX = polygonPoints[i]
            val screenY = polygonPoints[i + 1]

            minX = minOf(minX, screenX)
            maxX = maxOf(maxX, screenX)
            minY = minOf(minY, screenY)
            maxY = maxOf(maxY, screenY)
        }

        // Add margin for border effects and safety. The 10f floor preserves the
        // pre-refactor effective margin (was maxOf(borderWidth, glowWidth) with
        // glowWidth = 10 at runtime, before the dead glow effect was removed).
        val margin = maxOf(borderLayer.borderWidth, 10f) + 10f
        boundingBoxMinX = minX - margin
        boundingBoxMaxX = maxX + margin
        boundingBoxMinY = minY - margin
        boundingBoxMaxY = maxY + margin
        boundingBoxWidth = boundingBoxMaxX - boundingBoxMinX
        boundingBoxHeight = boundingBoxMaxY - boundingBoxMinY

        debugLayer.setBoundingBox(boundingBoxMinX, boundingBoxMinY, boundingBoxMaxX, boundingBoxMaxY)
    }

    fun updatePolygon(newPoints: List<Vector2f>, viewport: ViewportAPI, scale: Vector2f = Vector2f(1.0f, 1.0f)) {
        scaleX = scale.x
        scaleY = scale.y

        // Get viewport scale for world-to-screen conversion
        viewportScaleX = viewport.viewMult
        viewportScaleY = viewport.viewMult

        // Validate input points
        if (newPoints.size < 3) { // Need at least 3 points (6 coordinates)
            Global.getLogger(this.javaClass).warn("Invalid polygon - need at least 3 points, got ${newPoints.size / 2}")
            polygonPoints = listOf()
            return
        }

        // Store world coordinates directly - transformations are applied below
        val worldPoints = mutableListOf<Float>()
        for (segment in newPoints) {
            worldPoints.add(segment.x)
            worldPoints.add(segment.y)
        }

        // Apply smoothing to world coordinates first
        val smoothedWorldPoints = PolygonSmoothing.smooth(worldPoints, smoothingMethod, splineSubdivisions)

        // Convert world points to screen coordinates for the fragment shader
        val screenPoints = mutableListOf<Float>()
        var sweepMinX = Float.MAX_VALUE
        var sweepMaxX = Float.MIN_VALUE

        // Get ship center in screen coordinates for consistent calculations
        val shipScreenX = viewport.convertWorldXtoScreenX(ship.location.x)
        val shipScreenY = viewport.convertWorldYtoScreenY(ship.location.y)

        for (i in smoothedWorldPoints.indices step 2) {
            val worldPoint = Vector2f(smoothedWorldPoints[i], smoothedWorldPoints[i + 1])
            val scaledPoint = Vector2f(worldPoint.x * scaleX, worldPoint.y * scaleY)
            val translatedPoint = Vector2f(scaledPoint.x + ship.location.x, scaledPoint.y + ship.location.y)
            val rotatedPoint = rotatePointByDegrees(translatedPoint, ship.location, ship.facing)

            // Convert to screen coordinates using viewport
            val screenX = viewport.convertWorldXtoScreenX(rotatedPoint.x)
            val screenY = viewport.convertWorldYtoScreenY(rotatedPoint.y)

            // Calculate ship-relative coordinates in screen space for sweep bounds
            val screenRelPoint = Vector2f(screenX - shipScreenX, screenY - shipScreenY)
            val facingRad = Math.toRadians(ship.facing.toDouble()).toFloat()
            val cosFacing = kotlin.math.cos(facingRad)
            val sinFacing = kotlin.math.sin(facingRad)
            val shipRelX = screenRelPoint.x * cosFacing + screenRelPoint.y * sinFacing

            sweepMinX = minOf(sweepMinX, shipRelX)
            sweepMaxX = maxOf(sweepMaxX, shipRelX)

            screenPoints.add(screenX)
            screenPoints.add(screenY)
        }

        // Calculate sweep position using the bounds we just calculated
        val sweepWidth = sweepMaxX - sweepMinX
        sweepLayer.sweepPosition = sweepMaxX - (ship.phaseCloak?.effectLevel ?: 1f) * sweepWidth

        // Store the screen coordinates for the fragment shader
        polygonPoints = screenPoints

        // Calculate bounding box for rendering optimization
        calculateBoundingBox()

        // Update quad vertices to match bounding box
        updateQuadVertices()
    }

    fun updateColor(r: Int, g: Int, b: Int, a: Int) {
        fillLayer.setColor(r, g, b, a)
    }

    fun updateScreenResolution(width: Float, height: Float) {
        screenWidth = width
        screenHeight = height
        // Recalculate quad vertices when screen resolution changes
        if (boundingBoxWidth > 0f && boundingBoxHeight > 0f) {
            updateQuadVertices()
        }
    }

    fun updateShipPosition() {
        // Don't recalculate bounding box here - it should be calculated with viewport in updatePolygon
        // Just update rotation matrix when ship moves
        updateRotationMatrix()
    }

    private fun updateRotationMatrix() {
        // Calculate rotation matrix for ship facing
        val facingRad = Math.toRadians(ship.facing.toDouble()).toFloat()
        val cosFacing = kotlin.math.cos(facingRad)
        val sinFacing = kotlin.math.sin(facingRad)

        // 2x2 rotation matrix: [cos -sin; sin cos]
        shipRotationMatrix[0] = cosFacing
        shipRotationMatrix[1] = -sinFacing
        shipRotationMatrix[2] = sinFacing
        shipRotationMatrix[3] = cosFacing
    }

    fun setDebugMode(enabled: Boolean) {
        debugLayer.enabled = enabled
    }

    fun setBoundingBoxOptimization(enabled: Boolean) {
        enableBoundingBoxOptimization = enabled
    }

    fun setSplineSubdivisions(subdivisions: Int) {
        splineSubdivisions = subdivisions.coerceIn(1, 8) // Limit to reasonable range
    }

    fun getSplineSubdivisions(): Int {
        return splineSubdivisions
    }

    fun getSmoothingMethod(): SmoothingMethod {
        return smoothingMethod
    }

    fun updateBorder(r: Int, g: Int, b: Int, a: Int, width: Float, blend: Float) {
        borderLayer.setBorder(r, g, b, a, width)
    }

    fun startRipple(x: Float, y: Float, width: Float, r: Int, g: Int, b: Int, a: Int, intensity: Float = 1f) {
        rippleLayer.startRipple(x, y, width, r, g, b, a, intensity)
    }

    fun updateRipple(deltaTime: Float, speed: Float = 400f) {
        rippleLayer.update(deltaTime, speed, viewportScaleX)
    }

    fun setHoneycombEnabled(enabled: Boolean) {
        honeycombLayer.setEnabled(enabled)
    }

    fun updateTime(amount: Float) {
        this.time += amount
    }

    fun updateShieldHPPercent(percent: Float) {
        fillLayer.setShieldHPPercent(percent)
    }

    override fun render(viewport: ViewportAPI) {
        if (polygonPoints.isEmpty()) return

        // Pick up debug layer-set changes (ShaderLayers console command) and
        // rebuild the program lazily — render() is on the GL thread
        program.ensureUpToDate()

        // Update viewport scale for ripple calculations
        viewportScaleX = viewport.viewMult
        viewportScaleY = viewport.viewMult

        // Validate polygon before rendering
        if (polygonPoints.size < 6) {
            Global.getLogger(this.javaClass)
                .warn("Cannot render polygon with ${polygonPoints.size / 2} points (minimum 3 required)")
            return
        }

        // Ensure bounding box is up-to-date before rendering (if optimization is enabled)
        if (enableBoundingBoxOptimization) {
            // Don't recalculate bounding box here - it should already be calculated with viewport in updatePolygon
            // Just update quad vertices if needed
            if (boundingBoxWidth > 0f && boundingBoxHeight > 0f) {
                updateQuadVertices()
            }
        }

        // Ensure rotation matrix is up-to-date
        updateRotationMatrix()

        // Save current OpenGL state
        glPushAttrib(GL_ALL_ATTRIB_BITS)
        glPushMatrix()

        // Save current OpenGL state variables
        val currentProgram = glGetInteger(GL_CURRENT_PROGRAM)
        val currentVAO = glGetInteger(GL_VERTEX_ARRAY_BINDING)
        val currentVBO = glGetInteger(GL_ARRAY_BUFFER_BINDING)
        val currentTexture = glGetInteger(GL_TEXTURE_BINDING_2D)
        val blendEnabled = glIsEnabled(GL_BLEND)
        val depthTestEnabled = glIsEnabled(GL_DEPTH_TEST)
        val stencilTestEnabled = glIsEnabled(GL_STENCIL_TEST)
        val texture2DEnabled = glIsEnabled(GL_TEXTURE_2D)
        val scissorTestEnabled = glIsEnabled(GL_SCISSOR_TEST)

        try {
            // Use our shader program
            glUseProgram(program.programId)

            // Bind VAO
            glBindVertexArray(vao)

            // Shared uniforms (used by multiple layer modules)
            glUniform2f(glGetUniformLocation(program.programId, "resolution"), screenWidth, screenHeight)
            val shipScreenX = viewport.convertWorldXtoScreenX(ship.location.x)
            val shipScreenY = viewport.convertWorldYtoScreenY(ship.location.y)
            glUniform2f(glGetUniformLocation(program.programId, "center"), shipScreenX, shipScreenY)
            glUniform4f(
                glGetUniformLocation(program.programId, "shipRotationMatrix"),
                shipRotationMatrix[0], shipRotationMatrix[1], shipRotationMatrix[2], shipRotationMatrix[3]
            )
            glUniform2f(glGetUniformLocation(program.programId, "viewportScale"), viewportScaleX, viewportScaleY)
            glUniform1f(glGetUniformLocation(program.programId, "shipFacing"), ship.facing)
            glUniform1f(glGetUniformLocation(program.programId, "time"), time)

            // Polygon points as uniforms (zero-fill unused slots — the polygon
            // test iterates the full array)
            setPolygonPointUniforms(calculateMaxPointsAfterSmoothing(boundsCount))

            // Layer uniforms, in layer order (disabled layers skipped)
            program.uploadLayerUniforms(viewport)

            // Enable blending for transparency
            glEnable(GL_BLEND)
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)

            // Draw bounding-box quad to render the shield
            glDrawArrays(GL_TRIANGLE_FAN, 0, 4)

        } finally {
            // Restore OpenGL state variables
            glUseProgram(currentProgram)
            glBindVertexArray(currentVAO)
            glBindBuffer(GL_ARRAY_BUFFER, currentVBO)
            glBindTexture(GL_TEXTURE_2D, currentTexture)

            // Restore scissor test state
            if (scissorTestEnabled) {
                glEnable(GL_SCISSOR_TEST)
            } else {
                glDisable(GL_SCISSOR_TEST)
            }

            // Restore enabled states
            if (blendEnabled) glEnable(GL_BLEND) else glDisable(GL_BLEND)
            if (depthTestEnabled) glEnable(GL_DEPTH_TEST) else glDisable(GL_DEPTH_TEST)
            if (stencilTestEnabled) glEnable(GL_STENCIL_TEST) else glDisable(GL_STENCIL_TEST)
            if (texture2DEnabled) glEnable(GL_TEXTURE_2D) else glDisable(GL_TEXTURE_2D)

            // Restore OpenGL state
            glPopMatrix()
            glPopAttrib()
        }
    }

    override fun cleanup() {
        try {
            // Clean up OpenGL resources
            if (vbo != 0) {
                glDeleteBuffers(vbo)
                vbo = 0
            }
            if (vao != 0) {
                glDeleteVertexArrays(vao)
                vao = 0
            }
            program.dispose()
        } catch (e: Exception) {
            Global.getLogger(this.javaClass).error("Error during cleanup: ${e.message}")
        }
    }
}

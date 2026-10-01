package org.combustiblelemon.covenantremnants.utils.render

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ViewportAPI
import org.combustiblelemon.lemonlib.render.Shader
import org.combustiblelemon.lemonlib.render.composition.ComposedShaderProgram
import org.combustiblelemon.lemonlib.render.composition.CompositionDebug
import org.combustiblelemon.lemonlib.render.composition.ShaderLayer
import org.combustiblelemon.covenantremnants.utils.render.shield.BorderLayer
import org.combustiblelemon.covenantremnants.utils.render.shield.DebugLayer
import org.combustiblelemon.covenantremnants.utils.render.shield.DistortionLayer
import org.combustiblelemon.covenantremnants.utils.render.shield.FillLayer
import org.combustiblelemon.covenantremnants.utils.render.shield.FortressLayer
import org.combustiblelemon.covenantremnants.utils.render.shield.HoneycombLayer
import org.combustiblelemon.lemonlib.geometry.PolygonSmoothing
import org.combustiblelemon.covenantremnants.utils.render.shield.PlasmaLayer
import org.combustiblelemon.covenantremnants.utils.render.shield.ShieldProgram
import org.combustiblelemon.lemonlib.geometry.SmoothingMethod
import org.combustiblelemon.covenantremnants.utils.render.shield.SweepLayer
import org.combustiblelemon.lemonlib.geometry.rotatePointByDegrees
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL13.GL_ACTIVE_TEXTURE
import org.lwjgl.opengl.GL13.GL_TEXTURE0
import org.lwjgl.opengl.GL13.GL_TEXTURE1
import org.lwjgl.opengl.GL13.GL_TEXTURE2
import org.lwjgl.opengl.GL13.glActiveTexture
import org.lwjgl.opengl.GL15.*
import org.lwjgl.opengl.GL20.*
import org.lwjgl.opengl.GL30.*
import org.lwjgl.util.vector.Vector2f

/**
 * Covenant shield renderer, built on LemonLib's shader composition system
 * (see LemonLib/docs/shader-composition/). Each visual layer (fill, border, sweep,
 * honeycomb, fortress, plasma, distortion, debug) is a [ShaderLayer] owning its GLSL module +
 * uniforms. The GL program is the one shared [ShieldProgram] (compiled once at game start): this
 * class keeps its own layer instances for per-ship state and uploads their uniforms itself.
 *
 * Layer order: fill, border, sweep, honeycomb, fortress (Fortress Shield overlay), plasma (the
 * Halo: Reach hit / low-HP / collapse / recharge light), distortion (hit refraction rings, composes
 * the final colour), debug.
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
    private val honeycombLayer = HoneycombLayer()
    private val fortressLayer = FortressLayer()
    private val plasmaLayer = PlasmaLayer(ship)
    private val distortionLayer = DistortionLayer(ship)
    private val debugLayer = DebugLayer()

    private val layers: List<ShaderLayer> =
        listOf(fillLayer, borderLayer, sweepLayer, honeycombLayer, fortressLayer, plasmaLayer, distortionLayer, debugLayer)

    // Shared across all ships; never disposed here. Its own (template) layers are only used for
    // the GLSL source, so uploads go through this instance's layers below.
    private val program: ComposedShaderProgram = ShieldProgram.get()

    init {
        if (boundsCount > ShieldProgram.POINT_CAPACITY) {
            Global.getLogger(this.javaClass).warn(
                "${ship.hullSpec.hullId}: ${boundsCount} bound points exceed the shield shader's capacity " +
                        "${ShieldProgram.POINT_CAPACITY}; outline truncated"
            )
        }
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
     * Points the shader receives: all smoothing methods only reposition points, so this is the
     * original count capped at the shared program's array capacity.
     */
    private fun calculateMaxPointsAfterSmoothing(originalPointCount: Int): Int {
        return minOf(originalPointCount, ShieldProgram.POINT_CAPACITY)
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

        // Polygon points as uniforms (screen coordinates); the shader loops over pointCount, so
        // the unused capacity needs no zero-fill
        for (i in 0 until safePointCount) {
            val x = polygonPoints[i * 2]
            val y = polygonPoints[i * 2 + 1]
            val uniformLocation = glGetUniformLocation(program.programId, "points[$i]")
            glUniform2f(uniformLocation, x, y)
        }
        glUniform1i(glGetUniformLocation(program.programId, "pointCount"), safePointCount)

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
        distortionLayer.setCopyRegion(boundingBoxMinX, boundingBoxMinY, boundingBoxWidth, boundingBoxHeight)
    }

    fun updatePolygon(newPoints: List<Vector2f>, viewport: ViewportAPI, scale: Vector2f = Vector2f(1.0f, 1.0f)) {
        // Collapse pushes the shell outward (Reach extrusion); the bounding box follows the scaled points.
        val shellScale = plasmaLayer.effect.shellScale
        scaleX = scale.x * shellScale
        scaleY = scale.y * shellScale

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
        var relMinY = Float.MAX_VALUE
        var relMaxY = -Float.MAX_VALUE
        var maxRadius = 0f

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
            val shipRelY = -screenRelPoint.x * sinFacing + screenRelPoint.y * cosFacing

            sweepMinX = minOf(sweepMinX, shipRelX)
            sweepMaxX = maxOf(sweepMaxX, shipRelX)
            relMinY = minOf(relMinY, shipRelY)
            relMaxY = maxOf(relMaxY, shipRelY)
            maxRadius = maxOf(maxRadius, screenRelPoint.length())

            screenPoints.add(screenX)
            screenPoints.add(screenY)
        }

        // Calculate sweep position using the bounds we just calculated. A collapsing shield shows
        // its whole polygon (the cloak is already winding down).
        val sweepWidth = sweepMaxX - sweepMinX
        val sweepLevel = if (plasmaLayer.effect.collapseActive) 1f else (ship.phaseCloak?.effectLevel ?: 1f)
        sweepLayer.sweepPosition = sweepMaxX - sweepLevel * sweepWidth

        // Body metrics for the plasma layer (screen px): depth = half the shorter extent, radius = farthest point
        plasmaLayer.setBodyMetrics(0.5f * minOf(sweepWidth, relMaxY - relMinY), maxRadius)
        distortionLayer.setBodyRadius(maxRadius)

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

    /** A shield hit at a world position; [strength] is the damage level 0..1 (floored by the effect). */
    fun triggerHit(x: Float, y: Float, strength: Float) {
        plasmaLayer.effect.addHit(x, y, strength, ship.location.x, ship.location.y, ship.facing)
        distortionLayer.addWave(x, y, strength)
    }

    /** Starts the Reach collapse (flash + flare + expansion); the owner keeps rendering until [isCollapseFinished]. */
    fun startCollapse() {
        plasmaLayer.effect.startCollapse()
        distortionLayer.clear()
    }

    /** Starts the recharge wave (plasma sweeping out from the core). */
    fun triggerRecharge() {
        plasmaLayer.effect.startRecharge()
    }

    fun isCollapseActive(): Boolean = plasmaLayer.effect.collapseActive

    fun isCollapseFinished(): Boolean = plasmaLayer.effect.collapseFinished

    /** Clears a finished collapse so the shield can be drawn again when re-raised. */
    fun resetImpact() {
        plasmaLayer.effect.reset()
    }

    fun setHoneycombEnabled(enabled: Boolean) {
        honeycombLayer.setEnabled(enabled)
    }

    /**
     * Fortress Shield effect level, 0 (off) to 1: drives the fortress overlay and fades out the
     * honeycomb's breathing animation.
     */
    fun setFortressLevel(level: Float) {
        fortressLayer.level = level
        honeycombLayer.breathing = 1f - level
    }

    fun updateTime(amount: Float) {
        this.time += amount
        for (layer in layers) layer.advance(amount)
    }

    /** Shield HP fraction 0..1: drives the plasma layer's vitality (flare size/brightness, low-HP baseline). */
    fun updateShieldHPPercent(percent: Float) {
        plasmaLayer.effect.vitality = percent.coerceIn(0f, 1f)
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
        // The plasma layer binds its noise maps on units 0 and 1, the distortion layer the screen copy on 2
        val currentActiveTexture = glGetInteger(GL_ACTIVE_TEXTURE)
        glActiveTexture(GL_TEXTURE0)
        val currentTexture0 = glGetInteger(GL_TEXTURE_BINDING_2D)
        glActiveTexture(GL_TEXTURE1)
        val currentTexture1 = glGetInteger(GL_TEXTURE_BINDING_2D)
        glActiveTexture(GL_TEXTURE2)
        val currentTexture2 = glGetInteger(GL_TEXTURE_BINDING_2D)
        glActiveTexture(currentActiveTexture)
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

            // Polygon points + pointCount
            setPolygonPointUniforms(calculateMaxPointsAfterSmoothing(boundsCount))

            // Layer uniforms, in layer order; layers disabled by the ShaderLayers command are
            // stubbed in the shared program, so their uploads are skipped
            val enabled = CompositionDebug.enabledLayersFor(ShieldProgram.SYSTEM_NAME)
            for (layer in layers) {
                if (enabled == null || enabled.isEmpty() || layer.layerName in enabled) {
                    layer.uploadUniforms(program.programId, viewport)
                }
            }

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
            glActiveTexture(GL_TEXTURE2)
            glBindTexture(GL_TEXTURE_2D, currentTexture2)
            glActiveTexture(GL_TEXTURE1)
            glBindTexture(GL_TEXTURE_2D, currentTexture1)
            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GL_TEXTURE_2D, currentTexture0)
            glActiveTexture(currentActiveTexture)

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
            // The program is shared (ShieldProgram); not disposed per ship
        } catch (e: Exception) {
            Global.getLogger(this.javaClass).error("Error during cleanup: ${e.message}")
        }
    }
}

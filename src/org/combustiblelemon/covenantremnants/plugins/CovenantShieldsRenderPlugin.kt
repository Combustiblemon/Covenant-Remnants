package org.combustiblelemon.covenantremnants.plugins

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import org.combustiblelemon.covenantremnants.utils.PolygonShader
import org.lwjgl.input.Keyboard
import org.lwjgl.util.vector.Vector2f
import java.util.*
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class CovenantShieldsRenderPlugin(private val attachedTo: ShipAPI) : CombatLayeredRenderingPlugin {
    private var minX: Float = attachedTo.shieldCenterEvenIfNoShield.x - attachedTo.shieldRadiusEvenIfNoShield
    private var maxX: Float = attachedTo.shieldCenterEvenIfNoShield.x + attachedTo.shieldRadiusEvenIfNoShield
    private var minY: Float = 0F
    private var maxY: Float = 0F

    private val numPoints = 60
    private val points: Array<Vector2f> = Array(numPoints) { Vector2f(0F, 0F) }
    private val shader = PolygonShader()

    companion object {
        private val noiseSprite = Global.getSettings().getSprite("covenant_remnants", "shield_noise")
    }

    private var layer = CombatEngineLayers.ABOVE_SHIPS_LAYER

    private lateinit var entity: CombatEntityAPI

    private var rippleStarted = false
    private var rippleActive = false
    private var wasKeyPressed = false
    private var rippleStartTime = 0f
    private var currentTime = 0f

    private data class CircleData(
        val angle: Float,
        val radius: Float,
        val alpha: Int,
        val intensity: Float
    )

    private val staticCircles = mutableListOf<CircleData>()

    private data class RippleData(
        val angle: Float,
        val alpha: Int,
        val intensity: Float,
        var active: Boolean = true  // Track if ripple is still active
    )

    private val activeRipples = mutableListOf<RippleData>()

    override fun render(layer: CombatEngineLayers, viewport: ViewportAPI) {
        // Debug: Check for R key press
        if (Keyboard.isKeyDown(Keyboard.KEY_R) && !wasKeyPressed) {
            val r = Random()
            val random: Float = 0 + r.nextFloat() * (360 - 0)
            this.triggerRippleAtAngle(random, 80, 0.6f)

            addStaticCircleOnEdge(86f, 40f, 80, 1.0f)     // Full intensity circle at 86 degrees
            addStaticCircleOnEdge(180f, 40f, 80, 0.5f)    // Half intensity circle at 180 degrees
            addStaticCircleOnEdge(270f, 40f, 80, 0.25f)   // Quarter intensity circle at 270 degrees
        }
        wasKeyPressed = Keyboard.isKeyDown(Keyboard.KEY_R)

        val isPhaseActive = attachedTo.phaseCloak.isActive

        if (isPhaseActive) {
            renderShield()
        }
    }

    private fun renderShield() {
        val shipX = attachedTo.location!!.x
        val shipY = attachedTo.location!!.y

        // Create vertex data for the shield shape
        val vertexData = mutableListOf<Float>()

        // Add ship boundary points without rotation
        attachedTo.exactBounds!!.origSegments.forEach { segment ->
            vertexData.add(segment!!.p1.x + shipX)
            vertexData.add(segment.p1.y + shipY)
        }

        // Close the shape
        val firstSeg = attachedTo.exactBounds!!.origSegments[0]
        vertexData.add(firstSeg!!.p1.x + shipX)
        vertexData.add(firstSeg.p1.y + shipY)

        // Update shader with center position and other parameters
        shader.updateCenter(shipX, shipY)  // Set ship's center as the transform center
        shader.updatePolygon(vertexData)
        shader.updateScale(1.05f, 1.02f)
        shader.updateRotation(Math.toRadians(attachedTo.facing.toDouble()).toFloat())
        shader.updateColor(133, 237, 255, 30)
        shader.updateBorder(255, 255, 255, 60, 20f, 5f)  // White border at 50% opacity
        shader.updateGlow(1f, 40f)

        shader.drawPolygon()
    }

    private var statuskey = Any()
    private var statuskey2 = Any()

    private fun calcMaxY() {
        var _minY = 100000F
        var _maxY = -100000F

        attachedTo.exactBounds!!.segments.forEach { segment ->
            if (segment.p1.y < _minY) {
                _minY = segment.p1.y
            }

            if (segment.p1.y > _maxY) {
                _maxY = segment.p1.y
            }

            if (segment.p2.y < _minY) {
                _minY = segment.p2.y
            }

            if (segment.p2.y > _maxY) {
                _maxY = segment.p2.y
            }
        }

        maxY = _minY - 30
        minY = _maxY + 30

        val a = abs(maxX - minX) / 2
        val b = abs(maxY - minY) / 2

        for (i in 0 until numPoints) {
            val theta = Math.toRadians(i * (360.0 / numPoints))
            val x = (attachedTo.shieldCenterEvenIfNoShield.x + a * cos(theta)).toFloat()
            val y = (attachedTo.shieldCenterEvenIfNoShield.y + b * sin(theta)).toFloat()
            points[i] = Vector2f(x, y)
        }
    }

    override fun advance(amount: Float) {
        currentTime += amount

        if (rippleActive) {
            shader.updateRipple(amount, 1000f)
            rippleActive = shader.hasActiveRipples()
        }

        // Update circle positions every frame
        updateCirclePositions()
    }

    override fun cleanup() {
        shader.cleanup()
        rippleStarted = false
        activeRipples.clear() // Clear ripple data
    }

    override fun getActiveLayers(): EnumSet<CombatEngineLayers> {
        return EnumSet.of(layer)
    }

    override fun getRenderRadius(): Float {
        return Float.MAX_VALUE
    }

    override fun init(entity: CombatEntityAPI) {
        this.entity = entity
    }

    override fun isExpired(): Boolean {
        return false
    }

    /**
     * Triggers a ripple effect at the shield's edge based on an angle
     * @param angleInDegrees The angle in degrees (0-360) where 0 is right, 90 is up
     * @param alpha The opacity of the ripple (0-100)
     */
    fun triggerRippleAtAngle(angleInDegrees: Float, alpha: Int = 80, intensity: Float = 1f) {
        val radians = Math.toRadians(angleInDegrees.toDouble())

        val radiusX = attachedTo.shieldRadiusEvenIfNoShield
        val radiusY = radiusX
        val centerX = attachedTo.shieldCenterEvenIfNoShield.x
        val centerY = attachedTo.shieldCenterEvenIfNoShield.y

        val x = centerX + radiusX * cos(radians).toFloat()
        val y = centerY + radiusY * sin(radians).toFloat()

        shader.startRipple(x, y, 40f, 255, 255, 255, alpha, intensity)
        rippleActive = true
    }

    /**
     * Alternative version that directly accepts x,y coordinates
     */
    fun triggerRippleAtPoint(x: Float, y: Float, alpha: Int = 80, intensity: Float = 1f) {
        rippleStartTime = currentTime
        shader.startRipple(x, y, 40f, 255, 255, 255, alpha, intensity)
        rippleStarted = true
        rippleActive = true
    }

    fun addStaticCircle(angleInDegrees: Float, radius: Float, alpha: Int = 80, intensity: Float = 1f) {
        val radians = Math.toRadians(angleInDegrees.toDouble())

        val shieldRadius = attachedTo.shieldRadiusEvenIfNoShield
        val centerX = attachedTo.shieldCenterEvenIfNoShield.x
        val centerY = attachedTo.shieldCenterEvenIfNoShield.y

        val x = centerX + shieldRadius * cos(radians).toFloat()
        val y = centerY + shieldRadius * sin(radians).toFloat()

        shader.addStaticCircle(x, y, radius, 255, 255, 255, alpha, intensity)
    }

    fun addStaticCircleAtPoint(x: Float, y: Float, radius: Float, alpha: Int = 80, intensity: Float = 1f) {
        shader.addStaticCircle(x, y, radius, 255, 255, 255, alpha, intensity)
    }

    fun clearStaticCircles() {
        staticCircles.clear()
        shader.clearStaticCircles()
    }

    /**
     * Creates a static circle on the edge of the shield at the specified angle
     * @param angleInDegrees The angle in degrees (0-360) where 0 is right, 90 is up
     * @param radius The base radius of the circle
     * @param alpha The base opacity (0-100)
     * @param intensity The intensity of the circle (0.0-1.0) affecting size and opacity
     */
    fun addStaticCircleOnEdge(angleInDegrees: Float, radius: Float, alpha: Int = 80, intensity: Float = 1f) {
        // Store the circle data instead of creating it immediately
        staticCircles.add(CircleData(angleInDegrees, radius, alpha, intensity))
        updateCirclePositions() // Initial position update
    }

    private fun updateCirclePositions() {
        shader.clearStaticCircles()

        for (circle in staticCircles) {
            val radians = Math.toRadians(circle.angle.toDouble())

            // Get the shield's current dimensions and position
            val radiusX = attachedTo.shieldRadiusEvenIfNoShield
            val radiusY = radiusX
            val centerX = attachedTo.shieldCenterEvenIfNoShield.x
            val centerY = attachedTo.shieldCenterEvenIfNoShield.y

            // Calculate point on edge of shield
            val x = centerX + radiusX * cos(radians).toFloat()
            val y = centerY + radiusY * sin(radians).toFloat()

            // Add the static circle at the updated position
            shader.addStaticCircle(x, y, circle.radius, 255, 255, 255, circle.alpha, circle.intensity)
        }
    }

    private fun updateRipplePositions() {
        // Clear existing ripples in shader
        shader.clearRipples()

        // Update positions for all active ripples
        for (ripple in activeRipples) {
            if (!ripple.active) continue

            val radians = Math.toRadians(ripple.angle.toDouble())

            val radiusX = attachedTo.shieldRadiusEvenIfNoShield
            val radiusY = radiusX
            val centerX = attachedTo.shieldCenterEvenIfNoShield.x
            val centerY = attachedTo.shieldCenterEvenIfNoShield.y

            val x = centerX + radiusX * cos(radians).toFloat()
            val y = centerY + radiusY * sin(radians).toFloat()

            shader.startRipple(x, y, 40f, 255, 255, 255, ripple.alpha, ripple.intensity)
        }
    }
}

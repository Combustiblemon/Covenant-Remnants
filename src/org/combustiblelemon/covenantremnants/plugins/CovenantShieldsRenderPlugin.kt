package org.combustiblelemon.covenantremnants.plugins

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import org.combustiblelemon.covenantremnants.utils.PolygonShader
import org.lwjgl.input.Keyboard
import org.lwjgl.util.vector.Vector2f
import java.util.*
import kotlin.math.cos
import kotlin.math.sin

class CovenantShieldsRenderPlugin(private val attachedTo: ShipAPI, private val shipDimensions: Array<Vector2f>) :
    CombatLayeredRenderingPlugin {

    private val shader = PolygonShader(shipDimensions, 200f)

    companion object {
        private val noiseSprite = Global.getSettings().getSprite("covenant_remnants", "shield_noise")
    }

    private var layer = CombatEngineLayers.ABOVE_SHIPS_LAYER

    private lateinit var entity: CombatEntityAPI

    private var rippleStarted = false
    private var rippleActive = false
    private var rippleStartTime = 0f
    private var currentTime = 0f

    // Add new properties for hex animation
    private var hexAnimationTime = 0f
    private val hexAnimationSpeed = 1.5f
    private val hexSize = 40f
    private val hexSpacing = 10f

    private data class RippleData(
        val angle: Float,
        val alpha: Int,
        val intensity: Float,
        var active: Boolean = true  // Track if ripple is still active
    )

    private val activeRipples = mutableListOf<RippleData>()
    private var popActive = false

    private var wasPressed: Boolean = false

    override fun render(layer: CombatEngineLayers, viewport: ViewportAPI) {
        // Debug: Check for R key press
        if (Keyboard.isKeyDown(Keyboard.KEY_R)) {
            addStaticCircleOnEdge(86f, 40f, 80, 1.0f)
        }

        if (Keyboard.isKeyDown(Keyboard.KEY_B) && !wasPressed) {
            // addStaticCircleOnEdge(86f, 40f, 80, 1.0f)
            // triggerRippleAtAngle(90f, 80, 1f, 10f)

            wasPressed = true
        }

        if (Keyboard.isKeyDown(Keyboard.KEY_N)) {
            wasPressed = false
        }

        if (popActive) {
            popActive = shader.drawShieldPop()
        }

        // Use phaseCloak.effectLevel to check for activity
        if (attachedTo.phaseCloak.effectLevel > 0f) {
            renderShield()
            shader.updateHexAnimation(hexAnimationTime, hexSize, hexSpacing)
        }
    }

    fun triggerShieldPop() {
        shader.triggerShieldPop(1f)
        attachedTo.phaseCloak.deactivate()
        popActive = true
    }

    private fun renderShield() {
        val shipX = attachedTo.location!!.x
        val shipY = attachedTo.location!!.y

        // Create vertex data for the shield shape
        val vertexData = mutableListOf<Float>()

        attachedTo.exactBounds!!.origSegments.forEach { segment ->
            vertexData.add(segment!!.p1.x + shipX)
            vertexData.add(segment.p1.y + shipY)
        }

        val firstSeg = attachedTo.exactBounds!!.origSegments[0]
        vertexData.add(firstSeg!!.p1.x + shipX)
        vertexData.add(firstSeg.p1.y + shipY)

        shader.updateCenter(shipX, shipY)
        shader.updatePolygon(vertexData)
        shader.updateScale(1.05f, 1.02f)
        shader.updateRotation(Math.toRadians(attachedTo.facing.toDouble()).toFloat())
        shader.updateColor(133, 237, 255, 30)
        shader.updateBorder(255, 255, 255, 80, 5f, 5f)
        shader.updateGlow(10f, 10f)
        shader.updateHexParameters(20f, 90f, 2f, 1.2f)
        shader.updatePopEffectRadius(shipDimensions[0].y / 2)

        shader.drawPolygon()
    }

    override fun advance(amount: Float) {
        currentTime += amount
        hexAnimationTime += amount * hexAnimationSpeed

        if (rippleActive) {
            shader.updateRipple(amount, 1000f)
            rippleActive = shader.hasActiveRipples()
        }

        if (attachedTo.phaseCloak.effectLevel > 0f) {
            // Pass charge progress to shader
            shader.updateChargeProgress(attachedTo.phaseCloak.effectLevel, attachedTo.facing)
        }

        if (popActive) {
            shader.updateShieldPop(amount)
        }

        // Clear circles from previous frame
        shader.clearStaticCircles()
    }

    override fun cleanup() {
        shader.cleanup()
        rippleStarted = false
        wasPressed = false
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
    fun triggerRippleAtAngle(
        angleInDegrees: Float,
        alpha: Int = 80,
        intensity: Float = 1f,
        size: Float = 20f,

        ) {
        val radians = Math.toRadians(angleInDegrees.toDouble())

        val radius = attachedTo.shieldRadiusEvenIfNoShield
        val centerX = attachedTo.shieldCenterEvenIfNoShield.x
        val centerY = attachedTo.shieldCenterEvenIfNoShield.y

        val x = centerX + radius * cos(radians).toFloat()
        val y = centerY + radius * sin(radians).toFloat()

        shader.startRipple(x, y, size, 255, 255, 255, alpha, intensity)
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

    /**
     * Creates a static circle on the edge of the shield at the specified angle
     * @param angleInDegrees The angle in degrees (0-360) where 0 is right, 90 is up
     * @param radius The base radius of the circle
     * @param alpha The base opacity (0-100)
     * @param intensity The intensity of the circle (0.0-1.0) affecting size and opacity
     */
    fun addStaticCircleOnEdge(angleInDegrees: Float, radius: Float, alpha: Int = 80, intensity: Float = 1f) {
        // Add ship's facing angle to the input angle to account for rotation
        val totalAngle = angleInDegrees + attachedTo.facing - 90f  // Subtract 90 to align with ship's forward direction
        val radians = Math.toRadians(totalAngle.toDouble())

        // Get the shield's dimensions and center
        val shieldRadius = attachedTo.shieldRadiusEvenIfNoShield
        val centerX = attachedTo.location.x
        val centerY = attachedTo.location.y

        // Calculate point on edge of shield
        val x = centerX + shieldRadius * cos(radians).toFloat()
        val y = centerY + shieldRadius * sin(radians).toFloat()

        shader.addStaticCircle(x, y, radius, 255, 255, 255, alpha, intensity)
    }
}

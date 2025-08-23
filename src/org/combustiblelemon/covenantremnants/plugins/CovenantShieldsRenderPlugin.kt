package org.combustiblelemon.covenantremnants.plugins

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import org.combustiblelemon.covenantremnants.utils.Utils
import org.combustiblelemon.covenantremnants.utils.render.PolygonShader
import org.lwjgl.util.vector.Vector2f
import java.awt.Color
import java.util.*

class CovenantShieldsRenderPlugin(
    private val attachedTo: ShipAPI,
    shipDimensions: Array<Vector2f>,
) :
    CombatLayeredRenderingPlugin {

    private val shader = PolygonShader(shipDimensions, 200f)

    private var layer = CombatEngineLayers.ABOVE_SHIPS_LAYER

    private lateinit var entity: CombatEntityAPI

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
    var breakCooldownActive = false

    private var wasPressed: Boolean = false

    private var lastEMPTriggerTime: Float = 0f
    private val EMPCooldown: Float = 0.2f

    override fun render(layer: CombatEngineLayers, viewport: ViewportAPI) {
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

        spawnLightning()
    }

    private fun spawnLightning() {
        val polygonData = Utils.getShipPolygonInWorld(attachedTo)
        val lightningArcNumbers = 3
        val arcPoints = Utils.getRandomPointsInPolygon(polygonData, lightningArcNumbers * 2)

        val engine = Global.getCombatEngine()

        val mult = 1f
        val arcWidth = 40f + mult * 40f

        val params = EmpArcEntityAPI.EmpArcParams()
        params.segmentLengthMult = 8f
        params.zigZagReductionFactor = 0.15f
        params.fadeOutDist = 50f
        params.minFadeOutMult = 60f
        params.flickerRateMult = 0.8f

        for (i in 0..lightningArcNumbers) {

            val arc = engine.spawnEmpArcVisual(
                arcPoints[i], attachedTo, arcPoints[i + 1], attachedTo,
                arcWidth,  // thickness
                Color.CYAN,
                Color(255, 255, 255, 255),
                params
            )

            arc.coreWidthOverride = arcWidth / 2f

            arc.setRenderGlowAtStart(false)
            arc.setFadedOutAtStart(true)
            arc.setSingleFlickerMode(false)
        }

        lastEMPTriggerTime = engine.getTotalElapsedTime(false)
    }

    private fun getShipVertexData(): List<Float> {
        val shipX = attachedTo.location!!.x
        val shipY = attachedTo.location!!.y

        // Create vertex data for the shield shape
        val vertexData = mutableListOf<Float>()

        attachedTo.exactBounds!!.origSegments.forEach { segment ->
            vertexData.add(segment!!.p1.x + shipX)
            vertexData.add(segment.p1.y + shipY)
        }

        return vertexData
    }

    private fun renderShield() {
        val shipX = attachedTo.location!!.x
        val shipY = attachedTo.location!!.y
        val vertexData = getShipVertexData()

        shader.updateCenter(shipX, shipY)
        shader.updatePolygon(vertexData)
        shader.updateScale(1.05f, 1.02f)
        shader.updateRotation(Math.toRadians(attachedTo.facing.toDouble()).toFloat())
        shader.updateColor(133, 237, 255, 30)
        shader.updateBorder(255, 255, 255, 80, 5f, 5f)
        shader.updateGlow(10f, 10f)
        shader.updateHexParameters(20f, 90f, 2f, 1.2f)

        shader.drawPolygon()
    }

    override fun advance(amount: Float) {
        hexAnimationTime += amount * hexAnimationSpeed

        shader.updateRipple(amount, 1000f)

        if (attachedTo.phaseCloak.effectLevel > 0f) {
            // Pass charge progress to shader
            shader.updateChargeProgress(attachedTo.phaseCloak.effectLevel, attachedTo.facing)
        }

        if (popActive) {
            shader.updateShieldPop(amount)
            popActive = shader.isPopWaveActive()
        }

        if (breakCooldownActive) {
            if (Global.getCombatEngine().getTotalElapsedTime(false) > lastEMPTriggerTime + EMPCooldown) {
                spawnLightning()
            }
        }
    }

    override fun cleanup() {
        shader.cleanup()

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

    private var lastRippleTriggeredTime: Float = 0f
    private val rippleCooldown: Float = 0.3f

    /**
     * Alternative version that directly accepts x,y coordinates
     */
    fun triggerRippleAtPoint(x: Float, y: Float, alpha: Int = 80, intensity: Float = 1f) {
        val currentTime = Global.getCombatEngine().getTotalElapsedTime(false)

        if (lastRippleTriggeredTime + rippleCooldown > currentTime) {
            return
        }

        shader.startRipple(x, y, 40f, 255, 255, 255, alpha, intensity)
    }
}

package org.combustiblelemon.covenantremnants.plugins

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import org.combustiblelemon.covenantremnants.systems.CovenantShieldsStats
import org.combustiblelemon.covenantremnants.systems.FortressShield
import org.combustiblelemon.lemonlib.geometry.getRandomPointsInPolygon
import org.combustiblelemon.lemonlib.ship.getShipPolygonInWorld
import org.combustiblelemon.covenantremnants.utils.render.ShieldShader
import org.combustiblelemon.lemonlib.geometry.SmoothingMethod
import org.lwjgl.util.vector.Vector2f
import java.awt.Color
import java.util.*
import kotlin.math.roundToInt

class CovenantShieldsRenderPlugin(
    private val attachedTo: ShipAPI,
    private val stats: CovenantShieldsStats
) :
    CombatLayeredRenderingPlugin {

    private companion object {
        // r, g, b on 0-255; a on 0-100 (ShieldShader.updateColor/updateBorder scale).
        val BASE_FILL = intArrayOf(133, 237, 255, 30)
        val BASE_BORDER = intArrayOf(255, 255, 255, 80)
    }

    private val shader =
        ShieldShader(attachedTo, attachedTo.exactBounds.segments.size, SmoothingMethod.SPLINE)
//    private val testShader = MVPShader()

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
    var breakCooldownActive = false

    private var wasPressed: Boolean = false

    private var lastEMPTriggerTime: Float = 0f
    private val EMPCooldown: Float = 0.2f

    override fun render(layer: CombatEngineLayers, viewport: ViewportAPI) {

        // Use phaseCloak.effectLevel to check for activity
        if (attachedTo.phaseCloak.effectLevel > 0f) {
            renderShield(viewport)
        }
    }

    fun triggerShieldPop() {
        attachedTo.phaseCloak.deactivate()

        spawnLightning()
    }

    private fun spawnLightning() {
        val polygonData = getShipPolygonInWorld(attachedTo)
        val lightningArcNumbers = 3
        val arcPoints = getRandomPointsInPolygon(polygonData, lightningArcNumbers * 2)

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

    private fun lerpColor(from: IntArray, to: IntArray, t: Float): IntArray =
        IntArray(4) { i -> (from[i] + (to[i] - from[i]) * t).roundToInt() }

    private fun getShipVertexData(): List<Vector2f> {
        // Create vertex data for the shield shape
        val vertexData = mutableListOf<Vector2f>()

        attachedTo.exactBounds!!.origSegments.forEach { segment ->
            vertexData.add(segment.p1)
        }

        return vertexData
    }

    private fun renderShield(viewport: ViewportAPI) {
        val vertexData = getShipVertexData()

        shader.setDebugMode(false)
        shader.setHoneycombEnabled(true)
        shader.updatePolygon(vertexData, viewport, Vector2f(1.05f, 1.1f))
        // Fortress Shield: tint toward the vanilla Fortress look and drive the fortress overlay layer.
        val fortressLevel = FortressShield.effectLevel(attachedTo)
        val fill = lerpColor(BASE_FILL, FortressShield.TINT_FILL, fortressLevel)
        val border = lerpColor(BASE_BORDER, FortressShield.TINT_BORDER, fortressLevel)

        shader.updateColor(fill[0], fill[1], fill[2], fill[3])
        shader.updateBorder(border[0], border[1], border[2], border[3], 5f, 5f)
        shader.setFortressLevel(fortressLevel)
        shader.updateShieldHPPercent(stats.shieldHealth / stats.maxShieldHealth)
        shader.render(viewport)
    }

    override fun advance(amount: Float) {
        shader.updateTime(amount)
        shader.updateRipple(amount, 1000f)

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

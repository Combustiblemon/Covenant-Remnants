package org.combustiblelemon.covenantremnants.plugins

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import org.combustiblelemon.covenantremnants.utils.PolygonShader
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

    override fun render(layer: CombatEngineLayers, viewport: ViewportAPI) {
        if (!attachedTo.phaseCloak.isActive) {
            return
        }

        renderShield()
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
        shader.updateScale(1.2f, 1.05f)
        shader.updateRotation(Math.toRadians(attachedTo.facing.toDouble()).toFloat())
        shader.updateColor(0.5f, 1f, 0.5f, 0.3f)

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

    override fun advance(amount: Float) {}
    override fun cleanup() {
        shader.cleanup()
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
}

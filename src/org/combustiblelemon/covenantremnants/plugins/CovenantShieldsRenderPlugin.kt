package org.combustiblelemon.covenantremnants.plugins

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import org.combustiblelemon.covenantremnants.settings.CovenantSettings
import org.combustiblelemon.covenantremnants.systems.CovenantShieldsStats
import org.combustiblelemon.covenantremnants.systems.FortressShield
import org.combustiblelemon.lemonlib.geometry.getRandomPointsInPolygon
import org.combustiblelemon.lemonlib.ship.getShipPolygonInWorld
import org.combustiblelemon.covenantremnants.utils.render.ShieldShader
import org.combustiblelemon.lemonlib.geometry.SmoothingMethod
import org.lwjgl.util.vector.Vector2f
import java.awt.Color
import java.util.*
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Draws a ship's Covenant shield (composed shader, see [ShieldShader]) and plays the break effects.
 *
 * Damage feedback and the break follow the Halo: Reach Elite shield (plasma layer): every hit
 * flares the shield at the impact point, low HP keeps a faint glow on, a break plays the collapse
 * (flash, flare, expansion, sparks + ring, pop sound, EMP arcs) and the recharge wave plays when
 * regen resumes or the shield is re-raised. See `planning/implementations/shield-reach-impact.md`.
 */
class CovenantShieldsRenderPlugin(
    private val attachedTo: ShipAPI,
    private val stats: CovenantShieldsStats
) :
    CombatLayeredRenderingPlugin {

    private companion object {
        // r, g, b on 0-255; a on 0-100 (ShieldShader.updateColor/updateBorder scale).
        val BASE_FILL = intArrayOf(133, 237, 255, 30)
        val BASE_BORDER = intArrayOf(255, 255, 255, 80)

        // Break particles (Reach shield_collapse.effect: ~60 white sparks 0.15-0.35 s, ~10 growing
        // ring puffs 0.2-0.5 s). All tunables.
        const val SPARK_COUNT = 28
        const val RING_COUNT = 28
        val SPARK_COLOR = Color(200, 230, 255, 255)
        val RING_COLOR = Color(120, 170, 255, 255)
        val FLARE_COLOR = Color(110, 160, 255, 255)
        const val SOUND_ID = "covenant_remnants_shield_pop"
    }

    private val shader =
        ShieldShader(attachedTo, attachedTo.exactBounds.segments.size, SmoothingMethod.SPLINE)

    private var layer = CombatEngineLayers.ABOVE_SHIPS_LAYER

    private lateinit var entity: CombatEntityAPI

    var breakCooldownActive = false

    private var lastEMPTriggerTime: Float = 0f
    private val EMPCooldown: Float = 0.2f

    // After the collapse the shield is not drawn while the cloak winds down (no 2 s wipe).
    private var suppressUntilOff = false

    // Recharge wave queued for the re-raise after a break; fires once the activation sweep completes.
    private var pendingRaiseWave = false

    private val random = Random()

    override fun render(layer: CombatEngineLayers, viewport: ViewportAPI) {
        if (suppressUntilOff) return

        // Draw while the cloak is up, and through the collapse after a break
        if (shader.isCollapseActive() || attachedTo.phaseCloak.effectLevel > 0f) {
            renderShield(viewport)
        }
    }

    /** Shield broke: drop the cloak and play the collapse. Called from the stats script. */
    fun triggerShieldPop() {
        attachedTo.phaseCloak.deactivate()

        spawnLightning()
        shader.startCollapse()
        spawnBreakFx()
        Global.getSoundPlayer().playSound(SOUND_ID, 1f, CovenantSettings.effectVolume, attachedTo.location, attachedTo.velocity)
        pendingRaiseWave = true
    }

    /** A shield hit at [x], [y] (world); [level] is the damage level 0..1. Every hit flares. */
    fun triggerShieldHit(x: Float, y: Float, level: Float) {
        if (breakCooldownActive || attachedTo.phaseCloak.effectLevel <= 0f) return
        shader.triggerHit(x, y, level)
    }

    /** Regen resumed while the shield is up: play the recharge wave. */
    fun triggerRechargeWave() {
        if (breakCooldownActive || attachedTo.phaseCloak.effectLevel <= 0f) return
        shader.triggerRecharge()
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

    /**
     * Reach shield_collapse particles: a ring of soft puffs along the outline drifting outward, white
     * sparks flung outward from the outline, and one short flare at the centre.
     */
    private fun spawnBreakFx() {
        val engine = Global.getCombatEngine()
        val outline = getShipPolygonInWorld(attachedTo)
        if (outline.size < 3) return
        val center = attachedTo.location
        val shipVel = attachedTo.velocity
        val radius = max(attachedTo.collisionRadius, 20f)

        val ringSize = (radius * 0.12f).coerceIn(12f, 60f)
        for (p in evenOutlinePoints(outline, RING_COUNT)) {
            val out = outward(p, center)
            val vel = Vector2f(shipVel.x + out.x * 150f, shipVel.y + out.y * 150f)
            engine.addSmoothParticle(p, vel, ringSize, 1f, 0.1f, 0.3f + random.nextFloat() * 0.2f, RING_COLOR)
        }

        for (p in evenOutlinePoints(outline, SPARK_COUNT, random.nextFloat())) {
            val out = outward(p, center)
            val jitterAngle = (random.nextFloat() - 0.5f) * 1.2f
            val c = kotlin.math.cos(jitterAngle)
            val s = kotlin.math.sin(jitterAngle)
            val dirX = out.x * c - out.y * s
            val dirY = out.x * s + out.y * c
            val speed = 200f + random.nextFloat() * 300f
            val vel = Vector2f(shipVel.x + dirX * speed, shipVel.y + dirY * speed)
            val size = 4f + random.nextFloat() * 5f
            engine.addHitParticle(p, vel, size, 1f, 0.2f + random.nextFloat() * 0.25f, SPARK_COLOR)
        }

        engine.addHitParticle(Vector2f(center), Vector2f(shipVel), radius * 2.5f, 1f, 0.15f, FLARE_COLOR)
    }

    /** [count] points at even arc length along the closed [outline], starting at [phase] (0..1) of the perimeter. */
    private fun evenOutlinePoints(outline: Array<Vector2f>, count: Int, phase: Float = 0f): List<Vector2f> {
        val n = outline.size
        val lengths = FloatArray(n)
        var perimeter = 0f
        for (i in 0 until n) {
            val a = outline[i]
            val b = outline[(i + 1) % n]
            lengths[i] = Vector2f.sub(b, a, null).length()
            perimeter += lengths[i]
        }
        if (perimeter <= 0f) return emptyList()
        val step = perimeter / count
        val points = ArrayList<Vector2f>(count)
        var edge = 0
        var edgeStart = 0f
        for (k in 0 until count) {
            var target = (phase * perimeter + k * step) % perimeter
            // walk to the edge containing target (edges are visited in order; wrap resets)
            if (target < edgeStart) {
                edge = 0
                edgeStart = 0f
            }
            while (edge < n - 1 && target > edgeStart + lengths[edge]) {
                edgeStart += lengths[edge]
                edge++
            }
            val a = outline[edge]
            val b = outline[(edge + 1) % n]
            val t = if (lengths[edge] > 0f) ((target - edgeStart) / lengths[edge]).coerceIn(0f, 1f) else 0f
            points.add(Vector2f(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t))
        }
        return points
    }

    private fun outward(p: Vector2f, center: Vector2f): Vector2f {
        val dx = p.x - center.x
        val dy = p.y - center.y
        val len = sqrt(dx * dx + dy * dy)
        return if (len > 0.001f) Vector2f(dx / len, dy / len) else Vector2f(1f, 0f)
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
        shader.updateShieldHPPercent(if (shader.isCollapseActive()) 0f else stats.shieldHealth / stats.maxShieldHealth)
        shader.render(viewport)
    }

    override fun advance(amount: Float) {
        shader.updateTime(amount)

        // Collapse over: stop drawing until the cloak is fully down, then clear the collapse so the
        // shield draws again when re-raised.
        if (!suppressUntilOff && shader.isCollapseFinished()) {
            suppressUntilOff = true
        }
        if (suppressUntilOff && attachedTo.phaseCloak.effectLevel <= 0f) {
            suppressUntilOff = false
            shader.resetImpact()
        }

        if (breakCooldownActive) {
            if (Global.getCombatEngine().getTotalElapsedTime(false) > lastEMPTriggerTime + EMPCooldown) {
                spawnLightning()
            }
        }

        // Re-raise after a break: recharge wave once the activation sweep completes
        if (pendingRaiseWave && !breakCooldownActive && attachedTo.phaseCloak.effectLevel >= 1f) {
            pendingRaiseWave = false
            shader.triggerRecharge()
        }
    }

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

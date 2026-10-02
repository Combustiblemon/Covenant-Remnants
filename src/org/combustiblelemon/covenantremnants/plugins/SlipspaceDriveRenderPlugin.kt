package org.combustiblelemon.covenantremnants.plugins

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.CombatEngineLayers
import com.fs.starfarer.api.combat.CombatEntityAPI
import com.fs.starfarer.api.combat.CombatLayeredRenderingPlugin
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ViewportAPI
import org.combustiblelemon.covenantremnants.utils.render.blink.BlinkRenderer
import org.combustiblelemon.covenantremnants.utils.render.blink.BlinkRipple
import org.combustiblelemon.covenantremnants.utils.render.rupture.RuptureEffect
import org.combustiblelemon.covenantremnants.utils.render.rupture.RuptureRenderer
import org.lwjgl.util.vector.Vector2f
import java.util.EnumSet

/**
 * One-per-combat renderer for the slipspace drive blink (created by
 * [org.combustiblelemon.covenantremnants.systems.SlipspaceDrive.renderPlugin]). Owns every live
 * portal (a [RuptureEffect] of kind BLINK, drawn by its own [RuptureRenderer]) and hull ripple
 * ([BlinkRipple]) of every blinking ship. Portals draw below the ships (the ship's ghost slides
 * over them into / out of the portal); ripples draw above the ships, over the hull.
 */
class SlipspaceDriveRenderPlugin : CombatLayeredRenderingPlugin {
    private val portals = ArrayList<RuptureEffect>()
    private val ripples = ArrayList<BlinkRipple>()
    private val portalRenderer = RuptureRenderer()
    private var time = 0f

    fun addPortal(center: Vector2f, radius: Float, openDuration: Float): RuptureEffect =
        RuptureEffect.blink(center, radius, openDuration).also { portals.add(it) }

    fun addRipple(ship: ShipAPI, duration: Float): BlinkRipple =
        BlinkRipple(ship, duration).also { ripples.add(it) }

    override fun advance(amount: Float) {
        if (Global.getCombatEngine()?.isPaused == true) return
        time += amount
        portalRenderer.advance(amount)
        val portalIterator = portals.iterator()
        while (portalIterator.hasNext()) {
            val portal = portalIterator.next()
            portal.advance(amount)
            if (portal.isDone) portalIterator.remove()
        }
        val rippleIterator = ripples.iterator()
        while (rippleIterator.hasNext()) {
            val ripple = rippleIterator.next()
            ripple.advance(amount)
            if (ripple.isDone) rippleIterator.remove()
        }
    }

    override fun render(layer: CombatEngineLayers, viewport: ViewportAPI) {
        when (layer) {
            CombatEngineLayers.BELOW_SHIPS_LAYER -> {
                if (portals.isEmpty()) return
                portalRenderer.effects.clear()
                portalRenderer.effects.addAll(portals)
                portalRenderer.render(viewport)
            }
            CombatEngineLayers.ABOVE_SHIPS_LAYER -> BlinkRenderer.drawRipples(ripples, viewport, time)
            else -> {}
        }
    }

    override fun getActiveLayers(): EnumSet<CombatEngineLayers> =
        EnumSet.of(CombatEngineLayers.BELOW_SHIPS_LAYER, CombatEngineLayers.ABOVE_SHIPS_LAYER)

    override fun getRenderRadius(): Float = Float.MAX_VALUE

    override fun init(entity: CombatEntityAPI) {}

    override fun cleanup() {
        portalRenderer.cleanup()
    }

    override fun isExpired(): Boolean = false
}

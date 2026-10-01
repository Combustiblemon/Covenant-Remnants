package org.combustiblelemon.covenantremnants.plugins

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin
import com.fs.starfarer.api.combat.CombatEngineLayers
import com.fs.starfarer.api.combat.ShipSystemAPI
import com.fs.starfarer.api.combat.ViewportAPI
import com.fs.starfarer.api.util.Misc
import org.combustiblelemon.covenantremnants.systems.FleetSlipspaceJump
import org.combustiblelemon.covenantremnants.utils.render.rupture.RuptureEffect
import org.combustiblelemon.covenantremnants.utils.render.rupture.RuptureRenderer
import org.lazywizard.lazylib.opengl.DrawUtils
import org.lwjgl.opengl.GL11
import java.awt.Color
import java.util.EnumSet

/**
 * One-per-combat renderer for the Fleet Slipspace Jump (created by
 * [FleetSlipspaceJump.renderPlugin]). Owns the [RuptureRenderer] and every live [RuptureEffect]
 * (entry and exit ruptures of all carriers), and draws the player's aiming circles while the jump
 * is primed: a ring the size of the grab radius plus the carrier footprint at the cursor — green
 * when the jump is valid, red when it is not.
 *
 * Draws above ships, fighters and missiles so the opaque rupture hides its cargo.
 */
class FleetSlipspaceJumpRenderPlugin : BaseCombatLayeredRenderingPlugin(CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER) {

    private companion object {
        val PREVIEW_VALID = Color(90, 255, 140, 110)
        val PREVIEW_INVALID = Color(255, 80, 80, 110)
        const val PREVIEW_SEGMENTS = 96
    }

    private val effects = ArrayList<RuptureEffect>()

    // Builds its programs lazily in render(), on the GL thread.
    private val shader = RuptureRenderer()

    fun addEffect(effect: RuptureEffect): RuptureEffect {
        effects.add(effect)
        return effect
    }

    override fun advance(amount: Float) {
        if (Global.getCombatEngine()?.isPaused == true) return
        shader.advance(amount)
        val iterator = effects.iterator()
        while (iterator.hasNext()) {
            val effect = iterator.next()
            effect.advance(amount)
            if (effect.isDone) iterator.remove()
        }
    }

    override fun render(layer: CombatEngineLayers, viewport: ViewportAPI) {
        renderDestinationPreview()

        if (effects.isEmpty()) return
        shader.effects.clear()
        shader.effects.addAll(effects)
        shader.render(viewport)
    }

    /** Player-only: the grab footprint at the cursor while the jump is primed (aiming). */
    private fun renderDestinationPreview() {
        val engine = Global.getCombatEngine() ?: return
        val ship = engine.playerShip ?: return
        if (!FleetSlipspaceJump.isPlayerControlled(ship, engine) || !FleetSlipspaceJump.isPrimed(ship)) return
        val system = FleetSlipspaceJump.system(ship) ?: return
        if (system.state != ShipSystemAPI.SystemState.IDLE || system.isCoolingDown) return
        // The aim point, not the ship's mouse target: that one is not updated while paused.
        val target = FleetSlipspaceJump.aimPoint

        val valid = ship.fluxLevel < FleetSlipspaceJump.fluxGate.value &&
                FleetSlipspaceJump.destinationProblem(ship, target, engine) == null
        val color = if (valid) PREVIEW_VALID else PREVIEW_INVALID

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D)
            GL11.glEnable(GL11.GL_BLEND)
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
            GL11.glLineWidth(2f)
            Misc.setColor(color)
            DrawUtils.drawCircle(target.x, target.y, FleetSlipspaceJump.grabRadius.value, PREVIEW_SEGMENTS, false)
            // Carrier footprint at the center
            DrawUtils.drawCircle(target.x, target.y, ship.collisionRadius, 48, false)
        } finally {
            GL11.glPopAttrib()
        }
    }

    override fun cleanup() {
        shader.cleanup()
        effects.clear()
    }

    override fun getActiveLayers(): EnumSet<CombatEngineLayers> = EnumSet.of(layer)

    override fun getRenderRadius(): Float = Float.MAX_VALUE

    override fun isExpired(): Boolean = false
}

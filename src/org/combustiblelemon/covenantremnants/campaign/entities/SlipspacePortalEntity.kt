package org.combustiblelemon.covenantremnants.campaign.entities

import com.fs.starfarer.api.campaign.CampaignEngineLayers
import com.fs.starfarer.api.combat.ViewportAPI
import com.fs.starfarer.api.impl.campaign.BaseCustomEntityPlugin
import org.combustiblelemon.covenantremnants.campaign.abilities.SlipspaceJumpRules
import org.combustiblelemon.covenantremnants.utils.render.rupture.ArkVariant
import org.combustiblelemon.covenantremnants.utils.render.rupture.RuptureEffect
import org.combustiblelemon.covenantremnants.utils.render.rupture.RuptureRenderer
import org.lwjgl.util.vector.Vector2f

/**
 * The slipspace pocket's entrance and exit portals (custom entity types
 * `covenant_remnants_slipspace_entrance` / `_exit`, and the legacy `slipspace_jump_point`): the
 * Halo 3 "The Ark" slipspace exit ([RuptureEffect.Kind.ARK], developer ruling 2026-10-08). Idle
 * between uses (a dim held tube and ring with flickers), a full burst when the fleet arrives
 * through the entrance or leaves through the exit ([burst], called by the campaign ability).
 *
 * The entity's `facing` is the direction a fleet leaves the tube (set by the route layout); the
 * tube extends behind the mouth. Everything render-side is transient and rebuilt lazily; the
 * class name is kept because the entity json and saves reference it.
 */
class SlipspacePortalEntity : BaseCustomEntityPlugin() {
    private companion object {
        /** One renderer for all portals: one screen-copy texture per session (entity plugins have no cleanup hook). */
        val renderer: RuptureRenderer by lazy { RuptureRenderer() }

        val LAYER = CampaignEngineLayers.TERRAIN_8
    }

    @Transient
    private var effect: RuptureEffect? = null

    private fun idleRadius(): Float = entity.radius * SlipspaceJumpRules.arkPortalRadiusMult.value

    private fun effect(): RuptureEffect {
        effect?.let { return it }
        val created = RuptureEffect.ark(Vector2f(entity.location), entity.facing, idleRadius(), persistent = true)
        effect = created
        return created
    }

    override fun getRenderRange(): Float = idleRadius() * 12f

    override fun advance(amount: Float) {
        if (amount <= 0f) return
        if (!entity.isInCurrentLocation) {
            // A burst cut off by the player leaving must not resume on the next visit.
            effect = null
            return
        }
        val e = effect()
        e.center.set(entity.location)
        e.facing = entity.facing
        e.advance(amount)
    }

    override fun render(layer: CampaignEngineLayers?, viewport: ViewportAPI?) {
        if (layer != LAYER || viewport == null) return
        val e = effect ?: return
        val alpha = viewport.alphaMult
        if (alpha <= 0f) return
        renderer.effects.clear()
        renderer.effects.add(e)
        renderer.alphaMult = alpha
        renderer.render(viewport)
    }

    /**
     * Plays the exit burst of [variant] for a fleet of Ark radius [radiusWorld]; [onOpen] runs once
     * when the burst opens after its pre-roll (the caller plays the sound then).
     */
    fun burst(variant: ArkVariant, radiusWorld: Float, onOpen: (() -> Unit)? = null) {
        val e = effect()
        e.onArkOpen = onOpen
        e.burst(variant, radiusWorld)
    }
}

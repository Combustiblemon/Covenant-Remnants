package org.combustiblelemon.covenantremnants.campaign.systems.scripts

import com.fs.starfarer.api.EveryFrameScript
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.StarSystemAPI
import com.fs.starfarer.api.impl.campaign.ids.Abilities
import org.combustiblelemon.covenantremnants.campaign.systems.findSlipspaceSystem
import org.combustiblelemon.covenantremnants.utils.extensions.isInSlipspace
import java.awt.Color

/**
 * Pocket-system script.
 *
 * 1. While the player fleet is inside the slipspace pocket, the vanilla movement abilities are
 *    held disabled — only the path's current moves the fleet (developer ruling 2026-10-07).
 *    Guarded on the player actually being in the pocket: non-current star systems are advanced
 *    round-robin every frame too, so an unguarded script here would disable these abilities
 *    sector-wide (that was the prototype's bug).
 * 2. The engine's background star / dust particle layer is hidden in the pocket (developer
 *    2026-10-08): the location's particle colour is shifted to fully transparent every frame, the
 *    way vanilla's abyssal hyperspace darkens it. The engine only pushes the colour to the
 *    particles while a shift is active (and skips the layer at alpha 0), so a one-off base colour
 *    does nothing — the shift must be nudged each frame. Done regardless of where the player is,
 *    so the layer is already gone when the pocket becomes the current location.
 */
class SlipspaceSystemEFS : EveryFrameScript {
    private companion object {
        val BLOCKED = listOf(Abilities.TRANSVERSE_JUMP, Abilities.SUSTAINED_BURN, Abilities.EMERGENCY_BURN)
        const val PARTICLE_SHIFT_SOURCE = "covenant_remnants_slipspace_pocket"
        val PARTICLE_COLOR = Color(0, 0, 0, 0)
    }

    @Transient
    private var pocket: StarSystemAPI? = null

    override fun isDone(): Boolean = false

    override fun runWhilePaused(): Boolean = true

    override fun advance(amount: Float) {
        hideParticles()
        val fleet = Global.getSector().playerFleet ?: return
        if (!fleet.isInSlipspace) return
        for (id in BLOCKED) {
            val ability = fleet.getAbility(id) ?: continue
            if (ability.isActive) ability.deactivate()
            ability.forceDisable()
        }
    }

    private fun hideParticles() {
        val system = pocket ?: findSlipspaceSystem()?.also { pocket = it } ?: return
        system.backgroundParticleColorShifter.shift(PARTICLE_SHIFT_SOURCE, PARTICLE_COLOR, 0.1f, 0.1f, 1f)
    }
}

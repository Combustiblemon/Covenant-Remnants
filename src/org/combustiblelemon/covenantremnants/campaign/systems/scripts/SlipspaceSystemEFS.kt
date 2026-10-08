package org.combustiblelemon.covenantremnants.campaign.systems.scripts

import com.fs.starfarer.api.EveryFrameScript
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.impl.campaign.ids.Abilities
import org.combustiblelemon.covenantremnants.utils.extensions.isInSlipspace

/**
 * Pocket-system script: while the player fleet is inside the slipspace pocket, the vanilla
 * movement abilities are held disabled — only the path's current moves the fleet (developer
 * ruling 2026-10-07).
 *
 * Guarded on the player actually being in the pocket: non-current star systems are advanced
 * round-robin every frame too, so an unguarded script here would disable these abilities
 * sector-wide (that was the prototype's bug).
 */
class SlipspaceSystemEFS : EveryFrameScript {
    private companion object {
        val BLOCKED = listOf(Abilities.TRANSVERSE_JUMP, Abilities.SUSTAINED_BURN, Abilities.EMERGENCY_BURN)
    }

    override fun isDone(): Boolean = false

    override fun runWhilePaused(): Boolean = true

    override fun advance(amount: Float) {
        val fleet = Global.getSector().playerFleet ?: return
        if (!fleet.isInSlipspace) return
        for (id in BLOCKED) {
            val ability = fleet.getAbility(id) ?: continue
            if (ability.isActive) ability.deactivate()
            ability.forceDisable()
        }
    }
}

package org.combustiblelemon.covenantremnants.campaign.systems.scripts

import com.fs.starfarer.api.EveryFrameScript
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.impl.campaign.ids.Abilities

class SlipspaceSystemEFS : EveryFrameScript {
    override fun isDone(): Boolean {
        return false
    }

    override fun runWhilePaused(): Boolean {
        return true
    }

    override fun advance(amount: Float) {
        Global.getSector().playerFleet.getAbility(Abilities.TRANSVERSE_JUMP)?.forceDisable()
    }
}
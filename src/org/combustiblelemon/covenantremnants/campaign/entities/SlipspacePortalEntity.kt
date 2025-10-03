package org.combustiblelemon.covenantremnants.campaign.entities

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CampaignEngineLayers
import com.fs.starfarer.api.combat.ViewportAPI
import com.fs.starfarer.api.impl.campaign.BaseCustomEntityPlugin
import com.fs.starfarer.api.util.Misc
import org.combustiblelemon.covenantremnants.utils.extensions.isInSlipspace
import org.combustiblelemon.covenantremnants.utils.render.SlipspacePortalEntityShader
import org.combustiblelemon.covenantremnants.utils.render.SlipspacePortalEntityShader.AnimationState

class SlipspacePortalEntity : BaseCustomEntityPlugin() {
    @Transient
    var shader: SlipspacePortalEntityShader = SlipspacePortalEntityShader()

    companion object {
        const val RADIUS_TO_ANIMATE = 100f
    }

    override fun getRenderRange(): Float {
        return Float.MAX_VALUE
    }

    override fun advance(amount: Float) {
        val playerFleet = Global.getSector().playerFleet

        if (playerFleet.isInSlipspace) {
            val location = playerFleet.location
            val animationState = shader.getAnimationState()

            if (Misc.getDistance(this.entity.location, location) <= RADIUS_TO_ANIMATE) {
                when (animationState) {
                    AnimationState.CLOSED,
                    AnimationState.CLOSING -> shader.triggerOpening()

                    AnimationState.OPENING,
                    AnimationState.OPEN -> {
                    }
                }
            } else {
                when (animationState) {
                    AnimationState.CLOSED,
                    AnimationState.CLOSING -> {
                    }

                    AnimationState.OPENING,
                    AnimationState.OPEN -> shader.triggerClosing()
                }
            }
        }

        shader.updateTime(amount)
    }

    override fun render(layer: CampaignEngineLayers?, viewport: ViewportAPI?) {
        if (viewport == null) {
            return
        }

        shader.setPosition(this.entity.location.x, this.entity.location.y, 45f)
        shader.render(viewport)
    }
}

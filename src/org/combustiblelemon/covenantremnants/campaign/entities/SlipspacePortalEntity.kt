package org.combustiblelemon.covenantremnants.campaign.entities

import com.fs.starfarer.api.campaign.CampaignEngineLayers
import com.fs.starfarer.api.combat.ViewportAPI
import com.fs.starfarer.api.impl.campaign.BaseCustomEntityPlugin
import org.combustiblelemon.covenantremnants.utils.render.SlipspacePortalEntityShader

class SlipspacePortalEntity : BaseCustomEntityPlugin() {
    var shader: SlipspacePortalEntityShader = SlipspacePortalEntityShader()

    override fun getRenderRange(): Float {
        return Float.MAX_VALUE
    }

    override fun advance(amount: Float) {
       shader.updateTime(amount)
    }

    override fun render(layer: CampaignEngineLayers?, viewport: ViewportAPI?) {
        if (viewport == null) {
            return
        }

        shader?.setPosition(this.entity.location.x, this.entity.location.y, 45f)
        shader?.render(viewport)
    }
}

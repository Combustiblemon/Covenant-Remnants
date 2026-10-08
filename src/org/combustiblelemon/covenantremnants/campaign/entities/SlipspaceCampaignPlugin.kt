package org.combustiblelemon.covenantremnants.campaign.entities

import com.fs.starfarer.api.campaign.BaseCampaignPlugin
import com.fs.starfarer.api.campaign.CampaignPlugin.PickPriority
import com.fs.starfarer.api.campaign.InteractionDialogPlugin
import com.fs.starfarer.api.PluginPick
import com.fs.starfarer.api.campaign.SectorEntityToken
import org.combustiblelemon.covenantremnants.campaign.systems.SLIPSPACE_EXIT_ID
import org.combustiblelemon.covenantremnants.campaign.systems.SLIPSPACE_EXIT_TYPE

/**
 * Routes interaction with the pocket's exit portal to [SlipspaceExitDialogPlugin]. Transient:
 * registered on every game load from the mod plugin. `MOD_SPECIFIC` outranks the core
 * rules-based pick that the `has_interaction_dialog` tag would otherwise get.
 */
class SlipspaceCampaignPlugin : BaseCampaignPlugin() {
    override fun getId(): String = "covenant_remnants_slipspace"

    override fun isTransient(): Boolean = true

    override fun pickInteractionDialogPlugin(interactionTarget: SectorEntityToken?): PluginPick<InteractionDialogPlugin>? {
        if (interactionTarget == null) return null
        val isExit = interactionTarget.id == SLIPSPACE_EXIT_ID || interactionTarget.customEntityType == SLIPSPACE_EXIT_TYPE
        if (!isExit) return null
        return PluginPick(SlipspaceExitDialogPlugin(), PickPriority.MOD_SPECIFIC)
    }
}

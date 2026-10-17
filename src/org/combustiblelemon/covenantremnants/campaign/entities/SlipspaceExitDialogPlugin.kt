package org.combustiblelemon.covenantremnants.campaign.entities

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.InteractionDialogAPI
import com.fs.starfarer.api.campaign.InteractionDialogPlugin
import com.fs.starfarer.api.campaign.SectorEntityToken
import com.fs.starfarer.api.campaign.StarSystemAPI
import com.fs.starfarer.api.campaign.rules.MemoryAPI
import com.fs.starfarer.api.combat.EngagementResultAPI
import com.fs.starfarer.api.util.Misc
import org.combustiblelemon.covenantremnants.campaign.abilities.SlipspaceJump
import org.combustiblelemon.covenantremnants.campaign.abilities.SlipspaceJumpRules
import org.combustiblelemon.covenantremnants.campaign.systems.slipspaceRouteDestination
import org.combustiblelemon.covenantremnants.campaign.systems.slipspaceRoutePoint

/**
 * Dialog of the pocket's exit portal: shows where the corridor leads and lets the fleet leave
 * slipspace (the ability runs the exit lurch, the arrival rupture and the transition).
 */
class SlipspaceExitDialogPlugin : InteractionDialogPlugin {
    private companion object {
        const val OPT_EXIT = "covenant_remnants_slipspace_exit"
        const val OPT_LEAVE = "covenant_remnants_slipspace_leave"
    }

    private lateinit var dialog: InteractionDialogAPI
    private var entity: SectorEntityToken? = null
    private var ability: SlipspaceJump? = null

    override fun init(dialog: InteractionDialogAPI) {
        this.dialog = dialog
        entity = dialog.interactionTarget
        try {
            dialog.visualPanel.showImagePortion("illustrations", "jump_point_normal", 640f, 400f, 0f, 0f, 480f, 300f)
        } catch (_: Exception) {
        }

        val fleet = Global.getSector().playerFleet
        val pocket = fleet.containingLocation as? StarSystemAPI
        val destination = pocket?.let { slipspaceRouteDestination(it) }
        val plotted = pocket?.let { slipspaceRoutePoint(it) } != null
        val text = dialog.textPanel
        text.addPara("The corridor ends here, at the far aperture of the slipspace rupture.")
        if (destination != null) {
            val where = if (plotted) "It opens onto the %s, at the plotted coordinates." else "It opens onto the %s."
            text.addPara(where, Misc.getHighlightColor(), destination.nameWithLowercaseType)
        }

        ability = fleet.getAbility(SlipspaceJumpRules.ABILITY_ID) as? SlipspaceJump
        val ready = ability?.canExit() == true
        if (ready) {
            dialog.optionPanel.addOption("Exit slipspace", OPT_EXIT)
        } else {
            text.addPara("The drive is not ready to open the exit.", Misc.getNegativeHighlightColor())
        }
        dialog.optionPanel.addOption("Leave", OPT_LEAVE)
        dialog.setOptionOnEscape("Leave", OPT_LEAVE)
    }

    override fun optionSelected(optionText: String?, optionData: Any?) {
        when (optionData) {
            OPT_EXIT -> {
                dialog.dismiss()
                Global.getSector().isPaused = false
                val portal = entity ?: return
                ability?.beginExit(portal)
            }
            OPT_LEAVE -> dialog.dismiss()
        }
    }

    override fun optionMousedOver(optionText: String?, optionData: Any?) {}

    override fun advance(amount: Float) {}

    override fun backFromEngagement(battleResult: EngagementResultAPI?) {}

    override fun getContext(): Any? = null

    override fun getMemoryMap(): Map<String, MemoryAPI>? = null
}

package org.combustiblelemon.covenantremnants.campaign.items

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CargoTransferHandlerAPI
import com.fs.starfarer.api.campaign.impl.items.BaseSpecialItemPlugin
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.Misc
import org.combustiblelemon.covenantremnants.campaign.abilities.SlipspaceJumpRules

/**
 * Covenant Slipspace Navigation System (`covenant_remnants_slipspace_navigation`,
 * special_items.csv): right-click installs the Slipspace Jump navigation upgrade (exact exit
 * points, in-system jumps; developer rulings 2026-10-12). Without the Slipspace Jump the item
 * refuses and stays in the cargo; once installed, further copies are kept too. Placeholder icon
 * (vanilla hyperspace data) until real art exists.
 */
class SlipspaceNavigationSystemPlugin : BaseSpecialItemPlugin() {

    private val hasJump: Boolean
        get() = Global.getSector()?.playerFleet?.hasAbility(SlipspaceJumpRules.ABILITY_ID) == true

    private val installed: Boolean
        get() = SlipspaceJumpRules.hasNavigationUpgrade()

    override fun createTooltip(
        tooltip: TooltipMakerAPI,
        expanded: Boolean,
        transferHandler: CargoTransferHandlerAPI?,
        stackSource: Any?
    ) {
        val opad = 10f

        if (!Global.CODEX_TOOLTIP_MODE) {
            tooltip.addTitle(name)
        } else {
            tooltip.addSpacer(-opad)
        }
        designType?.let { Misc.addDesignTypePara(tooltip, it, opad) }
        if (spec.desc.isNotEmpty()) {
            if (Global.CODEX_TOOLTIP_MODE) tooltip.setParaSmallInsignia()
            tooltip.addPara(spec.desc, Misc.getTextColor(), opad)
        }
        addCostLabel(tooltip, opad, transferHandler, stackSource)

        if (!Global.CODEX_TOOLTIP_MODE) {
            when {
                installed -> tooltip.addPara("Slipspace Navigation System already installed", Misc.getGrayColor(), opad)
                !hasJump -> tooltip.addPara("Requires the Slipspace Jump ability", Misc.getNegativeHighlightColor(), opad)
                else -> tooltip.addPara("Right-click to install the Slipspace Navigation System", Misc.getPositiveHighlightColor(), opad)
            }
        }
    }

    override fun isTooltipExpandable(): Boolean = false

    override fun hasRightClickAction(): Boolean = true

    // Read by the game before the action: kept unless it gets installed
    override fun shouldRemoveOnRightClickAction(): Boolean = hasJump && !installed

    override fun performRightClickAction() {
        val messages = Global.getSector().campaignUI.messageDisplay
        when {
            !hasJump -> messages.addMessage("$name: requires Slipspace Jump")
            installed -> messages.addMessage("$name: already installed")
            else -> {
                SlipspaceJumpRules.grantUpgradeToPlayer()
                Global.getSoundPlayer().playUISound(spec.soundId, 1f, 1f)
                messages.addMessage("$name installed - slipspace exits can now be plotted")
            }
        }
    }
}

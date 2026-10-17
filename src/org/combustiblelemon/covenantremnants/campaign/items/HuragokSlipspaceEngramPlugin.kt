package org.combustiblelemon.covenantremnants.campaign.items

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CargoTransferHandlerAPI
import com.fs.starfarer.api.campaign.impl.items.BaseSpecialItemPlugin
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.Misc
import org.combustiblelemon.covenantremnants.campaign.abilities.SlipspaceJumpRules

/**
 * Covenant Slipspace Drive Schematics (`covenant_remnants_slipspace_engram`, special_items.csv;
 * named "Huragok Slipspace Engram" until 2026-10-12 — the id and this class name stay for saves):
 * right-click teaches the player the campaign Slipspace Jump ability. Modelled on vanilla's Janus
 * Device, with the blueprint items' "already known" handling: the item is only used up when it
 * teaches the ability. Placeholder icon (vanilla Janus Device) until real art exists.
 */
class HuragokSlipspaceEngramPlugin : BaseSpecialItemPlugin() {

    private val known: Boolean
        get() = Global.getSector()?.playerFleet?.hasAbility(SlipspaceJumpRules.ABILITY_ID) == true

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
            if (known) {
                tooltip.addPara("Slipspace Jump already known", Misc.getGrayColor(), opad)
            } else {
                tooltip.addPara("Right-click to learn the Slipspace Jump ability", Misc.getPositiveHighlightColor(), opad)
            }
        }
    }

    override fun isTooltipExpandable(): Boolean = false

    override fun hasRightClickAction(): Boolean = true

    // Same as vanilla's blueprint items: kept when the ability is already known
    override fun shouldRemoveOnRightClickAction(): Boolean = !known

    override fun performRightClickAction() {
        val messages = Global.getSector().campaignUI.messageDisplay
        if (known) {
            messages.addMessage("$name: Slipspace Jump already known")
            return
        }
        SlipspaceJumpRules.grantToPlayer()
        Global.getSoundPlayer().playUISound(spec.soundId, 1f, 1f)
        messages.addMessage("$name integrated - Slipspace Jump ability learned")
    }
}

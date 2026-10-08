package org.combustiblelemon.covenantremnants.campaign.abilities

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.BaseCampaignEntityPickerListener
import com.fs.starfarer.api.campaign.InteractionDialogAPI
import com.fs.starfarer.api.campaign.InteractionDialogPlugin
import com.fs.starfarer.api.campaign.SectorEntityToken
import com.fs.starfarer.api.campaign.StarSystemAPI
import com.fs.starfarer.api.campaign.rules.MemoryAPI
import com.fs.starfarer.api.combat.EngagementResultAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.Misc

/**
 * Host dialog for the destination pick: opens the sector-map entity picker (the gate-travel map)
 * over the player's star systems. A pick hands the system to the ability and unpauses; a cancel
 * (picker cancel, Escape, or the fallback option) returns the ability to idle and restores the
 * pause state from before the dialog. Nothing is charged here.
 */
class SlipspaceDestinationDialog(private val ability: SlipspaceJump) : InteractionDialogPlugin {
    private companion object {
        const val OPT_SELECT = "covenant_remnants_slipspace_select"
        const val OPT_CANCEL = "covenant_remnants_slipspace_cancel"
    }

    private lateinit var dialog: InteractionDialogAPI
    private var pickerShown = false
    private var resolved = false
    private var pickerAttempts = 0

    override fun init(dialog: InteractionDialogAPI) {
        this.dialog = dialog
        dialog.textPanel.addPara("The slipspace drive spools up. Select a destination star system.")
        dialog.optionPanel.addOption("Select destination", OPT_SELECT)
        dialog.optionPanel.addOption("Cancel", OPT_CANCEL)
        dialog.setOptionOnEscape("Cancel", OPT_CANCEL)
        showPicker()
    }

    override fun advance(amount: Float) {
        if (!pickerShown && !resolved) showPicker()
    }

    private fun showPicker() {
        pickerShown = true
        val fleet = Global.getSector().playerFleet
        val origin = fleet.containingLocation as? StarSystemAPI
        if (origin == null) {
            cancel()
            return
        }
        val entities = SlipspaceJumpRules.destinations(origin)
        if (entities.isEmpty()) {
            dialog.textPanel.addPara("No star system can be reached from here.", Misc.getNegativeHighlightColor())
            return
        }
        try {
            dialog.showCampaignEntityPicker(
                "Select slipspace destination", "Destination:", "Open rupture",
                Global.getSector().playerFaction, entities, Listener(origin)
            )
        } catch (e: Exception) {
            // Too early in the dialog's life: retried from advance(), a few times.
            pickerAttempts++
            if (pickerAttempts >= 5) {
                dialog.textPanel.addPara("The sector map could not be opened.", Misc.getNegativeHighlightColor())
                cancel()
            } else {
                pickerShown = false
            }
        }
    }

    private inner class Listener(private val origin: StarSystemAPI) : BaseCampaignEntityPickerListener() {
        private fun systemOf(entity: SectorEntityToken): StarSystemAPI? = Misc.getStarSystemForAnchor(entity)

        override fun pickedEntity(entity: SectorEntityToken) {
            val system = systemOf(entity)
            if (system == null) {
                cancel()
                return
            }
            if (resolved) return
            resolved = true
            dialog.dismiss()
            Global.getSector().isPaused = false
            ability.onDestinationPicked(system)
        }

        override fun cancelledEntityPicking() {
            cancel()
        }

        override fun getMenuItemNameOverrideFor(entity: SectorEntityToken): String? = systemOf(entity)?.nameWithLowercaseType

        override fun getSelectedTextOverrideFor(entity: SectorEntityToken): String? {
            val system = systemOf(entity) ?: return null
            val ly = SlipspaceJumpRules.distanceLY(origin, system)
            return "${system.nameWithLowercaseType} - ${Misc.getRoundedValueMaxOneAfterDecimal(ly)} LY"
        }

        override fun createInfoText(info: TooltipMakerAPI, entity: SectorEntityToken) {
            val system = systemOf(entity) ?: return
            val fleet = Global.getSector().playerFleet
            val cost = SlipspaceJumpRules.fuelCost(fleet, origin, system)
            val available = fleet.cargo.fuel.toInt()
            val ly = SlipspaceJumpRules.distanceLY(origin, system)
            val required = if (cost > available) Misc.getNegativeHighlightColor() else Misc.getHighlightColor()
            info.setParaSmallInsignia()
            info.beginGrid(200f, 2, Misc.getGrayColor())
            info.setGridFontSmallInsignia()
            info.addToGrid(0, 0, "    Distance:", Misc.getRoundedValueMaxOneAfterDecimal(ly) + " LY", Misc.getHighlightColor())
            info.addToGrid(0, 1, "    Fuel required:", Misc.getWithDGS(cost.toFloat()), required)
            info.addToGrid(0, 2, "    Fuel available:", Misc.getWithDGS(available.toFloat()), Misc.getHighlightColor())
            info.addGrid(0f)
        }

        override fun canConfirmSelection(entity: SectorEntityToken): Boolean {
            val system = systemOf(entity) ?: return false
            val fleet = Global.getSector().playerFleet
            return SlipspaceJumpRules.fuelCost(fleet, origin, system) <= fleet.cargo.fuel
        }

        override fun getFuelColorAlphaMult(): Float = 0.5f

        override fun getFuelRangeMult(): Float = SlipspaceJumpRules.fuelRangeMult()
    }

    private fun cancel() {
        if (resolved) return
        resolved = true
        dialog.dismiss()
        Global.getSector().isPaused = ability.wasPausedBeforeDialog
        ability.onPickingCancelled()
    }

    override fun optionSelected(optionText: String?, optionData: Any?) {
        when (optionData) {
            OPT_SELECT -> {
                pickerShown = false
                showPicker()
            }
            OPT_CANCEL -> cancel()
        }
    }

    override fun optionMousedOver(optionText: String?, optionData: Any?) {}

    override fun backFromEngagement(battleResult: EngagementResultAPI?) {}

    override fun getContext(): Any? = null

    override fun getMemoryMap(): Map<String, MemoryAPI>? = null
}

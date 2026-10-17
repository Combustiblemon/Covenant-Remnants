package org.combustiblelemon.covenantremnants.campaign.abilities

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.BaseCampaignEntityPickerListener
import com.fs.starfarer.api.campaign.CampaignFleetAPI
import com.fs.starfarer.api.campaign.InteractionDialogAPI
import com.fs.starfarer.api.campaign.InteractionDialogPlugin
import com.fs.starfarer.api.campaign.SectorEntityToken
import com.fs.starfarer.api.campaign.StarSystemAPI
import com.fs.starfarer.api.campaign.rules.MemoryAPI
import com.fs.starfarer.api.combat.EngagementResultAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.Misc
import org.lwjgl.util.vector.Vector2f
import java.util.IdentityHashMap

/**
 * Host dialog for the destination pick: opens the sector-map entity picker (the gate-travel map)
 * over the player's star systems. A pick hands the system to the ability and unpauses; a cancel
 * (picker cancel, Escape, or the fallback option) returns the ability to idle and restores the
 * pause state from before the dialog. Nothing is charged here.
 *
 * With the navigation upgrade (developer rulings 2026-10-12) a star's dropdown also offers
 * "<system> - plot coordinates" (a token inside the system; the picker lists every entry located
 * in the clicked system), which opens [SlipspacePointDialog] on the next frame to plot the exit
 * point; the current system offers only that row (the in-system jump). Back on that map returns
 * to the sector map.
 */
class SlipspaceDestinationDialog(private val ability: SlipspaceJump) : InteractionDialogPlugin {
    private companion object {
        const val OPT_SELECT = "covenant_remnants_slipspace_select"
        const val OPT_CANCEL = "covenant_remnants_slipspace_cancel"
        const val MAX_OPEN_ATTEMPTS = 5
    }

    /** What a picker entry stands for: the system's edge (its hyperspace anchor) or a plotted point. */
    private enum class Kind { EDGE, POINT }

    private class Row(val system: StarSystemAPI, val kind: Kind)

    private lateinit var dialog: InteractionDialogAPI
    private var pickerShown = false
    private var resolved = false
    private var pickerAttempts = 0

    /** The picker's entries of the current showing; tokens are compared by identity. */
    private val rows = IdentityHashMap<SectorEntityToken, Row>()
    private var origin: StarSystemAPI? = null

    /** A plot-coordinates row was confirmed: its system map opens on the next frame. */
    private var pendingPointSystem: StarSystemAPI? = null
    private var pointOpenAttempts = 0

    /** The system map was confirmed: the jump starts on the next frame. */
    private var pendingResult: Pair<StarSystemAPI, Vector2f>? = null

    override fun init(dialog: InteractionDialogAPI) {
        this.dialog = dialog
        dialog.textPanel.addPara("The slipspace drive spools up. Select a destination star system.")
        dialog.optionPanel.addOption("Select destination", OPT_SELECT)
        dialog.optionPanel.addOption("Cancel", OPT_CANCEL)
        dialog.setOptionOnEscape("Cancel", OPT_CANCEL)
        showPicker()
    }

    override fun advance(amount: Float) {
        if (resolved) return
        pendingResult?.let {
            pendingResult = null
            resolve(it.first, it.second)
            return
        }
        pendingPointSystem?.let {
            openPointMap(it)
            return
        }
        if (!pickerShown) showPicker()
    }

    private fun showPicker() {
        pickerShown = true
        val fleet = Global.getSector().playerFleet
        val origin = fleet.containingLocation as? StarSystemAPI
        if (origin == null) {
            cancel()
            return
        }
        this.origin = origin
        rows.clear()
        val entities = ArrayList<SectorEntityToken>()
        for (anchor in SlipspaceJumpRules.destinations(origin)) {
            val system = Misc.getStarSystemForAnchor(anchor) ?: continue
            rows[anchor] = Row(system, Kind.EDGE)
            entities.add(anchor)
        }
        for (system in SlipspaceJumpRules.pointDestinations(origin)) {
            val at = SlipspaceJumpRules.systemCenter(system)
            val token = system.createToken(at.x, at.y)
            rows[token] = Row(system, Kind.POINT)
            entities.add(token)
        }
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
            if (pickerAttempts >= MAX_OPEN_ATTEMPTS) {
                dialog.textPanel.addPara("The sector map could not be opened.", Misc.getNegativeHighlightColor())
                cancel()
            } else {
                pickerShown = false
            }
        }
    }

    /**
     * Opens the system map for [system]. Called from [advance], one frame after the picker
     * confirmed a plot-coordinates row (the picker must have closed first).
     */
    private fun openPointMap(system: StarSystemAPI) {
        val origin = origin
        if (origin == null) {
            cancel()
            return
        }
        val map = SlipspacePointDialog(this, system, origin)
        try {
            dialog.showCustomVisualDialog(SlipspacePointDialog.WIDTH, SlipspacePointDialog.HEIGHT, map)
        } catch (e: Exception) {
            Global.getLogger(SlipspaceDestinationDialog::class.java).warn("Slipspace system map: open failed", e)
        }
        if (map.opened) {
            pendingPointSystem = null
            pointOpenAttempts = 0
        } else if (++pointOpenAttempts >= MAX_OPEN_ATTEMPTS) {
            pendingPointSystem = null
            pointOpenAttempts = 0
            dialog.textPanel.addPara("The system map could not be opened.", Misc.getNegativeHighlightColor())
            pickerShown = false
        }
    }

    /**
     * From [SlipspacePointDialog] once it has closed: a plotted [point] starts the jump on the next
     * frame; none (Back, Escape) returns to the sector map.
     */
    fun onPointDialogClosed(system: StarSystemAPI, point: Vector2f?) {
        if (resolved) return
        if (point != null) {
            pendingResult = system to Vector2f(point)
        } else {
            pickerShown = false
        }
    }

    private inner class Listener(private val origin: StarSystemAPI) : BaseCampaignEntityPickerListener() {
        private fun rowOf(entity: SectorEntityToken): Row? = rows[entity]

        /** Fuel for the jump a row stands for: the flat in-system cost, or per light-year. */
        private fun fuelCost(fleet: CampaignFleetAPI, row: Row): Int =
            if (row.system === origin) {
                SlipspaceJumpRules.blinkFuelCost(fleet)
            } else {
                SlipspaceJumpRules.fuelCost(fleet, origin, row.system)
            }

        override fun pickedEntity(entity: SectorEntityToken) {
            val row = rowOf(entity)
            if (row == null) {
                cancel()
                return
            }
            if (resolved) return
            when (row.kind) {
                Kind.EDGE -> resolve(row.system, null)
                // The picker closes itself after this call; the map opens from advance().
                Kind.POINT -> pendingPointSystem = row.system
            }
        }

        override fun cancelledEntityPicking() {
            cancel()
        }

        override fun getMenuItemNameOverrideFor(entity: SectorEntityToken): String? {
            val row = rowOf(entity) ?: return null
            // The dropdown sorts by this text: the edge row is a prefix of the plot row, so it comes first.
            return when (row.kind) {
                Kind.EDGE -> row.system.nameWithLowercaseType
                Kind.POINT -> "${row.system.nameWithLowercaseType} - plot coordinates"
            }
        }

        override fun getSelectedTextOverrideFor(entity: SectorEntityToken): String? {
            val row = rowOf(entity) ?: return null
            val name = row.system.nameWithLowercaseType
            if (row.system === origin) return "$name - plot coordinates, in-system"
            val ly = Misc.getRoundedValueMaxOneAfterDecimal(SlipspaceJumpRules.distanceLY(origin, row.system))
            return when (row.kind) {
                Kind.EDGE -> "$name - $ly LY"
                Kind.POINT -> "$name - plot coordinates - $ly LY"
            }
        }

        override fun createInfoText(info: TooltipMakerAPI, entity: SectorEntityToken) {
            val row = rowOf(entity) ?: return
            val fleet = Global.getSector().playerFleet
            val cost = fuelCost(fleet, row)
            val available = fleet.cargo.fuel.toInt()
            val required = if (cost > available) Misc.getNegativeHighlightColor() else Misc.getHighlightColor()
            val distance = if (row.system === origin) {
                "in-system"
            } else {
                Misc.getRoundedValueMaxOneAfterDecimal(SlipspaceJumpRules.distanceLY(origin, row.system)) + " LY"
            }
            info.setParaSmallInsignia()
            info.beginGrid(200f, 2, Misc.getGrayColor())
            info.setGridFontSmallInsignia()
            info.addToGrid(0, 0, "    Distance:", distance, Misc.getHighlightColor())
            info.addToGrid(0, 1, "    Fuel required:", Misc.getWithDGS(cost.toFloat()), required)
            info.addToGrid(0, 2, "    Fuel available:", Misc.getWithDGS(available.toFloat()), Misc.getHighlightColor())
            info.addGrid(0f)
            if (row.kind == Kind.POINT) {
                info.addPara("    The exit point is plotted on the system map next.", Misc.getGrayColor(), 3f)
            }
        }

        override fun canConfirmSelection(entity: SectorEntityToken): Boolean {
            val row = rowOf(entity) ?: return false
            val fleet = Global.getSector().playerFleet
            return fuelCost(fleet, row) <= fleet.cargo.fuel
        }

        override fun getFuelColorAlphaMult(): Float = 0.5f

        override fun getFuelRangeMult(): Float = SlipspaceJumpRules.fuelRangeMult()
    }

    /** Hands the pick to the ability: the dialog closes, the game unpauses, the charge starts. */
    private fun resolve(system: StarSystemAPI, point: Vector2f?) {
        if (resolved) return
        resolved = true
        dialog.dismiss()
        Global.getSector().isPaused = false
        ability.onDestinationPicked(system, point)
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

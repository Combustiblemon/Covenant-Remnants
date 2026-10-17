package org.combustiblelemon.covenantremnants.campaign.abilities

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin
import com.fs.starfarer.api.campaign.CustomVisualDialogDelegate
import com.fs.starfarer.api.campaign.CustomVisualDialogDelegate.DialogCallbacks
import com.fs.starfarer.api.campaign.StarSystemAPI
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.ui.Alignment
import com.fs.starfarer.api.ui.ButtonAPI
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.CutStyle
import com.fs.starfarer.api.ui.Fonts
import com.fs.starfarer.api.ui.PositionAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.Misc
import org.lwjgl.input.Keyboard
import org.lwjgl.opengl.GL11
import org.lwjgl.util.vector.Vector2f

/**
 * The system map of the Slipspace Jump navigation upgrade (developer rulings 2026-10-12): a custom
 * visual dialog over [SlipspaceDestinationDialog] where the player plots the exit point in
 * [system] ([origin] itself = the in-system jump) on Ashlib's map. Confirm hands the point back;
 * Back or Escape returns to the sector map. The host opens it one frame after the sector map
 * closed: the game ignores a dialog opened from inside the picker's own callback.
 */
class SlipspacePointDialog(
    private val host: SlipspaceDestinationDialog,
    private val system: StarSystemAPI,
    origin: StarSystemAPI,
) : CustomVisualDialogDelegate {

    companion object {
        /** Share of the screen the dialog covers, on each side (developer 2026-10-17: about 90 %). */
        const val SCREEN_FRACTION = 0.9f

        /** Dialog width in UI (virtual) pixels, so it covers the same share at every UI scale. */
        fun width(): Float = Global.getSettings().screenWidth * SCREEN_FRACTION

        /** Dialog height in UI (virtual) pixels. */
        fun height(): Float = Global.getSettings().screenHeight * SCREEN_FRACTION
    }

    /** Set once the game showed the dialog and its contents were built ([init] runs inside `showCustomVisualDialog`). */
    var opened = false
        private set

    // Built eagerly: the game asks for the panel plugin before it calls init.
    private val content = SlipspacePointPanel(system, origin)

    override fun init(panel: CustomPanelAPI, callbacks: DialogCallbacks) {
        content.init(panel, callbacks)
        // Only after a complete build: a throw above leaves it false, so the host retries / reports.
        opened = true
    }

    override fun getCustomPanelPlugin(): CustomUIPanelPlugin = content

    override fun getNoiseAlpha(): Float = 0.5f

    override fun advance(amount: Float) {}

    override fun reportDismissed(option: Int) {
        host.onPointDialogClosed(system, content.confirmedPoint)
        content.dispose()
    }
}

/** The contents of [SlipspacePointDialog]: title, map, info lines, Confirm and Back. */
class SlipspacePointPanel(private val system: StarSystemAPI, private val origin: StarSystemAPI) : CustomUIPanelPlugin {

    private companion object {
        const val PAD = 15f
        const val MAP_TOP = 45f
        const val INFO_HEIGHT = 80f
        const val BUTTON_WIDTH = 160f
        const val BUTTON_HEIGHT = 25f
        const val GAP = 10f
    }

    /** The exit point accepted with Confirm (system world space); null after Back. */
    var confirmedPoint: Vector2f? = null
        private set

    private val inSystem = system === origin
    private var callbacks: DialogCallbacks? = null
    private var map: SlipspacePointMap? = null
    private var mapError = false
    private var header: TooltipMakerAPI? = null
    private var infoHolder: CustomPanelAPI? = null
    private var info: CustomPanelAPI? = null
    private var confirm: ButtonAPI? = null
    private var back: ButtonAPI? = null
    private var closing = false

    fun init(panel: CustomPanelAPI, callbacks: DialogCallbacks) {
        this.callbacks = callbacks
        val width = panel.position.width
        val height = panel.position.height
        val contentWidth = width - 2 * PAD

        // Title, centred
        val title = panel.createUIElement(contentWidth, 20f, false)
        title.setParaFont(Fonts.ORBITRON_20AABOLD)
        val label = title.addPara("Plot slipspace exit - ${system.nameWithLowercaseType}", Misc.getTooltipTitleAndLightHighlightColor(), 5f)
        panel.addUIElement(title).inTL(PAD, 10f)
        label.position.setLocation(0f, 0f).inTL(contentWidth / 2f - label.computeTextWidth(label.text) / 2f, 3f)
        header = title

        // Buttons first, so Back works even if the map fails to build.
        val buttonsWidth = BUTTON_WIDTH * 2 + GAP
        val buttons = panel.createUIElement(buttonsWidth, BUTTON_HEIGHT, false)
        buttons.setButtonFontOrbitron20()
        val confirmButton = buttons.addButton("Confirm", "confirm", Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), Alignment.MID, CutStyle.TL_BR, BUTTON_WIDTH, BUTTON_HEIGHT, 0f)
        val backButton = buttons.addButton("Back", "back", Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), Alignment.MID, CutStyle.TL_BR, BUTTON_WIDTH, BUTTON_HEIGHT, 0f)
        backButton.setShortcut(Keyboard.KEY_ESCAPE, true)
        confirmButton.position.inTL(0f, 0f)
        backButton.position.inTL(BUTTON_WIDTH + GAP, 0f)
        confirmButton.isEnabled = false
        panel.addUIElement(buttons).inTL(width - PAD - buttonsWidth, height - PAD - BUTTON_HEIGHT)
        confirm = confirmButton
        back = backButton

        // Info lines, left of the buttons
        val infoWidth = contentWidth - buttonsWidth - 2 * GAP
        val holder = Global.getSettings().createCustom(infoWidth, INFO_HEIGHT, null)
        panel.addComponent(holder).inTL(PAD, height - PAD - INFO_HEIGHT)
        infoHolder = holder

        // The map
        val mapHeight = height - MAP_TOP - GAP - INFO_HEIGHT - PAD
        val fleetPos = if (inSystem) Vector2f(Global.getSector().playerFleet.location) else null
        try {
            val built = SlipspacePointMap(contentWidth, mapHeight, system, fleetPos)
            panel.addComponent(built.getMainPanel()).inTL(PAD, MAP_TOP)
            map = built
        } catch (e: Exception) {
            mapFailed(e)
        } catch (e: LinkageError) {
            // A missing class inside Ashlib (e.g. an optional mod it expects): report, keep Back working.
            mapFailed(e)
        }
        rebuildInfo()
    }

    private fun mapFailed(e: Throwable) {
        mapError = true
        Global.getLogger(SlipspacePointPanel::class.java).error("Slipspace system map could not be built for ${system.id}", e)
    }

    private fun fuelCost(): Int {
        val fleet = Global.getSector().playerFleet
        return if (inSystem) SlipspaceJumpRules.blinkFuelCost(fleet) else SlipspaceJumpRules.fuelCost(fleet, origin, system)
    }

    private fun fuelOk(): Boolean = fuelCost() <= Global.getSector().playerFleet.cargo.fuel

    /** The info lines, rebuilt whole (Ashlib template's pattern): reason, point, fuel. */
    private fun rebuildInfo() {
        val holder = infoHolder ?: return
        info?.let { holder.removeComponent(it) }
        val w = holder.position.width
        val h = holder.position.height
        val panel = Global.getSettings().createCustom(w, h, null)
        val text = panel.createUIElement(w, h, false)
        val highlight = Misc.getHighlightColor()
        val bad = Misc.getNegativeHighlightColor()

        if (mapError) {
            text.addPara("The system map could not be opened.", bad, 0f)
        } else {
            val map = map
            val point = map?.chosenPoint
            map?.rejectReason?.let { text.addPara(it, bad, 0f) }
            if (point == null) {
                text.addPara(
                    "Click the map to plot the exit point. The red zones around stars and the space " +
                        "beyond the outer ring are out of reach" + (if (inSystem) ", as is the fleet's own vicinity." else "."),
                    Misc.getGrayColor(), 3f
                )
            } else {
                val from = if (inSystem) Global.getSector().playerFleet.location else SlipspaceJumpRules.systemCenter(system)
                text.addPara(
                    "Exit point: %s, %s - %s su from the " + (if (inSystem) "fleet" else "system centre"),
                    3f, highlight,
                    point.x.toInt().toString(), point.y.toInt().toString(),
                    Misc.getWithDGS(Misc.getDistance(from, point))
                )
            }
        }
        val cost = fuelCost()
        val available = Global.getSector().playerFleet.cargo.fuel.toInt()
        text.addPara(
            "Fuel required: %s - available: %s", 3f,
            arrayOf(if (cost > available) bad else highlight, highlight),
            Misc.getWithDGS(cost.toFloat()), Misc.getWithDGS(available.toFloat())
        )
        panel.addUIElement(text).inTL(0f, 0f)
        holder.addComponent(panel).inTL(0f, 0f)
        info = panel
    }

    override fun advance(amount: Float) {
        if (closing) return
        val map = map
        if (map != null && map.needsToUpdateUI) {
            map.needsToUpdateUI = false
            rebuildInfo()
        }
        val confirm = confirm ?: return
        val back = back ?: return
        val point = map?.chosenPoint
        val canConfirm = point != null && fuelOk()
        if (confirm.isEnabled != canConfirm) confirm.isEnabled = canConfirm
        if (confirm.isChecked) {
            confirm.isChecked = false
            if (point != null && canConfirm) {
                confirmedPoint = Vector2f(point)
                close()
            }
        } else if (back.isChecked) {
            back.isChecked = false
            close()
        }
    }

    private fun close() {
        closing = true
        callbacks?.dismissDialog()
    }

    /** Releases the map's UI (Ashlib's clean-up); a failure there must not break the jump. */
    fun dispose() {
        try {
            map?.clearUI()
        } catch (e: Exception) {
            Global.getLogger(SlipspacePointPanel::class.java).warn("Slipspace system map clean-up failed", e)
        }
        map = null
    }

    override fun renderBelow(alphaMult: Float) {
        // Title bar backdrop, as Ashlib's template draws it.
        val position: PositionAPI = header?.position ?: return
        val c = Global.getSector().playerFaction.darkUIColor.darker()
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT or GL11.GL_COLOR_BUFFER_BIT or GL11.GL_CURRENT_BIT)
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D)
            GL11.glEnable(GL11.GL_BLEND)
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
            GL11.glColor4f(c.red / 255f, c.green / 255f, c.blue / 255f, c.alpha / 255f * alphaMult)
            GL11.glRectf(position.x, position.y, position.x + position.width, position.y + position.height)
        } finally {
            GL11.glPopAttrib()
        }
    }

    override fun positionChanged(position: PositionAPI?) {}

    override fun render(alphaMult: Float) {}

    override fun processInput(events: MutableList<InputEventAPI>?) {}

    override fun buttonPressed(buttonId: Any?) {}
}

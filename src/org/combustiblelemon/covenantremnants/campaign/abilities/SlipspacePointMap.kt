package org.combustiblelemon.covenantremnants.campaign.abilities

import ashlib.data.plugins.ui.models.resizable.ResizableComponent
import ashlib.data.plugins.ui.models.resizable.map.MapOrbitRenderer
import ashlib.data.plugins.ui.template.RestrictedArea
import ashlib.data.plugins.ui.template.SlipSpaceJumpCoordinateUI
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.StarSystemAPI
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.util.Misc
import org.lwjgl.opengl.GL11
import org.lwjgl.util.vector.Vector2f
import java.awt.Color
import kotlin.math.cos
import kotlin.math.sin

/**
 * Ashlib's slipspace coordinate map (`SlipSpaceJumpCoordinateUI`) with the Covenant rules of the
 * navigation upgrade (developer rulings 2026-10-12):
 *  - Ashlib's red no-go zones (5 × star radius) are replaced by ours, [SlipspaceJumpRules.starZones];
 *  - a click is checked by [SlipspaceJumpRules.pointRejectReason] (star zones, the system's outer
 *    radius, and in-system the minimum distance from the fleet at [fleetPos]);
 *  - overlays: the outer limit as a ring, the plotted point as a reticle, the fleet as a marker
 *    (in-system only).
 *
 * Coordinates: Ashlib's map space is the system's world space shifted and Y-flipped
 * (`translateCoordinatesToUI` / `translateCoordinatesFromUIToWorld`); [chosenPoint] is in world
 * space. Ashlib builds the whole map inside its constructor and calls [afterFinalize] from there,
 * before this class's properties exist, so that override must not touch any of them.
 */
class SlipspacePointMap(
    width: Float,
    height: Float,
    private val targetSystem: StarSystemAPI,
    private val fleetPos: Vector2f?,
) : SlipSpaceJumpCoordinateUI(width, height, targetSystem) {

    private companion object {
        val BOUND_COLOR = Color(255, 180, 60, 150)
        const val BOUND_LINE_WIDTH = 2f
    }

    /** The plotted exit point (system world space), or null until a valid click. */
    var chosenPoint: Vector2f? = null
        private set

    /** Why the last click was refused (null after a valid click). */
    var rejectReason: String? = null
        private set

    private val reticle = MapGlyph(MapGlyph.Style.RETICLE, Misc.getPositiveHighlightColor())

    init {
        val zoom = getMapZoom()
        // Our no-go zones, in Ashlib's look (its own were removed in afterFinalize).
        for ((body, radius) in SlipspaceJumpRules.starZones(targetSystem)) {
            val ui = translateCoordinatesToUI(body.location)
            val zone = RestrictedArea(radius)
            zoom.addComponent(zone, ui.x - radius, ui.y - radius)
            zone.scale = zoom.currScale
        }
        // The outer limit: a ring around the system centre (Ashlib's orbit line, our colour).
        val center = translateCoordinatesToUI(SlipspaceJumpRules.systemCenter(targetSystem))
        val ring = MapOrbitRenderer(SlipspaceJumpRules.pointBoundsRadius(targetSystem))
        ring.color = BOUND_COLOR
        ring.lineWidth = BOUND_LINE_WIDTH
        zoom.addComponent(ring, center.x, center.y)
        ring.scale = zoom.currScale
        // The fleet, for the in-system jump.
        if (fleetPos != null) {
            val fleet = Global.getSector().playerFleet
            val marker = MapGlyph(MapGlyph.Style.FLEET, fleet.faction.baseUIColor, fleet.facing)
            val ui = translateCoordinatesToUI(fleetPos)
            zoom.addComponent(marker, ui.x, ui.y)
            marker.scale = zoom.currScale
        }
        // The plotted point, hidden until the first valid click.
        reticle.visible = false
        zoom.addComponent(reticle, center.x, center.y)
        reticle.scale = zoom.currScale
    }

    override fun afterFinalize() {
        // Ashlib's hook builds its 5 × radius zones. Keep the call (clearUI() clears the list it
        // creates), then drop the drawn zones: ours replace them in init.
        super.afterFinalize()
        getMapZoom().removeComponentOfClass(RestrictedArea::class.java)
    }

    /**
     * Replaces Ashlib's click handling entirely (no super call: it would apply its own 5 × radius
     * zones). A refused click keeps the previous point and reports why.
     */
    override fun onClick(inUIWorldCoordinates: Vector2f, event: InputEventAPI) {
        if (!event.isLMBUpEvent) return
        val world = translateCoordinatesFromUIToWorld(inUIWorldCoordinates)
        val reason = SlipspaceJumpRules.pointRejectReason(targetSystem, world, fleetPos)
        if (reason != null) {
            Global.getSoundPlayer().playUISound("ui_button_disabled_pressed", 1f, 1f)
            rejectReason = reason
        } else {
            Global.getSoundPlayer().playUISound("ui_button_pressed", 1f, 1f)
            rejectReason = null
            chosenPoint = world
            reticle.moveTo(inUIWorldCoordinates)
            reticle.visible = true
        }
        needsToUpdateUI = true
    }
}

/**
 * A fixed-size glyph on Ashlib's map at a map-space point: the plotted exit (a reticle) or the
 * fleet (a triangle along [facing]). The panel is 1 × 1 at the point, so the glyph does not scale
 * with the zoom.
 */
class MapGlyph(private val style: Style, private val color: Color, private val facing: Float = 0f) : ResizableComponent() {

    enum class Style { RETICLE, FLEET }

    var visible = true

    init {
        componentPanel = Global.getSettings().createCustom(1f, 1f, this)
    }

    /** Moves the glyph to [ui] (map space, as `translateCoordinatesToUI` gives). */
    fun moveTo(ui: Vector2f) {
        originalCoords = Vector2f(ui)
        resize(scale)
    }

    override fun render(alphaMult: Float) {
        if (!visible) return
        val position = componentPanel.position
        val x = position.centerX
        val y = position.centerY
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT or GL11.GL_COLOR_BUFFER_BIT or GL11.GL_LINE_BIT or GL11.GL_CURRENT_BIT)
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D)
            GL11.glEnable(GL11.GL_BLEND)
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
            GL11.glColor4f(color.red / 255f, color.green / 255f, color.blue / 255f, color.alpha / 255f * alphaMult)
            when (style) {
                Style.RETICLE -> drawReticle(x, y)
                Style.FLEET -> drawFleet(x, y)
            }
        } finally {
            GL11.glPopAttrib()
        }
    }

    private fun drawReticle(x: Float, y: Float) {
        val r = 9f
        GL11.glLineWidth(2f)
        GL11.glBegin(GL11.GL_LINE_LOOP)
        for (i in 0 until 24) {
            val t = (i * Math.PI * 2.0 / 24).toFloat()
            GL11.glVertex2f(x + cos(t) * r, y + sin(t) * r)
        }
        GL11.glEnd()
        GL11.glBegin(GL11.GL_LINES)
        for ((dx, dy) in listOf(1f to 0f, -1f to 0f, 0f to 1f, 0f to -1f)) {
            GL11.glVertex2f(x + dx * r * 1.4f, y + dy * r * 1.4f)
            GL11.glVertex2f(x + dx * r * 2.2f, y + dy * r * 2.2f)
        }
        GL11.glEnd()
    }

    private fun drawFleet(x: Float, y: Float) {
        // Screen Y points up like world Y, so the world facing applies as is.
        val size = 9f
        val angle = Math.toRadians(facing.toDouble())
        val ux = cos(angle).toFloat()
        val uy = sin(angle).toFloat()
        GL11.glBegin(GL11.GL_TRIANGLES)
        GL11.glVertex2f(x + ux * size, y + uy * size)
        GL11.glVertex2f(x - ux * size * 0.6f - uy * size * 0.6f, y - uy * size * 0.6f + ux * size * 0.6f)
        GL11.glVertex2f(x - ux * size * 0.6f + uy * size * 0.6f, y - uy * size * 0.6f - ux * size * 0.6f)
        GL11.glEnd()
    }
}

package org.combustiblelemon.covenantremnants.plugins

import com.fs.starfarer.api.combat.CombatEngineLayers
import com.fs.starfarer.api.combat.CombatEntityAPI
import com.fs.starfarer.api.combat.CombatLayeredRenderingPlugin
import com.fs.starfarer.api.combat.ViewportAPI
import com.fs.starfarer.api.util.Misc
import org.lazywizard.lazylib.opengl.DrawUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.util.vector.Vector2f
import java.awt.Color
import java.util.EnumSet
import kotlin.math.sqrt

/**
 * Draws the Energy Projector's pierce beam — the segment of lance that continues past the exit point
 * of a target it has punched through. [EnergyProjectorBeamEffect] calls [refresh] every frame it is
 * piercing; when it stops (beam ends / target re-shields / armor recovers), the strip is no longer
 * refreshed, fades out over [fadeTime], and the plugin self-expires.
 *
 * Additive glow: a wide faint fringe quad + a narrow bright core quad, tapering toward the far end,
 * plus a soft hit-glow at the exit point. Untextured vertex-colour gradient (swap a glow texture in
 * later). Modeled on [SlipspaceDriveRenderPlugin]'s raw-GL approach.
 */
class EnergyProjectorPierceRenderPlugin : CombatLayeredRenderingPlugin {

    private val fadeTime = 0.22f

    private var start: Vector2f? = null
    private var end: Vector2f? = null
    private var width = 25f
    private var core: Color = Color.white
    private var fringe: Color = Color(139, 130, 251)
    private var life = 1f

    /** Called by the beam effect each frame it is piercing. */
    fun refresh(start: Vector2f, end: Vector2f, width: Float, core: Color, fringe: Color) {
        this.start = Vector2f(start)
        this.end = Vector2f(end)
        this.width = width
        this.core = core
        this.fringe = fringe
        this.life = 1f
    }

    override fun advance(amount: Float) {
        if (life > 0f) life -= amount / fadeTime
    }

    override fun isExpired(): Boolean = life <= 0f

    override fun getActiveLayers(): EnumSet<CombatEngineLayers> =
        EnumSet.of(CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER)

    override fun getRenderRadius(): Float = Float.MAX_VALUE

    override fun init(entity: CombatEntityAPI?) {}

    override fun cleanup() {}

    override fun render(layer: CombatEngineLayers, viewport: ViewportAPI) {
        val s = start ?: return
        val e = end ?: return
        if (life <= 0f) return

        var dx = e.x - s.x
        var dy = e.y - s.y
        val len = sqrt(dx * dx + dy * dy)
        if (len < 1f) return
        dx /= len; dy /= len
        val px = -dy
        val py = dx
        val l = life.coerceIn(0f, 1f)

        GL11.glDisable(GL11.GL_TEXTURE_2D)
        GL11.glEnable(GL11.GL_BLEND)
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE) // additive

        // wide faint fringe, then narrow bright core; both taper alpha toward the far end
        drawStrip(s, e, px, py, width * 0.5f, fringe, 0.45f * l, 0f)
        drawStrip(s, e, px, py, width * 0.18f, core, 1.0f * l, 0.1f * l)

        // hit glow at the exit point
        GL11.glColor4f(fringe.red / 255f, fringe.green / 255f, fringe.blue / 255f, 0.55f * l)
        DrawUtils.drawCircle(s.x, s.y, width * 0.9f, 18, true)

        GL11.glEnable(GL11.GL_TEXTURE_2D)
        Misc.setColor(Color.white)
    }

    private fun drawStrip(
        s: Vector2f, e: Vector2f, px: Float, py: Float,
        halfW: Float, c: Color, aStart: Float, aEnd: Float
    ) {
        GL11.glBegin(GL11.GL_QUADS)
        GL11.glColor4f(c.red / 255f, c.green / 255f, c.blue / 255f, aStart)
        GL11.glVertex2f(s.x + px * halfW, s.y + py * halfW)
        GL11.glVertex2f(s.x - px * halfW, s.y - py * halfW)
        GL11.glColor4f(c.red / 255f, c.green / 255f, c.blue / 255f, aEnd)
        GL11.glVertex2f(e.x - px * halfW, e.y - py * halfW)
        GL11.glVertex2f(e.x + px * halfW, e.y + py * halfW)
        GL11.glEnd()
    }
}

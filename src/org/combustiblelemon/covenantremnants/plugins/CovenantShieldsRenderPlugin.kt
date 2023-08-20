package org.combustiblelemon.covenantremnants.plugins

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.impl.campaign.terrain.RingRenderer
import com.fs.starfarer.api.util.Misc
import org.combustiblelemon.covenantremnants.utils.CubicCurve2D
import org.combustiblelemon.covenantremnants.utils.Utils
import org.lazywizard.lazylib.MathUtils
import org.lazywizard.lazylib.opengl.DrawUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.util.vector.Vector2f
import java.awt.Color
import java.util.*
import kotlin.collections.HashSet

class CovenantShieldsRenderPlugin(private val attachedTo: ShipAPI) : CombatLayeredRenderingPlugin {
    companion object{
        private var layer = CombatEngineLayers.JUST_BELOW_WIDGETS
    }

    private lateinit var entity: CombatEntityAPI;


    override fun render(layer: CombatEngineLayers, viewport: ViewportAPI) {
        if (!attachedTo.phaseCloak.isActive) {
            return;
        }

        val shipX = attachedTo.location!!.x
        val shipY = attachedTo.location!!.y

        GL11.glDisable(GL11.GL_TEXTURE_2D)
        Misc.setColor(Color(255, 255, 255, 40))
        GL11.glBegin(GL11.GL_POLYGON)
        GL11.glVertex2f(shipX, shipY)
        attachedTo.exactBounds!!.origSegments.forEachIndexed { index, segment ->
//            Utils.logText(index.toString() + "  " + segment.p1.toString())

                val newP1 = Utils.rotatePointByDegrees(
                    segment!!.p1.x + shipX,
                    segment.p1.y + shipY,
                    attachedTo.location,
                    attachedTo.facing
                )
                GL11.glVertex2f(newP1.x, newP1.y)


                if (index == attachedTo.exactBounds.origSegments.count() - 1) {
                    val seg = attachedTo.exactBounds!!.origSegments[0]
                    val newP2 = Utils.rotatePointByDegrees(
                        seg.p1.x + shipX,
                        seg.p1.y + shipY,
                        attachedTo.location,
                        attachedTo.facing
                    )
                    GL11.glVertex2f(newP2.x, newP2.y)
                }

        }
        GL11.glEnd()
        GL11.glEnable(GL11.GL_TEXTURE_2D)
    }
    override fun advance(amount: Float) {}
    override fun cleanup() {}
    override fun getActiveLayers(): EnumSet<CombatEngineLayers> {
        return EnumSet.of(layer)
    }

    override fun getRenderRadius(): Float {
        return Float.MAX_VALUE
    }

    override fun init(entity: CombatEntityAPI) {
        this.entity = entity
    }

    override fun isExpired(): Boolean {
        return false
    }
}

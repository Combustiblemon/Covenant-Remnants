package org.combustiblelemon.covenantremnants.plugins

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.util.Misc
import org.combustiblelemon.covenantremnants.utils.Utils
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL14
import java.awt.Color
import java.util.*

class CovenantShieldsRenderPlugin(private val attachedTo: ShipAPI) : CombatLayeredRenderingPlugin {
    companion object {
        private val noiseSprite = Global.getSettings().getSprite("covenant_remnants", "shield_noise")
    }

    private var layer = CombatEngineLayers.ABOVE_SHIPS_LAYER

    private lateinit var entity: CombatEntityAPI

    override fun render(layer: CombatEngineLayers, viewport: ViewportAPI) {
        if (!attachedTo.phaseCloak.isActive) {
            return
        }

        renderShield()

    }

    private fun renderShield() {
        // TODO: FIX THE CONCAVE SHAPE RENDER ISSUE
        // TODO: IMPROVE VISUALS
        val shipX = attachedTo.location!!.x
        val shipY = attachedTo.location!!.y

        glDisable(GL_TEXTURE_2D)
        glClearStencil(0)
        glStencilMask(0xff)
        //set everything to 0
        glClear(GL_STENCIL_BUFFER_BIT)

        //disable drawing colour, enable stencil testing
        glColorMask(false, false, false, false) //disable colour
        glEnable(GL_STENCIL_TEST) //enable stencil

        // ... here you render the part of the scene you want masked, this may be a simple triangle or square, or for example a monitor on a computer in your spaceship ...
        //begin masking
        //put 1s where I want to draw
        glStencilFunc(
            GL_ALWAYS,
            1,
            0xff
        ) // Do not test the current value in the stencil buffer, always accept any value on there for drawing
        glStencilMask(0xff)
        glStencilOp(GL_REPLACE, GL_REPLACE, GL_REPLACE) // Make every test succeed

        // <draw a quad that dictates you want the boundaries of the panel to be>
        glBegin(GL_TRIANGLE_FAN)
        glVertex2f(shipX, shipY)
        attachedTo.exactBounds!!.origSegments.forEachIndexed { index, segment ->
            val newP1 = Utils.rotatePointByDegrees(
                segment!!.p1.x + shipX,
                segment.p1.y + shipY,
                attachedTo.location,
                attachedTo.facing
            )
            glVertex2f(newP1.x, newP1.y)

        }
        val seg = attachedTo.exactBounds!!.origSegments[0]
        val newP2 = Utils.rotatePointByDegrees(
            seg.p1.x + shipX,
            seg.p1.y + shipY,
            attachedTo.location,
            attachedTo.facing
        )
        glVertex2f(newP2.x, newP2.y)
        val newP3 = Utils.rotatePointByDegrees(
            shipX + attachedTo.shieldRadiusEvenIfNoShield / 2,
            shipY,
            attachedTo.location,
            attachedTo.facing
        )
        glVertex2f(newP3.x, newP3.y)
        glEnd()

        glStencilOp(
            GL_KEEP,
            GL_KEEP,
            GL_KEEP
        ) // Make sure you will no longer (over)write stencil values, even if any test succeeds
        glColorMask(true, true, true, true) // Make sure we draw on the backbuffer again.
        glStencilFunc(
            GL_EQUAL,
            1,
            0xFF
        ) // Now we will only draw pixels where the corresponding stencil buffer value equals 1

        //<draw the lines>
        glEnable(GL_TEXTURE_2D)

        noiseSprite.bindTexture()
//        noiseSprite.re
        glBegin(GL_POLYGON)
        Misc.setColor(Color(190, 140, 255, 120))
        glEnable(GL_BLEND)
        GL14.glBlendEquation(GL14.GL_FUNC_ADD)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        // Bottom Left
        val pBL = Utils.rotatePointByDegrees(
            shipX - attachedTo.collisionRadius,
            shipY - attachedTo.collisionRadius,
            attachedTo.location,
            attachedTo.facing
        )
        glTexCoord2f(0f, 0f)
        glVertex2f(pBL.x, pBL.y)

        // Bottom Right
        val pBR = Utils.rotatePointByDegrees(
            shipX + attachedTo.collisionRadius,
            shipY - attachedTo.collisionRadius,
            attachedTo.location,
            attachedTo.facing
        )
        glTexCoord2f(0.5f, 0f)
        glVertex2f(pBR.x, pBR.y)

        // Top Right
        val pTR = Utils.rotatePointByDegrees(
            shipX + attachedTo.collisionRadius,
            shipY + attachedTo.collisionRadius,
            attachedTo.location,
            attachedTo.facing
        )
        glTexCoord2f(0.5f, 0.5f)
        glVertex2f(pTR.x, pTR.y)

        // Top Left
        val pTL = Utils.rotatePointByDegrees(
            shipX - attachedTo.collisionRadius,
            shipY + attachedTo.collisionRadius,
            attachedTo.location,
            attachedTo.facing
        )
        glTexCoord2f(0f, 0.5f)
        glVertex2f(pTL.x, pTL.y)


        glEnd()
        glDisable(GL_BLEND)
        glDisable(GL_STENCIL_TEST)
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

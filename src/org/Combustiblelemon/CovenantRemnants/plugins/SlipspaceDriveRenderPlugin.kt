package org.Combustiblelemon.CovenantRemnants.plugins

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.impl.campaign.terrain.RingRenderer
import com.fs.starfarer.api.util.Misc
import org.Combustiblelemon.CovenantRemnants.utils.CubicCurve2D
import org.Combustiblelemon.CovenantRemnants.utils.Utils
import org.lazywizard.lazylib.MathUtils
import org.lazywizard.lazylib.opengl.DrawUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.util.vector.Vector2f
import java.awt.Color
import java.util.*

class SlipspaceDriveRenderPlugin(private val attachedTo: ShipAPI?, private val portalDuration: Float, var portalDistance: Float, var portalRadius: Float) : CombatLayeredRenderingPlugin {
    protected var layer = CombatEngineLayers.JUST_BELOW_WIDGETS
    protected var entity: CombatEntityAPI? = null
    private var portalLocation: Vector2f? = null
    private var startTime = 0f
    private val startUpTime = 3f
    private var isPortalActive = false
    private val cubicFunction = CubicCurve2D(.01, .75, 0.0, .99) //.02,.65,1,-0.91
    private val outerRingRenderer = RingRenderer("covenant_remnants", "portal_outer_loop")
    private val innerRingRenderer = RingRenderer("covenant_remnants", "portal_inner_loop")
    private val noiseSprite = Global.getSettings().getSprite("covenant_remnants", "portal_noise_long")
    val texturePoints = MathUtils.getPointsAlongCircumference(Vector2f(0.5f, 0.5f), 0.5f, 40, 0f).toTypedArray<Vector2f>()
    private var effectLevelExtra = 0f
    private var counter = 0
    private var reverse = false
    private var reverseLevel = 0f
    override fun render(layer: CombatEngineLayers, viewport: ViewportAPI) {
        if (!attachedTo!!.system.isActive) {
            return
        }
        val currentTime = Global.getCombatEngine().getTotalElapsedTime(false)

        // RENDER PORTAL
        if (!isPortalActive) {
            startTime = currentTime
            portalLocation = Utils.getLocationInFrontOfShip(attachedTo, portalDistance)
            isPortalActive = true
        } else if (startTime + portalDuration < currentTime) {
            isPortalActive = false
            startTime = 0f
        }
        if (isPortalActive) {
            val effectLevel = attachedTo.system.effectLevel

            // portal startup
            if (startTime + startUpTime > currentTime) {
                val radius = (60f
                        * (effectLevel - reverseLevel))
                GL11.glDisable(GL11.GL_TEXTURE_2D)
                Misc.setColor(Color.white)
                DrawUtils.drawCircle(portalLocation!!.x, portalLocation!!.y, radius, 20, true)
                GL11.glEnable(GL11.GL_TEXTURE_2D)
                if (counter > 9) {
                    counter = 0
                    Global.getCombatEngine().spawnEmpArcVisual(portalLocation, null,
                            MathUtils.getRandomPointInCircle(portalLocation, radius * 1.1f),
                            null, 5f, Color(139, 130, 251), Color.white)
                } else {
                    counter++
                }
                if (!reverse && startTime + startUpTime / 2 > currentTime) {
                    reverse = true
                    reverseLevel = effectLevel
                }
            } else {
                if (effectLevelExtra == 0f) {
                    effectLevelExtra = effectLevel
                }
                renderPortal(Math.min(Math.max(effectLevel - effectLevelExtra, 0f) * 3, 1f))
            }
        }
    }

    private fun renderPortal(radiusMultiplier: Float) {
        val radius = portalRadius * cubicFunction.getTime(Math.min(radiusMultiplier, 1f))
        val innerRadius = radius * 0.95f
        val noiseRadius = Math.max(innerRadius - 10, 0f)

        // Render the outer white ring
        outerRingRenderer.render(portalLocation, radius, radius * 0.9f, Color.white, false, 1f, 1f)
        // Render the inner purple-black ring
        innerRingRenderer.render(portalLocation, innerRadius, radius * 0.88f, Color.white, false, 1f, 1f)

        // Fill the inner ring with color
        GL11.glDisable(GL11.GL_TEXTURE_2D)
        Misc.setColor(Color(20, 23, 34))
        DrawUtils.drawCircle(portalLocation!!.x, portalLocation!!.y, radius * 0.9f, 50, true)
        GL11.glEnable(GL11.GL_TEXTURE_2D)

        // Get points so we can map the noise texture
        val points = MathUtils.getPointsAlongCircumference(portalLocation, noiseRadius, 40, 0f).toTypedArray<Vector2f>()
        noiseSprite.bindTexture()
        Misc.setColor(Color(139, 130, 251))
        GL11.glBegin(GL11.GL_POLYGON)
        for (i in points.indices) {
            // use stencil mask?
            GL11.glTexCoord2f(texturePoints[i].x, texturePoints[i].y)
            GL11.glVertex2f(points[i].x, points[i].y)

            // Walk the texture
            if (!Global.getCombatEngine().isPaused) {
                texturePoints[i] = Vector2f((texturePoints[i].x + 0.004).toFloat(), texturePoints[i].y)
            }
        }
        GL11.glEnd()
    }

    // if performance is an issue look at the code below
    // stencil
    //reset the whole stencil mask to 0s
    //        glClearStencil(0);
    //        glStencilMask(0xff);
    //        //set everything to 0
    //        glClear(GL_STENCIL_BUFFER_BIT);
    //
    //        //disable drawing colour, enable stencil testing
    //        glColorMask(false, false, false, false); //disable colour
    //        glEnable(GL_STENCIL_TEST); //enable stencil
    //
    //        // ... here you render the part of the scene you want masked, this may be a simple triangle or square, or for example a monitor on a computer in your spaceship ...
    //        //begin masking
    //        //put 1s where I want to draw
    //        glStencilFunc(GL_ALWAYS, 1, 0xff); // Do not test the current value in the stencil buffer, always accept any value on there for drawing
    //        glStencilMask(0xff);
    //        glStencilOp(GL_REPLACE, GL_REPLACE, GL_REPLACE); // Make every test succeed
    //
    //        <draw a quad that dictates you want the boundaries of the panel to be>
    //
    //        glStencilOp(GL_KEEP, GL_KEEP, GL_KEEP); // Make sure you will no longer (over)write stencil values, even if any test succeeds
    //        glColorMask(true, true, true, true); // Make sure we draw on the backbuffer again.
    //        glStencilFunc(GL_EQUAL, 1, 0xFF); // Now we will only draw pixels where the corresponding stencil buffer value equals 1
    //
    //        <draw the lines>
    //
    //                glDisable(GL_STENCIL_TEST);
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

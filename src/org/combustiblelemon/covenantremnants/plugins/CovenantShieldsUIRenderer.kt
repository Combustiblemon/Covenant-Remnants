package org.combustiblelemon.covenantremnants.plugins

import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ViewportAPI
import org.lwjgl.opengl.GL11.*

class CovenantShieldsUIRenderer(private val ship: ShipAPI) {
    companion object {
        private const val BAR_WIDTH = 60f
        private const val BAR_HEIGHT = 10f
        private const val PADDING = 5f
    }

    private var shieldHealthPercent: Float = 1f

    fun setHealthPercent(percent: Float) {
        shieldHealthPercent = percent
    }

    fun render(viewport: ViewportAPI) {
        // Save the current OpenGL state
        glPushAttrib(GL_ALL_ATTRIB_BITS)

        // Enable blending for transparency
        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)

        // Disable texture mapping as we're drawing shapes
        glDisable(GL_TEXTURE_2D)

        // Restore the OpenGL state
        glPopAttrib()
    }

    private fun renderCooldownBar(screenX: Float, screenY: Float) {
        // Draw background
        glColor4f(155f / 255f, 1f, 0f, 1f)
        glBegin(GL_QUADS)
        glVertex2f(screenX, screenY + PADDING)
        glVertex2f(screenX + 100f, screenY + PADDING)
        glVertex2f(screenX + 100f, screenY + PADDING + BAR_HEIGHT)
        glVertex2f(screenX, screenY + PADDING + BAR_HEIGHT)
        glEnd()

        // Draw progress
        glColor4f(0.0f, 0.8f, 1.0f, 0.8f)
    }
}

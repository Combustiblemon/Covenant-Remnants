package org.combustiblelemon.covenantremnants.plugins

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.util.Misc
import org.combustiblelemon.covenantremnants.utils.Utils
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL14
import org.lwjgl.opengl.GL15.*
import org.lwjgl.util.vector.Vector2f
import java.awt.Color
import java.util.*
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class CovenantShieldsRenderPlugin(private val attachedTo: ShipAPI) : CombatLayeredRenderingPlugin {
    private var minX: Float = attachedTo.shieldCenterEvenIfNoShield.x - attachedTo.shieldRadiusEvenIfNoShield
    private var maxX: Float = attachedTo.shieldCenterEvenIfNoShield.x + attachedTo.shieldRadiusEvenIfNoShield
    private var minY: Float = 0F
    private var maxY: Float = 0F

    private val numPoints = 60
    private val points: Array<Vector2f> = Array(numPoints) { Vector2f(0F, 0F) }

    companion object {
        private val noiseSprite = Global.getSettings().getSprite("covenant_remnants", "shield_noise")
    }

    private var layer = CombatEngineLayers.ABOVE_SHIPS_LAYER

    private lateinit var entity: CombatEntityAPI

    override fun render(layer: CombatEngineLayers, viewport: ViewportAPI) {
        if (!attachedTo.phaseCloak.isActive) {
            return
        }

        renderShieldEllipse()
    }

    private fun renderShield() {
        // TODO: FIX THE CONCAVE SHAPE RENDER ISSUE
        // TODO: IMPROVE VISUALS
        // TODO: FIX COLOR WHEN THERE ARE A LOT OF STUFF ON SCREEN
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
        // Assuming you have a float array to store your vertex data
        val vertexData = FloatArray((attachedTo.exactBounds!!.origSegments.size + 2) * 2)

// First vertex is the center of the shape
        vertexData[0] = shipX
        vertexData[1] = shipY

// Iterate through the segments and add the rotated points to the vertex data
        attachedTo.exactBounds!!.origSegments.forEachIndexed { index, segment ->
            val newIndex = (index + 1) * 2
            val newP1 = Utils.rotatePointByDegrees(
                segment!!.p1.x + shipX,
                segment.p1.y + shipY,
                attachedTo.location,
                attachedTo.facing
            )
            vertexData[newIndex] = newP1.x
            vertexData[newIndex + 1] = newP1.y
        }

// Add the last two vertices
        val seg = attachedTo.exactBounds!!.origSegments[0]
        val newP2 = Utils.rotatePointByDegrees(
            seg.p1.x + shipX,
            seg.p1.y + shipY,
            attachedTo.location,
            attachedTo.facing
        )
        vertexData[vertexData.size - 4] = newP2.x
        vertexData[vertexData.size - 3] = newP2.y

        val newP3 = Utils.rotatePointByDegrees(
            shipX + attachedTo.shieldRadiusEvenIfNoShield / 2,
            shipY,
            attachedTo.location,
            attachedTo.facing
        )
        vertexData[vertexData.size - 2] = newP3.x
        vertexData[vertexData.size - 1] = newP3.y

// Generate a buffer object
        val vertexBuffer = BufferUtils.createFloatBuffer(vertexData.size)
        vertexBuffer.put(vertexData).flip()

// Generate a buffer object
        val vbo = glGenBuffers()
        glBindBuffer(GL_ARRAY_BUFFER, vbo)

// Upload the vertex data to the buffer
        glBufferData(GL_ARRAY_BUFFER, vertexBuffer, GL_STATIC_DRAW)

// Now, replace glBegin/glEnd block with drawing from the buffer
        glBindBuffer(GL_ARRAY_BUFFER, vbo)
        glEnableClientState(GL_VERTEX_ARRAY)
        glVertexPointer(2, GL_FLOAT, 0, 0)

        glDrawArrays(GL_TRIANGLE_FAN, 0, vertexData.size / 2)

        glDisableClientState(GL_VERTEX_ARRAY)
        glBindBuffer(GL_ARRAY_BUFFER, 0)
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

    private var statuskey = Any()
    private var statuskey2 = Any()

    private fun calcMaxY() {
        var _minY = 100000F
        var _maxY = -100000F

        attachedTo.exactBounds!!.segments.forEach { segment ->
            if (segment.p1.y < _minY) {
                _minY = segment.p1.y
            }

            if (segment.p1.y > _maxY) {
                _maxY = segment.p1.y
            }

            if (segment.p2.y < _minY) {
                _minY = segment.p2.y
            }

            if (segment.p2.y > _maxY) {
                _maxY = segment.p2.y
            }
        }

        maxY = _minY - 30
        minY = _maxY + 30

        val a = abs(maxX - minX) / 2
        val b = abs(maxY - minY) / 2

        for (i in 0 until numPoints) {
            val theta = Math.toRadians(i * (360.0 / numPoints))
            val x = (attachedTo.shieldCenterEvenIfNoShield.x + a * cos(theta)).toFloat()
            val y = (attachedTo.shieldCenterEvenIfNoShield.y + b * sin(theta)).toFloat()
            points[i] = Vector2f(x, y)
        }
    }

    private fun renderShieldEllipse() {
        // TODO: IMPROVE VISUALS
        val shipX = attachedTo.location!!.x
        val shipY = attachedTo.location!!.y

        if (minY == 0F && maxY == 0F) {
            calcMaxY()
        }

        Global.getCombatEngine()
            .maintainStatusForPlayerShip(statuskey, "", "max: ${shipX + maxX}, ${shipY + maxY}", "", false)
        Global.getCombatEngine()
            .maintainStatusForPlayerShip(statuskey2, "", "min: ${shipX + minX}, ${shipY + minY}", "", false)

        val scale = attachedTo.phaseCloak.effectLevel

        // create a new array of points for the scaled ellipse
        val points2 = Array(numPoints) { Vector2f(0F, 0F) }
//        Utils.logText("points: ${points.size}")
//        Utils.logText("points2: ${points2.size}")

        // Scale the ellipse points
        for (i in points.indices) {
            val scaledX =
                (points[i].x - attachedTo.shieldCenterEvenIfNoShield.x) * scale + attachedTo.shieldCenterEvenIfNoShield.x
            val scaledY =
                (points[i].y - attachedTo.shieldCenterEvenIfNoShield.y) * scale + attachedTo.shieldCenterEvenIfNoShield.y
            points2[i].set(scaledX, scaledY)
//            points2[i].set(points[i].x, points[i].y)
            Utils.logText("point: ${points2[i]}")
        }
        glDisable(GL_TEXTURE_2D)

        // Set the color for the dot (red in this case)
        Misc.setColor(Color(255, 0, 0, 255))

        glPointSize(5.0f)
        glBegin(GL_POINTS)
        val rotMax = Utils.rotatePointByDegrees(
            shipX + maxX,
            shipY + maxY,
            attachedTo.location,
            attachedTo.facing
        )
        glVertex2f(rotMax.x, rotMax.y)
        val rotMin = Utils.rotatePointByDegrees(
            shipX + minX,
            shipY + minY,
            attachedTo.location,
            attachedTo.facing
        )
        glVertex2f(rotMin.x, rotMin.y)
        glEnd()

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

        val vertexData = FloatArray((points2.size) * 2)

        points2.forEachIndexed { index, point ->
            val newIndex = (index) * 2
            val newPoint = Utils.rotatePointByDegrees(
                point.x,
                point.y,
                attachedTo.shieldCenterEvenIfNoShield,
                attachedTo.facing
            )
            vertexData[newIndex] = newPoint.x
            vertexData[newIndex + 1] = newPoint.y
        }

// Create a direct buffer from the FloatArray
        val vertexBuffer = BufferUtils.createFloatBuffer(vertexData.size)
        vertexBuffer.put(vertexData).flip()

// Generate a buffer object
        val vbo = glGenBuffers()
        glBindBuffer(GL_ARRAY_BUFFER, vbo)

// Upload the vertex data to the buffer
        glBufferData(GL_ARRAY_BUFFER, vertexBuffer, GL_STATIC_DRAW)

// draw to the buffer
        glBindBuffer(GL_ARRAY_BUFFER, vbo)
        glEnableClientState(GL_VERTEX_ARRAY)
        glVertexPointer(2, GL_FLOAT, 0, 0)

        glDrawArrays(GL_TRIANGLE_FAN, 0, vertexData.size / 2)

        glDisableClientState(GL_VERTEX_ARRAY)
        glBindBuffer(GL_ARRAY_BUFFER, 0)
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

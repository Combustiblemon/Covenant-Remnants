package org.combustiblelemon.covenantremnants.utils

import com.fs.starfarer.api.combat.ShipAPI
import org.lazywizard.lazylib.MathUtils
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.util.vector.Vector2f
import java.awt.geom.AffineTransform
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

object Utils {
    /**
     * Given the ***target*** and ***ship*** will calculate if the target shields are facing the ***ship***
     * @param ship the ship's ship
     * @param target the target ship
     * @return boolean
     */
    fun isShieldFacingShip(ship: ShipAPI, target: ShipAPI): Boolean {
        var targetShieldFacing = target.shield.facing + 180f
        if (targetShieldFacing > 360) {
            targetShieldFacing -= 360f
        }
        val targetShieldArc = target.shield.arc
        val controllerShipFacing = ship.facing
        val targetLeft = abs(targetShieldFacing - targetShieldArc)
        var targetRight = targetShieldFacing + targetShieldArc
        if (targetRight > 360) {
            targetRight -= 360f
        }
        return controllerShipFacing < targetLeft && controllerShipFacing > targetRight
    }

    private const val Pi = 3.14f
    fun getLocationInFrontOfShip(ship: ShipAPI?, units: Float): Vector2f {
        val calculatedPos = Vector2f()
        val shipLocation = ship!!.location
        val facing = ship.facing
        calculatedPos.x = (shipLocation.x - units * cos(2.0 * Pi * (180.0 + facing) / 360.0)).toFloat()
        calculatedPos.y = (shipLocation.y - units * sin(2.0 * Pi * (180.0 + facing) / 360.0)).toFloat()
        return calculatedPos
    }

    fun getVelocityVectorInFacing(ship: ShipAPI?, units: Float): Vector2f {
        val velocity = Vector2f()
        val facing = ship!!.facing
        velocity.x = (-units * cos(2.0 * Pi * (180.0 + facing) / 360.0)).toFloat()
        velocity.y = (-units * sin(2.0 * Pi * (180.0 + facing) / 360.0)).toFloat()
        return velocity
    }

    fun rotatePointByDegrees(x: Float, y: Float, center: Vector2f, degrees: Float): Vector2f {
        val pt = doubleArrayOf(x.toDouble(), y.toDouble())
        AffineTransform.getRotateInstance(Math.toRadians(degrees.toDouble()), center.x.toDouble(), center.y.toDouble())
            .transform(pt, 0, pt, 0, 1)

        return Vector2f(
            pt[0].toFloat(),
            pt[1].toFloat(),
        )
    }

    fun normalize(value: Double, min: Double, max: Double): Float {
        return ((value - min) / (max - min)).toFloat()
    }

    /**
     *
     * @param center A Vector2f that holds the center point of the donut
     * @param radius The outer radius of the donut
     * @param numSegments number of segments
     * @param width the width of the donut (percent of the radius)
     */
    fun drawDonut(center: Vector2f?, radius: Float, numSegments: Int, width: Float) {
        if (numSegments < 3) {
            return
        }
        val hasWidth = width != 0f
        val isFilled = width == 1f
        val innerRadius = radius * (1 - width)
        val extraPoints = 2
        val verticesOuter = FloatArray(numSegments * 2 + extraPoints)
        var verticesInner: FloatArray? = null
        if (hasWidth && !isFilled) {
            verticesInner = FloatArray(verticesOuter.size)
        }
        val pointsOuter = MathUtils.getPointsAlongCircumference(center, radius, numSegments, 0f).toTypedArray<Vector2f>()
        var pointsInner: Array<Vector2f>? = null
        if (hasWidth && !isFilled) {
            pointsInner = MathUtils.getPointsAlongCircumference(center, innerRadius, numSegments, 0f).toTypedArray<Vector2f>()
        }
        for (i in pointsOuter.indices) {
            val vi = i * 2
            verticesOuter[vi] = pointsOuter[i].x
            verticesOuter[vi + 1] = pointsOuter[i].y
            if (hasWidth && !isFilled) {
                verticesInner!![vi] = pointsInner!![i].x
                verticesInner[vi + 1] = pointsInner[i].y
            }
        }
        verticesOuter[verticesOuter.size - 2] = pointsOuter[0].x
        verticesOuter[verticesOuter.size - 1] = pointsOuter[0].y

//        verticesOuter[verticesOuter.length - 2] = pointsOuter[1].x;
//        verticesOuter[verticesOuter.length - 1] = pointsOuter[1].y;
        if (hasWidth && !isFilled) {
            verticesInner!![verticesInner.size - 2] = pointsInner!![0].x
            verticesInner[verticesInner.size - 1] = pointsInner[0].y

//            verticesInner[verticesInner.length - 2] = pointsInner[1].x;
//            verticesInner[verticesInner.length - 1] = pointsInner[1].y;
        }
        var verticesBoth: FloatArray? = null
        if (hasWidth && !isFilled) {
            verticesBoth = FloatArray(verticesInner!!.size + verticesOuter.size)
            var i = 0
            while (i < verticesOuter.size) {
                verticesBoth[i * 2] = verticesOuter[i]
                verticesBoth[i * 2 + 1] = verticesOuter[i + 1]
                verticesBoth[i * 2 + 2] = verticesInner[i]
                verticesBoth[i * 2 + 3] = verticesInner[i + 1]
                i += 2
            }
        }
        val length = if (hasWidth and !isFilled) verticesBoth!!.size else verticesOuter.size
        val verticesMapTest = BufferUtils.createFloatBuffer(length)
        verticesMapTest.put(if (hasWidth and !isFilled) verticesBoth else verticesOuter).flip()
        GL11.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT)
        GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY)
        GL11.glVertexPointer(2, 0, verticesMapTest)
        GL11.glDrawArrays(if (hasWidth and !isFilled) GL11.GL_QUAD_STRIP else GL11.GL_TRIANGLE_FAN, 0, length / 2)
        GL11.glPopClientAttrib()
    }

    fun logText(text: String?) {
        println(text)
    }
}

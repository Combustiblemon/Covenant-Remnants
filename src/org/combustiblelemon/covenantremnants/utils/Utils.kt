package org.combustiblelemon.covenantremnants.utils

import com.fs.starfarer.api.combat.ShipAPI
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
        AffineTransform.getRotateInstance(
            Math.toRadians(degrees.toDouble()),
            center.x.toDouble(),
            center.y.toDouble()
        ).transform(pt, 0, pt, 0, 1)

        return Vector2f(
            pt[0].toFloat(),
            pt[1].toFloat(),
        )
    }

    fun normalize(value: Double, min: Double, max: Double): Float {
        return ((value - min) / (max - min)).toFloat()
    }

    fun logText(text: String?) {
        println(text)
    }

    fun getShipDimensions(ship: ShipAPI): Array<Vector2f> {

        val max = Vector2f(0f, 0f)
        val min = Vector2f(10000f, 10000f)

        ship.exactBounds.origSegments.forEach { segment ->
            // max
            if (segment.p1.x > max.y) {
                max.y = segment.p1.x
            }

            if (segment.p1.y > max.x) {
                max.x = segment.p1.y
            }

            // min
            if (segment.p1.x < min.y) {
                min.y = segment.p1.x
            }

            if (segment.p1.y < min.x) {
                min.x = segment.p1.y
            }
        }

        return arrayOf(max, min)
    }
}

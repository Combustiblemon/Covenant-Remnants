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

    fun getShipPolygonInWorld(ship: ShipAPI): Array<Vector2f> {
        val shipX = ship.location!!.x
        val shipY = ship.location!!.y
        val results = mutableListOf<Vector2f>()

        val facing = Math.toRadians(ship.facing.toDouble()).toFloat()
        val cos = cos(facing)
        val sin = sin(facing)

        ship.exactBounds!!.origSegments.forEach { segment ->
            val x = segment!!.p1.x
            val y = segment.p1.y

            results.add(Vector2f((x * cos - y * sin) + shipX, (x * sin + y * cos) + shipY))
        }

        return results.toTypedArray()
    }

    /**
     * Generates random points inside a polygon (works with concave shapes)
     * @param polygon Array of Vector2f points defining the polygon vertices
     * @param pointCount Number of points to generate (default 2)
     * @return Array of Vector2f containing the generated points
     */
    fun getRandomPointsInPolygon(polygon: Array<Vector2f>, pointCount: Int = 2): Array<Vector2f> {
        // Find bounding box
        val minX = polygon.minOf { it.x }
        val maxX = polygon.maxOf { it.x }
        val minY = polygon.minOf { it.y }
        val maxY = polygon.maxOf { it.y }

        val results = mutableListOf<Vector2f>()

        while (results.size < pointCount) {
            // Generate random point within bounding box
            val x = minX + (maxX - minX) * Math.random().toFloat()
            val y = minY + (maxY - minY) * Math.random().toFloat()
            val point = Vector2f(x, y)

            // Check if point is inside polygon using ray casting algorithm
            if (isPointInPolygon(point, polygon)) {
                results.add(point)
            }
        }

        return results.toTypedArray()
    }

    /**
     * Checks if a point is inside a polygon using ray casting algorithm
     * @param point The point to check
     * @param polygon Array of Vector2f defining the polygon vertices
     * @return true if point is inside the polygon
     */
    private fun isPointInPolygon(point: Vector2f, polygon: Array<Vector2f>): Boolean {
        var inside = false
        var j = polygon.size - 1

        for (i in polygon.indices) {
            if ((polygon[i].y > point.y) != (polygon[j].y > point.y) &&
                point.x < (polygon[j].x - polygon[i].x) * (point.y - polygon[i].y) /
                (polygon[j].y - polygon[i].y) + polygon[i].x
            ) {
                inside = !inside
            }
            j = i
        }

        return inside
    }
}

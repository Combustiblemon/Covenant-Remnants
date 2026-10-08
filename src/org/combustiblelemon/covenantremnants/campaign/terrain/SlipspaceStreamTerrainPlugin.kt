package org.combustiblelemon.covenantremnants.campaign.terrain

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CampaignFleetAPI
import com.fs.starfarer.api.campaign.SectorEntityToken
import com.fs.starfarer.api.impl.campaign.ids.Tags
import com.fs.starfarer.api.impl.campaign.velfield.SlipstreamTerrainPlugin2
import org.combustiblelemon.covenantremnants.campaign.abilities.SlipspaceJumpRules
import org.lwjgl.util.vector.Vector2f
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * The pocket's path: a vanilla slipstream (the current carries the fleet, the player can slow or
 * speed it slightly) that the fleet cannot leave. Outside the path the fleet is pushed back toward
 * the centreline, harder the further out, and hard-clamped at
 * [SlipspaceJumpRules.pathClampRadii.value] fleet radii past the edge (developer ruling 2026-10-07:
 * at most one fleet length outside).
 *
 * The vanilla containment query returns null outside the stream and off its ends, so the
 * push-back uses its own nearest-point-on-polyline over the saved segments. No `readResolve`
 * here on purpose: the superclass's is package-private and ours would shadow it.
 */
class SlipspaceStreamTerrainPlugin : SlipstreamTerrainPlugin2() {

    /** Where a point sits relative to the path. */
    class PathOffset(
        /** Distance along the path to the nearest point, su. */
        val along: Float,
        /** Signed distance from the centreline (left of travel positive), su. */
        val offset: Float,
        /** Half the path width at that point, su. */
        val halfWidth: Float,
        /** Nearest centreline point. */
        val point: Vector2f,
        /** Unit vector from the centreline toward the queried point (zero when on it). */
        val outward: Vector2f,
        /** True when the nearest point is the path's first or last point (the query lies beyond an end). */
        val beyondEnd: Boolean
    )

    override fun applyEffect(other: SectorEntityToken, days: Float) {
        // A fleet mid-transition (arriving: sliding out of the entrance tube, which starts beyond
        // the path's start) is placed by the transition script; neither the current nor the clamp
        // may touch it.
        if (other is CampaignFleetAPI && other.isInHyperspaceTransition) return
        super.applyEffect(other, days)
        if (other !is CampaignFleetAPI) return
        if (other.hasTag(Tags.UNAFFECTED_BY_SLIPSTREAM)) return
        contain(other, days)
    }

    /** Push-back and clamp for one fleet. */
    private fun contain(fleet: CampaignFleetAPI, days: Float) {
        val at = nearestOnPath(fleet.location) ?: return
        // Inside the corridor nothing to do; beyond an end the corridor has no width, so the whole
        // distance from the end point counts (this is what keeps the fleet from sailing past the exit).
        val inner = if (at.beyondEnd) 0f else at.halfWidth
        val excess = abs(at.offset) - inner
        if (excess <= 0f) return

        val seconds = days * Global.getSector().clock.secondsPerDay
        val radius = max(fleet.radius, 10f)
        val clamp = inner + SlipspaceJumpRules.pathClampRadii.value * radius

        // Push toward the centreline, growing with how far out the fleet is.
        val accel = SlipspaceJumpRules.pathPushAccel.value * (1f + excess / radius)
        val vel = fleet.velocity
        var vx = vel.x - at.outward.x * accel * seconds
        var vy = vel.y - at.outward.y * accel * seconds

        if (abs(at.offset) > clamp) {
            // Hard stop at the clamp line: back onto it, outward velocity stripped.
            fleet.setLocation(at.point.x + at.outward.x * clamp, at.point.y + at.outward.y * clamp)
            val outwardSpeed = vx * at.outward.x + vy * at.outward.y
            if (outwardSpeed > 0f) {
                vx -= at.outward.x * outwardSpeed
                vy -= at.outward.y * outwardSpeed
            }
        }
        fleet.setVelocity(vx, vy)
    }

    /**
     * Nearest point on the path polyline to [loc], with the signed lateral offset and the local
     * half-width. Works beyond either end (the end points are the nearest then).
     */
    fun nearestOnPath(loc: Vector2f): PathOffset? {
        recomputeIfNeeded()
        val segs = segments
        if (segs.isEmpty()) return null
        if (segs.size == 1) {
            val p = segs[0].loc
            val dx = loc.x - p.x
            val dy = loc.y - p.y
            val d = sqrt(dx * dx + dy * dy)
            val out = if (d > 0.001f) Vector2f(dx / d, dy / d) else Vector2f()
            return PathOffset(0f, d, segs[0].width * 0.5f, Vector2f(p), out, true)
        }

        var bestDist2 = Float.MAX_VALUE
        var bestIndex = 0
        var bestT = 0f
        var bestClamped = false
        var bestPoint = Vector2f()
        var along = 0f
        var bestAlong = 0f
        for (i in 0 until segs.size - 1) {
            val a = segs[i].loc
            val b = segs[i + 1].loc
            val abx = b.x - a.x
            val aby = b.y - a.y
            val len2 = abx * abx + aby * aby
            var t = if (len2 <= 0.0001f) 0f else ((loc.x - a.x) * abx + (loc.y - a.y) * aby) / len2
            var clamped = false
            if (t < 0f) {
                t = 0f
                clamped = i == 0
            }
            if (t > 1f) {
                t = 1f
                clamped = i == segs.size - 2
            }
            val px = a.x + abx * t
            val py = a.y + aby * t
            val dx = loc.x - px
            val dy = loc.y - py
            val d2 = dx * dx + dy * dy
            if (d2 < bestDist2) {
                bestDist2 = d2
                bestIndex = i
                bestT = t
                bestClamped = clamped
                bestPoint = Vector2f(px, py)
                bestAlong = along + sqrt(len2) * t
            }
            along += sqrt(len2)
        }

        val a = segs[bestIndex]
        val b = segs[bestIndex + 1]
        val halfWidth = (a.width + (b.width - a.width) * bestT) * 0.5f
        val dist = sqrt(bestDist2)
        val outward = if (dist > 0.001f) Vector2f((loc.x - bestPoint.x) / dist, (loc.y - bestPoint.y) / dist) else Vector2f()
        // Sign: left of the direction of travel is positive.
        val dirX = b.loc.x - a.loc.x
        val dirY = b.loc.y - a.loc.y
        val cross = dirX * (loc.y - bestPoint.y) - dirY * (loc.x - bestPoint.x)
        val signed = if (cross < 0f) -dist else dist
        return PathOffset(bestAlong, signed, halfWidth, bestPoint, outward, bestClamped)
    }
}

package org.combustiblelemon.covenantremnants.weapons

import com.fs.starfarer.api.combat.ArmorGridAPI
import org.lwjgl.util.vector.Vector2f
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Plasma armor melt of one torpedo hit (launcher polish, developer 2026-10-09): over [DURATION]
 * seconds a pool of armor damage is taken out of the armor cells around the hit, written straight
 * to the grid (no armor reduction, never hull), like vanilla `BreachOnHitEffect.dealArmorDamage`.
 *
 * - Cells: centre within the melt radius of the hit point and inside the hull; each one's share is
 *   (1 - d / radius) of the total weight, so the centre melts most. No such cell: the hit cell
 *   alone takes the pool.
 * - Rate: falls linearly from the impact to zero at [DURATION] (hottest at impact); it integrates
 *   to the pool. A cell holds at most its current armor; what a stripped cell cannot take is lost.
 * - The cells are picked once and are ship-relative, so the melt follows the ship.
 *
 * No Global / Misc / engine calls here (the headless MeltHarness runs it on a fake grid);
 * [org.combustiblelemon.covenantremnants.plugins.TorpedoRenderPlugin] drives it and shows the
 * floating numbers every [TICK] seconds.
 */
internal class MeltBurn private constructor(
    private val grid: ArmorGridAPI,
    private val cellX: IntArray,
    private val cellY: IntArray,
    private val share: FloatArray,
    val pool: Float
) {
    companion object {
        const val DURATION = 2f
        const val TICK = 0.5f

        /**
         * The burn for a hit at [point] (world) on a ship facing [facing] degrees with armor [grid]:
         * [pool] armor damage over the cells within [radius] su whose centre [inHull] accepts. Null
         * when there is nothing to melt.
         */
        fun pick(
            grid: ArmorGridAPI, facing: Float, point: Vector2f, radius: Float, pool: Float,
            inHull: (Vector2f) -> Boolean
        ): MeltBurn? {
            if (pool <= 0f || radius <= 0f) return null
            val cells = grid.grid
            val width = cells.size
            if (width == 0) return null
            val height = cells[0].size
            val size = grid.cellSize
            if (size <= 0f) return null

            // ArmorGridAPI.getLocation gives a cell's corner (its minimum in the sprite frame, rotated
            // by facing - 90): the centre is half a cell further along both sprite axes.
            val a = Math.toRadians((facing - 90f).toDouble())
            val c = cos(a).toFloat()
            val s = sin(a).toFloat()
            val half = size * 0.5f
            val offsetX = half * c - half * s
            val offsetY = half * s + half * c

            val hit = grid.getCellAtLocation(point)
            val reach = ceil(radius / size).toInt() + 1
            val x0 = if (hit != null) max(0, hit[0] - reach) else 0
            val x1 = if (hit != null) min(width - 1, hit[0] + reach) else width - 1
            val y0 = if (hit != null) max(0, hit[1] - reach) else 0
            val y1 = if (hit != null) min(height - 1, hit[1] + reach) else height - 1

            val xs = ArrayList<Int>()
            val ys = ArrayList<Int>()
            val ws = ArrayList<Float>()
            val centre = Vector2f()
            for (x in x0..x1) {
                for (y in y0..y1) {
                    val corner = grid.getLocation(x, y)
                    centre.set(corner.x + offsetX, corner.y + offsetY)
                    val dx = centre.x - point.x
                    val dy = centre.y - point.y
                    val d = sqrt(dx * dx + dy * dy)
                    if (d >= radius || !inHull(centre)) continue
                    xs.add(x)
                    ys.add(y)
                    ws.add(1f - d / radius)
                }
            }
            if (xs.isEmpty()) {
                if (hit == null) return null
                xs.add(hit[0])
                ys.add(hit[1])
                ws.add(1f)
            }
            val total = ws.sum()
            return MeltBurn(
                grid, xs.toIntArray(), ys.toIntArray(), FloatArray(ws.size) { ws[it] / total }, pool
            )
        }
    }

    var age = 0f
        private set

    /** Armor melted so far, and the part not yet shown as a floating number. */
    var melted = 0f
        private set
    private var pending = 0f
    private var sinceTick = 0f

    val cellCount: Int get() = cellX.size
    val isDone: Boolean get() = age >= DURATION

    /** Melts this frame's part; true when a floating-number tick is due (every [TICK] s and at the end). */
    fun advance(dt: Float): Boolean {
        if (isDone) return false
        val before = 1f - age / DURATION
        age = min(DURATION, age + dt)
        val after = 1f - age / DURATION
        // rate (2 / DURATION)(1 - t / DURATION): this frame's fraction of the pool
        val fraction = before * before - after * after
        if (fraction > 0f) {
            for (i in cellX.indices) {
                val have = grid.getArmorValue(cellX[i], cellY[i])
                val take = min(pool * share[i] * fraction, have)
                if (take <= 0f) continue
                grid.setArmorValue(cellX[i], cellY[i], have - take)
                melted += take
                pending += take
            }
        }
        sinceTick += dt
        if (sinceTick >= TICK || isDone) {
            sinceTick = 0f
            return true
        }
        return false
    }

    /** The armor melted since the last call. */
    fun takePending(): Float {
        val amount = pending
        pending = 0f
        return amount
    }
}

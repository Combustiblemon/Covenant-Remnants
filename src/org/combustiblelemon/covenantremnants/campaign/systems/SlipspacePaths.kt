package org.combustiblelemon.covenantremnants.campaign.systems

import com.fs.starfarer.api.Global
import org.combustiblelemon.covenantremnants.campaign.abilities.SlipspaceJumpRules
import org.lwjgl.util.vector.Vector2f
import java.util.Random
import kotlin.math.cos
import kotlin.math.sin

/**
 * One saved pocket path: seed (shape) and length. Stored per unordered system pair in the
 * sector's persistent data, so the same pair always gets the same path (developer ruling
 * 2026-10-07: random on first use, then saved).
 *
 * Plain class with public fields: it goes through XStream into the save.
 */
class SlipspacePathRecord(
    @JvmField var seed: Long = 0L,
    @JvmField var lengthSu: Float = 0f
)

object SlipspacePaths {
    const val PERSISTENT_KEY = "covenant_remnants_slipspace_paths"

    /** Unordered pair key: the pocket path is a property of the pair, not of the direction. */
    fun key(a: String, b: String): String = if (a <= b) "$a|$b" else "$b|$a"

    @Suppress("UNCHECKED_CAST")
    private fun records(): MutableMap<String, SlipspacePathRecord> {
        val data = Global.getSector().persistentData
        var map = data[PERSISTENT_KEY] as? MutableMap<String, SlipspacePathRecord>
        if (map == null) {
            map = HashMap()
            data[PERSISTENT_KEY] = map
        }
        return map
    }

    /** The record for the pair, generated and saved on first use. */
    fun recordFor(originId: String, destId: String): SlipspacePathRecord {
        val map = records()
        val k = key(originId, destId)
        map[k]?.let { if (it.lengthSu > 0f) return it }
        val random = Random()
        val length = SlipspaceJumpRules.pathLengthMin.value +
            random.nextFloat() * (SlipspaceJumpRules.pathLengthMax.value - SlipspaceJumpRules.pathLengthMin.value)
        val record = SlipspacePathRecord(random.nextLong(), length)
        map[k] = record
        return record
    }

    /**
     * The winding polyline for a record, starting at (0, 0): the heading drifts around a base
     * direction by three summed sine waves whose amplitudes total at most [SlipspaceJumpRules.pathWindMaxDegrees.value],
     * so the path always progresses and never crosses itself. Points every
     * [SlipspaceJumpRules.pathPointSpacing.value] su; deterministic in the seed.
     */
    fun buildPolyline(record: SlipspacePathRecord): List<Vector2f> {
        val random = Random(record.seed)
        val length = record.lengthSu
        val spacing = SlipspaceJumpRules.pathPointSpacing.value
        val base = random.nextFloat() * 360f

        // Three waves: amplitudes split the wind budget, frequencies in cycles over the path.
        val budget = SlipspaceJumpRules.pathWindMaxDegrees.value
        val amps = floatArrayOf(budget * (0.45f + 0.15f * random.nextFloat()), 0f, 0f)
        amps[1] = (budget - amps[0]) * (0.5f + 0.3f * random.nextFloat())
        amps[2] = budget - amps[0] - amps[1]
        val freqs = floatArrayOf(
            0.5f + random.nextFloat() * 1.0f,
            1.5f + random.nextFloat() * 1.5f,
            3.0f + random.nextFloat() * 2.0f
        )
        val phases = FloatArray(3) { random.nextFloat() * 360f }

        val points = ArrayList<Vector2f>()
        val current = Vector2f(0f, 0f)
        points.add(Vector2f(current))
        var travelled = 0f
        while (travelled < length) {
            val step = minOf(spacing, length - travelled)
            val s = (travelled + step * 0.5f) / length
            var heading = base
            for (i in 0 until 3) {
                heading += amps[i] * sin(Math.toRadians((360f * freqs[i] * s + phases[i]).toDouble())).toFloat()
            }
            val rad = Math.toRadians(heading.toDouble())
            current.x += cos(rad).toFloat() * step
            current.y += sin(rad).toFloat() * step
            points.add(Vector2f(current))
            travelled += step
        }
        return points
    }
}

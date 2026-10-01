package org.combustiblelemon.covenantremnants.utils.render.rupture

/**
 * A Halo: Reach function curve baked as evenly spaced samples over the input 0..1 (see
 * [ReachCurves]). [at] interpolates linearly and clamps the input.
 */
internal class ReachCurve(private val samples: FloatArray) {
    fun at(x: Float): Float {
        val last = samples.size - 1
        val position = x.coerceIn(0f, 1f) * last
        val index = position.toInt().coerceAtMost(last - 1)
        val fraction = position - index
        return samples[index] + (samples[index + 1] - samples[index]) * fraction
    }
}

/** A Halo: Reach colour curve: RGB triples over the input 0..1. */
internal class ReachColorCurve(private val samples: FloatArray) {
    private val count = samples.size / 3

    /** Writes the colour at [x] into [out] (RGB). */
    fun at(x: Float, out: FloatArray) {
        val last = count - 1
        val position = x.coerceIn(0f, 1f) * last
        val index = position.toInt().coerceAtMost(last - 1)
        val fraction = position - index
        for (channel in 0 until 3) {
            val a = samples[index * 3 + channel]
            val b = samples[(index + 1) * 3 + channel]
            out[channel] = a + (b - a) * fraction
        }
    }
}

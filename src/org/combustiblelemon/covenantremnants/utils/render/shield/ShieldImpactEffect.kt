package org.combustiblelemon.covenantremnants.utils.render.shield

import org.combustiblelemon.covenantremnants.utils.render.shield.ShieldImpactCurves as C
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * CPU state of the shield's Reach-style plasma look (no GL): the hits, the low-HP baseline, the
 * collapse and the recharge wave, reduced every frame to the parameter set `plasma.glsl` draws
 * with. Owned by [PlasmaLayer].
 *
 * Reach's runtime `shield_intensity` (spikes on a hit, decays) is not in the tags; it is modelled
 * here as the strongest active hit, floored by a baseline that grows as the shield weakens
 * (hybrid model, developer decision 2026-10-02).
 *
 * Hits are stored ship-local so they stay on the hull while it moves and turns.
 */
internal class ShieldImpactEffect {
    companion object {
        /** Reach curves take the hit progress 0..1 over the tag's hit time. */
        const val HIT_TIME = C.HIT_TIME

        /** Weakest flare any hit can make (strength floor; strength is the damage `level`). */
        const val HIT_MIN_STRENGTH = 0.25f

        /** Concurrent hits (shader array size). */
        const val MAX_HITS = 8

        /** Always-on intensity at full HP and at empty HP (0.45 is just past the 0.4 knee of the Reach colour curves). */
        const val BASELINE_FULL = 0.05f
        const val BASELINE_EMPTY = 0.45f

        /** Reach's hit colour is ranged by vitality (black at full HP); keep hits visible with this floor. */
        const val HIT_COLOR_MIN = 0.3f

        /** Hit radius multiplier on the Reach fraction (of the body radius). */
        const val HIT_RADIUS_MULT = 1f

        /** World units per noise repeat at tiling 1. */
        const val TILE_WORLD = 200f

        /** Reach's scroll speed is in uv per second; scaled down for the much larger ships. */
        const val SCROLL_UV_PER_SEC = 0.2f

        /** Collapse: the Reach event starts 0.05 s after the break and lasts 0.25 s. */
        const val COLLAPSE_DELAY = 0.05f
        const val COLLAPSE_EVENT = 0.25f
        const val COLLAPSE_DURATION = COLLAPSE_DELAY + COLLAPSE_EVENT

        /** Collapse shell push: Reach extrusion 0.035 -> 0.075 m on a ~2 m body. */
        const val COLLAPSE_EXPAND_START = 0.02f
        const val COLLAPSE_EXPAND_END = 0.04f

        /** Collapse: the body (fill/border/hex) fades out over the first 45% of the event. */
        const val COLLAPSE_BODY_FADE_END = 0.45f

        /** Break flash: lens flare + light in Reach, 0.1 s; colour = the area screen effect's filter. */
        const val FLASH_DURATION = 0.1f
        const val FLASH_STRENGTH = 0.8f
        val FLASH_COLOR = floatArrayOf(0.667f, 0.784f, 1.0f)

        /** Recharge wave length (Reach event 2 s with its ramp peaking at 0.5). */
        const val RECHARGE_DURATION = 1.2f

        /** Scroll directions of the two noise maps (unit vectors). */
        private const val DIR_AX = 0.8f
        private const val DIR_AY = 0.6f
        private const val DIR_BX = -0.6f
        private const val DIR_BY = 0.8f
    }

    class Hit(val localX: Float, val localY: Float, val strength: Float, var age: Float)

    val hits = ArrayList<Hit>(MAX_HITS)

    /** Shield HP fraction, 0..1 (Reach `shield_vitality`). Set every frame by the renderer. */
    var vitality = 1f

    private var collapseClock = -1f
    private var flashClock = -1f
    private var rechargeClock = -1f

    // Noise scroll offsets, uv units, accumulated and wrapped (no float drift, no jump when the
    // scroll speed changes with the state).
    var offsetAx = 0f; private set
    var offsetAy = 0f; private set
    var offsetBx = 0f; private set
    var offsetBy = 0f; private set

    // --- Per-frame outputs for PlasmaLayer ---
    val edgeGlow = FloatArray(3)
    val centerColor = FloatArray(3)
    val edgeColor = FloatArray(3)
    val flash = FloatArray(3)
    val edgeBand = FloatArray(4)
    val plasmaBand = FloatArray(4)
    var edgeSharpness = C.EDGE_SHARPNESS; private set
    var centerSharpness = C.CENTER_SHARPNESS; private set
    var tiling = C.TILING; private set
    var shellScale = 1f; private set
    var bodyFade = 1f; private set

    /** Active hit spots this frame: ship-local x, y; radius as a fraction of the body radius; strength; colour. */
    class Spot {
        var localX = 0f
        var localY = 0f
        var radiusFraction = 0f
        var strength = 0f
        val color = FloatArray(3)
    }

    val spots = ArrayList<Spot>(MAX_HITS + 1)
    private val spotPool = Array(MAX_HITS + 1) { Spot() }

    val collapseActive get() = collapseClock >= 0f && collapseClock < COLLAPSE_DURATION
    val collapseFinished get() = collapseClock >= COLLAPSE_DURATION
    val rechargeActive get() = rechargeClock >= 0f && rechargeClock < RECHARGE_DURATION

    /** True when the layer has anything to draw (the baseline is always on, so only idle after a finished collapse). */
    val active get() = !collapseFinished

    private val scratch = FloatArray(3)

    /** Registers a hit at a world position (ship-local storage), strength = damage level floored. */
    fun addHit(worldX: Float, worldY: Float, strength: Float, shipX: Float, shipY: Float, facingDegrees: Float) {
        val dx = worldX - shipX
        val dy = worldY - shipY
        val rad = Math.toRadians(-facingDegrees.toDouble())
        val c = cos(rad).toFloat()
        val s = sin(rad).toFloat()
        val hit = Hit(dx * c - dy * s, dx * s + dy * c, max(HIT_MIN_STRENGTH, strength).coerceAtMost(1f), 0f)
        if (hits.size >= MAX_HITS) {
            hits.removeAt(hits.indices.maxByOrNull { hits[it].age } ?: 0)
        }
        hits.add(hit)
    }

    fun startCollapse() {
        collapseClock = 0f
        flashClock = 0f
        rechargeClock = -1f
        hits.clear()
    }

    fun startRecharge() {
        if (collapseClock >= 0f) return
        rechargeClock = 0f
    }

    /** Clears the finished collapse so the layer draws again (shield re-raised). */
    fun reset() {
        collapseClock = -1f
        flashClock = -1f
        rechargeClock = -1f
        hits.clear()
    }

    /** Reach `shield_intensity`: strongest active hit, floored by the low-HP baseline. */
    fun intensity(): Float {
        var best = BASELINE_FULL + (BASELINE_EMPTY - BASELINE_FULL) * (1f - vitality)
        for (hit in hits) {
            best = max(best, hit.strength * (1f - hit.age / HIT_TIME))
        }
        return best.coerceIn(0f, 1f)
    }

    fun advance(dt: Float) {
        for (hit in hits) hit.age += dt
        hits.removeAll { it.age >= HIT_TIME }
        if (collapseClock >= 0f) collapseClock += dt
        if (flashClock >= 0f) flashClock += dt
        if (rechargeClock >= 0f) rechargeClock += dt
        if (flashClock >= FLASH_DURATION) flashClock = -1f
        if (rechargeClock >= RECHARGE_DURATION) rechargeClock = -1f
        update(dt)
    }

    private fun update(dt: Float) {
        spots.clear()
        val v = vitality.coerceIn(0f, 1f)

        val inEvent = collapseClock >= COLLAPSE_DELAY && collapseClock < COLLAPSE_DURATION
        if (inEvent) {
            val x = ((collapseClock - COLLAPSE_DELAY) / COLLAPSE_EVENT).coerceIn(0f, 1f)
            C.COLLAPSE_EDGE_GLOW_COLOR.at(x, edgeGlow); scale(edgeGlow, C.COLLAPSE_EDGE_GLOW_INTENSITY)
            C.COLLAPSE_PLASMA_CENTER_COLOR.at(x, centerColor); scale(centerColor, C.COLLAPSE_PLASMA_CENTER_INTENSITY)
            C.COLLAPSE_PLASMA_EDGE_COLOR.at(x, edgeColor); scale(edgeColor, C.COLLAPSE_PLASMA_EDGE_INTENSITY.at(x))
            band(edgeBand, C.COLLAPSE_GLOW_INNER, C.COLLAPSE_GLOW_CENTER, C.COLLAPSE_GLOW_OUTER)
            band(plasmaBand, C.COLLAPSE_PLASMA_INNER, C.COLLAPSE_PLASMA_CENTER, C.COLLAPSE_PLASMA_OUTER)
            edgeSharpness = C.COLLAPSE_EDGE_SHARPNESS
            centerSharpness = C.COLLAPSE_CENTER_SHARPNESS
            tiling = C.COLLAPSE_TILING
            shellScale = 1f + COLLAPSE_EXPAND_START + (COLLAPSE_EXPAND_END - COLLAPSE_EXPAND_START) * x
            bodyFade = 1f - (x / COLLAPSE_BODY_FADE_END).coerceIn(0f, 1f)
            scroll(dt, C.COLLAPSE_SCROLL)
        } else {
            val intensity = intensity()
            C.EDGE_GLOW_COLOR.at(intensity, edgeGlow); scale(edgeGlow, C.EDGE_GLOW_INTENSITY.at(v))
            C.PLASMA_CENTER_COLOR.at(intensity, centerColor); scale(centerColor, C.PLASMA_CENTER_INTENSITY.at(v))
            C.PLASMA_EDGE_COLOR.at(intensity, edgeColor); scale(edgeColor, C.PLASMA_EDGE_INTENSITY.at(v))
            band(edgeBand, C.GLOW_INNER, C.GLOW_CENTER, C.GLOW_OUTER)
            band(plasmaBand, C.PLASMA_INNER, C.PLASMA_CENTER, C.PLASMA_OUTER)
            edgeSharpness = C.EDGE_SHARPNESS
            centerSharpness = C.CENTER_SHARPNESS
            tiling = C.TILING
            shellScale = if (collapseClock >= 0f) 1f + COLLAPSE_EXPAND_START * (collapseClock / COLLAPSE_DELAY) else 1f
            bodyFade = if (collapseFinished) 0f else 1f
            scroll(dt, if (rechargeActive) C.RECHARGE_SCROLL else C.SCROLL)

            // Hit spots: Reach colour over the hit's progress; the colour is ranged by vitality
            // (black at full HP), floored so hits always read.
            val hitFade = HIT_COLOR_MIN + (1f - HIT_COLOR_MIN) * (1f - v)
            val hitRadius = C.HIT_RADIUS.at(v) * HIT_RADIUS_MULT
            for (hit in hits) {
                val spot = spotPool[spots.size]
                spot.localX = hit.localX
                spot.localY = hit.localY
                spot.radiusFraction = hitRadius
                spot.strength = hit.strength
                C.HIT_COLOR.at(hit.age / HIT_TIME, spot.color); scale(spot.color, hitFade)
                spots.add(spot)
            }

            // Recharge wave: the recharge tag's glow + band colours ramp up and down, and its hit
            // sweeps out from the core to the whole body.
            if (rechargeActive) {
                val x = rechargeClock / RECHARGE_DURATION
                C.RECHARGE_EDGE_GLOW_COLOR.at(x, scratch); add(edgeGlow, scratch, C.RECHARGE_EDGE_GLOW_INTENSITY)
                C.RECHARGE_PLASMA_EDGE_COLOR.at(x, scratch); add(edgeColor, scratch, C.RECHARGE_PLASMA_EDGE_INTENSITY)
                val spot = spotPool[spots.size]
                spot.localX = 0f
                spot.localY = 0f
                spot.radiusFraction = C.RECHARGE_HIT_RADIUS.at(x)
                spot.strength = 1f
                C.RECHARGE_HIT_COLOR.at(x, spot.color)
                spots.add(spot)
            }
        }

        if (flashClock >= 0f) {
            val k = FLASH_STRENGTH * (1f - flashClock / FLASH_DURATION).coerceIn(0f, 1f)
            for (i in 0 until 3) flash[i] = FLASH_COLOR[i] * k
        } else {
            flash.fill(0f)
        }
    }

    private fun scroll(dt: Float, speed: Float) {
        val step = speed * SCROLL_UV_PER_SEC * dt
        offsetAx = wrap(offsetAx + DIR_AX * step)
        offsetAy = wrap(offsetAy + DIR_AY * step)
        offsetBx = wrap(offsetBx + DIR_BX * step)
        offsetBy = wrap(offsetBy + DIR_BY * step)
    }

    private fun wrap(v: Float): Float = v - kotlin.math.floor(v)

    private fun scale(rgb: FloatArray, k: Float) {
        for (i in 0 until 3) rgb[i] *= k
    }

    private fun add(rgb: FloatArray, other: FloatArray, k: Float) {
        for (i in 0 until 3) rgb[i] += other[i] * k
    }

    /**
     * Reach band as scale/offset pairs: `sat(min(d * x + y, d * z + w))` rises from [inner] to 1 at
     * [center] and falls back to 0 at [outer] (depth d in 0..1).
     */
    private fun band(out: FloatArray, inner: Float, center: Float, outer: Float) {
        val up = max(center - inner, 1e-4f)
        val down = max(outer - center, 1e-4f)
        out[0] = 1f / up
        out[1] = -inner / up
        out[2] = -1f / down
        out[3] = outer / down
    }
}

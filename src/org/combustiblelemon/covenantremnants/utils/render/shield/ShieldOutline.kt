package org.combustiblelemon.covenantremnants.utils.render.shield

import org.combustiblelemon.lemonlib.geometry.PolygonSmoothing

/**
 * Live-tunable constants for the Covenant shield outline (how the hull bounds become the shield
 * polygon). Tuned in game through the `ShieldTune` console command; values reset on restart.
 *
 * Pipeline (per frame, in [org.combustiblelemon.covenantremnants.utils.render.ShieldShader]):
 * hull bounds -> Taubin smoothing ([iterations], [lambda], [mu]) -> adaptive Catmull-Rom corner
 * rounding ([curveDegrees], [curveMaxPerEdge], capped at the shader point capacity) -> outward
 * offset along vertex normals by [margin] -> scale ([scaleX], [scaleY]) -> ship transform.
 */
object ShieldOutline {

    /** One live-tunable constant. */
    class Tunable internal constructor(val name: String, val default: Float, val description: String) {
        var value: Float = default

        fun reset() {
            value = default
        }
    }

    private val registry = LinkedHashMap<String, Tunable>()

    private fun tune(name: String, default: Float, description: String): Tunable =
        Tunable(name, default, description).also { registry[name] = it }

    // Developer 2026-10-12: Laplacian smoothing shrank the outline inside the sprite. Taubin keeps
    // the size; the margin adds the visible gap between hull and rim.
    val margin = tune("MARGIN", 14f, "Outward offset of the outline along vertex normals, world units (negative = inward)")
    val iterations = tune("ITERATIONS", 8f, "Taubin smoothing iterations (0 = raw hull bounds)")
    val lambda = tune("LAMBDA", PolygonSmoothing.TAUBIN_LAMBDA, "Taubin shrink factor per iteration (0..1)")
    val mu = tune("MU", PolygonSmoothing.TAUBIN_MU, "Taubin inflate factor per iteration (negative, |mu| > lambda keeps size)")
    // Developer 2026-10-12: repositioning alone left visible corners; the spline adds points at
    // the sharp turns, within the shader's point budget (ShieldProgram.POINT_CAPACITY).
    val curveDegrees = tune("CURVE_DEG", 15f, "Spline: one added point per this many degrees of turn at a corner (0 = no spline)")
    val curveMaxPerEdge = tune("CURVE_MAX", 6f, "Spline: max segments per original edge")
    val scaleX = tune("SCALE_X", 1f, "Polygon scale across the hull, about the ship centre (was 1.05 before the margin existed)")
    val scaleY = tune("SCALE_Y", 1f, "Polygon scale along the hull, about the ship centre (was 1.1 before the margin existed)")

    val tunables: Collection<Tunable> get() = registry.values

    fun tunable(name: String): Tunable? = registry[name.uppercase()]

    fun resetAll() {
        registry.values.forEach { it.reset() }
    }
}

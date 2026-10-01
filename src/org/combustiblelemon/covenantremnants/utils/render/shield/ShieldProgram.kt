package org.combustiblelemon.covenantremnants.utils.render.shield

import com.fs.starfarer.api.Global
import org.combustiblelemon.lemonlib.render.composition.ComposedShaderProgram
import org.combustiblelemon.lemonlib.render.composition.ShaderLayer

/**
 * The one GL program every Covenant shield draws with. The assembled source depends only on the
 * layer set and the point-array capacity ([POINT_CAPACITY]; the live count is the `pointCount`
 * uniform), so it is identical for every ship: compiled once, at game start from the mod plugin
 * ([warmUp]), never per ship. Before this each ship compiled its own copy on first activation
 * (~250 ms each on an RTX 5080), which stacked into a multi-second hitch when a fleet raised shields.
 *
 * The program is built with throw-away template layers (only their module/stub source is used);
 * each [org.combustiblelemon.covenantremnants.utils.render.ShieldShader] keeps its own layer
 * instances for state and uploads their uniforms itself. `ShaderLayers` debug overrides still work:
 * `ensureUpToDate()` rebuilds this shared program, and the owners skip disabled layers' uploads.
 */
internal object ShieldProgram {
    /** Capacity of the `points[]` uniform array. Hulls have 16-64 bound points; larger hulls are clamped with a warning. */
    const val POINT_CAPACITY = 128

    const val SYSTEM_NAME = "shield"

    private var program: ComposedShaderProgram? = null

    /** Template layer list: same classes and order as the owner's list (GLSL concat order). */
    fun templateLayers(): List<ShaderLayer> = listOf(
        FillLayer(), BorderLayer(), SweepLayer(), HoneycombLayer(), FortressLayer(),
        PlasmaLayer(null), DistortionLayer(null), DebugLayer()
    )

    /** The shared program, compiled on first use (GL thread). */
    fun get(): ComposedShaderProgram {
        program?.let { return it }
        val built = ComposedShaderProgram(
            systemName = SYSTEM_NAME,
            vertexShaderFile = "data/shaders/shield/shield.vert",
            baseFragmentFile = "data/shaders/shield/base.frag",
            layers = templateLayers(),
            placeholders = mapOf("POINT_COUNT_PLACEHOLDER" to POINT_CAPACITY.toString())
        )
        program = built
        return built
    }

    /** Compiles the program now (game start, GL thread). Failures are logged, not thrown: the owners retry lazily. */
    fun warmUp() {
        if (program != null) return
        val start = System.nanoTime()
        try {
            get()
            Global.getLogger(ShieldProgram::class.java)
                .info("Covenant shield program compiled in ${(System.nanoTime() - start) / 1_000_000} ms")
        } catch (e: Exception) {
            Global.getLogger(ShieldProgram::class.java)
                .warn("Covenant shield program warm-up failed, will compile on first use: ${e.message}")
        }
    }
}

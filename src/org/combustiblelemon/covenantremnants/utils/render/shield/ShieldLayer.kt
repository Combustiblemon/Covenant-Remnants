package org.combustiblelemon.covenantremnants.utils.render.shield

import com.fs.starfarer.api.combat.ViewportAPI

/**
 * One visual layer of the composed shield shader.
 *
 * Owns its GLSL module snippet (uniform declarations + `<layerName>_`-prefixed functions)
 * and the Kotlin-side state + per-frame uniform uploads for those uniforms.
 *
 * Module contract:
 * - Every function in the module is prefixed with the layer name to avoid collisions.
 * - Every uniform is declared in exactly one file (shared uniforms live in base.frag).
 * - Cross-module reads may only reference modules earlier in the layer list
 *   (list order = GLSL concatenation order = uniform upload order).
 */
interface ShieldLayer {
    /** Stable identifier used to enable/disable this layer (e.g. "fill", "border"). */
    val layerName: String

    /** File under data/shaders/shield/ containing this layer's GLSL module. */
    val moduleFile: String

    /**
     * Neutral GLSL stub substituted for the module when this layer is disabled:
     * same function signatures, identity/no-op behavior, NO uniform declarations.
     * Lets any subset of layers compile and render for debugging.
     */
    val stubSource: String

    /** Upload this layer's uniforms. Called with the program already bound. */
    fun uploadUniforms(program: Int, viewport: ViewportAPI)

    /** Optional per-frame CPU-side state advance (seconds). */
    fun advance(deltaTime: Float) {}
}

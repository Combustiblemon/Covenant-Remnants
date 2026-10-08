package org.combustiblelemon.covenantremnants.utils.render.rupture

import com.fs.starfarer.api.Global

/**
 * The rupture's GL programs (analytic spheres, batched sprites, the Ark tube mesh), built once and
 * shared by every [RuptureRenderer] — the fleet jump's, the slipspace drive blink's, the campaign
 * jump's and the pocket portals' — and every combat. Compiled at game start from the mod plugin
 * ([warmUp]) so the first rupture or blink never hitches (ruling 2026-10-02: compile at game
 * start, share programs). Draws are sequential on the GL thread, so sharing the VAO/VBO state is
 * safe.
 */
internal object RupturePrograms {
    private var sphereShader: RuptureSphereShader? = null
    private var spriteShader: RuptureSpriteShader? = null
    private var tubeShader: ArkTubeShader? = null

    fun spheres(): RuptureSphereShader = sphereShader ?: RuptureSphereShader().also { sphereShader = it }

    fun sprites(): RuptureSpriteShader = spriteShader ?: RuptureSpriteShader().also { spriteShader = it }

    fun tube(): ArkTubeShader = tubeShader ?: ArkTubeShader().also { tubeShader = it }

    /** Compiles the programs now (game start, GL thread). Failures are logged; the renderers retry lazily. */
    fun warmUp() {
        if (sphereShader != null && spriteShader != null && tubeShader != null) return
        val start = System.nanoTime()
        try {
            spheres()
            sprites()
            tube()
            Global.getLogger(RupturePrograms::class.java)
                .info("Covenant rupture programs compiled in ${(System.nanoTime() - start) / 1_000_000} ms")
        } catch (e: Exception) {
            Global.getLogger(RupturePrograms::class.java)
                .warn("Covenant rupture program warm-up failed, will compile on first use: ${e.message}")
        }
    }
}

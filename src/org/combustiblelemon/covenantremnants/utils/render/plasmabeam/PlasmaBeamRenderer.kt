package org.combustiblelemon.covenantremnants.utils.render.plasmabeam

import com.fs.starfarer.api.combat.ViewportAPI
import org.combustiblelemon.covenantremnants.utils.render.rupture.RupturePrograms
import org.combustiblelemon.covenantremnants.utils.render.rupture.RuptureSprite
import org.combustiblelemon.covenantremnants.utils.render.rupture.RuptureView
import org.combustiblelemon.covenantremnants.utils.render.rupture.SpriteGlState
import org.combustiblelemon.covenantremnants.utils.render.rupture.SpriteMaterial

/**
 * Draws every live [PlasmaBeamLook] with the shared Reach sprite program ([RupturePrograms.sprites])
 * inside the common GL state wrap ([SpriteGlState]). Order: the two sheath layers, the pulses,
 * the centre, the muzzle and impact plasma, the flashes, the embers and sparks, the glows, the
 * impact lens flare.
 */
internal class PlasmaBeamRenderer {
    private companion object {
        const val EXPOSURE = 1f

        val ORDER = listOf(
            SpriteMaterial.PLASMA_BEAM_SHEATH_2,
            SpriteMaterial.PLASMA_BEAM_SHEATH,
            SpriteMaterial.PLASMA_BEAM_PULSE,
            SpriteMaterial.PLASMA_BEAM_CORE,
            SpriteMaterial.PLASMA_BEAM_MUZZLE_PLASMA,
            SpriteMaterial.PLASMA_BEAM_IMPACT_PLASMA,
            SpriteMaterial.TORPEDO_FLASH,
            SpriteMaterial.SPARK,
            SpriteMaterial.PLASMA_BEAM_SPARK_LONG,
            SpriteMaterial.PROJECTOR_FLARE,
            SpriteMaterial.TORPEDO_FLARE_SIMPLE
        )
    }

    private val all = ArrayList<RuptureSprite>()

    fun render(viewport: ViewportAPI, looks: Collection<PlasmaBeamLook>, time: Float) {
        all.clear()
        for (look in looks) all.addAll(look.sprites)
        if (all.isEmpty()) return
        val view = RuptureView(viewport)

        SpriteGlState.draw {
            val sprites = RupturePrograms.sprites()
            for (material in ORDER) {
                sprites.drawSprites(material, all, 0f, 0f, 1f, view, time, EXPOSURE)
            }
        }
    }
}

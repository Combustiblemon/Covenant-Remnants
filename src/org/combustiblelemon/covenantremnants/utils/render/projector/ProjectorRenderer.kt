package org.combustiblelemon.covenantremnants.utils.render.projector

import com.fs.starfarer.api.combat.ViewportAPI
import org.combustiblelemon.covenantremnants.utils.render.rupture.RupturePrograms
import org.combustiblelemon.covenantremnants.utils.render.rupture.RuptureSprite
import org.combustiblelemon.covenantremnants.utils.render.rupture.RuptureView
import org.combustiblelemon.covenantremnants.utils.render.rupture.SpriteGlState
import org.combustiblelemon.covenantremnants.utils.render.rupture.SpriteMaterial

/**
 * Draws every live [ProjectorEffect] with the shared Reach sprite program ([RupturePrograms.sprites])
 * inside the common GL state wrap ([SpriteGlState]). Order: the cloud ring and blue plasma (the
 * glassed ground), the plasma tongues, the bubble and half-dome glow, the flicker glow, the
 * charge rings and flashes, then the flares.
 */
internal class ProjectorRenderer {
    private companion object {
        const val EXPOSURE = 1f

        val ORDER = listOf(
            SpriteMaterial.PROJECTOR_CLOUD,
            SpriteMaterial.PROJECTOR_BLUE,
            SpriteMaterial.PROJECTOR_BURST,
            SpriteMaterial.PROJECTOR_BUBBLE,
            SpriteMaterial.PROJECTOR_GLOW_HALF,
            SpriteMaterial.PROJECTOR_EMP,
            SpriteMaterial.PROJECTOR_FLASH,
            SpriteMaterial.PROJECTOR_FLARE
        )
    }

    private val all = ArrayList<RuptureSprite>()

    fun render(viewport: ViewportAPI, effects: Collection<ProjectorEffect>, time: Float) {
        all.clear()
        for (effect in effects) all.addAll(effect.sprites)
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

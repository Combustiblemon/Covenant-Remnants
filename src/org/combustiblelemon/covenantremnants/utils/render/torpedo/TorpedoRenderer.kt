package org.combustiblelemon.covenantremnants.utils.render.torpedo

import com.fs.starfarer.api.combat.ViewportAPI
import org.combustiblelemon.covenantremnants.utils.render.rupture.RIBBON_STRIDE
import org.combustiblelemon.covenantremnants.utils.render.rupture.RupturePrograms
import org.combustiblelemon.covenantremnants.utils.render.rupture.RuptureSprite
import org.combustiblelemon.covenantremnants.utils.render.rupture.RuptureView
import org.combustiblelemon.covenantremnants.utils.render.rupture.SpriteGlState
import org.combustiblelemon.covenantremnants.utils.render.rupture.SpriteMaterial

/**
 * Draws every live torpedo ([TorpedoFlight]), Plasma Cannon round ([CannonFlight]) and burst
 * ([TorpedoBurst]) with the shared Reach sprite program ([RupturePrograms.sprites]) inside the
 * common GL state wrap ([SpriteGlState]). Order: trails, impact smoke and fireballs
 * (alpha-blended), impact plasma + cannon wisps, torpedo bodies, cannon glows and flares, muzzle
 * flashes, sparks, impact flares.
 */
internal class TorpedoRenderer {
    private companion object {
        // Tone-mapping exposure for Reach's HDR colours (the rupture's default).
        const val EXPOSURE = 1f

        val BURST_ORDER = listOf(
            SpriteMaterial.TORPEDO_SMOKE,
            SpriteMaterial.TORPEDO_FIREBALL
        )
        val FLASH_ORDER = listOf(
            SpriteMaterial.TORPEDO_FLASH,
            SpriteMaterial.TORPEDO_MUZZLE_ROUND,
            SpriteMaterial.TORPEDO_SPARK,
            SpriteMaterial.TORPEDO_FLARE_SIMPLE,
            SpriteMaterial.TORPEDO_FLARE_ANAMORPHIC
        )
    }

    private val burstSprites = ArrayList<RuptureSprite>()
    private val plasmaSprites = ArrayList<RuptureSprite>()
    private val bodySprites = ArrayList<RuptureSprite>()
    private var ribbon = FloatArray(0)

    fun render(
        viewport: ViewportAPI,
        flights: List<TorpedoFlight>,
        cannons: List<CannonFlight>,
        bursts: List<TorpedoBurst>,
        time: Float
    ) {
        if (flights.isEmpty() && cannons.isEmpty() && bursts.isEmpty()) return
        val view = RuptureView(viewport)

        SpriteGlState.draw {
            val sprites = RupturePrograms.sprites()

            for (flight in flights) {
                val needed = flight.maxTrailPoints * RIBBON_STRIDE
                if (ribbon.size < needed) ribbon = FloatArray(needed)
                val count = flight.writeTrail(ribbon, time)
                sprites.drawRibbon(SpriteMaterial.TORPEDO_TRAIL, ribbon, count, 1f, view, time, EXPOSURE)
            }

            burstSprites.clear()
            for (burst in bursts) burstSprites.addAll(burst.sprites)
            plasmaSprites.clear()
            plasmaSprites.addAll(burstSprites)
            for (cannon in cannons) plasmaSprites.addAll(cannon.wisps)
            bodySprites.clear()
            for (flight in flights) {
                if (!flight.flying) continue
                bodySprites.addAll(flight.body)
                bodySprites.add(flight.star)
            }
            for (cannon in cannons) {
                bodySprites.add(cannon.glowOuter)
                bodySprites.add(cannon.glowInner)
                bodySprites.add(cannon.star)
            }

            for (material in BURST_ORDER) {
                sprites.drawSprites(material, burstSprites, 0f, 0f, 1f, view, time, EXPOSURE)
            }
            sprites.drawSprites(SpriteMaterial.TORPEDO_PLASMA, plasmaSprites, 0f, 0f, 1f, view, time, EXPOSURE)
            sprites.drawSprites(SpriteMaterial.TORPEDO_BODY, bodySprites, 0f, 0f, 1f, view, time, EXPOSURE)
            sprites.drawSprites(SpriteMaterial.TORPEDO_STAR, bodySprites, 0f, 0f, 1f, view, time, EXPOSURE)
            for (material in FLASH_ORDER) {
                sprites.drawSprites(material, burstSprites, 0f, 0f, 1f, view, time, EXPOSURE)
            }
        }
    }
}

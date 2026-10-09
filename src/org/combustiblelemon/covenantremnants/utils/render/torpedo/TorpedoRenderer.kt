package org.combustiblelemon.covenantremnants.utils.render.torpedo

import com.fs.starfarer.api.combat.ViewportAPI
import org.combustiblelemon.covenantremnants.utils.render.rupture.RIBBON_STRIDE
import org.combustiblelemon.covenantremnants.utils.render.rupture.RupturePrograms
import org.combustiblelemon.covenantremnants.utils.render.rupture.RuptureSprite
import org.combustiblelemon.covenantremnants.utils.render.rupture.RuptureView
import org.combustiblelemon.covenantremnants.utils.render.rupture.SpriteGlState
import org.combustiblelemon.covenantremnants.utils.render.rupture.SpriteMaterial

/**
 * Draws every live torpedo ([TorpedoFlight]), Plasma Cannon round ([CannonFlight]), Plasma Mortar
 * round ([WraithFlight]), Plasma Bombardment round / charge / burst ([BombardFlight],
 * [BombardCharge], [ArtilleryBurst]) and burst ([TorpedoBurst]) with the shared Reach sprite program
 * ([RupturePrograms.sprites]) inside the common GL state wrap ([SpriteGlState]). Order: trails,
 * impact smoke and fireballs (alpha-blended), plasma gas, impact plasma + cannon wisps + charge
 * plasma / bolts, bodies, glows and star flares, muzzle flashes, sparks, impact flares.
 */
internal class TorpedoRenderer {
    private companion object {
        // Tone-mapping exposure for Reach's HDR colours (the rupture's default).
        const val EXPOSURE = 1f

        val BURST_ORDER = listOf(
            SpriteMaterial.TORPEDO_SMOKE,
            SpriteMaterial.TORPEDO_FIREBALL,
            SpriteMaterial.ARTILLERY_FIREBALL
        )
        // Additive plasma passes after the puffs / gas (TORPEDO_PLASMA first, as before the Bombardment).
        val PLASMA_ORDER = listOf(
            SpriteMaterial.TORPEDO_PLASMA,
            SpriteMaterial.PROJECTOR_BLUE,
            SpriteMaterial.BOMBARD_PULSE,
            SpriteMaterial.BOMBARD_CORE,
            SpriteMaterial.BOMBARD_BOLT
        )
        val FLASH_ORDER = listOf(
            SpriteMaterial.TORPEDO_FLASH,
            SpriteMaterial.PROJECTOR_FLASH,
            SpriteMaterial.BOMBARD_MUZZLE,
            SpriteMaterial.TORPEDO_MUZZLE_ROUND,
            SpriteMaterial.TORPEDO_SPARK,
            SpriteMaterial.TORPEDO_FLARE_SIMPLE,
            SpriteMaterial.TORPEDO_FLARE_ANAMORPHIC
        )
    }

    private val burstSprites = ArrayList<RuptureSprite>()
    private val plasmaSprites = ArrayList<RuptureSprite>()
    private val bodySprites = ArrayList<RuptureSprite>()
    private val puffSprites = ArrayList<RuptureSprite>()
    private val emberSprites = ArrayList<RuptureSprite>()
    private var ribbon = FloatArray(0)

    fun render(
        viewport: ViewportAPI,
        flights: List<TorpedoFlight>,
        cannons: List<CannonFlight>,
        wraiths: List<WraithFlight>,
        bombards: List<BombardFlight>,
        charges: Collection<BombardCharge>,
        artillery: List<ArtilleryBurst>,
        bursts: List<TorpedoBurst>,
        time: Float
    ) {
        if (flights.isEmpty() && cannons.isEmpty() && wraiths.isEmpty() && bombards.isEmpty() &&
            charges.isEmpty() && artillery.isEmpty() && bursts.isEmpty()
        ) return
        val view = RuptureView(viewport)

        SpriteGlState.draw {
            val sprites = RupturePrograms.sprites()

            for (flight in flights) {
                val needed = flight.maxTrailPoints * RIBBON_STRIDE
                if (ribbon.size < needed) ribbon = FloatArray(needed)
                val count = flight.writeTrail(ribbon, time)
                sprites.drawRibbon(SpriteMaterial.TORPEDO_TRAIL, ribbon, count, 1f, view, time, EXPOSURE)
            }
            for (wraith in wraiths) {
                val needed = wraith.maxTrailPoints * RIBBON_STRIDE
                if (ribbon.size < needed) ribbon = FloatArray(needed)
                val count = wraith.writeTrail(ribbon)
                sprites.drawRibbon(SpriteMaterial.WRAITH_CONTRAIL, ribbon, count, 1f, view, time, EXPOSURE)
            }
            for (bombard in bombards) {
                val needed = bombard.maxTrailPoints * RIBBON_STRIDE
                if (ribbon.size < needed) ribbon = FloatArray(needed)
                var count = bombard.writeWispy(ribbon, time)
                sprites.drawRibbon(SpriteMaterial.BOMBARD_WISPY, ribbon, count, 1f, view, time, EXPOSURE)
                count = bombard.writeTrail(ribbon, time)
                sprites.drawRibbon(SpriteMaterial.BOMBARD_CONTRAIL, ribbon, count, 1f, view, time, EXPOSURE)
            }

            // Every material filters its own sprites, so the Bombardment's mixed lists join the bursts.
            burstSprites.clear()
            for (burst in bursts) burstSprites.addAll(burst.sprites)
            for (burst in artillery) burstSprites.addAll(burst.sprites)
            for (charge in charges) burstSprites.addAll(charge.sprites)
            for (bombard in bombards) bombard.addSprites(burstSprites)
            plasmaSprites.clear()
            plasmaSprites.addAll(burstSprites)
            for (cannon in cannons) plasmaSprites.addAll(cannon.wisps)
            puffSprites.clear()
            emberSprites.clear()
            for (wraith in wraiths) {
                plasmaSprites.addAll(wraith.plasma)
                puffSprites.addAll(wraith.puffs)
                emberSprites.addAll(wraith.embers)
            }
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
            for (wraith in wraiths) {
                if (!wraith.flying) continue
                bodySprites.add(wraith.light)
                bodySprites.addAll(wraith.body)
                bodySprites.add(wraith.star)
            }

            for (material in BURST_ORDER) {
                sprites.drawSprites(material, burstSprites, 0f, 0f, 1f, view, time, EXPOSURE)
            }
            sprites.drawSprites(SpriteMaterial.WRAITH_PLASMA_TRAIL, puffSprites, 0f, 0f, 1f, view, time, EXPOSURE)
            sprites.drawSprites(SpriteMaterial.BOMBARD_GAS, burstSprites, 0f, 0f, 1f, view, time, EXPOSURE)
            for (material in PLASMA_ORDER) {
                sprites.drawSprites(material, plasmaSprites, 0f, 0f, 1f, view, time, EXPOSURE)
            }
            bodySprites.addAll(burstSprites)
            sprites.drawSprites(SpriteMaterial.TORPEDO_BODY, bodySprites, 0f, 0f, 1f, view, time, EXPOSURE)
            sprites.drawSprites(SpriteMaterial.TORPEDO_STAR, bodySprites, 0f, 0f, 1f, view, time, EXPOSURE)
            sprites.drawSprites(SpriteMaterial.SPARK, emberSprites, 0f, 0f, 1f, view, time, EXPOSURE)
            for (material in FLASH_ORDER) {
                sprites.drawSprites(material, burstSprites, 0f, 0f, 1f, view, time, EXPOSURE)
            }
        }
    }
}

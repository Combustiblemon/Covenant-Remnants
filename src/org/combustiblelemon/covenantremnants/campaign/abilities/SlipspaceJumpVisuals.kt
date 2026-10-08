package org.combustiblelemon.covenantremnants.campaign.abilities

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.LocationAPI
import com.fs.starfarer.api.combat.ViewportAPI
import org.combustiblelemon.covenantremnants.utils.render.rupture.RuptureEffect
import org.combustiblelemon.covenantremnants.utils.render.rupture.RuptureRenderer
import org.lwjgl.util.vector.Vector2f

/**
 * Campaign-layer owner of the Reach rupture effects for the Slipspace Jump ability: one shared
 * [RuptureRenderer], each effect tagged with the location it belongs to so a rupture left behind
 * in the origin system never shows in the pocket. Transient — the ability recreates it after a
 * load. Also plays the charge hum (looped, keyed by the effect) and the one-shot sounds.
 */
class SlipspaceJumpVisuals {
    private class Entry(val effect: RuptureEffect, val location: LocationAPI)

    val renderer = RuptureRenderer()
    private val entries = ArrayList<Entry>()

    fun add(effect: RuptureEffect, location: LocationAPI): RuptureEffect {
        entries.add(Entry(effect, location))
        return effect
    }

    val isEmpty: Boolean get() = entries.isEmpty()

    fun advance(amount: Float) {
        if (amount <= 0f) return
        renderer.advance(amount)
        val it = entries.iterator()
        while (it.hasNext()) {
            val e = it.next()
            e.effect.advance(amount)
            if (e.effect.isDone) it.remove()
        }
    }

    /** Draws the effects that belong to [location] in [pass]; the viewport alpha (screen fade) dims them. */
    fun render(viewport: ViewportAPI, location: LocationAPI?, pass: RuptureRenderer.Pass = RuptureRenderer.Pass.FULL) {
        if (location == null || entries.isEmpty()) return
        renderer.effects.clear()
        for (e in entries) if (e.location === location) renderer.effects.add(e.effect)
        if (renderer.effects.isEmpty()) return
        renderer.alphaMult = viewport.alphaMult
        renderer.pass = pass
        renderer.render(viewport)
    }

    fun abortAll() {
        for (e in entries) e.effect.abort()
    }

    /** Charge hum: pitch 0.7 → 1.25 and volume 0.35 → 1 with the charge (combat session values). */
    fun playChargeHum(effect: RuptureEffect, level: Float, volumeMult: Float = 1f) {
        val pitch = 0.7f + 0.55f * level
        val volume = (0.35f + 0.65f * level) * volumeMult
        Global.getSoundPlayer().playLoop(SlipspaceJumpRules.SOUND_CHARGE_LOOP, effect, pitch, volume, effect.center, Vector2f())
    }

    fun playOneShot(id: String, at: Vector2f) {
        Global.getSoundPlayer().playSound(id, 1f, 1f, at, Vector2f())
    }

    fun cleanup() {
        entries.clear()
        renderer.effects.clear()
        renderer.cleanup()
    }
}

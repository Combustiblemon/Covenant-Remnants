package org.combustiblelemon.covenantremnants.weapons

import com.fs.starfarer.api.combat.CombatEngineAPI
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin
import com.fs.starfarer.api.combat.WeaponAPI
import org.combustiblelemon.covenantremnants.plugins.TorpedoRenderPlugin

/**
 * Every-frame hook of the Plasma Mortar (`everyFrameEffect` in its .wpn). Only makes sure the
 * combat's [TorpedoRenderPlugin] exists while a mortar is on the field: the plugin also picks the
 * mortar rounds up from the engine's projectile list, so the Reach Wraith round is drawn even when
 * the on-fire hook does not register a round (2026-10-09: the rounds were never drawn in-game).
 */
class PlasmaMortarEffect : EveryFrameWeaponEffectPlugin {
    override fun advance(amount: Float, engine: CombatEngineAPI, weapon: WeaponAPI) {
        TorpedoRenderPlugin.forEngine(engine)
    }
}

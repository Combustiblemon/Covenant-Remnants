package org.combustiblelemon.covenantremnants.weapons

import com.fs.starfarer.api.combat.CombatEngineAPI
import com.fs.starfarer.api.combat.DamagingProjectileAPI
import com.fs.starfarer.api.combat.OnFireEffectPlugin
import com.fs.starfarer.api.combat.WeaponAPI
import org.combustiblelemon.covenantremnants.plugins.TorpedoRenderPlugin

/**
 * On-fire hook of the Plasma Mortar (`onFireEffect` in its .wpn): hands every round to the combat's
 * [TorpedoRenderPlugin], which draws the Halo: Reach Wraith mortar round over the invisible engine
 * round ([org.combustiblelemon.covenantremnants.utils.render.torpedo.WraithFlight]).
 */
class PlasmaMortarOnFire : OnFireEffectPlugin {
    override fun onFire(projectile: DamagingProjectileAPI, weapon: WeaponAPI, engine: CombatEngineAPI) {
        TorpedoRenderPlugin.forEngine(engine).trackWraithRound(projectile, PlasmaMortarOnHit.ROUND_UNIT)
    }
}

package org.combustiblelemon.covenantremnants.weapons

import com.fs.starfarer.api.combat.CombatEngineAPI
import com.fs.starfarer.api.combat.DamagingProjectileAPI
import com.fs.starfarer.api.combat.OnFireEffectPlugin
import com.fs.starfarer.api.combat.WeaponAPI
import org.combustiblelemon.covenantremnants.plugins.TorpedoRenderPlugin

/**
 * On-fire hook of the Plasma Cannon (`onFireEffect` in its .wpn): hands every round to the
 * combat's [TorpedoRenderPlugin], which draws the energy aura around the engine-drawn ball
 * (glow spheres, star flare, orbiting plasma wisps) until the round hits or expires.
 */
class PlasmaCannonOnFire : OnFireEffectPlugin {
    override fun onFire(projectile: DamagingProjectileAPI, weapon: WeaponAPI, engine: CombatEngineAPI) {
        TorpedoRenderPlugin.forEngine(engine).trackCannonRound(projectile, PlasmaCannonOnHit.UNIT)
    }
}

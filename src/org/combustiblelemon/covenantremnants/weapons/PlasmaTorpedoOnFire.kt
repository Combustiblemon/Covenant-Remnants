package org.combustiblelemon.covenantremnants.weapons

import com.fs.starfarer.api.combat.CombatEngineAPI
import com.fs.starfarer.api.combat.DamagingProjectileAPI
import com.fs.starfarer.api.combat.MissileAPI
import com.fs.starfarer.api.combat.OnFireEffectPlugin
import com.fs.starfarer.api.combat.WeaponAPI
import org.combustiblelemon.covenantremnants.plugins.TorpedoRenderPlugin

/**
 * On-fire hook of the three plasma torpedoes (`onFireEffect` in their .wpn): hands every torpedo
 * to the combat's [TorpedoRenderPlugin], which draws the Halo: Reach space Phantom torpedo look
 * (muzzle flash, plasma body, flare, trail, impact) scaled by the weapon size and plays its sounds.
 */
class PlasmaTorpedoOnFire : OnFireEffectPlugin {
    override fun onFire(projectile: DamagingProjectileAPI, weapon: WeaponAPI, engine: CombatEngineAPI) {
        val missile = projectile as? MissileAPI ?: return
        TorpedoRenderPlugin.forEngine(engine).launch(missile, weapon)
    }
}

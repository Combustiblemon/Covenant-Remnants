package org.combustiblelemon.covenantremnants.weapons

import com.fs.starfarer.api.combat.CombatEngineAPI
import com.fs.starfarer.api.combat.DamagingProjectileAPI
import com.fs.starfarer.api.combat.MissileAPI
import com.fs.starfarer.api.combat.OnFireEffectPlugin
import com.fs.starfarer.api.combat.WeaponAPI
import org.combustiblelemon.covenantremnants.plugins.TorpedoRenderPlugin

/**
 * On-fire hook of the three plasma torpedoes (`onFireEffect` in their .proj, where vanilla keeps
 * every missile's on-fire hook): hands every torpedo to the combat's [TorpedoRenderPlugin], which
 * draws the Halo: Reach space Phantom torpedo look (muzzle flash, plasma body, flare, trail, impact)
 * scaled by the launcher size and plays its sounds. The launcher's [PlasmaTorpedoEffect] and the
 * plugin's missile-list scan register torpedoes too; the plugin skips duplicates.
 */
class PlasmaTorpedoOnFire : OnFireEffectPlugin {
    override fun onFire(projectile: DamagingProjectileAPI, weapon: WeaponAPI?, engine: CombatEngineAPI) {
        val missile = projectile as? MissileAPI ?: return
        TorpedoRenderPlugin.forEngine(engine).launch(missile, weapon, "the .proj onFireEffect")
    }
}

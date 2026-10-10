package org.combustiblelemon.covenantremnants.weapons

import com.fs.starfarer.api.combat.CombatEngineAPI
import com.fs.starfarer.api.combat.DamagingProjectileAPI
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin
import com.fs.starfarer.api.combat.MissileAPI
import com.fs.starfarer.api.combat.OnFireEffectPlugin
import com.fs.starfarer.api.combat.WeaponAPI
import org.combustiblelemon.covenantremnants.plugins.TorpedoRenderPlugin

/**
 * Every-frame hook of the three plasma torpedo launchers (`everyFrameEffect` in their .wpn). Makes
 * sure the combat's [TorpedoRenderPlugin] exists while a launcher is on the field (it also picks
 * torpedoes up from the engine's missile list), and registers each torpedo on fire: the engine
 * calls `onFire` on an every-frame effect that is also an [OnFireEffectPlugin] (obf weapon fire, the
 * vanilla "handled in everyframe in .wpn file" pattern). Duplicates of [PlasmaTorpedoOnFire] are
 * skipped by the plugin.
 */
class PlasmaTorpedoEffect : EveryFrameWeaponEffectPlugin, OnFireEffectPlugin {
    override fun advance(amount: Float, engine: CombatEngineAPI, weapon: WeaponAPI) {
        TorpedoRenderPlugin.forEngine(engine)
    }

    override fun onFire(projectile: DamagingProjectileAPI, weapon: WeaponAPI?, engine: CombatEngineAPI) {
        val missile = projectile as? MissileAPI ?: return
        TorpedoRenderPlugin.forEngine(engine).launch(missile, weapon, "the .wpn everyFrameEffect")
    }
}

package org.combustiblelemon.covenantremnants.weapons

import com.fs.starfarer.api.combat.CombatEngineAPI
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin
import com.fs.starfarer.api.combat.WeaponAPI
import org.combustiblelemon.covenantremnants.plugins.EnergyProjectorRenderPlugin

/**
 * Energy Projector, every frame: locks the turret while it fires, and registers the weapon with
 * [EnergyProjectorRenderPlugin] (the Halo: Reach glassing look over the engine beam).
 */
class EnergyProjectorEffect : EveryFrameWeaponEffectPlugin {
    private var turnRate = -1f
    override fun advance(amount: Float, engine: CombatEngineAPI, weapon: WeaponAPI) {
        if (engine.isPaused) return
        if (turnRate == -1f) {
            turnRate = weapon.turnRate
        }
        if (weapon.isFiring) {
            weapon.setTurnRateOverride(0f)
        } else {
            weapon.setTurnRateOverride(turnRate)
        }
        EnergyProjectorRenderPlugin.forEngine(engine).register(weapon)
    }
}

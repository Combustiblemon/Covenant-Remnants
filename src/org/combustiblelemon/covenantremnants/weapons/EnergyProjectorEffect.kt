package org.combustiblelemon.covenantremnants.weapons

import com.fs.starfarer.api.combat.CombatEngineAPI
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin
import com.fs.starfarer.api.combat.WeaponAPI

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
    }
}

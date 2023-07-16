package org.Combustiblelemon.CovenantRemnants.weapons;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;

import java.util.Random;

public class EnergyProjectorEffect implements EveryFrameWeaponEffectPlugin {

    private float turnRate = -1f;

    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        if (engine.isPaused()) return;

        if (turnRate == -1){
            turnRate = weapon.getTurnRate();
        }

        if (weapon.isFiring()) {
            weapon.setTurnRateOverride(0f);
        } else {
            weapon.setTurnRateOverride(turnRate);
        }
    }
}

package org.Combustiblelemon.CovenantRemnants.ai;

import com.fs.starfarer.api.util.Misc;
import org.Combustiblelemon.CovenantRemnants.utils.Utils;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAIScript;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.util.IntervalUtil;


/**
 * Sample ship system AI.
 *
 * THIS CODE IS NOT ACTUALLY USED, AND IS PROVIDED AS AN EXAMPLE ONLY.
 *
 * To enable it, uncomment the relevant lines in fastmissileracks.system.
 *
 * @author Alex Mosolov
 *
 * Copyright 2012 Fractal Softworks, LLC
 */
public class PlasmaBarrageAI implements ShipSystemAIScript {

    protected static float RANGE = 1500f;

    private ShipAPI ship;
    private CombatEngineAPI engine;
    private ShipwideAIFlags flags;
    private ShipSystemAPI system;

    private IntervalUtil tracker = new IntervalUtil(0.5f, 1f);

    public void init(ShipAPI ship, ShipSystemAPI system, ShipwideAIFlags flags, CombatEngineAPI engine) {
        this.ship = ship;
        this.flags = flags;
        this.engine = engine;
        this.system = system;
    }


    @Override
    public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
        if(target == null){
            return;
        }

        boolean targetInRange = Misc.getDistance(ship.getLocation(),target.getLocation()) < RANGE;

        if(targetInRange && (!Utils.isShieldFacingShip(ship, target) || target.getShield().isOff())){
            ship.useSystem();
        }

    }


}


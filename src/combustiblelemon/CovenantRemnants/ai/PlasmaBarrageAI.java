package combustiblelemon.CovenantRemnants.ai;

import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.LazyLib;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAIScript;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.util.IntervalUtil;
import org.magiclib.util.MagicTargeting;
import sun.management.LazyCompositeData;


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
        System.out.println("Target shield facing: " + target.getShield().getFacing() + ", ship facing: " + ship.getFacing());
        System.out.println("Target shield arc: " + target.getShield().getArc());
        System.out.println("Target shield facing -180: " + (target.getShield().getFacing() + 180.0) );



        if(targetInRange && (isShieldFacingShip(target) || target.getShield().isOff())){
            ship.useSystem();
        }

    }

    private boolean isShieldFacingShip(ShipAPI target){
        float targetShieldFacing = target.getShield().getFacing();
        if(targetShieldFacing > 360){
            targetShieldFacing -= 360f;
        }

        float targetShieldArc = target.getShield().getArc();

        float shipFacing = ship.getFacing();

        float targetShieldFacingNormalized = calculateTargetShieldFacing(targetShieldFacing);
        float targetLeft = Math.abs(targetShieldFacingNormalized - targetShieldArc);
        float targetRight = targetShieldFacingNormalized + targetShieldArc;

        if(targetRight > 360) {
            targetRight -= 360;
        }


        if(shipFacing < targetLeft && shipFacing > targetRight) {
            return true;
        }

        return false;
    }

    private float calculateTargetShieldFacing(float shieldFacing){
        float newFacing = shieldFacing + 180f;

        if(newFacing > 360){
            newFacing = newFacing - 360f;
        }

        return newFacing;
    }
}


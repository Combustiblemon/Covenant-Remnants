package org.Combustiblelemon.CovenantRemnants.utils;

import com.fs.starfarer.api.combat.ShipAPI;
import com.sun.javafx.geom.CubicCurve2D;
import org.jetbrains.annotations.NotNull;
import org.lazywizard.lazylib.LazyLib;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;
import static org.lwjgl.opengl.GL11.glEnd;
import static org.lwjgl.opengl.GL11.glVertex2f;
import org.lwjgl.opengl.GL11;

import java.util.List;

public class Utils {
    /**
     * Given the <b><i>target</i></b> and <b><i>ship</i></b> will calculate if the target shields are facing the <b><i>ship</i></b>
     * @param ship the ship's ship
     * @param target the target ship
     * @return boolean
     */
    public static boolean isShieldFacingShip(@NotNull ShipAPI ship, ShipAPI target){
        float targetShieldFacing = target.getShield().getFacing() + 180f;
        if(targetShieldFacing > 360){
            targetShieldFacing -= 360f;
        }

        float targetShieldArc = target.getShield().getArc();
        float controllerShipFacing = ship.getFacing();

        float targetLeft = Math.abs(targetShieldFacing - targetShieldArc);
        float targetRight = targetShieldFacing + targetShieldArc;

        if(targetRight > 360) {
            targetRight -= 360;
        }

        return controllerShipFacing < targetLeft && controllerShipFacing > targetRight;
    }

    final static float Pi = 3.14f;

    public static Vector2f getLocationInFrontOfShip(ShipAPI ship, float units){
        Vector2f calculatedPos = new Vector2f();
        Vector2f shipLocation = ship.getLocation();
        float facing = ship.getFacing();

        calculatedPos.x = (float) (shipLocation.x - units * Math.cos(2.0*Pi*(180.0+facing)/360.0));
        calculatedPos.y = (float) (shipLocation.y - units * Math.sin(2.0*Pi*(180.0+facing)/360.0));

        return calculatedPos;
    }

    public static Vector2f getVelocityVectorInFacing(ShipAPI ship, float units){
        Vector2f velocity = new Vector2f();
        Vector2f shipLocation = ship.getLocation();
        float facing = ship.getFacing();

        velocity.x = (float) ( -units * Math.cos(2.0*Pi*(180.0+facing)/360.0));
        velocity.y = (float) ( -units * Math.sin(2.0*Pi*(180.0+facing)/360.0));

        return velocity;

    }

    public static float normalize(double value, double min, double max) {
        return (float) ((value - min) / (max - min));
    }



    public static void  logText(String text){
        System.out.println(text);
    }
}

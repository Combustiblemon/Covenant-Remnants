package org.Combustiblelemon.CovenantRemnants.utils;

import com.fs.starfarer.api.combat.ShipAPI;

public class Utils {
    /**
     * Given the <b><i>target</i></b> and <b><i>controller</i></b> ships will calculate if the target shields are facing the <b><i>controller</i></b> ship
     * @param controller the controller's ship
     * @param target the target ship
     * @return
     */
    public static boolean isShieldFacingShip(ShipAPI controller, ShipAPI target){
        float targetShieldFacing = target.getShield().getFacing() + 180f;
        if(targetShieldFacing > 360){
            targetShieldFacing -= 360f;
        }

        float targetShieldArc = target.getShield().getArc();

        float controllerShipFacing = controller.getFacing();

        float targetLeft = Math.abs(targetShieldFacing - targetShieldArc);
        float targetRight = targetShieldFacing + targetShieldArc;

        if(targetRight > 360) {
            targetRight -= 360;
        }

        if(controllerShipFacing < targetLeft && controllerShipFacing > targetRight) {
            return true;
        }

        return false;
    }

    public static void  logText(String text){
        System.out.println(text);
    }
}

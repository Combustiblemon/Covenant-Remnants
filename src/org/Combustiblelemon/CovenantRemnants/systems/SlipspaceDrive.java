package org.Combustiblelemon.CovenantRemnants.systems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import org.Combustiblelemon.CovenantRemnants.plugins.SlipspaceDriveRenderPlugin;
import org.Combustiblelemon.CovenantRemnants.utils.CubicCurve2D;
import org.Combustiblelemon.CovenantRemnants.utils.Utils;
import org.lwjgl.util.vector.Vector2f;

public class SlipspaceDrive extends BaseShipSystemScript {
    Vector2f mouseTarget;
    float facing;
    private SlipspaceDriveRenderPlugin renderPlugin;
    final private CubicCurve2D cubicFunction = new CubicCurve2D(1,0,0,1);

//    IN
    boolean isIn = false;

//    ACTIVE
    boolean isActive = false;
    float launchStartTime;
    final float LAUNCH_SPEED = 1500;
    final float PORTAL_DURATION = 14;

    float PORTAL_DISTANCE = 1000;
    float PORTAL_RADIUS = 400;

//    OUT
    boolean isOut = false;



    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        ShipAPI ship = null;
        if (stats.getEntity() instanceof ShipAPI) {
            ship = (ShipAPI) stats.getEntity();
        } else {
            return;
        }

        float currentTime = Global.getCombatEngine().getTotalElapsedTime(false);

        if(renderPlugin == null){
            renderPlugin = new SlipspaceDriveRenderPlugin(ship, PORTAL_DURATION, PORTAL_DISTANCE, PORTAL_RADIUS);
            Global.getCombatEngine().addLayeredRenderingPlugin(renderPlugin);
        }


        if (state == State.IN && !isIn) {
            isIn = true;

            mouseTarget = new Vector2f(ship.getMouseTarget());
            facing = ship.getFacing();

            ship.setAngularVelocity(0);
            ship.getVelocity().set(new Vector2f(0,0));
            ship.getFluxTracker().setCurrFlux(0);

            stats.getCombatEngineRepairTimeMult().modifyMult(id, 0);
            stats.getMaxSpeed().modifyFlat(id, 300);
        }

        if (state == State.ACTIVE) {
            if (!isActive){
                isActive = true;
                launchStartTime = currentTime;
            }else{
                float number = Utils.normalize(currentTime - launchStartTime, 0, 4);

                ship.getVelocity().set(Utils.getVelocityVectorInFacing(ship, LAUNCH_SPEED * cubicFunction.getTime(number)));
            }
        }

        if (state == State.OUT ){
            if (!isOut){

            isOut = true;

            stats.getMaxSpeed().unmodify(id);
            stats.getAcceleration().unmodify(id);
            stats.getCombatEngineRepairTimeMult().unmodify(id);
            ship.getLocation().set(mouseTarget);


            }

            ship.getVelocity().set(Utils.getVelocityVectorInFacing(ship, 0));
        }
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id) {
        stats.getMaxSpeed().unmodify(id);
        stats.getAcceleration().unmodify(id);
        isActive=false;
        isIn=false;
        isOut=false;
    }


}

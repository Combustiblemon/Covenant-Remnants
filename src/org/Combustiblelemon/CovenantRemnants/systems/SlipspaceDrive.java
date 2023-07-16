package org.Combustiblelemon.CovenantRemnants.systems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import org.Combustiblelemon.CovenantRemnants.plugins.SlipspaceDriveRenderPlugin;
import org.Combustiblelemon.CovenantRemnants.utils.CubicCurve2D;
import org.Combustiblelemon.CovenantRemnants.utils.Utils;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

public class SlipspaceDrive extends BaseShipSystemScript {
    Vector2f portalExit;
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
    float PORTAL_RADIUS = 500;
    float SYSTEM_USE_DISTANCE = 1000;

//    OUT
    boolean isOut = false;

    public boolean isUsable(ShipSystemAPI system, ShipAPI ship) {

        return !system.isActive() &&
                // Distance to exit is larger than SYSTEM_USE_DISTANCE
                MathUtils.getDistance(ship, ship.getMouseTarget()) > SYSTEM_USE_DISTANCE &&
                // No ships within the exit radius
                CombatUtils.getShipsWithinRange(ship.getMouseTarget(), ship.getShield().getRadius()).size() == 0;
    }

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

            facing = ship.getFacing();
            portalExit = new Vector2f(ship.getMouseTarget());

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
                ship.getLocation().set(portalExit);

                ship.getVelocity().set(Utils.getVelocityVectorInFacing(ship, 15));

            }
            ship.setJitter(this, Color.white, ship.getSystem().getEffectLevel(), 4, 40f);

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

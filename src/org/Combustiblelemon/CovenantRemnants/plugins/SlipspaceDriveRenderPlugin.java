package org.Combustiblelemon.CovenantRemnants.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.Misc;
import org.Combustiblelemon.CovenantRemnants.utils.CubicCurve2D;
import org.Combustiblelemon.CovenantRemnants.utils.Utils;
import org.lazywizard.lazylib.opengl.DrawUtils;
import org.lwjgl.opengl.GL11.*;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.EnumSet;

import static org.lwjgl.opengl.GL11.*;

public class SlipspaceDriveRenderPlugin implements CombatLayeredRenderingPlugin {
    protected CombatEngineLayers layer = CombatEngineLayers.JUST_BELOW_WIDGETS;
    protected CombatEntityAPI entity;

    final private ShipAPI attachedTo;

	private Vector2f portalLocation;
	private float facing;
	private float startTime = 0;
    final private float portalDuration;
    private boolean isExpired;
    private boolean isPortalActive = false;
    float portalDistance;
    float portalRadius;

    final private CubicCurve2D cubicFunction = new CubicCurve2D(.77,.17,.82,.41);

    public SlipspaceDriveRenderPlugin(ShipAPI attachedTo, float portalDuration, float portalDistance, float portalRadius) {
	    this.portalDuration = portalDuration;
        this.attachedTo = attachedTo;
        this.portalDistance = portalDistance;
        this.portalRadius = portalRadius;
    }


    public void advance(float amount) {

    }

    public void cleanup() {

    }

    public EnumSet<CombatEngineLayers> getActiveLayers() {
        return EnumSet.of(layer);
    }

    public float getRenderRadius() {
        return Float.MAX_VALUE;
    }

    public void init(CombatEntityAPI entity) {
        this.entity = entity;
    }

    public boolean isExpired() {

        return false;
    }

    public void render(CombatEngineLayers layer, ViewportAPI viewport) {
        if(!attachedTo.getSystem().isActive()){
            return;
        }

        float currentTime = Global.getCombatEngine().getTotalElapsedTime(false);

        // RENDER PORTAL

        if(!isPortalActive) {
            startTime = currentTime;
            portalLocation = Utils.getLocationInFrontOfShip(attachedTo, portalDistance);
            isPortalActive = true;
        } else if (startTime + portalDuration < currentTime){
            isPortalActive = false;
            startTime = 0;
        }


        if(isPortalActive){
            float effectLevel = attachedTo.getSystem().getEffectLevel();


            glDisable(GL_TEXTURE_2D);

            glEnable(GL_BLEND);
            glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
//            glAccum(GL_ACCUM, 1f);

            Misc.setColor(Color.white);
            Utils.drawDonut(portalLocation, portalRadius * cubicFunction.getTime(effectLevel), 50, 0.05f);

            Misc.setColor(new Color(139,130,251));
            Utils.drawDonut(portalLocation, (portalRadius * 0.96f) * cubicFunction.getTime(effectLevel), 50, 0.04f);



            Misc.setColor(new Color(60,14,70));
            Utils.drawDonut(portalLocation, (portalRadius * 0.92f) * cubicFunction.getTime(effectLevel), 50, 1f);
            glDisable(GL_BLEND);
//            DrawUtils.drawCircle(portalLocation.x,portalLocation.y, (portalRadius * 0.88f) * cubicFunction.getTime(effectLevel), 50, true);

            glEnable(GL_TEXTURE_2D);
        }

    }

//    private void createCircle()
}

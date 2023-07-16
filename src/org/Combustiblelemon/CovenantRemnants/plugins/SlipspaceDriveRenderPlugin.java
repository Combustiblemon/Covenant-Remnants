package org.Combustiblelemon.CovenantRemnants.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.terrain.RingRenderer;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.util.Misc;
import org.Combustiblelemon.CovenantRemnants.utils.CubicCurve2D;
import org.Combustiblelemon.CovenantRemnants.utils.Utils;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.opengl.DrawUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.EnumSet;
import java.util.concurrent.ThreadLocalRandom;

import static org.lwjgl.opengl.GL11.*;

public class SlipspaceDriveRenderPlugin implements CombatLayeredRenderingPlugin {
    protected CombatEngineLayers layer = CombatEngineLayers.JUST_BELOW_WIDGETS;
    protected CombatEntityAPI entity;
    final private ShipAPI attachedTo;
	private Vector2f portalLocation;
	private float startTime = 0;
    final private float portalDuration;
    final private float startUpTime = 3;
    private boolean isPortalActive = false;
    float portalDistance;
    float portalRadius;

    final private CubicCurve2D cubicFunction = new CubicCurve2D(.01,.75,0,.99); //.02,.65,1,-0.91
    final private RingRenderer outerRingRenderer = new RingRenderer("covenant_remnants", "portal_outer_loop");
    final private RingRenderer innerRingRenderer = new RingRenderer("covenant_remnants", "portal_inner_loop");
    final private SpriteAPI noiseSprite = Global.getSettings().getSprite("covenant_remnants", "portal_noise_long");
    final Vector2f[] texturePoints = MathUtils.getPointsAlongCircumference(new Vector2f(0.5F, 0.5F), 0.5F, 40, 0).toArray(new Vector2f[0]);
    private float effectLevelExtra = 0f;
    private int counter = 0;
    private boolean reverse = false;
    private float reverseLevel = 0;

    public SlipspaceDriveRenderPlugin(ShipAPI attachedTo, float portalDuration, float portalDistance, float portalRadius) {
	    this.portalDuration = portalDuration;
        this.attachedTo = attachedTo;
        this.portalDistance = portalDistance;
        this.portalRadius = portalRadius;
    }

    public void render(CombatEngineLayers layer, ViewportAPI viewport) {
        if (!attachedTo.getSystem().isActive()) {
            return;
        }

        float currentTime = Global.getCombatEngine().getTotalElapsedTime(false);

        // RENDER PORTAL

        if (!isPortalActive) {
            startTime = currentTime;
            portalLocation = Utils.getLocationInFrontOfShip(attachedTo, portalDistance);
            isPortalActive = true;
        } else if (startTime + portalDuration < currentTime) {
            isPortalActive = false;
            startTime = 0;
        }


        if (isPortalActive) {
            float effectLevel = attachedTo.getSystem().getEffectLevel();

            // portal startup
            if (startTime + startUpTime > currentTime) {
                float radius =  60f
                        * (effectLevel - reverseLevel);

                glDisable(GL_TEXTURE_2D);
                Misc.setColor(Color.white);
                DrawUtils.drawCircle(portalLocation.x, portalLocation.y, radius, 20, true);
                glEnable(GL_TEXTURE_2D);

                if(counter >9){
                    counter = 0;
                    Global.getCombatEngine().spawnEmpArcVisual(portalLocation, null,
                            MathUtils.getRandomPointInCircle(portalLocation, radius * 1.1f),
                            null, 5f, new Color(139, 130, 251), Color.white);
                } else {
                    counter++;
                }

                if(!reverse && startTime + startUpTime / 2 > currentTime ){
                    reverse = true;
                    reverseLevel = effectLevel;
                }


            } else {
                if (effectLevelExtra == 0){
                    effectLevelExtra = effectLevel;
                }

                renderPortal(Math.min(Math.max(effectLevel - effectLevelExtra, 0) * 3, 1));
            }
        }
    }

    private void renderPortal(float radiusMultiplier) {
        float radius = portalRadius * cubicFunction.getTime(Math.min(radiusMultiplier, 1));
        float innerRadius = radius * 0.95f;
        float noiseRadius = Math.max(innerRadius - 10, 0);

        // Render the outer white ring
        outerRingRenderer.render(portalLocation, radius, radius* 0.9f, Color.white, false, 1, 1);
        // Render the inner purple-black ring
        innerRingRenderer.render(portalLocation, innerRadius, radius * 0.88f, Color.white, false, 1, 1);

        // Fill the inner ring with color
        glDisable(GL_TEXTURE_2D);
        Misc.setColor(new Color(20,23,34));
        DrawUtils.drawCircle(portalLocation.x, portalLocation.y, radius * 0.9f , 50, true);
        glEnable(GL_TEXTURE_2D);

        // Get points so we can map the noise texture
        Vector2f[] points = MathUtils.getPointsAlongCircumference(portalLocation, noiseRadius, 40, 0).toArray(new Vector2f[0]);

        noiseSprite.bindTexture();
        Misc.setColor(new Color(139,130,251));
        glBegin(GL_POLYGON);

        for (int i = 0; i < points.length; i++){
            // use stencil mask?
            glTexCoord2f(texturePoints[i].x, texturePoints[i].y);
            glVertex2f(points[i].x, points[i].y);

            // Walk the texture
            if (!Global.getCombatEngine().isPaused()){
                texturePoints[i] = new Vector2f((float) (texturePoints[i].x + 0.004),texturePoints[i].y);
            }
        }

        glEnd();
    }


        // if performance is an issue look at the code below

        // stencil
        //reset the whole stencil mask to 0s
//        glClearStencil(0);
//        glStencilMask(0xff);
//        //set everything to 0
//        glClear(GL_STENCIL_BUFFER_BIT);
//
//        //disable drawing colour, enable stencil testing
//        glColorMask(false, false, false, false); //disable colour
//        glEnable(GL_STENCIL_TEST); //enable stencil
//
//        // ... here you render the part of the scene you want masked, this may be a simple triangle or square, or for example a monitor on a computer in your spaceship ...
//        //begin masking
//        //put 1s where I want to draw
//        glStencilFunc(GL_ALWAYS, 1, 0xff); // Do not test the current value in the stencil buffer, always accept any value on there for drawing
//        glStencilMask(0xff);
//        glStencilOp(GL_REPLACE, GL_REPLACE, GL_REPLACE); // Make every test succeed
//
//        <draw a quad that dictates you want the boundaries of the panel to be>
//
//        glStencilOp(GL_KEEP, GL_KEEP, GL_KEEP); // Make sure you will no longer (over)write stencil values, even if any test succeeds
//        glColorMask(true, true, true, true); // Make sure we draw on the backbuffer again.
//        glStencilFunc(GL_EQUAL, 1, 0xFF); // Now we will only draw pixels where the corresponding stencil buffer value equals 1
//
//        <draw the lines>
//
//                glDisable(GL_STENCIL_TEST);



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
}

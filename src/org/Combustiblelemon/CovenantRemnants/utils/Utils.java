package org.Combustiblelemon.CovenantRemnants.utils;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.util.Misc;
import org.jetbrains.annotations.NotNull;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.BufferUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;

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
        float facing = ship.getFacing();

        velocity.x = (float) ( -units * Math.cos(2.0*Pi*(180.0+facing)/360.0));
        velocity.y = (float) ( -units * Math.sin(2.0*Pi*(180.0+facing)/360.0));

        return velocity;

    }

    public static float normalize(double value, double min, double max) {
        return (float) ((value - min) / (max - min));
    }

    /**
     *
     * @param center A Vector2f that holds the center point of the donut
     * @param radius The outer radius of the donut
     * @param numSegments number of segments
     * @param width the width of the donut (percent of the radius)
     */
    public static void drawDonut(Vector2f center, float radius, int numSegments, float width){
        if (numSegments < 3)
        {
            return;
        }

        boolean hasWidth = !(width == 0);
        boolean isFilled = width == 1;

        float innerRadius = radius * (1 - width);

        int extraPoints = 2;


        float[] verticesOuter = new float[numSegments * 2 + extraPoints];
        float[] verticesInner = null;
        if(hasWidth && !isFilled){
            verticesInner = new float[verticesOuter.length];
        }

        Vector2f[] pointsOuter = MathUtils.getPointsAlongCircumference(center, radius, numSegments, 0).toArray(new Vector2f[0]);
        Vector2f[] pointsInner = null;
        if (hasWidth && !isFilled){
            pointsInner = MathUtils.getPointsAlongCircumference(center, innerRadius, numSegments, 0).toArray(new Vector2f[0]);
        }


        for(int i = 0; i < pointsOuter.length; i++){
            int vi = i * 2;

            verticesOuter[vi] = pointsOuter[i].x;
            verticesOuter[vi + 1] = pointsOuter[i].y;

            if(hasWidth && !isFilled){
                verticesInner[vi] = pointsInner[i].x;
                verticesInner[vi + 1] = pointsInner[i].y;
            }
        }



        verticesOuter[verticesOuter.length - 2] = pointsOuter[0].x;
        verticesOuter[verticesOuter.length - 1] = pointsOuter[0].y;

//        verticesOuter[verticesOuter.length - 2] = pointsOuter[1].x;
//        verticesOuter[verticesOuter.length - 1] = pointsOuter[1].y;

        if(hasWidth && !isFilled){
            verticesInner[verticesInner.length - 2] = pointsInner[0].x;
            verticesInner[verticesInner.length - 1] = pointsInner[0].y;

//            verticesInner[verticesInner.length - 2] = pointsInner[1].x;
//            verticesInner[verticesInner.length - 1] = pointsInner[1].y;
        }

        float[] VerticesBoth = null;

        if(hasWidth && !isFilled){
            VerticesBoth = new float[ verticesInner.length + verticesOuter.length];

            for (int i = 0; i < verticesOuter.length; i += 2) {


                VerticesBoth[i * 2] = verticesOuter[i];
                VerticesBoth[i * 2 + 1] = verticesOuter[i + 1];

                VerticesBoth[i * 2 + 2] = verticesInner[i];
                VerticesBoth[i * 2 + 3] = verticesInner[i + 1];
            }
        }

        int length = hasWidth & !isFilled ? VerticesBoth.length : verticesOuter.length;

        FloatBuffer verticesMapTest = BufferUtils.createFloatBuffer(length);
        verticesMapTest.put(hasWidth & !isFilled ? VerticesBoth: verticesOuter).flip();

        glPushClientAttrib(GL_CLIENT_VERTEX_ARRAY_BIT);
        glEnableClientState(GL_VERTEX_ARRAY);
        glVertexPointer(2, 0, verticesMapTest);
        glDrawArrays(hasWidth & !isFilled ? GL_QUAD_STRIP : GL_TRIANGLE_FAN, 0, length / 2);
        glPopClientAttrib();
    }

    public static void  logText(String text){
        System.out.println(text);
    }
}

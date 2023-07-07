package org.Combustiblelemon.CovenantRemnants.utils;

import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;

public class CubicCurve2D {
    private final Vector2f start = new Vector2f(0,0);
    private final Vector2f end = new Vector2f(1,1);

    public Vector2f control1;
    public Vector2f control2;

    public CubicCurve2D(double control1X,double control1Y,double control2X,double control2Y ) {
        this.control1 = new Vector2f((float)control1X, (float)control1Y);
        this.control2 = new Vector2f((float)control2X, (float)control2Y);
    }

    public CubicCurve2D(float control1X,float control1Y,float control2X,float control2Y ) {
        this.control1 = new Vector2f(control1X, control1Y);
        this.control2 = new Vector2f(control2X, control2Y);
    }

    /**
     * @param progression Needs to be between 0 and 1
     */
    public float getTime(float progression) {
        return solveCurveX(progression);
    }

    private float solveCurveX(float t){

        float t1= 1-t;

        return (float) (Math.pow(t1, 3 ) * start.x + 3 * t * Math.pow(t1,2)
                * control1.x + 3 * Math.pow(t, 2) * (t1) * control2.x
                + Math.pow(t,3) * end.x);
    }

    /**
     * @param time Needs to be between 0 and 1
     */
    public float getProgression(float time) {
        return solveCurveY(time);
    }

    private float solveCurveY(float t){

        float t1= 1-t;

        return (float) (Math.pow(t1, 3 ) * start.y + 3 * t * Math.pow(t1,2)
                * control1.y + 3 * Math.pow(t, 2) * (t1) * control2.y
                + Math.pow(t,3) * end.y);
    }
}

// Border layer — border drawn along the shield polygon edges.
// borderColor is also read by sweep.glsl (concatenated after this module).
uniform vec4 borderColor;
uniform float borderWidth;

vec4 border_apply(vec4 color, vec2 pixelPos) {
    float minDistToEdge = 1000.0;
    const int numPoints = POINT_COUNT_PLACEHOLDER;
    int j = numPoints - 1;

    for (int i = 0; i < numPoints; i++) {
        float dist = distanceToLine(pixelPos, points[i], points[j]);
        minDistToEdge = min(minDistToEdge, dist);
        j = i;
    }

    if (minDistToEdge < borderWidth) {
        float borderFactor = 1.0 - (minDistToEdge / borderWidth);
        return mix(color, borderColor, borderFactor);
    }

    return color;
}

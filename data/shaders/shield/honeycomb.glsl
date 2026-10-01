// Honeycomb layer — animated hex grid over the shield surface.
// Grid geometry is the shared hexGridEdgeDistance (base.frag), so other hex layers can overlap it.
uniform bool honeycombEnabled;
uniform float honeycombSize;
uniform float edgeWidth;
uniform float honeycombBreathing; // 1 = full breathing animation, 0 = held at full brightness

// Helper function to render honeycomb pattern covering entire shield
float honeycomb_calculate(vec2 pixelPos) {
    // Ship-relative coordinates, aligned with the ship's facing
    vec2 localPos = shipLocalPos(pixelPos);

    // Configurable hex parameters
    float hexRadius = honeycombSize / viewportScale.x;  // Use uniform honeycombSize
    float edgeThickness = edgeWidth / viewportScale.x;  // Use uniform edgeWidth

    float distToHexEdge = hexGridEdgeDistance(localPos, hexRadius);

    // Calculate hex effect
    if (distToHexEdge < edgeThickness) {
        // Breathing: a brightness wave across the grid, faded out by honeycombBreathing
        float animationFreq = 0.01 * viewportScale.x;
        float breath = 0.5 + 0.5 * sin(time + localPos.x * animationFreq + localPos.y * animationFreq * 0.6);
        breath = max(0.5, breath);
        float pixelAlpha = mix(1.0, breath, honeycombBreathing);

        // Calculate edge factor
        float edgeFactor = 1.0 - (distToHexEdge / edgeThickness);
        return pixelAlpha * edgeFactor;
    }

    return 0.0;
}

vec4 honeycomb_apply(vec4 color, vec2 pixelPos) {
    if (honeycombEnabled) {
        float honeycombEffect = honeycomb_calculate(pixelPos);
        if (honeycombEffect > 0.0) {
            vec4 honeycombColor = vec4(1.0, 1.0, 1.0, honeycombEffect);
            return mix(color, honeycombColor, honeycombEffect);
        }
    }

    return color;
}

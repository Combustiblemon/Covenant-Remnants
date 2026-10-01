// Fortress layer — Fortress Shield active: a locked (static) hex lattice plus a pulsing glow
// along the shield edge. fortressLevel is the ship system's effect level; at 0 the layer is a
// no-op (uniform branch, so it costs nothing while Fortress is off).
uniform float fortressLevel;
uniform vec4 fortressHexColor;    // lattice colour
uniform vec4 fortressGlowColor;   // edge glow colour
uniform float fortressHexSize;    // hex size, world units; same value as the honeycomb's honeycombSize
uniform float fortressHexEdge;    // lattice line half-width, world units
uniform float fortressGlowWidth;  // edge glow band width, world units
uniform float fortressPulseSpeed; // edge glow pulse rate, radians per second

// Lattice line strength at this pixel: 1 on a cell outline, fading to 0 over fortressHexEdge.
// Uses the shared hex grid (base.frag) at the honeycomb's size, so the lattice sits exactly on the
// honeycomb hexes; it has no time term, so it reads as locked plating.
float fortress_lattice(vec2 pixelPos) {
    float hexRadius = fortressHexSize / viewportScale.x;
    float halfWidth = fortressHexEdge / viewportScale.x;

    float distToHexEdge = hexGridEdgeDistance(shipLocalPos(pixelPos), hexRadius);
    return 1.0 - smoothstep(0.0, halfWidth, distToHexEdge);
}

// Edge glow strength at this pixel: 1 on the shield edge, fading to 0 over fortressGlowWidth.
float fortress_glow(vec2 pixelPos) {
    float minDistToEdge = 1000000.0;
    const int numPoints = POINT_COUNT_PLACEHOLDER;
    int j = numPoints - 1;

    for (int i = 0; i < numPoints; i++) {
        minDistToEdge = min(minDistToEdge, distanceToLine(pixelPos, points[i], points[j]));
        j = i;
    }

    float glowWidth = fortressGlowWidth / viewportScale.x;
    float glow = 1.0 - clamp(minDistToEdge / glowWidth, 0.0, 1.0);
    return glow * glow;
}

vec4 fortress_apply(vec4 color, vec2 pixelPos) {
    if (fortressLevel <= 0.0) {
        return color;
    }

    float pulse = 0.6 + 0.4 * sin(time * fortressPulseSpeed);
    float glow = fortress_glow(pixelPos) * pulse;
    vec4 result = mix(color, fortressGlowColor, clamp(glow * fortressLevel, 0.0, 1.0));

    // Lattice drawn over the glow so the hexes keep their colour near the shield edge
    return mix(result, fortressHexColor, clamp(fortress_lattice(pixelPos) * fortressLevel, 0.0, 1.0));
}

// Honeycomb layer — animated hex grid over the shield surface.
uniform bool honeycombEnabled;
uniform float honeycombSize;
uniform float edgeWidth;

// Helper function to render honeycomb pattern covering entire shield
float honeycomb_calculate(vec2 pixelPos) {
    // Convert pixel position to ship-relative coordinates
    vec2 shipRelPos = pixelPos - center;

    // Use ship facing to calculate the correct x-axis direction for grid rotation
    float facingRad = radians(shipFacing);
    float cosFacing = cos(facingRad);
    float sinFacing = sin(facingRad);

    // Rotate the position to align with ship's x-axis (facing direction)
    vec2 localPos = vec2(shipRelPos.x * cosFacing + shipRelPos.y * sinFacing, -shipRelPos.x * sinFacing + shipRelPos.y * cosFacing);

    // Configurable hex parameters
    float hexRadius = honeycombSize / viewportScale.x;  // Use uniform honeycombSize
    float edgeThickness = edgeWidth / viewportScale.x;  // Use uniform edgeWidth

    // Proper honeycomb spacing - hexagons with touching sides
    float hexSpacingX = hexRadius * 1.4;      // 1.5 * radius for touching hexagons
    float hexSpacingY = hexRadius * 1.2;      // Even closer vertical spacing for overlapping rows

    // Calculate which hexagon this pixel belongs to
    // First, determine which row we're in
    float hexY = localPos.y / hexSpacingY;
    int gridY = int(round(hexY));

    // Calculate row offset first
    float rowOffset = 0.0;
    if (mod(float(gridY), 2.0) != 0.0) {
        rowOffset = hexSpacingX * 0.5;
    }

    // Now calculate x position accounting for the row offset
    float hexX = (localPos.x - rowOffset) / hexSpacingX;
    int gridX = int(round(hexX));

    // Calculate hex center position
    vec2 hexCenter = vec2(float(gridX) * hexSpacingX + rowOffset, float(gridY) * hexSpacingY);

    // Calculate distance from pixel to hex center
    float distFromCenter = distance(localPos, hexCenter);

    // Simple hexagon edge calculation with proper scaling and rotation
    float distToHexEdge = 0.0;
    if (distFromCenter > 0.0) {
        // Calculate angle from hex center to pixel
        vec2 dir = (localPos - hexCenter) / distFromCenter;
        float angle = atan(dir.y, dir.x);
        if (angle < 0.0)
        angle += 2.0 * 3.14159265359;

        // Rotate by 90 degrees (π/2) so sides touch instead of points
        angle += 3.14159265359 / 2.0;

        // Normalize angle to 0-60 degree range (hexagon has 6-fold symmetry)
        float normalizedAngle = mod(angle, 3.14159265359 / 3.0);

        // Simple hexagon edge distance with proper scaling
        float hexEdgeDist = (hexRadius * 0.7) / cos(normalizedAngle - 3.14159265359 / 6.0);

        distToHexEdge = abs(distFromCenter - hexEdgeDist);
    }

    // Calculate hex effect
    if (distToHexEdge < edgeThickness) {
        // Simple animation
        float animationFreq = 0.01 * viewportScale.x;
        float pixelAlpha = 0.5 + 0.5 * sin(time + localPos.x * animationFreq + localPos.y * animationFreq * 0.6);
        pixelAlpha = max(0.5, pixelAlpha);

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

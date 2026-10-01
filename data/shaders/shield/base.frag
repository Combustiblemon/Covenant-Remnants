#version 330 core
out vec4 FragColor;

in vec2 TexCoord;

// Shared uniforms — used by more than one layer module.
// Layer-specific uniforms are declared in each layer's .glsl module.
uniform vec2 points[POINT_COUNT_PLACEHOLDER]; // Shield polygon, screen coordinates
uniform vec2 resolution;                      // Screen resolution
uniform vec2 center;                          // Ship center, screen coordinates
uniform vec4 shipRotationMatrix;              // Pre-calculated rotation matrix as vec4 (m00, m01, m10, m11)
uniform vec2 viewportScale;                   // For world-to-screen conversion
uniform float shipFacing;
uniform float time;

// Helper function to calculate distance from point to line segment
float distanceToLine(vec2 point, vec2 lineStart, vec2 lineEnd) {
    vec2 line = lineEnd - lineStart;
    vec2 pointToStart = point - lineStart;
    float t = clamp(dot(pointToStart, line) / dot(line, line), 0.0, 1.0);
    vec2 closest = lineStart + t * line;
    return distance(point, closest);
}

// Point-in-polygon test using ray casting algorithm
bool isPointInPolygon(vec2 point) {
    // Use the fixed array size from POINT_COUNT_PLACEHOLDER
    const int numPoints = POINT_COUNT_PLACEHOLDER;
    if (numPoints < 3)
    return false;

    bool inside = false;
    int j = numPoints - 1;

    for (int i = 0; i < numPoints; i++) {
        // Points are already in screen coordinates
        vec2 screenPointI = points[i];
        vec2 screenPointJ = points[j];

        // Check if point is on the same side of the edge as the ray
        if (((screenPointI.y > point.y) != (screenPointJ.y > point.y)) &&
        (point.x < (screenPointJ.x - screenPointI.x) * (point.y - screenPointI.y) / (screenPointJ.y - screenPointI.y) + screenPointI.x)) {
            inside = !inside;
        }
        j = i;
    }

    return inside;
}

// Pixel position in ship-local screen coordinates (x along the ship's facing).
vec2 shipLocalPos(vec2 pixelPos) {
    vec2 shipRelPos = pixelPos - center;
    float facingRad = radians(shipFacing);
    float cosFacing = cos(facingRad);
    float sinFacing = sin(facingRad);
    return vec2(shipRelPos.x * cosFacing + shipRelPos.y * sinFacing, -shipRelPos.x * sinFacing + shipRelPos.y * cosFacing);
}

// Distance from a ship-local position to the outline of its cell in the shield's hex grid
// (hexRadius in screen pixels). Shared so every hex layer (honeycomb, fortress) draws the same grid.
float hexGridEdgeDistance(vec2 localPos, float hexRadius) {
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

    return distToHexEdge;
}

// {{LAYER_MODULES}}

vec4 shieldFrag(vec2 pixelPos) {
    vec4 result = fill_color();

    float pixelShipRelX = sweep_shipRelX(pixelPos);
    vec4 sweepEdge = sweep_edgeColor(pixelShipRelX);

    // Sweep is mask-modifying: pixels beyond the sweep position in ship-relative
    // coordinates show only the sweep edge (or nothing)
    if (sweep_isCulled(pixelShipRelX)) {
        return sweepEdge;
    }

    // Mix order is the pixel contract — do not reorder
    result = border_apply(result, pixelPos);
    result = sweep_applyEdge(result, sweepEdge);
    result = ripple_apply(result, pixelPos);
    result = honeycomb_apply(result, pixelPos);
    result = fortress_apply(result, pixelPos);

    return result;
}

void main() {
    // Calculate pixel position from texture coordinates (this is the actual screen position)
    vec2 pixelPos = TexCoord * resolution;

    // Debug mode: show polygon outline, points, and bounding box.
    // The debugMode gate lives inside debug_check so this footer never
    // references a layer uniform (a stubbed debug layer does not declare it).
    vec4 dbgColor;
    if (debug_check(pixelPos, dbgColor)) {
        FragColor = dbgColor;
        return;
    }

    // Check if pixel is inside the shield polygon
    if (!isPointInPolygon(pixelPos)) {
        discard;
    }

    FragColor = shieldFrag(pixelPos);
}

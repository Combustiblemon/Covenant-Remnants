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

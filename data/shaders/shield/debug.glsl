// Debug layer — bounding-box outline (red), polygon points (green),
// polygon edges (magenta). Returns true when it produced a color.
uniform bool debugMode;
uniform vec2 boundingBoxMin;
uniform vec2 boundingBoxMax;

bool debug_check(vec2 pixelPos, out vec4 dbgColor) {
    const int numPoints = POINT_COUNT_PLACEHOLDER;

    // Show bounding box outline (red rectangle)
    float boundingBoxLeft = boundingBoxMin.x;
    float boundingBoxRight = boundingBoxMax.x;
    float boundingBoxTop = boundingBoxMax.y;
    float boundingBoxBottom = boundingBoxMin.y;

    // Check if pixel is near bounding box edges (increased thickness for visibility)
    float distToLeft = abs(pixelPos.x - boundingBoxLeft);
    float distToRight = abs(pixelPos.x - boundingBoxRight);
    float distToTop = abs(pixelPos.y - boundingBoxTop);
    float distToBottom = abs(pixelPos.y - boundingBoxBottom);

    bool nearLeftEdge = distToLeft < 5.0 && pixelPos.y >= boundingBoxBottom && pixelPos.y <= boundingBoxTop;
    bool nearRightEdge = distToRight < 5.0 && pixelPos.y >= boundingBoxBottom && pixelPos.y <= boundingBoxTop;
    bool nearTopEdge = distToTop < 5.0 && pixelPos.x >= boundingBoxLeft && pixelPos.x <= boundingBoxRight;
    bool nearBottomEdge = distToBottom < 5.0 && pixelPos.x >= boundingBoxLeft && pixelPos.x <= boundingBoxRight;

    if (nearLeftEdge || nearRightEdge || nearTopEdge || nearBottomEdge) {
        dbgColor = vec4(1.0, 0.0, 0.0, 1.0); // Red bounding box outline
        return true;
    }

    // Show all polygon points as small circles (only valid points)
    // Points are already in screen coordinates
    for (int i = 0; i < numPoints; i++) {
        float dist = distance(pixelPos, points[i]);
        if (dist < 3.0) {
            dbgColor = vec4(0.0, 1.0, 0.0, 1.0); // Green dots for points
            return true;
        }
    }

    // Check if pixel is near any polygon edge (only valid edges)
    // Points are already in screen coordinates
    float minDist = 1000.0;
    int j = numPoints - 1;

    for (int i = 0; i < numPoints; i++) {
        float dist = distanceToLine(pixelPos, points[i], points[j]);
        minDist = min(minDist, dist);
        j = i;
    }

    if (minDist < 2.0) {
        dbgColor = vec4(1.0, 0.0, 1.0, 1.0); // Magenta outline
        return true;
    }

    dbgColor = vec4(0.0);
    return false;
}

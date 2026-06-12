#version 330 core
out vec4 FragColor;

in vec2 TexCoord;

// Uniforms
uniform vec4 shieldColor;
uniform vec4 borderColor;
uniform float borderWidth;
uniform float shipFacing;
uniform vec2 center;
uniform float time;
uniform bool honeycombEnabled;
uniform float honeycombSize;
uniform float edgeWidth;
uniform float sweepEdgeWidth;       // Width of the sweep edge in pixels
uniform float shieldHPPercent;      // 0.0 to 1.0, shield health percentage

// Sweep position calculated on CPU
uniform float sweepPosition;        // Current sweep position in ship-relative coordinates

// Ripple uniforms
uniform int numRipples;
uniform vec4 rippleColors[10];
uniform vec2 rippleCenters[10];
uniform float rippleRadii[10];
uniform float rippleIntensities[10];
uniform float rippleWidths[10];


// Bounding box uniforms for debug visualization
uniform vec2 boundingBoxMin;
uniform vec2 boundingBoxMax;

// Polygon points as individual uniform arrays - no UBO needed
uniform vec2 points[POINT_COUNT_PLACEHOLDER]; // Screen coordinates


uniform vec2 resolution;   // Screen resolution
uniform bool debugMode;    // Debug mode to show polygon outline

// Additional uniforms needed for coordinate transformations
uniform vec4 shipRotationMatrix; // Pre-calculated rotation matrix as vec4 (m00, m01, m10, m11)
uniform vec2 viewportScale;      // For world-to-screen conversion

// Helper function to calculate distance from point to line segment
float distanceToLine(vec2 point, vec2 lineStart, vec2 lineEnd) {
    vec2 line = lineEnd - lineStart;
    vec2 pointToStart = point - lineStart;
    float t = clamp(dot(pointToStart, line) / dot(line, line), 0.0, 1.0);
    vec2 closest = lineStart + t * line;
    return distance(point, closest);
}

// Helper function to render honeycomb pattern covering entire shield
float calculateHoneycomb(vec2 pixelPos) {
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

// Shield bounds are now pre-calculated on CPU for performance

vec4 mainShield(vec2 pixelPos) {
    // Calculate shield color with charge progress and HP-based brightness
    vec4 finalShieldColor = shieldColor;

    // Make shield brighter as HP decreases (inverse relationship)
    // When shieldHPPercent is 1.0 (full), brightness is 1.0
    // When shieldHPPercent is 0.0 (empty), brightness is 2.0 (twice as bright)
    float hpBrightness = 2.0 - shieldHPPercent;
    finalShieldColor.rgb *= hpBrightness;

    // Make shield less transparent (more opaque) as HP decreases
    // When shieldHPPercent is 1.0 (full), alpha is unchanged (1.0 multiplier)
    // When shieldHPPercent is 0.0 (empty), alpha is increased (1.0 multiplier, fully opaque)
    float hpAlphaMultiplier = 1.0 - (shieldHPPercent * 0.0); // Range from 1.0 to 1.0 (no change)
    // Alternative: make it more opaque only when damaged
    float damageAlphaBoost = (1.0 - shieldHPPercent) * 0.6; // 0.0 to 0.3 boost when damaged
    finalShieldColor.a *= (1.0 + damageAlphaBoost);

    // Calculate ship-relative position in screen coordinates
    // Get ship-relative position (relative to ship center in screen coordinates)
    vec2 shipRelPos = pixelPos - center;
    // Apply rotation matrix to get ship-relative X coordinate (same as CPU calculation)
    // shipRotationMatrix[0] = cos(facing), shipRotationMatrix[1] = -sin(facing)
    // But we need +sin(facing) to match CPU calculation, so we negate shipRotationMatrix[1]
    float pixelShipRelX = shipRelPos.x * shipRotationMatrix[0] - shipRelPos.y * shipRotationMatrix[1];

    // Calculate sweep edge color based on distance from sweep position
    // Scale edge width with screen resolution for consistent visual size
    float scaledEdgeWidth = sweepEdgeWidth * (resolution.x / 1920.0);
    float halfEdgeWidth = scaledEdgeWidth * 0.5;
    float distanceFromSweep = abs(pixelShipRelX - sweepPosition);

    vec4 sweepEdgeColor = vec4(0.0, 0.0, 0.0, 0.0); // Default transparent
    if (distanceFromSweep < halfEdgeWidth) {
        float edgeFactor = 1.0 - (distanceFromSweep / halfEdgeWidth);
        sweepEdgeColor = vec4(borderColor.rgb, borderColor.a * edgeFactor);
    }

    // If pixel is beyond the sweep position in ship-relative coordinates, make it transparent
    if (pixelShipRelX < sweepPosition) {
        // If within sweep edge, show edge color, otherwise transparent
        return sweepEdgeColor;
    }

    // Apply border effect
    float minDistToEdge = 1000.0;
    const int numPoints = POINT_COUNT_PLACEHOLDER;
    int j = numPoints - 1;

    for (int i = 0; i < numPoints; i++) {
        float dist = distanceToLine(pixelPos, points[i], points[j]);
        minDistToEdge = min(minDistToEdge, dist);
        j = i;
    }

    // Start with base shield color
    vec4 resultColor = finalShieldColor;

    // Apply regular border
    if (minDistToEdge < borderWidth) {
        float borderFactor = 1.0 - (minDistToEdge / borderWidth);
        resultColor = mix(resultColor, borderColor, borderFactor);
    }

    // Apply sweep edge effect (blend with existing color)
    if (sweepEdgeColor.a > 0.0) {
        // Blend sweep edge with the current shield color
        resultColor = mix(resultColor, sweepEdgeColor, sweepEdgeColor.a);
    }

    // Apply ripple effects
    for (int i = 0; i < numRipples; i++) {
        float distanceToRipple = distance(pixelPos, rippleCenters[i]);
        // Use ripple radius and width directly (already converted to screen coordinates)
        float rippleRadius = rippleRadii[i];
        float rippleWidth = rippleWidths[i];

        // Check if pixel is within the ripple ring
        if (distanceToRipple >= rippleRadius - rippleWidth && distanceToRipple <= rippleRadius) {
            // Scale the fade calculation to maintain consistent visual appearance across zoom levels
            // When zoomed out (viewportScale.x > 1), we want sharper fades
            // When zoomed in (viewportScale.x < 1), we want more gradual fades
            float fadeWidth = rippleWidth / viewportScale.x;
            float rippleFactor = 1.0 - abs(distanceToRipple - rippleRadius) / fadeWidth;
            rippleFactor = clamp(rippleFactor, 0.0, 1.0);
            rippleFactor *= rippleIntensities[i];

            // Blend ripple color with current color
            resultColor = mix(resultColor, rippleColors[i], rippleFactor * rippleColors[i].a);
        }
    }

    // Apply honeycomb effect
    if (honeycombEnabled) {
        float honeycombEffect = calculateHoneycomb(pixelPos);
        if (honeycombEffect > 0.0) {
            vec4 honeycombColor = vec4(1.0, 1.0, 1.0, honeycombEffect);
            resultColor = mix(resultColor, honeycombColor, honeycombEffect);
        }
    }

    return resultColor;
}

void main() {
    // Calculate pixel position from texture coordinates (this is the actual screen position)
    vec2 pixelPos = TexCoord * resolution;

    // Debug mode: show polygon outline, points, and bounding box
    if (debugMode) {
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
            FragColor = vec4(1.0, 0.0, 0.0, 1.0); // Red bounding box outline
            return;
        }

        // Show all polygon points as small circles (only valid points)
        // Points are already in screen coordinates
        for (int i = 0; i < numPoints; i++) {
            float dist = distance(pixelPos, points[i]);
            if (dist < 3.0) {
                FragColor = vec4(0.0, 1.0, 0.0, 1.0); // Green dots for points
                return;
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
            FragColor = vec4(1.0, 0.0, 1.0, 1.0); // Magenta outline
            return;
        }
    }

    // Check if pixel is inside the shield polygon
    bool insidePolygon = isPointInPolygon(pixelPos);

    if (insidePolygon) {
        // Render the shield with sweep animation
        FragColor = mainShield(pixelPos);
    } else {
        // Point is outside the shield polygon
        discard;
    }
}

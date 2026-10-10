// Sweep layer — activation wipe across the shield in ship-relative X.
// Depends on border.glsl (reads borderColor) — border module must be
// concatenated before this one.
// The mask-modifying early return lives in base.frag's shieldFrag().
uniform float sweepPosition;  // Current sweep position in ship-relative coordinates
uniform float sweepEdgeWidth; // Width of the sweep edge in screen units (virtual px: scales with the UI scale)

// True when the pixel is beyond the sweep position (not yet covered by the
// activation wipe) — base.frag culls those pixels to the sweep edge color.
bool sweep_isCulled(float pixelShipRelX) {
    return pixelShipRelX < sweepPosition;
}

float sweep_shipRelX(vec2 pixelPos) {
    // Get ship-relative position (relative to ship center in screen coordinates)
    vec2 shipRelPos = pixelPos - center;
    // Apply rotation matrix to get ship-relative X coordinate (same as CPU calculation)
    // shipRotationMatrix[0] = cos(facing), shipRotationMatrix[1] = -sin(facing)
    // But we need +sin(facing) to match CPU calculation, so we negate shipRotationMatrix[1]
    return shipRelPos.x * shipRotationMatrix[0] - shipRelPos.y * shipRotationMatrix[1];
}

vec4 sweep_edgeColor(float pixelShipRelX) {
    // Calculate sweep edge color based on distance from sweep position. The width is not scaled by
    // the resolution: in screen units it already grows with the UI scale like the rest of the shield.
    float halfEdgeWidth = sweepEdgeWidth * 0.5;
    float distanceFromSweep = abs(pixelShipRelX - sweepPosition);

    vec4 sweepEdgeColor = vec4(0.0, 0.0, 0.0, 0.0); // Default transparent
    if (distanceFromSweep < halfEdgeWidth) {
        float edgeFactor = 1.0 - (distanceFromSweep / halfEdgeWidth);
        sweepEdgeColor = vec4(borderColor.rgb, borderColor.a * edgeFactor);
    }

    return sweepEdgeColor;
}

vec4 sweep_applyEdge(vec4 color, vec4 sweepEdge) {
    // Blend sweep edge with the current shield color
    if (sweepEdge.a > 0.0) {
        return mix(color, sweepEdge, sweepEdge.a);
    }
    return color;
}

#version 330 core
in vec2 TexCoord;
out vec4 FragColor;

uniform float aspectRatio;
uniform float time;
uniform float edgeFadeStart;
uniform vec3 baseColor;
uniform vec3 vortexColor;
uniform float vortexSpeed;
uniform int vortexArms;

// Energy flow uniforms
uniform float energyFlowSpeed;
uniform vec3 energyFlowColor;
uniform float energyFlowIntensity;
uniform int energyFlowStreams;

// Animated flow uniforms
uniform float flowStreakSpeed;
uniform float flowStreakIntensity;
uniform int flowStreakCount;
uniform float flowStreakWidth;
uniform float flowStreakGap;

// Outer ring uniforms
uniform float outerRingThickness;
uniform vec3 outerRingColor;
uniform float outerRingFadeStart;

// Inner edge wave distortion uniforms
uniform float innerEdgeWaveAmplitude;
uniform float innerEdgeWaveFrequency;
uniform float innerEdgeWaveSpeed;

// Animation uniforms
uniform float animationProgress;
uniform float closedStateRadius;
uniform vec3 closedStateColor;

const float TWO_PI = 6.28318530718;

// Calculate edge fade based on distance from center
float calculateEdgeFade(float dist) {
    if (dist < edgeFadeStart) {
        return 1.0;
    }
    // Smoothly fade from edgeFadeStart to edge (1.0)
    float fadeRange = 1.0 - edgeFadeStart;
    float fadeAmount = (dist - edgeFadeStart) / fadeRange;
    return 1.0 - smoothstep(0.0, 1.0, fadeAmount);
}

// Calculate breathing pattern (pulsing effect)
float calculateVortexPattern(vec2 coord, float dist) {
    // Create breathing effect using sine wave over time
    // This creates a smooth pulsing/breathing animation
    float breathe = sin(time * vortexSpeed) * 0.5 + 0.5;

    // Optional: Add subtle distance-based variation for depth
    float distanceVariation = sin(dist * 3.0) * 0.2 + 0.8;

    return breathe * distanceVariation;
}

// Simple pseudo-random function based on angle
float random(float angle, float seed) {
    return fract(sin(angle * 12.9898 + seed * 78.233) * 43758.5453);
}

// Calculate constant energy intensity (base layer)
float calculateEnergyFlow(vec2 coord, float dist) {
    // Base intensity that gets brighter towards center
    // Use inverse distance to make it brighter at center
    float centerBrightness = 1.0 + (1.0 - dist) * 0.5; // 1.0 at edge, 1.5 at center
    float intensity = centerBrightness;

    // Add subtle radial variation for depth
    float radialVariation = sin(dist * 5.0) * 0.1 + 0.9;
    intensity *= radialVariation;

    // Fade out in the inner 10% (from 0% to 10% of radius)
    float centerFade = smoothstep(0.0, 0.10, dist);

    // Fade in from edge
    float edgeFade = 1.0 - smoothstep(0.98, 1.0, dist);

    // Combine all elements
    return intensity * centerFade * edgeFade;
}

// Calculate outer ring with fade effect and inner edge wave distortion
// Returns intensity (0.0 to 1.0) with fade from center to edges
float calculateOuterRing(vec2 coord, float dist, float effectiveRadius) {
    // Ring follows the effective radius during animation
    // Outer edge is always at the current effective radius
    float ringOuterRadius = effectiveRadius;
    float ringCenterRadius = effectiveRadius - (outerRingThickness / 2.0);

    // Calculate angle for wave distortion
    float angle = atan(coord.y, coord.x);

    // Create wave distortion on inner edge
    // Waves move inward from the base inner radius
    float baseInnerRadius = effectiveRadius - outerRingThickness;
    float waveOffset = sin(angle * innerEdgeWaveFrequency + time * innerEdgeWaveSpeed) * innerEdgeWaveAmplitude;
    float ringInnerRadius = baseInnerRadius - waveOffset; // Wave goes inward (positive offset)

    // Check if we're in the ring
    if (dist < ringInnerRadius || dist > ringOuterRadius) {
        return 0.0;
    }

    // Calculate distance from ring center (not circle center)
    float distFromRingCenter = abs(dist - ringCenterRadius);
    float halfThickness = outerRingThickness / 2.0;

    // Normalize distance from ring center (0.0 at ring center, 1.0 at ring edges)
    float normalizedDist = distFromRingCenter / halfThickness;

    // Disable ring fading during animation (when not fully open)
    float intensity = 1.0;
    if (animationProgress >= 1.0) {
        // Only apply fading when fully open
        if (normalizedDist > outerRingFadeStart) {
            // Fade from fadeStart to edge
            float fadeRange = 1.0 - outerRingFadeStart;
            float fadeProgress = (normalizedDist - outerRingFadeStart) / fadeRange;
            intensity = 1.0 - smoothstep(0.0, 1.0, fadeProgress);
        }
    }

    return intensity;
}

// Calculate constant animated flow streaks
float calculateFlowStreaks(vec2 coord, float dist) {
    float angle = atan(coord.y, coord.x);
    float normalizedAngle = (angle / TWO_PI) + 0.5;

    float totalStreaks = 0.0;

    // Create 16 constant flow lines evenly distributed
    for (int i = 0; i < 16; i++) {
        if (i >= flowStreakCount) break;

        float streakAngle = float(i) / float(flowStreakCount);

        // Angular width of streak - alternate between thin and thick lines
        float streakWidth = (i % 2 == 0) ? 0.03 : 0.06; // Every second line is thicker
        float angleDiff = abs(normalizedAngle - streakAngle);
        if (angleDiff > 0.5) angleDiff = 1.0 - angleDiff;

        float angularIntensity = smoothstep(streakWidth, 0.0, angleDiff);

        // Constant base intensity for the band (always visible)
        float baseIntensity = 0.5;

        // Create traveling highlight that moves from edge to center
        // Add randomized offset for each streak to desync animations
        float streakRandomOffset = random(float(i), 42.0) * 10.0; // Random offset for this streak
        float animatedPosition = -time * flowStreakSpeed + streakRandomOffset;

        // Create multiple staggered highlights for smooth continuous animation
        // Ensure minimum spacing to prevent overlap
        float minSpacing = flowStreakWidth + max(flowStreakGap, 0.05);
        float highlightSpacing = minSpacing;

        // Determine how many highlights we need to cover the full radius
        int numHighlights = int(ceil(1.0 / highlightSpacing)) + 1;

        float highlight = 0.0;

        for (int h = 0; h < numHighlights; h++) {
            // Offset each highlight by spacing to prevent overlap
            float offset = float(h) * highlightSpacing;
            float basePosition = animatedPosition + offset;

            // The highlight travels from edge (1.0) to center (0.0)
            // Head is at basePosition, tail extends outward (toward edge)
            float headPosition = fract(basePosition);
            float tailPosition = fract(basePosition + flowStreakWidth);

            // Check if current distance is within the highlight range
            // Handle wrapping case where tail > head (highlight wraps around from edge)
            float isInHighlight = 0.0;

            if (tailPosition > headPosition) {
                // Normal case: tail is further from center than head
                if (dist >= headPosition && dist <= tailPosition) {
                    // Within the highlight range
                    float distFromHead = dist - headPosition;
                    float gradientPos = distFromHead / flowStreakWidth;

                    // Smooth fade at head (first 50% of width) and tail (last 80% of width)
                    float headFade = smoothstep(0.0, 0.5, gradientPos);
                    float tailFade = smoothstep(1.0, 0.2, gradientPos);

                    isInHighlight = headFade * tailFade;
                }
            } else {
                // Wrapping case: highlight crosses the edge (1.0 -> 0.0 boundary)
                if (dist >= headPosition || dist <= tailPosition) {
                    // Calculate distance from head, handling wrap
                    float distFromHead;
                    if (dist >= headPosition) {
                        distFromHead = dist - headPosition;
                    } else {
                        distFromHead = (1.0 - headPosition) + dist;
                    }
                    float gradientPos = distFromHead / flowStreakWidth;

                    // Smooth fade at head and tail
                    float headFade = smoothstep(0.0, 0.5, gradientPos);
                    float tailFade = smoothstep(1.0, 0.2, gradientPos);

                    isInHighlight = headFade * tailFade;
                }
            }

            // Accumulate highlights (use max to prevent overlapping brightening)
            highlight = max(highlight, isInHighlight);
        }

        // Boost highlight brightness
        highlight = pow(highlight, 0.5) * 0.8;

        // Combine base intensity with traveling highlight
        float flowIntensity = baseIntensity + highlight;

        // Fade out at center
        float centerFade = smoothstep(0.0, 0.10, dist);

        // Combine this streak
        totalStreaks += angularIntensity * flowIntensity * centerFade;
    }

    return clamp(totalStreaks, 0.0, 1.0);
}

void main() {
    // Adjust texture coordinates by aspect ratio to maintain circular shape
    // Divide Y by aspect ratio to compensate for wider screens
    vec2 adjustedCoord = vec2(TexCoord.x, TexCoord.y);

    // Calculate distance from center using adjusted coordinates
    float dist = length(adjustedCoord);

    // Calculate current effective radius based on animation progress
    // Interpolate between closed radius and full radius (1.0)
    float effectiveRadius = mix(closedStateRadius, 1.0, animationProgress);

    // Discard fragments outside the current effective circle
    if (dist > effectiveRadius) {
        discard;
    }

    // If animation progress is very low (closed state), render simple circle
    if (animationProgress < 0.01) {
        // Simple closed state - just a solid circle
        FragColor = vec4(closedStateColor, 1.0);
        return;
    }

    // Calculate edge fade (COMMENTED OUT FOR NOW)
    float edgeFade = calculateEdgeFade(dist);

    // Calculate breathing pattern using adjusted coordinates
    float vortexPattern = calculateVortexPattern(adjustedCoord, dist);

    // Calculate base energy flow effect
    float energyFlow = calculateEnergyFlow(adjustedCoord, dist);

    // Calculate animated flow streaks
    float flowStreaks = calculateFlowStreaks(adjustedCoord, dist);

    // Blend base color with vortex color based on breathing pattern
    vec3 finalColor = mix(baseColor, vortexColor, vortexPattern);

    // Add base energy flow on top with additive blending
    finalColor += energyFlowColor * energyFlow * energyFlowIntensity;

    // Add brighter animated flow streaks
    finalColor += energyFlowColor * flowStreaks * flowStreakIntensity;

    // Calculate and apply outer ring (on top of everything else)
    float ringIntensity = calculateOuterRing(adjustedCoord, dist, effectiveRadius);

    // Render ring if intensity > 0
    if (ringIntensity > 0.0) {
        // Blend ring color with existing color based on intensity
        finalColor = mix(finalColor, outerRingColor, ringIntensity);
    }

    // Apply edge fade to alpha
    float alpha = edgeFade;

    FragColor = vec4(finalColor, alpha);
}
// Ripple layer — impact ripple rings expanding across the shield.
uniform int numRipples;
uniform vec4 rippleColors[10];
uniform vec2 rippleCenters[10];
uniform float rippleRadii[10];
uniform float rippleIntensities[10];
uniform float rippleWidths[10];

vec4 ripple_apply(vec4 color, vec2 pixelPos) {
    vec4 resultColor = color;

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

    return resultColor;
}

// Distortion layer — refraction rings expanding from each shield hit. The wavefront bends the
// background behind the shield (a copy of the framebuffer under the shield's bounding box, taken
// just before the draw). Amplitude and travel distance scale with the hit's damage level.
//
// Composition (base.frag main()): distortion_offset() is computed first and the whole mix chain is
// evaluated at the displaced position, so the hexes, border and plasma bend with the ring too; then
// distortion_compose() puts the displaced background under it. Where no ring displaces the pixel the
// colour passes through unchanged, so the ordinary alpha-blended path is untouched. The polygon
// mask stays on the real pixel.
uniform sampler2D distortionScreen;      // framebuffer copy, texture unit 2, viewport-sized
uniform vec4 distortionViewport;         // GL viewport x, y, width, height (px)
uniform int distortionWaveCount;
uniform vec4 distortionWaves[8];         // xy: centre (screen px), z: ring radius (px), w: ring half-width (px)
uniform vec2 distortionWaveParams[8];    // x: amplitude (px), y: remaining strength 0..1

// Summed ring displacement at this pixel, screen px (zero outside every ring).
vec2 distortion_offset(vec2 pixelPos) {
    vec2 offset = vec2(0.0);
    for (int i = 0; i < distortionWaveCount; i++) {
        vec2 toPixel = pixelPos - distortionWaves[i].xy;
        float dist = length(toPixel);
        float p = (dist - distortionWaves[i].z) / distortionWaves[i].w;   // -1..1 across the ring
        if (abs(p) < 1.0 && dist > 0.001) {
            // Antisymmetric profile: the background is pulled in ahead of the front and pushed out
            // behind it, which reads as a travelling ripple.
            float profile = sin(3.14159265 * p) * (1.0 - p * p);
            offset += (toPixel / dist) * (distortionWaveParams[i].x * distortionWaveParams[i].y * profile);
        }
    }
    return offset;
}

vec4 distortion_compose(vec4 color, vec2 pixelPos, vec2 offset) {
    if (dot(offset, offset) < 0.0625) {   // under a quarter pixel: keep the plain blended path
        return color;
    }
    vec2 screenUv = (gl_FragCoord.xy - distortionViewport.xy + offset) / distortionViewport.zw;
    vec3 behind = texture(distortionScreen, clamp(screenUv, 0.0, 1.0)).rgb;
    return vec4(mix(behind, color.rgb, clamp(color.a, 0.0, 1.0)), 1.0);
}

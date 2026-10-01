// Plasma layer — the Halo: Reach Elite energy shield (fx\shield_impacts\elite_3p*.shield_impact,
// tags\shaders\explicit\shield_impact.hlsl) adapted to a 2D top-down polygon.
//
// Reach's fresnel term (cosine between view and surface normal: 0 at the silhouette, 1 facing the
// camera) becomes "depth into the body": distance to the shield outline over plasmaBodyDepth.
// Everything below is additive light. It is composited into the straight-alpha single pass at the
// end of plasma_apply so the blend function and the other layers stay untouched.
// All parameters (colours x intensities, bands, sharpness, hits) are evaluated on the CPU from the
// Reach curves (ShieldImpactEffect) for the current state: hits + low-HP baseline, recharge wave,
// or collapse.
uniform sampler2D plasmaNoiseA;      // plasma_clouds_512_a, texture unit 0
uniform sampler2D plasmaNoiseB;      // plasma_clouds_512_b, texture unit 1
uniform float plasmaActive;          // 0 = layer off (uniform early-out)
uniform float plasmaBodyFade;        // multiplies the incoming shield alpha (collapse fade-out)
uniform float plasmaBodyDepth;       // outline distance (px) that maps to depth 1
uniform vec4 plasmaEdgeBand;         // rim glow band: sat(min(d * x + y, d * z + w))
uniform vec4 plasmaPlasmaBand;       // plasma band, same encoding
uniform vec2 plasmaSharpness;        // (edge, centre) exponents: pow(plasma, mix(edge, centre, band))
uniform float plasmaUvScale;         // noise uv per screen px (ship-local)
uniform vec4 plasmaOffsets;          // noise scroll: A.xy added, B.zw subtracted
uniform vec3 plasmaEdgeGlow;         // rim glow colour x intensity
uniform vec3 plasmaCenterColor;      // plasma colour everywhere x intensity
uniform vec3 plasmaEdgeColor;        // plasma colour inside the band x intensity
uniform int plasmaHitCount;
uniform vec4 plasmaHits[8];          // xy: screen px, z: radius px, w: strength
uniform vec3 plasmaHitColors[8];     // hit colour x fade
uniform vec3 plasmaFlash;            // flat light over the whole polygon (break flash)

// Deviation from Reach: the rim glow is also textured by the broad plasma noise (Reach leaves it a
// flat band). 1 = fully textured, 0 = flat; the floor keeps the ring visible between filaments.
const float plasma_GLOW_NOISE = 1.0;

// Deviation from Reach: hit spots are mostly unmasked by the filament pattern (Reach multiplies them
// by it, which made them nearly invisible here). 1 = flat spot, 0 = Reach.
const float plasma_HIT_UNMASKED = 0.75;

float plasma_band(float depth, vec4 band) {
    return clamp(min(depth * band.x + band.y, depth * band.z + band.w), 0.0, 1.0);
}

// Distance from the pixel to the shield outline, screen px.
float plasma_outlineDistance(vec2 pixelPos) {
    float minDist = 1000000.0;
    int numPoints = pointCount;
    int j = numPoints - 1;
    for (int i = 0; i < numPoints; i++) {
        minDist = min(minDist, distanceToLine(pixelPos, points[i], points[j]));
        j = i;
    }
    return minDist;
}

vec4 plasma_apply(vec4 color, vec2 pixelPos) {
    if (plasmaActive <= 0.0) {
        return color;
    }
    color.a *= plasmaBodyFade;

    float depth = clamp(plasma_outlineDistance(pixelPos) / plasmaBodyDepth, 0.0, 1.0);
    float edgeLinear = plasma_band(depth, plasmaEdgeBand);
    float edge = edgeLinear * edgeLinear * edgeLinear * edgeLinear;
    float edgePlasma = plasma_band(depth, plasmaPlasmaBand);

    // Two scrolling cloud maps; their difference makes the filaments. textureLod: the fragment is
    // past a non-uniform discard, so derivatives are undefined (the maps have no mipmaps anyway).
    vec2 uv = shipLocalPos(pixelPos) * plasmaUvScale;
    float noiseA = textureLod(plasmaNoiseA, uv + plasmaOffsets.xy, 0.0).r;
    float noiseB = textureLod(plasmaNoiseB, uv - plasmaOffsets.zw, 0.0).r;
    float plasmaBase = clamp(1.0 - abs(noiseA - noiseB), 0.0, 1.0);
    float plasma = pow(plasmaBase, mix(plasmaSharpness.x, plasmaSharpness.y, edgePlasma));

    vec3 hit = vec3(0.0);
    for (int i = 0; i < plasmaHitCount; i++) {
        float falloff = clamp(1.0 - distance(pixelPos, plasmaHits[i].xy) / plasmaHits[i].z, 0.0, 1.0);
        hit += plasmaHitColors[i] * (plasmaHits[i].w * falloff);
    }

    float plasmaBroad = pow(plasmaBase, plasmaSharpness.x);
    vec3 light = edge * plasmaEdgeGlow * mix(1.0, plasmaBroad, plasma_GLOW_NOISE)
               + (plasmaEdgeColor * edgePlasma + plasmaCenterColor) * plasma
               + hit * mix(plasma, 1.0, plasma_HIT_UNMASKED)
               + plasmaFlash;

    // Additive light inside the GL_SRC_ALPHA / GL_ONE_MINUS_SRC_ALPHA pass: output a' and rgb' so
    // that rgb' * a' + bg * (1 - a') == rgb * a + light + bg * (1 - a') — exact additive light,
    // except that the background is dimmed by a' - a where the light is bright.
    vec3 premul = color.rgb * color.a + light;
    float alpha = clamp(max(color.a, max(premul.r, max(premul.g, premul.b))), 0.0, 1.0);
    if (alpha <= 0.0) {
        return color;
    }
    return vec4(min(premul / alpha, vec3(1.0)), alpha);
}

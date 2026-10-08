#version 330 core
// The Ark slipspace tube, additive and tone-mapped. Layers (planning/implementations/
// ark-portal-visuals-pass.md):
//   - streaks: the Halo bitmap (white streaks on black, brightest at the mouth) mixed with
//     procedural filaments - three octaves of ridged noise stretched along the tube and scrolled
//     by `flow` (positive = toward the mouth, v = 1), sharpened;
//   - colour: white-hot at the mouth, the body tint, violet deep inside; brightness fades with depth;
//   - depth: the back wall is dimmed except where it is seen through the mouth ellipse;
//   - lightning: Reach's palettized plasma (|noiseA - noiseB| indexes a palette that is violet near
//     0) crawling along the wall, gated by a slow noise so it comes in bursts, strongest near the mouth.
// Reach-style alpha black point on the streak brightness. Double-sided (the renderer disables
// culling), no depth.
out vec4 FragColor;

in vec2 vUv;
in float vDepth;
in float vFront;
in vec2 vMouth;

uniform sampler2D tubeMap;    // unit 0: Halo streak bitmap
uniform sampler2D noiseA;     // unit 1: tiling noise
uniform sampler2D noiseB;     // unit 2: tiling noise
uniform sampler2D palette;    // unit 3: lightning palette

uniform vec3 color;           // body tint x intensity (HDR)
uniform vec3 hotColor;        // white-hot tint x intensity (HDR)
uniform vec3 deepColor;       // deep violet tint x intensity (HDR)
uniform float alpha;          // master x fade
uniform float blackPoint;     // cut below, stretched above
uniform float exposure;       // tone-mapping exposure

uniform float bitmapMix;      // Halo bitmap share of the streaks (0..1)
uniform vec4 streak;          // x = u repeats (whole), y = v scale, z = sharpness, w = octave gain
uniform float streakGain;     // procedural streak brightness
uniform float flow;           // scroll along v, noise widths (CPU-integrated)
uniform vec2 hotSpan;         // x = white-hot depth, y = violet fully in by this depth (fractions of the length)
uniform float depthFade;      // brightness e-folds over the length
uniform float backDim;        // back wall brightness
uniform float backMouth;      // 1 = the back wall seen through the mouth is not dimmed

uniform vec4 arc;             // x = intensity, y = phase, z = u scale (whole), w = v scale
uniform float arcGate;        // gate threshold
uniform float arcReach;       // lightning fades out by this depth
uniform float paletteV;       // palette row
uniform float wallSign;       // 0 = both walls; +1 = only the wall facing the viewer; -1 = only the back wall

vec3 tonemap(vec3 hdr) {
    return vec3(1.0) - exp(-hdr * exposure);
}

float blackPointOf(float a, float point) {
    return clamp((a - point) / max(1.0 - point, 0.001), 0.0, 1.0);
}

float ridge(float n) {
    return 1.0 - abs(2.0 * n - 1.0);
}

// Three ridged octaves (A, B, A - GLSL 3.30 cannot index samplers in a loop) stretched along the
// tube; the u repeat is a whole number so the seam does not show.
float filaments(vec2 uv) {
    float uRep = max(floor(streak.x + 0.5), 1.0);
    float sharp = max(streak.z, 0.5);
    float gain = clamp(streak.w, 0.0, 1.0);
    float amp = 1.0;
    float norm = 0.0;
    float sum = 0.0;
    vec2 p0 = vec2(uv.x * uRep, uv.y * streak.y - flow);
    sum += amp * pow(ridge(texture(noiseA, p0).r), sharp);
    norm += amp;
    amp *= gain;
    vec2 p1 = vec2(uv.x * uRep * 2.0, uv.y * streak.y * 1.5 - flow * 1.4) + vec2(0.37, 0.11);
    sum += amp * pow(ridge(texture(noiseB, p1).r), sharp);
    norm += amp;
    amp *= gain;
    vec2 p2 = vec2(uv.x * uRep * 4.0, uv.y * streak.y * 2.25 - flow * 1.8) + vec2(0.71, 0.53);
    sum += amp * pow(ridge(texture(noiseA, p2).r), sharp);
    norm += amp;
    return sum / max(norm, 1e-4);
}

vec3 lightning(vec2 uv) {
    if (arc.x <= 0.0) {
        return vec3(0.0);
    }
    float uS = max(floor(arc.z + 0.5), 1.0);
    vec2 drift = vec2(arc.y * 0.13, arc.y);
    float a = texture(noiseA, vec2(uv.x * uS, uv.y * arc.w) + drift).r;
    float b = texture(noiseB, vec2(uv.x * uS * 1.31, uv.y * arc.w * 1.31) - drift).r;
    vec3 web = texture(palette, vec2(abs(a - b), paletteV)).rgb;
    float gate = smoothstep(arcGate, 1.0, texture(noiseB, vec2(uv.x, uv.y * 0.5) + vec2(arc.y * 0.07, -arc.y * 0.05)).r);
    float reach = 1.0 - smoothstep(0.0, max(arcReach, 1e-3), vDepth);
    return web * (gate * reach * arc.x);
}

void main() {
    // Split passes (campaign: back wall below the fleets, front wall above them).
    if (wallSign > 0.5 && vFront < 0.0) {
        discard;
    }
    if (wallSign < -0.5 && vFront >= 0.0) {
        discard;
    }
    vec4 t = texture(tubeMap, vUv);
    float bitmap = max(t.r, max(t.g, t.b)) * t.a;
    float proc = filaments(vUv) * streakGain;
    float base = mix(proc, bitmap, clamp(bitmapMix, 0.0, 1.0));

    float hotEnd = max(hotSpan.x, 1e-3);
    vec3 tint = mix(hotColor, color, smoothstep(0.0, hotEnd, vDepth));
    tint = mix(tint, deepColor, smoothstep(hotEnd, max(hotSpan.y, hotEnd + 1e-3), vDepth));
    float fade = exp(-depthFade * vDepth);

    // The back wall is only visible through the mouth ellipse; elsewhere the front wall covers it.
    float mouth = 1.0 - smoothstep(0.85, 1.0, length(vMouth));
    float back = mix(backDim, 1.0, clamp(backMouth, 0.0, 1.0) * mouth);
    float wall = mix(back, 1.0, smoothstep(-0.25, 0.25, vFront));

    vec3 c = tonemap(tint * base * fade * wall) * blackPointOf(base, blackPoint) * alpha;
    c += tonemap(lightning(vUv) * fade * wall) * alpha;
    if (max(c.r, max(c.g, c.b)) <= 0.001) {
        discard;
    }
    FragColor = vec4(c, 1.0);
}

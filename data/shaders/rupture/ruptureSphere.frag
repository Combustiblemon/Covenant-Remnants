#version 330 core
out vec4 FragColor;

in vec2 TexCoord;

// Slipspace rupture spheres (Fleet Slipspace Jump), remade from the Halo: Reach slipspace bomb
// (m45 ending, 045lf_jorge). One quad per sphere; p = (pixel - center) / radius, so the sphere's
// silhouette is |p| = 1. The Reach effect draws these as particles on two UV-sphere meshes; here
// they are drawn analytically with the meshes' exact UV layout (fitted from their vertices).
//
// Modes (RuptureSphereShader sets the blend function per mode):
//   0 GLOW         slipspace_sphere_glow: flat HDR colour, tone-mapped, plus a local bloom halo.
//                  Alpha blend.
//   1 BLACK        slipspace_sphere_multiply, made pure black (developer decision). Alpha blend.
//   2 WEB          slipspace_sphere_plasma: palettized plasma - two scrolling noise maps, their
//                  difference indexes a palette that is violet only near 0, so only the lines where
//                  the noises cross light up - masked at the poles. Side view (pole on screen Y).
//                  Additive.
//   3 SHELL        blast_radius: beam band on a shell seen from its pole (a soft ring). Additive.
//   4 PLASMA_SHELL plasma_sphere: palettized plasma on that shell. Additive.
//   5 RING         grab-radius marker during the charge (not in Reach). Additive.
//   6 LIGHT        Reach's sphere lights as a local glow, centre-weighted, out to the light's
//                  attenuation end. Additive.
uniform vec2 resolution;   // screen size, screen units
uniform vec2 center;       // sphere center, screen units
uniform float radius;      // sphere radius, screen units
uniform int mode;

uniform vec3 color;        // HDR colour, before intensity
uniform float intensity;
uniform float alpha;
uniform float exposure;    // tone-mapping exposure
uniform float master;      // whole-effect fade (abort)

uniform float halo;        // GLOW: bloom halo strength
uniform float haloWidth;   // GLOW: bloom falloff, in radii
uniform float ringWidth;   // RING: band width, in radii
uniform float lightStart;  // LIGHT: full-strength radius, in radii

uniform sampler2D noiseA;
uniform sampler2D noiseB;
uniform sampler2D palette;
uniform sampler2D alphaMap;
uniform vec4 xformA;       // noiseA: uv * xy + zw
uniform vec4 xformB;       // noiseB: uv * xy + zw
uniform float paletteV;
uniform float uOffset;     // mesh texcoord offset around the pole axis

const float PI = 3.14159265359;
const float TAU = 6.28318530718;

vec3 tonemap(vec3 hdr) {
    return vec3(1.0) - exp(-hdr * exposure);
}

// Disc coverage, anti-aliased over ~1.5 pixels at the silhouette.
float discCoverage(float d) {
    float aa = 1.5 / max(radius, 1.0);
    return 1.0 - smoothstep(1.0 - aa, 1.0, d);
}

// |noiseA - noiseB| looked up in the palette: Reach's palettized plasma.
vec3 plasma(vec2 uv) {
    float a = texture(noiseA, uv * xformA.xy + xformA.zw).r;
    float b = texture(noiseB, uv * xformB.xy + xformB.zw).r;
    return texture(palette, vec2(abs(a - b), paletteV)).rgb;
}

// UVs of slipspace_sphere (pole = mesh X, v = colatitude / pi, u = atan2(z, y) / 2pi - 0.1575),
// seen from the side: mesh X is screen Y, mesh Z faces the camera.
vec2 sideViewUv(vec2 p, float d) {
    vec3 n = vec3(p, sqrt(max(0.0, 1.0 - d * d)));
    float v = acos(clamp(n.y, -1.0, 1.0)) / PI;
    float u = atan(n.z, n.x) / TAU + uOffset;
    return vec2(u, v);
}

// UVs of blast_radius (pole = mesh Z, v = colatitude / pi, u = atan2(y, x) / 2pi + 0.75), seen
// from its pole. Only v of the front face; the back face sits at 1 - v on the same pixel.
vec2 poleViewUv(vec2 p, float d) {
    float v = acos(clamp(sqrt(max(0.0, 1.0 - d * d)), -1.0, 1.0)) / PI;
    float u = atan(p.y, p.x) / TAU + uOffset;
    return vec2(u, v);
}

void main() {
    vec2 pixelPos = TexCoord * resolution;
    vec2 p = (pixelPos - center) / radius;
    float d = length(p);

    vec4 outColor;
    if (mode == 0) {
        vec3 c = tonemap(color * intensity);
        float body = discCoverage(d);
        float bloom = halo * exp(-max(d - 1.0, 0.0) / max(haloWidth, 0.001));
        outColor = vec4(c, max(body, bloom) * alpha);
    } else if (mode == 1) {
        outColor = vec4(0.0, 0.0, 0.0, discCoverage(d) * alpha);
    } else if (mode == 2) {
        if (d >= 1.0) {
            discard;
        }
        vec2 uv = sideViewUv(p, d);
        float mask = texture(alphaMap, uv).a;
        vec3 web = tonemap(plasma(uv) * intensity) * mask * alpha * discCoverage(d);
        outColor = vec4(web, 1.0);
    } else if (mode == 3) {
        if (d >= 1.0) {
            discard;
        }
        vec2 uv = poleViewUv(p, d);
        float band = texture(alphaMap, uv).r + texture(alphaMap, vec2(uv.x, 1.0 - uv.y)).r;
        outColor = vec4(tonemap(color * intensity * band) * alpha * discCoverage(d), 1.0);
    } else if (mode == 4) {
        if (d >= 1.0) {
            discard;
        }
        vec2 uv = poleViewUv(p, d);
        vec3 c = plasma(uv) + plasma(vec2(uv.x, 1.0 - uv.y));
        outColor = vec4(tonemap(c * color * intensity) * alpha * discCoverage(d), 1.0);
    } else if (mode == 5) {
        float band = 1.0 - smoothstep(0.0, ringWidth, abs(d - 1.0));
        float glow = exp(-abs(d - 1.0) / max(ringWidth * 4.0, 0.001)) * 0.35;
        outColor = vec4(tonemap(color * intensity) * (band * band + glow) * alpha, 1.0);
    } else {
        // Centre-weighted, so it reads as glow around the source rather than a lit disc; the
        // light's full-strength radius only sharpens the outer edge.
        float fall = 1.0 - smoothstep(0.0, 1.0, d);
        float fade = fall * fall * (1.0 - smoothstep(lightStart, 1.0, d) * 0.5);
        outColor = vec4(tonemap(color * intensity) * fade * alpha, 1.0);
    }

    // Additive modes carry their strength in rgb (blend ONE, ONE); alpha modes in a.
    if (mode >= 2) {
        outColor.rgb *= master;
        if (max(outColor.r, max(outColor.g, outColor.b)) <= 0.001) {
            discard;
        }
    } else {
        outColor.a *= master;
        if (outColor.a <= 0.002) {
            discard;
        }
    }
    FragColor = outColor;
}

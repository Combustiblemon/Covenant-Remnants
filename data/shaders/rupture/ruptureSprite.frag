#version 330 core
out vec4 FragColor;

in vec2 vUv;
in vec2 vLocal;
in vec4 vColor;
in vec4 vParams;

// Halo: Reach particle sprites: the slipspace rupture (Fleet Slipspace Jump, the m45 slipspace
// bomb) and the plasma torpedoes (the m45 space Phantom torpedo). RuptureSpriteShader draws them in
// batches, one mode and texture set per batch.
//
// Modes:
//   0 PLASMA         Reach palettized_2d_plasma (charge stream, burst clouds, plasma flames): two
//                    scrolling noise maps, |a - b| indexes the palette row vParams.x; the sprite is
//                    sphere-warped (warp), zoomed by vParams.z, offset per particle by vParams.w,
//                    shaped by the alpha map with black point vParams.y. Additive.
//   1 TEXTURE_ADD    textured sprite (sparks, flares, torpedo trail), black point vParams.y.
//                    Additive.
//   2 SMOKE          Reach diffuse_plus_billboard_alpha with a white base: the tint shaped by the
//                    alpha map, black point vParams.y. Additive.
//   3 TEXTURE_ALPHA  textured sprite (twinkles, torpedo smoke), black point vParams.y. Alpha
//                    blend.
//   4 DISTORTION     heat-haze ring: the offset map's RG (0.5 = none) pushes the screen copy;
//                    vParams.x = offset scale in screen UV. Alpha blend.
//   5 PALETTE_ADD    Reach palettized_plus_sprite_alpha (torpedo muzzle flash): the sprite's
//                    intensity indexes the palette (row vParams.x), shaped by the sprite's alpha
//                    with black point vParams.y. Additive.
//   6 PALETTE_ALPHA  Reach palettized_plus_billboard_alpha (torpedo fireball): the base map,
//                    sphere-warped (warp) and zoomed by vParams.z, indexes the palette at row
//                    vParams.x; shaped by the alpha map with black point vParams.y. Alpha blend.
//   7 TEXTURE_ADD_ROT as 1, with the texture rotated about its centre by vParams.w (radians) and
//                    mirrored in u when vParams.z < 0 (Halo 3 Ark flashes: random rotation /
//                    mirror). Additive.
//   8 ANALYTIC_RING  Ark rings: an analytic ring in sprite-local space (an ellipse when the quad
//                    is one): white core line (ringCore), body in the sprite colour, chromatic
//                    fringes inside (ringInner) / outside (ringOuter), interior fill. vParams:
//                    x = aspect (halfLength / halfWidth), y = line radius (local units), z = core
//                    width (halfWidth units), w = fill. ringShape: x = body width × core,
//                    y = fringe offset, z = fringe width, w = fringe intensity. Additive.
//   9 REFRACT_RING   Ark refraction front: a thin ring that pushes the screen copy outward across
//                    its line. vParams: x = push scale in screen UV, y = line radius, z = width,
//                    w = aspect. Alpha blend.
//   10 PORTAL_WINDOW Ark mouth interior: near-black base colour with blobby plasma veins
//                    (|baseMap - noiseB| -> palette), soft elliptical edge. vParams: x = drift
//                    phase, y = vein scale, z = vein intensity, w = edge softness. Alpha blend.
uniform int mode;
uniform float exposure;   // tone-mapping exposure
uniform float master;     // whole-effect fade (abort)
uniform float halfSign;   // 0 = whole sprite; ±1 = only the half with sign(vLocal.x) == halfSign (Ark split passes)

uniform sampler2D baseMap;     // PLASMA: noise A; TEXTURE_*, PALETTE_*: the sprite; DISTORTION: offset map
uniform sampler2D noiseB;      // PLASMA
uniform sampler2D palette;     // PLASMA, PALETTE_*
uniform sampler2D alphaMap;    // PLASMA, SMOKE, PALETTE_ALPHA
uniform sampler2D screenCopy;  // DISTORTION

uniform vec4 xformA;           // PLASMA noise A: uv * xy + zw (zw scrolls with time)
uniform vec4 xformB;           // PLASMA noise B
uniform float warp;            // PLASMA, PALETTE_ALPHA: Reach sphere_warp_scale
uniform vec4 viewportPx;       // DISTORTION, REFRACT_RING: GL viewport x, y, width, height in pixels

uniform vec3 ringCore;         // ANALYTIC_RING: core colour × intensity
uniform vec3 ringInner;        // ANALYTIC_RING: inner fringe colour
uniform vec3 ringOuter;        // ANALYTIC_RING: outer fringe colour
uniform vec4 ringShape;        // ANALYTIC_RING: body width × core, fringe offset, fringe width, fringe intensity

// Signed distance from the ring line at local radius r0 for a quad of aspect a (halfLength /
// halfWidth), in halfWidth units: the local gradient is scaled by (1 / a, 1) to screen proportions.
float ringDistance(float r0, float a) {
    float len = max(length(vLocal), 1e-4);
    vec2 g = (vLocal / len) / vec2(max(a, 0.05), 1.0);
    return (len - r0) / max(length(g), 1e-4);
}

vec3 tonemap(vec3 hdr) {
    return vec3(1.0) - exp(-hdr * exposure);
}

// Reach's alpha black point: everything below it is cut, the rest stretched back to 0..1.
float blackPoint(float a, float point) {
    return clamp((a - point) / max(1.0 - point, 0.001), 0.0, 1.0);
}

void main() {
    if (halfSign > 0.5 && vLocal.x < 0.0) {
        discard;
    }
    if (halfSign < -0.5 && vLocal.x >= 0.0) {
        discard;
    }
    vec4 outColor;
    if (mode == 0) {
        float r2 = min(dot(vLocal, vLocal), 1.0);
        vec2 warped = vLocal * (1.0 + warp * (1.0 - sqrt(1.0 - r2)));
        vec2 uv = warped * vParams.z * 0.5 + 0.5 + vec2(vParams.w, vParams.w * 1.37);
        float a = texture(baseMap, uv * xformA.xy + xformA.zw).r;
        float b = texture(noiseB, uv * xformB.xy + xformB.zw).r;
        vec3 c = texture(palette, vec2(abs(a - b), vParams.x)).rgb;
        float shape = blackPoint(texture(alphaMap, vUv).a, vParams.y);
        outColor = vec4(tonemap(c * vColor.rgb) * shape * vColor.a * master, 1.0);
    } else if (mode == 1) {
        vec4 t = texture(baseMap, vUv);
        outColor = vec4(tonemap(t.rgb * vColor.rgb) * blackPoint(t.a, vParams.y) * vColor.a * master, 1.0);
    } else if (mode == 2) {
        float shape = blackPoint(texture(alphaMap, vUv).a, vParams.y);
        outColor = vec4(tonemap(vColor.rgb) * shape * vColor.a * master, 1.0);
    } else if (mode == 3) {
        vec4 t = texture(baseMap, vUv);
        outColor = vec4(tonemap(t.rgb * vColor.rgb), blackPoint(t.a, vParams.y) * vColor.a * master);
    } else if (mode == 5) {
        vec4 t = texture(baseMap, vUv);
        vec4 c = texture(palette, vec2(t.r, vParams.x));
        float shape = blackPoint(t.a, vParams.y);
        outColor = vec4(tonemap(c.rgb * vColor.rgb) * c.a * shape * vColor.a * master, 1.0);
    } else if (mode == 7) {
        vec2 uv = vUv - 0.5;
        if (vParams.z < 0.0) uv.x = -uv.x;
        float c = cos(vParams.w);
        float s = sin(vParams.w);
        uv = vec2(c * uv.x - s * uv.y, s * uv.x + c * uv.y) + 0.5;
        if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
            discard;
        }
        vec4 t = texture(baseMap, uv);
        outColor = vec4(tonemap(t.rgb * vColor.rgb) * blackPoint(t.a, vParams.y) * vColor.a * master, 1.0);
    } else if (mode == 6) {
        float r2 = min(dot(vLocal, vLocal), 1.0);
        vec2 warped = vLocal * (1.0 + warp * (1.0 - sqrt(1.0 - r2)));
        vec2 uv = warped * vParams.z * 0.5 + 0.5 + vec2(vParams.w, vParams.w * 1.37);
        float index = texture(baseMap, uv * xformA.xy + xformA.zw).r;
        vec3 c = texture(palette, vec2(index, vParams.x)).rgb;
        float shape = blackPoint(texture(alphaMap, vUv).a, vParams.y);
        outColor = vec4(tonemap(c * vColor.rgb), shape * vColor.a * master);
    } else if (mode == 8) {
        float d = ringDistance(vParams.y, vParams.x);
        float wc = max(vParams.z, fwidth(d));         // at least a pixel wide
        float energy = vParams.z / wc;                  // thinner than a pixel: keep the energy
        float core = exp(-0.5 * d * d / (wc * wc)) * energy;
        float wb = wc * max(ringShape.x, 1.0);
        float body = exp(-0.5 * d * d / (wb * wb));
        float wf = max(ringShape.z, fwidth(d));
        float din = d + ringShape.y;
        float dout = d - ringShape.y;
        float fin = exp(-0.5 * din * din / (wf * wf));
        float fout = exp(-0.5 * dout * dout / (wf * wf));
        float fill = vParams.w * (1.0 - smoothstep(-wb, 0.0, d));
        float s = max(vColor.r, max(vColor.g, vColor.b));
        vec3 hdr = s * (ringCore * core + (ringInner * fin + ringOuter * fout) * ringShape.w) + vColor.rgb * (body + fill);
        outColor = vec4(tonemap(hdr) * vColor.a * master, 1.0);
    } else if (mode == 10) {
        // Ark portal window: the mouth interior as a look into another dimension - a near-black
        // base with slow blobby plasma veins (|noiseA - noiseB| -> palette), soft elliptical edge.
        // vParams: x = drift phase (CPU-integrated), y = vein scale, z = vein intensity, w = edge softness.
        float len = length(vLocal);
        float coverage = 1.0 - smoothstep(1.0 - max(vParams.w, 0.01), 1.0, len);
        vec2 uv = vLocal * vParams.y * 0.5;
        float a = texture(baseMap, uv + vec2(vParams.x, vParams.x * 0.37)).r;
        float b = texture(noiseB, uv * 1.31 - vec2(vParams.x * 0.6, vParams.x)).r;
        vec3 veins = texture(palette, vec2(abs(a - b), 0.5)).rgb * vParams.z;
        outColor = vec4(tonemap(vColor.rgb + veins), coverage * vColor.a * master);
    } else if (mode == 9) {
        float d = ringDistance(vParams.y, vParams.w);
        vec2 n = vec2(dFdx(d), dFdy(d));
        n = n / max(length(n), 1e-6);
        float t = d / max(vParams.z, 1e-3);
        float push = 2.33 * t * exp(-t * t);
        vec2 offset = n * push * vParams.x * vColor.a * master * vec2(1.0, viewportPx.z / max(viewportPx.w, 1.0));
        vec2 screenUv = (gl_FragCoord.xy - viewportPx.xy) / viewportPx.zw;
        vec3 behind = texture(screenCopy, screenUv + offset).rgb;
        outColor = vec4(behind, smoothstep(0.0, 0.0015, length(offset)));
    } else {
        vec2 offset = (texture(baseMap, vUv).rg - 0.5) * 2.0 * vParams.x * vColor.a * master;
        vec2 screenUv = (gl_FragCoord.xy - viewportPx.xy) / viewportPx.zw;
        vec3 behind = texture(screenCopy, screenUv + offset).rgb;
        outColor = vec4(behind, smoothstep(0.0, 0.0015, length(offset)));
    }

    if (mode == 3 || mode == 4 || mode == 6 || mode == 9 || mode == 10) {
        if (outColor.a <= 0.002) {
            discard;
        }
    } else if (max(outColor.r, max(outColor.g, outColor.b)) <= 0.001) {
        discard;
    }
    FragColor = outColor;
}

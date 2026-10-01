#version 330 core
out vec4 FragColor;

in vec2 vUv;
in vec2 vLocal;
in vec4 vColor;
in vec4 vParams;

// Slipspace rupture sprites (Fleet Slipspace Jump), remade from the Halo: Reach slipspace bomb
// particles. RuptureSpriteShader draws them in batches, one mode and texture set per batch.
//
// Modes:
//   0 PLASMA         Reach palettized_2d_plasma (charge stream, burst clouds, plasma flames): two
//                    scrolling noise maps, |a - b| indexes the palette row vParams.x; the sprite is
//                    sphere-warped (warp), zoomed by vParams.z, offset per particle by vParams.w,
//                    shaped by the alpha map with black point vParams.y. Additive.
//   1 TEXTURE_ADD    textured sprite (sparks, flares). Additive.
//   2 SMOKE          Reach diffuse_plus_billboard_alpha with a white base: the tint shaped by the
//                    alpha map, black point vParams.y. Additive.
//   3 TEXTURE_ALPHA  textured sprite (twinkles). Alpha blend.
//   4 DISTORTION     heat-haze ring: the offset map's RG (0.5 = none) pushes the screen copy;
//                    vParams.x = offset scale in screen UV. Alpha blend.
uniform int mode;
uniform float exposure;   // tone-mapping exposure
uniform float master;     // whole-effect fade (abort)

uniform sampler2D baseMap;     // PLASMA: noise A; TEXTURE_*: the sprite; DISTORTION: offset map
uniform sampler2D noiseB;      // PLASMA
uniform sampler2D palette;     // PLASMA
uniform sampler2D alphaMap;    // PLASMA, SMOKE
uniform sampler2D screenCopy;  // DISTORTION

uniform vec4 xformA;           // PLASMA noise A: uv * xy + zw (zw scrolls with time)
uniform vec4 xformB;           // PLASMA noise B
uniform float warp;            // PLASMA: Reach sphere_warp_scale
uniform vec4 viewportPx;       // DISTORTION: GL viewport x, y, width, height in pixels

vec3 tonemap(vec3 hdr) {
    return vec3(1.0) - exp(-hdr * exposure);
}

// Reach's alpha black point: everything below it is cut, the rest stretched back to 0..1.
float blackPoint(float a, float point) {
    return clamp((a - point) / max(1.0 - point, 0.001), 0.0, 1.0);
}

void main() {
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
        outColor = vec4(tonemap(t.rgb * vColor.rgb) * t.a * vColor.a * master, 1.0);
    } else if (mode == 2) {
        float shape = blackPoint(texture(alphaMap, vUv).a, vParams.y);
        outColor = vec4(tonemap(vColor.rgb) * shape * vColor.a * master, 1.0);
    } else if (mode == 3) {
        vec4 t = texture(baseMap, vUv);
        outColor = vec4(tonemap(t.rgb * vColor.rgb), t.a * vColor.a * master);
    } else {
        vec2 offset = (texture(baseMap, vUv).rg - 0.5) * 2.0 * vParams.x * vColor.a * master;
        vec2 screenUv = (gl_FragCoord.xy - viewportPx.xy) / viewportPx.zw;
        vec3 behind = texture(screenCopy, screenUv + offset).rgb;
        outColor = vec4(behind, smoothstep(0.0, 0.0015, length(offset)));
    }

    if (mode == 3 || mode == 4) {
        if (outColor.a <= 0.002) {
            discard;
        }
    } else if (max(outColor.r, max(outColor.g, outColor.b)) <= 0.001) {
        discard;
    }
    FragColor = outColor;
}

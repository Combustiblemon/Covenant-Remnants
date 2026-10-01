#version 330 core
// One vertex of a rupture sprite quad; the quads are built on the CPU (RuptureSpriteShader).
layout (location = 0) in vec2 aPos;     // NDC
layout (location = 1) in vec2 aUv;      // texture coordinates (atlas cell already applied)
layout (location = 2) in vec2 aLocal;   // -1..1 across the sprite (plasma sphere warp)
layout (location = 3) in vec4 aColor;   // rgb = HDR colour x intensity, a = alpha
layout (location = 4) in vec4 aParams;  // per-mode extras, see ruptureSprite.frag

out vec2 vUv;
out vec2 vLocal;
out vec4 vColor;
out vec4 vParams;

void main() {
    gl_Position = vec4(aPos, 0.0, 1.0);
    vUv = aUv;
    vLocal = aLocal;
    vColor = aColor;
    vParams = aParams;
}

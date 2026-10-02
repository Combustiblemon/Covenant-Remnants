#version 330 core
// One corner of the ship-sprite quad (BlinkRippleShader builds it on the CPU from the ship's
// sprite, centre and facing).
layout (location = 0) in vec2 aPos;    // NDC
layout (location = 1) in vec2 aUv;     // ship sprite texture coordinates
layout (location = 2) in vec2 aLocal;  // x: -1..1 across the hull, y: 0 at the stern .. 1 at the bow

out vec2 vUv;
out vec2 vLocal;

void main() {
    gl_Position = vec4(aPos, 0.0, 1.0);
    vUv = aUv;
    vLocal = aLocal;
}

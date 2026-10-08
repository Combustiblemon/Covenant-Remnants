#version 330 core
// The Halo 3 Ark slipspace tube mesh (ArkTubeMesh, drawn by ArkTubeShader): an orthographic
// top-down view of the funnel with a fixed tilt. The mesh runs along +x from its mouth at x = 0.
layout (location = 0) in vec3 aPos;   // mesh units
layout (location = 1) in vec2 aUv;

uniform vec2 originNdc;   // the mouth, in NDC
uniform vec2 alongNdc;    // NDC per world unit along the tube's axis (already pointing the way the tube extends)
uniform vec2 acrossNdc;   // NDC per world unit across the axis (left normal)
uniform vec2 scale;       // world units per mesh unit: x along, y across
uniform vec2 spin;        // cos, sin of the spin about the axis
uniform vec2 tilt;        // cos, sin of the view tilt (0 = axis flat in the plane)
uniform float meshLength; // ArkTubeMesh.LENGTH
uniform float mouthRadius;// ArkTubeMesh.MOUTH_RADIUS

out vec2 vUv;
out float vDepth;   // 0 at the mouth, 1 at the far end
out float vFront;   // +1 = the wall facing the viewer, -1 = the back wall
out vec2 vMouth;    // position in mouth-ellipse space (|vMouth| < 1 = seen through the mouth)

void main() {
    // Spin the section about the axis, then tilt the axis toward the viewer: the out-of-plane
    // component leans into the along direction, so the mouth reads as an ellipse. The tilt
    // rotation's depth is -x sin + z cos: +z is near the viewer and the far end recedes.
    float y = aPos.y * spin.x - aPos.z * spin.y;
    float z = aPos.y * spin.y + aPos.z * spin.x;
    float along = aPos.x * scale.x * tilt.x + z * scale.y * tilt.y;
    float across = y * scale.y;
    gl_Position = vec4(originNdc + alongNdc * along + acrossNdc * across, 0.0, 1.0);
    vUv = aUv;
    vDepth = aPos.x / max(meshLength, 1e-4);
    vFront = z / max(length(aPos.yz), 1e-4);
    float minor = max(mouthRadius * scale.y * tilt.y, 1e-3);
    vMouth = vec2(along / minor, y / max(mouthRadius, 1e-4));
}

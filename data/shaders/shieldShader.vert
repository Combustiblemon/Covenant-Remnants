#version 330 core
        layout (location = 0) in vec2 aPos;        // NDC coordinates for full-screen quad
layout (location = 1) in vec2 worldPoint;  // World coordinates of polygon point

out vec2 TexCoord;
out vec2 screenPos;           // Pre-calculated screen position
out vec2 shipRelPos;          // Pre-calculated ship-relative position
out float distanceFromCenter; // Pre-calculated distance from ship center
out float shipRelX;           // Pre-calculated ship-relative X coordinate for sweep

uniform vec2 resolution;
uniform vec2 center;
uniform vec2 scale;
uniform vec4 shipRotationMatrix; // Pre-calculated rotation matrix as vec4 (m00, m01, m10, m11)
uniform vec2 viewportScale;      // For world-to-screen conversion

void main() {
    // Pass through the NDC position for full-screen quad
    gl_Position = vec4(aPos, 0.0, 1.0);

    // Convert NDC to texture coordinates (0 to 1)
    TexCoord = aPos * 0.5 + 0.5;

    // Transform world point through all stages (moved from CPU)
    // Note: worldPoint is currently dummy (0,0) for quad vertices
    // The actual polygon points are passed as uniforms to the fragment shader
    vec2 scaledPoint = worldPoint * scale;
    vec2 translatedPoint = scaledPoint + center;
    vec2 rotatedPoint = vec2(
    shipRotationMatrix.x * (translatedPoint.x - center.x) + shipRotationMatrix.y * (translatedPoint.y - center.y) + center.x,
    shipRotationMatrix.z * (translatedPoint.x - center.x) + shipRotationMatrix.w * (translatedPoint.y - center.y) + center.y
    );
    vec2 screenPoint = rotatedPoint * viewportScale;

    // Pre-calculate values for fragment shader
    screenPos = screenPoint;

    // Pre-calculate ship-relative position
    vec2 screenPointRel = screenPoint - center;
    shipRelPos = vec2(
    shipRotationMatrix.x * screenPointRel.x + shipRotationMatrix.y * screenPointRel.y,
    shipRotationMatrix.z * screenPointRel.x + shipRotationMatrix.w * screenPointRel.y
    );

    // Pre-calculate ship-relative X coordinate for sweep animation
    shipRelX = shipRelPos.x;

    // Pre-calculate distance from center (useful for many effects)
    distanceFromCenter = length(screenPointRel);
}
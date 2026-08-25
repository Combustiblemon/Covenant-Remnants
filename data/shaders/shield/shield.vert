#version 330 core
layout (location = 0) in vec2 aPos; // NDC coordinates of the bounding-box quad

out vec2 TexCoord;

void main() {
    gl_Position = vec4(aPos, 0.0, 1.0);

    // Convert NDC to texture coordinates (0 to 1)
    TexCoord = aPos * 0.5 + 0.5;
}

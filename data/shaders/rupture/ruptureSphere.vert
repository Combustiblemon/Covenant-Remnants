#version 330 core
layout (location = 0) in vec2 aPos; // NDC corners of the sphere's bounding quad

out vec2 TexCoord;

void main() {
    gl_Position = vec4(aPos, 0.0, 1.0);

    // NDC to 0..1; the fragment shader scales by the screen size
    TexCoord = aPos * 0.5 + 0.5;
}

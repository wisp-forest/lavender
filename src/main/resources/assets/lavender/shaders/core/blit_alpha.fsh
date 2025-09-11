#version 150

uniform sampler2D InSampler;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec4 color = texture(InSampler, texCoord);
    color.a *= .5f; // There's only one usage of this shader in this mod and it uses this constant.

    fragColor = color;
}

#version 150

uniform sampler2D uScene;
uniform vec2 uInvSize;
uniform float uStrength;

in vec2 vTex;
out vec4 outColor;

void main() {
    vec4 c = texture(uScene, vTex);
    
    // Luma sharpening
    float l = dot(c.rgb, vec3(0.2126, 0.7152, 0.0722));
    float l_sharp = l + uStrength * (l - texture(uScene, vTex + uInvSize).r);
    
    outColor = vec4(mix(c.rgb, vec3(l_sharp), 0.5), c.a);
}
#version 150

uniform sampler2D uScene;
uniform vec2 uSceneSize;
uniform vec2 uOutputSize;

in vec2 vTex;
out vec4 outColor;

void main() {
    vec2 scale = uSceneSize / uOutputSize;
    vec2 center = vTex * uOutputSize;
    vec2 samplePos = center * scale;
    vec2 uv = samplePos / uSceneSize;
    
    // Bilinear upscale with slight sharpening
    vec4 c = texture(uScene, uv);
    
    // Conservative sharpening
    vec2 pixel = 1.0 / uSceneSize;
    float l = dot(c.rgb, vec3(0.2126, 0.7152, 0.0722));
    float l_sharp = l + 0.15 * (l - texture(uScene, uv + vec2(pixel.x, 0)).r);
    
    outColor = vec4(mix(c.rgb, vec3(l_sharp), 0.5), c.a);
}
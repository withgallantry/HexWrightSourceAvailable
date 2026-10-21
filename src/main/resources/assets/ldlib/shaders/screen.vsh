#version 150

#define SUPPORT_POSE_STACK

in vec3 Position;

out vec2 screenPos;
out vec2 guiPos;
out vec2 uv;

uniform float GuiScale;
uniform vec2 ScreenSize;

#ifdef SUPPORT_POSE_STACK
uniform mat4 PoseStack;
uniform mat4 ProjMat;
#endif

void main() {
    gl_Position = vec4(Position.xy, 0.0, 1.0);
    vec2 normalizedPos = gl_Position.xy * 0.5 + 0.5;
    screenPos = ScreenSize * vec2(normalizedPos.x, 1-normalizedPos.y);
    guiPos = screenPos / GuiScale;
    uv = gl_Position.xy;
    #ifdef SUPPORT_POSE_STACK
    gl_Position = vec4(((ProjMat) * PoseStack  * vec4(guiPos, 0.0, 1.0)).xy, 0.0, 1.0);
    #endif
}
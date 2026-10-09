#version 300 es
precision highp float;

layout(location = 0) in vec2 a_pos;
layout(location = 1) in vec2 a_uv;

uniform sampler2D u_disp;
uniform float u_strength;

out vec2 v_uv;

void main() {
    vec2 d = texture(u_disp, a_uv).rg * 2.0 - 1.0;
    vec2 p = a_pos + d * u_strength;

    v_uv = a_uv;
    gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
}

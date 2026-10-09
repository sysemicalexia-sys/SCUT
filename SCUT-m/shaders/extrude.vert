#version 300 es
precision highp float;

layout(location = 0) in vec3 a_pos;
layout(location = 1) in vec2 a_uv;

uniform mat4 u_proj;
uniform mat4 u_view;
uniform float u_depth;
uniform float u_slice;

out vec2 v_uv;

void main() {
    vec3 p = a_pos;
    p.z -= u_depth * u_slice;

    v_uv = a_uv;
    gl_Position = u_proj * u_view * vec4(p, 1.0);
}

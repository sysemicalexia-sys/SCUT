#version 300 es
precision mediump float;

in vec2 v_uv;
uniform sampler2D u_video;
out vec4 out_color;

void main() {
    out_color = texture(u_video, v_uv);
}

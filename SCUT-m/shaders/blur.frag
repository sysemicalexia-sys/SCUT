#version 300 es
precision mediump float;

in vec2 v_uv;
uniform sampler2D u_tex;
uniform vec2 u_texel;
out vec4 out_color;

void main() {
    vec3 sum = vec3(0.0);

    sum += texture(u_tex, v_uv + vec2(-u_texel.x, -u_texel.y)).rgb * 0.0625;
    sum += texture(u_tex, v_uv + vec2(0.0, -u_texel.y)).rgb * 0.125;
    sum += texture(u_tex, v_uv + vec2(u_texel.x, -u_texel.y)).rgb * 0.0625;

    sum += texture(u_tex, v_uv + vec2(-u_texel.x, 0.0)).rgb * 0.125;
    sum += texture(u_tex, v_uv).rgb * 0.25;
    sum += texture(u_tex, v_uv + vec2(u_texel.x, 0.0)).rgb * 0.125;

    sum += texture(u_tex, v_uv + vec2(-u_texel.x, u_texel.y)).rgb * 0.0625;
    sum += texture(u_tex, v_uv + vec2(0.0, u_texel.y)).rgb * 0.125;
    sum += texture(u_tex, v_uv + vec2(u_texel.x, u_texel.y)).rgb * 0.0625;

    out_color = vec4(sum, 1.0);
}

#include "core/warp/grid_warp.hpp"
#include <algorithm>
#include <cmath>

namespace scut::warp {

static float clamp01(float v) {
    return v < 0.0f ? 0.0f : (v > 1.0f ? 1.0f : v);
}

static float lerp(float a, float b, float t) {
    return a + (b - a) * t;
}

void GridWarp::resize(int columns, int rows) {
    cols = std::max(1, columns);
    rows = std::max(1, rows);
    base.resize(cols * rows);
    offset.assign(cols * rows, Vec2{});

    for (int y = 0; y < rows; y++) {
        for (int x = 0; x < cols; x++) {
            int i = y * cols + x;
            base[i].x = cols == 1 ? 0.5f : static_cast<float>(x) / static_cast<float>(cols - 1);
            base[i].y = rows == 1 ? 0.5f : static_cast<float>(y) / static_cast<float>(rows - 1);
        }
    }
}

void GridWarp::reset() {
    offset.assign(cols * rows, Vec2{});
}

void GridWarp::nudge_point(int index, Vec2 delta, float strength) {
    if (index < 0 || index >= static_cast<int>(base.size())) return;

    Vec2 center = base[index];
    float r2 = radius * radius;

    for (size_t i = 0; i < base.size(); i++) {
        float dx = base[i].x - center.x;
        float dy = base[i].y - center.y;
        float d2 = dx * dx + dy * dy;

        float w = std::exp(-d2 / r2) * strength;

        offset[i].x += delta.x * w;
        offset[i].y += delta.y * w;
    }
}

void GridWarp::set_point(int index, Vec2 target, float strength) {
    if (index < 0 || index >= static_cast<int>(base.size())) return;

    Vec2 current;
    current.x = base[index].x + offset[index].x;
    current.y = base[index].y + offset[index].y;

    Vec2 delta;
    delta.x = target.x - current.x;
    delta.y = target.y - current.y;

    nudge_point(index, delta, strength);
}

Vec2 GridWarp::sample_offset(float u, float v) const {
    if (base.empty()) return Vec2{};

    u = clamp01(u);
    v = clamp01(v);

    float gx = u * static_cast<float>(cols - 1);
    float gy = v * static_cast<float>(rows - 1);

    int x0 = static_cast<int>(std::floor(gx));
    int y0 = static_cast<int>(std::floor(gy));
    int x1 = std::min(x0 + 1, cols - 1);
    int y1 = std::min(y0 + 1, rows - 1);

    x0 = std::max(0, std::min(x0, cols - 1));
    y0 = std::max(0, std::min(y0, rows - 1));

    float fx = gx - std::floor(gx);
    float fy = gy - std::floor(gy);

    Vec2 o00 = offset[y0 * cols + x0];
    Vec2 o10 = offset[y0 * cols + x1];
    Vec2 o01 = offset[y1 * cols + x0];
    Vec2 o11 = offset[y1 * cols + x1];

    Vec2 top;
    top.x = lerp(o00.x, o10.x, fx);
    top.y = lerp(o00.y, o10.y, fx);

    Vec2 bottom;
    bottom.x = lerp(o01.x, o11.x, fx);
    bottom.y = lerp(o01.y, o11.y, fx);

    Vec2 out;
    out.x = lerp(top.x, bottom.x, fy);
    out.y = lerp(top.y, bottom.y, fy);
    return out;
}

std::vector<unsigned char> GridWarp::encode_rgba(int width, int height) const {
    if (width <= 0 || height <= 0) return {};

    std::vector<unsigned char> out(width * height * 4);

    for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
            float u = (static_cast<float>(x) + 0.5f) / static_cast<float>(width);
            float v = (static_cast<float>(y) + 0.5f) / static_cast<float>(height);

            Vec2 o = sample_offset(u, v);

            float rx = clamp01(o.x * 0.5f + 0.5f);
            float ry = clamp01(o.y * 0.5f + 0.5f);

            int idx = (y * width + x) * 4;
            out[idx + 0] = static_cast<unsigned char>(rx * 255.0f);
            out[idx + 1] = static_cast<unsigned char>(ry * 255.0f);
            out[idx + 2] = 0;
            out[idx + 3] = 255;
        }
    }

    return out;
}

}

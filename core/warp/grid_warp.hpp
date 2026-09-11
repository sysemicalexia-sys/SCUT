#pragma once
#include <vector>

namespace scut::warp {

struct Vec2 {
    float x = 0.0f;
    float y = 0.0f;
};

class GridWarp {
public:
    void resize(int columns, int rows);
    void reset();

    void nudge_point(int index, Vec2 delta, float strength = 1.0f);
    void set_point(int index, Vec2 target, float strength = 1.0f);

    std::vector<unsigned char> encode_rgba(int width, int height) const;

    int columns() const { return cols; }
    int rows_count() const { return rows; }

private:
    Vec2 sample_offset(float u, float v) const;

    int cols = 8;
    int rows = 8;
    float radius = 0.22f;
    std::vector<Vec2> base;
    std::vector<Vec2> offset;
};

}

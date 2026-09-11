#include "core/scut_graph.hpp"
#include "core/dsp/fft.hpp"
#include "core/warp/grid_warp.hpp"
#include <cassert>
#include <cstdio>
#include <cmath>

int main() {
    scut::Graph graph;
    int source = graph.add(scut::NodeType::Source);
    int warp = graph.add(scut::NodeType::WarpGrid);
    int output = graph.add(scut::NodeType::Output);

    graph.connect(source, warp);
    graph.connect(warp, output);

    assert(graph.all().size() == 3);

    std::vector<scut::dsp::Complex> signal(8);
    for (int i = 0; i < 8; i++) {
        signal[i] = scut::dsp::Complex(std::cos(i * 0.3f), 0.0f);
    }

    scut::dsp::fft(signal, false);
    scut::dsp::keep_between(signal, 60, 0, 20);
    scut::dsp::fft(signal, true);

    assert(signal.size() == 8);

    scut::warp::GridWarp grid;
    grid.resize(4, 4);
    grid.nudge_point(5, scut::warp::Vec2{0.1f, -0.1f}, 1.0f);

    auto tex = grid.encode_rgba(16, 16);
    assert(tex.size() == 16 * 16 * 4);

    std::printf("scut core ok\n");
    return 0;
}

#include "core/scut_graph.hpp"
#include "core/dsp/fft.hpp"
#include "core/warp/grid_warp.hpp"
#include <algorithm>
#include <cassert>
#include <cmath>
#include <cstdio>
#include <limits>
#include <random>

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

    scut::warp::GridWarp wide;
    wide.resize(5, 3);
    assert(wide.columns() == 5 && wide.rows_count() == 3);
    wide.nudge_point(14, scut::warp::Vec2{0.2f, 0.2f}, 1.0f);
    assert(wide.encode_rgba(32, 32).size() == 32 * 32 * 4);
    assert(wide.encode_rgba(0, 32).empty());
    assert(wide.encode_rgba(1 << 20, 1 << 20).empty());

    auto before = wide.encode_rgba(16, 16);
    float nan = std::numeric_limits<float>::quiet_NaN();
    wide.nudge_point(0, scut::warp::Vec2{nan, 0.0f}, 1.0f);
    wide.nudge_point(0, scut::warp::Vec2{0.0f, 0.0f}, nan);
    wide.nudge_point(-1, scut::warp::Vec2{1.0f, 1.0f}, 1.0f);
    wide.nudge_point(15, scut::warp::Vec2{1.0f, 1.0f}, 1.0f);
    assert(wide.encode_rgba(16, 16) == before);

    wide.resize(0, 100000);
    assert(wide.columns() == 1 && wide.rows_count() == 256);

    std::mt19937 rng(7);
    std::uniform_real_distribution<float> dist(-1.0f, 1.0f);
    const size_t n = 1024;
    std::vector<scut::dsp::Complex> x(n);
    for (auto& v : x) v = scut::dsp::Complex(dist(rng), dist(rng));
    auto spectrum = x;
    scut::dsp::fft(spectrum, false);

    double worst = 0.0;
    for (size_t k = 0; k < n; k += 7) {
        std::complex<double> sum = 0.0;
        for (size_t t = 0; t < n; t++) {
            double a = 2.0 * M_PI * static_cast<double>(k * t % n) / static_cast<double>(n);
            sum += std::complex<double>(x[t].real(), x[t].imag()) * std::polar(1.0, a);
        }
        worst = std::max(worst, std::abs(sum - std::complex<double>(spectrum[k].real(), spectrum[k].imag())));
    }
    assert(worst < 1e-2);

    std::vector<scut::dsp::Complex> empty;
    scut::dsp::fft(empty, false);
    assert(empty.empty());

    std::printf("scut core ok (fft max error %.2e)\n", worst);
    return 0;
}

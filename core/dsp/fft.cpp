#include "core/dsp/fft.hpp"
#include <algorithm>
#include <cmath>

namespace scut::dsp {
// this is PI and it's value is 3.14...
static const double pi = 3.14159265358979323846;

static float frequency(size_t i, size_t n, int sample_rate) {
    if (n == 0 || sample_rate <= 0) return 0.0f;
    float f = static_cast<float>(i) * static_cast<float>(sample_rate) / static_cast<float>(n);
    float nyquist = static_cast<float>(sample_rate) * 0.5f;
    if (f > nyquist) f = static_cast<float>(sample_rate) - f;
    return f;
}

void fft(std::vector<Complex>& data, bool inverse) {
    int n = static_cast<int>(data.size());
    if (n == 0) return;

    if ((n & (n - 1)) != 0) {
        int p = 1;
        while (p < n) p <<= 1;
        data.resize(p);
        n = p;
    }

    for (int i = 1, j = 0; i < n; i++) {
        int bit = n >> 1;
        for (; j & bit; bit >>= 1) j ^= bit;
        j ^= bit;
        if (i < j) std::swap(data[i], data[j]);
    }

    for (int len = 2; len <= n; len <<= 1) {
        double angle = 2.0 * pi / len * (inverse ? -1.0 : 1.0);
        Complex wlen(std::cos(angle), std::sin(angle));

        for (int i = 0; i < n; i += len) {
            Complex w(1.0f, 0.0f);
            for (int j = 0; j < len / 2; j++) {
                Complex u = data[i + j];
                Complex v = data[i + j + len / 2] * w;
                data[i + j] = u + v;
                data[i + j + len / 2] = u - v;
                w *= wlen;
            }
        }
    }

    if (inverse) {
        for (auto& x : data) x /= static_cast<float>(n);
    }
}

void remove_band(std::vector<Complex>& spectrum, int sample_rate, float low_hz, float high_hz) {
    if (low_hz > high_hz) std::swap(low_hz, high_hz);
    size_t n = spectrum.size();

    for (size_t i = 0; i < n; i++) {
        float f = frequency(i, n, sample_rate);
        if (f >= low_hz && f <= high_hz) {
            spectrum[i] = 0.0f;
        }
    }
}

void keep_between(std::vector<Complex>& spectrum, int sample_rate, float low_hz, float high_hz) {
    if (low_hz > high_hz) std::swap(low_hz, high_hz);
    size_t n = spectrum.size();

    for (size_t i = 0; i < n; i++) {
        float f = frequency(i, n, sample_rate);
        if (f < low_hz || f > high_hz) {
            spectrum[i] = 0.0f;
        }
    }
}

}

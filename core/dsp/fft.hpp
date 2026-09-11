#pragma once
#include <vector>
#include <complex>

namespace scut::dsp {

using Complex = std::complex<float>;

void fft(std::vector<Complex>& data, bool inverse);

void remove_band(std::vector<Complex>& spectrum, int sample_rate, float low_hz, float high_hz);

void keep_between(std::vector<Complex>& spectrum, int sample_rate, float low_hz, float high_hz);

}

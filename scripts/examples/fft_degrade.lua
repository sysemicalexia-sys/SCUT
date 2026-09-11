
local fft = scut.add_node("fft")
scut.set_param(fft, "sample_rate", 44100)
scut.set_param(fft, "low_hz", 0)
scut.set_param(fft, "high_hz", 3200)
scut.set_param(fft, "mode", 1)

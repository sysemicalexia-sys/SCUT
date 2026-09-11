#!/bin/sh

CXX="${CXX:-clang++}"
command -v "$CXX" >/dev/null 2>&1 || CXX=g++

mkdir -p bin
"$CXX" -std=c++17 -O2 -Wall -Wextra -I. \
    core/dsp/fft.cpp \
    core/warp/grid_warp.cpp \
    tests/core_test.cpp \
    -o bin/core_test

./bin/core_test

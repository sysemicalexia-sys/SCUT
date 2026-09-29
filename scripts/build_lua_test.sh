#!/bin/sh

CXX="${CXX:-clang++}"
CC="${CC:-clang}"
command -v "$CXX" >/dev/null 2>&1 || CXX=g++
command -v "$CC" >/dev/null 2>&1 || CC=gcc

mkdir -p bin/lua
for f in third_party/lua/*.c; do
    case "$f" in */lua.c|*/luac.c) continue ;; esac
    "$CC" -O2 -Ithird_party/lua -c "$f" -o "bin/lua/$(basename "$f" .c).o" || exit 1
done

"$CXX" -std=c++17 -O2 -Wall -Wextra -I. -Ithird_party/lua -Iapp/src/main/cpp \
    core/dsp/fft.cpp \
    core/warp/grid_warp.cpp \
    app/src/main/cpp/lua_binding.cpp \
    tests/lua_test.cpp \
    bin/lua/*.o \
    -o bin/lua_test || exit 1

./bin/lua_test

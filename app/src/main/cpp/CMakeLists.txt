cmake_minimum_required(VERSION 3.22.1)
project(scut)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Repo root
set(SCUT_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/../../../..)

# Lua 5.4
set(LUA_DIR ${SCUT_ROOT}/third_party/lua)

set(LUA_SOURCES
    ${LUA_DIR}/lapi.c     ${LUA_DIR}/lauxlib.c  ${LUA_DIR}/lbaselib.c
    ${LUA_DIR}/lcode.c    ${LUA_DIR}/lcorolib.c ${LUA_DIR}/lctype.c
    ${LUA_DIR}/ldblib.c   ${LUA_DIR}/ldebug.c   ${LUA_DIR}/ldo.c
    ${LUA_DIR}/ldump.c    ${LUA_DIR}/lfunc.c    ${LUA_DIR}/lgc.c
    ${LUA_DIR}/linit.c    ${LUA_DIR}/liolib.c   ${LUA_DIR}/llex.c
    ${LUA_DIR}/lmathlib.c ${LUA_DIR}/lmem.c     ${LUA_DIR}/loadlib.c
    ${LUA_DIR}/lobject.c  ${LUA_DIR}/lopcodes.c ${LUA_DIR}/loslib.c
    ${LUA_DIR}/lparser.c  ${LUA_DIR}/lstate.c   ${LUA_DIR}/lstring.c
    ${LUA_DIR}/lstrlib.c  ${LUA_DIR}/ltable.c   ${LUA_DIR}/ltablib.c
    ${LUA_DIR}/ltm.c      ${LUA_DIR}/lundump.c  ${LUA_DIR}/lutf8lib.c
    ${LUA_DIR}/lvm.c      ${LUA_DIR}/lzio.c
)

add_library(scut SHARED
    scut_jni.cpp
    lua_binding.cpp
    ${SCUT_ROOT}/core/dsp/fft.cpp
    ${SCUT_ROOT}/core/warp/grid_warp.cpp
    ${LUA_SOURCES}
)

target_include_directories(scut PRIVATE
    ${SCUT_ROOT}
    ${LUA_DIR}
)

# Deliberately not defining LUA_USE_LINUX: that pulls in dlfcn.h/readline
# build free of extra system-library dependencies.

find_library(log-lib log)
find_library(GLESv3-lib GLESv3)
find_library(android-lib android)

target_link_libraries(scut
    ${log-lib}
    ${GLESv3-lib}
    ${android-lib}
)

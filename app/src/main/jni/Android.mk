LOCAL_PATH := $(call my-dir)

PROJECT_ROOT := $(LOCAL_PATH)/../../../..
LUA_DIR := ../../../../third_party/lua

include $(CLEAR_VARS)

LOCAL_MODULE := scut

LOCAL_SRC_FILES := \
    ../cpp/scut_jni.cpp \
    ../cpp/lua_binding.cpp \
    ../../../../core/dsp/fft.cpp \
    ../../../../core/warp/grid_warp.cpp \
    $(addprefix $(LUA_DIR)/, \
        lapi.c lauxlib.c lbaselib.c lcode.c lcorolib.c lctype.c \
        ldblib.c ldebug.c ldo.c ldump.c lfunc.c lgc.c \
        linit.c liolib.c llex.c lmathlib.c lmem.c loadlib.c \
        lobject.c lopcodes.c loslib.c lparser.c lstate.c lstring.c \
        lstrlib.c ltable.c ltablib.c ltm.c lundump.c lutf8lib.c \
        lvm.c lzio.c)

LOCAL_C_INCLUDES := $(PROJECT_ROOT) $(LOCAL_PATH)/$(LUA_DIR) $(LOCAL_PATH)/../cpp

LOCAL_CPP_FEATURES := exceptions rtti
LOCAL_CFLAGS := -fvisibility=hidden
LOCAL_CPPFLAGS := -std=c++11
LOCAL_LDFLAGS := -Wl,-z,max-page-size=16384
LOCAL_LDLIBS := -llog

include $(BUILD_SHARED_LIBRARY)

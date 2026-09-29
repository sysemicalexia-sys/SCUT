#include "lua_binding.hpp"

extern "C" {
#include "lua.h"
#include "lauxlib.h"
#include "lualib.h"
}

#include <climits>
#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <new>
#include <stdexcept>

#ifdef __ANDROID__
#include <android/log.h>
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "SCUT", __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "SCUT", __VA_ARGS__)
#else
#define LOGE(...) (std::fprintf(stderr, "E/SCUT: " __VA_ARGS__), std::fputc('\n', stderr))
#define LOGI(...) (std::fprintf(stderr, "I/SCUT: " __VA_ARGS__), std::fputc('\n', stderr))
#endif

namespace scut::script {

namespace {

// Lua reports errors with longjmp. So in every function Lua calls: no C++
// object with a destructor may be alive when luaL_error / luaL_check* can
// fire, and luaL_error is never called from inside a catch block.

constexpr int kSafeLibs = LUA_GLIBK | LUA_COLIBK | LUA_TABLIBK | LUA_STRLIBK |
                          LUA_MATHLIBK | LUA_UTF8LIBK;
constexpr int kHookInterval = 1000;

void* budgetAlloc(void* ud, void* ptr, size_t osize, size_t nsize) {
    auto* budget = static_cast<MemoryBudget*>(ud);
    if (nsize == 0) {
        if (ptr != nullptr) {
            budget->used -= osize;
            std::free(ptr);
        }
        return nullptr;
    }
    size_t old = ptr != nullptr ? osize : 0;
    if (nsize > old && (budget->used - old) + nsize > budget->limit) return nullptr;
    void* grown = std::realloc(ptr, nsize);
    if (grown != nullptr) budget->used = budget->used - old + nsize;
    return grown;
}

int panicHandler(lua_State* L) {
    const char* msg = lua_type(L, -1) == LUA_TSTRING ? lua_tostring(L, -1) : "non-string error";
    LOGE("lua panic: %s", msg);
    return 0;
}

LuaEngine* engineFrom(lua_State* L) {
    void* ud = nullptr;
    lua_getallocf(L, &ud);
    return static_cast<MemoryBudget*>(ud)->owner;
}

// Installed once on the main state and inherited by every coroutine, so a
// coroutine kept from an earlier run can't spin unchecked later.
void deadlineHook(lua_State* L, lua_Debug*) {
    if (engineFrom(L)->deadlineExpired()) {
        // pcall() can swallow this error, so keep firing on every instruction
        lua_sethook(L, deadlineHook, LUA_MASKCOUNT, 1);
        luaL_error(L, "script exceeded its time budget");
    }
    if (lua_gethookcount(L) != kHookInterval) {
        lua_sethook(L, deadlineHook, LUA_MASKCOUNT, kHookInterval);
    }
}

int checkInt(lua_State* L, int arg) {
    lua_Integer v = luaL_checkinteger(L, arg);
    luaL_argcheck(L, v >= INT_MIN && v <= INT_MAX, arg, "integer out of range");
    return static_cast<int>(v);
}

double checkFinite(lua_State* L, int arg) {
    constexpr double kLimit = 1e9;
    double v = luaL_checknumber(L, arg);
    luaL_argcheck(L, std::isfinite(v) && std::fabs(v) <= kLimit, arg, "number must be finite and within +-1e9");
    return v;
}

int l_print(lua_State* L) {
    int n = lua_gettop(L);
    luaL_Buffer b;
    luaL_buffinit(L, &b);
    for (int i = 1; i <= n; i++) {
        if (i > 1) luaL_addchar(&b, '\t');
        luaL_tolstring(L, i, nullptr);
        luaL_addvalue(&b);
    }
    luaL_pushresult(&b);
    LOGI("lua: %s", lua_tostring(L, -1));
    return 0;
}

int l_add_node(lua_State* L) {
    const char* typeName = luaL_checkstring(L, 1);
    LuaEngine* engine = engineFrom(L);

    const char* err = nullptr;
    int id = 0;
    try {
        NodeType type;
        if (!nodeTypeFromName(typeName, type)) {
            err = "unknown node type";
        } else if (engine->graph().all().size() >= LuaEngine::kMaxNodes) {
            err = "node limit reached";
        } else {
            id = engine->graph().add(type);
        }
    } catch (const std::exception&) {
        err = "out of memory";
    }
    if (err != nullptr) return luaL_error(L, "scut.add_node: %s '%s'", err, typeName);

    lua_pushinteger(L, id);
    return 1;
}

int l_set_param(lua_State* L) {
    int id = checkInt(L, 1);
    const char* key = luaL_checkstring(L, 2);
    double value = checkFinite(L, 3);
    LuaEngine* engine = engineFrom(L);

    const char* err = nullptr;
    try {
        Node* node = engine->graph().find(id);
        if (node == nullptr) {
            err = "no such node";
        } else if (node->params.size() >= LuaEngine::kMaxParamsPerNode &&
                   node->params.find(key) == node->params.end()) {
            err = "too many params on node";
        } else {
            node->params[key] = static_cast<float>(value);
        }
    } catch (const std::exception&) {
        err = "out of memory";
    }
    if (err != nullptr) return luaL_error(L, "scut.set_param: %s (id %d)", err, id);
    return 0;
}

int l_get_node(lua_State* L) {
    const char* typeName = luaL_checkstring(L, 1);
    LuaEngine* engine = engineFrom(L);

    bool known = false;
    bool failed = false;
    int id = -1;
    try {
        NodeType type;
        known = nodeTypeFromName(typeName, type);
        if (known) {
            Node* node = engine->graph().firstOfType(type);
            if (node != nullptr) id = node->id;
        }
    } catch (const std::exception&) {
        failed = true;
    }
    if (failed) return luaL_error(L, "scut.get_node: out of memory");
    if (!known) return luaL_error(L, "scut.get_node: unknown node type '%s'", typeName);

    if (id < 0) {
        lua_pushnil(L);
    } else {
        lua_pushinteger(L, id);
    }
    return 1;
}

// scut.hotkey("slide_in", function() ... end) - stores the closure in the
// registry; triggerHotkey(name) pulls it back out later.
int l_hotkey(lua_State* L) {
    const char* name = luaL_checkstring(L, 1);
    luaL_checktype(L, 2, LUA_TFUNCTION);
    LuaEngine* engine = engineFrom(L);

    lua_pushvalue(L, 2);
    int ref = luaL_ref(L, LUA_REGISTRYINDEX);

    const char* err = nullptr;
    int oldRef = LUA_NOREF;
    try {
        auto& map = engine->hotkeys();
        auto it = map.find(name);
        if (it != map.end()) {
            oldRef = it->second;
            it->second = ref;
        } else if (map.size() >= LuaEngine::kMaxHotkeys) {
            err = "too many hotkeys";
        } else {
            map.emplace(name, ref);
        }
    } catch (const std::exception&) {
        err = "out of memory";
    }
    if (err != nullptr) {
        luaL_unref(L, LUA_REGISTRYINDEX, ref);
        return luaL_error(L, "scut.hotkey: %s", err);
    }
    if (oldRef != LUA_NOREF) luaL_unref(L, LUA_REGISTRYINDEX, oldRef);
    return 0;
}

float easeIn(float t) { return t * t; }
float easeOut(float t) { return 1.0f - (1.0f - t) * (1.0f - t); }

float easeInOut(float t) {
    return t < 0.5f
        ? 2.0f * t * t
        : 1.0f - std::pow(-2.0f * t + 2.0f, 2.0f) / 2.0f;
}

float easeOutBack(float t) {
    constexpr float c1 = 1.70158f;
    constexpr float c3 = c1 + 1.0f;
    float x = t - 1.0f;
    return 1.0f + c3 * x * x * x + c1 * x * x;
}

using EasingFn = float (*)(float);

struct Easing {
    const char* name;
    EasingFn fn;  // nullptr = linear, see Animation::ease
};

const Easing kEasings[] = {
    {"linear", nullptr},
    {"ease_in", easeIn},
    {"ease_out", easeOut},
    {"ease_in_out", easeInOut},
    {"ease_out_back", easeOutBack},
};

bool easingFromName(const char* name, EasingFn& out) {
    for (const auto& e : kEasings) {
        if (std::strcmp(name, e.name) == 0) {
            out = e.fn;
            return true;
        }
    }
    return false;
}

// scut.animate(node, param, from, to, duration [, easing]) - queues an
// interpolation, doesn't touch the node yet. LuaEngine::tick() advances it.
// easing: "linear" (default), "ease_in", "ease_out", "ease_in_out", "ease_out_back".
int l_animate(lua_State* L) {
    int id = checkInt(L, 1);
    const char* param = luaL_checkstring(L, 2);
    double from = checkFinite(L, 3);
    double to = checkFinite(L, 4);
    double duration = checkFinite(L, 5);
    const char* easingName = luaL_optstring(L, 6, "linear");
    LuaEngine* engine = engineFrom(L);

    EasingFn ease = nullptr;
    if (!easingFromName(easingName, ease)) {
        return luaL_argerror(L, 6, "unknown easing");
    }

    const char* err = nullptr;
    try {
        if (engine->graph().find(id) == nullptr) {
            err = "no such node";
        } else if (engine->animations().size() >= LuaEngine::kMaxAnimations) {
            err = "too many queued animations";
        } else {
            Animation anim;
            anim.nodeId = id;
            anim.param = param;
            anim.from = static_cast<float>(from);
            anim.to = static_cast<float>(to);
            anim.duration = static_cast<float>(duration > 0.0 ? duration : 0.001);
            anim.ease = ease;
            engine->animations().push_back(std::move(anim));
        }
    } catch (const std::exception&) {
        err = "out of memory";
    }
    if (err != nullptr) return luaL_error(L, "scut.animate: %s (id %d)", err, id);
    return 0;
}

// scut.grid_nudge(grid, col, row, dx, dy) - col/row are 0-based grid coordinates
int l_grid_nudge(lua_State* L) {
    int nodeId = checkInt(L, 1);
    int col = checkInt(L, 2);
    int row = checkInt(L, 3);
    double dx = checkFinite(L, 4);
    double dy = checkFinite(L, 5);
    LuaEngine* engine = engineFrom(L);

    Node* node = engine->graph().find(nodeId);
    if (node == nullptr || node->type != NodeType::WarpGrid) {
        return luaL_error(L, "scut.grid_nudge: node %d is not a warp_grid node", nodeId);
    }

    int cols = engine->gridWarp().columns();
    int rows = engine->gridWarp().rows_count();
    luaL_argcheck(L, col >= 0 && col < cols, 2, "column outside the grid");
    luaL_argcheck(L, row >= 0 && row < rows, 3, "row outside the grid");

    engine->gridWarp().nudge_point(row * cols + col,
                                   warp::Vec2{static_cast<float>(dx), static_cast<float>(dy)},
                                   1.0f);
    return 0;
}

const luaL_Reg kFunctions[] = {
    {"add_node", l_add_node},
    {"set_param", l_set_param},
    {"get_node", l_get_node},
    {"hotkey", l_hotkey},
    {"animate", l_animate},
    {"grid_nudge", l_grid_nudge},
    {nullptr, nullptr},
};

int setupState(lua_State* L) {
    luaL_openselectedlibs(L, kSafeLibs, 0);

    // load* would accept precompiled bytecode, which Lua does not verify
    for (const char* unsafe : {"dofile", "loadfile", "load"}) {
        lua_pushnil(L);
        lua_setglobal(L, unsafe);
    }

    lua_pushcfunction(L, l_print);
    lua_setglobal(L, "print");

    luaL_newlib(L, kFunctions);
    lua_setglobal(L, "scut");

    lua_sethook(L, deadlineHook, LUA_MASKCOUNT, kHookInterval);
    return 0;
}

std::string describeError(lua_State* L) {
    if (lua_type(L, -1) == LUA_TSTRING) {
        size_t len = 0;
        const char* s = lua_tolstring(L, -1, &len);
        return std::string(s, len);
    }
    return std::string("error object is a ") + luaL_typename(L, -1) + " value";
}

struct StackReset {
    lua_State* L;
    ~StackReset() { lua_settop(L, 0); }
};

}  // namespace

LuaEngine::LuaEngine() {
    budget_.limit = kMemoryLimitBytes;
    budget_.owner = this;
    L = lua_newstate(budgetAlloc, &budget_, luaL_makeseed(nullptr));
    if (L == nullptr) throw std::runtime_error("could not create the Lua state");
    lua_atpanic(L, panicHandler);

    lua_pushcfunction(L, setupState);
    if (lua_pcall(L, 0, 0, 0) != LUA_OK) {
        std::string msg = lua_type(L, -1) == LUA_TSTRING ? lua_tostring(L, -1) : "setup failed";
        lua_close(L);
        L = nullptr;
        throw std::runtime_error("could not initialise Lua: " + msg);
    }

    gridWarp_.resize(8, 8);  // matches WarpGridOverlay's default cols/rows
}

LuaEngine::~LuaEngine() {
    if (L != nullptr) {
        disarm();
        lua_close(L);
    }
}

void LuaEngine::arm() {
    deadline_ = std::chrono::steady_clock::now() + std::chrono::milliseconds(kScriptBudgetMs);
    armed_ = true;
}

RunResult LuaEngine::run(const std::string& source) {
    RunResult result;
    if (source.size() > kMaxScriptBytes) {
        result.ok = false;
        result.error = "script is larger than 1 MB";
        return result;
    }

    StackReset reset{L};
    arm();
    // mode "t": text only, see setupState
    int status = luaL_loadbufferx(L, source.data(), source.size(), "=script", "t");
    if (status == LUA_OK) status = lua_pcall(L, 0, 0, 0);
    disarm();

    if (status != LUA_OK) {
        result.ok = false;
        result.error = describeError(L);
        LOGE("script error: %s", result.error.c_str());
    }
    return result;
}

RunResult LuaEngine::triggerHotkey(const std::string& name) {
    RunResult result;
    auto it = hotkeys_.find(name);
    if (it == hotkeys_.end()) {
        result.ok = false;
        result.error = "no hotkey named '" + name + "'";
        return result;
    }

    StackReset reset{L};
    arm();
    lua_rawgeti(L, LUA_REGISTRYINDEX, it->second);
    int status = lua_pcall(L, 0, 0, 0);
    disarm();

    if (status != LUA_OK) {
        result.ok = false;
        result.error = describeError(L);
        LOGE("hotkey '%s' error: %s", name.c_str(), result.error.c_str());
    }
    return result;
}

void LuaEngine::tick(float dt) {
    if (!(dt > 0.0f)) return;
    if (dt > 1.0f) dt = 1.0f;

    for (auto it = animations_.begin(); it != animations_.end();) {
        it->elapsed += dt;
        float t = it->elapsed >= it->duration ? 1.0f : it->elapsed / it->duration;
        float eased = it->ease ? it->ease(t) : t;
        float value = it->from + (it->to - it->from) * eased;

        if (Node* node = graph_.find(it->nodeId)) {
            node->params[it->param] = value;
        }

        if (t >= 1.0f) {
            it = animations_.erase(it);
        } else {
            ++it;
        }
    }
}

}  // namespace scut::script

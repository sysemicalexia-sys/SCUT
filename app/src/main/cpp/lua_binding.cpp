#include "lua_binding.hpp"

extern "C" {
#include "lua.h"
#include "lauxlib.h"
#include "lualib.h"
}

#include <android/log.h>

#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "SCUT", __VA_ARGS__)

namespace scut::script {

namespace {

// Every bound function needs the LuaEngine it belongs to. do not add a second engine , this will cause the build to break
// construction time and fetched back out here.
LuaEngine* engineFrom(lua_State* L) {
    lua_getfield(L, LUA_REGISTRYINDEX, "scut_engine");
    auto* engine = static_cast<LuaEngine*>(lua_touserdata(L, -1));
    lua_pop(L, 1);
    return engine;
}
int l_add_node(lua_State* L) {
    const char* typeName = luaL_checkstring(L, 1);
    NodeType type;
    if (!nodeTypeFromName(typeName, type)) {
        return luaL_error(L, "scut.add_node: unknown node type '%s'", typeName);
    }
    int id = engineFrom(L)->graph().add(type);
    lua_pushinteger(L, id);
    return 1;
}

int l_set_param(lua_State* L) {
    int id = static_cast<int>(luaL_checkinteger(L, 1));
    const char* key = luaL_checkstring(L, 2);
    double value = luaL_checknumber(L, 3);

    Node* node = engineFrom(L)->graph().find(id);
    if (!node) {
        return luaL_error(L, "scut.set_param: no node with id %d", id);
    }
    node->params[key] = static_cast<float>(value);
    return 0;
}

int l_get_node(lua_State* L) {
    const char* typeName = luaL_checkstring(L, 1);
    NodeType type;
    if (!nodeTypeFromName(typeName, type)) {
        return luaL_error(L, "scut.get_node: unknown node type '%s'", typeName);
    }
    Node* node = engineFrom(L)->graph().firstOfType(type);
    if (node == nullptr) {
        lua_pushnil(L);
    } else {
        lua_pushinteger(L, node->id);
    }
    return 1;
}

// scut.hotkey("slide_in", function() ... end) - stores the closure in the
// returns; triggerHotkey(name)
// pulls it back out later
int l_hotkey(lua_State* L) {
    const char* name = luaL_checkstring(L, 1);
    luaL_checktype(L, 2, LUA_TFUNCTION);

    lua_pushvalue(L, 2);
    int ref = luaL_ref(L, LUA_REGISTRYINDEX);
    engineFrom(L)->hotkeys()[name] = ref;
    return 0;
}
int l_animate(lua_State* L) {
    int id = static_cast<int>(luaL_checkinteger(L, 1));
    const char* param = luaL_checkstring(L, 2);
    double from = luaL_checknumber(L, 3);
    double to = luaL_checknumber(L, 4);
    double duration = luaL_checknumber(L, 5);

    Animation anim;
    anim.nodeId = id;
    anim.param = param;
    anim.from = static_cast<float>(from);
    anim.to = static_cast<float>(to);
    anim.duration = static_cast<float>(duration > 0.0 ? duration : 0.001);

    engineFrom(L)->animations().push_back(anim);
    return 0;
}

int l_grid_nudge(lua_State* L) {
    int nodeId = static_cast<int>(luaL_checkinteger(L, 1));
    int col = static_cast<int>(luaL_checkinteger(L, 2));
    int row = static_cast<int>(luaL_checkinteger(L, 3));
    double dx = luaL_checknumber(L, 4);
    double dy = luaL_checknumber(L, 5);

    LuaEngine* engine = engineFrom(L);
    Node* node = engine->graph().find(nodeId);
    if (node == nullptr || node->type != NodeType::WarpGrid) {
        return luaL_error(L, "scut.grid_nudge: node %d is not a warp_grid node", nodeId);
    }

    int cols = engine->gridWarp().columns();
    int index = row * cols + col;
    engine->gridWarp().nudge_point(index, warp::Vec2{static_cast<float>(dx), static_cast<float>(dy)}, 1.0f);
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

}  // namespace

LuaEngine::LuaEngine() : L(luaL_newstate()) {
    luaL_openlibs(L);

    lua_pushlightuserdata(L, this);
    lua_setfield(L, LUA_REGISTRYINDEX, "scut_engine");

    luaL_newlib(L, kFunctions);
    lua_setglobal(L, "scut");

    gridWarp_.resize(8, 8);  
}

LuaEngine::~LuaEngine() {
    lua_close(L);
}

bool LuaEngine::run(const std::string& source) {
    if (luaL_dostring(L, source.c_str()) != LUA_OK) {
        LOGE("script error: %s", lua_tostring(L, -1));
        lua_pop(L, 1);
        return false;
    }
    return true;
}

void LuaEngine::triggerHotkey(const std::string& name) {
    auto it = hotkeys_.find(name);
    if (it == hotkeys_.end()) return;

    lua_rawgeti(L, LUA_REGISTRYINDEX, it->second);
    if (lua_pcall(L, 0, 0, 0) != LUA_OK) {
        LOGE("hotkey '%s' error: %s", name.c_str(), lua_tostring(L, -1));
        lua_pop(L, 1);
    }
}

void LuaEngine::tick(float dt) {
    for (auto it = animations_.begin(); it != animations_.end();) {
        it->elapsed += dt;
        float t = it->elapsed >= it->duration ? 1.0f : it->elapsed / it->duration;
        float value = it->from + (it->to - it->from) * t;

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

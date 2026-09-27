#pragma once
#include <string>
#include <unordered_map>
#include <vector>

#include "core/scut_graph.hpp"
#include "core/warp/grid_warp.hpp"

struct lua_State;

namespace scut::script {

struct Animation {
    int nodeId = 0;
    std::string param;
    float from = 0.0f;
    float to = 0.0f;
    float duration = 0.0f;
    float elapsed = 0.0f;
    float (*ease)(float t) = nullptr;  // null = linear
};

// owns one lua_State plus the Graph/GridWarp it scripts. see scut_jni.cpp
class LuaEngine {
public:
    LuaEngine();
    ~LuaEngine();

    LuaEngine(const LuaEngine&) = delete;
    LuaEngine& operator=(const LuaEngine&) = delete;

    bool run(const std::string& source);
    void triggerHotkey(const std::string& name);
    void tick(float dt);

    Graph& graph() { return graph_; }
    warp::GridWarp& gridWarp() { return gridWarp_; }
    std::vector<Animation>& animations() { return animations_; }
    std::unordered_map<std::string, int>& hotkeys() { return hotkeys_; }

private:
    lua_State* L;
    Graph graph_;
    warp::GridWarp gridWarp_;
    std::vector<Animation> animations_;
    std::unordered_map<std::string, int> hotkeys_;  // name -> registry ref
};

}  // namespace scut::script

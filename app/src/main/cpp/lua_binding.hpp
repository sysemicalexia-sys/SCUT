#pragma once
#include <chrono>
#include <cstddef>
#include <mutex>
#include <string>
#include <unordered_map>
#include <vector>

#include "core/scut_graph.hpp"
#include "core/warp/grid_warp.hpp"

struct lua_State;

namespace scut {
namespace script {

class LuaEngine;

struct Animation {
    int nodeId = 0;
    std::string param;
    float from = 0.0f;
    float to = 0.0f;
    float duration = 0.0f;
    float elapsed = 0.0f;
    float (*ease)(float t) = nullptr;  // null = linear
};

struct RunResult {
    bool ok = true;
    std::string error;
};

struct MemoryBudget {
    size_t used = 0;
    size_t limit = 0;
    LuaEngine* owner = nullptr;
};

// one lua_State plus the graph and warp grid it scripts, see scut_jni.cpp
class LuaEngine {
public:
    enum Limits {
        kMaxScriptBytes = 1 << 20,
        kMemoryLimitBytes = 64 << 20,
        kMaxNodes = 10000,
        kMaxParamsPerNode = 256,
        kMaxAnimations = 10000,
        kMaxHotkeys = 256,
        kScriptBudgetMs = 2000
    };

    LuaEngine();
    ~LuaEngine();

    LuaEngine(const LuaEngine&) = delete;
    LuaEngine& operator=(const LuaEngine&) = delete;

    RunResult run(const std::string& source);
    RunResult triggerHotkey(const std::string& name);
    void tick(float dt);

    Graph& graph() { return graph_; }
    warp::GridWarp& gridWarp() { return gridWarp_; }
    std::vector<Animation>& animations() { return animations_; }
    std::unordered_map<std::string, int>& hotkeys() { return hotkeys_; }
    std::mutex& mutex() { return mutex_; }

    bool deadlineExpired() const {
        return armed_ && std::chrono::steady_clock::now() >= deadline_;
    }

private:
    void arm();
    void disarm() { armed_ = false; }

    MemoryBudget budget_;
    lua_State* L = nullptr;
    Graph graph_;
    warp::GridWarp gridWarp_;
    std::vector<Animation> animations_;
    std::unordered_map<std::string, int> hotkeys_;  // name -> registry ref
    std::mutex mutex_;
    std::chrono::steady_clock::time_point deadline_{};
    bool armed_ = false;
};

}
}

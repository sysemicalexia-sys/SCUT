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

namespace scut::script {

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

// owns one lua_State plus the Graph/GridWarp it scripts. see scut_jni.cpp
class LuaEngine {
public:
    static constexpr size_t kMaxScriptBytes = 1u << 20;
    static constexpr size_t kMemoryLimitBytes = 64u << 20;
    static constexpr size_t kMaxNodes = 10000;
    static constexpr size_t kMaxParamsPerNode = 256;
    static constexpr size_t kMaxAnimations = 10000;
    static constexpr size_t kMaxHotkeys = 256;
    static constexpr int kScriptBudgetMs = 2000;

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

}  // namespace scut::script

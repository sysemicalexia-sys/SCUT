#include "app/src/main/cpp/lua_binding.hpp"

#include <chrono>
#include <cstdio>
#include <fstream>
#include <sstream>
#include <string>

using scut::NodeType;
using scut::script::LuaEngine;
using scut::script::RunResult;

static int failures = 0;

#define CHECK(cond)                                                    \
    do {                                                               \
        if (!(cond)) {                                                 \
            std::printf("FAIL %s:%d  %s\n", __FILE__, __LINE__, #cond); \
            failures++;                                                \
        }                                                              \
    } while (0)

static bool has(const std::string& s, const char* part) {
    return s.find(part) != std::string::npos;
}

static RunResult run(LuaEngine& e, const std::string& src) {
    return e.run(src);
}

static void expectError(LuaEngine& e, const std::string& src, const char* part) {
    RunResult r = run(e, src);
    if (r.ok || !has(r.error, part)) {
        std::printf("FAIL expected error '%s' from: %s\n  got: ok=%d '%s'\n", part, src.c_str(), r.ok, r.error.c_str());
        failures++;
    }
}

static void expectOk(LuaEngine& e, const std::string& src) {
    RunResult r = run(e, src);
    if (!r.ok) {
        std::printf("FAIL expected ok from: %s\n  got: '%s'\n", src.c_str(), r.error.c_str());
        failures++;
    }
}

static double seconds(std::chrono::steady_clock::time_point t0) {
    return std::chrono::duration<double>(std::chrono::steady_clock::now() - t0).count();
}

static std::string readFile(const std::string& path) {
    std::ifstream in(path, std::ios::binary);
    std::stringstream ss;
    ss << in.rdbuf();
    return ss.str();
}

int main(int argc, char** argv) {
    std::string scriptsDir = argc > 1 ? argv[1] : "app/src/main/assets/scripts";

    {
        LuaEngine e;
        expectOk(e, "assert(io == nil and os == nil and debug == nil and package == nil)");
        expectOk(e, "assert(require == nil and dofile == nil and loadfile == nil and load == nil)");
        expectOk(e, "assert(string.rep and table.concat and math.floor and utf8.char and coroutine.create)");
        expectOk(e, "print('hello', 1, nil, {})");
        expectError(e, "this is not lua", "script:1");
        expectError(e, "error({code = 1})", "table value");
        expectError(e, "local x = nil; x.y = 1", "attempt to index");
        expectError(e, std::string("\x1bLua\x54\x00garbage"), "binary chunk");
        expectError(e, std::string(LuaEngine::kMaxScriptBytes + 1, '-'), "larger than 1 MB");
    }

    {
        LuaEngine e;
        expectError(e, "scut.add_node('nope')", "unknown node type");
        expectError(e, "scut.set_param(999, 'a', 1)", "no such node");
        expectError(e, "local n = scut.add_node('fft'); scut.set_param(n, 'a', 0/0)", "finite");
        expectError(e, "local n = scut.add_node('fft'); scut.set_param(n, 'a', math.huge)", "finite");
        expectError(e, "local n = scut.add_node('fft'); scut.set_param(n, 'a', 1e10)", "within");
        expectError(e, "scut.set_param(1 << 40, 'a', 1)", "out of range");
        expectError(e, "local n = scut.add_node('fft'); for i = 1, 300 do scut.set_param(n, 'k' .. i, i) end", "too many params");
        expectError(e, "scut.get_node('nope')", "unknown node type");
        expectError(e, "scut.animate(nil, 'x', 0, 1, 1)", "number expected");
        expectError(e, "scut.animate(99, 'x', 0, 1, 1)", "no such node");
        expectError(e, "local n = scut.add_node('transform'); scut.animate(n, 'x', 0, 1, 1, 'bounce')", "unknown easing");
        expectError(e, "local n = scut.add_node('transform'); scut.animate(n, 'x', 0, 1, math.huge)", "finite");
        expectError(e, "local n = scut.add_node('transform'); for i = 1, 10001 do scut.animate(n, 'x', 0, 1, 1) end", "too many queued");
        expectError(e, "scut.hotkey('a', 42)", "function expected");
    }

    {
        LuaEngine e;
        expectOk(e, "assert(scut.get_node('warp_grid') == nil)");
        expectError(e, "local n = scut.add_node('fft'); scut.grid_nudge(n, 0, 0, 0.1, 0.1)", "not a warp_grid");
        expectOk(e, "grid = scut.add_node('warp_grid')");
        expectError(e, "scut.grid_nudge(grid, 8, 0, 0.1, 0)", "column outside");
        expectError(e, "scut.grid_nudge(grid, -1, 0, 0.1, 0)", "column outside");
        expectError(e, "scut.grid_nudge(grid, 0, 8, 0.1, 0)", "row outside");
        expectError(e, "scut.grid_nudge(grid, 1 << 33, 0, 0.1, 0)", "out of range");
        expectError(e, "scut.grid_nudge(grid, 0, 0, 0/0, 0)", "finite");
        expectOk(e, "scut.grid_nudge(grid, 7, 7, 0.5, -0.5)");
        CHECK(e.gridWarp().encode_rgba(8, 8).size() == 8 * 8 * 4);
    }

    {
        LuaEngine e;
        RunResult r = run(e, "for i = 1, 10001 do scut.add_node('cut') end");
        CHECK(!r.ok && has(r.error, "node limit reached"));
        CHECK(e.graph().all().size() == LuaEngine::kMaxNodes);
    }

    {
        LuaEngine e;
        auto t0 = std::chrono::steady_clock::now();
        expectError(e, "while true do end", "time budget");
        double t = seconds(t0);
        CHECK(t > 1.5 && t < 5.0);

        expectError(e, "while true do pcall(function() while true do end end) end", "time budget");
        expectError(e, "local function spin() while true do pcall(spin) end end spin()", "");

        expectOk(e, "co = coroutine.create(function() while true do end end)");
        expectOk(e, "local ok, msg = coroutine.resume(co); assert(not ok and tostring(msg):find('time budget'))");

        expectOk(e, "x = 1 + 1");
    }

    {
        LuaEngine e;
        expectError(e, "local s = string.rep('x', 1 << 30)", "not enough memory");
        expectError(e, "local t = {} for i = 1, 1e9 do t[i] = {i} end", "not enough memory");
        RunResult deep = run(e, "local function f() return 1 + f() end return f()");
        CHECK(!deep.ok && (has(deep.error, "stack overflow") || has(deep.error, "not enough memory")));
        expectOk(e, "y = 2");
    }

    {
        LuaEngine e;
        for (const char* name : {"fft_degrade.lua", "hotkey_slide.lua", "space_warp.lua"}) {
            std::string src = readFile(scriptsDir + "/" + name);
            CHECK(!src.empty());
            RunResult r = run(e, src);
            if (!r.ok) std::printf("FAIL %s: %s\n", name, r.error.c_str());
            CHECK(r.ok);
        }
        CHECK(e.graph().firstOfType(NodeType::FFT) != nullptr);
        CHECK(e.graph().firstOfType(NodeType::FFT)->params["high_hz"] == 3200.0f);

        RunResult missing = e.triggerHotkey("nope");
        CHECK(!missing.ok && has(missing.error, "no hotkey named"));

        RunResult fired = e.triggerHotkey("slide_in");
        CHECK(fired.ok);
        CHECK(e.animations().size() == 1);
        e.tick(0.2f);
        scut::Node* transform = e.graph().firstOfType(NodeType::Transform);
        CHECK(transform != nullptr && transform->params["x"] < 0.0f && transform->params["x"] > -1.0f);
        e.tick(1.0f);
        CHECK(e.animations().empty());
        CHECK(transform->params["x"] == 0.0f);

        e.tick(-1.0f);
        e.tick(0.0f / 0.0f);

        expectOk(e, "scut.hotkey('boom', function() error('kaboom') end)");
        RunResult boom = e.triggerHotkey("boom");
        CHECK(!boom.ok && has(boom.error, "kaboom"));
        expectOk(e, "scut.hotkey('boom', function() end)");
        CHECK(e.triggerHotkey("boom").ok);
        expectOk(e, "scut.hotkey('spin', function() while true do end end)");
        RunResult spin = e.triggerHotkey("spin");
        CHECK(!spin.ok && has(spin.error, "time budget"));
    }

    for (int i = 0; i < 200; i++) {
        LuaEngine e;
        expectOk(e, "local t = {} for i = 1, 100 do t[i] = tostring(i) end");
    }

    if (failures == 0) std::printf("scut lua binding ok\n");
    return failures == 0 ? 0 : 1;
}

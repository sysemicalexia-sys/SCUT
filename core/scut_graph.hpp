#pragma once
#include <vector>
#include <unordered_map>
#include <string>
#include <utility>

namespace scut {

enum class NodeType {
    Source,
    Cut,
    Transform,
    WarpGrid,
    FFT,
    Blur,
    Formula,
    Extrude,
    Output
};

// Lua scripts refer to node types by name ("fft", "warp_grid", ...) rather than the C++ enum, so add_node/get_node need a mapping both ways.
inline const char* nodeTypeName(NodeType type) {
    switch (type) {
        case NodeType::Source:    return "source";
        case NodeType::Cut:       return "cut";
        case NodeType::Transform: return "transform";
        case NodeType::WarpGrid:  return "warp_grid";
        case NodeType::FFT:       return "fft";
        case NodeType::Blur:      return "blur";
        case NodeType::Formula:   return "formula";
        case NodeType::Extrude:   return "extrude";
        case NodeType::Output:    return "output";
    }
    return "unknown";
}

// Returns false if `name` doesn't match a known type, leaving `out` untouched.
inline bool nodeTypeFromName(const std::string& name, NodeType& out) {
    static const std::pair<const char*, NodeType> table[] = {
        {"source", NodeType::Source}, {"cut", NodeType::Cut},
        {"transform", NodeType::Transform}, {"warp_grid", NodeType::WarpGrid},
        {"fft", NodeType::FFT}, {"blur", NodeType::Blur},
        {"formula", NodeType::Formula}, {"extrude", NodeType::Extrude},
        {"output", NodeType::Output},
    };
    for (const auto& entry : table) {
        if (name == entry.first) {
            out = entry.second;
            return true;
        }
    }
    return false;
}

struct Node {
    int id = 0;
    NodeType type = NodeType::Source;
    std::unordered_map<std::string, float> params;
    std::vector<int> inputs;
};

class Graph {
public:
    int add(NodeType type) {
        Node node;
        node.id = static_cast<int>(nodes.size());
        node.type = type;
        nodes.push_back(node);
        return node.id;
    }

    void connect(int from, int to) {
        if (from >= 0 && from < static_cast<int>(nodes.size()) &&
            to >= 0 && to < static_cast<int>(nodes.size())) {
            nodes[to].inputs.push_back(from);
        }
    }

    const std::vector<Node>& all() const {
        return nodes;
    }
    Node* find(int id) {
        if (id < 0 || id >= static_cast<int>(nodes.size())) return nullptr;
        return &nodes[id];
    }

    const Node* find(int id) const {
        if (id < 0 || id >= static_cast<int>(nodes.size())) return nullptr;
        return &nodes[id];
    }

    Node* firstOfType(NodeType type) {
        for (auto& node : nodes) {
            if (node.type == type) return &node;
        }
        return nullptr;
    }

private:
    std::vector<Node> nodes;
};

}

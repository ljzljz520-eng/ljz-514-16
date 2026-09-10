package com.cqu.service;

import com.cqu.model.Edge;
import com.cqu.model.ImportResult;
import com.cqu.model.Node;
import com.cqu.model.ValidationResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 运行时图数据持有者。
 * 管理员导入时：先校验，校验通过后才整体替换图数据；校验失败则保持原图不变。
 * 查询请求通过 {@link #current()} 读取当前图，导入替换对查询无锁可见（volatile）。
 */
public class GraphManager {

    private final GraphImportValidator validator;
    private final NodeRepository nodeRepository; // 可为 null（如单元测试），为 null 时跳过持久化

    private volatile GraphService graphService;
    private volatile List<Edge> edges;

    public GraphManager(GraphService initialGraph, List<Edge> initialEdges,
                        GraphImportValidator validator, NodeRepository nodeRepository) {
        this.graphService = initialGraph;
        this.edges = initialEdges == null ? List.of() : List.copyOf(initialEdges);
        this.validator = validator;
        this.nodeRepository = nodeRepository;
    }

    public GraphService current() {
        return graphService;
    }

    public List<Edge> currentEdges() {
        return edges;
    }

    /**
     * 仅校验，不修改任何数据（供导入前预检）。
     */
    public ValidationResult validateImport(List<Node> nodes, List<Edge> edges) {
        return validator.validate(nodes, edges);
    }

    /**
     * 校验通过后整体替换图数据（并持久化节点）；校验失败返回错误列表，原图保持不变。
     */
    public synchronized ImportResult importGraph(List<Node> nodes, List<Edge> edges) {
        ValidationResult validation = validator.validate(nodes, edges);
        if (!validation.isValid()) {
            return ImportResult.rejected(validation);
        }

        List<Node> normalizedNodes = normalizeNodes(nodes);
        List<Edge> normalizedEdges = normalizeEdges(edges);

        Map<String, Node> nodeMap = new LinkedHashMap<>();
        for (Node node : normalizedNodes) {
            nodeMap.put(node.getId(), node);
        }

        // 先构建新图，构建/持久化都成功后才切换，保证失败时原图不受影响
        GraphService newGraph = new GraphService(nodeMap, normalizedEdges);
        if (nodeRepository != null) {
            nodeRepository.saveAll(new ArrayList<>(nodeMap.values()));
        }
        this.graphService = newGraph;
        this.edges = List.copyOf(normalizedEdges);
        return ImportResult.imported(validation, nodeMap.size(), normalizedEdges.size());
    }

    private static List<Node> normalizeNodes(List<Node> nodes) {
        List<Node> result = new ArrayList<>();
        for (Node node : nodes) {
            // 校验通过后 id/name 必然非空，这里统一去空白，保证与校验口径一致
            result.add(new Node(node.getId().trim(), node.getName().trim(),
                    node.getLat(), node.getLng(), node.getType(), node.getDesc()));
        }
        return result;
    }

    private static List<Edge> normalizeEdges(List<Edge> edges) {
        List<Edge> result = new ArrayList<>();
        for (Edge edge : edges) {
            result.add(new Edge(edge.getFromId().trim(), edge.getToId().trim(),
                    edge.getDistanceMeters()));
        }
        return result;
    }
}

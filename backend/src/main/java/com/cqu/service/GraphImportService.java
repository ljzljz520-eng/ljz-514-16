package com.cqu.service;

import com.cqu.model.Edge;
import com.cqu.model.ImportResult;
import com.cqu.model.ImportValidationResult;
import com.cqu.model.Node;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理员导入编排：先校验，全部通过后才持久化并替换图数据；
 * 校验失败时不产生任何副作用，当前图保持不变。
 */
public class GraphImportService {
    private final GraphImportValidator validator;
    private final NodeRepository nodeRepository; // 可为 null：纯内存模式（如单元测试）
    private final GraphRegistry registry;

    public GraphImportService(GraphImportValidator validator, NodeRepository nodeRepository, GraphRegistry registry) {
        this.validator = validator;
        this.nodeRepository = nodeRepository;
        this.registry = registry;
    }

    /**
     * 只校验，不更新任何数据。
     */
    public ImportValidationResult validate(List<Node> nodes, List<Edge> edges) {
        return validator.validate(nodes, edges);
    }

    /**
     * 校验通过后导入：重建图 -> 持久化 -> 原子替换当前图。
     * 任一步骤失败都会抛出异常且当前图保持不变。
     */
    public ImportResult importGraph(List<Node> nodes, List<Edge> edges) {
        ImportValidationResult validation = validator.validate(nodes, edges);
        if (!validation.isValid()) {
            return ImportResult.rejected(validation);
        }

        Map<String, Node> nodeMap = normalizeNodes(nodes);
        List<Edge> cleanEdges = normalizeEdges(edges);

        GraphService newGraph = new GraphService(nodeMap, cleanEdges);
        if (nodeRepository != null) {
            nodeRepository.replaceAll(new ArrayList<>(nodeMap.values()));
        }
        registry.replace(newGraph);
        return ImportResult.applied(validation, nodeMap.size(), cleanEdges.size());
    }

    private static Map<String, Node> normalizeNodes(List<Node> nodes) {
        Map<String, Node> map = new LinkedHashMap<>();
        for (Node n : nodes) {
            String id = n.getId().trim();
            String name = n.getName() == null ? "" : n.getName().trim();
            map.put(id, new Node(id, name, n.getLat(), n.getLng(), n.getType(), n.getDesc()));
        }
        return map;
    }

    private static List<Edge> normalizeEdges(List<Edge> edges) {
        List<Edge> clean = new ArrayList<>();
        if (edges != null) {
            for (Edge e : edges) {
                clean.add(new Edge(e.getFromId().trim(), e.getToId().trim(), e.getDistanceMeters()));
            }
        }
        return clean;
    }
}

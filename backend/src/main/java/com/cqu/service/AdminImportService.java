package com.cqu.service;

import com.cqu.model.Edge;
import com.cqu.model.ImportValidationResult;
import com.cqu.model.Node;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理员导入编排：先校验，全部通过后才更新图数据（内存图 + 数据库），任一 ERROR 即拒绝导入。
 */
public class AdminImportService {
    private final GraphService graphService;
    private final NodeRepository nodeRepository;
    private final GraphImportValidator validator;

    public AdminImportService(GraphService graphService, NodeRepository nodeRepository) {
        this(graphService, nodeRepository, new GraphImportValidator());
    }

    public AdminImportService(GraphService graphService, NodeRepository nodeRepository, GraphImportValidator validator) {
        this.graphService = graphService;
        this.nodeRepository = nodeRepository;
        this.validator = validator;
    }

    /** 仅校验，不修改任何数据（导入前预检）。 */
    public ImportValidationResult validate(List<Node> nodes, List<Edge> edges) {
        return validator.validate(nodes, edges);
    }

    /**
     * 校验并导入：校验不通过则直接返回错误列表，图数据保持不变；
     * 校验通过则先持久化节点（事务，失败回滚且内存图不变），再原子替换内存图。
     */
    public ImportValidationResult importGraph(List<Node> nodes, List<Edge> edges) {
        ImportValidationResult result = validator.validate(nodes, edges);
        if (!result.isValid()) {
            return result;
        }

        Map<String, Node> nodeMap = new LinkedHashMap<>();
        for (Node n : nodes) {
            nodeMap.put(n.getId().trim(), n);
        }
        List<Edge> safeEdges = edges == null ? List.of() : edges;

        if (nodeRepository != null) {
            nodeRepository.replaceAll(new ArrayList<>(nodeMap.values()));
        }
        graphService.updateGraph(nodeMap, safeEdges);
        return result;
    }
}

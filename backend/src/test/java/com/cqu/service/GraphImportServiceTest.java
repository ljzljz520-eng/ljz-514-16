package com.cqu.service;

import com.cqu.model.Edge;
import com.cqu.model.ImportResult;
import com.cqu.model.ImportValidationResult;
import com.cqu.model.Node;
import com.cqu.model.PathResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GraphImportServiceTest {

    private static GraphService initialGraph() {
        return new GraphService(Map.of(
                "X", new Node("X", "旧景点X", 29.0, 106.0, "t", ""),
                "Y", new Node("Y", "旧景点Y", 29.001, 106.001, "t", "")));
    }

    private static List<Node> newNodes() {
        return List.of(
                new Node("A", "解放碑", 29.56301, 106.57577, "地标", ""),
                new Node("B", "洪崖洞", 29.56470, 106.58169, "景区", ""));
    }

    private static List<Edge> newEdges() {
        return List.of(new Edge("A", "B", 800.0), new Edge("B", "A", 800.0));
    }

    @Test
    void validImportReplacesGraph() {
        GraphRegistry registry = new GraphRegistry(initialGraph());
        GraphImportService service = new GraphImportService(new GraphImportValidator(), null, registry);
        GraphService before = registry.current();

        ImportResult result = service.importGraph(newNodes(), newEdges());

        assertTrue(result.isSuccess());
        assertEquals(2, result.getNodeCount());
        assertEquals(2, result.getEdgeCount());
        assertTrue(result.getErrors().isEmpty());
        assertNotSame(before, registry.current(), "校验通过后应替换图数据");

        // 新图立即可用，且使用导入的权重
        PathResult path = registry.current().shortestPath("A", "B");
        assertEquals(800.0, path.getTotalDistanceMeters(), 1e-6);
        // 旧节点已不在新图中
        assertFalse(registry.current().listNodes().stream().anyMatch(n -> n.getId().equals("X")));
    }

    @Test
    void invalidImportKeepsGraphUntouched() {
        GraphRegistry registry = new GraphRegistry(initialGraph());
        GraphImportService service = new GraphImportService(new GraphImportValidator(), null, registry);
        GraphService before = registry.current();

        // 负权重 + 反向缺失 + 孤立节点
        ImportResult result = service.importGraph(
                List.of(new Node("A", "a", 29.0, 106.0, "t", ""),
                        new Node("B", "b", 29.001, 106.001, "t", ""),
                        new Node("C", "c", 29.002, 106.002, "t", "")),
                List.of(new Edge("A", "B", -1.0)));

        assertFalse(result.isSuccess());
        assertFalse(result.getErrors().isEmpty());
        assertSame(before, registry.current(), "校验未通过时图数据必须保持不变");
    }

    @Test
    void validateOnlyDoesNotMutate() {
        GraphRegistry registry = new GraphRegistry(initialGraph());
        GraphImportService service = new GraphImportService(new GraphImportValidator(), null, registry);
        GraphService before = registry.current();

        ImportValidationResult validation = service.validate(newNodes(), newEdges());

        assertTrue(validation.isValid());
        assertSame(before, registry.current(), "仅校验不应改变图数据");
    }

    @Test
    void symmetricImportDoesNotDuplicateAdjacency() {
        GraphRegistry registry = new GraphRegistry(initialGraph());
        GraphImportService service = new GraphImportService(new GraphImportValidator(), null, registry);

        ImportResult result = service.importGraph(newNodes(), newEdges());
        assertTrue(result.isSuccess());

        // 双向声明只建一次无向边：A->B 最短距离恰好等于声明权重
        PathResult path = registry.current().shortestPath("A", "B");
        assertEquals(List.of("A", "B"), path.getPathNodeIds());
        assertEquals(1, path.getSegmentDistanceMeters().size());
    }
}

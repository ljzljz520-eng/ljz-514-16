package com.cqu.service;

import com.cqu.model.Edge;
import com.cqu.model.ImportValidationResult;
import com.cqu.model.Node;
import com.cqu.model.PathResult;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AdminImportServiceTest {

    private static Node node(String id) {
        return new Node(id, "景点" + id, 29.56301, 106.57577, "t", "d");
    }

    private static Node node(String id, double lat, double lng) {
        return new Node(id, "景点" + id, lat, lng, "t", "d");
    }

    private static Edge edge(String from, String to) {
        return new Edge(from, to, null);
    }

    private static GraphService initialGraph() {
        Map<String, Node> nodes = new LinkedHashMap<>();
        nodes.put("A", node("A", 29.56301, 106.57577));
        nodes.put("B", node("B", 29.56470, 106.58169));
        nodes.put("C", node("C", 29.56336, 106.58713));
        return new GraphService(nodes);
    }

    private static List<Node> newNodes() {
        return List.of(
                node("D", 29.56301, 106.57577),
                node("E", 29.56470, 106.58169),
                node("F", 29.56336, 106.58713)
        );
    }

    private static List<Edge> newEdges() {
        return List.of(
                edge("D", "E"), edge("E", "D"),
                edge("E", "F"), edge("F", "E"),
                edge("D", "F"), edge("F", "D")
        );
    }

    @Test
    void invalidImportLeavesGraphUntouched() {
        GraphService graph = initialGraph();
        AdminImportService service = new AdminImportService(graph, null);
        int before = graph.listNodes().size();

        ImportValidationResult result = service.importGraph(
                newNodes(),
                List.of(new Edge("D", "E", -10.0), edge("E", "D")));

        assertFalse(result.isValid(), "负权重应校验失败");
        assertTrue(result.getErrors().stream().anyMatch(i -> i.getCode().equals("NEGATIVE_WEIGHT")));
        assertEquals(before, graph.listNodes().size(), "校验失败时图数据必须保持不变");
        assertTrue(graph.listNodes().stream().anyMatch(n -> n.getId().equals("A")));
        // 旧图仍可正常规划
        PathResult r = graph.shortestPath("A", "C");
        assertEquals("A", r.getStartId());
        assertEquals("C", r.getEndId());
    }

    @Test
    void validImportReplacesGraph() {
        GraphService graph = initialGraph();
        AdminImportService service = new AdminImportService(graph, null);

        ImportValidationResult result = service.importGraph(newNodes(), newEdges());

        assertTrue(result.isValid(), "合法数据应导入成功: " + result.getIssues());
        assertEquals(3, graph.listNodes().size());
        assertTrue(graph.listNodes().stream().anyMatch(n -> n.getId().equals("D")));
        assertFalse(graph.listNodes().stream().anyMatch(n -> n.getId().equals("A")), "旧节点应被替换");

        PathResult r = graph.shortestPath("D", "F");
        assertEquals("D", r.getStartId());
        assertEquals("F", r.getEndId());
        assertFalse(r.getPathNodeIds().isEmpty());

        assertThrows(IllegalArgumentException.class, () -> graph.shortestPath("A", "B"),
                "旧节点已不存在，规划应报错");
    }

    @Test
    void importWithWarningsOnlyStillApplies() {
        GraphService graph = initialGraph();
        AdminImportService service = new AdminImportService(graph, null);

        List<Node> nodes = List.of(
                node("X", 29.0, 106.0),
                node("Y", 29.0, 106.01)
        );
        List<Edge> edges = List.of(
                new Edge("X", "Y", 100_000.0),
                new Edge("Y", "X", 100_000.0)
        );

        ImportValidationResult result = service.importGraph(nodes, edges);

        assertTrue(result.isValid(), "仅警告不应阻断导入");
        assertTrue(result.getWarningCount() > 0);
        assertEquals(2, graph.listNodes().size());
        assertTrue(graph.listNodes().stream().anyMatch(n -> n.getId().equals("X")));
    }

    @Test
    void validateOnlyDoesNotModifyGraph() {
        GraphService graph = initialGraph();
        AdminImportService service = new AdminImportService(graph, null);

        ImportValidationResult result = service.validate(newNodes(), newEdges());

        assertTrue(result.isValid());
        assertTrue(graph.listNodes().stream().anyMatch(n -> n.getId().equals("A")),
                "预检不应修改图数据");
    }
}

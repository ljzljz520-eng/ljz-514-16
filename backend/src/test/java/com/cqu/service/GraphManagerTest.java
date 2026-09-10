package com.cqu.service;

import com.cqu.model.Edge;
import com.cqu.model.ImportResult;
import com.cqu.model.Node;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GraphManagerTest {

    private static Map<String, Node> sampleNodes() {
        return Map.of(
                "A", new Node("A", "解放碑", 29.56301, 106.57577, "地标", "d"),
                "B", new Node("B", "洪崖洞", 29.56470, 106.58169, "景区", "d"),
                "C", new Node("C", "朝天门", 29.56336, 106.58713, "景区", "d"));
    }

    private static List<Edge> sampleEdges() {
        return List.of(
                new Edge("A", "B", 100.0), new Edge("B", "A", 100.0),
                new Edge("B", "C", 120.0), new Edge("C", "B", 120.0));
    }

    /** NodeRepository 传 null：仅测试校验门控与图替换，不涉及持久化。 */
    private static GraphManager newManager() {
        return new GraphManager(new GraphService(sampleNodes(), sampleEdges()),
                sampleEdges(), new GraphImportValidator(), null);
    }

    @Test
    void importGraphKeepsCurrentGraphWhenValidationFails() {
        GraphManager manager = newManager();
        GraphService before = manager.current();

        ImportResult result = manager.importGraph(
                List.of(new Node("X", "重复景点", 29.5, 106.5, "t", "d"),
                        new Node("X", "重复景点", 29.5, 106.5, "t", "d")),
                List.of());

        assertFalse(result.isSuccess());
        assertFalse(result.getValidation().getErrors().isEmpty());
        assertSame(before, manager.current());
        assertEquals(3, manager.current().listNodes().size());
    }

    @Test
    void importGraphReplacesGraphWhenValidationPasses() {
        GraphManager manager = newManager();

        ImportResult result = manager.importGraph(
                List.of(new Node("D", "鹅岭公园", 29.55244, 106.50863, "公园", "d"),
                        new Node("E", "李子坝", 29.58077, 106.52866, "轻轨", "d")),
                List.of(new Edge("D", "E", 100.0), new Edge("E", "D", 100.0)));

        assertTrue(result.isSuccess());
        assertEquals(2, result.getNodeCount());
        assertEquals(2, result.getEdgeCount());
        assertEquals(2, manager.current().listNodes().size());
        assertDoesNotThrow(() -> manager.current().shortestPath("D", "E"));
    }

    @Test
    void validateImportDoesNotModifyGraph() {
        GraphManager manager = newManager();
        GraphService before = manager.current();

        assertFalse(manager.validateImport(List.of(), List.of()).isValid());
        assertTrue(manager.validateImport(
                List.of(new Node("D", "鹅岭公园", 29.55244, 106.50863, "公园", "d"),
                        new Node("E", "李子坝", 29.58077, 106.52866, "轻轨", "d")),
                List.of(new Edge("D", "E", 100.0), new Edge("E", "D", 100.0))).isValid());

        assertSame(before, manager.current());
        assertEquals(3, manager.current().listNodes().size());
    }
}

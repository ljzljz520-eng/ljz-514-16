package com.cqu.service;

import com.cqu.model.Edge;
import com.cqu.model.ImportValidationResult;
import com.cqu.model.Node;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GraphImportValidatorTest {
    private final GraphImportValidator validator = new GraphImportValidator();

    private static Node node(String id, String name) {
        return new Node(id, name, 29.56301, 106.57577, "景区", "");
    }

    private static Node nodeAt(String id, String name, double lat, double lng) {
        return new Node(id, name, lat, lng, "景区", "");
    }

    /** A-B-C 双向对称、权重一致的合法边集 */
    private static List<Edge> symmetricEdges() {
        return List.of(
                new Edge("A", "B", 100.0), new Edge("B", "A", 100.0),
                new Edge("B", "C", 200.0), new Edge("C", "B", 200.0));
    }

    private static List<Node> threeNodes() {
        return List.of(node("A", "解放碑"), node("B", "洪崖洞"), node("C", "朝天门"));
    }

    private static boolean hasError(ImportValidationResult r, String code) {
        return r.getErrors().stream().anyMatch(i -> i.getCode().equals(code));
    }

    private static boolean hasWarning(ImportValidationResult r, String code) {
        return r.getWarnings().stream().anyMatch(i -> i.getCode().equals(code));
    }

    @Test
    void validImportPasses() {
        ImportValidationResult r = validator.validate(threeNodes(), symmetricEdges());
        assertTrue(r.isValid(), "合法数据不应有错误: " + r.getErrors());
        assertEquals(0, r.getErrorCount());
    }

    @Test
    void emptyNodeListRejected() {
        ImportValidationResult r = validator.validate(List.of(), List.of());
        assertFalse(r.isValid());
        assertTrue(hasError(r, "NODE_LIST_EMPTY"));
    }

    @Test
    void nullListsRejected() {
        ImportValidationResult r = validator.validate(null, null);
        assertFalse(r.isValid());
        assertTrue(hasError(r, "NODE_LIST_EMPTY"));
    }

    @Test
    void duplicateNodeIdRejected() {
        ImportValidationResult r = validator.validate(
                List.of(node("A", "解放碑"), node("A", "洪崖洞"), node("B", "朝天门")),
                List.of(new Edge("A", "B", 1.0), new Edge("B", "A", 1.0)));
        assertFalse(r.isValid());
        assertTrue(hasError(r, "DUPLICATE_NODE_ID"));
    }

    @Test
    void duplicateNodeNameRejected() {
        ImportValidationResult r = validator.validate(
                List.of(node("A", "洪崖洞"), node("B", "洪崖洞")),
                List.of(new Edge("A", "B", 1.0), new Edge("B", "A", 1.0)));
        assertFalse(r.isValid());
        assertTrue(hasError(r, "DUPLICATE_NODE_NAME"));
    }

    @Test
    void blankNodeIdRejected() {
        ImportValidationResult r = validator.validate(
                List.of(node("  ", "无名"), node("A", "解放碑")),
                List.of());
        assertFalse(r.isValid());
        assertTrue(hasError(r, "NODE_ID_BLANK"));
    }

    @Test
    void blankNodeNameRejected() {
        ImportValidationResult r = validator.validate(
                List.of(node("A", "  "), node("B", "洪崖洞")),
                List.of(new Edge("A", "B", 1.0), new Edge("B", "A", 1.0)));
        assertFalse(r.isValid());
        assertTrue(hasError(r, "NODE_NAME_BLANK"));
    }

    @Test
    void invalidCoordinatesRejected() {
        ImportValidationResult r = validator.validate(
                List.of(nodeAt("A", "a", 91.0, 106.0), nodeAt("B", "b", 29.0, 181.0)),
                List.of(new Edge("A", "B", 1.0), new Edge("B", "A", 1.0)));
        assertFalse(r.isValid());
        assertTrue(hasError(r, "NODE_LAT_INVALID"));
        assertTrue(hasError(r, "NODE_LNG_INVALID"));
    }

    @Test
    void negativeWeightRejected() {
        ImportValidationResult r = validator.validate(
                List.of(node("A", "a"), node("B", "b")),
                List.of(new Edge("A", "B", -5.0), new Edge("B", "A", -5.0)));
        assertFalse(r.isValid());
        assertTrue(hasError(r, "EDGE_WEIGHT_NEGATIVE"));
    }

    @Test
    void zeroWeightOnlyWarns() {
        ImportValidationResult r = validator.validate(
                List.of(node("A", "a"), node("B", "b")),
                List.of(new Edge("A", "B", 0.0), new Edge("B", "A", 0.0)));
        assertTrue(r.isValid());
        assertTrue(hasWarning(r, "EDGE_WEIGHT_ZERO"));
    }

    @Test
    void missingReverseEdgeRejected() {
        ImportValidationResult r = validator.validate(
                List.of(node("A", "a"), node("B", "b")),
                List.of(new Edge("A", "B", 100.0)));
        assertFalse(r.isValid());
        assertTrue(hasError(r, "EDGE_REVERSE_MISSING"));
        // 反向缺失只报一次，而不是每个方向各报一次
        assertEquals(1, r.getErrorCount());
    }

    @Test
    void reverseWeightMismatchOnlyWarns() {
        ImportValidationResult r = validator.validate(
                List.of(node("A", "a"), node("B", "b")),
                List.of(new Edge("A", "B", 100.0), new Edge("B", "A", 120.0)));
        assertTrue(r.isValid());
        assertTrue(hasWarning(r, "EDGE_REVERSE_WEIGHT_MISMATCH"));
    }

    @Test
    void isolatedNodeRejected() {
        ImportValidationResult r = validator.validate(threeNodes(),
                List.of(new Edge("A", "B", 1.0), new Edge("B", "A", 1.0)));
        assertFalse(r.isValid());
        assertTrue(hasError(r, "ISOLATED_NODE"));
        // 报错信息应包含孤立节点 id，便于定位
        String msg = r.getErrors().stream()
                .filter(i -> i.getCode().equals("ISOLATED_NODE"))
                .findFirst().orElseThrow().getMessage();
        assertTrue(msg.contains("C"), "孤立节点信息应包含节点 id: " + msg);
    }

    @Test
    void unknownEdgeEndpointRejected() {
        ImportValidationResult r = validator.validate(
                List.of(node("A", "a"), node("B", "b")),
                List.of(new Edge("A", "X", 1.0), new Edge("X", "A", 1.0),
                        new Edge("A", "B", 1.0), new Edge("B", "A", 1.0)));
        assertFalse(r.isValid());
        assertTrue(hasError(r, "EDGE_UNKNOWN_NODE"));
    }

    @Test
    void selfLoopRejected() {
        ImportValidationResult r = validator.validate(
                List.of(node("A", "a"), node("B", "b")),
                List.of(new Edge("A", "A", 1.0),
                        new Edge("A", "B", 1.0), new Edge("B", "A", 1.0)));
        assertFalse(r.isValid());
        assertTrue(hasError(r, "EDGE_SELF_LOOP"));
    }

    @Test
    void duplicateEdgeRejected() {
        ImportValidationResult r = validator.validate(
                List.of(node("A", "a"), node("B", "b")),
                List.of(new Edge("A", "B", 1.0), new Edge("A", "B", 2.0),
                        new Edge("B", "A", 1.0)));
        assertFalse(r.isValid());
        assertTrue(hasError(r, "DUPLICATE_EDGE"));
    }

    @Test
    void blankEdgeEndpointRejected() {
        ImportValidationResult r = validator.validate(
                List.of(node("A", "a"), node("B", "b")),
                List.of(new Edge("A", " ", 1.0)));
        assertFalse(r.isValid());
        assertTrue(hasError(r, "EDGE_ENDPOINT_BLANK"));
    }

    @Test
    void disconnectedComponentOnlyWarns() {
        // A-B 与 C-D 两个独立子图：无孤立节点，但图不连通
        ImportValidationResult r = validator.validate(
                List.of(node("A", "a"), node("B", "b"), node("C", "c"), node("D", "d")),
                List.of(new Edge("A", "B", 1.0), new Edge("B", "A", 1.0),
                        new Edge("C", "D", 1.0), new Edge("D", "C", 1.0)));
        assertTrue(r.isValid(), "不连通只应告警不应阻断: " + r.getErrors());
        assertTrue(hasWarning(r, "GRAPH_DISCONNECTED"));
    }

    @Test
    void singleNodeWithoutEdgesPasses() {
        ImportValidationResult r = validator.validate(List.of(node("A", "a")), List.of());
        assertTrue(r.isValid());
    }

    @Test
    void errorMessagesAreReadable() {
        ImportValidationResult r = validator.validate(
                List.of(node("A", "解放碑"), node("B", "洪崖洞")),
                List.of(new Edge("A", "B", -3.5)));
        for (var issue : r.getErrors()) {
            assertFalse(issue.getMessage().isBlank());
            assertFalse(issue.getCode().isBlank());
        }
        assertTrue(r.getErrors().stream().anyMatch(i -> i.getMessage().contains("负权重")));
        assertTrue(r.getErrors().stream().anyMatch(i -> i.getMessage().contains("反向缺失")));
    }
}

package com.cqu.service;

import com.cqu.model.Edge;
import com.cqu.model.ImportValidationIssue;
import com.cqu.model.ImportValidationResult;
import com.cqu.model.Node;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GraphImportValidatorTest {
    private final GraphImportValidator validator = new GraphImportValidator();

    private static Node node(String id) {
        return new Node(id, "景点" + id, 29.5, 106.5, "t", "d");
    }

    private static Node node(String id, String name) {
        return new Node(id, name, 29.5, 106.5, "t", "d");
    }

    private static Node node(String id, double lat, double lng) {
        return new Node(id, "景点" + id, lat, lng, "t", "d");
    }

    private static Edge edge(String from, String to) {
        return new Edge(from, to, null);
    }

    private static Edge edge(String from, String to, double weight) {
        return new Edge(from, to, weight);
    }

    /** A、B、C 三角全连接（双向），默认合法。 */
    private static List<Edge> triangleEdges() {
        return List.of(
                edge("A", "B"), edge("B", "A"),
                edge("B", "C"), edge("C", "B"),
                edge("A", "C"), edge("C", "A")
        );
    }

    private static boolean hasError(ImportValidationResult r, String code) {
        return r.getErrors().stream().anyMatch(i -> i.getCode().equals(code));
    }

    private static boolean hasWarning(ImportValidationResult r, String code) {
        return r.getWarnings().stream().anyMatch(i -> i.getCode().equals(code));
    }

    @Test
    void validGraphPasses() {
        ImportValidationResult r = validator.validate(
                List.of(node("A"), node("B"), node("C")), triangleEdges());
        assertTrue(r.isValid(), "合法数据应通过校验: " + r.getIssues());
        assertEquals(0, r.getErrorCount());
        assertEquals(0, r.getWarningCount());
    }

    @Test
    void emptyNodeListRejected() {
        ImportValidationResult r = validator.validate(List.of(), List.of());
        assertFalse(r.isValid());
        assertTrue(hasError(r, "EMPTY_NODE_LIST"));
    }

    @Test
    void nullNodeListRejected() {
        ImportValidationResult r = validator.validate(null, null);
        assertFalse(r.isValid());
        assertTrue(hasError(r, "EMPTY_NODE_LIST"));
    }

    @Test
    void duplicateNodeIdRejected() {
        ImportValidationResult r = validator.validate(
                List.of(node("A"), node("A", "另一个名字"), node("B")),
                List.of(edge("A", "B"), edge("B", "A")));
        assertFalse(r.isValid());
        assertTrue(hasError(r, "DUPLICATE_NODE_ID"));
    }

    @Test
    void duplicateNodeNameRejected() {
        ImportValidationResult r = validator.validate(
                List.of(node("A", "磁器口"), node("B", "磁器口")),
                List.of(edge("A", "B"), edge("B", "A")));
        assertFalse(r.isValid());
        assertTrue(hasError(r, "DUPLICATE_NODE_NAME"));
    }

    @Test
    void missingNodeNameRejected() {
        ImportValidationResult r = validator.validate(
                List.of(node("A", ""), node("B")),
                List.of(edge("A", "B"), edge("B", "A")));
        assertFalse(r.isValid());
        assertTrue(hasError(r, "MISSING_NODE_NAME"));
    }

    @Test
    void invalidCoordinatesRejected() {
        ImportValidationResult r = validator.validate(
                List.of(node("A", 999.0, 106.5), node("B")),
                List.of(edge("A", "B"), edge("B", "A")));
        assertFalse(r.isValid());
        assertTrue(hasError(r, "INVALID_COORDINATES"));
    }

    @Test
    void negativeWeightRejected() {
        ImportValidationResult r = validator.validate(
                List.of(node("A"), node("B")),
                List.of(edge("A", "B", -5.0), edge("B", "A")));
        assertFalse(r.isValid());
        assertTrue(hasError(r, "NEGATIVE_WEIGHT"));
    }

    @Test
    void zeroWeightRejected() {
        ImportValidationResult r = validator.validate(
                List.of(node("A"), node("B")),
                List.of(edge("A", "B", 0.0), edge("B", "A")));
        assertFalse(r.isValid());
        assertTrue(hasError(r, "ZERO_WEIGHT"));
    }

    @Test
    void nanWeightRejected() {
        ImportValidationResult r = validator.validate(
                List.of(node("A"), node("B")),
                List.of(edge("A", "B", Double.NaN), edge("B", "A")));
        assertFalse(r.isValid());
        assertTrue(hasError(r, "INVALID_WEIGHT"));
    }

    @Test
    void missingReverseEdgeRejected() {
        ImportValidationResult r = validator.validate(
                List.of(node("A"), node("B")),
                List.of(edge("A", "B")));
        assertFalse(r.isValid());
        assertTrue(hasError(r, "MISSING_REVERSE_EDGE"));
        assertEquals(1, r.getErrorCount(), "只应报告缺少反向边一个问题: " + r.getIssues());
    }

    @Test
    void isolatedNodeRejected() {
        ImportValidationResult r = validator.validate(
                List.of(node("A"), node("B"), node("C")),
                List.of(edge("A", "B"), edge("B", "A")));
        assertFalse(r.isValid());
        assertTrue(hasError(r, "ISOLATED_NODE"));
        assertEquals(1, r.getErrorCount());
        assertTrue(r.getErrors().get(0).getMessage().contains("C"), "错误信息应指出孤立景点");
    }

    @Test
    void unknownEndpointRejected() {
        ImportValidationResult r = validator.validate(
                List.of(node("A"), node("B")),
                List.of(edge("A", "ZZZ"), edge("A", "B"), edge("B", "A")));
        assertFalse(r.isValid());
        assertTrue(hasError(r, "UNKNOWN_ENDPOINT"));
    }

    @Test
    void selfLoopRejected() {
        ImportValidationResult r = validator.validate(
                List.of(node("A"), node("B")),
                List.of(edge("A", "A"), edge("A", "B"), edge("B", "A")));
        assertFalse(r.isValid());
        assertTrue(hasError(r, "SELF_LOOP"));
    }

    @Test
    void duplicateEdgeRejected() {
        ImportValidationResult r = validator.validate(
                List.of(node("A"), node("B")),
                List.of(edge("A", "B"), edge("A", "B"), edge("B", "A")));
        assertFalse(r.isValid());
        assertTrue(hasError(r, "DUPLICATE_EDGE"));
    }

    @Test
    void emptyEdgeListRejected() {
        ImportValidationResult r = validator.validate(
                List.of(node("A"), node("B")), List.of());
        assertFalse(r.isValid());
        assertTrue(hasError(r, "EMPTY_EDGE_LIST"));
    }

    @Test
    void disconnectedGraphRejected() {
        ImportValidationResult r = validator.validate(
                List.of(node("A"), node("B"), node("C"), node("D")),
                List.of(edge("A", "B"), edge("B", "A"), edge("C", "D"), edge("D", "C")));
        assertFalse(r.isValid());
        assertTrue(hasError(r, "DISCONNECTED_GRAPH"));
        assertFalse(hasError(r, "ISOLATED_NODE"), "成对连接但子图断开时不应误报孤立节点");
    }

    @Test
    void weightDeviationOnlyWarns() {
        // A、B 直线距离约 973 米，权重 100 公里明显异常 -> 警告但不阻断
        ImportValidationResult r = validator.validate(
                List.of(node("A", 29.0, 106.0), node("B", 29.0, 106.01)),
                List.of(edge("A", "B", 100_000.0), edge("B", "A", 100_000.0)));
        assertTrue(r.isValid(), "仅警告时仍应通过: " + r.getIssues());
        assertTrue(hasWarning(r, "WEIGHT_DEVIATION"));
    }

    @Test
    void weightMismatchOnlyWarns() {
        // A、B 直线距离约 973 米，双向权重 1000/2000 都在合理偏差内但互不一致
        ImportValidationResult r = validator.validate(
                List.of(node("A", 29.0, 106.0), node("B", 29.0, 106.01)),
                List.of(edge("A", "B", 1000.0), edge("B", "A", 2000.0)));
        assertTrue(r.isValid(), "仅警告时仍应通过: " + r.getIssues());
        assertTrue(hasWarning(r, "WEIGHT_MISMATCH"));
        assertEquals(1, r.getWarningCount(), "同一连接的双向权重不一致只应报告一次");
    }

    @Test
    void collectsMultipleErrorsAtOnce() {
        ImportValidationResult r = validator.validate(
                List.of(node("A"), node("A"), node("B"), node("C")),
                List.of(edge("A", "B", -1.0), edge("B", "A")));
        assertFalse(r.isValid());
        assertTrue(hasError(r, "DUPLICATE_NODE_ID"));
        assertTrue(hasError(r, "NEGATIVE_WEIGHT"));
        assertTrue(hasError(r, "ISOLATED_NODE"));
        assertTrue(r.getErrorCount() >= 3, "应一次性收集全部错误: " + r.getIssues());
    }

    @Test
    void errorMessagesAreReadable() {
        ImportValidationResult r = validator.validate(
                List.of(node("A"), node("B")),
                List.of(edge("A", "B", -3.5)));
        for (ImportValidationIssue issue : r.getIssues()) {
            assertFalse(issue.getMessage().isBlank(), "错误信息不能为空");
            assertFalse(issue.getCode().isBlank(), "错误码不能为空");
        }
        assertTrue(r.getErrors().get(0).getMessage().contains("-3.5"), "信息应包含具体数值");
    }
}

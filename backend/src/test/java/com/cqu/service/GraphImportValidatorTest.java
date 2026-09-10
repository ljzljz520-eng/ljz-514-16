package com.cqu.service;

import com.cqu.model.Edge;
import com.cqu.model.Node;
import com.cqu.model.ValidationIssue;
import com.cqu.model.ValidationResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GraphImportValidatorTest {

    private final GraphImportValidator validator = new GraphImportValidator();

    private static Node node(String id, String name) {
        return new Node(id, name, 29.5630, 106.5758, "景区", "描述");
    }

    private static Node nodeAt(String id, String name, double lat, double lng) {
        return new Node(id, name, lat, lng, "景区", "描述");
    }

    /** 生成 A-B-C-... 链式双向边，权重 100 米。 */
    private static List<Edge> chain(String... ids) {
        List<Edge> edges = new ArrayList<>();
        for (int i = 1; i < ids.length; i++) {
            edges.add(new Edge(ids[i - 1], ids[i], 100.0));
            edges.add(new Edge(ids[i], ids[i - 1], 100.0));
        }
        return edges;
    }

    private static Set<ValidationIssue.Code> errorCodes(ValidationResult result) {
        return result.getErrors().stream()
                .map(ValidationIssue::getCode)
                .collect(Collectors.toSet());
    }

    private static Set<ValidationIssue.Code> warningCodes(ValidationResult result) {
        return result.getWarnings().stream()
                .map(ValidationIssue::getCode)
                .collect(Collectors.toSet());
    }

    @Test
    void validDataPassesWithoutErrors() {
        ValidationResult result = validator.validate(
                List.of(node("A", "解放碑"), node("B", "洪崖洞"), node("C", "朝天门")),
                chain("A", "B", "C"));

        assertTrue(result.isValid());
        assertTrue(result.getErrors().isEmpty());
        assertTrue(result.getWarnings().isEmpty());
    }

    @Test
    void duplicateNodeIdIsRejected() {
        ValidationResult result = validator.validate(
                List.of(node("A", "解放碑"), node("A", "洪崖洞"), node("B", "朝天门")),
                chain("A", "B"));

        assertFalse(result.isValid());
        assertTrue(errorCodes(result).contains(ValidationIssue.Code.DUPLICATE_NODE_ID));
    }

    @Test
    void duplicateNodeNameIsRejected() {
        ValidationResult result = validator.validate(
                List.of(node("A", "解放碑"), node("B", "解放碑"), node("C", "朝天门")),
                chain("A", "B", "C"));

        assertFalse(result.isValid());
        assertTrue(errorCodes(result).contains(ValidationIssue.Code.DUPLICATE_NODE_NAME));
    }

    @Test
    void negativeWeightIsRejected() {
        List<Edge> edges = new ArrayList<>(chain("B", "C"));
        edges.add(new Edge("A", "B", -10.0));
        edges.add(new Edge("B", "A", -10.0));

        ValidationResult result = validator.validate(
                List.of(node("A", "解放碑"), node("B", "洪崖洞"), node("C", "朝天门")),
                edges);

        assertFalse(result.isValid());
        assertTrue(errorCodes(result).contains(ValidationIssue.Code.NEGATIVE_WEIGHT));
    }

    @Test
    void missingReverseEdgeIsRejected() {
        ValidationResult result = validator.validate(
                List.of(node("A", "解放碑"), node("B", "洪崖洞")),
                List.of(new Edge("A", "B", 100.0)));

        assertFalse(result.isValid());
        assertTrue(errorCodes(result).contains(ValidationIssue.Code.MISSING_REVERSE_EDGE));
    }

    @Test
    void isolatedNodeIsRejected() {
        ValidationResult result = validator.validate(
                List.of(node("A", "解放碑"), node("B", "洪崖洞"), node("C", "朝天门")),
                chain("A", "B"));

        assertFalse(result.isValid());
        assertTrue(errorCodes(result).contains(ValidationIssue.Code.ISOLATED_NODE));
    }

    @Test
    void edgeReferencingUnknownNodeIsRejected() {
        ValidationResult result = validator.validate(
                List.of(node("A", "解放碑"), node("B", "洪崖洞")),
                List.of(new Edge("A", "X", 100.0), new Edge("X", "A", 100.0),
                        new Edge("A", "B", 100.0), new Edge("B", "A", 100.0)));

        assertFalse(result.isValid());
        assertTrue(errorCodes(result).contains(ValidationIssue.Code.UNKNOWN_NODE));
    }

    @Test
    void selfLoopIsRejected() {
        List<Edge> edges = new ArrayList<>(chain("A", "B"));
        edges.add(new Edge("A", "A", 0.0));

        ValidationResult result = validator.validate(
                List.of(node("A", "解放碑"), node("B", "洪崖洞")),
                edges);

        assertFalse(result.isValid());
        assertTrue(errorCodes(result).contains(ValidationIssue.Code.SELF_LOOP));
    }

    @Test
    void invalidCoordinateIsRejected() {
        ValidationResult result = validator.validate(
                List.of(nodeAt("A", "解放碑", 91.0, 106.5758), node("B", "洪崖洞")),
                chain("A", "B"));

        assertFalse(result.isValid());
        assertTrue(errorCodes(result).contains(ValidationIssue.Code.INVALID_COORDINATE));
    }

    @Test
    void blankNodeIdIsRejected() {
        ValidationResult result = validator.validate(
                List.of(node("  ", "解放碑"), node("B", "洪崖洞")),
                chain("A", "B"));

        assertFalse(result.isValid());
        assertTrue(errorCodes(result).contains(ValidationIssue.Code.BLANK_NODE_ID));
    }

    @Test
    void emptyNodeListIsRejected() {
        ValidationResult result = validator.validate(List.of(), chain("A", "B"));

        assertFalse(result.isValid());
        assertTrue(errorCodes(result).contains(ValidationIssue.Code.EMPTY_NODE_LIST));
    }

    @Test
    void emptyEdgeListIsRejected() {
        ValidationResult result = validator.validate(
                List.of(node("A", "解放碑"), node("B", "洪崖洞")),
                List.of());

        assertFalse(result.isValid());
        assertTrue(errorCodes(result).contains(ValidationIssue.Code.EMPTY_EDGE_LIST));
    }

    @Test
    void asymmetricReverseWeightIsRejected() {
        ValidationResult result = validator.validate(
                List.of(node("A", "解放碑"), node("B", "洪崖洞")),
                List.of(new Edge("A", "B", 100.0), new Edge("B", "A", 150.0)));

        assertFalse(result.isValid());
        assertTrue(errorCodes(result).contains(ValidationIssue.Code.ASYMMETRIC_WEIGHT));
    }

    @Test
    void missingDistanceIsWarningAndDoesNotBlock() {
        ValidationResult result = validator.validate(
                List.of(node("A", "解放碑"), node("B", "洪崖洞")),
                List.of(new Edge("A", "B", null), new Edge("B", "A", null)));

        assertTrue(result.isValid());
        assertTrue(warningCodes(result).contains(ValidationIssue.Code.MISSING_DISTANCE));
    }

    @Test
    void duplicateEdgeIsWarningAndDoesNotBlock() {
        ValidationResult result = validator.validate(
                List.of(node("A", "解放碑"), node("B", "洪崖洞")),
                List.of(new Edge("A", "B", 100.0), new Edge("A", "B", 100.0),
                        new Edge("B", "A", 100.0)));

        assertTrue(result.isValid());
        assertTrue(warningCodes(result).contains(ValidationIssue.Code.DUPLICATE_EDGE));
    }

    @Test
    void disconnectedGraphIsWarningAndDoesNotBlock() {
        List<Edge> edges = new ArrayList<>(chain("A", "B"));
        edges.addAll(chain("C", "D"));

        ValidationResult result = validator.validate(
                List.of(node("A", "解放碑"), node("B", "洪崖洞"),
                        node("C", "朝天门"), node("D", "磁器口")),
                edges);

        assertTrue(result.isValid());
        assertTrue(warningCodes(result).contains(ValidationIssue.Code.DISCONNECTED_GRAPH));
    }

    @Test
    void errorMessagesAreReadableAndContainContext() {
        ValidationResult result = validator.validate(
                List.of(node("A", "解放碑"), node("A", "解放碑")),
                List.of());

        assertFalse(result.getErrors().isEmpty());
        for (ValidationIssue issue : result.getErrors()) {
            assertFalse(issue.getMessage() == null || issue.getMessage().isBlank());
        }
        assertTrue(result.getErrors().stream()
                .anyMatch(i -> i.getMessage().contains("A")));
    }
}

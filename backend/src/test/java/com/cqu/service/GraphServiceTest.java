package com.cqu.service;

import com.cqu.model.Node;
import com.cqu.model.PathResult;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class GraphServiceTest {
    @Test
    void shortestPathReturnsValidResult() {
        Map<String, Node> nodes = Map.of(
                "A", new Node("A", "A", 29.56301, 106.57577, "t", "d"),
                "B", new Node("B", "B", 29.56470, 106.58169, "t", "d"),
                "C", new Node("C", "C", 29.56336, 106.58713, "t", "d")
        );
        GraphService g = new GraphService(nodes);
        PathResult r = g.shortestPath("A", "C");
        assertNotNull(r);
        assertEquals("A", r.getStartId());
        assertEquals("C", r.getEndId());
        assertFalse(r.getPathNodeIds().isEmpty());
        assertEquals(r.getPathNodeIds().size(), r.getPathNodes().size());
    }
}


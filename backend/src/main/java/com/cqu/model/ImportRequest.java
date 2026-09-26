package com.cqu.model;

import java.util.List;

/**
 * 管理员导入请求体：{"nodes": [...], "edges": [...]}。
 */
public class ImportRequest {
    private List<Node> nodes;
    private List<Edge> edges;

    public ImportRequest() {
    }

    public List<Node> getNodes() {
        return nodes;
    }

    public void setNodes(List<Node> nodes) {
        this.nodes = nodes;
    }

    public List<Edge> getEdges() {
        return edges;
    }

    public void setEdges(List<Edge> edges) {
        this.edges = edges;
    }
}

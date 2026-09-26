package com.cqu.model;

import java.util.List;

/**
 * 管理员导入请求体：整图替换式的节点 + 边数据。
 */
public class ImportRequest {
    private List<Node> nodes;
    private List<Edge> edges;

    public ImportRequest() {
    }

    public ImportRequest(List<Node> nodes, List<Edge> edges) {
        this.nodes = nodes;
        this.edges = edges;
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

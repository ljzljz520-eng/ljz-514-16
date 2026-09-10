package com.cqu.model;

import java.util.List;

/**
 * 管理员导入图数据的请求体：nodes + edges 整体构成一份新的图数据快照。
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

package com.cqu.model;

public class Edge {
    private final String fromId;
    private final String toId;
    private final Double distanceMeters;

    public Edge(String fromId, String toId, Double distanceMeters) {
        this.fromId = fromId;
        this.toId = toId;
        this.distanceMeters = distanceMeters;
    }

    public String getFromId() {
        return fromId;
    }

    public String getToId() {
        return toId;
    }

    public Double getDistanceMeters() {
        return distanceMeters;
    }
}


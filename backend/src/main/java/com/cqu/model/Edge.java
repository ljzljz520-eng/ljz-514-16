package com.cqu.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Edge {
    private final String fromId;
    private final String toId;
    private final Double distanceMeters;

    @JsonCreator
    public Edge(@JsonProperty("fromId") @JsonAlias("from") String fromId,
                @JsonProperty("toId") @JsonAlias("to") String toId,
                @JsonProperty("distanceMeters")
                @JsonAlias({"distance_meters", "weightMeters", "weight_meters"}) Double distanceMeters) {
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


package com.cqu.service;

import com.cqu.model.Node;
import com.cqu.model.Edge;
import com.cqu.model.PathResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

public class GraphService {
    private final Map<String, Node> nodes;
    private final Map<String, List<Neighbor>> adjacency;

    public GraphService(Map<String, Node> nodes) {
        this.nodes = Map.copyOf(nodes);
        this.adjacency = buildGraph(this.nodes);
    }

    public GraphService(Map<String, Node> nodes, List<Edge> edges) {
        this.nodes = Map.copyOf(nodes);
        if (edges != null && !edges.isEmpty()) {
            this.adjacency = buildGraphFromEdges(this.nodes, edges);
        } else {
            this.adjacency = buildGraph(this.nodes);
        }
    }

    public List<Node> listNodes() {
        return nodes.values().stream().sorted(Comparator.comparing(Node::getName)).toList();
    }

    public PathResult shortestPath(String fromId, String toId) {
        if (fromId == null || toId == null || !nodes.containsKey(fromId) || !nodes.containsKey(toId)) {
            throw new IllegalArgumentException("起点或终点不存在");
        }
        if (fromId.equals(toId)) {
            List<String> ids = List.of(fromId);
            List<Node> ns = List.of(nodes.get(fromId));
            return new PathResult(fromId, toId, 0.0, ids, ns, List.of());
        }

        Map<String, Double> dist = new HashMap<>();
        Map<String, String> prev = new HashMap<>();
        PriorityQueue<State> pq = new PriorityQueue<>(Comparator.comparingDouble(s -> s.distance));

        for (String id : nodes.keySet()) {
            dist.put(id, Double.POSITIVE_INFINITY);
        }
        dist.put(fromId, 0.0);
        pq.add(new State(fromId, 0.0));

        while (!pq.isEmpty()) {
            State cur = pq.poll();
            if (cur.distance > dist.get(cur.id)) {
                continue;
            }
            if (cur.id.equals(toId)) {
                break;
            }
            List<Neighbor> neighbors = adjacency.getOrDefault(cur.id, List.of());
            for (Neighbor nb : neighbors) {
                double nd = cur.distance + nb.weightMeters;
                if (nd < dist.get(nb.toId)) {
                    dist.put(nb.toId, nd);
                    prev.put(nb.toId, cur.id);
                    pq.add(new State(nb.toId, nd));
                }
            }
        }

        if (!prev.containsKey(toId)) {
            throw new IllegalStateException("未找到可达路径");
        }

        List<String> pathIds = new ArrayList<>();
        String cur = toId;
        pathIds.add(cur);
        while (!cur.equals(fromId)) {
            cur = prev.get(cur);
            if (cur == null) {
                throw new IllegalStateException("未找到可达路径");
            }
            pathIds.add(cur);
        }
        java.util.Collections.reverse(pathIds);

        List<Node> pathNodes = pathIds.stream().map(nodes::get).toList();
        List<Double> segments = new ArrayList<>();
        double total = dist.getOrDefault(toId, Double.POSITIVE_INFINITY);
        for (int i = 1; i < pathNodes.size(); i++) {
            Node a = pathNodes.get(i - 1);
            Node b = pathNodes.get(i);
            segments.add(weightBetween(a.getId(), b.getId(), a.getLat(), a.getLng(), b.getLat(), b.getLng()));
        }

        return new PathResult(fromId, toId, total, pathIds, pathNodes, segments);
    }

    private double weightBetween(String fromId, String toId, double fromLat, double fromLng, double toLat, double toLng) {
        for (Neighbor nb : adjacency.getOrDefault(fromId, List.of())) {
            if (nb.toId.equals(toId)) {
                return nb.weightMeters;
            }
        }
        return GeoUtils.haversineMeters(fromLat, fromLng, toLat, toLng);
    }

    private static Map<String, List<Neighbor>> buildGraph(Map<String, Node> nodes) {
        int k = Integer.parseInt(System.getenv().getOrDefault("GRAPH_K", "6"));
        double maxDist = Double.parseDouble(System.getenv().getOrDefault("GRAPH_MAX_DISTANCE_METERS", "15000"));

        Map<String, List<Neighbor>> adj = new HashMap<>();
        for (String id : nodes.keySet()) {
            adj.put(id, new ArrayList<>());
        }

        Set<String> undirected = new HashSet<>();
        List<Node> all = new ArrayList<>(nodes.values());

        for (Node a : all) {
            List<Neighbor> candidates = new ArrayList<>();
            for (Node b : all) {
                if (a.getId().equals(b.getId())) {
                    continue;
                }
                double d = GeoUtils.haversineMeters(a.getLat(), a.getLng(), b.getLat(), b.getLng());
                if (d <= maxDist) {
                    candidates.add(new Neighbor(b.getId(), d));
                }
            }
            candidates.sort(Comparator.comparingDouble(n -> n.weightMeters));
            int limit = Math.min(k, candidates.size());
            for (int i = 0; i < limit; i++) {
                Neighbor nb = candidates.get(i);
                String key = pairKey(a.getId(), nb.toId);
                if (undirected.add(key)) {
                    adj.get(a.getId()).add(nb);
                    adj.get(nb.toId).add(new Neighbor(a.getId(), nb.weightMeters));
                }
            }
        }

        ensureConnectivity(adj, nodes);
        return adj;
    }

    private static Map<String, List<Neighbor>> buildGraphFromEdges(Map<String, Node> nodes, List<Edge> edges) {
        Map<String, List<Neighbor>> adj = new HashMap<>();
        for (String id : nodes.keySet()) {
            adj.put(id, new ArrayList<>());
        }

        for (Edge e : edges) {
            if (e == null) {
                continue;
            }
            String from = e.getFromId();
            String to = e.getToId();
            if (from == null || to == null || !nodes.containsKey(from) || !nodes.containsKey(to) || from.equals(to)) {
                continue;
            }

            Node a = nodes.get(from);
            Node b = nodes.get(to);
            double w = e.getDistanceMeters() != null
                    ? e.getDistanceMeters()
                    : GeoUtils.haversineMeters(a.getLat(), a.getLng(), b.getLat(), b.getLng());

            adj.get(from).add(new Neighbor(to, w));
            adj.get(to).add(new Neighbor(from, w));
        }

        boolean ensure = Boolean.parseBoolean(System.getenv().getOrDefault("GRAPH_ENSURE_CONNECTIVITY", "true"));
        if (ensure) {
            ensureConnectivity(adj, nodes);
        }
        return adj;
    }

    private static void ensureConnectivity(Map<String, List<Neighbor>> adj, Map<String, Node> nodes) {
        if (nodes.isEmpty()) {
            return;
        }
        Set<String> visited = new HashSet<>();
        String start = nodes.keySet().iterator().next();
        dfs(start, adj, visited);
        if (visited.size() == nodes.size()) {
            return;
        }

        List<String> remaining = nodes.keySet().stream().filter(id -> !visited.contains(id)).toList();
        Set<String> allVisited = new HashSet<>(visited);
        for (String id : remaining) {
            String connectTo = nearestInSet(id, allVisited, nodes);
            Node a = nodes.get(id);
            Node b = nodes.get(connectTo);
            double d = GeoUtils.haversineMeters(a.getLat(), a.getLng(), b.getLat(), b.getLng());
            adj.get(id).add(new Neighbor(connectTo, d));
            adj.get(connectTo).add(new Neighbor(id, d));
            dfs(id, adj, allVisited);
        }
    }

    private static String nearestInSet(String fromId, Set<String> set, Map<String, Node> nodes) {
        Node a = nodes.get(fromId);
        String bestId = null;
        double best = Double.POSITIVE_INFINITY;
        for (String candidate : set) {
            Node b = nodes.get(candidate);
            double d = GeoUtils.haversineMeters(a.getLat(), a.getLng(), b.getLat(), b.getLng());
            if (d < best) {
                best = d;
                bestId = candidate;
            }
        }
        if (bestId == null) {
            throw new IllegalStateException("无法确保连通性");
        }
        return bestId;
    }

    private static void dfs(String id, Map<String, List<Neighbor>> adj, Set<String> visited) {
        if (!visited.add(id)) {
            return;
        }
        for (Neighbor nb : adj.getOrDefault(id, List.of())) {
            dfs(nb.toId, adj, visited);
        }
    }

    private static String pairKey(String a, String b) {
        return a.compareTo(b) < 0 ? a + "::" + b : b + "::" + a;
    }

    private static final class Neighbor {
        private final String toId;
        private final double weightMeters;

        private Neighbor(String toId, double weightMeters) {
            this.toId = toId;
            this.weightMeters = weightMeters;
        }
    }

    private static final class State {
        private final String id;
        private final double distance;

        private State(String id, double distance) {
            this.id = id;
            this.distance = distance;
        }
    }
}

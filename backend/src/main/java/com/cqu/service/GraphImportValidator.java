package com.cqu.service;

import com.cqu.model.Edge;
import com.cqu.model.ImportValidationIssue;
import com.cqu.model.ImportValidationResult;
import com.cqu.model.Node;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 管理员导入校验（服务层，纯逻辑，便于单元测试）。
 *
 * 校验规则：
 * - 节点：非空、id/name 必填、坐标合法、景点 id/名称不重复
 * - 边：端点必填且已存在、禁止自环、权重必须为正数（拒绝负权重/0/非法数值）、同一连接不重复定义、反向边必须成对出现
 * - 图：不允许孤立节点、必须整体连通
 * - 提示（WARNING，不阻断导入）：双向权重不一致、权重与直线距离偏差过大
 */
public class GraphImportValidator {
    /** 权重小于直线距离 × 该比例时给出提示（物理上不可能更短，留 10% 测量容差） */
    static final double WEIGHT_SHORT_RATIO = 0.9;
    /** 权重大于直线距离 × 该比例时给出提示（可能单位填错） */
    static final double WEIGHT_LONG_RATIO = 3.0;
    /** 双向权重差超过该比例时给出提示 */
    static final double WEIGHT_MISMATCH_RATIO = 0.01;
    /** 不连通提示中最多列出的景点数量 */
    static final int MAX_LISTED_DISCONNECTED = 5;

    public ImportValidationResult validate(List<Node> nodes, List<Edge> edges) {
        List<ImportValidationIssue> issues = new ArrayList<>();
        Map<String, Node> nodeIndex = checkNodes(nodes, issues);
        checkEdges(edges, nodeIndex, issues);
        if (!nodeIndex.isEmpty()) {
            checkIsolatedAndConnectivity(nodeIndex, edges, issues);
        }
        return new ImportValidationResult(issues);
    }

    /**
     * 校验节点并返回 去重后的 id → 节点 索引（重复/非法节点不覆盖首次出现的）。
     */
    private Map<String, Node> checkNodes(List<Node> nodes, List<ImportValidationIssue> issues) {
        Map<String, Node> index = new LinkedHashMap<>();
        if (nodes == null || nodes.isEmpty()) {
            issues.add(ImportValidationIssue.error("EMPTY_NODE_LIST", "节点列表为空，至少需要提供一个景点"));
            return index;
        }

        Map<String, Integer> idCount = new LinkedHashMap<>();
        Map<String, List<String>> nameToIds = new LinkedHashMap<>();
        for (int i = 0; i < nodes.size(); i++) {
            Node n = nodes.get(i);
            int row = i + 1;
            if (n == null) {
                issues.add(ImportValidationIssue.error("NULL_NODE", "第 " + row + " 个节点为空"));
                continue;
            }
            String id = trimToNull(n.getId());
            String name = trimToNull(n.getName());
            if (id == null) {
                issues.add(ImportValidationIssue.error("MISSING_NODE_ID", "第 " + row + " 个节点缺少 id"));
                continue;
            }
            if (name == null) {
                issues.add(ImportValidationIssue.error("MISSING_NODE_NAME", "景点 '" + id + "' 缺少名称"));
            }
            if (!Double.isFinite(n.getLat()) || !Double.isFinite(n.getLng())
                    || Math.abs(n.getLat()) > 90 || Math.abs(n.getLng()) > 180) {
                issues.add(ImportValidationIssue.error("INVALID_COORDINATES",
                        "景点 '" + displayName(n) + "'(" + id + ") 坐标非法：lat=" + n.getLat() + ", lng=" + n.getLng()));
            }

            idCount.merge(id, 1, Integer::sum);
            if (name != null) {
                nameToIds.computeIfAbsent(name, k -> new ArrayList<>()).add(id);
            }
            index.putIfAbsent(id, n);
        }

        for (Map.Entry<String, Integer> e : idCount.entrySet()) {
            if (e.getValue() > 1) {
                issues.add(ImportValidationIssue.error("DUPLICATE_NODE_ID",
                        "重复的景点ID：'" + e.getKey() + "' 出现了 " + e.getValue() + " 次"));
            }
        }
        for (Map.Entry<String, List<String>> e : nameToIds.entrySet()) {
            if (e.getValue().size() > 1) {
                issues.add(ImportValidationIssue.error("DUPLICATE_NODE_NAME",
                        "重复的景点名称：'" + e.getKey() + "' 对应多个ID " + e.getValue()));
            }
        }
        return index;
    }

    private void checkEdges(List<Edge> edges, Map<String, Node> nodeIndex, List<ImportValidationIssue> issues) {
        if (edges == null || edges.isEmpty()) {
            if (nodeIndex.size() > 1) {
                issues.add(ImportValidationIssue.error("EMPTY_EDGE_LIST",
                        "边列表为空：" + nodeIndex.size() + " 个景点之间没有任何连接"));
            }
            return;
        }

        Map<DirectedPair, Integer> directedCount = new LinkedHashMap<>();
        Map<DirectedPair, Double> directedWeight = new HashMap<>();
        for (int i = 0; i < edges.size(); i++) {
            Edge e = edges.get(i);
            int row = i + 1;
            if (e == null) {
                issues.add(ImportValidationIssue.error("NULL_EDGE", "第 " + row + " 条边为空"));
                continue;
            }
            String from = trimToNull(e.getFromId());
            String to = trimToNull(e.getToId());
            if (from == null || to == null) {
                issues.add(ImportValidationIssue.error("MISSING_EDGE_ENDPOINT",
                        "第 " + row + " 条边缺少起点或终点（from=" + e.getFromId() + ", to=" + e.getToId() + "）"));
                continue;
            }
            if (from.equals(to)) {
                issues.add(ImportValidationIssue.error("SELF_LOOP", "第 " + row + " 条边是自环：'" + from + "' 连接到自身"));
                continue;
            }

            Double w = e.getDistanceMeters();
            if (w != null) {
                if (!Double.isFinite(w)) {
                    issues.add(ImportValidationIssue.error("INVALID_WEIGHT",
                            "边 " + label(from, to) + " 的权重不是有效数值：" + w));
                } else if (w < 0) {
                    issues.add(ImportValidationIssue.error("NEGATIVE_WEIGHT",
                            "边 " + label(from, to) + " 的权重为负数：" + w));
                } else if (w == 0) {
                    issues.add(ImportValidationIssue.error("ZERO_WEIGHT",
                            "边 " + label(from, to) + " 的权重为 0，必须为正数"));
                }
            }

            if (!nodeIndex.containsKey(from) || !nodeIndex.containsKey(to)) {
                List<String> missing = new ArrayList<>();
                if (!nodeIndex.containsKey(from)) {
                    missing.add("'" + from + "'");
                }
                if (!nodeIndex.containsKey(to)) {
                    missing.add("'" + to + "'");
                }
                issues.add(ImportValidationIssue.error("UNKNOWN_ENDPOINT",
                        "边 " + label(from, to) + " 引用了不存在的景点：" + String.join("、", missing)));
                continue;
            }

            DirectedPair pair = new DirectedPair(from, to);
            directedCount.merge(pair, 1, Integer::sum);
            if (w != null && Double.isFinite(w) && w > 0) {
                directedWeight.putIfAbsent(pair, w);
                checkWeightDeviation(nodeIndex.get(from), nodeIndex.get(to), w, issues);
            }
        }

        for (Map.Entry<DirectedPair, Integer> e : directedCount.entrySet()) {
            if (e.getValue() > 1) {
                issues.add(ImportValidationIssue.error("DUPLICATE_EDGE",
                        "连接 " + e.getKey().label() + " 重复定义了 " + e.getValue() + " 次"));
            }
        }

        Set<DirectedPair> pairs = directedCount.keySet();
        Set<DirectedPair> reportedReverse = new HashSet<>();
        for (DirectedPair pair : pairs) {
            DirectedPair reverse = pair.reversed();
            if (!pairs.contains(reverse) && reportedReverse.add(pair.canonical())) {
                issues.add(ImportValidationIssue.error("MISSING_REVERSE_EDGE",
                        "连接 " + pair.label() + " 缺少反向边 " + reverse.label()
                                + "（图按无向处理，同一连接需成对导入）"));
            }
        }

        for (DirectedPair pair : pairs) {
            if (pair.from.compareTo(pair.to) > 0) {
                continue; // 每个无向连接只检查一次
            }
            DirectedPair reverse = pair.reversed();
            if (!pairs.contains(reverse)) {
                continue;
            }
            Double w1 = directedWeight.get(pair);
            Double w2 = directedWeight.get(reverse);
            if (w1 != null && w2 != null && weightMismatch(w1, w2)) {
                issues.add(ImportValidationIssue.warning("WEIGHT_MISMATCH",
                        "连接 " + pair.label() + " 双向权重不一致（" + w1 + " / " + w2 + " 米），请确认"));
            }
        }
    }

    /**
     * 孤立节点（无任何连接）与整体连通性检查。只统计端点合法的边。
     */
    private void checkIsolatedAndConnectivity(Map<String, Node> nodeIndex, List<Edge> edges, List<ImportValidationIssue> issues) {
        Map<String, Set<String>> undirected = new HashMap<>();
        for (String id : nodeIndex.keySet()) {
            undirected.put(id, new HashSet<>());
        }
        if (edges != null) {
            for (Edge e : edges) {
                if (e == null) {
                    continue;
                }
                String from = trimToNull(e.getFromId());
                String to = trimToNull(e.getToId());
                if (from == null || to == null || from.equals(to)
                        || !nodeIndex.containsKey(from) || !nodeIndex.containsKey(to)) {
                    continue;
                }
                undirected.get(from).add(to);
                undirected.get(to).add(from);
            }
        }

        for (Map.Entry<String, Set<String>> e : undirected.entrySet()) {
            if (e.getValue().isEmpty()) {
                Node n = nodeIndex.get(e.getKey());
                issues.add(ImportValidationIssue.error("ISOLATED_NODE",
                        "孤立景点：'" + displayName(n) + "'(" + e.getKey() + ") 没有任何连接，导入后将无法到达"));
            }
        }

        if (nodeIndex.size() <= 1) {
            return;
        }
        Set<String> visited = new HashSet<>();
        String start = nodeIndex.keySet().iterator().next();
        Deque<String> stack = new ArrayDeque<>();
        stack.push(start);
        visited.add(start);
        while (!stack.isEmpty()) {
            String cur = stack.pop();
            for (String next : undirected.getOrDefault(cur, Set.of())) {
                if (visited.add(next)) {
                    stack.push(next);
                }
            }
        }
        if (visited.size() == nodeIndex.size()) {
            return;
        }

        List<String> disconnected = new ArrayList<>();
        for (String id : nodeIndex.keySet()) {
            if (!visited.contains(id) && !undirected.get(id).isEmpty()) {
                disconnected.add("'" + displayName(nodeIndex.get(id)) + "'(" + id + ")");
            }
        }
        if (!disconnected.isEmpty()) {
            List<String> listed = disconnected.subList(0, Math.min(MAX_LISTED_DISCONNECTED, disconnected.size()));
            String suffix = disconnected.size() > MAX_LISTED_DISCONNECTED
                    ? " 等 " + disconnected.size() + " 个"
                    : "";
            issues.add(ImportValidationIssue.error("DISCONNECTED_GRAPH",
                    "图不连通：景点 " + String.join("、", listed) + suffix + " 与主图断开，请补充连接边"));
        }
    }

    private void checkWeightDeviation(Node a, Node b, double weight, List<ImportValidationIssue> issues) {
        double straight = GeoUtils.haversineMeters(a.getLat(), a.getLng(), b.getLat(), b.getLng());
        if (straight <= 0) {
            return;
        }
        if (weight < straight * WEIGHT_SHORT_RATIO) {
            issues.add(ImportValidationIssue.warning("WEIGHT_DEVIATION",
                    "边 " + label(a.getId(), b.getId()) + " 的权重 " + weight + " 米小于两地直线距离 "
                            + Math.round(straight) + " 米，请确认是否填错"));
        } else if (weight > straight * WEIGHT_LONG_RATIO) {
            issues.add(ImportValidationIssue.warning("WEIGHT_DEVIATION",
                    "边 " + label(a.getId(), b.getId()) + " 的权重 " + weight + " 米明显大于两地直线距离 "
                            + Math.round(straight) + " 米，请确认单位是否为米"));
        }
    }

    private static boolean weightMismatch(double w1, double w2) {
        double max = Math.max(w1, w2);
        return max > 0 && Math.abs(w1 - w2) / max > WEIGHT_MISMATCH_RATIO;
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String displayName(Node n) {
        String name = n.getName();
        return name == null || name.isBlank() ? "未命名" : name;
    }

    private static String label(String from, String to) {
        return "'" + from + "'→'" + to + "'";
    }

    /** 有向连接对，用作 Map key，避免字符串拼接分隔符与节点 id 冲突。 */
    private static final class DirectedPair {
        private final String from;
        private final String to;

        private DirectedPair(String from, String to) {
            this.from = from;
            this.to = to;
        }

        private DirectedPair reversed() {
            return new DirectedPair(to, from);
        }

        /** 无向规范化：保证 (a,b) 与 (b,a) 得到同一个实例，用于去重。 */
        private DirectedPair canonical() {
            return from.compareTo(to) <= 0 ? this : new DirectedPair(to, from);
        }

        private String label() {
            return GraphImportValidator.label(from, to);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof DirectedPair)) {
                return false;
            }
            DirectedPair other = (DirectedPair) o;
            return from.equals(other.from) && to.equals(other.to);
        }

        @Override
        public int hashCode() {
            return Objects.hash(from, to);
        }
    }
}

package com.cqu.service;

import com.cqu.model.Edge;
import com.cqu.model.ImportValidationResult;
import com.cqu.model.Node;
import com.cqu.model.ValidationIssue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 管理员导入节点/边之前的校验逻辑。
 *
 * <p>纯函数、无副作用、不依赖数据库与 Web 层，便于单元测试。</p>
 *
 * <p>错误（阻断导入）：重复景点（id 或名称重复）、孤立节点、负权重、
 * 同一连接反向缺失、边引用不存在的景点、自环、重复边、非法坐标、必填字段为空等。</p>
 *
 * <p>警告（不阻断）：双向权重不一致、权重为 0、图不连通（导入后系统会自动补连）、
 * 多个景点坐标完全相同。</p>
 */
public class GraphImportValidator {

    private static final int MAX_LISTED_IDS = 10;

    public ImportValidationResult validate(List<Node> nodes, List<Edge> edges) {
        List<ValidationIssue> errors = new ArrayList<>();
        List<ValidationIssue> warnings = new ArrayList<>();

        List<Node> safeNodes = nodes == null ? List.of() : nodes;
        List<Edge> safeEdges = edges == null ? List.of() : edges;

        Map<String, Node> nodeById = validateNodes(safeNodes, errors, warnings);
        Map<String, Double> directedEdges = validateEdges(safeEdges, nodeById.keySet(), errors, warnings);
        validateReversePairs(directedEdges, errors, warnings);
        validateConnectivity(nodeById, safeEdges, errors, warnings);

        return new ImportValidationResult(errors, warnings);
    }

    // ------------------------------------------------------------------
    // 节点校验：必填字段、坐标范围、重复景点（id / 名称）
    // ------------------------------------------------------------------
    private Map<String, Node> validateNodes(List<Node> nodes,
                                            List<ValidationIssue> errors,
                                            List<ValidationIssue> warnings) {
        Map<String, Node> byId = new LinkedHashMap<>();
        if (nodes.isEmpty()) {
            errors.add(ValidationIssue.error("NODE_LIST_EMPTY", "节点列表为空：未导入任何景点"));
            return byId;
        }

        Map<String, List<String>> idsByName = new LinkedHashMap<>();
        Map<String, List<String>> idsByCoord = new LinkedHashMap<>();

        int row = 0;
        for (Node n : nodes) {
            row++;
            if (n == null) {
                errors.add(ValidationIssue.error("NODE_NULL", "第 " + row + " 个节点：数据为空"));
                continue;
            }

            String id = trimToNull(n.getId());
            String label = id == null ? "第 " + row + " 个节点" : "景点[" + id + "]";

            if (id == null) {
                errors.add(ValidationIssue.error("NODE_ID_BLANK", "第 " + row + " 个节点：景点 id 不能为空"));
            } else if (byId.putIfAbsent(id, n) != null) {
                errors.add(ValidationIssue.error("DUPLICATE_NODE_ID",
                        "重复的景点 id：" + id + "（第 " + row + " 个节点与之前的定义重复）"));
            }

            String name = trimToNull(n.getName());
            if (name == null) {
                errors.add(ValidationIssue.error("NODE_NAME_BLANK", label + "：景点名称不能为空"));
            } else if (id != null) {
                idsByName.computeIfAbsent(name, k -> new ArrayList<>()).add(id);
            }

            double lat = n.getLat();
            double lng = n.getLng();
            if (Double.isNaN(lat) || lat < -90 || lat > 90) {
                errors.add(ValidationIssue.error("NODE_LAT_INVALID",
                        label + "：纬度 " + lat + " 超出合法范围 [-90, 90]"));
            }
            if (Double.isNaN(lng) || lng < -180 || lng > 180) {
                errors.add(ValidationIssue.error("NODE_LNG_INVALID",
                        label + "：经度 " + lng + " 超出合法范围 [-180, 180]"));
            }

            if (id != null && !Double.isNaN(lat) && !Double.isNaN(lng)) {
                idsByCoord.computeIfAbsent(lat + "," + lng, k -> new ArrayList<>()).add(id);
            }
        }

        for (Map.Entry<String, List<String>> e : idsByName.entrySet()) {
            if (e.getValue().size() > 1) {
                errors.add(ValidationIssue.error("DUPLICATE_NODE_NAME",
                        "重复景点：名称「" + e.getKey() + "」被多个景点使用（id: "
                                + String.join(", ", e.getValue()) + "）"));
            }
        }

        for (Map.Entry<String, List<String>> e : idsByCoord.entrySet()) {
            if (e.getValue().size() > 1) {
                warnings.add(ValidationIssue.warning("NODE_SAME_COORDS",
                        "多个景点坐标完全相同（" + e.getKey() + "）：" + String.join(", ", e.getValue())));
            }
        }

        return byId;
    }

    // ------------------------------------------------------------------
    // 边校验：端点存在、自环、负权重、重复边
    // ------------------------------------------------------------------
    private Map<String, Double> validateEdges(List<Edge> edges,
                                              Set<String> nodeIds,
                                              List<ValidationIssue> errors,
                                              List<ValidationIssue> warnings) {
        // 有向边 "from\0to" -> 权重（用于反向缺失与权重一致性检查）
        Map<String, Double> directed = new LinkedHashMap<>();

        int row = 0;
        for (Edge e : edges) {
            row++;
            if (e == null) {
                errors.add(ValidationIssue.error("EDGE_NULL", "第 " + row + " 条边：数据为空"));
                continue;
            }

            String from = trimToNull(e.getFromId());
            String to = trimToNull(e.getToId());
            if (from == null || to == null) {
                errors.add(ValidationIssue.error("EDGE_ENDPOINT_BLANK",
                        "第 " + row + " 条边：起点和终点 id 不能为空"));
                continue;
            }
            String label = "边[" + from + " -> " + to + "]";

            if (from.equals(to)) {
                errors.add(ValidationIssue.error("EDGE_SELF_LOOP",
                        label + "：起点与终点相同（自环），不允许"));
            }
            if (!nodeIds.contains(from)) {
                errors.add(ValidationIssue.error("EDGE_UNKNOWN_NODE",
                        label + "：起点 " + from + " 不在景点列表中"));
            }
            if (!nodeIds.contains(to)) {
                errors.add(ValidationIssue.error("EDGE_UNKNOWN_NODE",
                        label + "：终点 " + to + " 不在景点列表中"));
            }

            Double w = e.getDistanceMeters();
            if (w != null) {
                if (w.isNaN() || w.isInfinite()) {
                    errors.add(ValidationIssue.error("EDGE_WEIGHT_INVALID",
                            label + "：权重非法（" + w + "）"));
                } else if (w < 0) {
                    errors.add(ValidationIssue.error("EDGE_WEIGHT_NEGATIVE",
                            label + "：负权重（" + w + " 米），距离不能为负"));
                } else if (w == 0) {
                    warnings.add(ValidationIssue.warning("EDGE_WEIGHT_ZERO",
                            label + "：权重为 0，请确认是否为占位值"));
                }
            }

            String key = directedKey(from, to);
            if (directed.containsKey(key)) {
                errors.add(ValidationIssue.error("DUPLICATE_EDGE",
                        "重复声明的边：" + from + " -> " + to));
            } else {
                directed.put(key, w);
            }
        }
        return directed;
    }

    // ------------------------------------------------------------------
    // 同一连接反向缺失 / 双向权重不一致
    // ------------------------------------------------------------------
    private void validateReversePairs(Map<String, Double> directed,
                                      List<ValidationIssue> errors,
                                      List<ValidationIssue> warnings) {
        Set<String> checkedPairs = new HashSet<>();
        for (Map.Entry<String, Double> entry : directed.entrySet()) {
            String[] parts = entry.getKey().split("\0");
            String from = parts[0];
            String to = parts[1];
            if (from.equals(to)) {
                continue; // 自环已单独报错
            }
            String pairKey = from.compareTo(to) < 0 ? from + "\0" + to : to + "\0" + from;
            if (!checkedPairs.add(pairKey)) {
                continue; // 每个无向连接只检查一次
            }

            boolean hasForward = directed.containsKey(directedKey(from, to));
            boolean hasBackward = directed.containsKey(directedKey(to, from));
            if (hasForward && !hasBackward) {
                errors.add(ValidationIssue.error("EDGE_REVERSE_MISSING",
                        "边[" + from + " -> " + to + "]：同一连接反向缺失，请补充反向边["
                                + to + " -> " + from + "]"));
            } else if (hasBackward && !hasForward) {
                errors.add(ValidationIssue.error("EDGE_REVERSE_MISSING",
                        "边[" + to + " -> " + from + "]：同一连接反向缺失，请补充反向边["
                                + from + " -> " + to + "]"));
            } else {
                Double forward = directed.get(directedKey(from, to));
                Double backward = directed.get(directedKey(to, from));
                if (forward != null && backward != null && Math.abs(forward - backward) > 1e-6) {
                    warnings.add(ValidationIssue.warning("EDGE_REVERSE_WEIGHT_MISMATCH",
                            "边[" + from + " -> " + to + "] 与反向边权重不一致（"
                                    + forward + " 米 vs " + backward + " 米）"));
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // 图结构校验：孤立节点（错误）、图不连通（警告）
    // ------------------------------------------------------------------
    private void validateConnectivity(Map<String, Node> nodeById,
                                      List<Edge> edges,
                                      List<ValidationIssue> errors,
                                      List<ValidationIssue> warnings) {
        if (nodeById.size() <= 1) {
            return;
        }
        Set<String> nodeIds = nodeById.keySet();

        // 仅统计两个端点都真实存在的边（无向）
        Map<String, Set<String>> adj = new HashMap<>();
        for (String id : nodeIds) {
            adj.put(id, new HashSet<>());
        }
        for (Edge e : edges) {
            if (e == null) {
                continue;
            }
            String from = trimToNull(e.getFromId());
            String to = trimToNull(e.getToId());
            if (from == null || to == null || from.equals(to)) {
                continue;
            }
            if (!nodeIds.contains(from) || !nodeIds.contains(to)) {
                continue;
            }
            adj.get(from).add(to);
            adj.get(to).add(from);
        }

        for (Map.Entry<String, Node> entry : nodeById.entrySet()) {
            String id = entry.getKey();
            if (adj.get(id).isEmpty()) {
                String name = trimToNull(entry.getValue().getName());
                errors.add(ValidationIssue.error("ISOLATED_NODE",
                        "孤立节点：景点[" + id + "]" + (name == null ? "" : "（" + name + "）")
                                + "没有任何边与其他景点相连"));
            }
        }

        // 连通性：从任一节点出发遍历，统计无法到达且非孤立的节点（独立子图）
        Set<String> visited = new HashSet<>();
        ArrayDeque<String> stack = new ArrayDeque<>();
        String start = nodeIds.iterator().next();
        visited.add(start);
        stack.push(start);
        while (!stack.isEmpty()) {
            String cur = stack.pop();
            for (String nb : adj.get(cur)) {
                if (visited.add(nb)) {
                    stack.push(nb);
                }
            }
        }
        if (visited.size() < nodeIds.size()) {
            List<String> detached = new ArrayList<>();
            for (String id : nodeIds) {
                if (!visited.contains(id) && !adj.get(id).isEmpty()) {
                    detached.add(id);
                }
            }
            if (!detached.isEmpty()) {
                warnings.add(ValidationIssue.warning("GRAPH_DISCONNECTED",
                        "图不连通：景点 " + abbreviate(detached)
                                + " 与主网络不连通，导入后系统将自动补充连接"));
            }
        }
    }

    private static String abbreviate(List<String> ids) {
        if (ids.size() <= MAX_LISTED_IDS) {
            return String.join(", ", ids);
        }
        return String.join(", ", ids.subList(0, MAX_LISTED_IDS)) + " 等 " + ids.size() + " 个";
    }

    private static String directedKey(String from, String to) {
        return from + "\0" + to;
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}

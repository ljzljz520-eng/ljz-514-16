package com.cqu.service;

import com.cqu.model.Edge;
import com.cqu.model.Node;
import com.cqu.model.ValidationIssue;
import com.cqu.model.ValidationResult;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 管理员导入图数据（节点 + 边）前的服务层校验器。
 *
 * 错误（阻断导入）：
 *   - 重复景点：id 重复 / 名称重复
 *   - 节点必填字段缺失、经纬度非法
 *   - 边端点缺失、自环、引用不存在的节点
 *   - 负权重 / 非法权重（NaN）
 *   - 同一连接反向缺失（图按无向处理，需成对导入）
 *   - 反向两边距离不一致
 *   - 孤立节点（没有任何边与其连接）
 * 警告（不阻断导入）：
 *   - 边未填写距离（导入时按两点直线距离估算）
 *   - 同方向边重复出现
 *   - 图不连通（导入时将自动补边保证连通）
 *
 * 纯内存计算，不依赖数据库与 HTTP 层，方便单元测试。
 */
public class GraphImportValidator {

    private static final double WEIGHT_EPSILON_METERS = 0.01;

    public ValidationResult validate(List<Node> nodes, List<Edge> edges) {
        List<ValidationIssue> issues = new ArrayList<>();
        List<Node> safeNodes = nodes == null ? List.of() : nodes;
        List<Edge> safeEdges = edges == null ? List.of() : edges;

        Map<String, Node> nodesById = validateNodes(safeNodes, issues);
        Map<String, Edge> directedEdges = validateEdges(safeEdges, nodesById, issues);
        validateReverseAndSymmetry(directedEdges, issues);
        validateTopology(nodesById, safeEdges, issues);
        return new ValidationResult(issues);
    }

    // ---------- 节点校验 ----------

    private Map<String, Node> validateNodes(List<Node> nodes, List<ValidationIssue> issues) {
        Map<String, Node> nodesById = new LinkedHashMap<>();
        if (nodes.isEmpty()) {
            issues.add(ValidationIssue.error(ValidationIssue.Code.EMPTY_NODE_LIST,
                    "节点列表为空：导入数据必须至少包含一个景点节点"));
            return nodesById;
        }

        Map<String, Integer> firstRowById = new HashMap<>();
        Map<String, String> idByName = new HashMap<>();

        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            int row = i + 1;
            if (node == null) {
                issues.add(ValidationIssue.error(ValidationIssue.Code.NULL_NODE,
                        "第 " + row + " 个节点为空"));
                continue;
            }

            String id = trimToNull(node.getId());
            String name = trimToNull(node.getName());
            String ref = id != null ? "'" + id + "'" : "第 " + row + " 行";

            if (id == null) {
                issues.add(ValidationIssue.error(ValidationIssue.Code.BLANK_NODE_ID,
                        "第 " + row + " 个节点缺少 id"));
            }
            if (name == null) {
                issues.add(ValidationIssue.error(ValidationIssue.Code.BLANK_NODE_NAME,
                        "节点 " + ref + " 缺少名称"));
            }
            if (Double.isNaN(node.getLat()) || Double.isNaN(node.getLng())
                    || node.getLat() < -90 || node.getLat() > 90
                    || node.getLng() < -180 || node.getLng() > 180) {
                issues.add(ValidationIssue.error(ValidationIssue.Code.INVALID_COORDINATE,
                        "节点 " + ref + " 的经纬度非法：(" + node.getLat() + ", " + node.getLng()
                                + ")，纬度需在 [-90, 90]，经度需在 [-180, 180]"));
            }

            if (id != null) {
                Integer firstRow = firstRowById.get(id);
                if (firstRow != null) {
                    issues.add(ValidationIssue.error(ValidationIssue.Code.DUPLICATE_NODE_ID,
                            "重复景点：id '" + id + "' 在第 " + firstRow + "、" + row + " 行重复出现"));
                } else {
                    firstRowById.put(id, row);
                    nodesById.put(id, node);
                }
            }
            if (name != null) {
                String existingRef = idByName.get(name);
                String currentRef = id != null ? "'" + id + "'" : "第 " + row + " 行";
                if (existingRef != null && !existingRef.equals(currentRef)) {
                    issues.add(ValidationIssue.error(ValidationIssue.Code.DUPLICATE_NODE_NAME,
                            "重复景点：名称 '" + name + "' 同时对应 " + existingRef + " 和 " + currentRef));
                } else if (existingRef == null) {
                    idByName.put(name, currentRef);
                }
            }
        }
        return nodesById;
    }

    // ---------- 边校验 ----------

    private Map<String, Edge> validateEdges(List<Edge> edges, Map<String, Node> nodesById,
                                            List<ValidationIssue> issues) {
        Map<String, Edge> directedEdges = new LinkedHashMap<>();
        Map<String, Integer> firstRowByKey = new HashMap<>();

        for (int i = 0; i < edges.size(); i++) {
            Edge edge = edges.get(i);
            int row = i + 1;
            if (edge == null) {
                issues.add(ValidationIssue.error(ValidationIssue.Code.NULL_EDGE,
                        "第 " + row + " 条边为空"));
                continue;
            }

            String from = trimToNull(edge.getFromId());
            String to = trimToNull(edge.getToId());
            if (from == null || to == null) {
                issues.add(ValidationIssue.error(ValidationIssue.Code.BLANK_EDGE_ENDPOINT,
                        "第 " + row + " 条边缺少起点或终点"));
                continue;
            }
            String label = from + " → " + to;

            if (from.equals(to)) {
                issues.add(ValidationIssue.error(ValidationIssue.Code.SELF_LOOP,
                        "边 " + label + " 是自环：起点与终点相同"));
                continue;
            }
            if (!nodesById.containsKey(from) || !nodesById.containsKey(to)) {
                List<String> missing = new ArrayList<>();
                if (!nodesById.containsKey(from)) {
                    missing.add("'" + from + "'");
                }
                if (!nodesById.containsKey(to)) {
                    missing.add("'" + to + "'");
                }
                issues.add(ValidationIssue.error(ValidationIssue.Code.UNKNOWN_NODE,
                        "边 " + label + " 引用了不存在的节点：" + String.join("、", missing)));
                continue;
            }

            Double weight = edge.getDistanceMeters();
            if (weight != null) {
                if (Double.isNaN(weight)) {
                    issues.add(ValidationIssue.error(ValidationIssue.Code.INVALID_WEIGHT,
                            "边 " + label + " 的距离非法（NaN）"));
                    continue;
                }
                if (weight < 0) {
                    issues.add(ValidationIssue.error(ValidationIssue.Code.NEGATIVE_WEIGHT,
                            "边 " + label + " 的权重为负数（" + weight + " 米），距离不能为负"));
                    continue;
                }
            } else {
                issues.add(ValidationIssue.warning(ValidationIssue.Code.MISSING_DISTANCE,
                        "边 " + label + " 未填写距离，导入时将按两点间直线距离估算"));
            }

            String key = from + "->" + to;
            Integer firstRow = firstRowByKey.get(key);
            if (firstRow != null) {
                issues.add(ValidationIssue.warning(ValidationIssue.Code.DUPLICATE_EDGE,
                        "边 " + label + " 重复出现（第 " + firstRow + "、" + row + " 条），仅第一条生效"));
                continue;
            }
            firstRowByKey.put(key, row);
            directedEdges.put(key, edge);
        }
        return directedEdges;
    }

    // ---------- 反向缺失 / 距离不一致 ----------

    private void validateReverseAndSymmetry(Map<String, Edge> directedEdges,
                                            List<ValidationIssue> issues) {
        for (Edge edge : directedEdges.values()) {
            String from = edge.getFromId().trim();
            String to = edge.getToId().trim();
            Edge reverse = directedEdges.get(to + "->" + from);
            if (reverse == null) {
                issues.add(ValidationIssue.error(ValidationIssue.Code.MISSING_REVERSE_EDGE,
                        "连接 " + from + " → " + to + " 缺少反向边 " + to + " → " + from
                                + "：图按无向处理，请成对导入两个方向的边"));
                continue;
            }
            // 同一对连接只检查一次距离一致性
            if (from.compareTo(to) < 0) {
                Double forward = edge.getDistanceMeters();
                Double backward = reverse.getDistanceMeters();
                if (forward != null && backward != null
                        && Math.abs(forward - backward) > WEIGHT_EPSILON_METERS) {
                    issues.add(ValidationIssue.error(ValidationIssue.Code.ASYMMETRIC_WEIGHT,
                            "连接 " + from + " → " + to + "（" + forward + " 米）与反向边 "
                                    + to + " → " + from + "（" + backward + " 米）的距离不一致，请统一"));
                }
            }
        }
    }

    // ---------- 孤立节点 / 连通性 ----------

    private void validateTopology(Map<String, Node> nodesById, List<Edge> edges,
                                  List<ValidationIssue> issues) {
        if (nodesById.isEmpty()) {
            return;
        }
        if (edges.isEmpty()) {
            issues.add(ValidationIssue.error(ValidationIssue.Code.EMPTY_EDGE_LIST,
                    "边列表为空：所有节点都是孤立节点，无法规划路径"));
            return;
        }

        Map<String, Set<String>> adjacency = new HashMap<>();
        for (String id : nodesById.keySet()) {
            adjacency.put(id, new HashSet<>());
        }
        for (Edge edge : edges) {
            if (edge == null) {
                continue;
            }
            String from = trimToNull(edge.getFromId());
            String to = trimToNull(edge.getToId());
            if (from == null || to == null || from.equals(to)) {
                continue;
            }
            if (!nodesById.containsKey(from) || !nodesById.containsKey(to)) {
                continue;
            }
            adjacency.get(from).add(to);
            adjacency.get(to).add(from);
        }

        boolean hasIsolated = false;
        for (Map.Entry<String, Node> entry : nodesById.entrySet()) {
            String id = entry.getKey();
            if (adjacency.get(id).isEmpty()) {
                hasIsolated = true;
                String name = trimToNull(entry.getValue().getName());
                issues.add(ValidationIssue.error(ValidationIssue.Code.ISOLATED_NODE,
                        "孤立节点：景点 '" + id + "'" + (name != null ? "（" + name + "）" : "")
                                + " 没有任何边与其连接，将无法参与路径规划"));
            }
        }
        if (hasIsolated) {
            return;
        }

        int components = countComponents(adjacency);
        if (components > 1) {
            issues.add(ValidationIssue.warning(ValidationIssue.Code.DISCONNECTED_GRAPH,
                    "图数据不连通：共 " + components + " 个连通分量，导入时将自动补充连接边；建议显式补全边数据"));
        }
    }

    private static int countComponents(Map<String, Set<String>> adjacency) {
        Set<String> visited = new HashSet<>();
        int components = 0;
        for (String id : adjacency.keySet()) {
            if (visited.contains(id)) {
                continue;
            }
            components++;
            Deque<String> stack = new ArrayDeque<>();
            stack.push(id);
            while (!stack.isEmpty()) {
                String current = stack.pop();
                if (!visited.add(current)) {
                    continue;
                }
                for (String neighbor : adjacency.get(current)) {
                    stack.push(neighbor);
                }
            }
        }
        return components;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

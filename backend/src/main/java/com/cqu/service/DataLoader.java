package com.cqu.service;

import com.cqu.model.Node;
import com.cqu.model.Edge;
import com.opencsv.CSVReader;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DataLoader {
    private static final Logger logger = Logger.getLogger(DataLoader.class.getName());

    public List<Node> loadNodeList() {
        InputStream input = DataLoader.class.getClassLoader().getResourceAsStream("nodes.csv");
        if (input == null) {
            throw new IllegalStateException("未找到 nodes.csv（resources）");
        }

        List<Node> nodes = new ArrayList<>();
        try (CSVReader reader = new CSVReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String[] header = reader.readNext();
            if (header == null) {
                throw new IllegalStateException("nodes.csv 为空");
            }

            Map<String, Integer> index = new HashMap<>();
            for (int i = 0; i < header.length; i++) {
                index.put(header[i].trim(), i);
            }

            requireColumns(index, List.of("id", "name", "lat", "lng"));

            String[] row;
            while ((row = reader.readNext()) != null) {
                if (row.length == 0) {
                    continue;
                }

                String id = readRequiredColOrNull(row, index, "id");
                if (id == null || id.isBlank()) {
                    continue;
                }

                String name = readRequiredColOrNull(row, index, "name");
                String latRaw = readRequiredColOrNull(row, index, "lat");
                String lngRaw = readRequiredColOrNull(row, index, "lng");
                if (name == null || latRaw == null || lngRaw == null) {
                    logger.log(Level.WARNING, "Skip invalid nodes.csv row: missing required columns for id=" + id);
                    continue;
                }

                double lat;
                double lng;
                try {
                    lat = Double.parseDouble(latRaw);
                    lng = Double.parseDouble(lngRaw);
                } catch (NumberFormatException e) {
                    logger.log(Level.WARNING, "Skip invalid nodes.csv row: invalid lat/lng for id=" + id);
                    continue;
                }

                String type = readColOrNull(row, index, "type");
                String desc = readColOrNull(row, index, "desc");
                nodes.add(new Node(id.trim(), name == null ? "" : name.trim(), lat, lng, type, desc));
            }
        } catch (Exception e) {
            throw new IllegalStateException("读取 nodes.csv 失败", e);
        }

        if (nodes.isEmpty()) {
            throw new IllegalStateException("nodes.csv 未加载到任何节点");
        }
        return nodes;
    }

    public Map<String, Node> loadNodes() {
        List<Node> nodes = loadNodeList();
        Map<String, Node> map = new HashMap<>();
        for (Node n : nodes) {
            map.put(n.getId(), n);
        }
        return map;
    }

    public List<Edge> loadEdgeListOrEmpty() {
        InputStream input = DataLoader.class.getClassLoader().getResourceAsStream("edges.csv");
        if (input == null) {
            return List.of();
        }

        try (CSVReader reader = new CSVReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String[] header = reader.readNext();
            if (header == null) {
                return List.of();
            }

            Map<String, Integer> index = new HashMap<>();
            for (int i = 0; i < header.length; i++) {
                index.put(header[i].trim().toLowerCase(), i);
            }

            requireColumns(index, List.of("from", "to"));

            List<Edge> edges = new ArrayList<>();
            String[] row;
            while ((row = reader.readNext()) != null) {
                String from = readRequiredColOrNull(row, index, "from");
                String to = readRequiredColOrNull(row, index, "to");
                if (from == null || from.isBlank() || to == null || to.isBlank()) {
                    continue;
                }

                Double dist = readDoubleOrNull(row, index, "distance_meters");
                if (dist == null) {
                    dist = readDoubleOrNull(row, index, "distanceMeters");
                }
                if (dist == null) {
                    dist = readDoubleOrNull(row, index, "weight_meters");
                }
                if (dist == null) {
                    dist = readDoubleOrNull(row, index, "weightMeters");
                }

                edges.add(new Edge(from.trim(), to.trim(), dist));
            }

            return edges;
        } catch (Exception e) {
            throw new IllegalStateException("读取 edges.csv 失败", e);
        }
    }

    private static void requireColumns(Map<String, Integer> index, List<String> required) {
        for (String col : required) {
            if (!index.containsKey(col)) {
                throw new IllegalStateException("nodes.csv 缺少必填列：" + col);
            }
        }
    }

    private static String readRequiredColOrNull(String[] row, Map<String, Integer> index, String key) {
        Integer i = index.get(key);
        if (i == null || i < 0 || i >= row.length) {
            return null;
        }
        String v = row[i];
        return v == null ? null : v.trim();
    }

    private static String readColOrNull(String[] row, Map<String, Integer> index, String key) {
        Integer i = index.get(key);
        if (i == null || i < 0 || i >= row.length) {
            return null;
        }
        String v = row[i];
        return v == null ? null : v.trim();
    }

    private static Double readDoubleOrNull(String[] row, Map<String, Integer> index, String key) {
        String raw = readColOrNull(row, index, key);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

package com.cqu.handler;

import com.cqu.model.ImportRequest;
import com.cqu.model.ImportValidationResult;
import com.cqu.model.PathResult;
import com.cqu.service.AdminImportService;
import com.cqu.service.GraphService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class RequestHandler {
    private final GraphService graphService;
    private final AdminImportService adminImportService;
    private final ObjectMapper mapper;

    public RequestHandler(GraphService graphService, AdminImportService adminImportService) {
        this.graphService = graphService;
        this.adminImportService = adminImportService;
        this.mapper = new ObjectMapper();
    }

    public void handleHealth(HttpExchange exchange) throws IOException {
        if (isPreflight(exchange)) {
            respondNoContent(exchange);
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("status", "ok");
        payload.put("time", Instant.now().toString());
        writeJson(exchange, 200, payload);
    }

    public void handleNodes(HttpExchange exchange) throws IOException {
        if (isPreflight(exchange)) {
            respondNoContent(exchange);
            return;
        }
        writeJson(exchange, 200, graphService.listNodes());
    }

    public void handlePath(HttpExchange exchange) throws IOException {
        if (isPreflight(exchange)) {
            respondNoContent(exchange);
            return;
        }
        try {
            Map<String, String> q = parseQuery(exchange.getRequestURI().getRawQuery());
            String from = q.get("from");
            String to = q.get("to");
            if (from == null || to == null || from.isBlank() || to.isBlank()) {
                writeJson(exchange, 400, Map.of("error", "缺少必填参数：from、to"));
                return;
            }
            PathResult result = graphService.shortestPath(from, to);
            writeJson(exchange, 200, result);
        } catch (IllegalArgumentException e) {
            writeJson(exchange, 400, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            writeJson(exchange, 500, Map.of("error", "服务器内部错误"));
        }
    }

    /**
     * 管理员导入预检：POST /api/admin/validate
     * 只校验不修改数据，始终返回 200 + 校验结果（valid/issues）。
     */
    public void handleAdminValidate(HttpExchange exchange) throws IOException {
        if (isPreflight(exchange)) {
            respondNoContent(exchange);
            return;
        }
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            writeJson(exchange, 405, Map.of("error", "仅支持 POST"));
            return;
        }
        try {
            ImportRequest req = parseImportRequest(exchange);
            if (req == null) {
                return;
            }
            ImportValidationResult result = adminImportService.validate(req.getNodes(), req.getEdges());
            writeJson(exchange, 200, result);
        } catch (Exception e) {
            writeJson(exchange, 500, Map.of("error", "服务器内部错误"));
        }
    }

    /**
     * 管理员导入：POST /api/admin/import
     * 校验失败返回 400 + 可读错误列表，图数据保持不变；校验通过才更新图数据。
     */
    public void handleAdminImport(HttpExchange exchange) throws IOException {
        if (isPreflight(exchange)) {
            respondNoContent(exchange);
            return;
        }
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            writeJson(exchange, 405, Map.of("error", "仅支持 POST"));
            return;
        }
        try {
            ImportRequest req = parseImportRequest(exchange);
            if (req == null) {
                return;
            }
            ImportValidationResult result = adminImportService.importGraph(req.getNodes(), req.getEdges());
            if (!result.isValid()) {
                Map<String, Object> payload = new HashMap<>();
                payload.put("error", "校验失败，图数据未更新");
                payload.put("valid", false);
                payload.put("errorCount", result.getErrorCount());
                payload.put("warningCount", result.getWarningCount());
                payload.put("issues", result.getIssues());
                writeJson(exchange, 400, payload);
                return;
            }
            Map<String, Object> payload = new HashMap<>();
            payload.put("message", "导入成功，图数据已更新");
            payload.put("valid", true);
            payload.put("nodeCount", req.getNodes().size());
            payload.put("edgeCount", req.getEdges() == null ? 0 : req.getEdges().size());
            payload.put("warningCount", result.getWarningCount());
            payload.put("warnings", result.getWarnings());
            writeJson(exchange, 200, payload);
        } catch (Exception e) {
            writeJson(exchange, 500, Map.of("error", "服务器内部错误"));
        }
    }

    /** 解析导入请求体；非法时直接写出 400 响应并返回 null。 */
    private ImportRequest parseImportRequest(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (body.isBlank()) {
            writeJson(exchange, 400, Map.of("error", "请求体为空，需要 JSON：{ nodes: [...], edges: [...] }"));
            return null;
        }
        try {
            return mapper.readValue(body, ImportRequest.class);
        } catch (Exception e) {
            writeJson(exchange, 400, Map.of("error", "请求体不是合法的 JSON 或字段类型不正确"));
            return null;
        }
    }

    private void writeJson(HttpExchange exchange, int status, Object payload) throws IOException {
        byte[] bytes = mapper.writeValueAsBytes(payload);
        Headers h = exchange.getResponseHeaders();
        applyCors(exchange);
        h.set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> map = new HashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return map;
        }
        String[] pairs = rawQuery.split("&");
        for (String p : pairs) {
            int idx = p.indexOf('=');
            if (idx <= 0) {
                continue;
            }
            String k = URLDecoder.decode(p.substring(0, idx), StandardCharsets.UTF_8);
            String v = URLDecoder.decode(p.substring(idx + 1), StandardCharsets.UTF_8);
            map.put(k, v);
        }
        return map;
    }

    private static boolean isPreflight(HttpExchange exchange) {
        return "OPTIONS".equalsIgnoreCase(exchange.getRequestMethod());
    }

    private static void respondNoContent(HttpExchange exchange) throws IOException {
        applyCors(exchange);
        exchange.sendResponseHeaders(204, -1);
        exchange.close();
    }

    private static void applyCors(HttpExchange exchange) {
        Headers h = exchange.getResponseHeaders();
        h.set("Access-Control-Allow-Origin", "*");
        h.set("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
        h.set("Access-Control-Allow-Headers", "Content-Type,Authorization");
        h.set("Access-Control-Max-Age", "86400");
    }
}

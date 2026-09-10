package com.cqu.handler;

import com.cqu.model.ImportRequest;
import com.cqu.model.ImportResult;
import com.cqu.model.PathResult;
import com.cqu.model.ValidationResult;
import com.cqu.service.GraphManager;
import com.fasterxml.jackson.databind.DeserializationFeature;
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
    private final GraphManager graphManager;
    private final ObjectMapper mapper;

    public RequestHandler(GraphManager graphManager) {
        this.graphManager = graphManager;
        this.mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
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
        writeJson(exchange, 200, graphManager.current().listNodes());
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
            PathResult result = graphManager.current().shortestPath(from, to);
            writeJson(exchange, 200, result);
        } catch (IllegalArgumentException e) {
            writeJson(exchange, 400, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            writeJson(exchange, 500, Map.of("error", "服务器内部错误"));
        }
    }

    /**
     * POST /api/admin/import/validate —— 仅校验导入数据，返回可读错误/警告列表，不修改图数据。
     */
    public void handleAdminImportValidate(HttpExchange exchange) throws IOException {
        if (isPreflight(exchange)) {
            respondNoContent(exchange);
            return;
        }
        if (!requirePost(exchange)) {
            return;
        }
        ImportRequest request = readImportRequest(exchange);
        if (request == null) {
            return;
        }
        ValidationResult result = graphManager.validateImport(request.getNodes(), request.getEdges());
        writeJson(exchange, 200, result);
    }

    /**
     * POST /api/admin/import —— 校验通过后整体替换图数据；校验失败返回 400 与错误列表，原图不变。
     */
    public void handleAdminImport(HttpExchange exchange) throws IOException {
        if (isPreflight(exchange)) {
            respondNoContent(exchange);
            return;
        }
        if (!requirePost(exchange)) {
            return;
        }
        ImportRequest request = readImportRequest(exchange);
        if (request == null) {
            return;
        }
        ImportResult result = graphManager.importGraph(request.getNodes(), request.getEdges());
        writeJson(exchange, result.isSuccess() ? 200 : 400, result);
    }

    private ImportRequest readImportRequest(HttpExchange exchange) throws IOException {
        try {
            ImportRequest request = mapper.readValue(exchange.getRequestBody(), ImportRequest.class);
            return request == null ? new ImportRequest() : request;
        } catch (IOException e) {
            writeJson(exchange, 400, Map.of(
                    "error", "请求体不是合法的 JSON 或字段类型不正确",
                    "expect", "{\"nodes\": [{\"id\",\"name\",\"lat\",\"lng\",\"type\",\"desc\"}], "
                            + "\"edges\": [{\"fromId\",\"toId\",\"distanceMeters\"}]}"));
            return null;
        }
    }

    private boolean requirePost(HttpExchange exchange) throws IOException {
        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            return true;
        }
        writeJson(exchange, 405, Map.of("error", "仅支持 POST 请求"));
        return false;
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

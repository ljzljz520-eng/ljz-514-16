package com.cqu.handler;

import com.cqu.model.ImportRequest;
import com.cqu.model.ImportResult;
import com.cqu.model.ImportValidationResult;
import com.cqu.model.PathResult;
import com.cqu.service.GraphImportService;
import com.cqu.service.GraphRegistry;
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
    private final GraphRegistry graphRegistry;
    private final GraphImportService importService;
    private final ObjectMapper mapper;

    public RequestHandler(GraphRegistry graphRegistry, GraphImportService importService) {
        this.graphRegistry = graphRegistry;
        this.importService = importService;
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
        writeJson(exchange, 200, graphRegistry.current().listNodes());
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
            PathResult result = graphRegistry.current().shortestPath(from, to);
            writeJson(exchange, 200, result);
        } catch (IllegalArgumentException e) {
            writeJson(exchange, 400, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            writeJson(exchange, 500, Map.of("error", "服务器内部错误"));
        }
    }

    /**
     * 管理员：只校验导入数据，不更新图。始终返回 200 + 校验结果。
     */
    public void handleAdminImportValidate(HttpExchange exchange) throws IOException {
        if (isPreflight(exchange)) {
            respondNoContent(exchange);
            return;
        }
        if (!requirePost(exchange)) {
            return;
        }
        ImportRequest req = readImportRequest(exchange);
        if (req == null) {
            return;
        }
        ImportValidationResult result = importService.validate(req.getNodes(), req.getEdges());
        writeJson(exchange, 200, result);
    }

    /**
     * 管理员：校验通过后才会更新图数据；校验失败返回 400 + 可读错误列表，图保持不变。
     */
    public void handleAdminImport(HttpExchange exchange) throws IOException {
        if (isPreflight(exchange)) {
            respondNoContent(exchange);
            return;
        }
        if (!requirePost(exchange)) {
            return;
        }
        ImportRequest req = readImportRequest(exchange);
        if (req == null) {
            return;
        }
        try {
            ImportResult result = importService.importGraph(req.getNodes(), req.getEdges());
            writeJson(exchange, result.isSuccess() ? 200 : 400, result);
        } catch (Exception e) {
            writeJson(exchange, 500, Map.of("error", "导入失败：" + e.getMessage()));
        }
    }

    private ImportRequest readImportRequest(HttpExchange exchange) throws IOException {
        try {
            return mapper.readValue(exchange.getRequestBody(), ImportRequest.class);
        } catch (IOException e) {
            writeJson(exchange, 400, Map.of("error", "请求体不是合法的 JSON"));
            return null;
        }
    }

    private boolean requirePost(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            writeJson(exchange, 405, Map.of("error", "仅支持 POST 请求"));
            return false;
        }
        return true;
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

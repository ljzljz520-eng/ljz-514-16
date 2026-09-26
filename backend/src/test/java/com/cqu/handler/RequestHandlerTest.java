package com.cqu.handler;

import com.cqu.service.GraphImportService;
import com.cqu.service.GraphImportValidator;
import com.cqu.service.GraphRegistry;
import com.cqu.service.GraphService;
import com.cqu.model.Node;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 管理端导入接口的端到端测试：内存图 + 真实 HTTP，不依赖数据库。
 */
public class RequestHandlerTest {
    private HttpServer server;
    private String base;
    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    void setUp() throws Exception {
        GraphService initial = new GraphService(Map.of(
                "X", new Node("X", "旧景点X", 29.0, 106.0, "t", ""),
                "Y", new Node("Y", "旧景点Y", 29.001, 106.001, "t", "")));
        GraphRegistry registry = new GraphRegistry(initial);
        GraphImportService importService = new GraphImportService(new GraphImportValidator(), null, registry);
        RequestHandler handler = new RequestHandler(registry, importService);

        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/nodes", handler::handleNodes);
        server.createContext("/api/admin/import/validate", handler::handleAdminImportValidate);
        server.createContext("/api/admin/import", handler::handleAdminImport);
        server.start();
        base = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private HttpResponse<String> post(String path, String json) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(base + path))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .header("Content-Type", "application/json")
                .build();
        return client.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(base + path)).GET().build();
        return client.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private static final String INVALID_PAYLOAD =
            "{\n" +
            "  \"nodes\": [\n" +
            "    {\"id\": \"A\", \"name\": \"解放碑\", \"lat\": 29.56301, \"lng\": 106.57577},\n" +
            "    {\"id\": \"B\", \"name\": \"洪崖洞\", \"lat\": 29.56470, \"lng\": 106.58169},\n" +
            "    {\"id\": \"C\", \"name\": \"朝天门\", \"lat\": 29.56336, \"lng\": 106.58713}\n" +
            "  ],\n" +
            "  \"edges\": [\n" +
            "    {\"fromId\": \"A\", \"toId\": \"B\", \"distanceMeters\": -10}\n" +
            "  ]\n" +
            "}\n";

    private static final String VALID_PAYLOAD =
            "{\n" +
            "  \"nodes\": [\n" +
            "    {\"id\": \"A\", \"name\": \"解放碑\", \"lat\": 29.56301, \"lng\": 106.57577},\n" +
            "    {\"id\": \"B\", \"name\": \"洪崖洞\", \"lat\": 29.56470, \"lng\": 106.58169}\n" +
            "  ],\n" +
            "  \"edges\": [\n" +
            "    {\"from\": \"A\", \"to\": \"B\", \"distance_meters\": 800},\n" +
            "    {\"from\": \"B\", \"to\": \"A\", \"distance_meters\": 800}\n" +
            "  ]\n" +
            "}\n";

    @Test
    void validateEndpointReturnsReadableErrorList() throws Exception {
        HttpResponse<String> res = post("/api/admin/import/validate", INVALID_PAYLOAD);
        assertEquals(200, res.statusCode());
        String body = res.body();
        assertTrue(body.contains("\"valid\":false"), body);
        assertTrue(body.contains("EDGE_WEIGHT_NEGATIVE"), body);
        assertTrue(body.contains("EDGE_REVERSE_MISSING"), body);
        assertTrue(body.contains("ISOLATED_NODE"), body);
        assertTrue(body.contains("负权重"), body);
    }

    @Test
    void importRejectsInvalidDataAndKeepsGraph() throws Exception {
        HttpResponse<String> res = post("/api/admin/import", INVALID_PAYLOAD);
        assertEquals(400, res.statusCode());
        assertTrue(res.body().contains("\"success\":false"), res.body());

        // 图数据未被污染
        HttpResponse<String> nodes = get("/api/nodes");
        assertTrue(nodes.body().contains("旧景点X"), nodes.body());
        assertFalse(nodes.body().contains("解放碑"), nodes.body());
    }

    @Test
    void importAppliesValidData() throws Exception {
        HttpResponse<String> res = post("/api/admin/import", VALID_PAYLOAD);
        assertEquals(200, res.statusCode(), res.body());
        assertTrue(res.body().contains("\"success\":true"), res.body());
        assertTrue(res.body().contains("\"nodeCount\":2"), res.body());

        // 新图生效（同时验证 from/to/distance_meters 别名解析）
        HttpResponse<String> nodes = get("/api/nodes");
        assertTrue(nodes.body().contains("解放碑"), nodes.body());
        assertFalse(nodes.body().contains("旧景点X"), nodes.body());
    }

    @Test
    void malformedJsonRejected() throws Exception {
        HttpResponse<String> res = post("/api/admin/import", "{not json");
        assertEquals(400, res.statusCode());
        assertTrue(res.body().contains("JSON"), res.body());
    }

    @Test
    void getMethodNotAllowedOnImport() throws Exception {
        HttpResponse<String> res = get("/api/admin/import");
        assertEquals(405, res.statusCode());
    }
}

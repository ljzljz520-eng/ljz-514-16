package com.cqu;

import com.cqu.handler.RequestHandler;
import com.cqu.service.DataLoader;
import com.cqu.service.Db;
import com.cqu.service.GraphService;
import com.cqu.service.NodeRepository;
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main {
    private static final Logger logger = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));

        Db db = new Db();
        NodeRepository repo = new NodeRepository(db.emf());
        DataLoader dataLoader = new DataLoader();

        if (!repo.hasAnyNodes()) {
            repo.saveAll(dataLoader.loadNodeList());
            logger.log(Level.INFO, "Seeded nodes into database from nodes.csv");
        }

        GraphService graphService = new GraphService(repo.findAllAsMap(), dataLoader.loadEdgeListOrEmpty());
        RequestHandler handler = new RequestHandler(graphService);

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/health", handler::handleHealth);
        server.createContext("/api/nodes", handler::handleNodes);
        server.createContext("/api/path", handler::handlePath);
        server.setExecutor(Executors.newFixedThreadPool(Math.max(4, Runtime.getRuntime().availableProcessors())));
        server.start();
        logger.log(Level.INFO, "Backend started on port " + port);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                server.stop(0);
            } catch (Exception ignored) {
            }
            try {
                db.close();
            } catch (Exception ignored) {
            }
        }));
    }
}

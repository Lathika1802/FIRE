package sudar;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Small web server (JDK built-in HTTP server, no extra libraries) for the
 * frontend and REST API.
 *   GET  /api/state      whole data set + dispatch results
 *   POST /api/incident   {"type":"..","location":"ERD","severity":4,"needs":["FIRE"]}
 *   POST /api/road       {"from":"ERD","to":"CHD","blocked":true}
 */
public class ApiServer {
    private final AppState app;
    private final int port;
    private final Path webRoot;
    private HttpServer server;

    public ApiServer(AppState app, int port) { this(app, port, Paths.get("web")); }
    public ApiServer(AppState app, int port, Path webRoot) {
        this.app = app;
        this.port = port;
        this.webRoot = webRoot.toAbsolutePath().normalize();
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/api/state", ex -> handle(ex, "GET", () -> app.snapshot()));
        server.createContext("/api/incident", ex -> handle(ex, "POST", () -> {
            Map<String, Object> b = body(ex);
            Set<String> needs = new LinkedHashSet<>();
            for (Object n : (List<?>) b.getOrDefault("needs", Collections.emptyList())) needs.add(String.valueOf(n).toUpperCase());
            String loc = String.valueOf(b.get("location")); String type = String.valueOf(b.getOrDefault("type", "")).trim();
            if (type.isEmpty() || needs.isEmpty() || app.data().location(loc) == null) throw new IllegalArgumentException("type, location and needs are required");
            int sev = ((Number) b.getOrDefault("severity", 3.0)).intValue();
            if (sev < 1 || sev > 5) throw new IllegalArgumentException("severity must be 1-5");
            Incident inc = app.report(type, loc, sev, needs);
            String err = app.save();
            Map<String, Object> out = app.snapshot(); out.put("created", inc.getId()); if (err != null) out.put("warning", err);
            return out;
        }));
        server.createContext("/api/road", ex -> handle(ex, "POST", () -> {
            Map<String, Object> b = body(ex);
            if (!app.setRoad(String.valueOf(b.get("from")), String.valueOf(b.get("to")), Boolean.TRUE.equals(b.get("blocked"))))
                throw new IllegalArgumentException("unknown road");
            String err = app.save();
            Map<String, Object> out = app.snapshot(); if (err != null) out.put("warning", err);
            return out;
        }));
        server.createContext("/", this::serveWeb);
        server.start();
    }
    public void stop() { if (server != null) server.stop(0); }
    public int getPort() { return port; }

    private interface Action { Object run() throws Exception; }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> body(HttpExchange ex) throws IOException {
        byte[] raw = ex.getRequestBody().readAllBytes();
        return (Map<String, Object>) MiniJson.parse(new String(raw, StandardCharsets.UTF_8));
    }

    private void handle(HttpExchange ex, String method, Action a) throws IOException {
        ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
        ex.getResponseHeaders().add("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
        if ("OPTIONS".equals(ex.getRequestMethod())) { ex.sendResponseHeaders(204, -1); ex.close(); return; }
        if (!method.equals(ex.getRequestMethod())) {
            sendJson(ex, 405, Map.of("error", "Method not allowed"));
            return;
        }
        int code = 200; String json;
        try { json = MiniJson.write(a.run()); }
        catch (IllegalArgumentException | ClassCastException | NullPointerException e) { code = 400; json = MiniJson.write(Map.of("error", String.valueOf(e.getMessage()))); }
        catch (Exception e) { code = 500; json = MiniJson.write(Map.of("error", String.valueOf(e.getMessage()))); }
        sendJson(ex, code, json);
    }

    private void serveWeb(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        if ("OPTIONS".equals(method)) { ex.sendResponseHeaders(204, -1); ex.close(); return; }
        if (!"GET".equals(method) && !"HEAD".equals(method)) {
            ex.getResponseHeaders().set("Allow", "GET, HEAD");
            sendText(ex, 405, "Method not allowed", "text/plain; charset=utf-8", "HEAD".equals(method));
            return;
        }

        String requestPath = ex.getRequestURI().getPath();
        String relative = "/".equals(requestPath) ? "login.html" : requestPath.substring(1);
        Path file = webRoot.resolve(relative).normalize();
        if (!file.startsWith(webRoot) || !Files.isRegularFile(file)) {
            sendText(ex, 404, "Not found", "text/plain; charset=utf-8", "HEAD".equals(method));
            return;
        }
        Path realRoot = webRoot.toRealPath();
        Path realFile = file.toRealPath();
        if (!realFile.startsWith(realRoot)) {
            sendText(ex, 404, "Not found", "text/plain; charset=utf-8", "HEAD".equals(method));
            return;
        }

        String contentType = contentType(file);
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.getResponseHeaders().set("Cache-Control", "no-cache");
        long length = Files.size(realFile);
        ex.sendResponseHeaders(200, "HEAD".equals(method) ? -1 : length);
        if (!"HEAD".equals(method)) {
            try (OutputStream out = ex.getResponseBody()) { Files.copy(realFile, out); }
        } else {
            ex.close();
        }
    }

    private static String contentType(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".html")) return "text/html; charset=utf-8";
        if (name.endsWith(".css")) return "text/css; charset=utf-8";
        if (name.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (name.endsWith(".json")) return "application/json; charset=utf-8";
        if (name.endsWith(".svg")) return "image/svg+xml";
        return "application/octet-stream";
    }

    private static void sendJson(HttpExchange ex, int code, Object value) throws IOException {
        sendText(ex, code, value instanceof String ? (String) value : MiniJson.write(value),
                "application/json; charset=utf-8", false);
    }

    private static void sendText(HttpExchange ex, int code, String text, String contentType, boolean head) throws IOException {
        byte[] out = text.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.sendResponseHeaders(code, head ? -1 : out.length);
        if (!head) {
            try (OutputStream os = ex.getResponseBody()) { os.write(out); }
        } else {
            ex.close();
        }
    }
}

package com.pvprooms.api;

import com.pvprooms.PvPRoomsPro;
import com.pvprooms.managers.TierManager;
import com.pvprooms.model.Tier;
import com.pvprooms.model.TierTitle;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;

/**
 * Servidor HTTP embebido que expone la API de tiers y sirve la web.
 *
 * Rutas:
 *   GET /                      → index.html
 *   GET /api/top               → top 100 jugadores (puntuación total)
 *   GET /api/top/{kit}         → top 100 para un kit
 *   GET /api/kits              → lista de kits
 *   GET /api/player/{nombre}   → datos de un jugador
 *   GET /api/stats             → estadísticas del servidor
 */
public class TierApiServer {

    private final PvPRoomsPro plugin;
    private HttpServer server;
    private final int port;

    public TierApiServer(PvPRoomsPro plugin, int port) {
        this.plugin = plugin;
        this.port   = port;
    }

    public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress(port), 50);
            server.createContext("/api/top",    this::handleTop);
            server.createContext("/api/kits",   this::handleKits);
            server.createContext("/api/player", this::handlePlayer);
            server.createContext("/api/stats",  this::handleStats);
            server.createContext("/",           this::handleRoot);
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
            plugin.getLogger().info("[TierAPI] Servidor web iniciado → http://0.0.0.0:" + port);
        } catch (Exception e) {
            plugin.getLogger().severe("[TierAPI] Error al iniciar: " + e.getMessage());
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(1);
            plugin.getLogger().info("[TierAPI] Servidor web detenido.");
        }
    }

    // ── Handlers ─────────────────────────────────────────────────────────

    private void handleRoot(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equalsIgnoreCase("GET")) { ex.sendResponseHeaders(405, -1); return; }

        String path = ex.getRequestURI().getPath();
        // Strip leading slash; default to index.html
        String resource = path.equals("/") || path.isBlank() ? "index.html" : path.substring(1);

        // Only allow known static web resources
        String contentType;
        if (resource.endsWith(".html"))      contentType = "text/html; charset=UTF-8";
        else if (resource.endsWith(".css"))  contentType = "text/css; charset=UTF-8";
        else if (resource.endsWith(".js"))   contentType = "application/javascript; charset=UTF-8";
        else if (resource.endsWith(".png"))  contentType = "image/png";
        else if (resource.endsWith(".ico"))  contentType = "image/x-icon";
        else { resource = "index.html"; contentType = "text/html; charset=UTF-8"; }

        InputStream is = plugin.getResource("web/" + resource);
        if (is == null) {
            // Fall back to index.html for SPA-style routing
            is = plugin.getResource("web/index.html");
            contentType = "text/html; charset=UTF-8";
        }
        if (is == null) { sendError(ex, 404, resource + " no encontrado"); return; }

        byte[] bytes = is.readAllBytes();
        ex.getResponseHeaders().set("Content-Type", contentType);
        cors(ex);
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    private void handleTop(HttpExchange ex) throws IOException {
        cors(ex);
        if (preflight(ex)) return;

        String[] parts = ex.getRequestURI().getPath().split("/", -1);
        // /api/top → parts=[, api, top]  |  /api/top/espada → parts=[, api, top, espada]
        String kitFilter = (parts.length >= 4 && !parts[3].isBlank()) ? parts[3] : null;

        TierManager tm = plugin.getTierManager();
        List<TierManager.PlayerRank> ranks = kitFilter != null
                ? tm.getTopForKit(kitFilter, 100)
                : tm.getTopPlayers(100);

        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < ranks.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(playerJson(ranks.get(i).uuid(), tm, kitFilter));
        }
        sb.append("]");
        sendJson(ex, sb.toString());
    }

    private void handleKits(HttpExchange ex) throws IOException {
        cors(ex);
        if (preflight(ex)) return;
        List<String> kits = plugin.getKitManager().getKitNames();
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < kits.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(esc(kits.get(i))).append("\"");
        }
        sb.append("]");
        sendJson(ex, sb.toString());
    }

    private void handlePlayer(HttpExchange ex) throws IOException {
        cors(ex);
        if (preflight(ex)) return;
        String[] parts = ex.getRequestURI().getPath().split("/", -1);
        if (parts.length < 4 || parts[3].isBlank()) {
            sendError(ex, 400, "Uso: /api/player/{nombre}"); return;
        }
        String name = parts[3];
        UUID uuid = resolveUUID(name);
        if (uuid == null) { sendError(ex, 404, "Jugador no encontrado"); return; }
        sendJson(ex, playerJson(uuid, plugin.getTierManager(), null));
    }

    private void handleStats(HttpExchange ex) throws IOException {
        cors(ex);
        if (preflight(ex)) return;
        int online = plugin.getServer().getOnlinePlayers().size();
        int duels  = plugin.getDuelManager().getActiveDuelCount();
        int queued = plugin.getQueueManager().getTotalQueued();
        String region = plugin.getServerRegion().toUpperCase();
        sendJson(ex, "{\"online\":" + online + ",\"duelos\":" + duels
                + ",\"en_cola\":" + queued + ",\"region\":\"" + esc(region) + "\"}");
    }

    // ── JSON builders ─────────────────────────────────────────────────────

    private String playerJson(UUID uuid, TierManager tm, String focusKit) {
        String name     = resolveName(uuid);
        int totalScore  = tm.getTotalScore(uuid);
        TierTitle title = tm.getTitle(uuid);
        Map<String, Integer> kits = tm.getKitPoints(uuid);

        StringBuilder kitsJson = new StringBuilder("{");
        boolean first = true;
        for (var entry : kits.entrySet()) {
            if (!first) kitsJson.append(",");
            Tier t = Tier.fromPoints(entry.getValue());
            kitsJson.append("\"").append(esc(entry.getKey())).append("\":{")
                    .append("\"puntos\":").append(entry.getValue())
                    .append(",\"tier\":\"").append(esc(t.displayName)).append("\"")
                    .append(",\"tierScore\":").append(t.tierScore())
                    .append(",\"color\":\"").append(esc(tierCssClass(t))).append("\"")
                    .append("}");
            first = false;
        }
        kitsJson.append("}");

        int focusPts = focusKit != null ? tm.getPoints(uuid, focusKit) : -1;
        String focusTier = focusKit != null && focusPts >= 0
                ? Tier.fromPoints(focusPts).displayName : null;

        return "{"
                + "\"uuid\":\""         + uuid           + "\""
                + ",\"nombre\":\""      + esc(name)      + "\""
                + ",\"titulo\":\""      + esc(title.name)    + "\""
                + ",\"tituloColor\":\"" + esc(title.colour)  + "\""
                + ",\"tituloSymbol\":\"" + esc(title.symbol) + "\""
                + ",\"puntosTotales\":" + totalScore
                + ",\"region\":\""      + esc(plugin.getServerRegion().toUpperCase()) + "\""
                + (focusTier != null ? ",\"focusTier\":\"" + esc(focusTier) + "\",\"focusPts\":" + focusPts : "")
                + ",\"kits\":"          + kitsJson
                + "}";
    }

    private static String tierCssClass(Tier t) {
        return t.name().toLowerCase().replace("_", "-");
    }

    // ── HTTP utilities ────────────────────────────────────────────────────

    private void sendJson(HttpExchange ex, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        cors(ex);
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    private void sendError(HttpExchange ex, int code, String msg) throws IOException {
        String body = "{\"error\":\"" + esc(msg) + "\"}";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        cors(ex);
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    private void cors(HttpExchange ex) {
        ex.getResponseHeaders().set("Access-Control-Allow-Origin",  "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    }

    /** Handles OPTIONS preflight. Returns true if it was a preflight (caller should return). */
    private boolean preflight(HttpExchange ex) throws IOException {
        if (ex.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
            ex.sendResponseHeaders(204, -1);
            return true;
        }
        return false;
    }

    // ── Name/UUID helpers ─────────────────────────────────────────────────

    private String resolveName(UUID uuid) {
        var online = plugin.getServer().getPlayer(uuid);
        if (online != null) return online.getName();
        var offline = plugin.getServer().getOfflinePlayer(uuid);
        String n = offline.getName();
        return n != null ? n : uuid.toString().substring(0, 8);
    }

    private UUID resolveUUID(String name) {
        for (var p : plugin.getServer().getOfflinePlayers()) {
            if (name.equalsIgnoreCase(p.getName())) return p.getUniqueId();
        }
        return null;
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "");
    }
}

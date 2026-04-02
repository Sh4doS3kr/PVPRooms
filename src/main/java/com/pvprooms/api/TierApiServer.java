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
            // Auth & Ticket endpoints
            server.createContext("/api/auth/register",  this::handleRegister);
            server.createContext("/api/auth/login",     this::handleLogin);
            server.createContext("/api/auth/logout",    this::handleLogout);
            server.createContext("/api/auth/session",   this::handleSession);
            server.createContext("/api/tickets/create", this::handleTicketCreate);
            server.createContext("/api/tickets/list",   this::handleTicketList);
            server.createContext("/api/tickets/view",   this::handleTicketView);
            server.createContext("/api/tickets/message",this::handleTicketMessage);
            server.createContext("/api/tickets/claim",  this::handleTicketClaim);
            server.createContext("/api/tickets/status", this::handleTicketStatus);
            server.createContext("/api/tickets/schedule", this::handleTicketSchedule);
            server.createContext("/api/tickets/result", this::handleTicketResult);
            server.createContext("/api/slots",          this::handleSlots);
            // Admin-only endpoints
            server.createContext("/api/admin/users",    this::handleAdminUsers);
            server.createContext("/api/admin/logs",     this::handleAdminLogs);
            server.createContext("/api/admin/user",     this::handleAdminUserAction);
            // Discord bot endpoints
            server.createContext("/api/discord/settier",       this::handleDiscordSetTier);
            server.createContext("/api/discord/notify",        this::handleDiscordNotify);
            server.createContext("/api/discord/player",        this::handleDiscordPlayer);
            server.createContext("/api/discord/send-link-code",this::handleDiscordSendLinkCode);
            server.createContext("/api/discord/confirm-link",  this::handleDiscordConfirmLink);
            server.createContext("/api/discord/link-status",   this::handleDiscordLinkStatus);
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
        int online  = plugin.getServer().getOnlinePlayers().size();
        int duels   = plugin.getDuelManager().getActiveDuelCount();
        int queued  = plugin.getQueueManager().getTotalQueued();
        int total   = plugin.getEloManager().getEloMap().size();
        String region = plugin.getServerRegion().toUpperCase();
        sendJson(ex, "{\"online\":" + online + ",\"duelos\":" + duels
                + ",\"en_cola\":" + queued + ",\"region\":\"" + esc(region) + "\""
                + ",\"total_jugadores\":" + total + "}");
    }

    // ── JSON builders ─────────────────────────────────────────────────────

    private String playerJson(UUID uuid, TierManager tm, String focusKit) {
        String name     = resolveName(uuid);
        int totalScore  = tm.getTotalScore(uuid);
        TierTitle title = tm.getTitle(uuid);
        Map<String, Integer> kits = tm.getKitPoints(uuid);
        int elo         = plugin.getEloManager().getElo(uuid);
        int eloRank     = plugin.getEloManager().getRank(uuid);
        Tier bestTier   = tm.getBestTier(uuid);
        String country  = plugin.getEloManager().getCountry(uuid);

        // Stats
        var stats = plugin.getStatsManager().getStats(uuid);
        int wins = stats.wins(), losses = stats.losses();
        int kills = stats.kills(), deaths = stats.deaths();
        int streak = stats.currentStreak(), bestStreak = stats.bestStreak();
        int hits = stats.hits(), swings = stats.swings();
        double kdr = stats.getKDR();
        double accuracy = stats.getAccuracy();
        double winrate = (wins + losses) > 0 ? (double) wins / (wins + losses) * 100 : 0;

        // Ping (if online)
        var player = plugin.getServer().getPlayer(uuid);
        int ping = player != null ? player.getPing() : -1;
        boolean online = player != null;

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
                    .append(",\"tierOrdinal\":").append(t.ordinal())
                    .append("}");
            first = false;
        }
        kitsJson.append("}");

        int focusPts = focusKit != null ? tm.getPoints(uuid, focusKit) : -1;
        String focusTier = focusKit != null && focusPts >= 0
                ? Tier.fromPoints(focusPts).displayName : null;

        String officialRank = plugin.getEloManager().getOfficialRank(uuid);

        return "{"
                + "\"uuid\":\""           + uuid                       + "\""
                + ",\"nombre\":\""        + esc(name)                  + "\""
                + ",\"titulo\":\""        + esc(title.name)            + "\""
                + ",\"tituloColor\":\""   + esc(title.colour)          + "\""
                + ",\"tituloSymbol\":\""  + esc(title.symbol)          + "\""
                + ",\"puntosTotales\":"   + totalScore
                + ",\"elo\":"             + elo
                + ",\"eloRank\":"         + eloRank
                + ",\"bestTier\":\""      + esc(bestTier.displayName)  + "\""
                + ",\"bestTierOrdinal\":" + bestTier.ordinal()
                + ",\"region\":\""        + esc(plugin.getServerRegion().toUpperCase()) + "\""
                + ",\"pais\":\""          + esc(country)               + "\""
                + ",\"officialRank\":\""  + esc(officialRank)          + "\""
                + ",\"wins\":"            + wins
                + ",\"losses\":"          + losses
                + ",\"kills\":"           + kills
                + ",\"deaths\":"          + deaths
                + ",\"kdr\":"             + String.format(java.util.Locale.US, "%.2f", kdr)
                + ",\"winrate\":"         + String.format(java.util.Locale.US, "%.1f", winrate)
                + ",\"streak\":"          + streak
                + ",\"bestStreak\":"      + bestStreak
                + ",\"hits\":"            + hits
                + ",\"swings\":"          + swings
                + ",\"accuracy\":"        + String.format(java.util.Locale.US, "%.1f", accuracy)
                + ",\"ping\":"            + ping
                + ",\"online\":"          + online
                + (focusTier != null ? ",\"focusTier\":\"" + esc(focusTier) + "\",\"focusPts\":" + focusPts : "")
                + ",\"kits\":"            + kitsJson
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
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        // Prevent caching - always get fresh data
        ex.getResponseHeaders().set("Cache-Control", "no-cache, no-store, must-revalidate");
        ex.getResponseHeaders().set("Pragma", "no-cache");
        ex.getResponseHeaders().set("Expires", "0");
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
        // Check online players first — guaranteed to be found
        for (var p : plugin.getServer().getOnlinePlayers()) {
            if (name.equalsIgnoreCase(p.getName())) return p.getUniqueId();
        }
        // Fall back to offline player data
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

    // ═══ AUTH HANDLERS ═════════════════════════════════════════════════════

    private void handleRegister(HttpExchange ex) throws IOException {
        cors(ex);
        if (preflight(ex)) return;
        if (!ex.getRequestMethod().equalsIgnoreCase("POST")) {
            sendError(ex, 405, "POST required");
            return;
        }

        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String username = extractJson(body, "username");
        String ip = getClientIP(ex);

        var tm = plugin.getTicketManager();
        if (tm == null) { sendError(ex, 500, "Sistema no disponible"); return; }

        var result = tm.initiateRegistration(username, ip);
        sendJson(ex, "{\"success\":" + result.success() + 
                     ",\"code\":" + (result.code() != null ? "\"" + result.code() + "\"" : "null") +
                     ",\"message\":\"" + esc(result.message()) + "\"}");
    }

    private void handleLogin(HttpExchange ex) throws IOException {
        cors(ex);
        if (preflight(ex)) return;
        if (!ex.getRequestMethod().equalsIgnoreCase("POST")) {
            sendError(ex, 405, "POST required");
            return;
        }

        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String username = extractJson(body, "username");
        String ip = getClientIP(ex);

        var tm = plugin.getTicketManager();
        if (tm == null) { sendError(ex, 500, "Sistema no disponible"); return; }

        var result = tm.login(username, ip);
        if (result.success()) {
            var user = tm.getUser(username);
            String role = tm.getRole(username);
            sendJson(ex, "{\"success\":true,\"token\":\"" + result.token() + "\"" +
                        ",\"username\":\"" + esc(user.username) + "\"" +
                        ",\"uuid\":\"" + user.uuid + "\"" +
                        ",\"role\":\"" + role + "\"" +
                        ",\"message\":\"" + esc(result.message()) + "\"}");
        } else {
            sendJson(ex, "{\"success\":false,\"message\":\"" + esc(result.message()) + "\"}");
        }
    }

    private void handleLogout(HttpExchange ex) throws IOException {
        cors(ex);
        if (preflight(ex)) return;

        String token = getAuthToken(ex);
        if (token != null) {
            plugin.getTicketManager().logout(token);
        }
        sendJson(ex, "{\"success\":true}");
    }

    private void handleSession(HttpExchange ex) throws IOException {
        cors(ex);
        if (preflight(ex)) return;

        String token = getAuthToken(ex);
        var tm = plugin.getTicketManager();
        if (tm == null) { sendError(ex, 500, "Sistema no disponible"); return; }

        String username = tm.validateSession(token);
        if (username == null) {
            sendJson(ex, "{\"valid\":false}");
            return;
        }

        var user = tm.getUser(username);
        String role = tm.getRole(username);
        sendJson(ex, "{\"valid\":true,\"username\":\"" + esc(user.username) + "\"" +
                    ",\"uuid\":\"" + user.uuid + "\"" +
                    ",\"role\":\"" + role + "\"}");
    }

    // ═══ TICKET HANDLERS ═══════════════════════════════════════════════════

    private void handleTicketCreate(HttpExchange ex) throws IOException {
        cors(ex);
        if (preflight(ex)) return;
        if (!ex.getRequestMethod().equalsIgnoreCase("POST")) {
            sendError(ex, 405, "POST required");
            return;
        }

        var tm = plugin.getTicketManager();
        String token = getAuthToken(ex);
        String username = tm.validateSession(token);
        if (username == null) { sendError(ex, 401, "Sesión inválida"); return; }

        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String kit = extractJson(body, "kit");
        String tier = extractJson(body, "tier");
        String slot = extractJson(body, "slot");
        String notes = extractJson(body, "notes");
        String ip = getClientIP(ex);

        var result = tm.createTicket(username, kit, tier, slot, notes, ip);
        sendJson(ex, "{\"success\":" + result.success() + 
                    ",\"ticketId\":" + (result.ticketId() != null ? "\"" + result.ticketId() + "\"" : "null") +
                    ",\"message\":\"" + esc(result.message()) + "\"}");
    }

    private void handleTicketList(HttpExchange ex) throws IOException {
        cors(ex);
        if (preflight(ex)) return;

        var tm = plugin.getTicketManager();
        String token = getAuthToken(ex);
        String username = tm.validateSession(token);
        if (username == null) { sendError(ex, 401, "Sesión inválida"); return; }

        String statusFilter = getQueryParam(ex, "status");
        List<com.pvprooms.managers.TicketManager.Ticket> tickets;

        if (tm.isStaff(username)) {
            tickets = tm.getAllTickets(statusFilter);
        } else {
            tickets = tm.getUserTickets(username);
        }

        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (var ticket : tickets) {
            if (!first) sb.append(",");
            sb.append(ticketToJson(ticket, false));
            first = false;
        }
        sb.append("]");
        sendJson(ex, sb.toString());
    }

    private void handleTicketView(HttpExchange ex) throws IOException {
        cors(ex);
        if (preflight(ex)) return;

        var tm = plugin.getTicketManager();
        String token = getAuthToken(ex);
        String username = tm.validateSession(token);
        if (username == null) { sendError(ex, 401, "Sesión inválida"); return; }

        String ticketId = getQueryParam(ex, "id");
        var ticket = tm.getTicket(ticketId);
        if (ticket == null) { sendError(ex, 404, "Ticket no encontrado"); return; }

        // Check access
        if (!ticket.username.equalsIgnoreCase(username) && !tm.isStaff(username)) {
            sendError(ex, 403, "Sin acceso a este ticket");
            return;
        }

        sendJson(ex, ticketToJson(ticket, true));
    }

    private void handleTicketMessage(HttpExchange ex) throws IOException {
        cors(ex);
        if (preflight(ex)) return;
        if (!ex.getRequestMethod().equalsIgnoreCase("POST")) {
            sendError(ex, 405, "POST required");
            return;
        }

        var tm = plugin.getTicketManager();
        String token = getAuthToken(ex);
        String username = tm.validateSession(token);
        if (username == null) { sendError(ex, 401, "Sesión inválida"); return; }

        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String ticketId = extractJson(body, "ticketId");
        String message = extractJson(body, "message");

        var ticket = tm.getTicket(ticketId);
        if (ticket == null) { sendError(ex, 404, "Ticket no encontrado"); return; }

        if (!ticket.username.equalsIgnoreCase(username) && !tm.isStaff(username)) {
            sendError(ex, 403, "Sin acceso");
            return;
        }

        boolean success = tm.addMessage(ticketId, username, message);
        sendJson(ex, "{\"success\":" + success + "}");
    }

    private void handleTicketClaim(HttpExchange ex) throws IOException {
        cors(ex);
        if (preflight(ex)) return;
        if (!ex.getRequestMethod().equalsIgnoreCase("POST")) {
            sendError(ex, 405, "POST required");
            return;
        }

        var tm = plugin.getTicketManager();
        String token = getAuthToken(ex);
        String username = tm.validateSession(token);
        if (username == null) { sendError(ex, 401, "Sesión inválida"); return; }
        if (!tm.isStaff(username)) { sendError(ex, 403, "Solo staff"); return; }

        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String ticketId = extractJson(body, "ticketId");

        boolean success = tm.claimTicket(ticketId, username);
        sendJson(ex, "{\"success\":" + success + "}");
    }

    private void handleTicketStatus(HttpExchange ex) throws IOException {
        cors(ex);
        if (preflight(ex)) return;
        if (!ex.getRequestMethod().equalsIgnoreCase("POST")) {
            sendError(ex, 405, "POST required");
            return;
        }

        var tm = plugin.getTicketManager();
        String token = getAuthToken(ex);
        String username = tm.validateSession(token);
        if (username == null) { sendError(ex, 401, "Sesión inválida"); return; }
        if (!tm.isStaff(username)) { sendError(ex, 403, "Solo staff"); return; }

        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String ticketId = extractJson(body, "ticketId");
        String status = extractJson(body, "status");

        boolean success = tm.updateTicketStatus(ticketId, 
            com.pvprooms.managers.TicketManager.TicketStatus.valueOf(status.toUpperCase()), username);
        sendJson(ex, "{\"success\":" + success + "}");
    }

    private void handleTicketSchedule(HttpExchange ex) throws IOException {
        cors(ex);
        if (preflight(ex)) return;
        if (!ex.getRequestMethod().equalsIgnoreCase("POST")) {
            sendError(ex, 405, "POST required");
            return;
        }

        var tm = plugin.getTicketManager();
        String token = getAuthToken(ex);
        String username = tm.validateSession(token);
        if (username == null) { sendError(ex, 401, "Sesión inválida"); return; }
        if (!tm.isStaff(username)) { sendError(ex, 403, "Solo staff"); return; }

        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String ticketId = extractJson(body, "ticketId");
        String dateTime = extractJson(body, "dateTime");

        boolean success = tm.scheduleTest(ticketId, dateTime, username);
        sendJson(ex, "{\"success\":" + success + "}");
    }

    private void handleTicketResult(HttpExchange ex) throws IOException {
        cors(ex);
        if (preflight(ex)) return;
        if (!ex.getRequestMethod().equalsIgnoreCase("POST")) {
            sendError(ex, 405, "POST required");
            return;
        }

        var tm = plugin.getTicketManager();
        String token = getAuthToken(ex);
        String username = tm.validateSession(token);
        if (username == null) { sendError(ex, 401, "Sesión inválida"); return; }
        if (!tm.isStaff(username)) { sendError(ex, 403, "Solo staff"); return; }

        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String ticketId = extractJson(body, "ticketId");
        boolean passed = "true".equalsIgnoreCase(extractJson(body, "passed"));
        String newTier = extractJson(body, "newTier");

        boolean success = tm.setTestResult(ticketId, passed, newTier, username);
        sendJson(ex, "{\"success\":" + success + "}");
    }

    private void handleSlots(HttpExchange ex) throws IOException {
        cors(ex);
        if (preflight(ex)) return;

        StringBuilder sb = new StringBuilder("[");
        String[] slots = com.pvprooms.managers.TicketManager.AVAILABLE_SLOTS;
        for (int i = 0; i < slots.length; i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(esc(slots[i])).append("\"");
        }
        sb.append("]");
        sendJson(ex, sb.toString());
    }

    // ═══ HELPER METHODS ════════════════════════════════════════════════════

    private String ticketToJson(com.pvprooms.managers.TicketManager.Ticket t, boolean includeMessages) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"id\":\"").append(esc(t.id)).append("\"");
        sb.append(",\"username\":\"").append(esc(t.username)).append("\"");
        sb.append(",\"kit\":\"").append(esc(t.kit)).append("\"");
        sb.append(",\"requestedTier\":\"").append(esc(t.requestedTier)).append("\"");
        sb.append(",\"preferredSlot\":\"").append(esc(t.preferredSlot)).append("\"");
        sb.append(",\"notes\":\"").append(esc(t.notes)).append("\"");
        sb.append(",\"createdAt\":").append(t.createdAt);
        sb.append(",\"status\":\"").append(t.status.name()).append("\"");
        sb.append(",\"statusDisplay\":\"").append(esc(t.status.displayName)).append("\"");
        sb.append(",\"statusColor\":\"").append(esc(t.status.color)).append("\"");
        sb.append(",\"assignedTester\":").append(t.assignedTester != null ? "\"" + esc(t.assignedTester) + "\"" : "null");
        sb.append(",\"scheduledTime\":").append(t.scheduledTime != null ? "\"" + esc(t.scheduledTime) + "\"" : "null");
        sb.append(",\"resultTier\":").append(t.resultTier != null ? "\"" + esc(t.resultTier) + "\"" : "null");

        if (includeMessages) {
            sb.append(",\"messages\":[");
            boolean first = true;
            for (var msg : t.messages) {
                if (!first) sb.append(",");
                sb.append("{\"sender\":\"").append(esc(msg.sender)).append("\"");
                sb.append(",\"timestamp\":").append(msg.timestamp);
                sb.append(",\"message\":\"").append(esc(msg.message)).append("\"}");
                first = false;
            }
            sb.append("]");
        }

        sb.append("}");
        return sb.toString();
    }

    private String getAuthToken(HttpExchange ex) {
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7);
        }
        return getQueryParam(ex, "token");
    }

    private String getClientIP(HttpExchange ex) {
        String xff = ex.getRequestHeaders().getFirst("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return ex.getRemoteAddress().getAddress().getHostAddress();
    }

    private String getQueryParam(HttpExchange ex, String key) {
        String query = ex.getRequestURI().getQuery();
        if (query == null) return null;
        for (String param : query.split("&")) {
            String[] kv = param.split("=", 2);
            if (kv.length == 2 && kv[0].equals(key)) {
                return java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    // ═══ ADMIN HANDLERS ═════════════════════════════════════════════════════

    private void handleAdminUsers(HttpExchange ex) throws IOException {
        cors(ex);
        if (preflight(ex)) return;

        var tm = plugin.getTicketManager();
        String token = getAuthToken(ex);
        String username = tm.validateSession(token);
        if (username == null || !tm.isAdmin(username)) {
            sendError(ex, 403, "Acceso denegado. Solo administradores.");
            return;
        }

        // Get all registered users
        var users = tm.getAllUsers();
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (var user : users) {
            if (!first) sb.append(",");
            String role = tm.getRole(user.username);
            sb.append("{\"username\":\"").append(esc(user.username)).append("\"");
            sb.append(",\"uuid\":\"").append(user.uuid).append("\"");
            sb.append(",\"role\":\"").append(role).append("\"");
            sb.append(",\"registeredAt\":").append(user.registeredAt);
            sb.append(",\"lastIp\":\"").append(esc(user.registeredIp)).append("\"}");
            first = false;
        }
        sb.append("]");
        sendJson(ex, sb.toString());
    }

    private void handleAdminLogs(HttpExchange ex) throws IOException {
        cors(ex);
        if (preflight(ex)) return;

        var tm = plugin.getTicketManager();
        String token = getAuthToken(ex);
        String username = tm.validateSession(token);
        if (username == null || !tm.isAdmin(username)) {
            sendError(ex, 403, "Acceso denegado. Solo administradores.");
            return;
        }

        // Get recent activity logs
        var logs = tm.getActivityLogs(50);
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (var log : logs) {
            if (!first) sb.append(",");
            sb.append("{\"timestamp\":").append(log.timestamp);
            sb.append(",\"type\":\"").append(esc(log.type)).append("\"");
            sb.append(",\"username\":\"").append(esc(log.username)).append("\"");
            sb.append(",\"details\":\"").append(esc(log.details)).append("\"}");
            first = false;
        }
        sb.append("]");
        sendJson(ex, sb.toString());
    }

    private void handleAdminUserAction(HttpExchange ex) throws IOException {
        cors(ex);
        if (preflight(ex)) return;
        if (!ex.getRequestMethod().equalsIgnoreCase("POST")) {
            sendError(ex, 405, "POST required");
            return;
        }

        var tm = plugin.getTicketManager();
        String token = getAuthToken(ex);
        String adminUser = tm.validateSession(token);
        if (adminUser == null || !tm.isAdmin(adminUser)) {
            sendError(ex, 403, "Acceso denegado. Solo administradores.");
            return;
        }

        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String targetUser = extractJson(body, "username");
        String action = extractJson(body, "action");

        if (targetUser == null || targetUser.isBlank()) {
            sendError(ex, 400, "Usuario requerido");
            return;
        }

        boolean success = false;
        String message = "";

        switch (action.toLowerCase()) {
            case "delete" -> {
                success = tm.deleteUser(targetUser);
                message = success ? "Usuario eliminado" : "Usuario no encontrado";
                if (success) tm.addLog("USER_DELETE", adminUser, "Eliminó usuario: " + targetUser);
            }
            case "reset_sessions" -> {
                tm.invalidateUserSessions(targetUser);
                success = true;
                message = "Sesiones invalidadas";
                tm.addLog("SESSION_RESET", adminUser, "Invalidó sesiones de: " + targetUser);
            }
            case "set_tester" -> {
                // Note: This would require dynamic role management - for now just log
                tm.addLog("ROLE_CHANGE", adminUser, "Intentó cambiar rol de: " + targetUser);
                message = "Los roles se configuran en el código. Contacta al desarrollador.";
            }
            default -> {
                sendError(ex, 400, "Acción no válida: " + action);
                return;
            }
        }

        sendJson(ex, "{\"success\":" + success + ",\"message\":\"" + esc(message) + "\"}");
    }

    // ── Discord Bot Endpoints ─────────────────────────────────────────────

    /**
     * POST /api/discord/settier
     * Headers: X-Api-Key: <secret>
     * Body: { "player": "Steve", "tier": "LT3", "kit": "sword" }
     * Assigns a tier to a player (online or offline) and notifies them in-game if online.
     */
    private void handleDiscordSetTier(HttpExchange ex) throws IOException {
        cors(ex);
        if (preflight(ex)) return;
        if (!checkDiscordKey(ex)) return;
        if (!ex.getRequestMethod().equalsIgnoreCase("POST")) { ex.sendResponseHeaders(405, -1); return; }

        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String playerName = extractJson(body, "player");
        String tierStr    = extractJson(body, "tier").toUpperCase();
        String kitName    = extractJson(body, "kit").toLowerCase();

        if (playerName.isBlank() || tierStr.isBlank() || kitName.isBlank()) {
            sendError(ex, 400, "Requerido: player, tier, kit"); return;
        }

        Tier tier;
        try { tier = Tier.valueOf(tierStr); }
        catch (IllegalArgumentException e) {
            sendError(ex, 400, "Tier inválido: " + tierStr + ". Válidos: " +
                    java.util.Arrays.stream(Tier.values()).map(Enum::name)
                            .collect(java.util.stream.Collectors.joining(", ")));
            return;
        }

        UUID uuid = resolveUUID(playerName);
        if (uuid == null) { sendError(ex, 404, "Jugador no encontrado: " + playerName); return; }

        // Set tier points to the minimum for that tier
        plugin.getTierManager().setPoints(uuid, kitName, tier.minPoints);

        // Notify the player if online (must run on main thread)
        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
            org.bukkit.entity.Player p = org.bukkit.Bukkit.getPlayer(uuid);
            if (p != null) {
                p.sendMessage(plugin.prefix() + "§d§l✦ ¡TIER ASIGNADO POR DISCORD!");
                p.sendMessage(plugin.prefix() + "§7Kit: §b" + kitName + " §8→ " + tier.colour + "§l" + tier.displayName);
                p.playSound(p.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            }
        });

        plugin.getLogger().info("[Discord] Tier asignado: " + playerName + " → " + tier.displayName + " (" + kitName + ")");
        sendJson(ex, "{\"success\":true,\"player\":\"" + esc(playerName) + "\",\"tier\":\"" + tier.displayName + "\",\"kit\":\"" + esc(kitName) + "\"}");
    }

    /**
     * POST /api/discord/notify
     * Headers: X-Api-Key: <secret>
     * Body: { "player": "Steve", "message": "Tu test está listo" }
     * Sends a chat message to an online player from Discord staff.
     */
    private void handleDiscordNotify(HttpExchange ex) throws IOException {
        cors(ex);
        if (preflight(ex)) return;
        if (!checkDiscordKey(ex)) return;
        if (!ex.getRequestMethod().equalsIgnoreCase("POST")) { ex.sendResponseHeaders(405, -1); return; }

        String body    = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String target  = extractJson(body, "player");
        String message = extractJson(body, "message");

        if (target.isBlank() || message.isBlank()) {
            sendError(ex, 400, "Requerido: player, message"); return;
        }

        UUID uuid = resolveUUID(target);
        if (uuid == null) { sendError(ex, 404, "Jugador no encontrado: " + target); return; }

        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
            org.bukkit.entity.Player p = org.bukkit.Bukkit.getPlayer(uuid);
            if (p != null) {
                p.sendMessage("§b[Discord Staff] §f" + message);
            }
        });

        boolean online = org.bukkit.Bukkit.getPlayer(uuid) != null;
        sendJson(ex, "{\"success\":true,\"online\":" + online + "}");
    }

    /**
     * GET /api/discord/player/{nombre}
     * Headers: X-Api-Key: <secret>
     * Returns tier and ELO info for a player — useful for the Discord bot to display stats.
     */
    private void handleDiscordPlayer(HttpExchange ex) throws IOException {
        cors(ex);
        if (preflight(ex)) return;
        if (!checkDiscordKey(ex)) return;

        String[] parts = ex.getRequestURI().getPath().split("/", -1);
        if (parts.length < 5 || parts[4].isBlank()) {
            sendError(ex, 400, "Uso: /api/discord/player/{nombre}"); return;
        }
        String name = parts[4];
        UUID uuid = resolveUUID(name);
        if (uuid == null) { sendError(ex, 404, "Jugador no encontrado"); return; }

        TierManager tm = plugin.getTierManager();
        int elo = plugin.getEloManager().getElo(uuid);
        Tier bestTier = tm.getBestTier(uuid);
        boolean online = org.bukkit.Bukkit.getPlayer(uuid) != null;

        StringBuilder kitsJson = new StringBuilder("{");
        boolean first = true;
        for (var entry : tm.getKitPoints(uuid).entrySet()) {
            if (!first) kitsJson.append(",");
            Tier t = Tier.fromPoints(entry.getValue());
            kitsJson.append("\"").append(esc(entry.getKey())).append("\":{\"points\":")
                    .append(entry.getValue()).append(",\"tier\":\"")
                    .append(t.displayName).append("\"}");
            first = false;
        }
        kitsJson.append("}");

        sendJson(ex, "{\"player\":\"" + esc(resolveName(uuid)) + "\",\"elo\":" + elo
                + ",\"best_tier\":\"" + bestTier.displayName + "\",\"online\":" + online
                + ",\"kits\":" + kitsJson + "}");
    }

    /**
     * POST /api/discord/send-link-code
     * Body: { "player": "Steve", "discordId": "123456789", "discordUsername": "steve" }
     * Generates a 6-digit code, sends it to the player in-game, and waits for them to
     * confirm by telling the bot the code (which then calls /confirm-link).
     */
    private void handleDiscordSendLinkCode(HttpExchange ex) throws IOException {
        cors(ex);
        if (preflight(ex)) return;
        if (!checkDiscordKey(ex)) return;
        if (!ex.getRequestMethod().equalsIgnoreCase("POST")) { ex.sendResponseHeaders(405, -1); return; }

        String body          = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String playerName    = extractJson(body, "player");
        String discordId     = extractJson(body, "discordId");
        String discordUser   = extractJson(body, "discordUsername");

        if (playerName.isBlank() || discordId.isBlank()) {
            sendError(ex, 400, "Requerido: player, discordId"); return;
        }

        UUID uuid = resolveUUID(playerName);
        if (uuid == null) { sendError(ex, 404, "Jugador no encontrado: " + playerName); return; }

        String code = plugin.getTierManager().createLinkCode(uuid, discordId, discordUser.isBlank() ? discordId : discordUser);

        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
            org.bukkit.entity.Player p = org.bukkit.Bukkit.getPlayer(uuid);
            if (p != null) {
                p.sendMessage("§8§m──────────────────────────────");
                p.sendMessage(plugin.prefix() + "§b§lVinculación de Discord");
                p.sendMessage(plugin.prefix() + "§7Tu código de vinculación es:");
                p.sendMessage(plugin.prefix() + "§a§l" + code);
                p.sendMessage(plugin.prefix() + "§7Envíalo al bot de Discord. Válido 5 min.");
                p.sendMessage("§8§m──────────────────────────────");
                p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1f);
            }
        });

        boolean online = org.bukkit.Bukkit.getPlayer(uuid) != null;
        sendJson(ex, "{\"success\":true,\"code\":\"" + code + "\",\"online\":" + online + "}");
    }

    /**
     * POST /api/discord/confirm-link
     * Body: { "code": "123456", "discordId": "123456789" }
     * Called by the Discord bot after the player tells them the code.
     * Confirms the link and notifies the player in-game.
     */
    private void handleDiscordConfirmLink(HttpExchange ex) throws IOException {
        cors(ex);
        if (preflight(ex)) return;
        if (!checkDiscordKey(ex)) return;
        if (!ex.getRequestMethod().equalsIgnoreCase("POST")) { ex.sendResponseHeaders(405, -1); return; }

        String body      = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String code      = extractJson(body, "code");
        String discordId = extractJson(body, "discordId");

        if (code.isBlank() || discordId.isBlank()) {
            sendError(ex, 400, "Requerido: code, discordId"); return;
        }

        UUID uuid = plugin.getTierManager().confirmLinkCode(code, discordId);
        if (uuid == null) {
            sendError(ex, 400, "Código inválido, expirado o Discord ID incorrecto"); return;
        }

        String playerName = resolveName(uuid);
        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
            org.bukkit.entity.Player p = org.bukkit.Bukkit.getPlayer(uuid);
            if (p != null) {
                p.sendMessage(plugin.prefix() + "§a§l¡Discord vinculado correctamente!");
                p.sendMessage(plugin.prefix() + "§7Cuenta: §b" + discordId);
                p.playSound(p.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            }
        });

        plugin.getLogger().info("[Discord] Vinculado: " + playerName + " → " + discordId);
        sendJson(ex, "{\"success\":true,\"player\":\"" + esc(playerName) + "\",\"uuid\":\"" + uuid + "\"}");
    }

    /**
     * GET /api/discord/link-status/{discordId}
     * Returns whether a Discord ID is linked to a Minecraft account.
     */
    private void handleDiscordLinkStatus(HttpExchange ex) throws IOException {
        cors(ex);
        if (preflight(ex)) return;
        if (!checkDiscordKey(ex)) return;

        String[] parts = ex.getRequestURI().getPath().split("/", -1);
        if (parts.length < 5 || parts[4].isBlank()) {
            sendError(ex, 400, "Uso: /api/discord/link-status/{discordId}"); return;
        }
        String discordId = parts[4];
        UUID uuid = plugin.getTierManager().getLinkedUUID(discordId);
        if (uuid == null) {
            sendJson(ex, "{\"linked\":false}"); return;
        }
        String name = resolveName(uuid);
        boolean online = org.bukkit.Bukkit.getPlayer(uuid) != null;
        sendJson(ex, "{\"linked\":true,\"player\":\"" + esc(name) + "\",\"uuid\":\"" + uuid + "\",\"online\":" + online + "}");
    }

    /** Returns true if the request carries a valid Discord API key, sends 403 otherwise. */
    private boolean checkDiscordKey(HttpExchange ex) throws IOException {
        String expected = plugin.getConfig().getString("web-api.discord-api-key", "");
        String provided = ex.getRequestHeaders().getFirst("X-Api-Key");
        if (expected.isBlank() || !expected.equals(provided)) {
            sendError(ex, 403, "API key inválida");
            return false;
        }
        return true;
    }

    private String extractJson(String json, String key) {
        // Simple JSON extraction (works for flat objects)
        String pattern = "\"" + key + "\"\\s*:\\s*";
        int idx = json.indexOf("\"" + key + "\"");
        if (idx == -1) return "";
        
        int colonIdx = json.indexOf(":", idx);
        if (colonIdx == -1) return "";
        
        int start = colonIdx + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) start++;
        
        if (start >= json.length()) return "";
        
        if (json.charAt(start) == '"') {
            int end = start + 1;
            while (end < json.length() && json.charAt(end) != '"') {
                if (json.charAt(end) == '\\') end++;
                end++;
            }
            return json.substring(start + 1, end).replace("\\\"", "\"").replace("\\n", "\n");
        } else {
            int end = start;
            while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}') end++;
            return json.substring(start, end).trim();
        }
    }
}

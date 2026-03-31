package com.pvprooms.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import java.util.stream.Collectors;

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
    private final Gson gson = new GsonBuilder().create();
    private HttpServer server;
    private final int port;

    public TierApiServer(PvPRoomsPro plugin, int port) {
        this.plugin = plugin;
        this.port   = port;
    }

    public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress(port), 50);
            server.createContext("/api/top",       this::handleTop);
            server.createContext("/api/kits",      this::handleKits);
            server.createContext("/api/player",    this::handlePlayer);
            server.createContext("/api/stats",     this::handleStats);
            // Auth
            server.createContext("/api/auth",      this::handleAuth);
            // Tickets
            server.createContext("/api/tickets",   this::handleTickets);
            // Schedules
            server.createContext("/api/schedules", this::handleSchedules);
            // Admin
            server.createContext("/api/admin",     this::handleAdmin);
            server.createContext("/",              this::handleRoot);
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
        double kdr = stats.getKDR();
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
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Token");
    }

    /** Handles OPTIONS preflight. Returns true if it was a preflight (caller should return). */
    private boolean preflight(HttpExchange ex) throws IOException {
        if (ex.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
            cors(ex);
            ex.sendResponseHeaders(204, -1);
            return true;
        }
        return false;
    }

    // ── Auth handler ─────────────────────────────────────────────────────────

    private void handleAuth(HttpExchange ex) throws IOException {
        cors(ex);
        if (preflight(ex)) return;
        String path   = ex.getRequestURI().getPath(); // /api/auth/register etc.
        String method = ex.getRequestMethod().toUpperCase();
        TicketManager tm = plugin.getTicketManager();

        // GET /api/auth/me
        if (method.equals("GET") && path.endsWith("/me")) {
            TicketManager.WebUser u = tm.getByToken(getToken(ex));
            if (u == null) { sendError(ex, 401, "No autenticado."); return; }
            sendJson(ex, userJson(u));
            return;
        }

        // POST /api/auth/register
        if (method.equals("POST") && path.endsWith("/register")) {
            JsonObject body = parseBody(ex);
            if (body == null) { sendError(ex, 400, "Body inválido."); return; }
            String username = getString(body, "username");
            String ip       = getClientIp(ex);
            var result = tm.register(username, ip);
            if (!result.success()) { sendError(ex, 400, result.error()); return; }
            sendJson(ex, "{\"success\":true,\"message\":\"Código enviado al juego. Tienes 5 minutos para introducirlo.\"}");
            return;
        }

        // POST /api/auth/verify
        if (method.equals("POST") && path.endsWith("/verify")) {
            JsonObject body = parseBody(ex);
            if (body == null) { sendError(ex, 400, "Body inválido."); return; }
            String username = getString(body, "username");
            String code     = getString(body, "code");
            String ip       = getClientIp(ex);
            var result = tm.verify(username, code, ip);
            if (!result.success()) { sendError(ex, 400, result.error()); return; }
            sendJson(ex, "{\"success\":true,\"token\":\"" + esc(result.token()) + "\",\"user\":" + userJson(result.user()) + "}");
            return;
        }

        // POST /api/auth/logout
        if (method.equals("POST") && path.endsWith("/logout")) {
            tm.logout(getToken(ex));
            sendJson(ex, "{\"success\":true}");
            return;
        }

        sendError(ex, 404, "Endpoint no encontrado.");
    }

    // ── Tickets handler ──────────────────────────────────────────────────────

    private void handleTickets(HttpExchange ex) throws IOException {
        cors(ex);
        if (preflight(ex)) return;
        String path   = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod().toUpperCase();
        TicketManager tm   = plugin.getTicketManager();
        TicketManager.WebUser user = tm.getByToken(getToken(ex));

        // GET /api/tickets  or  POST /api/tickets
        if (path.equals("/api/tickets") || path.equals("/api/tickets/")) {
            if (method.equals("GET")) {
                if (user == null) { sendError(ex, 401, "No autenticado."); return; }
                List<TicketManager.Ticket> list = tm.getTicketsFor(user);
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) sb.append(",");
                    sb.append(ticketJson(list.get(i), user));
                }
                sb.append("]");
                sendJson(ex, sb.toString());
                return;
            }
            if (method.equals("POST")) {
                if (user == null) { sendError(ex, 401, "No autenticado."); return; }
                JsonObject body = parseBody(ex);
                if (body == null) { sendError(ex, 400, "Body inválido."); return; }
                String kit  = getString(body, "kit");
                String tier = getString(body, "targetTier");
                String ip   = getClientIp(ex);
                var result  = tm.createTicket(user, kit, tier, ip);
                if (!result.success()) { sendError(ex, 400, result.error()); return; }
                sendJson(ex, ticketJson(result.ticket(), user));
                return;
            }
        }

        // /api/tickets/{id}/...
        String[] parts = path.split("/", -1);
        if (parts.length >= 4) {
            String id  = parts[3].toUpperCase();
            String sub = parts.length >= 5 ? parts[4] : "";

            // GET /api/tickets/{id}
            if (method.equals("GET") && sub.isEmpty()) {
                if (user == null) { sendError(ex, 401, "No autenticado."); return; }
                TicketManager.Ticket t = tm.getTicket(id);
                if (t == null) { sendError(ex, 404, "Ticket no encontrado."); return; }
                boolean owner = t.username.equalsIgnoreCase(user.username);
                boolean staff = !TicketManager.ROLE_USER.equals(user.role);
                if (!owner && !staff) { sendError(ex, 403, "Sin permisos."); return; }
                sendJson(ex, ticketJson(t, user));
                return;
            }

            // POST /api/tickets/{id}/message
            if (method.equals("POST") && sub.equals("message")) {
                if (user == null) { sendError(ex, 401, "No autenticado."); return; }
                JsonObject body = parseBody(ex);
                if (body == null) { sendError(ex, 400, "Body inválido."); return; }
                var result = tm.addMessage(id, user, getString(body, "content"));
                if (!result.success()) { sendError(ex, 400, result.error()); return; }
                sendJson(ex, msgJson(result.message()));
                return;
            }

            // PUT /api/tickets/{id}/assign
            if (method.equals("PUT") && sub.equals("assign")) {
                if (user == null) { sendError(ex, 401, "No autenticado."); return; }
                JsonObject body = parseBody(ex);
                if (body == null) { sendError(ex, 400, "Body inválido."); return; }
                var result = tm.assignTicket(id, user, getString(body, "tester"));
                if (!result.success()) { sendError(ex, 400, result.error()); return; }
                sendJson(ex, "{\"success\":true}");
                return;
            }

            // PUT /api/tickets/{id}/schedule
            if (method.equals("PUT") && sub.equals("schedule")) {
                if (user == null) { sendError(ex, 401, "No autenticado."); return; }
                JsonObject body = parseBody(ex);
                if (body == null) { sendError(ex, 400, "Body inválido."); return; }
                var result = tm.scheduleTicket(id, user, getString(body, "slotId"));
                if (!result.success()) { sendError(ex, 400, result.error()); return; }
                sendJson(ex, "{\"success\":true}");
                return;
            }

            // PUT /api/tickets/{id}/resolve
            if (method.equals("PUT") && sub.equals("resolve")) {
                if (user == null) { sendError(ex, 401, "No autenticado."); return; }
                JsonObject body = parseBody(ex);
                if (body == null) { sendError(ex, 400, "Body inválido."); return; }
                String resolution = getString(body, "resolution");
                String reason     = getString(body, "reason");
                String newTier    = getString(body, "newTier");
                int    newPoints  = body.has("newPoints") ? body.get("newPoints").getAsInt() : 0;
                var result = tm.resolveTicket(id, user, resolution, reason, newTier, newPoints);
                if (!result.success()) { sendError(ex, 400, result.error()); return; }
                sendJson(ex, "{\"success\":true}");
                return;
            }

            // PUT /api/tickets/{id}/status
            if (method.equals("PUT") && sub.equals("status")) {
                if (user == null) { sendError(ex, 401, "No autenticado."); return; }
                JsonObject body = parseBody(ex);
                if (body == null) { sendError(ex, 400, "Body inválido."); return; }
                TicketManager.Ticket t = tm.getTicket(id);
                if (t == null) { sendError(ex, 404, "Ticket no encontrado."); return; }
                if (TicketManager.ROLE_USER.equals(user.role)) { sendError(ex, 403, "Sin permisos."); return; }
                t.status = getString(body, "status");
                plugin.getTicketManager().saveTickets();
                sendJson(ex, "{\"success\":true}");
                return;
            }
        }

        sendError(ex, 404, "Endpoint no encontrado.");
    }

    // ── Schedules handler ────────────────────────────────────────────────────

    private void handleSchedules(HttpExchange ex) throws IOException {
        cors(ex);
        if (preflight(ex)) return;
        String method = ex.getRequestMethod().toUpperCase();
        TicketManager tm = plugin.getTicketManager();

        if (method.equals("GET")) {
            List<TicketManager.TesterSchedule> list = tm.allSchedules();
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(gson.toJson(list.get(i)));
            }
            sb.append("]");
            sendJson(ex, sb.toString());
            return;
        }

        if (method.equals("PUT")) {
            TicketManager.WebUser user = tm.getByToken(getToken(ex));
            if (user == null) { sendError(ex, 401, "No autenticado."); return; }
            JsonObject body = parseBody(ex);
            if (body == null) { sendError(ex, 400, "Body inválido."); return; }
            String testerName = getString(body, "testerName");
            com.google.gson.JsonArray slotsArr = body.has("slots") ? body.getAsJsonArray("slots") : new com.google.gson.JsonArray();
            List<TicketManager.SlotTemplate> slots = new ArrayList<>();
            for (var el : slotsArr) slots.add(gson.fromJson(el, TicketManager.SlotTemplate.class));
            var result = tm.updateSchedule(user, testerName, slots);
            if (!result.success()) { sendError(ex, 400, result.error()); return; }
            sendJson(ex, "{\"success\":true}");
            return;
        }

        sendError(ex, 405, "Método no permitido.");
    }

    // ── Admin handler ────────────────────────────────────────────────────────

    private void handleAdmin(HttpExchange ex) throws IOException {
        cors(ex);
        if (preflight(ex)) return;
        String path   = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod().toUpperCase();
        TicketManager tm   = plugin.getTicketManager();
        TicketManager.WebUser user = tm.getByToken(getToken(ex));

        if (user == null || TicketManager.ROLE_USER.equals(user.role)) {
            sendError(ex, 403, "Acceso denegado."); return;
        }

        // GET /api/admin/users
        if (method.equals("GET") && path.endsWith("/users")) {
            List<TicketManager.WebUser> list = tm.allUsers();
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (TicketManager.WebUser u : list) {
                if (!first) sb.append(","); first = false;
                sb.append(userJson(u));
            }
            sb.append("]");
            sendJson(ex, sb.toString());
            return;
        }

        // PUT /api/admin/users/{username}/role
        if (method.equals("PUT") && path.contains("/users/") && path.endsWith("/role")) {
            String[] parts = path.split("/", -1);
            if (parts.length >= 6) {
                String target = parts[4];
                JsonObject body = parseBody(ex);
                if (body == null) { sendError(ex, 400, "Body inválido."); return; }
                var result = tm.setRole(user, target, getString(body, "role"));
                if (!result.success()) { sendError(ex, 400, result.error()); return; }
                sendJson(ex, "{\"success\":true}");
                return;
            }
        }

        // GET /api/admin/tiers  - list of testable tiers + tier minPoints
        if (method.equals("GET") && path.endsWith("/tiers")) {
            StringBuilder sb = new StringBuilder("[");
            for (Tier t : Tier.values()) {
                if (t == Tier.UNRANKED) continue;
                if (sb.length() > 1) sb.append(",");
                sb.append("{\"name\":\"").append(esc(t.displayName)).append("\"")
                  .append(",\"minPoints\":").append(t.minPoints)
                  .append(",\"testable\":").append(Arrays.asList(tm.getTestableTiers()).contains(t.displayName))
                  .append("}");
            }
            sb.append("]");
            sendJson(ex, sb.toString());
            return;
        }

        sendError(ex, 404, "Endpoint no encontrado.");
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

    // ── New JSON serializers ──────────────────────────────────────────────────

    private String userJson(TicketManager.WebUser u) {
        if (u == null) return "null";
        return "{\"username\":\"" + esc(u.username) + "\""
                + ",\"uuid\":\""   + esc(u.uuid)     + "\""
                + ",\"role\":\""   + esc(u.role)      + "\""
                + ",\"createdAt\":" + u.createdAt
                + "}";
    }

    private String ticketJson(TicketManager.Ticket t, TicketManager.WebUser viewer) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"id\":\"").append(esc(t.id)).append("\"");
        sb.append(",\"username\":\"").append(esc(t.username)).append("\"");
        sb.append(",\"kit\":\"").append(esc(t.kit)).append("\"");
        sb.append(",\"targetTier\":\"").append(esc(t.targetTier)).append("\"");
        sb.append(",\"status\":\"").append(esc(t.status)).append("\"");
        sb.append(",\"assignedTester\":").append(t.assignedTester != null ? "\"" + esc(t.assignedTester) + "\"" : "null");
        sb.append(",\"createdAt\":").append(t.createdAt);
        sb.append(",\"resolvedAt\":").append(t.resolvedAt != null ? t.resolvedAt : "null");
        sb.append(",\"resolvedBy\":").append(t.resolvedBy != null ? "\"" + esc(t.resolvedBy) + "\"" : "null");
        sb.append(",\"resolution\":").append(t.resolution != null ? "\"" + esc(t.resolution) + "\"" : "null");
        sb.append(",\"rejectionReason\":").append(t.rejectionReason != null ? "\"" + esc(t.rejectionReason) + "\"" : "null");
        // Slot
        if (t.slot != null) {
            sb.append(",\"slot\":{\"id\":\"").append(esc(t.slot.id)).append("\"")
              .append(",\"testerName\":\"").append(esc(t.slot.testerName)).append("\"")
              .append(",\"dayOfWeek\":").append(t.slot.dayOfWeek)
              .append(",\"time\":\"").append(esc(t.slot.time)).append("\"")
              .append(",\"date\":\"").append(esc(t.slot.date)).append("\"}");
        } else {
            sb.append(",\"slot\":null");
        }
        // Messages
        sb.append(",\"messages\":[");
        boolean first = true;
        for (TicketManager.TicketMessage m : t.messages) {
            if (!first) sb.append(","); first = false;
            sb.append(msgJson(m));
        }
        sb.append("]}");
        return sb.toString();
    }

    private String msgJson(TicketManager.TicketMessage m) {
        return "{\"id\":\"" + esc(m.id) + "\""
                + ",\"author\":\"" + esc(m.author) + "\""
                + ",\"role\":\""   + esc(m.role)   + "\""
                + ",\"content\":\"" + esc(m.content) + "\""
                + ",\"ts\":"        + m.ts
                + "}";
    }

    // ── HTTP helpers ─────────────────────────────────────────────────────────

    private String getToken(HttpExchange ex) {
        // Check X-Token header first, then Authorization: Bearer ...
        String xToken = ex.getRequestHeaders().getFirst("X-Token");
        if (xToken != null && !xToken.isBlank()) return xToken.trim();
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) return auth.substring(7).trim();
        // Fall back to query param ?token=...
        String query = ex.getRequestURI().getQuery();
        if (query != null) {
            for (String p : query.split("&")) {
                if (p.startsWith("token=")) return p.substring(6);
            }
        }
        return null;
    }

    private String getClientIp(HttpExchange ex) {
        String forwarded = ex.getRequestHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
        return ex.getRemoteAddress().getAddress().getHostAddress();
    }

    private JsonObject parseBody(HttpExchange ex) {
        try {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            return JsonParser.parseString(body).getAsJsonObject();
        } catch (Exception e) { return null; }
    }

    private String getString(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return null;
        return obj.get(key).getAsString();
    }
}

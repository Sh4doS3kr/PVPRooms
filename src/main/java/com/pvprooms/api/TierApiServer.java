package com.pvprooms.api;

import com.pvprooms.PvPRoomsPro;
import com.pvprooms.managers.TierManager;
import com.pvprooms.model.Tier;
import com.pvprooms.model.TierTitle;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
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
 *   POST /api/auth/register    → iniciar registro (devuelve código)
 *   POST /api/auth/login       → login con username
 *   POST /api/auth/logout      → logout
 *   GET /api/auth/me           → datos del usuario logueado
 *   POST /api/tickets          → crear ticket
 *   GET /api/tickets           → listar tickets
 *   GET /api/tickets/{id}      → obtener ticket
 *   POST /api/tickets/{id}/message → enviar mensaje
 *   POST /api/tickets/{id}/status  → actualizar estado (staff)
 *   GET /api/tickets/timeslots → horarios disponibles
 *   GET /api/admin/users       → listar usuarios (admin)
 *   POST /api/admin/users/{name}/role → cambiar rol (admin)
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
            // Auth endpoints
            server.createContext("/api/auth/register", this::handleRegister);
            server.createContext("/api/auth/login",    this::handleLogin);
            server.createContext("/api/auth/logout",   this::handleLogout);
            server.createContext("/api/auth/me",       this::handleMe);
            // Ticket endpoints
            server.createContext("/api/tickets/timeslots", this::handleTimeSlots);
            server.createContext("/api/tickets",       this::handleTickets);
            // Admin endpoints
            server.createContext("/api/admin/users",   this::handleAdminUsers);
            server.createContext("/",                  this::handleRoot);
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
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
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

    // ══════════════════════════════════════════════════════════════════════
    // AUTH HANDLERS
    // ══════════════════════════════════════════════════════════════════════

    private void handleRegister(HttpExchange ex) throws IOException {
        cors(ex);
        if (preflight(ex)) return;
        if (!ex.getRequestMethod().equalsIgnoreCase("POST")) {
            sendError(ex, 405, "Method not allowed");
            return;
        }

        Map<String, String> body = parseJsonBody(ex);
        String username = body.get("username");
        if (username == null || username.isBlank()) {
            sendError(ex, 400, "Username requerido");
            return;
        }

        // Check if already registered
        if (plugin.getTicketManager().getUser(username) != null) {
            sendError(ex, 409, "Usuario ya registrado. Usa 'Iniciar Sesión'.");
            return;
        }

        // Start registration - generate code
        String code = plugin.getTicketManager().startRegistration(username);
        if (code == null) {
            sendError(ex, 409, "Usuario ya registrado");
            return;
        }

        sendJson(ex, "{\"success\":true,\"code\":\"" + code + "\",\"message\":\"Entra al servidor y usa: /verificar " + code + "\"}");
    }

    private void handleLogin(HttpExchange ex) throws IOException {
        cors(ex);
        if (preflight(ex)) return;
        if (!ex.getRequestMethod().equalsIgnoreCase("POST")) {
            sendError(ex, 405, "Method not allowed");
            return;
        }

        Map<String, String> body = parseJsonBody(ex);
        String username = body.get("username");
        if (username == null || username.isBlank()) {
            sendError(ex, 400, "Username requerido");
            return;
        }

        // Check if user exists
        TicketManager.WebUser user = plugin.getTicketManager().getUser(username);
        if (user == null) {
            sendError(ex, 404, "Usuario no registrado. Regístrate primero.");
            return;
        }

        // Create session
        String token = plugin.getTicketManager().login(username);
        sendJson(ex, "{\"success\":true,\"token\":\"" + token + "\",\"user\":" + userJson(user) + "}");
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

    private void handleMe(HttpExchange ex) throws IOException {
        cors(ex);
        if (preflight(ex)) return;

        String token = getAuthToken(ex);
        TicketManager.WebUser user = plugin.getTicketManager().getUserBySession(token);
        if (user == null) {
            sendError(ex, 401, "No autenticado");
            return;
        }

        sendJson(ex, userJson(user));
    }

    // ══════════════════════════════════════════════════════════════════════
    // TICKET HANDLERS
    // ══════════════════════════════════════════════════════════════════════

    private void handleTimeSlots(HttpExchange ex) throws IOException {
        cors(ex);
        if (preflight(ex)) return;

        List<TicketManager.TimeSlot> slots = plugin.getTicketManager().getTimeSlots();
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < slots.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("{\"value\":\"").append(esc(slots.get(i).value))
              .append("\",\"display\":\"").append(esc(slots.get(i).display)).append("\"}");
        }
        sb.append("]");
        sendJson(ex, sb.toString());
    }

    private void handleTickets(HttpExchange ex) throws IOException {
        cors(ex);
        if (preflight(ex)) return;

        String token = getAuthToken(ex);
        TicketManager.WebUser user = plugin.getTicketManager().getUserBySession(token);
        
        String path = ex.getRequestURI().getPath();
        String[] parts = path.split("/");

        // POST /api/tickets - create new ticket
        if (ex.getRequestMethod().equalsIgnoreCase("POST") && parts.length == 3) {
            if (user == null) { sendError(ex, 401, "No autenticado"); return; }
            
            Map<String, String> body = parseJsonBody(ex);
            String kit = body.get("kit");
            String date = body.get("date");
            String timeSlot = body.get("timeSlot");
            String notes = body.getOrDefault("notes", "");

            if (kit == null || date == null || timeSlot == null) {
                sendError(ex, 400, "Faltan campos requeridos (kit, date, timeSlot)");
                return;
            }

            TicketManager.Ticket ticket = plugin.getTicketManager().createTicket(
                    user.name, kit, date, timeSlot, notes);
            if (ticket == null) {
                sendError(ex, 500, "Error al crear ticket");
                return;
            }

            sendJson(ex, ticketJson(ticket));
            return;
        }

        // GET /api/tickets - list tickets
        if (ex.getRequestMethod().equalsIgnoreCase("GET") && parts.length == 3) {
            if (user == null) { sendError(ex, 401, "No autenticado"); return; }

            List<TicketManager.Ticket> tickets;
            if (user.role.equals("admin") || user.role.equals("tester")) {
                tickets = plugin.getTicketManager().getAllTickets();
            } else {
                tickets = plugin.getTicketManager().getTicketsForUser(user.name);
            }

            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < tickets.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(ticketJson(tickets.get(i)));
            }
            sb.append("]");
            sendJson(ex, sb.toString());
            return;
        }

        // /api/tickets/{id}...
        if (parts.length >= 4) {
            String ticketId = parts[3];
            TicketManager.Ticket ticket = plugin.getTicketManager().getTicket(ticketId);
            
            if (ticket == null) {
                sendError(ex, 404, "Ticket no encontrado");
                return;
            }

            // Check access
            if (user == null) { sendError(ex, 401, "No autenticado"); return; }
            boolean isOwner = ticket.username.equalsIgnoreCase(user.name);
            boolean isStaff = user.role.equals("admin") || user.role.equals("tester");
            if (!isOwner && !isStaff) {
                sendError(ex, 403, "Sin acceso a este ticket");
                return;
            }

            // GET /api/tickets/{id}
            if (ex.getRequestMethod().equalsIgnoreCase("GET") && parts.length == 4) {
                sendJson(ex, ticketJson(ticket));
                return;
            }

            // POST /api/tickets/{id}/message
            if (ex.getRequestMethod().equalsIgnoreCase("POST") && parts.length == 5 && parts[4].equals("message")) {
                Map<String, String> body = parseJsonBody(ex);
                String message = body.get("message");
                if (message == null || message.isBlank()) {
                    sendError(ex, 400, "Mensaje requerido");
                    return;
                }

                plugin.getTicketManager().addMessage(ticketId, user.name, message, isStaff);
                sendJson(ex, "{\"success\":true}");
                return;
            }

            // POST /api/tickets/{id}/status (staff only)
            if (ex.getRequestMethod().equalsIgnoreCase("POST") && parts.length == 5 && parts[4].equals("status")) {
                if (!isStaff) { sendError(ex, 403, "Solo staff"); return; }
                
                Map<String, String> body = parseJsonBody(ex);
                String status = body.get("status");
                if (status == null) {
                    sendError(ex, 400, "Status requerido");
                    return;
                }

                plugin.getTicketManager().updateTicketStatus(ticketId, status, user.name);
                sendJson(ex, "{\"success\":true}");
                return;
            }
        }

        sendError(ex, 404, "Endpoint no encontrado");
    }

    // ══════════════════════════════════════════════════════════════════════
    // ADMIN HANDLERS
    // ══════════════════════════════════════════════════════════════════════

    private void handleAdminUsers(HttpExchange ex) throws IOException {
        cors(ex);
        if (preflight(ex)) return;

        String token = getAuthToken(ex);
        TicketManager.WebUser user = plugin.getTicketManager().getUserBySession(token);
        
        if (user == null || !user.role.equals("admin")) {
            sendError(ex, 403, "Solo administradores");
            return;
        }

        String path = ex.getRequestURI().getPath();
        String[] parts = path.split("/");

        // GET /api/admin/users - list all users
        if (ex.getRequestMethod().equalsIgnoreCase("GET") && parts.length == 4) {
            List<TicketManager.WebUser> users = plugin.getTicketManager().getAllUsers();
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < users.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(userJson(users.get(i)));
            }
            sb.append("]");
            sendJson(ex, sb.toString());
            return;
        }

        // POST /api/admin/users/{name}/role
        if (ex.getRequestMethod().equalsIgnoreCase("POST") && parts.length == 6 && parts[5].equals("role")) {
            String targetUser = parts[4];
            Map<String, String> body = parseJsonBody(ex);
            String newRole = body.get("role");
            
            if (newRole == null || (!newRole.equals("user") && !newRole.equals("tester") && !newRole.equals("admin"))) {
                sendError(ex, 400, "Rol inválido (user, tester, admin)");
                return;
            }

            if (plugin.getTicketManager().setUserRole(targetUser, newRole)) {
                sendJson(ex, "{\"success\":true}");
            } else {
                sendError(ex, 404, "Usuario no encontrado");
            }
            return;
        }

        sendError(ex, 404, "Endpoint no encontrado");
    }

    // ══════════════════════════════════════════════════════════════════════
    // JSON HELPERS
    // ══════════════════════════════════════════════════════════════════════

    private String userJson(TicketManager.WebUser user) {
        return "{\"uuid\":\"" + user.uuid + "\""
                + ",\"name\":\"" + esc(user.name) + "\""
                + ",\"role\":\"" + esc(user.role) + "\"}";
    }

    private String ticketJson(TicketManager.Ticket t) {
        StringBuilder msgs = new StringBuilder("[");
        for (int i = 0; i < t.messages.size(); i++) {
            if (i > 0) msgs.append(",");
            TicketManager.ChatMessage m = t.messages.get(i);
            msgs.append("{\"author\":\"").append(esc(m.author))
                .append("\",\"message\":\"").append(esc(m.message))
                .append("\",\"isStaff\":").append(m.isStaff)
                .append(",\"timestamp\":").append(m.timestamp).append("}");
        }
        msgs.append("]");

        return "{\"id\":\"" + esc(t.id) + "\""
                + ",\"username\":\"" + esc(t.username) + "\""
                + ",\"uuid\":\"" + t.uuid + "\""
                + ",\"kit\":\"" + esc(t.kit) + "\""
                + ",\"date\":\"" + esc(t.date) + "\""
                + ",\"timeSlot\":\"" + esc(t.timeSlot) + "\""
                + ",\"notes\":\"" + esc(t.notes) + "\""
                + ",\"status\":\"" + esc(t.status) + "\""
                + ",\"assignedTo\":\"" + esc(t.assignedTo) + "\""
                + ",\"createdAt\":" + t.createdAt
                + ",\"messages\":" + msgs + "}";
    }

    private Map<String, String> parseJsonBody(HttpExchange ex) throws IOException {
        Map<String, String> result = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(ex.getRequestBody(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            String json = sb.toString().trim();
            
            // Simple JSON parser for flat objects
            if (json.startsWith("{") && json.endsWith("}")) {
                json = json.substring(1, json.length() - 1);
                String[] pairs = json.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
                for (String pair : pairs) {
                    String[] kv = pair.split(":", 2);
                    if (kv.length == 2) {
                        String key = kv[0].trim().replace("\"", "");
                        String value = kv[1].trim();
                        if (value.startsWith("\"") && value.endsWith("\"")) {
                            value = value.substring(1, value.length() - 1);
                        }
                        result.put(key, value);
                    }
                }
            }
        }
        return result;
    }

    private String getAuthToken(HttpExchange ex) {
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7);
        }
        return null;
    }
}

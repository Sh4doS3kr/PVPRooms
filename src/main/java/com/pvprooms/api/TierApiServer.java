package com.pvprooms.api;

import com.pvprooms.PvPRoomsPro;
import com.pvprooms.managers.TierManager;
import com.pvprooms.managers.TicketManager;
import com.pvprooms.managers.TicketManager.*;
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
import java.util.stream.Collectors;

/**
 * Servidor HTTP embebido que expone la API de tiers, tickets y sirve la web.
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
            // Existing endpoints
            server.createContext("/api/top",    this::handleTop);
            server.createContext("/api/kits",   this::handleKits);
            server.createContext("/api/player", this::handlePlayer);
            server.createContext("/api/stats",  this::handleStats);
            
            // Auth endpoints
            server.createContext("/api/auth/register", this::handleRegister);
            server.createContext("/api/auth/login", this::handleLogin);
            server.createContext("/api/auth/logout", this::handleLogout);
            server.createContext("/api/auth/me", this::handleMe);
            server.createContext("/api/auth/check-online", this::handleCheckOnline);
            
            // Ticket endpoints
            server.createContext("/api/tickets/create", this::handleTicketCreate);
            server.createContext("/api/tickets/list", this::handleTicketList);
            server.createContext("/api/tickets/get", this::handleTicketGet);
            server.createContext("/api/tickets/message", this::handleTicketMessage);
            server.createContext("/api/tickets/schedule", this::handleTicketSchedule);
            server.createContext("/api/tickets/complete", this::handleTicketComplete);
            server.createContext("/api/tickets/slots", this::handleTicketSlots);
            
            // Admin endpoints
            server.createContext("/api/admin/users", this::handleAdminUsers);
            server.createContext("/api/admin/tickets", this::handleAdminTickets);
            server.createContext("/api/admin/set-tier", this::handleAdminSetTier);
            server.createContext("/api/admin/set-role", this::handleAdminSetRole);
            server.createContext("/api/admin/assign", this::handleAdminAssign);
            
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

    private void corsPost(HttpExchange ex) {
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        ex.getResponseHeaders().set("Access-Control-Allow-Credentials", "true");
    }

    private void cors(HttpExchange ex) {
        corsPost(ex);
    }

    private boolean preflight(HttpExchange ex) throws IOException {
        if (ex.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
            corsPost(ex);
            ex.sendResponseHeaders(204, -1);
            return true;
        }
        return false;
    }

    private String getClientIP(HttpExchange ex) {
        String xff = ex.getRequestHeaders().getFirst("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return ex.getRemoteAddress().getAddress().getHostAddress();
    }

    private String getSessionFromHeader(HttpExchange ex) {
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7);
        }
        return null;
    }

    private Map<String, String> parseBody(HttpExchange ex) throws IOException {
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> params = new HashMap<>();
        if (body.isBlank()) return params;
        
        // Handle JSON body
        if (body.startsWith("{")) {
            body = body.substring(1, body.length() - 1);
            for (String pair : body.split(",")) {
                String[] kv = pair.split(":", 2);
                if (kv.length == 2) {
                    String key = kv[0].trim().replaceAll("^\"|\"$", "");
                    String val = kv[1].trim().replaceAll("^\"|\"$", "");
                    params.put(key, val);
                }
            }
        } else {
            // URL encoded
            for (String pair : body.split("&")) {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2) {
                    params.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                               URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
                }
            }
        }
        return params;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // AUTH HANDLERS
    // ══════════════════════════════════════════════════════════════════════════

    private void handleRegister(HttpExchange ex) throws IOException {
        corsPost(ex);
        if (preflight(ex)) return;
        if (!ex.getRequestMethod().equalsIgnoreCase("POST")) {
            sendError(ex, 405, "Method not allowed"); return;
        }

        Map<String, String> body = parseBody(ex);
        String username = body.get("username");
        if (username == null || username.isBlank() || username.length() < 3 || username.length() > 16) {
            sendError(ex, 400, "Nombre de usuario inválido (3-16 caracteres)"); return;
        }

        String ip = getClientIP(ex);
        String result = plugin.getTicketManager().startRegistration(username, ip);

        if (result == null) {
            sendError(ex, 429, "Demasiadas solicitudes. Espera unos segundos."); return;
        }
        if (result.equals("EXISTS")) {
            sendError(ex, 409, "Este usuario ya está registrado"); return;
        }

        sendJson(ex, "{\"success\":true,\"code\":\"" + result + "\",\"message\":\"Entra al servidor de Minecraft y usa /verify " + result + "\"}");
    }

    private void handleLogin(HttpExchange ex) throws IOException {
        corsPost(ex);
        if (preflight(ex)) return;
        if (!ex.getRequestMethod().equalsIgnoreCase("POST")) {
            sendError(ex, 405, "Method not allowed"); return;
        }

        Map<String, String> body = parseBody(ex);
        String username = body.get("username");
        if (username == null || username.isBlank()) {
            sendError(ex, 400, "Nombre de usuario requerido"); return;
        }

        String ip = getClientIP(ex);
        String result = plugin.getTicketManager().login(username, ip);

        if (result == null) {
            sendError(ex, 401, "Usuario no encontrado o no verificado"); return;
        }
        if (result.startsWith("VERIFY:")) {
            String code = result.substring(7);
            sendJson(ex, "{\"needsVerify\":true,\"code\":\"" + code + "\",\"message\":\"Debes estar online en el servidor. Usa /verify " + code + "\"}");
            return;
        }

        WebUser user = plugin.getTicketManager().getUserBySession(result);
        sendJson(ex, "{\"success\":true,\"session\":\"" + result + "\",\"user\":" + userJson(user) + "}");
    }

    private void handleLogout(HttpExchange ex) throws IOException {
        corsPost(ex);
        if (preflight(ex)) return;
        String session = getSessionFromHeader(ex);
        if (session != null) {
            plugin.getTicketManager().logout(session);
        }
        sendJson(ex, "{\"success\":true}");
    }

    private void handleMe(HttpExchange ex) throws IOException {
        corsPost(ex);
        if (preflight(ex)) return;
        String session = getSessionFromHeader(ex);
        if (session == null) {
            sendError(ex, 401, "No autenticado"); return;
        }
        WebUser user = plugin.getTicketManager().getUserBySession(session);
        if (user == null) {
            sendError(ex, 401, "Sesión inválida"); return;
        }
        sendJson(ex, userJson(user));
    }

    private void handleCheckOnline(HttpExchange ex) throws IOException {
        corsPost(ex);
        if (preflight(ex)) return;
        String[] parts = ex.getRequestURI().getPath().split("/");
        String username = parts.length > 4 ? parts[4] : null;
        if (username == null) {
            sendError(ex, 400, "Username required"); return;
        }
        boolean online = plugin.getServer().getPlayerExact(username) != null;
        sendJson(ex, "{\"online\":" + online + "}");
    }

    private String userJson(WebUser user) {
        return "{\"username\":\"" + esc(user.username) + "\""
             + ",\"role\":\"" + esc(user.role) + "\""
             + ",\"verified\":" + user.verified
             + ",\"createdAt\":" + user.createdAt + "}";
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TICKET HANDLERS
    // ══════════════════════════════════════════════════════════════════════════

    private void handleTicketCreate(HttpExchange ex) throws IOException {
        corsPost(ex);
        if (preflight(ex)) return;
        if (!ex.getRequestMethod().equalsIgnoreCase("POST")) {
            sendError(ex, 405, "Method not allowed"); return;
        }

        String session = getSessionFromHeader(ex);
        WebUser user = session != null ? plugin.getTicketManager().getUserBySession(session) : null;
        if (user == null) {
            sendError(ex, 401, "No autenticado"); return;
        }

        Map<String, String> body = parseBody(ex);
        String kit = body.get("kit");
        String targetTier = body.get("targetTier");

        if (kit == null || targetTier == null) {
            sendError(ex, 400, "Kit y tier objetivo requeridos"); return;
        }

        String ip = getClientIP(ex);
        Ticket ticket = plugin.getTicketManager().createTicket(user.username, kit, targetTier, ip);

        if (ticket == null) {
            sendError(ex, 409, "Ya tienes un ticket pendiente para este kit o ya solicitaste este tier"); return;
        }

        sendJson(ex, "{\"success\":true,\"ticket\":" + ticketJson(ticket) + "}");
    }

    private void handleTicketList(HttpExchange ex) throws IOException {
        corsPost(ex);
        if (preflight(ex)) return;

        String session = getSessionFromHeader(ex);
        WebUser user = session != null ? plugin.getTicketManager().getUserBySession(session) : null;
        if (user == null) {
            sendError(ex, 401, "No autenticado"); return;
        }

        List<Ticket> tickets;
        if ("admin".equals(user.role)) {
            tickets = plugin.getTicketManager().getAllTickets();
        } else if ("tester".equals(user.role)) {
            tickets = plugin.getTicketManager().getTicketsForTester(user.username);
            // Also include pending unassigned tickets
            tickets.addAll(plugin.getTicketManager().getAllPendingTickets().stream()
                .filter(t -> t.assignedTester == null)
                .collect(Collectors.toList()));
        } else {
            tickets = plugin.getTicketManager().getTicketsForUser(user.username);
        }

        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < tickets.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(ticketJson(tickets.get(i)));
        }
        sb.append("]");
        sendJson(ex, sb.toString());
    }

    private void handleTicketGet(HttpExchange ex) throws IOException {
        corsPost(ex);
        if (preflight(ex)) return;

        String session = getSessionFromHeader(ex);
        WebUser user = session != null ? plugin.getTicketManager().getUserBySession(session) : null;
        if (user == null) {
            sendError(ex, 401, "No autenticado"); return;
        }

        String[] parts = ex.getRequestURI().getPath().split("/");
        String ticketId = parts.length > 4 ? parts[4] : null;
        if (ticketId == null) {
            sendError(ex, 400, "ID de ticket requerido"); return;
        }

        Ticket ticket = plugin.getTicketManager().getTicket(ticketId);
        if (ticket == null) {
            sendError(ex, 404, "Ticket no encontrado"); return;
        }

        // Check access
        if (!"admin".equals(user.role) && !"tester".equals(user.role) 
            && !ticket.username.equalsIgnoreCase(user.username)) {
            sendError(ex, 403, "Sin acceso a este ticket"); return;
        }

        sendJson(ex, ticketJson(ticket));
    }

    private void handleTicketMessage(HttpExchange ex) throws IOException {
        corsPost(ex);
        if (preflight(ex)) return;
        if (!ex.getRequestMethod().equalsIgnoreCase("POST")) {
            sendError(ex, 405, "Method not allowed"); return;
        }

        String session = getSessionFromHeader(ex);
        WebUser user = session != null ? plugin.getTicketManager().getUserBySession(session) : null;
        if (user == null) {
            sendError(ex, 401, "No autenticado"); return;
        }

        Map<String, String> body = parseBody(ex);
        String ticketId = body.get("ticketId");
        String message = body.get("message");

        if (ticketId == null || message == null || message.isBlank()) {
            sendError(ex, 400, "ticketId y message requeridos"); return;
        }

        Ticket ticket = plugin.getTicketManager().getTicket(ticketId);
        if (ticket == null) {
            sendError(ex, 404, "Ticket no encontrado"); return;
        }

        // Check access
        if (!"admin".equals(user.role) && !"tester".equals(user.role)
            && !ticket.username.equalsIgnoreCase(user.username)) {
            sendError(ex, 403, "Sin acceso a este ticket"); return;
        }

        plugin.getTicketManager().addMessage(ticketId, user.username, user.role, message);
        sendJson(ex, "{\"success\":true}");
    }

    private void handleTicketSchedule(HttpExchange ex) throws IOException {
        corsPost(ex);
        if (preflight(ex)) return;
        if (!ex.getRequestMethod().equalsIgnoreCase("POST")) {
            sendError(ex, 405, "Method not allowed"); return;
        }

        String session = getSessionFromHeader(ex);
        WebUser user = session != null ? plugin.getTicketManager().getUserBySession(session) : null;
        if (user == null || (!"admin".equals(user.role) && !"tester".equals(user.role))) {
            sendError(ex, 403, "Sin permisos"); return;
        }

        Map<String, String> body = parseBody(ex);
        String ticketId = body.get("ticketId");
        String date = body.get("date");
        String time = body.get("time");

        if (ticketId == null || date == null || time == null) {
            sendError(ex, 400, "ticketId, date y time requeridos"); return;
        }

        plugin.getTicketManager().scheduleTicket(ticketId, date, time);
        sendJson(ex, "{\"success\":true}");
    }

    private void handleTicketComplete(HttpExchange ex) throws IOException {
        corsPost(ex);
        if (preflight(ex)) return;
        if (!ex.getRequestMethod().equalsIgnoreCase("POST")) {
            sendError(ex, 405, "Method not allowed"); return;
        }

        String session = getSessionFromHeader(ex);
        WebUser user = session != null ? plugin.getTicketManager().getUserBySession(session) : null;
        if (user == null || (!"admin".equals(user.role) && !"tester".equals(user.role))) {
            sendError(ex, 403, "Sin permisos"); return;
        }

        Map<String, String> body = parseBody(ex);
        String ticketId = body.get("ticketId");
        String result = body.get("result"); // "approved" or "denied"
        String newTier = body.get("newTier");

        if (ticketId == null || result == null) {
            sendError(ex, 400, "ticketId y result requeridos"); return;
        }

        plugin.getTicketManager().completeTicket(ticketId, result, newTier);
        sendJson(ex, "{\"success\":true}");
    }

    private void handleTicketSlots(HttpExchange ex) throws IOException {
        corsPost(ex);
        if (preflight(ex)) return;

        String[] parts = ex.getRequestURI().getPath().split("/");
        String tester = parts.length > 4 ? parts[4] : "420Sleeptyx";
        String date = parts.length > 5 ? parts[5] : java.time.LocalDate.now().toString();

        List<TimeSlot> slots = plugin.getTicketManager().getAvailableSlots(tester, date);
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < slots.size(); i++) {
            if (i > 0) sb.append(",");
            TimeSlot s = slots.get(i);
            sb.append("{\"start\":\"").append(esc(s.start)).append("\"")
              .append(",\"end\":\"").append(esc(s.end)).append("\"}");
        }
        sb.append("]");
        sendJson(ex, sb.toString());
    }

    private String ticketJson(Ticket t) {
        StringBuilder msgs = new StringBuilder("[");
        for (int i = 0; i < t.messages.size(); i++) {
            if (i > 0) msgs.append(",");
            ChatMessage m = t.messages.get(i);
            msgs.append("{\"sender\":\"").append(esc(m.sender)).append("\"")
                .append(",\"role\":\"").append(esc(m.role)).append("\"")
                .append(",\"message\":\"").append(esc(m.message)).append("\"")
                .append(",\"timestamp\":").append(m.timestamp).append("}");
        }
        msgs.append("]");

        return "{\"id\":\"" + esc(t.id) + "\""
             + ",\"username\":\"" + esc(t.username) + "\""
             + ",\"kit\":\"" + esc(t.kit) + "\""
             + ",\"targetTier\":\"" + esc(t.targetTier) + "\""
             + ",\"status\":\"" + esc(t.status) + "\""
             + ",\"assignedTester\":" + (t.assignedTester != null ? "\"" + esc(t.assignedTester) + "\"" : "null")
             + ",\"scheduledDate\":" + (t.scheduledDate != null ? "\"" + esc(t.scheduledDate) + "\"" : "null")
             + ",\"scheduledTime\":" + (t.scheduledTime != null ? "\"" + esc(t.scheduledTime) + "\"" : "null")
             + ",\"createdAt\":" + t.createdAt
             + ",\"updatedAt\":" + t.updatedAt
             + ",\"result\":" + (t.result != null ? "\"" + esc(t.result) + "\"" : "null")
             + ",\"newTier\":" + (t.newTier != null ? "\"" + esc(t.newTier) + "\"" : "null")
             + ",\"messages\":" + msgs + "}";
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ADMIN HANDLERS
    // ══════════════════════════════════════════════════════════════════════════

    private void handleAdminUsers(HttpExchange ex) throws IOException {
        corsPost(ex);
        if (preflight(ex)) return;

        String session = getSessionFromHeader(ex);
        WebUser user = session != null ? plugin.getTicketManager().getUserBySession(session) : null;
        if (user == null || !"admin".equals(user.role)) {
            sendError(ex, 403, "Solo administradores"); return;
        }

        List<WebUser> users = plugin.getTicketManager().getAllUsers();
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < users.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(userJson(users.get(i)));
        }
        sb.append("]");
        sendJson(ex, sb.toString());
    }

    private void handleAdminTickets(HttpExchange ex) throws IOException {
        corsPost(ex);
        if (preflight(ex)) return;

        String session = getSessionFromHeader(ex);
        WebUser user = session != null ? plugin.getTicketManager().getUserBySession(session) : null;
        if (user == null || !"admin".equals(user.role)) {
            sendError(ex, 403, "Solo administradores"); return;
        }

        List<Ticket> tickets = plugin.getTicketManager().getAllTickets();
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < tickets.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(ticketJson(tickets.get(i)));
        }
        sb.append("]");
        sendJson(ex, sb.toString());
    }

    private void handleAdminSetTier(HttpExchange ex) throws IOException {
        corsPost(ex);
        if (preflight(ex)) return;
        if (!ex.getRequestMethod().equalsIgnoreCase("POST")) {
            sendError(ex, 405, "Method not allowed"); return;
        }

        String session = getSessionFromHeader(ex);
        WebUser user = session != null ? plugin.getTicketManager().getUserBySession(session) : null;
        if (user == null || !"admin".equals(user.role)) {
            sendError(ex, 403, "Solo administradores"); return;
        }

        Map<String, String> body = parseBody(ex);
        String username = body.get("username");
        String kit = body.get("kit");
        String tier = body.get("tier");

        if (username == null || kit == null || tier == null) {
            sendError(ex, 400, "username, kit y tier requeridos"); return;
        }

        boolean success = plugin.getTicketManager().setUserTier(username, kit, tier);
        if (!success) {
            sendError(ex, 400, "Error al establecer tier"); return;
        }
        sendJson(ex, "{\"success\":true}");
    }

    private void handleAdminSetRole(HttpExchange ex) throws IOException {
        corsPost(ex);
        if (preflight(ex)) return;
        if (!ex.getRequestMethod().equalsIgnoreCase("POST")) {
            sendError(ex, 405, "Method not allowed"); return;
        }

        String session = getSessionFromHeader(ex);
        WebUser user = session != null ? plugin.getTicketManager().getUserBySession(session) : null;
        if (user == null || !"admin".equals(user.role)) {
            sendError(ex, 403, "Solo administradores"); return;
        }

        Map<String, String> body = parseBody(ex);
        String username = body.get("username");
        String role = body.get("role");

        if (username == null || role == null) {
            sendError(ex, 400, "username y role requeridos"); return;
        }

        boolean success = plugin.getTicketManager().setUserRole(username, role);
        if (!success) {
            sendError(ex, 404, "Usuario no encontrado"); return;
        }
        sendJson(ex, "{\"success\":true}");
    }

    private void handleAdminAssign(HttpExchange ex) throws IOException {
        corsPost(ex);
        if (preflight(ex)) return;
        if (!ex.getRequestMethod().equalsIgnoreCase("POST")) {
            sendError(ex, 405, "Method not allowed"); return;
        }

        String session = getSessionFromHeader(ex);
        WebUser user = session != null ? plugin.getTicketManager().getUserBySession(session) : null;
        if (user == null || (!"admin".equals(user.role) && !"tester".equals(user.role))) {
            sendError(ex, 403, "Sin permisos"); return;
        }

        Map<String, String> body = parseBody(ex);
        String ticketId = body.get("ticketId");
        String tester = body.get("tester");

        if (ticketId == null || tester == null) {
            sendError(ex, 400, "ticketId y tester requeridos"); return;
        }

        boolean success = plugin.getTicketManager().assignTester(ticketId, tester);
        if (!success) {
            sendError(ex, 404, "Ticket no encontrado"); return;
        }
        sendJson(ex, "{\"success\":true}");
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

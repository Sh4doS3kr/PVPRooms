package com.pvprooms.api;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.pvprooms.PvPRoomsPro;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;

public class TicketManager {

    private final PvPRoomsPro plugin;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private final Map<String, WebUser>        users     = new ConcurrentHashMap<>();
    private final Map<String, Ticket>         tickets   = new ConcurrentHashMap<>();
    private final Map<String, TesterSchedule> schedules = new ConcurrentHashMap<>();
    private final Map<String, PendingCode>    pending   = new ConcurrentHashMap<>();
    private final Map<String, List<Long>>     rateLims  = new ConcurrentHashMap<>();

    public static final String ADMIN_NAME  = "Sh4doS3kr";
    public static final String ROLE_ADMIN  = "admin";
    public static final String ROLE_TESTER = "tester";
    public static final String ROLE_USER   = "user";

    private static final String[] TESTABLE_TIERS = {"HT3","LT2","HT2","LT1","HT1"};

    // ── Inner data classes ───────────────────────────────────────────────────

    public static class WebUser {
        public String username;
        public String uuid;
        public String role = ROLE_USER;
        public List<SessionEntry> sessions = new ArrayList<>();
        public long   createdAt;
        public String registeredIp;
    }

    public static class SessionEntry {
        public String token;
        public String ip;
        public long   createdAt;
        public long   lastUsed;
    }

    public static class PendingCode {
        public String username;
        public String code;
        public String ip;
        public long   createdAt;
        public long   expiresAt;
    }

    public static class Ticket {
        public String id;
        public String username;
        public String uuid;
        public String kit;
        public String targetTier;
        public String status = "pending"; // pending|scheduled|testing|completed|rejected
        public String assignedTester;
        public ScheduledSlot slot;
        public List<TicketMessage> messages = new ArrayList<>();
        public String ip;
        public long   createdAt;
        public Long   resolvedAt;
        public String resolvedBy;
        public String resolution;
        public String rejectionReason;
    }

    public static class ScheduledSlot {
        public String id;
        public String testerName;
        public int    dayOfWeek;
        public String time;
        public String date;
    }

    public static class TicketMessage {
        public String id;
        public String author;
        public String role;
        public String content;
        public long   ts;
    }

    public static class TesterSchedule {
        public String           testerName;
        public List<SlotTemplate> slots = new ArrayList<>();
    }

    public static class SlotTemplate {
        public String  id;
        public int     dayOfWeek;
        public String  time;
        public boolean available = true;
        public String  timezone  = "Europe/Madrid";
        public String  note;
    }

    public record RegisterResult(boolean success, String error, String username) {}
    public record VerifyResult  (boolean success, String error, String token, WebUser user) {}
    public record TicketResult  (boolean success, String error, Ticket ticket) {}
    public record MsgResult     (boolean success, String error, TicketMessage message) {}
    public record StatusResult  (boolean success, String error) {}

    // ── Constructor ──────────────────────────────────────────────────────────

    public TicketManager(PvPRoomsPro plugin) {
        this.plugin = plugin;
        load();
        ensureAdmin();
        ensureDefaultSchedule();
    }

    private void ensureAdmin() {
        users.compute(ADMIN_NAME.toLowerCase(), (k, u) -> {
            if (u == null) {
                u = new WebUser();
                u.username  = ADMIN_NAME;
                u.createdAt = System.currentTimeMillis();
            }
            u.role = ROLE_ADMIN;
            UUID uuid = resolveUUID(ADMIN_NAME);
            if (uuid != null) u.uuid = uuid.toString();
            return u;
        });
        save();
    }

    private void ensureDefaultSchedule() {
        if (schedules.containsKey("420Sleeptyx")) return;
        TesterSchedule ts = new TesterSchedule();
        ts.testerName = "420Sleeptyx";
        int[][] weekdays = {{1,20},{1,21},{2,20},{2,21},{3,20},{3,21},{4,20},{4,21},{5,20},{5,21}};
        int[][] weekend  = {{6,18},{6,19},{6,20},{6,21},{7,18},{7,19},{7,20},{7,21}};
        for (int[] d : weekdays) addSlot(ts, d[0], String.format("%02d:00", d[1]));
        for (int[] d : weekend)  addSlot(ts, d[0], String.format("%02d:00", d[1]));
        schedules.put("420Sleeptyx", ts);

        // Make 420Sleeptyx a tester
        users.compute("420sleeptyx", (k, u) -> {
            if (u == null) {
                u = new WebUser();
                u.username  = "420Sleeptyx";
                u.createdAt = System.currentTimeMillis();
                UUID uuid = resolveUUID("420Sleeptyx");
                if (uuid != null) u.uuid = uuid.toString();
            }
            if (!ROLE_ADMIN.equals(u.role)) u.role = ROLE_TESTER;
            return u;
        });
        save();
        saveSchedules();
    }

    private void addSlot(TesterSchedule ts, int day, String time) {
        SlotTemplate s = new SlotTemplate();
        s.id        = ts.testerName + "-" + day + "-" + time.replace(":", "");
        s.dayOfWeek = day;
        s.time      = time;
        s.available = true;
        ts.slots.add(s);
    }

    // ── Auth ─────────────────────────────────────────────────────────────────

    public RegisterResult register(String username, String ip) {
        if (!rateCheck(ip, "reg", 3, 3_600_000))
            return new RegisterResult(false, "Demasiados intentos. Espera 1 hora.", null);

        if (username == null || !username.matches("[a-zA-Z0-9_]{3,16}"))
            return new RegisterResult(false, "Nombre inválido (3-16 caracteres, solo letras, números y _).", null);

        UUID uuid = resolveUUID(username);
        if (uuid == null)
            return new RegisterResult(false, "Jugador no encontrado en el servidor. Debes haber jugado al menos una vez.", null);

        String key = username.toLowerCase();
        WebUser user = users.computeIfAbsent(key, k -> {
            WebUser u = new WebUser();
            u.username     = username;
            u.uuid         = uuid.toString();
            u.registeredIp = ip;
            u.createdAt    = System.currentTimeMillis();
            return u;
        });
        // Always update uuid (might have changed)
        user.uuid = uuid.toString();

        String code = String.format("%09d", new SecureRandom().nextInt(1_000_000_000));
        PendingCode pc = new PendingCode();
        pc.username  = username;
        pc.code      = code;
        pc.ip        = ip;
        pc.createdAt = System.currentTimeMillis();
        pc.expiresAt = pc.createdAt + 5 * 60_000;
        pending.put(key, pc);

        sendCode(username, code);
        save();
        return new RegisterResult(true, null, username);
    }

    public VerifyResult verify(String username, String code, String ip) {
        if (!rateCheck(ip, "verify", 5, 300_000))
            return new VerifyResult(false, "Demasiados intentos. Espera 5 minutos.", null, null);

        if (username == null || code == null)
            return new VerifyResult(false, "Parámetros inválidos.", null, null);

        String key = username.toLowerCase();
        PendingCode pc = pending.get(key);
        if (pc == null)
            return new VerifyResult(false, "No hay código pendiente. Solicita uno nuevo.", null, null);
        if (System.currentTimeMillis() > pc.expiresAt) {
            pending.remove(key);
            return new VerifyResult(false, "Código expirado. Solicita uno nuevo.", null, null);
        }
        if (!pc.code.equals(code.trim()))
            return new VerifyResult(false, "Código incorrecto.", null, null);

        pending.remove(key);
        WebUser user = users.get(key);
        if (user == null) return new VerifyResult(false, "Usuario no encontrado.", null, null);

        String token = UUID.randomUUID().toString();
        SessionEntry se = new SessionEntry();
        se.token     = token;
        se.ip        = ip;
        se.createdAt = System.currentTimeMillis();
        se.lastUsed  = se.createdAt;

        if (user.sessions == null) user.sessions = new ArrayList<>();
        user.sessions.removeIf(s -> s.ip.equals(ip));
        if (user.sessions.size() >= 5) {
            user.sessions.sort(Comparator.comparingLong(s -> s.lastUsed));
            user.sessions.remove(0);
        }
        user.sessions.add(se);
        save();
        return new VerifyResult(true, null, token, user);
    }

    public WebUser getByToken(String token) {
        if (token == null || token.isBlank()) return null;
        long now    = System.currentTimeMillis();
        long expiry = 30L * 86_400_000;
        for (WebUser u : users.values()) {
            if (u.sessions == null) continue;
            for (SessionEntry s : u.sessions) {
                if (s.token.equals(token) && (now - s.lastUsed) < expiry) {
                    s.lastUsed = now;
                    return u;
                }
            }
        }
        return null;
    }

    public void logout(String token) {
        users.values().forEach(u -> {
            if (u.sessions != null) u.sessions.removeIf(s -> s.token.equals(token));
        });
        save();
    }

    // ── Tickets ──────────────────────────────────────────────────────────────

    public TicketResult createTicket(WebUser user, String kit, String targetTier, String ip) {
        if (!Arrays.asList(TESTABLE_TIERS).contains(targetTier))
            return new TicketResult(false, "Tier inválido. Disponibles: " + String.join(", ", TESTABLE_TIERS), null);

        List<String> kits = plugin.getKitManager().getKitNames();
        boolean kitOk = kits.stream().anyMatch(k -> k.equalsIgnoreCase(kit));
        if (!kitOk) return new TicketResult(false, "Kit no encontrado.", null);

        String kitLow = kit.toLowerCase();

        // One active ticket per user+kit+tier
        for (Ticket t : tickets.values()) {
            if (t.username.equalsIgnoreCase(user.username) && t.kit.equalsIgnoreCase(kit)
                    && t.targetTier.equals(targetTier)
                    && List.of("pending","scheduled","testing").contains(t.status))
                return new TicketResult(false, "Ya tienes un ticket activo para " + targetTier + " en " + kit + ".", null);
        }

        // One active ticket per IP+kit+tier
        for (Ticket t : tickets.values()) {
            if (ip.equals(t.ip) && t.kit.equalsIgnoreCase(kit) && t.targetTier.equals(targetTier)
                    && List.of("pending","scheduled").contains(t.status))
                return new TicketResult(false, "Ya hay un ticket activo desde tu IP para ese tier.", null);
        }

        if (!rateCheck(ip, "ticket", 3, 86_400_000))
            return new TicketResult(false, "Máximo 3 tickets por día.", null);

        Ticket ticket       = new Ticket();
        ticket.id           = genId();
        ticket.username     = user.username;
        ticket.uuid         = user.uuid;
        ticket.kit          = kitLow;
        ticket.targetTier   = targetTier;
        ticket.status       = "pending";
        ticket.ip           = ip;
        ticket.createdAt    = System.currentTimeMillis();
        ticket.messages     = new ArrayList<>();

        sysMsg(ticket, "✅ Ticket creado correctamente. Un tester revisará tu solicitud y te asignará un horario. Estate atento a este ticket.");
        tickets.put(ticket.id, ticket);
        saveTickets();

        notifyStaff("§d[MoonTiers] §fNuevo ticket §e#" + ticket.id + " §fde §a" + user.username
                + " §7[" + targetTier + " · " + kit + "]");
        return new TicketResult(true, null, ticket);
    }

    public List<Ticket> getTicketsFor(WebUser user) {
        boolean staff = !ROLE_USER.equals(user.role);
        return tickets.values().stream()
                .filter(t -> staff || t.username.equalsIgnoreCase(user.username))
                .sorted(Comparator.comparingLong((Ticket t) -> t.createdAt).reversed())
                .collect(Collectors.toList());
    }

    public Ticket getTicket(String id) {
        return id == null ? null : tickets.get(id.toUpperCase());
    }

    public MsgResult addMessage(String ticketId, WebUser user, String content) {
        Ticket t = getTicket(ticketId);
        if (t == null) return new MsgResult(false, "Ticket no encontrado.", null);

        boolean owner = t.username.equalsIgnoreCase(user.username);
        boolean staff = !ROLE_USER.equals(user.role);
        if (!owner && !staff) return new MsgResult(false, "Sin permisos.", null);
        if (List.of("completed","rejected").contains(t.status))
            return new MsgResult(false, "El ticket está cerrado.", null);
        if (content == null || content.isBlank() || content.length() > 500)
            return new MsgResult(false, "Mensaje inválido (máx 500 caracteres).", null);

        TicketMessage msg = new TicketMessage();
        msg.id      = UUID.randomUUID().toString().substring(0, 8);
        msg.author  = user.username;
        msg.role    = user.role;
        msg.content = content.trim();
        msg.ts      = System.currentTimeMillis();
        t.messages.add(msg);
        saveTickets();
        return new MsgResult(true, null, msg);
    }

    public StatusResult assignTicket(String id, WebUser admin, String testerName) {
        if (!ROLE_ADMIN.equals(admin.role)) return new StatusResult(false, "Sin permisos.");
        Ticket t = getTicket(id);
        if (t == null) return new StatusResult(false, "Ticket no encontrado.");
        t.assignedTester = testerName;
        sysMsg(t, "📋 Ticket asignado al tester **" + testerName + "**.");
        saveTickets();
        return new StatusResult(true, null);
    }

    public StatusResult scheduleTicket(String id, WebUser staff, String slotId) {
        if (ROLE_USER.equals(staff.role)) return new StatusResult(false, "Sin permisos.");
        Ticket t = getTicket(id);
        if (t == null) return new StatusResult(false, "Ticket no encontrado.");

        ScheduledSlot slot = resolveSlot(slotId);
        if (slot == null) return new StatusResult(false, "Horario no disponible.");

        t.slot           = slot;
        t.status         = "scheduled";
        t.assignedTester = t.assignedTester != null ? t.assignedTester : staff.username;

        String day = dayName(slot.dayOfWeek);
        sysMsg(t, "📅 **Test programado**\n🗓 " + day + " " + slot.date
                + " · " + slot.time + " (hora España, CET/CEST)\n👤 Tester: " + slot.testerName
                + "\n\n⚠️ Conéctate al servidor a esa hora. Si no apareces, el ticket se archivará.");
        saveTickets();
        return new StatusResult(true, null);
    }

    public StatusResult resolveTicket(String id, WebUser resolver, String resolution,
                                      String reason, String newTier, int newPoints) {
        if (ROLE_USER.equals(resolver.role)) return new StatusResult(false, "Sin permisos.");
        Ticket t = getTicket(id);
        if (t == null) return new StatusResult(false, "Ticket no encontrado.");

        t.status          = "approved".equals(resolution) ? "completed" : "rejected";
        t.resolvedAt      = System.currentTimeMillis();
        t.resolvedBy      = resolver.username;
        t.resolution      = resolution;
        t.rejectionReason = reason;

        if ("approved".equals(resolution) && newTier != null && ROLE_ADMIN.equals(resolver.role)) {
            String finalTier = newTier;
            int    finalPts  = newPoints;
            String finalKit  = t.kit;
            UUID   uuid      = t.uuid != null ? tryParseUUID(t.uuid) : resolveUUID(t.username);
            if (uuid != null) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    plugin.getTierManager().setPoints(uuid, finalKit, finalPts);
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) p.sendMessage(
                        "§d§l[MoonTiers] §f¡Felicidades §a" + t.username + "§f! Tu tier en §e"
                        + finalKit + " §fha sido subido a §6§l" + finalTier + "§f. ¡Enhorabuena!");
                });
            }
            sysMsg(t, "✅ **¡Test APROBADO!** Tu tier en **" + t.kit + "** ha sido actualizado a **" + newTier + "**. ¡Enhorabuena!");
        } else if ("rejected".equals(resolution)) {
            String rText = reason != null && !reason.isBlank() ? "\n\n💬 Razón: " + reason : "";
            sysMsg(t, "❌ **Test no superado**." + rText + "\n\nSigue practicando y vuelve a intentarlo cuando estés listo. ¡Mucho ánimo!");
        }
        saveTickets();
        return new StatusResult(true, null);
    }

    // ── Schedules ─────────────────────────────────────────────────────────────

    public List<TesterSchedule> allSchedules() { return new ArrayList<>(schedules.values()); }

    public TesterSchedule getSchedule(String tester) { return schedules.get(tester); }

    public StatusResult updateSchedule(WebUser staff, String testerName, List<SlotTemplate> slots) {
        if (ROLE_USER.equals(staff.role)) return new StatusResult(false, "Sin permisos.");
        // Tester can only update own; admin can update any
        if (ROLE_TESTER.equals(staff.role) && !staff.username.equalsIgnoreCase(testerName))
            return new StatusResult(false, "Solo puedes modificar tu propio horario.");
        TesterSchedule ts = schedules.getOrDefault(testerName, new TesterSchedule());
        ts.testerName = testerName;
        ts.slots      = slots;
        schedules.put(testerName, ts);
        saveSchedules();
        return new StatusResult(true, null);
    }

    // ── Users (admin) ────────────────────────────────────────────────────────

    public List<WebUser>  allUsers()                  { return new ArrayList<>(users.values()); }
    public WebUser        getByUsername(String name)  { return name == null ? null : users.get(name.toLowerCase()); }

    public StatusResult setRole(WebUser admin, String target, String role) {
        if (!ROLE_ADMIN.equals(admin.role)) return new StatusResult(false, "Sin permisos.");
        WebUser u = getByUsername(target);
        if (u == null) return new StatusResult(false, "Usuario no encontrado.");
        if (!List.of("user","tester","admin").contains(role)) return new StatusResult(false, "Rol inválido.");
        u.role = role;
        save();
        return new StatusResult(true, null);
    }

    public String[] getTestableTiers() { return TESTABLE_TIERS; }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void sysMsg(Ticket t, String content) {
        TicketMessage msg = new TicketMessage();
        msg.id      = UUID.randomUUID().toString().substring(0, 8);
        msg.author  = "Sistema";
        msg.role    = "system";
        msg.content = content;
        msg.ts      = System.currentTimeMillis();
        t.messages.add(msg);
    }

    private void notifyStaff(String msg) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                WebUser wu = getByUsername(p.getName());
                if (wu != null && !ROLE_USER.equals(wu.role)) p.sendMessage(msg);
            }
        });
    }

    private void sendCode(String username, String code) {
        String fmt = code.substring(0,3) + "-" + code.substring(3,6) + "-" + code.substring(6,9);
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player p = Bukkit.getPlayer(username);
            if (p != null) {
                p.sendMessage("");
                p.sendMessage("§d§l▶ §d§lMoonTiers §r§7— Verificación Web");
                p.sendMessage("§fTu código: §e§l" + fmt);
                p.sendMessage("§7Expira en §f5 minutos§7. Introdúcelo en la web.");
                p.sendMessage("");
            } else {
                plugin.getLogger().info("[TicketManager] Código para " + username + " (offline): " + fmt);
            }
        });
    }

    private boolean rateCheck(String ip, String action, int max, long windowMs) {
        String key = ip + ":" + action;
        long now = System.currentTimeMillis();
        List<Long> ts = rateLims.computeIfAbsent(key, k -> new ArrayList<>());
        ts.removeIf(t -> (now - t) > windowMs);
        if (ts.size() >= max) return false;
        ts.add(now);
        return true;
    }

    private ScheduledSlot resolveSlot(String slotId) {
        for (TesterSchedule ts : schedules.values()) {
            for (SlotTemplate st : ts.slots) {
                if (st.id.equals(slotId) && st.available) {
                    ScheduledSlot s = new ScheduledSlot();
                    s.id         = slotId;
                    s.testerName = ts.testerName;
                    s.dayOfWeek  = st.dayOfWeek;
                    s.time       = st.time;
                    s.date       = nextOccurrence(st.dayOfWeek, st.time);
                    return s;
                }
            }
        }
        return null;
    }

    private String nextOccurrence(int dow, String time) {
        try {
            LocalDate today  = LocalDate.now(ZoneId.of("Europe/Madrid"));
            DayOfWeek target = DayOfWeek.of(dow);
            LocalDate next   = today.with(TemporalAdjusters.nextOrSame(target));
            return next + " " + time;
        } catch (Exception e) { return time; }
    }

    private String dayName(int dow) {
        String[] n = {"","Lunes","Martes","Miércoles","Jueves","Viernes","Sábado","Domingo"};
        return (dow >= 1 && dow <= 7) ? n[dow] : "?";
    }

    private String genId() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private UUID resolveUUID(String name) {
        for (var p : plugin.getServer().getOfflinePlayers())
            if (name.equalsIgnoreCase(p.getName())) return p.getUniqueId();
        return null;
    }

    private UUID tryParseUUID(String s) {
        try { return UUID.fromString(s); } catch (Exception e) { return null; }
    }

    // ── Persistence ──────────────────────────────────────────────────────────

    public void load() {
        loadMap("web_users.json",     users,     new TypeToken<Map<String,WebUser>>(){}.getType());
        loadMap("web_tickets.json",   tickets,   new TypeToken<Map<String,Ticket>>(){}.getType());
        loadMap("web_schedules.json", schedules, new TypeToken<Map<String,TesterSchedule>>(){}.getType());
    }

    @SuppressWarnings("unchecked")
    private <V> void loadMap(String filename, Map<String,V> map, Type type) {
        File f = new File(plugin.getDataFolder(), filename);
        if (!f.exists()) return;
        try {
            String json = Files.readString(f.toPath(), StandardCharsets.UTF_8);
            Map<String,V> data = gson.fromJson(json, type);
            if (data != null) map.putAll(data);
        } catch (Exception e) {
            plugin.getLogger().warning("[TicketManager] Error loading " + filename + ": " + e.getMessage());
        }
    }

    public synchronized void save() {
        write("web_users.json", users);
    }

    public synchronized void saveTickets() {
        write("web_tickets.json", tickets);
    }

    public synchronized void saveSchedules() {
        write("web_schedules.json", schedules);
    }

    private void write(String filename, Object data) {
        try {
            plugin.getDataFolder().mkdirs();
            Files.writeString(new File(plugin.getDataFolder(), filename).toPath(),
                    gson.toJson(data), StandardCharsets.UTF_8);
        } catch (Exception e) {
            plugin.getLogger().warning("[TicketManager] Error saving " + filename + ": " + e.getMessage());
        }
    }
}

package com.pvprooms.managers;

import com.pvprooms.PvPRoomsPro;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manages the web ticket system for tier verification requests.
 * Handles user authentication, tickets, and chat.
 */
public class TicketManager {

    private final PvPRoomsPro plugin;
    private final File dataFile;
    private YamlConfiguration config;

    // In-memory data structures
    private final Map<String, WebUser> users = new ConcurrentHashMap<>();
    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();
    private final Map<String, String> sessions = new ConcurrentHashMap<>(); // sessionId -> username
    private final Map<String, String> pendingCodes = new ConcurrentHashMap<>(); // code -> username
    private final Map<String, Long> rateLimits = new ConcurrentHashMap<>(); // IP -> last request time
    private final Map<String, Set<String>> tierRequests = new ConcurrentHashMap<>(); // username -> set of requested tiers

    // Tester schedules (day -> list of time slots)
    private final Map<String, List<TimeSlot>> testerSchedules = new ConcurrentHashMap<>();

    private static final SecureRandom random = new SecureRandom();

    public TicketManager(PvPRoomsPro plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "tickets.yml");
        load();
        initDefaultSchedules();
    }

    private void initDefaultSchedules() {
        // Default schedule for testers (can be modified)
        List<TimeSlot> defaultSlots = Arrays.asList(
            new TimeSlot("16:00", "17:00"),
            new TimeSlot("17:00", "18:00"),
            new TimeSlot("18:00", "19:00"),
            new TimeSlot("19:00", "20:00"),
            new TimeSlot("20:00", "21:00"),
            new TimeSlot("21:00", "22:00")
        );
        testerSchedules.put("420Sleeptyx", new ArrayList<>(defaultSlots));
    }

    // ══════════════════════════════════════════════════════════════════════
    // USER MANAGEMENT
    // ══════════════════════════════════════════════════════════════════════

    public static class WebUser {
        public String username;
        public String mcUuid;
        public String role; // "user", "tester", "admin"
        public long createdAt;
        public String registerIp;
        public boolean verified;

        public WebUser(String username, String role) {
            this.username = username;
            this.role = role;
            this.createdAt = System.currentTimeMillis();
            this.verified = false;
        }
    }

    /**
     * Starts registration - generates a 9-digit code for in-game verification.
     */
    public String startRegistration(String username, String ip) {
        // Check rate limit
        if (isRateLimited(ip)) {
            return null;
        }

        // Check if user already exists
        if (users.containsKey(username.toLowerCase())) {
            return "EXISTS";
        }

        // Generate 9-digit code
        String code = generateCode();
        
        // Store pending registration
        WebUser user = new WebUser(username, "user");
        user.registerIp = ip;
        pendingCodes.put(code, username.toLowerCase());
        users.put(username.toLowerCase(), user);

        // Set admin/tester roles
        if (username.equalsIgnoreCase("Sh4doS3kr")) {
            user.role = "admin";
            user.verified = true; // Auto-verify admin
        } else if (username.equalsIgnoreCase("420Sleeptyx")) {
            user.role = "tester";
            user.verified = true; // Auto-verify tester
        }

        updateRateLimit(ip);
        save();
        return code;
    }

    /**
     * Verifies the code entered in-game.
     */
    public boolean verifyCode(Player player, String code) {
        String username = pendingCodes.get(code);
        if (username == null) return false;

        WebUser user = users.get(username);
        if (user == null) return false;

        // Verify the player name matches
        if (!player.getName().equalsIgnoreCase(username)) {
            return false;
        }

        user.mcUuid = player.getUniqueId().toString();
        user.verified = true;
        pendingCodes.remove(code);
        save();

        player.sendMessage(plugin.prefix() + "§a¡Cuenta verificada! Ya puedes iniciar sesión en la web.");
        return true;
    }

    /**
     * Login - returns session ID or null if failed.
     */
    public String login(String username, String ip) {
        if (isRateLimited(ip)) return null;

        WebUser user = users.get(username.toLowerCase());
        if (user == null || !user.verified) {
            updateRateLimit(ip);
            return null;
        }

        // Check if MC player is online with same name
        Player player = Bukkit.getPlayerExact(username);
        if (player == null) {
            // Generate new verification code
            String code = generateCode();
            pendingCodes.put(code, username.toLowerCase());
            updateRateLimit(ip);
            return "VERIFY:" + code;
        }

        // Create session
        String sessionId = UUID.randomUUID().toString();
        sessions.put(sessionId, username.toLowerCase());
        return sessionId;
    }

    /**
     * Quick login for online players - returns session directly.
     */
    public String quickLogin(String username) {
        WebUser user = users.get(username.toLowerCase());
        if (user == null) return null;
        if (!user.verified) return null;

        String sessionId = UUID.randomUUID().toString();
        sessions.put(sessionId, username.toLowerCase());
        return sessionId;
    }

    public WebUser getUserBySession(String sessionId) {
        String username = sessions.get(sessionId);
        if (username == null) return null;
        return users.get(username);
    }

    public void logout(String sessionId) {
        sessions.remove(sessionId);
    }

    public WebUser getUser(String username) {
        return users.get(username.toLowerCase());
    }

    // ══════════════════════════════════════════════════════════════════════
    // TICKET MANAGEMENT
    // ══════════════════════════════════════════════════════════════════════

    public static class Ticket {
        public String id;
        public String username;
        public String kit;
        public String targetTier; // HT3, HT2, HT1, LT5
        public String status; // "pending", "scheduled", "in_progress", "completed", "rejected"
        public String assignedTester;
        public String scheduledDate;
        public String scheduledTime;
        public long createdAt;
        public long updatedAt;
        public List<ChatMessage> messages = new ArrayList<>();
        public String result; // "approved", "denied", null
        public String newTier; // If approved, what tier they got

        public Ticket(String id, String username, String kit, String targetTier) {
            this.id = id;
            this.username = username;
            this.kit = kit;
            this.targetTier = targetTier;
            this.status = "pending";
            this.createdAt = System.currentTimeMillis();
            this.updatedAt = System.currentTimeMillis();
        }
    }

    public static class ChatMessage {
        public String sender;
        public String role;
        public String message;
        public long timestamp;

        public ChatMessage(String sender, String role, String message) {
            this.sender = sender;
            this.role = role;
            this.message = message;
            this.timestamp = System.currentTimeMillis();
        }
    }

    public static class TimeSlot {
        public String start;
        public String end;
        public boolean available = true;
        public String bookedBy;
        public String date;

        public TimeSlot(String start, String end) {
            this.start = start;
            this.end = end;
        }
    }

    /**
     * Creates a new ticket.
     */
    public Ticket createTicket(String username, String kit, String targetTier, String ip) {
        // Rate limit check
        if (isRateLimited(ip)) return null;

        // Check if user has already requested this tier for this kit
        String key = username.toLowerCase() + ":" + kit.toLowerCase() + ":" + targetTier;
        Set<String> userRequests = tierRequests.computeIfAbsent(username.toLowerCase(), k -> new HashSet<>());
        if (userRequests.contains(key)) {
            return null; // Already requested
        }

        // Check for pending tickets for same kit
        for (Ticket t : tickets.values()) {
            if (t.username.equalsIgnoreCase(username) && 
                t.kit.equalsIgnoreCase(kit) &&
                (t.status.equals("pending") || t.status.equals("scheduled") || t.status.equals("in_progress"))) {
                return null; // Already has pending ticket
            }
        }

        String id = "TKT-" + System.currentTimeMillis() % 100000 + "-" + random.nextInt(1000);
        Ticket ticket = new Ticket(id, username, kit, targetTier);
        tickets.put(id, ticket);
        userRequests.add(key);

        updateRateLimit(ip);
        save();
        return ticket;
    }

    public Ticket getTicket(String id) {
        return tickets.get(id);
    }

    public List<Ticket> getTicketsForUser(String username) {
        return tickets.values().stream()
                .filter(t -> t.username.equalsIgnoreCase(username))
                .sorted((a, b) -> Long.compare(b.createdAt, a.createdAt))
                .collect(Collectors.toList());
    }

    public List<Ticket> getTicketsForTester(String tester) {
        return tickets.values().stream()
                .filter(t -> t.assignedTester != null && t.assignedTester.equalsIgnoreCase(tester))
                .sorted((a, b) -> Long.compare(b.updatedAt, a.updatedAt))
                .collect(Collectors.toList());
    }

    public List<Ticket> getAllPendingTickets() {
        return tickets.values().stream()
                .filter(t -> t.status.equals("pending"))
                .sorted((a, b) -> Long.compare(a.createdAt, b.createdAt))
                .collect(Collectors.toList());
    }

    public List<Ticket> getAllTickets() {
        return tickets.values().stream()
                .sorted((a, b) -> Long.compare(b.createdAt, a.createdAt))
                .collect(Collectors.toList());
    }

    /**
     * Assign a tester to a ticket.
     */
    public boolean assignTester(String ticketId, String tester) {
        Ticket ticket = tickets.get(ticketId);
        if (ticket == null) return false;
        ticket.assignedTester = tester;
        ticket.status = "pending";
        ticket.updatedAt = System.currentTimeMillis();
        save();
        return true;
    }

    /**
     * Schedule a ticket for a specific date/time.
     */
    public boolean scheduleTicket(String ticketId, String date, String time) {
        Ticket ticket = tickets.get(ticketId);
        if (ticket == null) return false;
        ticket.scheduledDate = date;
        ticket.scheduledTime = time;
        ticket.status = "scheduled";
        ticket.updatedAt = System.currentTimeMillis();
        save();
        return true;
    }

    /**
     * Add a message to ticket chat.
     */
    public boolean addMessage(String ticketId, String sender, String role, String message) {
        Ticket ticket = tickets.get(ticketId);
        if (ticket == null) return false;
        ticket.messages.add(new ChatMessage(sender, role, message));
        ticket.updatedAt = System.currentTimeMillis();
        save();
        return true;
    }

    /**
     * Complete a ticket with result.
     */
    public boolean completeTicket(String ticketId, String result, String newTier) {
        Ticket ticket = tickets.get(ticketId);
        if (ticket == null) return false;
        ticket.status = "completed";
        ticket.result = result;
        ticket.newTier = newTier;
        ticket.updatedAt = System.currentTimeMillis();
        
        // If approved, update player tier in-game
        if ("approved".equals(result) && newTier != null) {
            applyTierChange(ticket.username, ticket.kit, newTier);
        }
        
        save();
        return true;
    }

    private void applyTierChange(String username, String kit, String tierName) {
        // Find the player's UUID
        for (var p : Bukkit.getOfflinePlayers()) {
            if (username.equalsIgnoreCase(p.getName())) {
                var tier = com.pvprooms.model.Tier.valueOf(tierName.toUpperCase().replace("-", "_"));
                // Use setPoints with the tier's minimum points
                plugin.getTierManager().setPoints(p.getUniqueId(), kit, tier.minPoints);
                
                Player online = Bukkit.getPlayerExact(username);
                if (online != null) {
                    online.sendMessage(plugin.prefix() + "§a¡Tu tier en §e" + kit + " §aha sido actualizado a " + tier.formatted() + "§a!");
                }
                break;
            }
        }
    }

    /**
     * Admin: Set user tier directly.
     */
    public boolean setUserTier(String username, String kit, String tierName) {
        try {
            applyTierChange(username, kit, tierName);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get available time slots for a date.
     */
    public List<TimeSlot> getAvailableSlots(String tester, String date) {
        List<TimeSlot> slots = testerSchedules.get(tester);
        if (slots == null) return Collections.emptyList();

        // Filter out already booked slots for this date
        Set<String> bookedTimes = tickets.values().stream()
                .filter(t -> t.assignedTester != null && t.assignedTester.equalsIgnoreCase(tester))
                .filter(t -> date.equals(t.scheduledDate))
                .filter(t -> "scheduled".equals(t.status) || "in_progress".equals(t.status))
                .map(t -> t.scheduledTime)
                .collect(Collectors.toSet());

        return slots.stream()
                .filter(s -> !bookedTimes.contains(s.start))
                .collect(Collectors.toList());
    }

    // ══════════════════════════════════════════════════════════════════════
    // RATE LIMITING & UTILITIES
    // ══════════════════════════════════════════════════════════════════════

    private boolean isRateLimited(String ip) {
        Long lastRequest = rateLimits.get(ip);
        if (lastRequest == null) return false;
        return System.currentTimeMillis() - lastRequest < 5000; // 5 second cooldown
    }

    private void updateRateLimit(String ip) {
        rateLimits.put(ip, System.currentTimeMillis());
    }

    private String generateCode() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 9; i++) {
            code.append(random.nextInt(10));
        }
        return code.toString();
    }

    // ══════════════════════════════════════════════════════════════════════
    // PERSISTENCE
    // ══════════════════════════════════════════════════════════════════════

    public void load() {
        if (!dataFile.exists()) {
            config = new YamlConfiguration();
            return;
        }
        config = YamlConfiguration.loadConfiguration(dataFile);

        // Load users
        if (config.isConfigurationSection("users")) {
            for (String key : config.getConfigurationSection("users").getKeys(false)) {
                String path = "users." + key;
                WebUser user = new WebUser(
                    config.getString(path + ".username", key),
                    config.getString(path + ".role", "user")
                );
                user.mcUuid = config.getString(path + ".uuid");
                user.createdAt = config.getLong(path + ".createdAt", System.currentTimeMillis());
                user.registerIp = config.getString(path + ".ip");
                user.verified = config.getBoolean(path + ".verified", false);
                users.put(key.toLowerCase(), user);
            }
        }

        // Load tickets
        if (config.isConfigurationSection("tickets")) {
            for (String id : config.getConfigurationSection("tickets").getKeys(false)) {
                String path = "tickets." + id;
                Ticket ticket = new Ticket(
                    id,
                    config.getString(path + ".username"),
                    config.getString(path + ".kit"),
                    config.getString(path + ".targetTier")
                );
                ticket.status = config.getString(path + ".status", "pending");
                ticket.assignedTester = config.getString(path + ".tester");
                ticket.scheduledDate = config.getString(path + ".date");
                ticket.scheduledTime = config.getString(path + ".time");
                ticket.createdAt = config.getLong(path + ".createdAt", System.currentTimeMillis());
                ticket.updatedAt = config.getLong(path + ".updatedAt", System.currentTimeMillis());
                ticket.result = config.getString(path + ".result");
                ticket.newTier = config.getString(path + ".newTier");

                // Load messages
                if (config.isList(path + ".messages")) {
                    for (var msgMap : config.getMapList(path + ".messages")) {
                        ChatMessage msg = new ChatMessage(
                            (String) msgMap.get("sender"),
                            (String) msgMap.get("role"),
                            (String) msgMap.get("message")
                        );
                        Object tsObj = msgMap.get("timestamp");
                        msg.timestamp = tsObj != null ? ((Number) tsObj).longValue() : System.currentTimeMillis();
                        ticket.messages.add(msg);
                    }
                }

                tickets.put(id, ticket);
            }
        }

        // Load tier requests
        if (config.isConfigurationSection("tierRequests")) {
            for (String user : config.getConfigurationSection("tierRequests").getKeys(false)) {
                List<String> requests = config.getStringList("tierRequests." + user);
                tierRequests.put(user.toLowerCase(), new HashSet<>(requests));
            }
        }
    }

    public void save() {
        config = new YamlConfiguration();

        // Save users
        for (var entry : users.entrySet()) {
            String path = "users." + entry.getKey();
            WebUser user = entry.getValue();
            config.set(path + ".username", user.username);
            config.set(path + ".role", user.role);
            config.set(path + ".uuid", user.mcUuid);
            config.set(path + ".createdAt", user.createdAt);
            config.set(path + ".ip", user.registerIp);
            config.set(path + ".verified", user.verified);
        }

        // Save tickets
        for (var entry : tickets.entrySet()) {
            String path = "tickets." + entry.getKey();
            Ticket ticket = entry.getValue();
            config.set(path + ".username", ticket.username);
            config.set(path + ".kit", ticket.kit);
            config.set(path + ".targetTier", ticket.targetTier);
            config.set(path + ".status", ticket.status);
            config.set(path + ".tester", ticket.assignedTester);
            config.set(path + ".date", ticket.scheduledDate);
            config.set(path + ".time", ticket.scheduledTime);
            config.set(path + ".createdAt", ticket.createdAt);
            config.set(path + ".updatedAt", ticket.updatedAt);
            config.set(path + ".result", ticket.result);
            config.set(path + ".newTier", ticket.newTier);

            // Save messages
            List<Map<String, Object>> msgList = new ArrayList<>();
            for (ChatMessage msg : ticket.messages) {
                Map<String, Object> msgMap = new HashMap<>();
                msgMap.put("sender", msg.sender);
                msgMap.put("role", msg.role);
                msgMap.put("message", msg.message);
                msgMap.put("timestamp", msg.timestamp);
                msgList.add(msgMap);
            }
            config.set(path + ".messages", msgList);
        }

        // Save tier requests
        for (var entry : tierRequests.entrySet()) {
            config.set("tierRequests." + entry.getKey(), new ArrayList<>(entry.getValue()));
        }

        try {
            config.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("[TicketManager] Error saving: " + e.getMessage());
        }
    }

    public List<WebUser> getAllUsers() {
        return new ArrayList<>(users.values());
    }

    public boolean setUserRole(String username, String role) {
        WebUser user = users.get(username.toLowerCase());
        if (user == null) return false;
        user.role = role;
        save();
        return true;
    }

    public boolean deleteUser(String username) {
        WebUser removed = users.remove(username.toLowerCase());
        if (removed != null) {
            save();
            return true;
        }
        return false;
    }

    public Map<String, List<TimeSlot>> getTesterSchedules() {
        return testerSchedules;
    }

    public void updateTesterSchedule(String tester, List<TimeSlot> slots) {
        testerSchedules.put(tester, slots);
    }
}

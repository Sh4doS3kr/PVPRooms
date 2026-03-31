package com.pvprooms.api;

import com.pvprooms.PvPRoomsPro;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the web ticket system for tier tests.
 * Handles user authentication, tickets, and chat messages.
 */
public class TicketManager {

    private final PvPRoomsPro plugin;
    private final File dataFolder;
    
    // In-memory caches (persisted to YAML)
    private final Map<String, WebUser> users = new ConcurrentHashMap<>();           // username -> WebUser
    private final Map<String, String> sessions = new ConcurrentHashMap<>();          // sessionToken -> username
    private final Map<String, PendingVerification> pendingVerifications = new ConcurrentHashMap<>(); // username -> code
    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();           // ticketId -> Ticket
    
    // Available time slots for tests (hour in 24h format)
    private static final List<TimeSlot> TIME_SLOTS = List.of(
            new TimeSlot("16:00", "16:00 - 17:00 (Tarde)"),
            new TimeSlot("17:00", "17:00 - 18:00 (Tarde)"),
            new TimeSlot("18:00", "18:00 - 19:00 (Tarde)"),
            new TimeSlot("19:00", "19:00 - 20:00 (Noche)"),
            new TimeSlot("20:00", "20:00 - 21:00 (Noche)"),
            new TimeSlot("21:00", "21:00 - 22:00 (Noche)"),
            new TimeSlot("22:00", "22:00 - 23:00 (Noche)")
    );
    
    // Staff roles
    private static final Map<String, String> STAFF_ROLES = Map.of(
            "sh4dos3kr", "admin",
            "420sleeptyx", "tester"
    );

    public TicketManager(PvPRoomsPro plugin) {
        this.plugin = plugin;
        this.dataFolder = new File(plugin.getDataFolder(), "tickets");
        if (!dataFolder.exists()) dataFolder.mkdirs();
        load();
    }

    // ══════════════════════════════════════════════════════════════════════
    // AUTHENTICATION
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Start registration process - creates pending verification.
     * @return verification code to enter in-game, or null if already registered
     */
    public String startRegistration(String username) {
        String lowerUser = username.toLowerCase();
        
        // Check if already registered
        if (users.containsKey(lowerUser)) {
            return null; // Already registered
        }
        
        // Generate 9-digit code
        String code = generateCode();
        pendingVerifications.put(lowerUser, new PendingVerification(code, System.currentTimeMillis()));
        
        return code;
    }
    
    /**
     * Verify code entered in-game.
     * @return true if verification successful
     */
    public boolean verifyCode(UUID playerUUID, String playerName, String code) {
        String lowerName = playerName.toLowerCase();
        PendingVerification pending = pendingVerifications.get(lowerName);
        
        if (pending == null) return false;
        
        // Check if expired (10 minutes)
        if (System.currentTimeMillis() - pending.timestamp > 600000) {
            pendingVerifications.remove(lowerName);
            return false;
        }
        
        if (!pending.code.equals(code)) return false;
        
        // Create user account
        WebUser user = new WebUser(playerUUID, playerName, getRole(lowerName));
        users.put(lowerName, user);
        pendingVerifications.remove(lowerName);
        save();
        
        return true;
    }
    
    /**
     * Check if there's a pending verification for a player.
     */
    public PendingVerification getPendingVerification(String username) {
        return pendingVerifications.get(username.toLowerCase());
    }
    
    /**
     * Login user and create session.
     * @return session token or null if user not found
     */
    public String login(String username) {
        String lowerUser = username.toLowerCase();
        WebUser user = users.get(lowerUser);
        if (user == null) return null;
        
        // Generate session token
        String token = UUID.randomUUID().toString();
        sessions.put(token, lowerUser);
        
        return token;
    }
    
    /**
     * Logout and invalidate session.
     */
    public void logout(String sessionToken) {
        sessions.remove(sessionToken);
    }
    
    /**
     * Get user from session token.
     */
    public WebUser getUserBySession(String sessionToken) {
        if (sessionToken == null) return null;
        String username = sessions.get(sessionToken);
        if (username == null) return null;
        return users.get(username);
    }
    
    /**
     * Get user by username.
     */
    public WebUser getUser(String username) {
        return users.get(username.toLowerCase());
    }
    
    private String getRole(String username) {
        return STAFF_ROLES.getOrDefault(username.toLowerCase(), "user");
    }
    
    private String generateCode() {
        Random random = new Random();
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 9; i++) {
            code.append(random.nextInt(10));
        }
        return code.toString();
    }

    // ══════════════════════════════════════════════════════════════════════
    // TICKETS
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Create a new ticket for tier test.
     */
    public Ticket createTicket(String username, String kit, String date, String timeSlot, String notes) {
        WebUser user = users.get(username.toLowerCase());
        if (user == null) return null;
        
        String ticketId = "TKT-" + System.currentTimeMillis() % 100000;
        Ticket ticket = new Ticket(
                ticketId,
                username,
                user.uuid,
                kit,
                date,
                timeSlot,
                notes,
                "pending",
                new ArrayList<>(),
                System.currentTimeMillis()
        );
        
        tickets.put(ticketId, ticket);
        save();
        
        return ticket;
    }
    
    /**
     * Get tickets for a user.
     */
    public List<Ticket> getTicketsForUser(String username) {
        List<Ticket> result = new ArrayList<>();
        for (Ticket t : tickets.values()) {
            if (t.username.equalsIgnoreCase(username)) {
                result.add(t);
            }
        }
        result.sort((a, b) -> Long.compare(b.createdAt, a.createdAt));
        return result;
    }
    
    /**
     * Get all tickets (for staff).
     */
    public List<Ticket> getAllTickets() {
        List<Ticket> result = new ArrayList<>(tickets.values());
        result.sort((a, b) -> Long.compare(b.createdAt, a.createdAt));
        return result;
    }
    
    /**
     * Get pending tickets (for testers).
     */
    public List<Ticket> getPendingTickets() {
        List<Ticket> result = new ArrayList<>();
        for (Ticket t : tickets.values()) {
            if (t.status.equals("pending") || t.status.equals("scheduled")) {
                result.add(t);
            }
        }
        result.sort((a, b) -> Long.compare(b.createdAt, a.createdAt));
        return result;
    }
    
    /**
     * Get ticket by ID.
     */
    public Ticket getTicket(String ticketId) {
        return tickets.get(ticketId);
    }
    
    /**
     * Update ticket status.
     */
    public boolean updateTicketStatus(String ticketId, String status, String staffUser) {
        Ticket ticket = tickets.get(ticketId);
        if (ticket == null) return false;
        
        ticket.status = status;
        if (staffUser != null) {
            ticket.assignedTo = staffUser;
        }
        save();
        return true;
    }
    
    /**
     * Add chat message to ticket.
     */
    public boolean addMessage(String ticketId, String author, String message, boolean isStaff) {
        Ticket ticket = tickets.get(ticketId);
        if (ticket == null) return false;
        
        ticket.messages.add(new ChatMessage(author, message, isStaff, System.currentTimeMillis()));
        save();
        return true;
    }
    
    /**
     * Get available time slots.
     */
    public List<TimeSlot> getTimeSlots() {
        return TIME_SLOTS;
    }
    
    /**
     * Get all users (for admin).
     */
    public List<WebUser> getAllUsers() {
        return new ArrayList<>(users.values());
    }
    
    /**
     * Update user role (admin only).
     */
    public boolean setUserRole(String username, String role) {
        WebUser user = users.get(username.toLowerCase());
        if (user == null) return false;
        user.role = role;
        save();
        return true;
    }

    // ══════════════════════════════════════════════════════════════════════
    // PERSISTENCE
    // ══════════════════════════════════════════════════════════════════════

    private void load() {
        // Load users
        File usersFile = new File(dataFolder, "users.yml");
        if (usersFile.exists()) {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(usersFile);
            for (String key : cfg.getKeys(false)) {
                var section = cfg.getConfigurationSection(key);
                if (section != null) {
                    WebUser user = new WebUser(
                            UUID.fromString(section.getString("uuid", UUID.randomUUID().toString())),
                            section.getString("name", key),
                            section.getString("role", "user")
                    );
                    users.put(key.toLowerCase(), user);
                }
            }
        }
        
        // Load tickets
        File ticketsFile = new File(dataFolder, "tickets.yml");
        if (ticketsFile.exists()) {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(ticketsFile);
            for (String ticketId : cfg.getKeys(false)) {
                var section = cfg.getConfigurationSection(ticketId);
                if (section != null) {
                    List<ChatMessage> messages = new ArrayList<>();
                    var msgSection = section.getConfigurationSection("messages");
                    if (msgSection != null) {
                        for (String msgKey : msgSection.getKeys(false)) {
                            var msg = msgSection.getConfigurationSection(msgKey);
                            if (msg != null) {
                                messages.add(new ChatMessage(
                                        msg.getString("author", ""),
                                        msg.getString("message", ""),
                                        msg.getBoolean("isStaff", false),
                                        msg.getLong("timestamp", 0)
                                ));
                            }
                        }
                    }
                    
                    Ticket ticket = new Ticket(
                            ticketId,
                            section.getString("username", ""),
                            UUID.fromString(section.getString("uuid", UUID.randomUUID().toString())),
                            section.getString("kit", ""),
                            section.getString("date", ""),
                            section.getString("timeSlot", ""),
                            section.getString("notes", ""),
                            section.getString("status", "pending"),
                            messages,
                            section.getLong("createdAt", 0)
                    );
                    ticket.assignedTo = section.getString("assignedTo");
                    tickets.put(ticketId, ticket);
                }
            }
        }
        
        plugin.getLogger().info("[Tickets] Cargados " + users.size() + " usuarios y " + tickets.size() + " tickets.");
    }
    
    public void save() {
        // Save users
        YamlConfiguration usersCfg = new YamlConfiguration();
        for (var entry : users.entrySet()) {
            WebUser user = entry.getValue();
            usersCfg.set(entry.getKey() + ".uuid", user.uuid.toString());
            usersCfg.set(entry.getKey() + ".name", user.name);
            usersCfg.set(entry.getKey() + ".role", user.role);
        }
        try {
            usersCfg.save(new File(dataFolder, "users.yml"));
        } catch (IOException e) {
            plugin.getLogger().warning("[Tickets] Error guardando usuarios: " + e.getMessage());
        }
        
        // Save tickets
        YamlConfiguration ticketsCfg = new YamlConfiguration();
        for (var entry : tickets.entrySet()) {
            Ticket t = entry.getValue();
            String id = entry.getKey();
            ticketsCfg.set(id + ".username", t.username);
            ticketsCfg.set(id + ".uuid", t.uuid.toString());
            ticketsCfg.set(id + ".kit", t.kit);
            ticketsCfg.set(id + ".date", t.date);
            ticketsCfg.set(id + ".timeSlot", t.timeSlot);
            ticketsCfg.set(id + ".notes", t.notes);
            ticketsCfg.set(id + ".status", t.status);
            ticketsCfg.set(id + ".assignedTo", t.assignedTo);
            ticketsCfg.set(id + ".createdAt", t.createdAt);
            
            for (int i = 0; i < t.messages.size(); i++) {
                ChatMessage msg = t.messages.get(i);
                ticketsCfg.set(id + ".messages." + i + ".author", msg.author);
                ticketsCfg.set(id + ".messages." + i + ".message", msg.message);
                ticketsCfg.set(id + ".messages." + i + ".isStaff", msg.isStaff);
                ticketsCfg.set(id + ".messages." + i + ".timestamp", msg.timestamp);
            }
        }
        try {
            ticketsCfg.save(new File(dataFolder, "tickets.yml"));
        } catch (IOException e) {
            plugin.getLogger().warning("[Tickets] Error guardando tickets: " + e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // DATA CLASSES
    // ══════════════════════════════════════════════════════════════════════

    public static class WebUser {
        public final UUID uuid;
        public final String name;
        public String role; // "user", "tester", "admin"
        
        public WebUser(UUID uuid, String name, String role) {
            this.uuid = uuid;
            this.name = name;
            this.role = role;
        }
    }
    
    public static class PendingVerification {
        public final String code;
        public final long timestamp;
        
        public PendingVerification(String code, long timestamp) {
            this.code = code;
            this.timestamp = timestamp;
        }
    }
    
    public static class Ticket {
        public final String id;
        public final String username;
        public final UUID uuid;
        public final String kit;
        public final String date;
        public final String timeSlot;
        public final String notes;
        public String status; // "pending", "scheduled", "completed", "cancelled"
        public String assignedTo;
        public final List<ChatMessage> messages;
        public final long createdAt;
        
        public Ticket(String id, String username, UUID uuid, String kit, String date,
                      String timeSlot, String notes, String status, List<ChatMessage> messages, long createdAt) {
            this.id = id;
            this.username = username;
            this.uuid = uuid;
            this.kit = kit;
            this.date = date;
            this.timeSlot = timeSlot;
            this.notes = notes;
            this.status = status;
            this.messages = messages;
            this.createdAt = createdAt;
        }
    }
    
    public static class ChatMessage {
        public final String author;
        public final String message;
        public final boolean isStaff;
        public final long timestamp;
        
        public ChatMessage(String author, String message, boolean isStaff, long timestamp) {
            this.author = author;
            this.message = message;
            this.isStaff = isStaff;
            this.timestamp = timestamp;
        }
    }
    
    public static class TimeSlot {
        public final String value;
        public final String display;
        
        public TimeSlot(String value, String display) {
            this.value = value;
            this.display = display;
        }
    }
}

package com.pvprooms.managers;

import com.pvprooms.PvPRoomsPro;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manages web authentication, tickets, and chat for the tier test system.
 * 
 * Features:
 * - User registration with in-game verification codes
 * - Session management with tokens
 * - Ticket system for tier tests (HT3+)
 * - Real-time chat between users and testers
 * - Rate limiting per IP and username
 */
public class TicketManager {

    private final PvPRoomsPro plugin;
    private final File dataFile;
    
    // ═══ Data Storage ═══════════════════════════════════════════════════════
    
    // Users: username -> WebUser
    private final Map<String, WebUser> users = new ConcurrentHashMap<>();
    
    // Active sessions: token -> username
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    
    // Pending verification codes: code -> PendingVerification
    private final Map<String, PendingVerification> pendingVerifications = new ConcurrentHashMap<>();
    
    // Verified sessions waiting for web login: username -> timestamp (valid for 5 minutes after verification)
    private final Map<String, Long> verifiedPendingLogin = new ConcurrentHashMap<>();
    
    // Tickets: ticketId -> Ticket
    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();
    
    // Rate limiting: IP -> last request times
    private final Map<String, List<Long>> rateLimits = new ConcurrentHashMap<>();
    
    // Tier request tracking: "username:tier" -> timestamp (to prevent duplicates)
    private final Map<String, Long> tierRequests = new ConcurrentHashMap<>();
    
    // IP tracking for registration: IP -> list of usernames registered
    private final Map<String, Set<String>> ipRegistrations = new ConcurrentHashMap<>();
    
    // ═══ Constants ══════════════════════════════════════════════════════════
    
    private static final long SESSION_DURATION_MS = 7 * 24 * 60 * 60 * 1000L; // 7 days
    private static final long VERIFICATION_TIMEOUT_MS = 10 * 60 * 1000L; // 10 minutes
    private static final int MAX_REQUESTS_PER_MINUTE = 30;
    private static final int MAX_REGISTRATIONS_PER_IP = 3;
    
    // Available time slots for tests (Spain timezone)
    public static final String[] AVAILABLE_SLOTS = {
        "16:00 - 17:00",
        "17:00 - 18:00",
        "18:00 - 19:00",
        "19:00 - 20:00",
        "20:00 - 21:00",
        "21:00 - 22:00",
        "22:00 - 23:00"
    };
    
    // Admin and Tester usernames (case-insensitive)
    private static final Set<String> ADMINS = Set.of("sh4dos3kr");
    private static final Set<String> TESTERS = Set.of("420sleeptyx", "holaregez");
    
    // ═══ Constructor ════════════════════════════════════════════════════════
    
    public TicketManager(PvPRoomsPro plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "tickets.yml");
        load();
        
        // Cleanup task every 5 minutes
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::cleanup, 6000L, 6000L);
    }
    
    // ═══ User Registration & Authentication ═════════════════════════════════
    
    /**
     * Initiates registration for a username. Returns a 9-digit code to verify in-game.
     */
    public synchronized RegistrationResult initiateRegistration(String username, String ip) {
        username = username.toLowerCase().trim();
        
        // Check rate limit
        if (isRateLimited(ip)) {
            return new RegistrationResult(false, null, "Demasiadas solicitudes. Espera un momento.");
        }
        
        // Check if already registered - if so, generate login code instead
        WebUser existingUser = users.get(username);
        if (existingUser != null) {
            // Generate login verification code
            String code = generateVerificationCode();
            pendingVerifications.put(code, new PendingVerification(username, existingUser.uuid, ip, System.currentTimeMillis()));
            return new RegistrationResult(true, code, "Código generado. Usa /verificar " + code + " en Minecraft.");
        }
        
        // Check IP registration limit
        Set<String> ipUsers = ipRegistrations.getOrDefault(ip, new HashSet<>());
        if (ipUsers.size() >= MAX_REGISTRATIONS_PER_IP && !ipUsers.contains(username)) {
            return new RegistrationResult(false, null, "Has alcanzado el límite de cuentas por IP.");
        }
        
        // Check if player exists in Minecraft
        boolean playerExists = false;
        UUID playerUUID = null;
        for (var p : Bukkit.getOfflinePlayers()) {
            if (username.equalsIgnoreCase(p.getName())) {
                playerExists = true;
                playerUUID = p.getUniqueId();
                break;
            }
        }
        if (!playerExists) {
            return new RegistrationResult(false, null, "Usuario de Minecraft no encontrado. Debes haber jugado en el servidor.");
        }
        
        // Generate 9-digit verification code
        String code = generateVerificationCode();
        
        // Store pending verification
        pendingVerifications.put(code, new PendingVerification(username, playerUUID, ip, System.currentTimeMillis()));
        
        return new RegistrationResult(true, code, "Código generado. Usa /verificar " + code + " en Minecraft.");
    }
    
    /**
     * Verifies a code in-game and completes registration.
     */
    public synchronized boolean verifyCode(Player player, String code) {
        PendingVerification pv = pendingVerifications.get(code);
        if (pv == null) return false;
        
        // Check timeout
        if (System.currentTimeMillis() - pv.timestamp > VERIFICATION_TIMEOUT_MS) {
            pendingVerifications.remove(code);
            return false;
        }
        
        // Check username matches
        if (!pv.username.equalsIgnoreCase(player.getName())) {
            return false;
        }
        
        // Create user account
        String passwordHash = generateSessionToken().substring(0, 32); // Temporary password
        WebUser user = new WebUser(pv.username, pv.uuid, passwordHash, pv.ip, System.currentTimeMillis());
        users.put(pv.username.toLowerCase(), user);
        
        // Track IP registration
        ipRegistrations.computeIfAbsent(pv.ip, k -> new HashSet<>()).add(pv.username.toLowerCase());
        
        pendingVerifications.remove(code);
        
        // Mark user as verified and ready for web login
        verifiedPendingLogin.put(pv.username.toLowerCase(), System.currentTimeMillis());
        
        save();
        addLog("VERIFY", pv.username, "Verificación completada desde IP: " + pv.ip);
        
        player.sendMessage(plugin.prefix() + "§a¡Cuenta verificada! Ya puedes iniciar sesión en la web.");
        player.sendMessage(plugin.prefix() + "§7Vuelve a la web y pulsa 'Ya verifiqué, entrar'.");
        return true;
    }
    
    /**
     * Attempts login. Returns session token or null.
     * REQUIRES that the user has verified their code in Minecraft first.
     */
    public synchronized LoginResult login(String username, String ip) {
        username = username.toLowerCase().trim();
        
        if (isRateLimited(ip)) {
            return new LoginResult(false, null, "Demasiadas solicitudes. Espera un momento.");
        }
        
        WebUser user = users.get(username);
        if (user == null) {
            return new LoginResult(false, null, "Usuario no encontrado. Regístrate primero.");
        }
        
        // Check if user has verified their code in Minecraft
        Long verifiedAt = verifiedPendingLogin.get(username);
        if (verifiedAt == null) {
            return new LoginResult(false, null, "Aún no has verificado el código. Usa /verificar <código> en Minecraft.");
        }
        
        // Check if verification is still valid (5 minutes)
        if (System.currentTimeMillis() - verifiedAt > 5 * 60 * 1000L) {
            verifiedPendingLogin.remove(username);
            return new LoginResult(false, null, "La verificación ha expirado. Solicita un nuevo código.");
        }
        
        // Clear the pending verification
        verifiedPendingLogin.remove(username);
        
        // Generate new session
        String token = generateSessionToken();
        sessions.put(token, new Session(username, ip, System.currentTimeMillis()));
        
        addLog("LOGIN", username, "Inicio de sesión desde IP: " + ip);
        return new LoginResult(true, token, "Sesión iniciada correctamente.");
    }
    
    /**
     * Validates a session token and returns the username, or null if invalid.
     */
    public String validateSession(String token) {
        if (token == null || token.isBlank()) return null;
        Session session = sessions.get(token);
        if (session == null) return null;
        if (System.currentTimeMillis() - session.timestamp > SESSION_DURATION_MS) {
            sessions.remove(token);
            return null;
        }
        return session.username;
    }
    
    /**
     * Logs out a session.
     */
    public void logout(String token) {
        sessions.remove(token);
    }
    
    /**
     * Gets user data for API response.
     */
    public WebUser getUser(String username) {
        return users.get(username.toLowerCase());
    }
    
    // ═══ Role Checking ══════════════════════════════════════════════════════
    
    public boolean isAdmin(String username) {
        return ADMINS.contains(username.toLowerCase());
    }
    
    public boolean isTester(String username) {
        return TESTERS.contains(username.toLowerCase()) || isAdmin(username);
    }
    
    public boolean isStaff(String username) {
        return isAdmin(username) || isTester(username);
    }
    
    public String getRole(String username) {
        if (isAdmin(username)) return "admin";
        if (isTester(username)) return "tester";
        return "user";
    }
    
    // ═══ Ticket System ══════════════════════════════════════════════════════
    
    /**
     * Creates a new tier test ticket.
     */
    public synchronized TicketResult createTicket(String username, String kit, String tier, 
                                                   String preferredSlot, String notes, String ip) {
        username = username.toLowerCase();
        
        if (isRateLimited(ip)) {
            return new TicketResult(false, null, "Demasiadas solicitudes. Espera un momento.");
        }
        
        // Check if user already has a pending ticket for this tier
        String requestKey = username + ":" + tier.toLowerCase();
        if (tierRequests.containsKey(requestKey)) {
            long lastRequest = tierRequests.get(requestKey);
            // Allow new request after 12 hours
            if (System.currentTimeMillis() - lastRequest < 12 * 60 * 60 * 1000L) {
                return new TicketResult(false, null, "Ya tienes una solicitud pendiente para este tier. Espera 12 horas para volver a solicitar.");
            }
        }
        
        // Check if user has open ticket
        for (Ticket t : tickets.values()) {
            if (t.username.equals(username) && t.status != TicketStatus.CLOSED && t.status != TicketStatus.COMPLETED) {
                return new TicketResult(false, null, "Ya tienes un ticket abierto. Espera a que se cierre antes de crear otro.");
            }
        }
        
        // Validate tier (must be HT3 or above)
        String[] validTiers = {"HT3", "HT2", "HT1", "LT5", "LT4", "LT3", "LT2", "LT1"};
        boolean validTier = false;
        for (String vt : validTiers) {
            if (vt.equalsIgnoreCase(tier)) {
                validTier = true;
                tier = vt; // Normalize case
                break;
            }
        }
        if (!validTier) {
            return new TicketResult(false, null, "Tier inválido. Tiers disponibles: HT3, HT2, HT1, LT5, LT4, LT3, LT2, LT1");
        }
        
        // Create ticket
        String ticketId = generateTicketId();
        Ticket ticket = new Ticket(
            ticketId, username, kit, tier, preferredSlot, notes,
            System.currentTimeMillis(), TicketStatus.OPEN, null, new ArrayList<>()
        );
        tickets.put(ticketId, ticket);
        tierRequests.put(requestKey, System.currentTimeMillis());
        
        save();
        
        return new TicketResult(true, ticketId, "Ticket creado correctamente. Un tester te contactará pronto.");
    }
    
    /**
     * Gets tickets for a user.
     */
    public List<Ticket> getUserTickets(String username) {
        return tickets.values().stream()
            .filter(t -> t.username.equalsIgnoreCase(username))
            .sorted((a, b) -> Long.compare(b.createdAt, a.createdAt))
            .collect(Collectors.toList());
    }
    
    /**
     * Gets all tickets (for staff).
     */
    public List<Ticket> getAllTickets(String statusFilter) {
        return tickets.values().stream()
            .filter(t -> statusFilter == null || statusFilter.isBlank() || t.status.name().equalsIgnoreCase(statusFilter))
            .sorted((a, b) -> Long.compare(b.createdAt, a.createdAt))
            .collect(Collectors.toList());
    }
    
    /**
     * Gets a specific ticket.
     */
    public Ticket getTicket(String ticketId) {
        return tickets.get(ticketId);
    }
    
    /**
     * Claims a ticket (tester takes ownership).
     */
    public synchronized boolean claimTicket(String ticketId, String testerUsername) {
        Ticket ticket = tickets.get(ticketId);
        if (ticket == null || ticket.assignedTester != null) return false;
        
        ticket.assignedTester = testerUsername;
        ticket.status = TicketStatus.IN_PROGRESS;
        
        // Add system message
        ticket.messages.add(new TicketMessage(
            "system", System.currentTimeMillis(),
            "El tester " + testerUsername + " ha tomado este ticket."
        ));
        
        save();
        return true;
    }
    
    /**
     * Updates ticket status.
     */
    public synchronized boolean updateTicketStatus(String ticketId, TicketStatus status, String updatedBy) {
        Ticket ticket = tickets.get(ticketId);
        if (ticket == null) return false;
        
        ticket.status = status;
        ticket.messages.add(new TicketMessage(
            "system", System.currentTimeMillis(),
            "Estado actualizado a " + status.displayName + " por " + updatedBy
        ));
        
        save();
        return true;
    }
    
    /**
     * Adds a chat message to a ticket.
     */
    public synchronized boolean addMessage(String ticketId, String sender, String message) {
        Ticket ticket = tickets.get(ticketId);
        if (ticket == null) return false;
        
        ticket.messages.add(new TicketMessage(sender, System.currentTimeMillis(), message));
        save();
        return true;
    }
    
    /**
     * Sets the scheduled date/time for a test.
     */
    public synchronized boolean scheduleTest(String ticketId, String dateTime, String tester) {
        Ticket ticket = tickets.get(ticketId);
        if (ticket == null) return false;
        
        ticket.scheduledTime = dateTime;
        ticket.status = TicketStatus.SCHEDULED;
        ticket.messages.add(new TicketMessage(
            "system", System.currentTimeMillis(),
            "Test programado para: " + dateTime + " (Hora España)"
        ));
        
        save();
        return true;
    }
    
    /**
     * Sets the result of a tier test.
     */
    public synchronized boolean setTestResult(String ticketId, boolean passed, String newTier, String tester) {
        Ticket ticket = tickets.get(ticketId);
        if (ticket == null) return false;
        
        if (passed) {
            ticket.status = TicketStatus.COMPLETED;
            ticket.resultTier = newTier;
            ticket.messages.add(new TicketMessage(
                "system", System.currentTimeMillis(),
                "¡APROBADO! Nuevo tier: " + newTier + " — Verificado por " + tester
            ));
            
            // Update player tier in-game by setting points to tier minimum
            WebUser user = users.get(ticket.username.toLowerCase());
            if (user != null) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    com.pvprooms.model.Tier tier = com.pvprooms.model.Tier.valueOf(newTier.toUpperCase());
                    plugin.getTierManager().setPoints(user.uuid, ticket.kit, tier.minPoints);
                });
            }
        } else {
            ticket.status = TicketStatus.CLOSED;
            ticket.messages.add(new TicketMessage(
                "system", System.currentTimeMillis(),
                "No aprobado. Sigue practicando y vuelve a intentarlo. — " + tester
            ));
        }
        
        save();
        return true;
    }
    
    // ═══ Rate Limiting ══════════════════════════════════════════════════════
    
    private boolean isRateLimited(String ip) {
        long now = System.currentTimeMillis();
        List<Long> requests = rateLimits.computeIfAbsent(ip, k -> new ArrayList<>());
        
        // Remove old requests (older than 1 minute)
        requests.removeIf(t -> now - t > 60000);
        
        if (requests.size() >= MAX_REQUESTS_PER_MINUTE) {
            return true;
        }
        
        requests.add(now);
        return false;
    }
    
    public void recordRequest(String ip) {
        isRateLimited(ip); // This also records the request
    }
    
    // ═══ Utility Methods ════════════════════════════════════════════════════
    
    private String generateVerificationCode() {
        SecureRandom random = new SecureRandom();
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 9; i++) {
            code.append(random.nextInt(10));
        }
        return code.toString();
    }
    
    private String generateSessionToken() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
    
    private String generateTicketId() {
        return "TK-" + System.currentTimeMillis() % 100000 + "-" + new SecureRandom().nextInt(1000);
    }
    
    private void cleanup() {
        long now = System.currentTimeMillis();
        
        // Clean expired verifications
        pendingVerifications.entrySet().removeIf(e -> now - e.getValue().timestamp > VERIFICATION_TIMEOUT_MS);
        
        // Clean expired sessions
        sessions.entrySet().removeIf(e -> now - e.getValue().timestamp > SESSION_DURATION_MS);
        
        // Clean old rate limit data
        rateLimits.values().forEach(list -> list.removeIf(t -> now - t > 60000));
        rateLimits.entrySet().removeIf(e -> e.getValue().isEmpty());
    }
    
    // ═══ Persistence ════════════════════════════════════════════════════════
    
    public void load() {
        if (!dataFile.exists()) return;
        
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(dataFile);
        
        // Load users
        if (cfg.contains("users")) {
            for (String key : cfg.getConfigurationSection("users").getKeys(false)) {
                String path = "users." + key;
                WebUser user = new WebUser(
                    cfg.getString(path + ".username"),
                    UUID.fromString(cfg.getString(path + ".uuid")),
                    cfg.getString(path + ".passwordHash"),
                    cfg.getString(path + ".registeredIp"),
                    cfg.getLong(path + ".registeredAt")
                );
                users.put(key, user);
            }
        }
        
        // Load tickets
        if (cfg.contains("tickets")) {
            for (String ticketId : cfg.getConfigurationSection("tickets").getKeys(false)) {
                String path = "tickets." + ticketId;
                List<TicketMessage> messages = new ArrayList<>();
                if (cfg.contains(path + ".messages")) {
                    for (String msgKey : cfg.getConfigurationSection(path + ".messages").getKeys(false)) {
                        String msgPath = path + ".messages." + msgKey;
                        messages.add(new TicketMessage(
                            cfg.getString(msgPath + ".sender"),
                            cfg.getLong(msgPath + ".timestamp"),
                            cfg.getString(msgPath + ".message")
                        ));
                    }
                }
                
                Ticket ticket = new Ticket(
                    ticketId,
                    cfg.getString(path + ".username"),
                    cfg.getString(path + ".kit"),
                    cfg.getString(path + ".requestedTier"),
                    cfg.getString(path + ".preferredSlot"),
                    cfg.getString(path + ".notes"),
                    cfg.getLong(path + ".createdAt"),
                    TicketStatus.valueOf(cfg.getString(path + ".status", "OPEN")),
                    cfg.getString(path + ".assignedTester"),
                    messages
                );
                ticket.scheduledTime = cfg.getString(path + ".scheduledTime");
                ticket.resultTier = cfg.getString(path + ".resultTier");
                tickets.put(ticketId, ticket);
            }
        }
        
        // Load tier requests
        if (cfg.contains("tierRequests")) {
            for (String key : cfg.getConfigurationSection("tierRequests").getKeys(false)) {
                tierRequests.put(key.replace("_", ":"), cfg.getLong("tierRequests." + key));
            }
        }
        
        // Load IP registrations
        if (cfg.contains("ipRegistrations")) {
            for (String ip : cfg.getConfigurationSection("ipRegistrations").getKeys(false)) {
                Set<String> usernames = new HashSet<>(cfg.getStringList("ipRegistrations." + ip));
                ipRegistrations.put(ip.replace("_", "."), usernames);
            }
        }
        
        plugin.getLogger().info("[TicketManager] Loaded " + users.size() + " users, " + tickets.size() + " tickets");
    }
    
    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        
        // Save users
        for (var entry : users.entrySet()) {
            WebUser user = entry.getValue();
            String path = "users." + entry.getKey();
            cfg.set(path + ".username", user.username);
            cfg.set(path + ".uuid", user.uuid.toString());
            cfg.set(path + ".passwordHash", user.passwordHash);
            cfg.set(path + ".registeredIp", user.registeredIp);
            cfg.set(path + ".registeredAt", user.registeredAt);
        }
        
        // Save tickets
        for (var entry : tickets.entrySet()) {
            Ticket ticket = entry.getValue();
            String path = "tickets." + entry.getKey();
            cfg.set(path + ".username", ticket.username);
            cfg.set(path + ".kit", ticket.kit);
            cfg.set(path + ".requestedTier", ticket.requestedTier);
            cfg.set(path + ".preferredSlot", ticket.preferredSlot);
            cfg.set(path + ".notes", ticket.notes);
            cfg.set(path + ".createdAt", ticket.createdAt);
            cfg.set(path + ".status", ticket.status.name());
            cfg.set(path + ".assignedTester", ticket.assignedTester);
            cfg.set(path + ".scheduledTime", ticket.scheduledTime);
            cfg.set(path + ".resultTier", ticket.resultTier);
            
            int i = 0;
            for (TicketMessage msg : ticket.messages) {
                String msgPath = path + ".messages." + i++;
                cfg.set(msgPath + ".sender", msg.sender);
                cfg.set(msgPath + ".timestamp", msg.timestamp);
                cfg.set(msgPath + ".message", msg.message);
            }
        }
        
        // Save tier requests
        for (var entry : tierRequests.entrySet()) {
            cfg.set("tierRequests." + entry.getKey().replace(":", "_"), entry.getValue());
        }
        
        // Save IP registrations
        for (var entry : ipRegistrations.entrySet()) {
            cfg.set("ipRegistrations." + entry.getKey().replace(".", "_"), new ArrayList<>(entry.getValue()));
        }
        
        try {
            cfg.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("[TicketManager] Error saving: " + e.getMessage());
        }
    }
    
    // ═══ Data Classes ═══════════════════════════════════════════════════════
    
    public static class WebUser {
        public final String username;
        public final UUID uuid;
        public final String passwordHash;
        public final String registeredIp;
        public final long registeredAt;
        
        public WebUser(String username, UUID uuid, String passwordHash, String registeredIp, long registeredAt) {
            this.username = username;
            this.uuid = uuid;
            this.passwordHash = passwordHash;
            this.registeredIp = registeredIp;
            this.registeredAt = registeredAt;
        }
    }
    
    public static class Session {
        public final String username;
        public final String ip;
        public final long timestamp;
        
        public Session(String username, String ip, long timestamp) {
            this.username = username;
            this.ip = ip;
            this.timestamp = timestamp;
        }
    }
    
    public static class PendingVerification {
        public final String username;
        public final UUID uuid;
        public final String ip;
        public final long timestamp;
        
        public PendingVerification(String username, UUID uuid, String ip, long timestamp) {
            this.username = username;
            this.uuid = uuid;
            this.ip = ip;
            this.timestamp = timestamp;
        }
    }
    
    public static class Ticket {
        public final String id;
        public final String username;
        public final String kit;
        public final String requestedTier;
        public final String preferredSlot;
        public final String notes;
        public final long createdAt;
        public TicketStatus status;
        public String assignedTester;
        public String scheduledTime;
        public String resultTier;
        public final List<TicketMessage> messages;
        
        public Ticket(String id, String username, String kit, String requestedTier,
                     String preferredSlot, String notes, long createdAt, 
                     TicketStatus status, String assignedTester, List<TicketMessage> messages) {
            this.id = id;
            this.username = username;
            this.kit = kit;
            this.requestedTier = requestedTier;
            this.preferredSlot = preferredSlot;
            this.notes = notes;
            this.createdAt = createdAt;
            this.status = status;
            this.assignedTester = assignedTester;
            this.messages = messages;
        }
    }
    
    public static class TicketMessage {
        public final String sender;
        public final long timestamp;
        public final String message;
        
        public TicketMessage(String sender, long timestamp, String message) {
            this.sender = sender;
            this.timestamp = timestamp;
            this.message = message;
        }
    }
    
    public enum TicketStatus {
        OPEN("Abierto", "#3b82f6"),
        IN_PROGRESS("En Proceso", "#f59e0b"),
        SCHEDULED("Programado", "#8b5cf6"),
        TESTING("En Test", "#ec4899"),
        COMPLETED("Completado", "#22c55e"),
        CLOSED("Cerrado", "#6b7280");
        
        public final String displayName;
        public final String color;
        
        TicketStatus(String displayName, String color) {
            this.displayName = displayName;
            this.color = color;
        }
    }
    
    // ═══ Admin Methods ══════════════════════════════════════════════════════
    
    /** Activity logs for admin panel */
    private final List<ActivityLog> activityLogs = Collections.synchronizedList(new ArrayList<>());
    
    /** Get all registered users */
    public Collection<WebUser> getAllUsers() {
        return Collections.unmodifiableCollection(users.values());
    }
    
    /** Delete a user account */
    public boolean deleteUser(String username) {
        username = username.toLowerCase();
        WebUser removed = users.remove(username);
        if (removed != null) {
            // Also invalidate their sessions
            invalidateUserSessions(username);
            save();
            return true;
        }
        return false;
    }
    
    /** Invalidate all sessions for a user */
    public void invalidateUserSessions(String username) {
        username = username.toLowerCase();
        String finalUsername = username;
        sessions.entrySet().removeIf(e -> e.getValue().username.equalsIgnoreCase(finalUsername));
    }
    
    /** Add an activity log entry */
    public void addLog(String type, String username, String details) {
        activityLogs.add(0, new ActivityLog(System.currentTimeMillis(), type, username, details));
        // Keep only last 200 logs
        while (activityLogs.size() > 200) {
            activityLogs.remove(activityLogs.size() - 1);
        }
    }
    
    /** Get recent activity logs */
    public List<ActivityLog> getActivityLogs(int limit) {
        return activityLogs.stream().limit(limit).toList();
    }
    
    /** Activity log entry */
    public static class ActivityLog {
        public final long timestamp;
        public final String type;
        public final String username;
        public final String details;
        
        public ActivityLog(long timestamp, String type, String username, String details) {
            this.timestamp = timestamp;
            this.type = type;
            this.username = username;
            this.details = details;
        }
    }
    
    // ═══ Result Classes ═════════════════════════════════════════════════════
    
    public record RegistrationResult(boolean success, String code, String message) {}
    public record LoginResult(boolean success, String token, String message) {}
    public record TicketResult(boolean success, String ticketId, String message) {}
}

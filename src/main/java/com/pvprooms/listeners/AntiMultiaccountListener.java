package com.pvprooms.listeners;

import com.pvprooms.PvPRoomsPro;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prevents multiaccounting by blocking duplicate IPs.
 * Only one player per IP address is allowed at a time.
 */
public class AntiMultiaccountListener implements Listener {

    private final PvPRoomsPro plugin;
    
    // Active connections: IP -> Player UUID
    private final Map<String, UUID> activeIpToPlayer = new ConcurrentHashMap<>();
    
    // Whitelisted IPs (admins, staff, etc.)
    private final Set<String> whitelistedIps = ConcurrentHashMap.newKeySet();
    
    // Whitelisted UUIDs (bypass for specific players)
    private final Set<UUID> whitelistedPlayers = ConcurrentHashMap.newKeySet();
    
    // Known accounts per IP (for logging/detection)
    private final Map<String, Set<UUID>> ipHistory = new ConcurrentHashMap<>();
    
    private final File dataFile;

    public AntiMultiaccountListener(PvPRoomsPro plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "ip_data.yml");
        load();
        
        // Populate active connections with currently online players
        for (Player p : Bukkit.getOnlinePlayers()) {
            String ip = getIp(p);
            if (ip != null) {
                activeIpToPlayer.put(ip, p.getUniqueId());
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        InetAddress address = event.getAddress();
        if (address == null) return;
        
        String ip = address.getHostAddress();
        UUID uuid = event.getUniqueId();
        String name = event.getName();
        
        // Check if IP is whitelisted
        if (whitelistedIps.contains(ip)) return;
        
        // Check if player is whitelisted
        if (whitelistedPlayers.contains(uuid)) return;
        
        // Check if this IP is already connected with a different account
        UUID existingPlayer = activeIpToPlayer.get(ip);
        if (existingPlayer != null && !existingPlayer.equals(uuid)) {
            // Another account with same IP is already online
            Player online = Bukkit.getPlayer(existingPlayer);
            String onlineName = online != null ? online.getName() : "otro jugador";
            
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    "§c§l⛔ MULTICUENTAS PROHIBIDAS\n\n" +
                    "§fYa hay una cuenta conectada desde tu IP.\n" +
                    "§7Cuenta activa: §e" + onlineName + "\n\n" +
                    "§cSi crees que esto es un error, contacta a un admin.");
            
            // Log the attempt
            plugin.getLogger().warning("[AntiMultiaccount] Bloqueado " + name + " (" + uuid + ") - IP " + ip + " ya usada por " + onlineName);
            return;
        }
        
        // Record this IP-UUID association for history
        ipHistory.computeIfAbsent(ip, k -> ConcurrentHashMap.newKeySet()).add(uuid);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String ip = getIp(player);
        
        if (ip != null) {
            activeIpToPlayer.put(ip, player.getUniqueId());
            
            // Check if this IP has multiple known accounts (warn admins)
            Set<UUID> knownAccounts = ipHistory.get(ip);
            if (knownAccounts != null && knownAccounts.size() > 1) {
                // Notify admins
                String msg = plugin.prefix() + "§c[AntiMC] §e" + player.getName() + 
                        " §7tiene §c" + knownAccounts.size() + " cuentas §7registradas en su IP.";
                for (Player admin : Bukkit.getOnlinePlayers()) {
                    if (admin.hasPermission("pvprooms.admin")) {
                        admin.sendMessage(msg);
                    }
                }
            }
        }
        
        saveAsync();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        String ip = getIp(player);
        
        if (ip != null) {
            // Only remove if this player owns this IP slot
            activeIpToPlayer.remove(ip, player.getUniqueId());
        }
    }

    private String getIp(Player player) {
        if (player.getAddress() == null) return null;
        return player.getAddress().getAddress().getHostAddress();
    }

    // ══════════════════════════════════════════════════════════════════════
    // Admin commands
    // ══════════════════════════════════════════════════════════════════════

    public void whitelistIp(String ip) {
        whitelistedIps.add(ip);
        saveAsync();
    }

    public void unwhitelistIp(String ip) {
        whitelistedIps.remove(ip);
        saveAsync();
    }

    public void whitelistPlayer(UUID uuid) {
        whitelistedPlayers.add(uuid);
        saveAsync();
    }

    public void unwhitelistPlayer(UUID uuid) {
        whitelistedPlayers.remove(uuid);
        saveAsync();
    }

    public Set<String> getWhitelistedIps() {
        return Collections.unmodifiableSet(whitelistedIps);
    }

    public Set<UUID> getWhitelistedPlayers() {
        return Collections.unmodifiableSet(whitelistedPlayers);
    }

    public Set<UUID> getAccountsForIp(String ip) {
        return ipHistory.getOrDefault(ip, Collections.emptySet());
    }

    public String getPlayerIp(Player player) {
        return getIp(player);
    }

    public Map<String, Set<UUID>> getIpHistory() {
        return Collections.unmodifiableMap(ipHistory);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Persistence
    // ══════════════════════════════════════════════════════════════════════

    private void load() {
        if (!dataFile.exists()) return;
        
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(dataFile);
        
        // Load whitelisted IPs
        whitelistedIps.addAll(cfg.getStringList("whitelisted_ips"));
        
        // Load whitelisted players
        for (String uuidStr : cfg.getStringList("whitelisted_players")) {
            try {
                whitelistedPlayers.add(UUID.fromString(uuidStr));
            } catch (Exception ignored) {}
        }
        
        // Load IP history
        if (cfg.isConfigurationSection("ip_history")) {
            for (String ip : cfg.getConfigurationSection("ip_history").getKeys(false)) {
                Set<UUID> uuids = ConcurrentHashMap.newKeySet();
                for (String uuidStr : cfg.getStringList("ip_history." + ip)) {
                    try {
                        uuids.add(UUID.fromString(uuidStr));
                    } catch (Exception ignored) {}
                }
                if (!uuids.isEmpty()) {
                    ipHistory.put(ip, uuids);
                }
            }
        }
        
        plugin.getLogger().info("[AntiMultiaccount] Cargadas " + whitelistedIps.size() + " IPs en whitelist, " + 
                ipHistory.size() + " IPs con historial.");
    }

    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        
        cfg.set("whitelisted_ips", new ArrayList<>(whitelistedIps));
        cfg.set("whitelisted_players", whitelistedPlayers.stream().map(UUID::toString).toList());
        
        for (Map.Entry<String, Set<UUID>> entry : ipHistory.entrySet()) {
            cfg.set("ip_history." + entry.getKey(), 
                    entry.getValue().stream().map(UUID::toString).toList());
        }
        
        try {
            cfg.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("[AntiMultiaccount] Error guardando datos: " + e.getMessage());
        }
    }

    private void saveAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::save);
    }
}

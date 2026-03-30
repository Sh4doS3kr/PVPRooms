package com.pvprooms.managers;

import com.pvprooms.PvPRoomsPro;
import com.pvprooms.model.ArmorPiece;
import com.pvprooms.model.Trim;
import org.bukkit.Bukkit;
import org.bukkit.Registry;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

/**
 * Manages per-player armor trims.
 * Persistence: plugins/PvPRoomsPro/trims.yml
 *
 * SISTEMA HÍBRIDO:
 * - PATRONES: Controlados por administradores (solo admins dan acceso)
 * - MATERIALES: Libres para todos los jugadores
 * 
 * ACCESO HÍBRIDO: Si tienes un patrón, puedes usarlo con CUALQUIER material.
 * Ejemplo: Si admin te da "Vex", puedes usar Vex con Diamond, Netherite, Gold, etc.
 *
 * Priority order when applying trims:
 *   1. Player personal trims (highest — override everything)
 *   2. Kit-default trims
 */
public class TrimManager {

    private final PvPRoomsPro plugin;
    private final File trimsFile;

    /** Maps player UUID → (armor piece → unlocked trims set) */
    private final Map<UUID, Map<ArmorPiece, Set<String>>> unlockedTrims = new HashMap<>();

    /** Maps player UUID → (armor piece → currently equipped trim) */
    private final Map<UUID, Map<ArmorPiece, Trim>> playerTrims = new HashMap<>();

    /** Ordered list of all valid TrimMaterial keys from the Bukkit Registry. */
    private final List<String> materialKeys = new ArrayList<>();
    /** Ordered list of all valid TrimPattern keys from the Bukkit Registry. */
    private final List<String> patternKeys  = new ArrayList<>();
    
    /** Shared random instance for consistent randomness */
    private final Random random = new Random();

    private static final List<String> LEGENDARY_PATTERNS = List.of(
            "silence", "vex", "spire", "shaper", "raiser", "host", "flow", "bolt"
    );
    private static final List<String> NORMAL_PATTERNS = List.of(
            "coast", "dune", "eye", "rib", "sentry", "snout", "tide", "ward", "wayfinder", "wild"
    );

    // Patrones recomendados por pieza de armadura (basado en estética)
    private static final Map<ArmorPiece, List<String>> PIECE_PREFERRED_PATTERNS = Map.of(
            ArmorPiece.HELMET, List.of("eye", "ward", "snout", "sentry", "wayfinder"),
            ArmorPiece.CHESTPLATE, List.of("rib", "spire", "shaper", "raiser", "host"),
            ArmorPiece.LEGGINGS, List.of("coast", "tide", "flow", "wild", "dune"),
            ArmorPiece.BOOTS, List.of("bolt", "silence", "vex", "ward", "rib")
    );

    /** Maps pattern key → display colour code for GUI labels. */
    private static final Map<String, String> PATTERN_COLOURS = Map.ofEntries(
            Map.entry("bolt",      "§e"), Map.entry("coast",    "§b"),
            Map.entry("dune",      "§6"), Map.entry("eye",       "§5"),
            Map.entry("flow",      "§a"), Map.entry("host",      "§d"),
            Map.entry("raiser",    "§2"), Map.entry("rib",       "§f"),
            Map.entry("sentry",    "§7"), Map.entry("shaper",    "§3"),
            Map.entry("silence",   "§8"), Map.entry("snout",     "§c"),
            Map.entry("spire",     "§4"), Map.entry("tide",      "§9"),
            Map.entry("vex",       "§5"), Map.entry("ward",      "§1"),
            Map.entry("wayfinder", "§6"), Map.entry("wild",      "§2")
    );

    /** Maps material key → display colour code. */
    private static final Map<String, String> MATERIAL_COLOURS = Map.ofEntries(
            Map.entry("amethyst",  "§5"), Map.entry("copper",    "§6"),
            Map.entry("diamond",   "§b"), Map.entry("emerald",   "§a"),
            Map.entry("gold",      "§e"), Map.entry("iron",      "§7"),
            Map.entry("lapis",     "§9"), Map.entry("netherite", "§8"),
            Map.entry("quartz",    "§f"), Map.entry("redstone",  "§c")
    );

    public TrimManager(PvPRoomsPro plugin) {
        this.plugin    = plugin;
        this.trimsFile = new File(plugin.getDataFolder(), "trims.yml");
        buildRegistryLists();
        load();
    }

    // ── Registry helpers ──────────────────────────────────────────────────

    private void buildRegistryLists() {
        for (TrimMaterial m : Registry.TRIM_MATERIAL) materialKeys.add(m.getKey().getKey());
        for (TrimPattern  p : Registry.TRIM_PATTERN)  patternKeys.add(p.getKey().getKey());
        Collections.sort(materialKeys);
        Collections.sort(patternKeys);
    }

    public List<String> getMaterialKeys()             { return Collections.unmodifiableList(materialKeys); }
    public List<String> getPatternKeys()              { return Collections.unmodifiableList(patternKeys); }
    public List<String> getLegendaryPatternKeys()     { return LEGENDARY_PATTERNS; }
    public List<String> getNormalPatternKeys()        { return NORMAL_PATTERNS; }
    public String patternColour(String key)           { return PATTERN_COLOURS.getOrDefault(key, "§f"); }
    public String materialColour(String key)          { return MATERIAL_COLOURS.getOrDefault(key, "§f"); }

    // ── Player trim CRUD ──────────────────────────────────────────────────

    /** Returns all trims for a player (never null, may be empty). */
    public Map<ArmorPiece, Trim> getPlayerTrims(UUID uuid) {
        return Collections.unmodifiableMap(playerTrims.getOrDefault(uuid, Collections.emptyMap()));
    }

    /** Returns the trim for a specific piece, or null. */
    public Trim getPlayerTrim(UUID uuid, ArmorPiece piece) {
        Map<ArmorPiece, Trim> map = playerTrims.get(uuid);
        return map != null ? map.get(piece) : null;
    }

    /** Sets (or replaces) the trim for a single armor piece and persists. */
    public void setPlayerTrim(UUID uuid, ArmorPiece piece, Trim trim) {
        playerTrims.computeIfAbsent(uuid, k -> new EnumMap<>(ArmorPiece.class)).put(piece, trim);
        save();
    }

    /** Removes the trim for a single armor piece and persists. */
    public void clearPlayerTrim(UUID uuid, ArmorPiece piece) {
        Map<ArmorPiece, Trim> map = playerTrims.get(uuid);
        if (map != null) {
            map.remove(piece);
            if (map.isEmpty()) playerTrims.remove(uuid);
        }
        save();
    }

    /** Removes all personal trims for a player. */
    public void clearAllTrims(UUID uuid) {
        playerTrims.remove(uuid);
        save();
    }

    // ── Unlocked trims system ─────────────────────────────────────────────────

    /** Returns all unlocked trims for a player and armor piece. */
    public Set<String> getUnlockedTrims(UUID uuid, ArmorPiece piece) {
        // SISTEMA HÍBRIDO: Patrones controlados, materiales libres
        Map<ArmorPiece, Set<String>> playerUnlocks = unlockedTrims.get(uuid);
        if (playerUnlocks == null) return Collections.emptySet();
        return Collections.unmodifiableSet(playerUnlocks.getOrDefault(piece, Collections.emptySet()));
    }

    /** Checks if a player has unlocked a specific trim pattern for a piece. */
    public boolean hasUnlockedTrim(UUID uuid, ArmorPiece piece, String pattern) {
        // SISTEMA HÍBRIDO: Verificar si tiene el patrón desbloqueado
        return getUnlockedTrims(uuid, piece).contains(pattern.toLowerCase());
    }

    /** Unlocks a trim pattern for a player and armor piece. */
    public void unlockTrim(UUID uuid, ArmorPiece piece, String pattern) {
        unlockedTrims.computeIfAbsent(uuid, k -> new EnumMap<>(ArmorPiece.class))
                .computeIfAbsent(piece, p -> new HashSet<>())
                .add(pattern.toLowerCase());
        save();
    }

    /** 
     * Unlocks a FULL trim (pattern + material combo) for a player.
     * Stores as "pattern:material" in the unlocked set.
     */
    public void unlockFullTrim(UUID uuid, ArmorPiece piece, Trim trim) {
        Set<String> playerSet = unlockedTrims.computeIfAbsent(uuid, k -> new EnumMap<>(ArmorPiece.class))
                .computeIfAbsent(piece, p -> new HashSet<>());
        // Store both the pattern alone AND the full combo
        playerSet.add(trim.getPattern().toLowerCase());
        playerSet.add(trim.getPattern().toLowerCase() + ":" + trim.getMaterial().toLowerCase());
        save();
    }

    /** Checks if a player has unlocked a specific pattern+material combo. */
    public boolean hasUnlockedFullTrim(UUID uuid, ArmorPiece piece, String pattern, String material) {
        Set<String> unlocked = getUnlockedTrims(uuid, piece);
        return unlocked.contains(pattern.toLowerCase() + ":" + material.toLowerCase());
    }

    /** Unlocks multiple trim patterns at once. */
    public void unlockTrims(UUID uuid, ArmorPiece piece, Set<String> patterns) {
        Set<String> playerSet = unlockedTrims.computeIfAbsent(uuid, k -> new EnumMap<>(ArmorPiece.class))
                .computeIfAbsent(piece, p -> new HashSet<>());
        patterns.forEach(p -> playerSet.add(p.toLowerCase()));
        save();
    }

    // ── Administrative trim access ─────────────────────────────────────────────

    /**
     * Administrative method to give a specific trim pattern to a player.
     * Only admins should use this method.
     * Players can choose any material for the given pattern.
     */
    public void adminGiveTrim(UUID playerUuid, ArmorPiece piece, String pattern) {
        unlockTrim(playerUuid, piece, pattern);
        
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null && player.isOnline()) {
            String patColor = patternColour(pattern);
            player.sendMessage(plugin.prefix() + "§a§l✦ ¡Un administrador te ha dado un patrón!");
            player.sendMessage(plugin.prefix() + "§7Patrón recibido: " + patColor + cap(pattern));
            player.sendMessage(plugin.prefix() + "§7Pieza: " + piece.getDisplayName());
            player.sendMessage(plugin.prefix() + "§7§l✨ ¡Puedes usarlo con CUALQUIER material!");
            player.sendMessage(plugin.prefix() + "§eUsa §7/trim §epara equiparlo.");
        }
    }

    /**
     * Administrative method to give multiple trims to a player.
     * Only admins should use this method.
     */
    public void adminGiveMultipleTrims(UUID playerUuid, Map<ArmorPiece, List<String>> trims) {
        int totalGiven = 0;
        
        for (Map.Entry<ArmorPiece, List<String>> entry : trims.entrySet()) {
            ArmorPiece piece = entry.getKey();
            List<String> patterns = entry.getValue();
            
            if (!patterns.isEmpty()) {
                unlockTrims(playerUuid, piece, new HashSet<>(patterns));
                totalGiven += patterns.size();
            }
        }
        
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null && player.isOnline()) {
            player.sendMessage(plugin.prefix() + "§a§l✦ ¡Un administrador te ha dado " + totalGiven + " trims!");
            player.sendMessage(plugin.prefix() + "§7Usa §7/trim §epara ver y equipar tus nuevos trims.");
        }
    }

    /**
     * Administrative method to give all trims to a player for a specific piece.
     * Only admins should use this method.
     */
    public void adminGiveAllTrimsForPiece(UUID playerUuid, ArmorPiece piece) {
        Set<String> allPatterns = new HashSet<>();
        allPatterns.addAll(NORMAL_PATTERNS);
        allPatterns.addAll(LEGENDARY_PATTERNS);
        
        unlockTrims(playerUuid, piece, allPatterns);
        
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null && player.isOnline()) {
            player.sendMessage(plugin.prefix() + "§a§l✦ ¡Un administrador te ha dado TODOS los trims!");
            player.sendMessage(plugin.prefix() + "§7Pieza: " + piece.getDisplayName());
            player.sendMessage(plugin.prefix() + "§7Trims recibidos: " + allPatterns.size() + " patrones");
            player.sendMessage(plugin.prefix() + "§eUsa §7/trim §epara equiparlos.");
        }
    }

    /**
     * Administrative method to remove a trim from a player.
     * Only admins should use this method.
     */
    public void adminRemoveTrim(UUID playerUuid, ArmorPiece piece, String pattern) {
        Map<ArmorPiece, Set<String>> playerUnlocks = unlockedTrims.get(playerUuid);
        if (playerUnlocks != null) {
            Set<String> pieceTrims = playerUnlocks.get(piece);
            if (pieceTrims != null) {
                pieceTrims.remove(pattern.toLowerCase());
                if (pieceTrims.isEmpty()) {
                    playerUnlocks.remove(piece);
                }
                if (playerUnlocks.isEmpty()) {
                    unlockedTrims.remove(playerUuid);
                }
                save();
                
                Player player = Bukkit.getPlayer(playerUuid);
                if (player != null && player.isOnline()) {
                    player.sendMessage(plugin.prefix() + "§c§l✦ Un administrador te ha quitado un trim.");
                    player.sendMessage(plugin.prefix() + "§7Trim removido: " + patternColour(pattern) + cap(pattern));
                    player.sendMessage(plugin.prefix() + "§7Pieza: " + piece.getDisplayName());
                }
            }
        }
    }

    private static String cap(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ── Random trim generation ───────────────────────────────────────────────

    /**
     * Generates a random trim for a specific armor piece.
     * @param piece The armor piece to generate a trim for
     * @param legendary Whether to generate legendary patterns only
     * @return A random Trim object
     */
    public Trim randomTrimForPiece(ArmorPiece piece, boolean legendary) {
        // Choose pattern based on legendary flag
        List<String> availablePatterns = legendary ? LEGENDARY_PATTERNS : NORMAL_PATTERNS;
        String pattern = availablePatterns.get(random.nextInt(availablePatterns.size()));
        
        // Choose material (all materials available)
        String material = materialKeys.get(random.nextInt(materialKeys.size()));
        
        return new Trim(material, pattern);
    }

    /**
     * Generates a completely random trim.
     * @param legendary Whether to generate legendary patterns only
     * @return A random Trim object
     */
    public Trim randomTrim(boolean legendary) {
        // Choose pattern based on legendary flag
        List<String> availablePatterns = legendary ? LEGENDARY_PATTERNS : NORMAL_PATTERNS;
        String pattern = availablePatterns.get(random.nextInt(availablePatterns.size()));
        
        // Choose material (all materials available)
        String material = materialKeys.get(random.nextInt(materialKeys.size()));
        
        return new Trim(material, pattern);
    }

    // ── Applying trims ────────────────────────────────────────────────────

    /**
     * Applies player personal trims to their current equipped armor.
     * Call this after applyKit() to overlay personal trims.
     */
    public void applyPlayerTrims(Player player) {
        ItemStack[] armor = player.getInventory().getArmorContents();
        getPlayerTrims(player.getUniqueId()).forEach((piece, trim) ->
                applyTrimToSlot(armor, piece.getArmorSlot(), trim));
        player.getInventory().setArmorContents(armor);
        player.updateInventory();
    }

    /**
     * Applies a single trim to the player's currently equipped armor piece.
     * This method works instantly both in lobby and during matches.
     */
    public void applyTrimInstantly(Player player, ArmorPiece piece, Trim trim) {
        ItemStack[] armor = player.getInventory().getArmorContents();
        applyTrimToSlot(armor, piece.getArmorSlot(), trim);
        player.getInventory().setArmorContents(armor);
        player.updateInventory();
        
        // Also save the trim to player's personal trims
        setPlayerTrim(player.getUniqueId(), piece, trim);
    }

    /**
     * Applies all player trims instantly. Works both in lobby and during matches.
     */
    public void applyAllTrimsInstantly(Player player) {
        Map<ArmorPiece, Trim> trims = getPlayerTrims(player.getUniqueId());
        if (trims.isEmpty()) return;
        
        ItemStack[] armor = player.getInventory().getArmorContents();
        trims.forEach((piece, trim) -> applyTrimToSlot(armor, piece.getArmorSlot(), trim));
        player.getInventory().setArmorContents(armor);
        player.updateInventory();
    }

    /**
     * Applies kit-default trims first, then overlays personal trims on top.
     * Called from KitManager.applyKit() after setting armor contents.
     */
    public void applyTrimsForKit(Player player, Map<ArmorPiece, Trim> kitTrims) {
        ItemStack[] armor = player.getInventory().getArmorContents();
        if (kitTrims != null) {
            kitTrims.forEach((piece, trim) ->
                    applyTrimToSlot(armor, piece.getArmorSlot(), trim));
        }
        getPlayerTrims(player.getUniqueId()).forEach((piece, trim) ->
                applyTrimToSlot(armor, piece.getArmorSlot(), trim));
        player.getInventory().setArmorContents(armor);
        player.updateInventory();
    }

    private void applyTrimToSlot(ItemStack[] armor, int slot, Trim trim) {
        ItemStack item = armor[slot];
        if (item == null || item.getType().isAir()) return;
        if (!(item.getItemMeta() instanceof ArmorMeta armorMeta)) return;
        TrimMaterial material = trim.toBukkitMaterial();
        TrimPattern  pattern  = trim.toBukkitPattern();
        if (material == null || pattern == null) return;
        armorMeta.setTrim(new ArmorTrim(material, pattern));
        item.setItemMeta(armorMeta);
    }

    // ── Persistence ───────────────────────────────────────────────────────

    public void load() {
        playerTrims.clear();
        unlockedTrims.clear();
        if (!trimsFile.exists()) return;
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(trimsFile);
        
        // Load equipped trims
        if (cfg.contains("trims")) {
            for (String uuidStr : cfg.getConfigurationSection("trims").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    Map<ArmorPiece, Trim> map = new EnumMap<>(ArmorPiece.class);
                    for (ArmorPiece piece : ArmorPiece.values()) {
                        String val = cfg.getString("trims." + uuidStr + "." + piece.name().toLowerCase());
                        Trim t = Trim.fromString(val);
                        if (t != null) map.put(piece, t);
                    }
                    if (!map.isEmpty()) playerTrims.put(uuid, map);
                } catch (IllegalArgumentException ignored) {}
            }
        }
        
        // Load unlocked trims (por pieza)
        if (cfg.contains("unlocked")) {
            for (String uuidStr : cfg.getConfigurationSection("unlocked").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    Map<ArmorPiece, Set<String>> unlockedMap = new EnumMap<>(ArmorPiece.class);
                    for (ArmorPiece piece : ArmorPiece.values()) {
                        List<String> patterns = cfg.getStringList("unlocked." + uuidStr + "." + piece.name().toLowerCase());
                        if (!patterns.isEmpty()) {
                            unlockedMap.put(piece, new HashSet<>(patterns));
                        }
                    }
                    if (!unlockedMap.isEmpty()) unlockedTrims.put(uuid, unlockedMap);
                } catch (IllegalArgumentException ignored) {}
            }
        }
        
        plugin.getLogger().info("[TrimManager] Loaded trims for " + playerTrims.size() + " player(s).");
    }

    public void save() {
        FileConfiguration cfg = new YamlConfiguration();
        
        // Save equipped trims
        for (Map.Entry<UUID, Map<ArmorPiece, Trim>> entry : playerTrims.entrySet()) {
            String base = "trims." + entry.getKey();
            entry.getValue().forEach((piece, trim) ->
                    cfg.set(base + "." + piece.name().toLowerCase(), trim.toString()));
        }
        
        // Save unlocked trims (por pieza)
        for (Map.Entry<UUID, Map<ArmorPiece, Set<String>>> entry : unlockedTrims.entrySet()) {
            String base = "unlocked." + entry.getKey();
            entry.getValue().forEach((piece, patterns) ->
                    cfg.set(base + "." + piece.name().toLowerCase(), new ArrayList<>(patterns)));
        }
        
        try {
            cfg.save(trimsFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "[TrimManager] Failed to save trims.yml", e);
        }
    }
}

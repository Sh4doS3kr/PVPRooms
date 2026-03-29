package com.pvprooms.managers;

import com.pvprooms.PvPRoomsPro;
import com.pvprooms.model.ArmorPiece;
import com.pvprooms.model.Trim;
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
 * Manages per-player armor trims and crate reward logic.
 * Persistence: plugins/PvPRoomsPro/trims.yml
 *
 * Priority order when applying trims:
 *   1. Player personal trims (highest — override everything)
 *   2. Kit-default trims
 */
public class TrimManager {

    private final PvPRoomsPro plugin;
    private final File trimsFile;

    /** UUID → (ArmorPiece → Trim) for personal trims. */
    private final Map<UUID, Map<ArmorPiece, Trim>> playerTrims = new HashMap<>();

    /** Ordered list of all valid TrimMaterial keys from the Bukkit Registry. */
    private final List<String> materialKeys = new ArrayList<>();
    /** Ordered list of all valid TrimPattern keys from the Bukkit Registry. */
    private final List<String> patternKeys  = new ArrayList<>();

    private static final List<String> LEGENDARY_PATTERNS = List.of(
            "silence", "vex", "spire", "shaper", "raiser", "host", "flow", "bolt"
    );
    private static final List<String> NORMAL_PATTERNS = List.of(
            "coast", "dune", "eye", "rib", "sentry", "snout", "tide", "ward", "wayfinder", "wild"
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

    // ── Random crate reward ───────────────────────────────────────────────

    /**
     * Generates a random trim.
     *
     * @param legendary if true selects from legendary patterns; otherwise normal
     */
    public Trim randomTrim(boolean legendary) {
        List<String> pool    = legendary ? LEGENDARY_PATTERNS : NORMAL_PATTERNS;
        List<String> matPool = materialKeys.isEmpty() ? List.of("iron") : materialKeys;
        Random rng = new Random();
        String pattern  = pool.get(rng.nextInt(pool.size()));
        String material = matPool.get(rng.nextInt(matPool.size()));
        return new Trim(material, pattern);
    }

    // ── Persistence ───────────────────────────────────────────────────────

    public void load() {
        playerTrims.clear();
        if (!trimsFile.exists()) return;
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(trimsFile);
        if (!cfg.contains("trims")) return;
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
        plugin.getLogger().info("[TrimManager] Loaded trims for " + playerTrims.size() + " player(s).");
    }

    public void save() {
        FileConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<UUID, Map<ArmorPiece, Trim>> entry : playerTrims.entrySet()) {
            String base = "trims." + entry.getKey();
            entry.getValue().forEach((piece, trim) ->
                    cfg.set(base + "." + piece.name().toLowerCase(), trim.toString()));
        }
        try { cfg.save(trimsFile); }
        catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save trims.yml", e);
        }
    }
}

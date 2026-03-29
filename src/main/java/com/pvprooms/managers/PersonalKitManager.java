package com.pvprooms.managers;

import com.pvprooms.PvPRoomsPro;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Stores per-player personal kit item arrangements.
 *
 * When a player right-clicks a kit in the queue GUI they open their own
 * copy of the kit's 36 inventory slots.  Changes are saved here and used
 * instead of the global kit contents when the kit is applied to that player.
 *
 * Persistence: plugins/PvPRoomsPro/personal_kits.yml
 * Format:  <uuid>.<kitName>.<slot>: ItemStack
 */
public class PersonalKitManager {

    private final PvPRoomsPro plugin;
    private final File dataFile;

    /** uuid → (kitName → contents[36]) */
    private final Map<UUID, Map<String, ItemStack[]>> layouts = new HashMap<>();

    public PersonalKitManager(PvPRoomsPro plugin) {
        this.plugin   = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "personal_kits.yml");
        load();
    }

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Returns the player's personal item layout for a given kit, or null if
     * they haven't customised it yet (the global kit will be used instead).
     */
    public ItemStack[] getPersonalLayout(UUID uuid, String kitName) {
        Map<String, ItemStack[]> playerLayouts = layouts.get(uuid);
        if (playerLayouts == null) return null;
        return playerLayouts.get(kitName.toLowerCase());
    }

    /**
     * Saves a player's personal item arrangement for a kit and persists it.
     */
    public void setPersonalLayout(UUID uuid, String kitName, ItemStack[] contents) {
        layouts.computeIfAbsent(uuid, k -> new HashMap<>())
               .put(kitName.toLowerCase(), contents.clone());
        save();
    }

    // ── Persistence ───────────────────────────────────────────────────────

    private void load() {
        if (!dataFile.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(dataFile);
        for (String uuidStr : cfg.getKeys(false)) {
            UUID uuid;
            try { uuid = UUID.fromString(uuidStr); }
            catch (IllegalArgumentException ignored) { continue; }

            var section = cfg.getConfigurationSection(uuidStr);
            if (section == null) continue;

            Map<String, ItemStack[]> playerLayouts = new HashMap<>();
            for (String kitName : section.getKeys(false)) {
                ItemStack[] contents = new ItemStack[36];
                for (int i = 0; i < 36; i++) {
                    contents[i] = cfg.getItemStack(uuidStr + "." + kitName + "." + i);
                }
                playerLayouts.put(kitName, contents);
            }
            layouts.put(uuid, playerLayouts);
        }
        plugin.getLogger().info("PersonalKitManager: loaded layouts for " + layouts.size() + " player(s).");
    }

    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<UUID, Map<String, ItemStack[]>> pEntry : layouts.entrySet()) {
            String uuidStr = pEntry.getKey().toString();
            for (Map.Entry<String, ItemStack[]> kEntry : pEntry.getValue().entrySet()) {
                String kitName     = kEntry.getKey();
                ItemStack[] items  = kEntry.getValue();
                for (int i = 0; i < items.length; i++) {
                    if (items[i] != null) {
                        cfg.set(uuidStr + "." + kitName + "." + i, items[i]);
                    }
                }
            }
        }
        try {
            cfg.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save personal_kits.yml: " + e.getMessage());
        }
    }
}

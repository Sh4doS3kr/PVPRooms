package com.pvprooms.managers;

import com.pvprooms.PvPRoomsPro;
import com.pvprooms.model.Kit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

/**
 * Manages all PvP kits – creation, deletion, persistence and application.
 * Kits are stored in plugins/PvPRoomsPro/kits.yml.
 */
public class KitManager {

    private final PvPRoomsPro plugin;
    private final File kitsFile;
    private FileConfiguration kitsConfig;

    /** In-memory kit registry: kit name (lowercase) → Kit object */
    private final Map<String, Kit> kits = new LinkedHashMap<>();

    public KitManager(PvPRoomsPro plugin) {
        this.plugin = plugin;
        this.kitsFile = new File(plugin.getDataFolder(), "kits.yml");
        loadKits();
    }

    // ── Load / Save ────────────────────────────────────────────────────────

    /** Loads all kits from kits.yml into memory. */
    public void loadKits() {
        kits.clear();
        if (!kitsFile.exists()) {
            saveKits();
            return;
        }
        kitsConfig = YamlConfiguration.loadConfiguration(kitsFile);
        if (!kitsConfig.contains("kits")) return;

        for (String kitName : kitsConfig.getConfigurationSection("kits").getKeys(false)) {
            String path = "kits." + kitName;

            @SuppressWarnings("unchecked")
            List<ItemStack> contentList = (List<ItemStack>) kitsConfig.getList(path + ".contents");
            @SuppressWarnings("unchecked")
            List<ItemStack> armorList   = (List<ItemStack>) kitsConfig.getList(path + ".armor");

            ItemStack[] contents = contentList != null
                    ? contentList.toArray(new ItemStack[36])
                    : new ItemStack[36];
            ItemStack[] armor    = armorList != null
                    ? armorList.toArray(new ItemStack[4])
                    : new ItemStack[4];

            ItemStack offhand = kitsConfig.getItemStack(path + ".offhand");

            Kit kit = new Kit(kitName, contents, armor, offhand);

            String iconName = kitsConfig.getString(path + ".icon");
            if (iconName != null) {
                try {
                    kit.setIconMaterial(Material.valueOf(iconName));
                } catch (IllegalArgumentException ignored) {}
            }

            kits.put(kitName.toLowerCase(), kit);
        }
        plugin.getLogger().info("Loaded " + kits.size() + " kit(s).");
    }

    /** Persists all in-memory kits to kits.yml. */
    public void saveKits() {
        kitsConfig = new YamlConfiguration();
        for (Kit kit : kits.values()) {
            String path = "kits." + kit.getName();
            kitsConfig.set(path + ".contents", Arrays.asList(kit.getContents()));
            kitsConfig.set(path + ".armor",    Arrays.asList(kit.getArmorContents()));
            kitsConfig.set(path + ".offhand",  kit.getOffhand());
            kitsConfig.set(path + ".icon",     kit.getIconMaterial().name());
        }
        try {
            kitsConfig.save(kitsFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save kits.yml", e);
        }
    }

    // ── CRUD ───────────────────────────────────────────────────────────────

    /**
     * Creates a new kit from an admin's current inventory.
     *
     * @return false if a kit with that name already exists.
     */
    public boolean createKit(String name, Player admin) {
        if (kits.containsKey(name.toLowerCase())) return false;
        Kit kit = new Kit(
                name,
                admin.getInventory().getContents(),
                admin.getInventory().getArmorContents(),
                admin.getInventory().getItemInOffHand()
        );
        kits.put(name.toLowerCase(), kit);
        saveKits();
        return true;
    }

    /**
     * Overwrites an existing kit's contents from an admin's current inventory.
     *
     * @return false if the kit does not exist.
     */
    public boolean editKit(String name, Player admin) {
        Kit kit = kits.get(name.toLowerCase());
        if (kit == null) return false;
        kit.setContents(admin.getInventory().getContents());
        kit.setArmorContents(admin.getInventory().getArmorContents());
        kit.setOffhand(admin.getInventory().getItemInOffHand());
        saveKits();
        return true;
    }

    /**
     * Deletes a kit by name.
     *
     * @return false if the kit does not exist.
     */
    public boolean deleteKit(String name) {
        if (kits.remove(name.toLowerCase()) == null) return false;
        saveKits();
        return true;
    }

    // ── Application ───────────────────────────────────────────────────────

    /**
     * Clears a player's inventory and applies the kit.
     */
    public void applyKit(Player player, String kitName) {
        Kit kit = getKit(kitName);
        if (kit == null) return;

        player.getInventory().clear();
        player.getInventory().setContents(kit.getContents());
        player.getInventory().setArmorContents(kit.getArmorContents());
        player.getInventory().setItemInOffHand(kit.getOffhand());
        player.updateInventory();
    }

    // ── Query helpers ──────────────────────────────────────────────────────

    public Kit getKit(String name) {
        return kits.get(name.toLowerCase());
    }

    public boolean kitExists(String name) {
        return kits.containsKey(name.toLowerCase());
    }

    public Collection<Kit> getAllKits() {
        return Collections.unmodifiableCollection(kits.values());
    }

    public List<String> getKitNames() {
        return new ArrayList<>(kits.keySet());
    }
}

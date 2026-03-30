package com.pvprooms.managers;

import com.pvprooms.PvPRoomsPro;
import com.pvprooms.model.ArmorPiece;
import com.pvprooms.model.Kit;
import com.pvprooms.model.Trim;
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

            String connectedArena = kitsConfig.getString(path + ".connected-arena");
            if (connectedArena != null && !connectedArena.isBlank()) {
                kit.setConnectedArena(connectedArena);
            }

            // Load per-piece trims
            java.util.Map<ArmorPiece, Trim> kitTrims = new java.util.EnumMap<>(ArmorPiece.class);
            for (ArmorPiece piece : ArmorPiece.values()) {
                String trimStr = kitsConfig.getString(path + ".trims." + piece.name().toLowerCase());
                Trim t = Trim.fromString(trimStr);
                if (t != null) kitTrims.put(piece, t);
            }
            kit.setTrims(kitTrims);

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
            kitsConfig.set(path + ".connected-arena",
                    kit.getConnectedArena() != null ? kit.getConnectedArena() : "");
            // Save per-piece trims
            kit.getTrims().forEach((piece, trim) ->
                    kitsConfig.set(path + ".trims." + piece.name().toLowerCase(), trim.toString()));
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
     * Creates a new kit programmatically from arrays.
     * Used by PresetKits to install official kit configurations.
     */
    public void createKit(String name, ItemStack[] armor, ItemStack[] inventory, Material icon) {
        // Build full contents array (36 slots)
        ItemStack[] contents = new ItemStack[36];
        if (inventory != null) {
            System.arraycopy(inventory, 0, contents, 0, Math.min(inventory.length, 36));
        }
        
        Kit kit = new Kit(name, contents, armor, null);
        kit.setIconMaterial(icon);
        kits.put(name.toLowerCase(), kit);
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

    /**
     * Connects a kit to a specific arena.
     * Pass null to arenaName to disconnect (use random arena).
     * @return false if the kit does not exist.
     */
    public boolean connectKitToArena(String kitName, String arenaName) {
        Kit kit = kits.get(kitName.toLowerCase());
        if (kit == null) return false;
        kit.setConnectedArena(arenaName == null || arenaName.isBlank() ? null : arenaName);
        saveKits();
        return true;
    }

    public String getConnectedArena(String kitName) {
        Kit kit = kits.get(kitName.toLowerCase());
        return kit != null ? kit.getConnectedArena() : null;
    }

    /**
     * Updates kit contents from the kit editor GUI.
     */
    public boolean setKitFromEditor(String kitName, ItemStack[] contents,
                                     ItemStack[] armor, ItemStack offhand) {
        Kit kit = kits.get(kitName.toLowerCase());
        if (kit == null) return false;
        kit.setContents(contents);
        kit.setArmorContents(armor);
        kit.setOffhand(offhand);
        saveKits();
        return true;
    }

    /**
     * Changes the display icon of a kit.
     * @return false if the kit does not exist.
     */
    public boolean setKitIcon(String kitName, Material material) {
        Kit kit = kits.get(kitName.toLowerCase());
        if (kit == null) return false;
        kit.setIconMaterial(material);
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

        // Use the player's personal item arrangement if they have one
        ItemStack[] personal = plugin.getPersonalKitManager()
                .getPersonalLayout(player.getUniqueId(), kitName);
        ItemStack[] contents = (personal != null) ? personal : kit.getContents();

        player.getInventory().clear();
        player.getInventory().setContents(contents);
        player.getInventory().setArmorContents(kit.getArmorContents());
        player.getInventory().setItemInOffHand(kit.getOffhand());
        player.updateInventory();
        // Apply kit-default trims then overlay player personal trims
        plugin.getTrimManager().applyTrimsForKit(player, kit.getTrims());
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

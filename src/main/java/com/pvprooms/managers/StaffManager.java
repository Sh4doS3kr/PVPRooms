package com.pvprooms.managers;

import com.pvprooms.PvPRoomsPro;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

/**
 * Manages staff mode: vanish, hotbar items, frozen players.
 */
public class StaffManager {

    private final PvPRoomsPro plugin;

    /** Players currently in staff mode */
    private final Set<UUID> staffMode = new HashSet<>();
    /** Players currently vanished */
    private final Set<UUID> vanished = new HashSet<>();
    /** Players currently frozen */
    private final Set<UUID> frozen = new HashSet<>();
    /** Saved hotbar snapshots when entering staff mode: uuid -> 9 items */
    private final Map<UUID, ItemStack[]> hotbarSnapshots = new HashMap<>();

    public StaffManager(PvPRoomsPro plugin) {
        this.plugin = plugin;
    }

    // ── Staff mode toggle ──────────────────────────────────────────────────

    public boolean isInStaffMode(UUID uuid) { return staffMode.contains(uuid); }
    public boolean isVanished(UUID uuid)    { return vanished.contains(uuid); }
    public boolean isFrozen(UUID uuid)      { return frozen.contains(uuid); }
    public Set<UUID> getVanishedPlayers()   { return Collections.unmodifiableSet(vanished); }

    public void enterStaffMode(Player player) {
        UUID uuid = player.getUniqueId();
        staffMode.add(uuid);

        // Save current hotbar (slots 0-8)
        ItemStack[] snap = new ItemStack[9];
        for (int i = 0; i < 9; i++) {
            ItemStack item = player.getInventory().getItem(i);
            snap[i] = item != null ? item.clone() : null;
        }
        hotbarSnapshots.put(uuid, snap);

        // Clear hotbar and give staff items
        for (int i = 0; i < 9; i++) player.getInventory().setItem(i, null);
        giveStaffItems(player);

        player.sendMessage(plugin.prefix() + "§a§lModo Staff §aactivado.");
        player.sendMessage(plugin.prefix() + "§7Usa los items de tu hotbar para acceder a las funciones.");
    }

    public void exitStaffMode(Player player) {
        UUID uuid = player.getUniqueId();
        staffMode.remove(uuid);

        // Disable vanish if active
        if (vanished.contains(uuid)) setVanish(player, false);

        // Disable fly if it was granted by staff mode
        if (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE) {
            player.setAllowFlight(false);
            player.setFlying(false);
        }

        // Restore hotbar
        ItemStack[] snap = hotbarSnapshots.remove(uuid);
        if (snap != null) {
            for (int i = 0; i < 9; i++) player.getInventory().setItem(i, snap[i]);
        }

        player.sendMessage(plugin.prefix() + "§c§lModo Staff §cdesactivado.");
    }

    // ── Vanish ─────────────────────────────────────────────────────────────

    public void setVanish(Player player, boolean hide) {
        UUID uuid = player.getUniqueId();
        if (hide) {
            vanished.add(uuid);
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!online.hasPermission("pvprooms.staff")) {
                    online.hidePlayer(plugin, player);
                }
            }
            player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,
                    Integer.MAX_VALUE, 0, false, false, false));
            player.sendMessage(plugin.prefix() + "§dVanish §aactivado. §7Eres invisible para los jugadores.");
        } else {
            vanished.remove(uuid);
            for (Player online : Bukkit.getOnlinePlayers()) {
                online.showPlayer(plugin, player);
            }
            player.removePotionEffect(PotionEffectType.INVISIBILITY);
            player.sendMessage(plugin.prefix() + "§dVanish §cdesactivado. §7Ahora eres visible.");
        }
        // Refresh vanish item lore
        refreshVanishItem(player);
    }

    public void toggleVanish(Player player) {
        setVanish(player, !vanished.contains(player.getUniqueId()));
    }

    /** When a vanished staff member logs in, re-hide them from non-staff. */
    public void applyVanishOnJoin(Player joined) {
        for (UUID vid : vanished) {
            Player vp = Bukkit.getPlayer(vid);
            if (vp != null && !joined.hasPermission("pvprooms.staff")) {
                joined.hidePlayer(plugin, vp);
            }
        }
    }

    // ── Fly ────────────────────────────────────────────────────────────────

    public void toggleFly(Player player) {
        if (player.getAllowFlight()) {
            player.setAllowFlight(false);
            player.setFlying(false);
            player.sendMessage(plugin.prefix() + "§cVuelo desactivado.");
        } else {
            player.setAllowFlight(true);
            player.setFlying(true);
            player.sendMessage(plugin.prefix() + "§aVuelo activado.");
        }
    }

    // ── Freeze ─────────────────────────────────────────────────────────────

    public void toggleFreeze(Player staff, Player target) {
        UUID tid = target.getUniqueId();
        if (frozen.contains(tid)) {
            frozen.remove(tid);
            target.sendMessage(plugin.prefix() + "§aHas sido descongelado.");
            staff.sendMessage(plugin.prefix() + "§a" + target.getName() + " §adescongelado.");
        } else {
            frozen.add(tid);
            target.sendMessage(plugin.prefix() + "§c§l¡HAS SIDO CONGELADO! §cNo puedes moverte.");
            staff.sendMessage(plugin.prefix() + "§e" + target.getName() + " §econgelado.");
        }
    }

    // ── Hotbar items ───────────────────────────────────────────────────────

    private void giveStaffItems(Player player) {
        boolean isVanished = vanished.contains(player.getUniqueId());
        player.getInventory().setItem(0, makeVanishItem(isVanished));
        player.getInventory().setItem(1, makeItem(Material.COMPASS,        "§e§lJUGADORES",        List.of("§7Clic derecho para ver", "§7jugadores online", "", "§7y teleportarte a ellos")));
        player.getInventory().setItem(2, makeItem(Material.BOOK,           "§b§lPARTIDAS ACTIVAS",  List.of("§7Clic derecho para ver", "§7las partidas en curso", "", "§7y teleportarte a ellas")));
        player.getInventory().setItem(3, makeItem(Material.FEATHER,        "§a§lVUELO",             List.of("§7Clic derecho para", "§7activar/desactivar vuelo")));
        player.getInventory().setItem(4, makeItem(Material.BLAZE_POWDER,   "§c§lFORZAR FIN DUELO", List.of("§7Clic derecho para", "§7finalizar un duelo", "§cforzadamente")));
        player.getInventory().setItem(5, makeItem(Material.LEVER,          "§6§lCONGELAR",          List.of("§7Clic derecho para", "§7congelar/descongelar", "§7un jugador")));
        player.getInventory().setItem(6, makeItem(Material.PAPER,          "§f§lINFO JUGADOR",      List.of("§7Clic derecho para ver", "§7información de un jugador")));
        player.getInventory().setItem(7, makeItem(Material.CLOCK,          "§d§lESTADÍSTICAS",      List.of("§7Clic derecho para ver", "§7estadísticas del servidor")));
        player.getInventory().setItem(8, makeItem(Material.BARRIER,        "§c§lSALIR STAFF",       List.of("§7Clic derecho para", "§csalir del modo staff")));
    }

    private void refreshVanishItem(Player player) {
        if (!staffMode.contains(player.getUniqueId())) return;
        boolean isVanished = vanished.contains(player.getUniqueId());
        player.getInventory().setItem(0, makeVanishItem(isVanished));
    }

    private ItemStack makeVanishItem(boolean active) {
        String state = active ? "§a§lACTIVO" : "§c§lINACTIVO";
        return makeItem(Material.ENDER_EYE, "§d§lVANISH §8[" + (active ? "§aON" : "§cOFF") + "§8]",
                List.of("§7Clic derecho para activar/desactivar", "", "§7Estado: " + state));
    }

    public static ItemStack makeItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(new ArrayList<>(lore));
        item.setItemMeta(meta);
        return item;
    }

    // ── Identification ─────────────────────────────────────────────────────

    /** Returns true if the item is one of the staff hotbar items (checks display name). */
    public static boolean isStaffItem(ItemStack item) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) return false;
        String name = item.getItemMeta().getDisplayName();
        return name.contains("VANISH") || name.contains("JUGADORES") || name.contains("PARTIDAS ACTIVAS")
                || name.contains("VUELO") || name.contains("FORZAR FIN") || name.contains("CONGELAR")
                || name.contains("INFO JUGADOR") || name.contains("ESTADÍSTICAS") || name.contains("SALIR STAFF");
    }

    public void removeOnDisable() {
        for (UUID uuid : new HashSet<>(staffMode)) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) exitStaffMode(p);
        }
    }
}

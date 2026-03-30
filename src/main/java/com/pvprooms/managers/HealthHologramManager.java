package com.pvprooms.managers;

import com.pvprooms.PvPRoomsPro;
import com.pvprooms.model.Duel;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Displays a floating health hologram above each dueling player using
 * an invisible, marker ArmorStand that tracks the player every 2 ticks.
 *
 * Format: §c❤ §f18 §8/ §f20   (colour shifts green → yellow → red)
 */
public class HealthHologramManager {

    /** Y offset above the player's feet (≈ top of head + a little extra) */
    private static final double OFFSET_Y = 2.35;
    /** Ticks between position + name updates */
    private static final long UPDATE_INTERVAL = 2L;

    private final PvPRoomsPro plugin;

    /** duelId → update task */
    private final Map<UUID, BukkitTask> tasks = new HashMap<>();
    /** duelId → [standForP1, standForP2] */
    private final Map<UUID, ArmorStand[]> stands = new HashMap<>();

    public HealthHologramManager(PvPRoomsPro plugin) {
        this.plugin = plugin;
    }

    // ── Public API ────────────────────────────────────────────────────────

    /** Call when the duel enters FIGHTING state. */
    public void startHolograms(Duel duel, World world) {
        Player p1 = Bukkit.getPlayer(duel.getPlayer1());
        Player p2 = Bukkit.getPlayer(duel.getPlayer2());
        if (p1 == null || p2 == null) return;

        ArmorStand stand1 = spawnStand(p1.getLocation().add(0, OFFSET_Y, 0));
        ArmorStand stand2 = spawnStand(p2.getLocation().add(0, OFFSET_Y, 0));
        stands.put(duel.getId(), new ArmorStand[]{stand1, stand2});

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Player a = Bukkit.getPlayer(duel.getPlayer1());
            Player b = Bukkit.getPlayer(duel.getPlayer2());

            // If either player is gone the task will be cleaned up by endDuel
            if (a != null && !stand1.isDead()) {
                stand1.teleport(a.getLocation().add(0, OFFSET_Y, 0));
                stand1.setCustomName(healthLabel(a));
            }
            if (b != null && !stand2.isDead()) {
                stand2.teleport(b.getLocation().add(0, OFFSET_Y, 0));
                stand2.setCustomName(healthLabel(b));
            }
        }, 0L, UPDATE_INTERVAL);

        tasks.put(duel.getId(), task);
    }

    /** Call when the duel ends (any reason). */
    public void stopHolograms(UUID duelId) {
        BukkitTask task = tasks.remove(duelId);
        if (task != null) task.cancel();

        ArmorStand[] pair = stands.remove(duelId);
        if (pair != null) {
            for (ArmorStand stand : pair) {
                if (stand != null && !stand.isDead()) stand.remove();
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private ArmorStand spawnStand(Location loc) {
        ArmorStand stand = (ArmorStand) loc.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setSmall(true);
        stand.setMarker(true);
        stand.setBasePlate(false);
        stand.setArms(false);
        stand.setCustomNameVisible(true);
        stand.setCustomName("§c❤ §f? §8/ §f?");
        stand.setInvulnerable(true);
        stand.setSilent(true);
        return stand;
    }

    /** Builds the coloured health label for a player. */
    private String healthLabel(Player player) {
        double maxHp = maxHp(player);
        double hp    = Math.max(0, player.getHealth());
        int hpInt    = (int) Math.ceil(hp);
        int maxInt   = (int) Math.ceil(maxHp);
        double pct   = maxHp > 0 ? hp / maxHp : 0;

        String colour;
        if (pct > 0.60)      colour = "§a";   // green
        else if (pct > 0.30) colour = "§e";   // yellow
        else                  colour = "§c";   // red

        return colour + "❤ §f" + hpInt + " §8/ §f" + maxInt;
    }

    private double maxHp(Player player) {
        var attr = player.getAttribute(Attribute.MAX_HEALTH);
        return attr != null ? attr.getValue() : 20.0;
    }
}

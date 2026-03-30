package com.pvprooms.commands;

import com.pvprooms.PvPRoomsPro;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Maneja los comandos /setspawn y /setspawnworld.
 *
 *   /setspawn       — guarda la posición actual (mundo + coords) como spawn del lobby
 *   /setspawnworld  — guarda únicamente el mundo actual como mundo del lobby
 *
 * Ambos guardan los valores en config.yml y recargan en caliente la
 * ubicación devuelta por PvPRoomsPro#getLobbySpawn().
 */
public class SetSpawnCommand implements CommandExecutor {

    private final PvPRoomsPro plugin;
    private final boolean worldOnly;

    /**
     * @param worldOnly si es true se comporta como /setspawnworld,
     *                  si es false se comporta como /setspawn.
     */
    public SetSpawnCommand(PvPRoomsPro plugin, boolean worldOnly) {
        this.plugin    = plugin;
        this.worldOnly = worldOnly;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof org.bukkit.entity.Player p) || !p.isOp()) {
            sender.sendMessage(plugin.prefix() + "§cSolo OPs pueden usar este comando.");
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.prefix() + "§cEste comando solo puede usarlo un jugador.");
            return true;
        }

        Location loc = player.getLocation();
        String worldName = loc.getWorld().getName();

        if (worldOnly) {
            plugin.getConfig().set("general.lobby-world", worldName);
            plugin.saveConfig();
            player.sendMessage(plugin.prefix()
                    + "§aEl mundo del lobby ha sido establecido a §e" + worldName + "§a.");
        } else {
            plugin.getConfig().set("general.lobby-world",   worldName);
            plugin.getConfig().set("general.lobby-spawn.x",     loc.getX());
            plugin.getConfig().set("general.lobby-spawn.y",     loc.getY());
            plugin.getConfig().set("general.lobby-spawn.z",     loc.getZ());
            plugin.getConfig().set("general.lobby-spawn.yaw",   (double) loc.getYaw());
            plugin.getConfig().set("general.lobby-spawn.pitch", (double) loc.getPitch());
            plugin.saveConfig();

            player.sendMessage(plugin.prefix()
                    + "§aSpawn del lobby establecido en §e" + worldName
                    + " §7(" + fmt(loc.getX()) + ", " + fmt(loc.getY()) + ", " + fmt(loc.getZ()) + ")§a.");
        }
        return true;
    }

    private String fmt(double v) {
        return String.format("%.1f", v);
    }
}

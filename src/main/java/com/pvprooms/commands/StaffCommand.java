package com.pvprooms.commands;

import com.pvprooms.PvPRoomsPro;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /staff — Toggles staff mode for players with pvprooms.staff permission.
 */
public class StaffCommand implements CommandExecutor {

    private final PvPRoomsPro plugin;

    public StaffCommand(PvPRoomsPro plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cSolo jugadores pueden usar este comando.");
            return true;
        }
        if (!player.hasPermission("pvprooms.staff")) {
            player.sendMessage(plugin.prefix() + "§cNo tienes permiso para usar este comando.");
            return true;
        }

        if (plugin.getStaffManager().isInStaffMode(player.getUniqueId())) {
            plugin.getStaffManager().exitStaffMode(player);
        } else {
            plugin.getStaffManager().enterStaffMode(player);
        }
        return true;
    }
}

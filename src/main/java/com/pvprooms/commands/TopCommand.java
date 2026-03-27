package com.pvprooms.commands;

import com.pvprooms.PvPRoomsPro;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * Handles the /top command.
 * Shows the top 10 players by ELO.
 */
public class TopCommand implements CommandExecutor {

    private final PvPRoomsPro plugin;

    public TopCommand(PvPRoomsPro plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("pvprooms.top")) {
            sender.sendMessage(plugin.prefix() + "§cYou do not have permission to use this command.");
            return true;
        }

        List<String> top = plugin.getEloManager().getTopPlayers(10);

        sender.sendMessage("§8§m                              ");
        sender.sendMessage("§c§l  PvPRooms — Top ELO Leaderboard");
        sender.sendMessage("§8§m                              ");

        if (top.isEmpty()) {
            sender.sendMessage("§7No players have played yet.");
        } else {
            for (int i = 0; i < top.size(); i++) {
                String color = switch (i) {
                    case 0  -> "§6§l";
                    case 1  -> "§f§l";
                    case 2  -> "§c§l";
                    default -> "§7";
                };
                sender.sendMessage(color + "#" + (i + 1) + " §r" + top.get(i));
            }
        }

        sender.sendMessage("§8§m                              ");
        return true;
    }
}

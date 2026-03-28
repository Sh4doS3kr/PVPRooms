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
            sender.sendMessage(plugin.prefix() + "§cNo tienes permiso para usar este comando.");
            return true;
        }

        List<String> top = plugin.getEloManager().getTopPlayers(10);

        sender.sendMessage("§8§m══════════════════════════════");
        sender.sendMessage("§6§l  ⚔ PvPRooms — Top ELO");
        sender.sendMessage("§8§m══════════════════════════════");

        if (top.isEmpty()) {
            sender.sendMessage("§7Aún no hay jugadores con partidas.");
        } else {
            for (int i = 0; i < top.size(); i++) {
                String medal = switch (i) {
                    case 0  -> "§6🥇 ";
                    case 1  -> "§f🥈 ";
                    case 2  -> "§c🥉 ";
                    default -> "§7#" + (i + 1) + " ";
                };
                sender.sendMessage(medal + "§r" + top.get(i));
            }
        }

        sender.sendMessage("§8§m══════════════════════════════");
        return true;
    }
}

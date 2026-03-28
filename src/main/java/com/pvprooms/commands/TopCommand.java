package com.pvprooms.commands;

import com.pvprooms.PvPRoomsPro;
import com.pvprooms.model.Tier;
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

        sender.sendMessage("§8§m══════════════════════════════════════");
        sender.sendMessage("§6§l  ⚔ PvPRooms — Top ELO / Tier");
        sender.sendMessage("§8§m══════════════════════════════════════");

        var eloMap = plugin.getEloManager().getEloMap();
        var nameMap = plugin.getEloManager().getNameMap();

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
                // top.get(i) = "Name §7— §eELO"
                String entry = top.get(i);
                // Extract name to get ELO for tier
                String rawName = entry.split(" §")[0];
                String uuidKey = nameMap.entrySet().stream()
                        .filter(e -> e.getValue().equalsIgnoreCase(rawName))
                        .map(java.util.Map.Entry::getKey).findFirst().orElse(null);
                int elo = uuidKey != null ? eloMap.getOrDefault(uuidKey, 1000) : 1000;
                Tier tier = Tier.fromElo(elo);
                sender.sendMessage(medal + "§r" + entry + "  " + tier.formatted());
            }
        }

        sender.sendMessage("§8§m══════════════════════════════════════");
        return true;
    }
}

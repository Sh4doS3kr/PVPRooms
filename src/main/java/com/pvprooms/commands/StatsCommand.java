package com.pvprooms.commands;

import com.pvprooms.PvPRoomsPro;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Handles the /stats [player] command.
 * Shows ELO and rank for a player.
 */
public class StatsCommand implements CommandExecutor, TabCompleter {

    private final PvPRoomsPro plugin;

    public StatsCommand(PvPRoomsPro plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("pvprooms.stats")) {
            sender.sendMessage(plugin.prefix() + "§cYou do not have permission to use this command.");
            return true;
        }

        Player target;
        if (args.length > 0) {
            target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(plugin.prefix() + "§cPlayer §e" + args[0] + " §cis not online.");
                return true;
            }
        } else {
            if (!(sender instanceof Player)) {
                sender.sendMessage(plugin.prefix() + "§cSpecify a player name when running from console.");
                return true;
            }
            target = (Player) sender;
        }

        UUID uuid = target.getUniqueId();
        int elo  = plugin.getEloManager().getElo(uuid);
        int rank = plugin.getEloManager().getRank(uuid);

        sender.sendMessage("§8§m                              ");
        sender.sendMessage("§e§lStats for §f" + target.getName());
        sender.sendMessage("§fELO: §e" + elo);
        sender.sendMessage("§fRank: §e" + (rank == -1 ? "Unranked" : "#" + rank));

        String status;
        if (plugin.getDuelManager().isInDuel(uuid)) {
            status = "§aIn Duel";
        } else if (plugin.getQueueManager().isInQueue(uuid)) {
            status = "§eIn Queue (" + plugin.getQueueManager().getQueuedKit(uuid) + ")";
        } else {
            status = "§7Idle";
        }
        sender.sendMessage("§fStatus: " + status);
        sender.sendMessage("§8§m                              ");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("pvprooms.stats")) return List.of();
        if (args.length == 1) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}

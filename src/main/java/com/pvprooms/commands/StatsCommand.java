package com.pvprooms.commands;

import com.pvprooms.PvPRoomsPro;
import com.pvprooms.model.Tier;
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
            sender.sendMessage(plugin.prefix() + "§cNo tienes permiso para usar este comando.");
            return true;
        }

        Player target;
        if (args.length > 0) {
            target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(plugin.prefix() + "§cEl jugador §e" + args[0] + " §cno está conectado.");
                return true;
            }
        } else {
            if (!(sender instanceof Player)) {
                sender.sendMessage(plugin.prefix() + "§cEspecifica un nombre de jugador desde la consola.");
                return true;
            }
            target = (Player) sender;
        }

        UUID uuid = target.getUniqueId();
        int elo  = plugin.getEloManager().getElo(uuid);
        int rank = plugin.getEloManager().getRank(uuid);

        sender.sendMessage("§8§m══════════════════════════════");
        sender.sendMessage("§6§l  ⚔ Estadísticas de §f" + target.getName());
        sender.sendMessage("§8§m══════════════════════════════");
        Tier tier = Tier.forPlayer(plugin.getEloManager(), uuid);
        sender.sendMessage("§eELO:     §f" + elo);
        sender.sendMessage("§eTier:    " + tier.formatted());
        sender.sendMessage("§eRanking: §f" + (rank == -1 ? "§7Sin rango" : "#" + rank));

        String status;
        if (plugin.getDuelManager().isInDuel(uuid)) {
            status = "§a⚔ En duelo";
        } else if (plugin.getQueueManager().isInQueue(uuid)) {
            status = "§e⏳ En cola §7(" + plugin.getQueueManager().getQueuedKit(uuid) + ")";
        } else {
            status = "§7● Inactivo";
        }
        sender.sendMessage("§eEstado: " + status);
        sender.sendMessage("§8§m══════════════════════════════");
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

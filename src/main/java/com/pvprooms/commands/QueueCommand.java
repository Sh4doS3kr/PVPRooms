package com.pvprooms.commands;

import com.pvprooms.PvPRoomsPro;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Handles the /queue command.
 * Opens the Kit Selection GUI so the player can choose a kit and join queue.
 * If the player is already in a queue or a duel, they are notified.
 */
public class QueueCommand implements CommandExecutor {

    private final PvPRoomsPro plugin;

    public QueueCommand(PvPRoomsPro plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("pvprooms.queue")) {
            sender.sendMessage(plugin.prefix() + "§cNo tienes permiso para usar este comando.");
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.prefix() + "§cEste comando solo puede usarlo un jugador.");
            return true;
        }

        if (plugin.getDuelManager().isInDuel(player.getUniqueId())) {
            player.sendMessage(plugin.prefix() + "§cYa estás en un duelo. Usa §f/pvpleave §cpara rendirte.");
            return true;
        }

        if (plugin.getQueueManager().isInQueue(player.getUniqueId())) {
            String kit = plugin.getQueueManager().getQueuedKit(player.getUniqueId());
            player.sendMessage(plugin.prefix() + "§cYa estás en la cola de §e" + kit + "§c. Usa §f/pvpleave §cpara salir.");
            return true;
        }

        // Open mode selection GUI (ELO vs TIER)
        int elo = plugin.getEloManager().getElo(player.getUniqueId());
        player.openInventory(plugin.getQueueModeGUI().build(elo));
        return true;
    }
}

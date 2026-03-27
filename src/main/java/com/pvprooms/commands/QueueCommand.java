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
            sender.sendMessage(plugin.prefix() + "§cYou do not have permission to use this command.");
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.prefix() + "§cThis command must be run by a player.");
            return true;
        }

        // Already in a duel
        if (plugin.getDuelManager().isInDuel(player.getUniqueId())) {
            player.sendMessage(plugin.prefix() + "§cYou are already in a duel. Use §f/pvpleave §cto forfeit.");
            return true;
        }

        // Already in queue
        if (plugin.getQueueManager().isInQueue(player.getUniqueId())) {
            String kit = plugin.getQueueManager().getQueuedKit(player.getUniqueId());
            player.sendMessage(plugin.prefix() + "§cYou are already in the §e" + kit + " §cqueue. Use §f/pvpleave §cto leave.");
            return true;
        }

        // Open GUI
        plugin.getKitGUI().open(player);
        return true;
    }
}

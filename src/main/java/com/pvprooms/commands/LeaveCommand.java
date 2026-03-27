package com.pvprooms.commands;

import com.pvprooms.PvPRoomsPro;
import com.pvprooms.model.Duel;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Handles the /pvpleave command.
 * Allows a player to leave the queue, forfeit an active duel, or stop spectating.
 */
public class LeaveCommand implements CommandExecutor {

    private final PvPRoomsPro plugin;

    public LeaveCommand(PvPRoomsPro plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("pvprooms.leave")) {
            sender.sendMessage(plugin.prefix() + "§cYou do not have permission to use this command.");
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.prefix() + "§cThis command must be run by a player.");
            return true;
        }

        UUID uuid = player.getUniqueId();

        // Check if spectating
        Duel spectatedDuel = findSpectatedDuel(uuid);
        if (spectatedDuel != null) {
            plugin.getDuelManager().removeSpectatorFromDuel(player, spectatedDuel);
            player.sendMessage(plugin.prefix() + "§aYou have stopped spectating.");
            return true;
        }

        // Check if in queue
        if (plugin.getQueueManager().isInQueue(uuid)) {
            String kit = plugin.getQueueManager().getQueuedKit(uuid);
            plugin.getQueueManager().removeFromQueue(uuid);
            plugin.getScoreboardManager().clearScoreboard(player);
            player.sendMessage(plugin.prefix() + "§aYou have left the §e" + kit + " §aqueue.");
            return true;
        }

        // Check if in a duel
        Duel duel = plugin.getDuelManager().getDuelByPlayer(uuid);
        if (duel != null && duel.getState() != Duel.State.ENDED) {
            UUID opponentUUID = duel.getOpponent(uuid);
            player.sendMessage(plugin.prefix() + "§cYou forfeited the duel.");
            plugin.getDuelManager().endDuel(duel, opponentUUID, "forfeit");
            return true;
        }

        player.sendMessage(plugin.prefix() + "§cYou are not in a queue, duel, or spectating.");
        return true;
    }

    /** Finds the duel a player is spectating, or null if they're not spectating. */
    private Duel findSpectatedDuel(UUID uuid) {
        for (Duel duel : plugin.getDuelManager().getActiveDuels()) {
            if (duel.isSpectator(uuid)) return duel;
        }
        return null;
    }
}

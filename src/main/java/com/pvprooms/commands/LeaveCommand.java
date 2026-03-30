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
            sender.sendMessage(plugin.prefix() + "§cNo tienes permiso para usar este comando.");
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.prefix() + "§cEste comando solo puede usarlo un jugador.");
            return true;
        }

        UUID uuid = player.getUniqueId();

        Duel spectatedDuel = findSpectatedDuel(uuid);
        if (spectatedDuel != null) {
            plugin.getDuelManager().removeSpectatorFromDuel(player, spectatedDuel);
            plugin.getScoreboardManager().restoreLobbyScoreboard(player);
            player.sendMessage(plugin.prefix() + "§aHas dejado de espectear.");
            return true;
        }

        if (plugin.getQueueManager().isInQueue(uuid)) {
            String kit = plugin.getQueueManager().getQueuedKit(uuid);
            plugin.getQueueManager().removeFromQueue(uuid);
            plugin.getScoreboardManager().restoreLobbyScoreboard(player);
            player.sendMessage(plugin.prefix() + "§aSaliste de la cola de §e" + kit + "§a.");
            return true;
        }

        // Check for bot duel first
        if (plugin.getBotManager().isInBotDuel(uuid)) {
            plugin.getBotManager().forfeitBotDuel(uuid);
            return true;
        }

        Duel duel = plugin.getDuelManager().getDuelByPlayer(uuid);
        if (duel != null && duel.getState() != Duel.State.ENDED) {
            UUID opponentUUID = duel.getOpponent(uuid);
            player.sendMessage(plugin.prefix() + "§cTe has rendido en el duelo.");
            plugin.getDuelManager().endDuel(duel, opponentUUID, "rendición");
            return true;
        }

        player.sendMessage(plugin.prefix() + "§cNo estás en ninguna cola, duelo ni especteando.");
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

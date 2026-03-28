package com.pvprooms.commands;

import com.pvprooms.PvPRoomsPro;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SpawnCommand implements CommandExecutor {

    private final PvPRoomsPro plugin;

    public SpawnCommand(PvPRoomsPro plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.prefix() + "§cEste comando solo puede usarlo un jugador.");
            return true;
        }

        if (!player.hasPermission("pvprooms.spawn")) {
            player.sendMessage(plugin.prefix() + "§cNo tienes permiso para usar este comando.");
            return true;
        }

        Location lobby = plugin.getLobbySpawn();
        player.teleport(lobby);
        player.sendMessage(plugin.prefix() + "§aTeletransportado al spawn.");
        return true;
    }
}

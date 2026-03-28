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

        // Bloquear en mundos de arena (instancias activas o mundo plantilla durante duelo)
        String instancePrefix = plugin.getConfig().getString("arenas.instance-prefix", "pvp_match_");
        if (player.getWorld().getName().startsWith(instancePrefix)) {
            player.sendMessage(plugin.prefix() + "§cNo puedes usar /spawn durante un duelo.");
            return true;
        }
        if (plugin.getDuelManager().getDuelByPlayer(player.getUniqueId()) != null) {
            player.sendMessage(plugin.prefix() + "§cNo puedes usar /spawn durante un duelo.");
            return true;
        }

        Location lobby = plugin.getLobbySpawn();
        player.teleport(lobby);
        player.sendMessage(plugin.prefix() + "§aTeletransportado al spawn.");
        return true;
    }
}

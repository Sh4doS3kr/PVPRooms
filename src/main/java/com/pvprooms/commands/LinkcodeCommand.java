package com.pvprooms.commands;

import com.pvprooms.PvPRoomsPro;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /linkcode — Shows the player their pending Discord link code.
 */
public class LinkcodeCommand implements CommandExecutor {

    private final PvPRoomsPro plugin;

    public LinkcodeCommand(PvPRoomsPro plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Solo jugadores pueden usar este comando.");
            return true;
        }

        String linked = plugin.getTierManager().getLinkedDiscord(player.getUniqueId());
        if (linked != null) {
            player.sendMessage(plugin.prefix() + "§a§lYa tienes Discord vinculado. §7ID: §b" + linked);
            return true;
        }

        String code = plugin.getTierManager().getPendingCode(player.getUniqueId());
        if (code == null) {
            player.sendMessage(plugin.prefix() + "§cNo tienes ningún código pendiente.");
            player.sendMessage(plugin.prefix() + "§7Pide la vinculación desde el bot de Discord.");
            return true;
        }

        player.sendMessage("§8§m──────────────────────────────");
        player.sendMessage(plugin.prefix() + "§b§lVinculación de Discord");
        player.sendMessage(plugin.prefix() + "§7Tu código de vinculación es:");
        player.sendMessage(plugin.prefix() + "§a§l" + code);
        player.sendMessage(plugin.prefix() + "§7Envíalo al bot de Discord. Válido 5 min.");
        player.sendMessage("§8§m──────────────────────────────");
        return true;
    }
}

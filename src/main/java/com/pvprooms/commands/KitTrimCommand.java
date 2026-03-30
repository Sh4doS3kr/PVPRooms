package com.pvprooms.commands;

import com.pvprooms.PvPRoomsPro;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;

/**
 * /kittrim <kitName> [gui]
 * Opens the admin trim-configuration GUI for a specific kit.
 */
public class KitTrimCommand implements CommandExecutor, TabCompleter {

    private final PvPRoomsPro plugin;

    public KitTrimCommand(PvPRoomsPro plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cSolo jugadores.");
            return true;
        }

        if (!player.isOp()) {
            player.sendMessage(plugin.prefix() + "§cSolo OPs pueden usar este comando.");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage("§cUso: /kittrim <nombre-kit> [gui]");
            return true;
        }

        String kitName = args[0].toLowerCase();
        if (!plugin.getKitManager().kitExists(kitName)) {
            player.sendMessage(plugin.prefix() + "§cKit '§e" + kitName + "§c' no existe.");
            return true;
        }

        plugin.getKitTrimGUI().openPickPiece(player, kitName);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            return plugin.getKitManager().getKitNames().stream()
                    .filter(n -> n.startsWith(partial))
                    .collect(Collectors.toList());
        }
        if (args.length == 2) return List.of("gui");
        return List.of();
    }
}

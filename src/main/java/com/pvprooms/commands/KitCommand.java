package com.pvprooms.commands;

import com.pvprooms.PvPRoomsPro;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Handles the /kit command.
 * Requires pvprooms.kit permission (op by default).
 *
 * Subcommands:
 *   /kit create <name>  — Creates a kit from the admin's current inventory
 *   /kit edit <name>    — Overwrites an existing kit with the admin's current inventory
 *   /kit delete <name>  — Deletes a kit
 *   /kit list           — Lists all kits
 */
public class KitCommand implements CommandExecutor, TabCompleter {

    private final PvPRoomsPro plugin;

    public KitCommand(PvPRoomsPro plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("pvprooms.kit")) {
            sender.sendMessage(plugin.prefix() + "§cNo tienes permiso para usar este comando.");
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.prefix() + "§cEste comando solo puede usarlo un jugador.");
            return true;
        }
        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "create" -> {
                if (args.length < 2) { player.sendMessage(plugin.prefix() + "§cUso: /kit create <nombre>"); return true; }
                String name = args[1];
                if (plugin.getKitManager().createKit(name, player)) {
                    player.sendMessage(plugin.prefix() + "§aKit §e" + name + " §acreado con tu inventario actual.");
                } else {
                    player.sendMessage(plugin.prefix() + "§cYa existe un kit llamado §e" + name + "§c. Usa §f/kit edit §cpara sobreescribirlo.");
                }
            }
            case "edit" -> {
                if (args.length < 2) { player.sendMessage(plugin.prefix() + "§cUso: /kit edit <nombre>"); return true; }
                String name = args[1];
                if (plugin.getKitManager().editKit(name, player)) {
                    player.sendMessage(plugin.prefix() + "§aKit §e" + name + " §aactualizado con tu inventario actual.");
                } else {
                    player.sendMessage(plugin.prefix() + "§cEl kit §e" + name + " §cno existe. Usa §f/kit create §cprimero.");
                }
            }
            case "delete" -> {
                if (args.length < 2) { player.sendMessage(plugin.prefix() + "§cUso: /kit delete <nombre>"); return true; }
                String name = args[1];
                if (plugin.getKitManager().deleteKit(name)) {
                    player.sendMessage(plugin.prefix() + "§aKit §e" + name + " §aeliminado correctamente.");
                } else {
                    player.sendMessage(plugin.prefix() + "§cEl kit §e" + name + " §cno existe.");
                }
            }
            case "list" -> {
                List<String> names = plugin.getKitManager().getKitNames();
                if (names.isEmpty()) {
                    player.sendMessage(plugin.prefix() + "§cAún no hay kits creados.");
                } else {
                    player.sendMessage(plugin.prefix() + "§aKits disponibles: §e" + String.join("§7, §e", names));
                }
            }
            default -> sendHelp(player);
        }
        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage("§8§m══════════════════════════════");
        player.sendMessage("§6§l  ⚔ Comandos de Kit");
        player.sendMessage("§8§m══════════════════════════════");
        player.sendMessage("§e/kit create §f<nombre> §8» §7Crear kit del inventario");
        player.sendMessage("§e/kit edit §f<nombre>   §8» §7Sobreescribir kit");
        player.sendMessage("§e/kit delete §f<nombre> §8» §7Eliminar kit");
        player.sendMessage("§e/kit list              §8» §7Listar todos los kits");
        player.sendMessage("§8§m══════════════════════════════");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("pvprooms.kit")) return List.of();
        if (args.length == 1) {
            return Arrays.asList("create", "edit", "delete", "list").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("edit") || args[0].equalsIgnoreCase("delete"))) {
            return plugin.getKitManager().getKitNames().stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}

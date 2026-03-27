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
            sender.sendMessage(plugin.prefix() + "§cYou do not have permission to use this command.");
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.prefix() + "§cThis command must be run by a player.");
            return true;
        }
        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "create" -> {
                if (args.length < 2) { player.sendMessage(plugin.prefix() + "§cUsage: /kit create <name>"); return true; }
                String name = args[1];
                if (plugin.getKitManager().createKit(name, player)) {
                    player.sendMessage(plugin.prefix() + "§aKit §e" + name + " §acreated from your current inventory.");
                } else {
                    player.sendMessage(plugin.prefix() + "§cA kit named §e" + name + " §calready exists. Use §f/kit edit§c to overwrite it.");
                }
            }
            case "edit" -> {
                if (args.length < 2) { player.sendMessage(plugin.prefix() + "§cUsage: /kit edit <name>"); return true; }
                String name = args[1];
                if (plugin.getKitManager().editKit(name, player)) {
                    player.sendMessage(plugin.prefix() + "§aKit §e" + name + " §aupdated from your current inventory.");
                } else {
                    player.sendMessage(plugin.prefix() + "§cKit §e" + name + " §cdoes not exist. Use §f/kit create§c first.");
                }
            }
            case "delete" -> {
                if (args.length < 2) { player.sendMessage(plugin.prefix() + "§cUsage: /kit delete <name>"); return true; }
                String name = args[1];
                if (plugin.getKitManager().deleteKit(name)) {
                    player.sendMessage(plugin.prefix() + "§aKit §e" + name + " §adeleted.");
                } else {
                    player.sendMessage(plugin.prefix() + "§cKit §e" + name + " §cdoes not exist.");
                }
            }
            case "list" -> {
                List<String> names = plugin.getKitManager().getKitNames();
                if (names.isEmpty()) {
                    player.sendMessage(plugin.prefix() + "§cNo kits have been created yet.");
                } else {
                    player.sendMessage(plugin.prefix() + "§aAvailable kits: §e" + String.join("§7, §e", names));
                }
            }
            default -> sendHelp(player);
        }
        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage("§8§m                              ");
        player.sendMessage("§c§lKit Commands");
        player.sendMessage("§f/kit create §e<name> §7— Create kit from inventory");
        player.sendMessage("§f/kit edit §e<name>   §7— Overwrite kit from inventory");
        player.sendMessage("§f/kit delete §e<name> §7— Delete a kit");
        player.sendMessage("§f/kit list             §7— List all kits");
        player.sendMessage("§8§m                              ");
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

package com.pvprooms.commands;

import com.pvprooms.PvPRoomsPro;
import com.pvprooms.model.ArenaTemplate;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Handles the /arena command.
 * Requires pvprooms.arena permission (op by default).
 *
 * Subcommands:
 *   /arena create <name>     — Create a flat world at server root and teleport admin to it
 *   /arena setspawn1 <name>  — Set spawn 1 to current location
 *   /arena setspawn2 <name>  — Set spawn 2 to current location
 *   /arena delete <name>     — Delete an arena template
 *   /arena list              — List all arena templates
 *   /arena info <name>       — Show details of an arena template
 */
public class ArenaCommand implements CommandExecutor, TabCompleter {

    private final PvPRoomsPro plugin;

    public ArenaCommand(PvPRoomsPro plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("pvprooms.arena")) {
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
                if (args.length < 2) { player.sendMessage(plugin.prefix() + "§cUsage: /arena create <name>"); return true; }
                String name = args[1];

                if (plugin.getArenaManager().getArena(name) != null) {
                    player.sendMessage(plugin.prefix() + "§cAn arena named §e" + name + " §calready exists.");
                    return true;
                }

                player.sendMessage(plugin.prefix() + "§eCreating world §f" + name + "§e, please wait...");

                // Create the world at the server root as a flat world
                World world = Bukkit.getWorld(name);
                if (world == null) {
                    WorldCreator creator = new WorldCreator(name);
                    creator.type(WorldType.FLAT);
                    creator.generateStructures(false);
                    world = Bukkit.createWorld(creator);
                }

                if (world == null) {
                    player.sendMessage(plugin.prefix() + "§cFailed to create world §e" + name + "§c. Check console.");
                    return true;
                }

                // Apply comfortable gamerules for building
                world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
                world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
                world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
                world.setGameRule(GameRule.KEEP_INVENTORY, true);
                world.setTime(6000L);

                // Register the arena template
                plugin.getArenaManager().createArena(name);

                // Teleport admin to the world
                player.teleport(world.getSpawnLocation());

                player.sendMessage(plugin.prefix() + "§aWorld §e" + name + " §acreated and registered as an arena.");
                player.sendMessage(plugin.prefix() + "§7Build your map here, then run:");
                player.sendMessage(plugin.prefix() + "§f  /arena setspawn1 " + name + " §7— stand at spawn 1");
                player.sendMessage(plugin.prefix() + "§f  /arena setspawn2 " + name + " §7— stand at spawn 2");
            }
            case "setspawn1" -> {
                if (args.length < 2) { player.sendMessage(plugin.prefix() + "§cUsage: /arena setspawn1 <name>"); return true; }
                String name = args[1];
                if (plugin.getArenaManager().setSpawn1(name, player)) {
                    player.sendMessage(plugin.prefix() + "§aSpawn 1 for arena §e" + name + " §aset to your current location.");
                } else {
                    player.sendMessage(plugin.prefix() + "§cArena §e" + name + " §cdoes not exist.");
                }
            }
            case "setspawn2" -> {
                if (args.length < 2) { player.sendMessage(plugin.prefix() + "§cUsage: /arena setspawn2 <name>"); return true; }
                String name = args[1];
                if (plugin.getArenaManager().setSpawn2(name, player)) {
                    player.sendMessage(plugin.prefix() + "§aSpawn 2 for arena §e" + name + " §aset to your current location.");
                } else {
                    player.sendMessage(plugin.prefix() + "§cArena §e" + name + " §cdoes not exist.");
                }
            }
            case "delete" -> {
                if (args.length < 2) { player.sendMessage(plugin.prefix() + "§cUsage: /arena delete <name>"); return true; }
                String name = args[1];
                if (plugin.getArenaManager().deleteArena(name)) {
                    player.sendMessage(plugin.prefix() + "§aArena §e" + name + " §adeleted.");
                } else {
                    player.sendMessage(plugin.prefix() + "§cArena §e" + name + " §cdoes not exist.");
                }
            }
            case "list" -> {
                List<String> names = plugin.getArenaManager().getArenaNames();
                if (names.isEmpty()) {
                    player.sendMessage(plugin.prefix() + "§cNo arenas have been configured yet.");
                } else {
                    player.sendMessage(plugin.prefix() + "§aArenas: §e" + String.join("§7, §e", names));
                }
            }
            case "info" -> {
                if (args.length < 2) { player.sendMessage(plugin.prefix() + "§cUsage: /arena info <name>"); return true; }
                String name = args[1];
                ArenaTemplate t = plugin.getArenaManager().getArena(name);
                if (t == null) { player.sendMessage(plugin.prefix() + "§cArena §e" + name + " §cdoes not exist."); return true; }
                player.sendMessage("§8§m                              ");
                player.sendMessage("§e§lArena: §f" + t.getName());
                player.sendMessage("§fWorld: §e" + t.getWorldName());
                player.sendMessage("§fSpawn 1: §e" + formatCoords(t.getSpawn1X(), t.getSpawn1Y(), t.getSpawn1Z()));
                player.sendMessage("§fSpawn 2: §e" + formatCoords(t.getSpawn2X(), t.getSpawn2Y(), t.getSpawn2Z()));
                player.sendMessage("§fConfigured: §e" + (t.isFullyConfigured() ? "§aYes" : "§cNo (missing spawns)"));
                player.sendMessage("§8§m                              ");
            }
            default -> sendHelp(player);
        }
        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage("§8§m                              ");
        player.sendMessage("§c§lArena Commands");
        player.sendMessage("§f/arena create §e<name>    §7— Create world & teleport here");
        player.sendMessage("§f/arena setspawn1 §e<name> §7— Set spawn 1");
        player.sendMessage("§f/arena setspawn2 §e<name> §7— Set spawn 2");
        player.sendMessage("§f/arena delete §e<name>    §7— Delete arena");
        player.sendMessage("§f/arena list               §7— List all arenas");
        player.sendMessage("§f/arena info §e<name>      §7— Show arena details");
        player.sendMessage("§8§m                              ");
    }

    private String formatCoords(double x, double y, double z) {
        return String.format("%.1f, %.1f, %.1f", x, y, z);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("pvprooms.arena")) return List.of();
        if (args.length == 1) {
            return Arrays.asList("create", "setspawn1", "setspawn2", "delete", "list", "info").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("setspawn1") || sub.equals("setspawn2") || sub.equals("delete") || sub.equals("info")) {
                return plugin.getArenaManager().getArenaNames().stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }
        return List.of();
    }
}

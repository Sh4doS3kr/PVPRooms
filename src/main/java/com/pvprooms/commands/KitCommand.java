package com.pvprooms.commands;

import com.pvprooms.PvPRoomsPro;
import com.pvprooms.gui.KitEditorGUI;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

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
    private final KitEditorGUI editorGUI = new KitEditorGUI();

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
                var kit = plugin.getKitManager().getKit(name);
                if (kit == null) {
                    player.sendMessage(plugin.prefix() + "§cEl kit §e" + name + " §cno existe. Usa §f/kit create §cprimero.");
                } else {
                    editorGUI.open(player, kit);
                }
            }
            case "editicon" -> {
                if (args.length < 2) { player.sendMessage(plugin.prefix() + "§cUso: /kit editicon <nombre>"); return true; }
                String name = args[1];
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (hand.getType() == Material.AIR) {
                    player.sendMessage(plugin.prefix() + "§cTienes que tener el objeto deseado en la mano.");
                    return true;
                }
                if (plugin.getKitManager().setKitIcon(name, hand.getType())) {
                    player.sendMessage(plugin.prefix() + "§aIcono del kit §e" + name + " §acambiado a §f" + hand.getType().name() + "§a.");
                } else {
                    player.sendMessage(plugin.prefix() + "§cEl kit §e" + name + " §cno existe.");
                }
            }
            case "connect" -> {
                if (args.length < 3) { player.sendMessage(plugin.prefix() + "§cUso: /kit connect <kit> <arena|none>"); return true; }
                String kitName  = args[1];
                String arenaArg = args[2];
                boolean isNone  = arenaArg.equalsIgnoreCase("none") || arenaArg.equalsIgnoreCase("-");
                String arenaName = isNone ? null : arenaArg;

                if (!isNone && plugin.getArenaManager().getArena(arenaName) == null) {
                    player.sendMessage(plugin.prefix() + "§cLa arena §e" + arenaName + " §cno existe.");
                    return true;
                }
                if (plugin.getKitManager().connectKitToArena(kitName, arenaName)) {
                    if (isNone) {
                        player.sendMessage(plugin.prefix() + "§aKit §e" + kitName + " §adesvinculado. Usará arena aleatoria.");
                    } else {
                        player.sendMessage(plugin.prefix() + "§aKit §e" + kitName + " §avinculado a la arena §e" + arenaName + "§a.");
                    }
                } else {
                    player.sendMessage(plugin.prefix() + "§cEl kit §e" + kitName + " §cno existe.");
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
        player.sendMessage("§e/kit create §f<nombre>       §8» §7Crear kit del inventario");
        player.sendMessage("§e/kit edit §f<nombre>         §8» §7Editar kit en panel GUI");
        player.sendMessage("§e/kit editicon §f<nombre>     §8» §7Cambiar icono al item en mano");
        player.sendMessage("§e/kit connect §f<kit> <arena> §8» §7Vincular kit a arena");
        player.sendMessage("§e/kit connect §f<kit> none    §8» §7Desvincular (arena aleatoria)");
        player.sendMessage("§e/kit delete §f<nombre>       §8» §7Eliminar kit");
        player.sendMessage("§e/kit list                   §8» §7Listar todos los kits");
        player.sendMessage("§8§m══════════════════════════════");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("pvprooms.kit")) return List.of();
        if (args.length == 1) {
            return Arrays.asList("create", "edit", "editicon", "connect", "delete", "list").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        String sub = args[0].toLowerCase();
        if (args.length == 2 && (sub.equals("edit") || sub.equals("editicon")
                || sub.equals("delete") || sub.equals("connect"))) {
            return plugin.getKitManager().getKitNames().stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 3 && sub.equals("connect")) {
            List<String> options = new ArrayList<>(plugin.getArenaManager().getArenaNames());
            options.add("none");
            return options.stream()
                    .filter(s -> s.startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}

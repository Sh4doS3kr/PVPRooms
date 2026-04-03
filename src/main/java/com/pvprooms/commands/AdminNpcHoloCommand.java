package com.pvprooms.commands;

import com.pvprooms.PvPRoomsPro;
import com.pvprooms.managers.LeaderboardHologramManager;
import com.pvprooms.managers.NpcManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Admin commands for managing NPCs and Holograms.
 * Handles undo, delete, list, teleport, reload operations.
 */
public class AdminNpcHoloCommand implements CommandExecutor, TabCompleter {

    private final PvPRoomsPro plugin;
    
    // Track last action type for undo
    private static String lastActionType = null; // "npc" or "holo"

    public AdminNpcHoloCommand(PvPRoomsPro plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p) || !p.isOp()) {
            sender.sendMessage("§cSolo OPs pueden usar este comando.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "undo" -> handleUndo(sender);
            case "delete" -> handleDelete(sender, args);
            case "list" -> handleList(sender, args);
            case "tp" -> handleTeleport(sender, args);
            case "reload" -> handleReload(sender, args);
            case "kicktierlist" -> handleKickTierList(sender, args);
            default -> sendHelp(sender);
        }

        return true;
    }

    private void handleUndo(CommandSender sender) {
        NpcManager npcManager = plugin.getNpcManager();
        LeaderboardHologramManager holoManager = plugin.getHologramManager();

        // Try to undo NPC first
        if (npcManager != null) {
            Integer npcId = npcManager.undoLast();
            if (npcId != null) {
                sender.sendMessage("§a✓ Deshecho: NPC §e" + npcId + " §aeliminado.");
                return;
            }
        }

        // Try to undo hologram
        if (holoManager != null) {
            Integer holoId = holoManager.undoLast();
            if (holoId != null) {
                sender.sendMessage("§a✓ Deshecho: Holograma §e" + holoId + " §aeliminado.");
                return;
            }
        }

        sender.sendMessage("§cNo hay acciones para deshacer.");
    }

    private void handleDelete(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUso: /admin delete <npc|holo|nearest> [id]");
            return;
        }

        String type = args[1].toLowerCase();

        switch (type) {
            case "npc" -> {
                if (args.length < 3) {
                    sender.sendMessage("§cUso: /admin delete npc <id>");
                    return;
                }

                int id;
                try {
                    id = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cID inválido.");
                    return;
                }

                NpcManager npcManager = plugin.getNpcManager();
                if (npcManager != null && npcManager.deleteNpc(id)) {
                    sender.sendMessage("§a✓ NPC §e" + id + " §aeliminado.");
                } else {
                    sender.sendMessage("§cNo se encontró NPC con ID " + id);
                }
            }

            case "holo", "hologram" -> {
                if (args.length < 3) {
                    sender.sendMessage("§cUso: /admin delete holo <id>");
                    return;
                }

                int id;
                try {
                    id = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cID inválido.");
                    return;
                }

                LeaderboardHologramManager holoManager = plugin.getHologramManager();
                if (holoManager != null && holoManager.deleteHologram(id)) {
                    sender.sendMessage("§a✓ Holograma §e" + id + " §aeliminado.");
                } else {
                    sender.sendMessage("§cNo se encontró holograma con ID " + id);
                }
            }

            case "nearest" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§cSolo jugadores pueden usar este comando.");
                    return;
                }

                if (args.length < 3) {
                    sender.sendMessage("§cUso: /admin delete nearest <npc|holo>");
                    return;
                }

                String nearType = args[2].toLowerCase();

                if (nearType.equals("npc")) {
                    NpcManager npcManager = plugin.getNpcManager();
                    if (npcManager != null) {
                        var nearest = npcManager.getNearestNpc(player.getLocation(), 10);
                        if (nearest != null) {
                            npcManager.deleteNpc(nearest.id());
                            sender.sendMessage("§a✓ NPC más cercano §e" + nearest.id() + " §aeliminado.");
                        } else {
                            sender.sendMessage("§cNo hay NPCs cerca (radio 10 bloques).");
                        }
                    }
                } else if (nearType.equals("holo") || nearType.equals("hologram")) {
                    LeaderboardHologramManager holoManager = plugin.getHologramManager();
                    if (holoManager != null) {
                        var nearest = holoManager.getNearestHologram(player.getLocation(), 10);
                        if (nearest != null) {
                            holoManager.deleteHologram(nearest.id());
                            sender.sendMessage("§a✓ Holograma más cercano §e" + nearest.id() + " §aeliminado.");
                        } else {
                            sender.sendMessage("§cNo hay hologramas cerca (radio 10 bloques).");
                        }
                    }
                } else {
                    sender.sendMessage("§cUso: /admin delete nearest <npc|holo>");
                }
            }

            case "all" -> {
                if (args.length < 3) {
                    sender.sendMessage("§cUso: /admin delete all <npcs|holos>");
                    return;
                }

                String allType = args[2].toLowerCase();

                if (allType.equals("npcs")) {
                    NpcManager npcManager = plugin.getNpcManager();
                    if (npcManager != null) {
                        int count = 0;
                        for (var npc : new ArrayList<>(npcManager.getAllNpcs())) {
                            npcManager.deleteNpc(npc.id());
                            count++;
                        }
                        sender.sendMessage("§a✓ Eliminados §e" + count + " §aNPCs.");
                    }
                } else if (allType.equals("holos") || allType.equals("holograms")) {
                    LeaderboardHologramManager holoManager = plugin.getHologramManager();
                    if (holoManager != null) {
                        int count = 0;
                        for (var holo : new ArrayList<>(holoManager.getAllHolograms())) {
                            holoManager.deleteHologram(holo.id());
                            count++;
                        }
                        sender.sendMessage("§a✓ Eliminados §e" + count + " §ahologramas.");
                    }
                } else {
                    sender.sendMessage("§cUso: /admin delete all <npcs|holos>");
                }
            }

            default -> sender.sendMessage("§cUso: /admin delete <npc|holo|nearest|all> ...");
        }
    }

    private void handleList(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUso: /admin list <npcs|holos>");
            return;
        }

        String type = args[1].toLowerCase();

        if (type.equals("npcs")) {
            NpcManager npcManager = plugin.getNpcManager();
            if (npcManager != null) {
                var npcs = npcManager.getAllNpcs();
                if (npcs.isEmpty()) {
                    sender.sendMessage("§7No hay NPCs creados.");
                    return;
                }
                sender.sendMessage("§5§l⚔ NPCs §7(" + npcs.size() + ")");
                for (var npc : npcs) {
                    sender.sendMessage("§8• §e" + npc.id() + " §7- " + npc.type() + 
                        (npc.kit() != null ? " (" + npc.kit() + ")" : "") +
                        " §8@ §7" + formatLoc(npc.location()));
                }
            }
        } else if (type.equals("holos") || type.equals("holograms")) {
            LeaderboardHologramManager holoManager = plugin.getHologramManager();
            if (holoManager != null) {
                var holos = holoManager.getAllHolograms();
                if (holos.isEmpty()) {
                    sender.sendMessage("§7No hay hologramas creados.");
                    return;
                }
                sender.sendMessage("§5§l📊 Hologramas §7(" + holos.size() + ")");
                for (var holo : holos) {
                    sender.sendMessage("§8• §e" + holo.id() + " §7- " + holo.type() + 
                        (holo.subtype() != null ? " (" + holo.subtype() + ")" : "") +
                        " §8@ §7" + formatLoc(holo.location()));
                }
            }
        } else {
            sender.sendMessage("§cUso: /admin list <npcs|holos>");
        }
    }

    private void handleTeleport(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cSolo jugadores pueden usar este comando.");
            return;
        }

        if (args.length < 3) {
            sender.sendMessage("§cUso: /admin tp <npc|holo> <id>");
            return;
        }

        String type = args[1].toLowerCase();
        int id;
        try {
            id = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cID inválido.");
            return;
        }

        if (type.equals("npc")) {
            NpcManager npcManager = plugin.getNpcManager();
            if (npcManager != null) {
                var npc = npcManager.getNpc(id);
                if (npc != null) {
                    player.teleport(npc.location());
                    player.sendMessage("§a✓ Teletransportado al NPC §e" + id);
                } else {
                    player.sendMessage("§cNo se encontró NPC con ID " + id);
                }
            }
        } else if (type.equals("holo") || type.equals("hologram")) {
            LeaderboardHologramManager holoManager = plugin.getHologramManager();
            if (holoManager != null) {
                var holo = holoManager.getHologram(id);
                if (holo != null) {
                    player.teleport(holo.location());
                    player.sendMessage("§a✓ Teletransportado al holograma §e" + id);
                } else {
                    player.sendMessage("§cNo se encontró holograma con ID " + id);
                }
            }
        } else {
            sender.sendMessage("§cUso: /admin tp <npc|holo> <id>");
        }
    }

    private void handleReload(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUso: /admin reload <npcs|holos|all>");
            return;
        }

        String type = args[1].toLowerCase();

        if (type.equals("npcs") || type.equals("all")) {
            NpcManager npcManager = plugin.getNpcManager();
            if (npcManager != null) {
                npcManager.shutdown();
                npcManager.load();
                sender.sendMessage("§a✓ NPCs recargados.");
            }
        }

        if (type.equals("holos") || type.equals("holograms") || type.equals("all")) {
            LeaderboardHologramManager holoManager = plugin.getHologramManager();
            if (holoManager != null) {
                holoManager.shutdown();
                holoManager.load();
                sender.sendMessage("§a✓ Hologramas recargados.");
            }
        }

        if (!type.equals("npcs") && !type.equals("holos") && !type.equals("holograms") && !type.equals("all")) {
            sender.sendMessage("§cUso: /admin reload <npcs|holos|all>");
        }
    }

    private void handleKickTierList(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUso: /admin kicktierlist <jugador>");
            return;
        }

        Player target = plugin.getServer().getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage("§cJugador no encontrado: " + args[1]);
            return;
        }

        UUID uuid = target.getUniqueId();
        plugin.getTierManager().resetPlayer(uuid);
        sender.sendMessage("§a✓ Jugador §e" + target.getName() + " §aexpulsado de la tierlist.");
        target.sendMessage(plugin.prefix() + "§cHas sido expulsado de la tierlist por un administrador.");
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§5§l⚙ Admin NPC/Holograms");
        sender.sendMessage("§e/admin undo §7- Deshacer última acción");
        sender.sendMessage("§e/admin delete npc <id> §7- Eliminar NPC");
        sender.sendMessage("§e/admin delete holo <id> §7- Eliminar holograma");
        sender.sendMessage("§e/admin delete nearest <npc|holo> §7- Eliminar más cercano");
        sender.sendMessage("§e/admin delete all <npcs|holos> §7- Eliminar todos");
        sender.sendMessage("§e/admin list <npcs|holos> §7- Listar elementos");
        sender.sendMessage("§e/admin tp <npc|holo> <id> §7- Teletransportarte");
        sender.sendMessage("§e/admin reload <npcs|holos|all> §7- Recargar");
        sender.sendMessage("§e/admin kicktierlist <jugador> §7- Expulsar de la tierlist");
    }

    private String formatLoc(org.bukkit.Location loc) {
        return String.format("%.0f, %.0f, %.0f (%s)", loc.getX(), loc.getY(), loc.getZ(), loc.getWorld().getName());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(Arrays.asList("undo", "delete", "list", "tp", "reload", "kicktierlist"));
        } else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "delete" -> completions.addAll(Arrays.asList("npc", "holo", "nearest", "all"));
                case "list" -> completions.addAll(Arrays.asList("npcs", "holos"));
                case "tp" -> completions.addAll(Arrays.asList("npc", "holo"));
                case "reload" -> completions.addAll(Arrays.asList("npcs", "holos", "all"));
                case "kicktierlist" -> {
                    // Tab complete online player names
                    for (Player p : plugin.getServer().getOnlinePlayers()) {
                        completions.add(p.getName());
                    }
                }
            }
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("delete")) {
                if (args[1].equalsIgnoreCase("npc")) {
                    if (plugin.getNpcManager() != null) {
                        plugin.getNpcManager().getAllNpcs().forEach(n -> completions.add(String.valueOf(n.id())));
                    }
                } else if (args[1].equalsIgnoreCase("holo")) {
                    if (plugin.getHologramManager() != null) {
                        plugin.getHologramManager().getAllHolograms().forEach(h -> completions.add(String.valueOf(h.id())));
                    }
                } else if (args[1].equalsIgnoreCase("nearest")) {
                    completions.addAll(Arrays.asList("npc", "holo"));
                } else if (args[1].equalsIgnoreCase("all")) {
                    completions.addAll(Arrays.asList("npcs", "holos"));
                }
            } else if (args[0].equalsIgnoreCase("tp")) {
                if (args[1].equalsIgnoreCase("npc")) {
                    if (plugin.getNpcManager() != null) {
                        plugin.getNpcManager().getAllNpcs().forEach(n -> completions.add(String.valueOf(n.id())));
                    }
                } else if (args[1].equalsIgnoreCase("holo")) {
                    if (plugin.getHologramManager() != null) {
                        plugin.getHologramManager().getAllHolograms().forEach(h -> completions.add(String.valueOf(h.id())));
                    }
                }
            }
        }

        String input = args[args.length - 1].toLowerCase();
        return completions.stream()
            .filter(s -> s.toLowerCase().startsWith(input))
            .collect(Collectors.toList());
    }
}

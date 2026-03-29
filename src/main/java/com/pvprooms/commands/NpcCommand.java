package com.pvprooms.commands;

import com.pvprooms.PvPRoomsPro;
import com.pvprooms.managers.NpcManager;
import com.pvprooms.managers.NpcManager.NpcType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class NpcCommand implements CommandExecutor, TabCompleter {

    private final PvPRoomsPro plugin;

    public NpcCommand(PvPRoomsPro plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use this command.");
            return true;
        }

        if (!player.hasPermission("pvprooms.admin")) {
            player.sendMessage("§cNo tienes permiso para usar este comando.");
            return true;
        }

        NpcManager npcManager = plugin.getNpcManager();
        if (npcManager == null) {
            player.sendMessage("§cNPC system is not initialized.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "create" -> {
                if (args.length < 2) {
                    player.sendMessage("§cUso: /npc create <type> [kit]");
                    player.sendMessage("§7Tipos: queue, instant, unranked, ffa, stats, leaderboard, kits, shop, info, events, tournaments, practice, spectate, party, 1v1, 2v2, bo3, bo5");
                    return true;
                }

                String typeStr = args[1].toLowerCase();
                String kit = args.length > 2 ? args[2] : null;

                NpcType type = parseNpcType(typeStr);
                if (type == null) {
                    player.sendMessage("§cTipo de NPC inválido: " + typeStr);
                    return true;
                }

                String defaultName = getDefaultName(type, kit);
                int id = npcManager.createNpc(type, kit, player.getLocation(), defaultName);
                player.sendMessage("§a✓ NPC creado con ID §e" + id + " §aen tu posición.");
                player.sendMessage("§7Usa §e/npc name " + id + " <nombre> §7para cambiar el nombre.");
            }

            case "name" -> {
                if (args.length < 3) {
                    player.sendMessage("§cUso: /npc name <id> <nombre...>");
                    return true;
                }

                int id;
                try {
                    id = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    player.sendMessage("§cID inválido.");
                    return true;
                }

                String name = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                npcManager.setNpcName(id, name);
                player.sendMessage("§a✓ Nombre del NPC §e" + id + " §aactualizado.");
            }

            case "move" -> {
                if (args.length < 2) {
                    player.sendMessage("§cUso: /npc move <id>");
                    return true;
                }

                int id;
                try {
                    id = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    player.sendMessage("§cID inválido.");
                    return true;
                }

                npcManager.moveNpc(id, player.getLocation());
                player.sendMessage("§a✓ NPC §e" + id + " §amovido a tu posición.");
            }

            case "delete" -> {
                if (args.length < 2) {
                    player.sendMessage("§cUso: /npc delete <id>");
                    return true;
                }

                int id;
                try {
                    id = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    player.sendMessage("§cID inválido.");
                    return true;
                }

                if (npcManager.deleteNpc(id)) {
                    player.sendMessage("§a✓ NPC §e" + id + " §aeliminado.");
                } else {
                    player.sendMessage("§cNo se encontró NPC con ID " + id);
                }
            }

            case "list" -> {
                var npcs = npcManager.getAllNpcs();
                if (npcs.isEmpty()) {
                    player.sendMessage("§7No hay NPCs creados.");
                    return true;
                }

                player.sendMessage("§5§l⚔ NPCs §7(" + npcs.size() + ")");
                for (var npc : npcs) {
                    player.sendMessage("§8• §e" + npc.id() + " §7- " + npc.type() + " §8@ §7" + formatLoc(npc.location()));
                }
            }

            case "skin" -> {
                if (args.length < 3) {
                    player.sendMessage("§cUso: /npc skin <id> <skinName>");
                    return true;
                }
                player.sendMessage("§eSkin system requires Citizens or similar plugin for full support.");
            }

            default -> sendHelp(player);
        }

        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage("§5§l⚔ NPC Commands");
        player.sendMessage("§e/npc create <type> [kit] §7- Crear NPC en tu posición");
        player.sendMessage("§e/npc name <id> <nombre> §7- Cambiar nombre");
        player.sendMessage("§e/npc move <id> §7- Mover a tu posición");
        player.sendMessage("§e/npc delete <id> §7- Eliminar NPC");
        player.sendMessage("§e/npc list §7- Listar todos");
        player.sendMessage("§7Tipos: queue, instant, unranked, ffa, stats, leaderboard, kits, shop, info, events, tournaments, practice, spectate, party, 1v1, 2v2, bo3, bo5");
    }

    private NpcType parseNpcType(String type) {
        return switch (type) {
            case "queue" -> NpcType.QUEUE;
            case "instant" -> NpcType.INSTANT;
            case "unranked" -> NpcType.UNRANKED;
            case "ffa" -> NpcType.FFA;
            case "stats" -> NpcType.STATS;
            case "leaderboard" -> NpcType.LEADERBOARD;
            case "kits" -> NpcType.KITS;
            case "shop" -> NpcType.SHOP;
            case "info" -> NpcType.INFO;
            case "events" -> NpcType.EVENTS;
            case "tournaments" -> NpcType.TOURNAMENTS;
            case "practice" -> NpcType.PRACTICE;
            case "spectate" -> NpcType.SPECTATE;
            case "party" -> NpcType.PARTY;
            case "1v1" -> NpcType.DUEL_1V1;
            case "2v2" -> NpcType.DUEL_2V2;
            case "bo3" -> NpcType.BO3;
            case "bo5" -> NpcType.BO5;
            default -> null;
        };
    }

    private String getDefaultName(NpcType type, String kit) {
        String base = switch (type) {
            case QUEUE -> "&5&lQUEUE";
            case INSTANT -> "&e&lINSTANT MATCH";
            case UNRANKED -> "&7&lUNRANKED";
            case FFA -> "&c&lFFA";
            case STATS -> "&b&lSTATS";
            case LEADERBOARD -> "&6&lLEADERBOARD";
            case KITS -> "&a&lKITS";
            case SHOP -> "&d&lSHOP";
            case INFO -> "&f&lINFO";
            case EVENTS -> "&c&lEVENTS";
            case TOURNAMENTS -> "&5&lTOURNAMENTS";
            case PRACTICE -> "&7&lPRACTICE";
            case SPECTATE -> "&b&lSPECTATE";
            case PARTY -> "&e&lPARTY";
            case DUEL_1V1 -> "&c&l1v1";
            case DUEL_2V2 -> "&6&l2v2";
            case BO3 -> "&d&lBO3";
            case BO5 -> "&5&lBO5";
        };

        if (kit != null && !kit.isEmpty()) {
            base += " &7(" + kit + ")";
        }
        return base;
    }

    private String formatLoc(org.bukkit.Location loc) {
        return String.format("%.0f, %.0f, %.0f", loc.getX(), loc.getY(), loc.getZ());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(Arrays.asList("create", "name", "move", "delete", "list", "skin"));
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("create")) {
                completions.addAll(Arrays.asList("queue", "instant", "unranked", "ffa", "stats", "leaderboard", "kits", "shop", "info", "events", "tournaments", "practice", "spectate", "party", "1v1", "2v2", "bo3", "bo5"));
            } else if (Arrays.asList("name", "move", "delete", "skin").contains(args[0].toLowerCase())) {
                if (plugin.getNpcManager() != null) {
                    plugin.getNpcManager().getAllNpcs().forEach(n -> completions.add(String.valueOf(n.id())));
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("create")) {
            // Kit names
            if (plugin.getKitManager() != null) {
                completions.addAll(plugin.getKitManager().getKitNames());
            }
        }

        String input = args[args.length - 1].toLowerCase();
        return completions.stream()
            .filter(s -> s.toLowerCase().startsWith(input))
            .collect(Collectors.toList());
    }
}

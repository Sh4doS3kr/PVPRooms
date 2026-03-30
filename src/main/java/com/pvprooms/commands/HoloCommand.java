package com.pvprooms.commands;

import com.pvprooms.PvPRoomsPro;
import com.pvprooms.managers.LeaderboardHologramManager;
import com.pvprooms.managers.LeaderboardHologramManager.HoloType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class HoloCommand implements CommandExecutor, TabCompleter {

    private final PvPRoomsPro plugin;

    public HoloCommand(PvPRoomsPro plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use this command.");
            return true;
        }

        if (!player.isOp()) {
            player.sendMessage("§cSolo OPs pueden usar este comando.");
            return true;
        }

        LeaderboardHologramManager holoManager = plugin.getHologramManager();
        if (holoManager == null) {
            player.sendMessage("§cHologram system is not initialized.");
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
                    player.sendMessage("§cUso: /holo create <type> [subtype]");
                    player.sendMessage("§7Tipos: top, stats, info, event, custom");
                    return true;
                }

                String typeStr = args[1].toLowerCase();
                String subtype = args.length > 2 ? args[2].toLowerCase() : null;

                HoloType type = parseHoloType(typeStr, subtype);
                if (type == null) {
                    player.sendMessage("§cTipo de holograma inválido.");
                    return true;
                }

                int id = holoManager.createHologram(type, subtype, player.getLocation());
                player.sendMessage("§a✓ Holograma creado con ID §e" + id + " §aen tu posición.");
            }

            case "custom" -> {
                if (args.length < 2) {
                    player.sendMessage("§cUso: /holo custom <línea1> | <línea2> | ...");
                    return true;
                }

                String text = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                List<String> lines = Arrays.asList(text.split("\\|"));
                lines = lines.stream().map(String::trim).collect(Collectors.toList());

                int id = holoManager.createCustomHologram(player.getLocation(), lines);
                player.sendMessage("§a✓ Holograma personalizado creado con ID §e" + id);
            }

            case "addline" -> {
                if (args.length < 3) {
                    player.sendMessage("§cUso: /holo addline <id> <texto>");
                    return true;
                }

                int id;
                try {
                    id = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    player.sendMessage("§cID inválido.");
                    return true;
                }

                String text = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                holoManager.addLine(id, text);
                player.sendMessage("§a✓ Línea añadida al holograma §e" + id);
            }

            case "setline" -> {
                if (args.length < 4) {
                    player.sendMessage("§cUso: /holo setline <id> <lineNum> <texto>");
                    return true;
                }

                int id, lineNum;
                try {
                    id = Integer.parseInt(args[1]);
                    lineNum = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    player.sendMessage("§cID o número de línea inválido.");
                    return true;
                }

                String text = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
                holoManager.setLine(id, lineNum, text);
                player.sendMessage("§a✓ Línea §e" + lineNum + " §aactualizada en holograma §e" + id);
            }

            case "delline" -> {
                if (args.length < 3) {
                    player.sendMessage("§cUso: /holo delline <id> <lineNum>");
                    return true;
                }

                int id, lineNum;
                try {
                    id = Integer.parseInt(args[1]);
                    lineNum = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    player.sendMessage("§cID o número de línea inválido.");
                    return true;
                }

                holoManager.deleteLine(id, lineNum);
                player.sendMessage("§a✓ Línea §e" + lineNum + " §aeliminada del holograma §e" + id);
            }

            case "move" -> {
                if (args.length < 2) {
                    player.sendMessage("§cUso: /holo move <id>");
                    return true;
                }

                int id;
                try {
                    id = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    player.sendMessage("§cID inválido.");
                    return true;
                }

                holoManager.moveHologram(id, player.getLocation());
                player.sendMessage("§a✓ Holograma §e" + id + " §amovido a tu posición.");
            }

            case "delete" -> {
                if (args.length < 2) {
                    player.sendMessage("§cUso: /holo delete <id>");
                    return true;
                }

                int id;
                try {
                    id = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    player.sendMessage("§cID inválido.");
                    return true;
                }

                if (holoManager.deleteHologram(id)) {
                    player.sendMessage("§a✓ Holograma §e" + id + " §aeliminado.");
                } else {
                    player.sendMessage("§cNo se encontró holograma con ID " + id);
                }
            }

            case "list" -> {
                var holos = holoManager.getAllHolograms();
                if (holos.isEmpty()) {
                    player.sendMessage("§7No hay hologramas creados.");
                    return true;
                }

                player.sendMessage("§5§l📊 Hologramas §7(" + holos.size() + ")");
                for (var holo : holos) {
                    player.sendMessage("§8• §e" + holo.id() + " §7- " + holo.type() + " §8@ §7" + formatLoc(holo.location()));
                }
            }

            default -> sendHelp(player);
        }

        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage("§5§l📊 Hologram Commands");
        player.sendMessage("§e/holo create top [general|elo|wins|kit] §7- Top players");
        player.sendMessage("§e/holo create stats [online|duels|queue] §7- Stats en vivo");
        player.sendMessage("§e/holo create info [welcome|rules|ranks|kits|commands] §7- Info");
        player.sendMessage("§e/holo custom <líneas separadas por |> §7- Personalizado");
        player.sendMessage("§e/holo addline <id> <texto> §7- Añadir línea");
        player.sendMessage("§e/holo setline <id> <num> <texto> §7- Editar línea");
        player.sendMessage("§e/holo delline <id> <num> §7- Eliminar línea");
        player.sendMessage("§e/holo move <id> §7- Mover a tu posición");
        player.sendMessage("§e/holo delete <id> §7- Eliminar");
        player.sendMessage("§e/holo list §7- Listar todos");
    }

    private HoloType parseHoloType(String type, String subtype) {
        return switch (type) {
            case "top" -> {
                if (subtype == null || subtype.equals("general")) yield HoloType.TOP_GENERAL;
                yield switch (subtype) {
                    case "elo" -> HoloType.TOP_ELO;
                    case "wins" -> HoloType.TOP_WINS;
                    case "streak" -> HoloType.TOP_STREAK;
                    case "kdr" -> HoloType.TOP_KDR;
                    default -> HoloType.TOP_KIT;
                };
            }
            case "stats" -> {
                if (subtype == null) yield HoloType.STATS_ONLINE;
                yield switch (subtype) {
                    case "online" -> HoloType.STATS_ONLINE;
                    case "duels" -> HoloType.STATS_DUELS;
                    case "queue" -> HoloType.STATS_QUEUE;
                    case "today" -> HoloType.STATS_TODAY;
                    case "week" -> HoloType.STATS_WEEK;
                    default -> HoloType.STATS_ONLINE;
                };
            }
            case "info" -> {
                if (subtype == null) yield HoloType.INFO_WELCOME;
                yield switch (subtype) {
                    case "welcome" -> HoloType.INFO_WELCOME;
                    case "rules" -> HoloType.INFO_RULES;
                    case "ranks" -> HoloType.INFO_RANKS;
                    case "kits" -> HoloType.INFO_KITS;
                    case "commands" -> HoloType.INFO_COMMANDS;
                    case "rewards" -> HoloType.INFO_REWARDS;
                    case "elo" -> HoloType.INFO_ELO;
                    case "seasons" -> HoloType.INFO_SEASONS;
                    default -> HoloType.INFO_WELCOME;
                };
            }
            case "event" -> {
                if (subtype == null) yield HoloType.EVENT_NEXT;
                yield switch (subtype) {
                    case "next" -> HoloType.EVENT_NEXT;
                    case "active" -> HoloType.EVENT_ACTIVE;
                    case "winners" -> HoloType.EVENT_WINNERS;
                    default -> HoloType.EVENT_NEXT;
                };
            }
            case "custom" -> HoloType.CUSTOM;
            default -> null;
        };
    }

    private String formatLoc(org.bukkit.Location loc) {
        return String.format("%.0f, %.0f, %.0f", loc.getX(), loc.getY(), loc.getZ());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(Arrays.asList("create", "custom", "addline", "setline", "delline", "move", "delete", "list"));
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("create")) {
                completions.addAll(Arrays.asList("top", "stats", "info", "event"));
            } else if (Arrays.asList("addline", "setline", "delline", "move", "delete").contains(args[0].toLowerCase())) {
                if (plugin.getHologramManager() != null) {
                    plugin.getHologramManager().getAllHolograms().forEach(h -> completions.add(String.valueOf(h.id())));
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("create")) {
            String type = args[1].toLowerCase();
            switch (type) {
                case "top" -> completions.addAll(Arrays.asList("general", "elo", "wins", "streak", "kdr"));
                case "stats" -> completions.addAll(Arrays.asList("online", "duels", "queue", "today", "week"));
                case "info" -> completions.addAll(Arrays.asList("welcome", "rules", "ranks", "kits", "commands", "rewards", "elo", "seasons"));
                case "event" -> completions.addAll(Arrays.asList("next", "active", "winners"));
            }
        }

        String input = args[args.length - 1].toLowerCase();
        return completions.stream()
            .filter(s -> s.toLowerCase().startsWith(input))
            .collect(Collectors.toList());
    }
}

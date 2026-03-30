package com.pvprooms.commands;

import com.pvprooms.PvPRoomsPro;
import com.pvprooms.model.ArenaTemplate;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.WorldCreator;
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
        if (!(sender instanceof Player p) || !p.isOp()) {
            sender.sendMessage(plugin.prefix() + "§cSolo OPs pueden usar este comando.");
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
                if (args.length < 2) { player.sendMessage(plugin.prefix() + "§cUso: /arena create <nombre>"); return true; }
                String name = args[1];

                boolean existsOnDisk = new java.io.File(
                        org.bukkit.Bukkit.getWorldContainer(), name).isDirectory();
                boolean alreadyRegistered = plugin.getArenaManager().getArena(name) != null;

                // Si ya está registrada Y no hay carpeta en disco → nada que hacer
                if (alreadyRegistered && !existsOnDisk) {
                    player.sendMessage(plugin.prefix() + "§cLa arena §e" + name + " §cya está registrada y no se encontró la carpeta del mundo.");
                    player.sendMessage(plugin.prefix() + "§7Usa §e/arena setspawn1 §7y §e/arena setspawn2 §7para configurar los spawns.");
                    return true;
                }

                // ── Obtener/cargar/crear el mundo ──────────────────────────
                World world = Bukkit.getWorld(name);

                if (world == null && existsOnDisk) {
                    player.sendMessage(plugin.prefix() + "§7Carpeta encontrada, cargando mundo §e" + name + "§7...");
                    WorldCreator loader = new WorldCreator(name);
                    loader.generateStructures(false);
                    world = Bukkit.createWorld(loader);
                }

                if (world == null && !existsOnDisk) {
                    player.sendMessage(plugin.prefix() + "§7No se encontró carpeta, creando mundo vacío para §e" + name + "§7...");
                    WorldCreator creator = new WorldCreator(name);
                    creator.generator(plugin.getArenaInstanceManager().voidGenerator());
                    creator.generateStructures(false);
                    world = Bukkit.createWorld(creator);
                }

                if (world == null) {
                    player.sendMessage(plugin.prefix() + "§cError al cargar/crear el mundo §e" + name + "§c. Revisa la consola.");
                    return true;
                }

                world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
                world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
                world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
                world.setGameRule(GameRule.KEEP_INVENTORY, true);
                world.setTime(6000L);

                // Registrar solo si no lo estaba ya
                if (!alreadyRegistered) {
                    plugin.getArenaManager().createArena(name);
                }

                player.teleport(world.getSpawnLocation());

                String modoStr = existsOnDisk ? "§acargado desde disco" : "§acreado como mundo vacío";
                player.sendMessage(plugin.prefix() + "§aMundo §e" + name + " §a" + modoStr + " y registrado como arena.");
                player.sendMessage(plugin.prefix() + "§7Ahora establece los spawns:");
                player.sendMessage(plugin.prefix() + "§e  /arena setspawn1 " + name + " §8› §7Párate en el spawn 1 y ejecútalo");
                player.sendMessage(plugin.prefix() + "§e  /arena setspawn2 " + name + " §8› §7Párate en el spawn 2 y ejecútalo");
            }
            case "setspawn1" -> {
                if (args.length < 2) { player.sendMessage(plugin.prefix() + "§cUso: /arena setspawn1 <nombre>"); return true; }
                String name = args[1];
                if (plugin.getArenaManager().setSpawn1(name, player)) {
                    player.sendMessage(plugin.prefix() + "§aSpawn 1 de §e" + name + " §aestablecido en tu posición.");
                } else {
                    player.sendMessage(plugin.prefix() + "§cLa arena §e" + name + " §cno existe.");
                }
            }
            case "setspawn2" -> {
                if (args.length < 2) { player.sendMessage(plugin.prefix() + "§cUso: /arena setspawn2 <nombre>"); return true; }
                String name = args[1];
                if (plugin.getArenaManager().setSpawn2(name, player)) {
                    player.sendMessage(plugin.prefix() + "§aSpawn 2 de §e" + name + " §aestablecido en tu posición.");
                } else {
                    player.sendMessage(plugin.prefix() + "§cLa arena §e" + name + " §cno existe.");
                }
            }
            case "delete" -> {
                if (args.length < 2) { player.sendMessage(plugin.prefix() + "§cUso: /arena delete <nombre>"); return true; }
                String name = args[1];
                if (plugin.getArenaManager().deleteArena(name)) {
                    player.sendMessage(plugin.prefix() + "§aArena §e" + name + " §aeliminada correctamente.");
                } else {
                    player.sendMessage(plugin.prefix() + "§cLa arena §e" + name + " §cno existe.");
                }
            }
            case "list" -> {
                List<String> names = plugin.getArenaManager().getArenaNames();
                if (names.isEmpty()) {
                    player.sendMessage(plugin.prefix() + "§cAún no hay arenas configuradas.");
                } else {
                    player.sendMessage(plugin.prefix() + "§aArenas disponibles: §e" + String.join("§7, §e", names));
                }
            }
            case "info" -> {
                if (args.length < 2) { player.sendMessage(plugin.prefix() + "§cUso: /arena info <nombre>"); return true; }
                String name = args[1];
                ArenaTemplate t = plugin.getArenaManager().getArena(name);
                if (t == null) { player.sendMessage(plugin.prefix() + "§cLa arena §e" + name + " §cno existe."); return true; }
                player.sendMessage("§8§m══════════════════════════════");
                player.sendMessage("§6§l  ⚔ Arena: §f" + t.getName());
                player.sendMessage("§8§m══════════════════════════════");
                player.sendMessage("§eMundo: §f" + t.getWorldName());
                player.sendMessage("§eSpawn 1: §f" + formatCoords(t.getSpawn1X(), t.getSpawn1Y(), t.getSpawn1Z()));
                player.sendMessage("§eSpawn 2: §f" + formatCoords(t.getSpawn2X(), t.getSpawn2Y(), t.getSpawn2Z()));
                player.sendMessage("§eConfigurada: " + (t.isFullyConfigured() ? "§a✔ Sí" : "§c✘ No (faltan spawns)"));
                player.sendMessage("§8§m══════════════════════════════");
            }
            default -> sendHelp(player);
        }
        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage("§8§m══════════════════════════════");
        player.sendMessage("§6§l  ⚔ Comandos de Arena");
        player.sendMessage("§8§m══════════════════════════════");
        player.sendMessage("§e/arena create §f<nombre>    §8› §7Crear mundo y teleportarse");
        player.sendMessage("§e/arena setspawn1 §f<nombre> §8› §7Establecer spawn 1");
        player.sendMessage("§e/arena setspawn2 §f<nombre> §8› §7Establecer spawn 2");
        player.sendMessage("§e/arena delete §f<nombre>    §8› §7Eliminar arena");
        player.sendMessage("§e/arena list                 §8› §7Listar arenas");
        player.sendMessage("§e/arena info §f<nombre>      §8› §7Ver detalles");
        player.sendMessage("§8§m══════════════════════════════");
    }

    private String formatCoords(double x, double y, double z) {
        return String.format("%.1f, %.1f, %.1f", x, y, z);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p) || !p.isOp()) return List.of();
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

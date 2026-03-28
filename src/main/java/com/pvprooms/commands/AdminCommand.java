package com.pvprooms.commands;

import com.pvprooms.PvPRoomsPro;
import com.pvprooms.model.ArenaTemplate;
import com.pvprooms.model.Duel;
import com.pvprooms.weapons.SpearItem;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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
 * Handles /admin and /adminpanel commands.
 * Permission: pvprooms.admin
 *
 * Subcommands:
 *   /admin elo reset <player>
 *   /admin elo set <player> <value>
 *   /admin elo get <player>
 *   /admin elo resetall
 *   /admin kick <player>
 *   /admin forceend <player>
 *   /admin reload
 *   /admin info
 *   /adminpanel
 */
public class AdminCommand implements CommandExecutor, TabCompleter {

    private final PvPRoomsPro plugin;

    public AdminCommand(PvPRoomsPro plugin) {
        this.plugin = plugin;
    }

    // ── /adminpanel ────────────────────────────────────────────────────────

    public boolean openPanel(CommandSender sender) {
        if (!sender.hasPermission("pvprooms.admin")) {
            sender.sendMessage(plugin.prefix() + "§cNo tienes permiso para usar este comando.");
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.prefix() + "§cEste comando solo puede usarlo un jugador.");
            return true;
        }
        plugin.getAdminPanelGUI().open(player);
        return true;
    }

    // When /adminpanel is used WITH args (e.g. /adminpanel setupwall) treat as /admin
    private boolean handleAdminpanelWithArgs(CommandSender sender, String[] args) {
        if (!sender.hasPermission("pvprooms.admin")) {
            sender.sendMessage(plugin.prefix() + "§cNo tienes permiso para usar este comando.");
            return true;
        }
        return dispatchSubcommand(sender, args);
    }

    // ── /admin ────────────────────────────────────────────────────────────

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (label.equalsIgnoreCase("adminpanel")) return openPanel(sender);

        if (!sender.hasPermission("pvprooms.admin")) {
            sender.sendMessage(plugin.prefix() + "§cNo tienes permiso para usar este comando.");
            return true;
        }

        if (args.length == 0) {
            if (label.equalsIgnoreCase("adminpanel")) return openPanel(sender);
            sendHelp(sender);
            return true;
        }
        if (label.equalsIgnoreCase("adminpanel")) return handleAdminpanelWithArgs(sender, args);

        return dispatchSubcommand(sender, args);
    }

    private boolean dispatchSubcommand(CommandSender sender, String[] args) {
        switch (args[0].toLowerCase()) {
            case "elo" -> handleElo(sender, args);
            case "kick" -> handleKick(sender, args);
            case "forceend" -> handleForceEnd(sender, args);
            case "setupwall" -> handleSetupWall(sender, args);
            case "config"    -> handleConfig(sender, args);
            case "travel" -> handleTravel(sender, args);
            case "reload" -> handleReload(sender);
            case "info" -> handleInfo(sender);
            case "spear"     -> handleSpear(sender, args);
            default -> sendHelp(sender);
        }
        return true;
    }

    // ── ELO subcommands ───────────────────────────────────────────────────

    private void handleElo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(plugin.prefix() + "§cUso: /admin elo <reset|set|get|resetall> [jugador] [valor]");
            return;
        }
        switch (args[1].toLowerCase()) {
            case "reset" -> {
                if (args.length < 3) { sender.sendMessage(plugin.prefix() + "§cUso: /admin elo reset <jugador>"); return; }
                UUID uuid = resolveUUID(args[2]);
                if (uuid == null) { sender.sendMessage(plugin.prefix() + "§cJugador §e" + args[2] + " §cno encontrado."); return; }
                plugin.getEloManager().resetElo(uuid);
                int def = plugin.getEloManager().getDefaultElo();
                sender.sendMessage(plugin.prefix() + "§aELO de §e" + args[2] + " §arestablecido a §e" + def + "§a.");
                Player target = Bukkit.getPlayer(uuid);
                if (target != null) target.sendMessage(plugin.prefix() + "§7Un admin ha restablecido tu ELO a §e" + def + "§7.");
            }
            case "set" -> {
                if (args.length < 4) { sender.sendMessage(plugin.prefix() + "§cUso: /admin elo set <jugador> <valor>"); return; }
                UUID uuid = resolveUUID(args[2]);
                if (uuid == null) { sender.sendMessage(plugin.prefix() + "§cJugador §e" + args[2] + " §cno encontrado."); return; }
                int value;
                try { value = Integer.parseInt(args[3]); } catch (NumberFormatException e) {
                    sender.sendMessage(plugin.prefix() + "§cValor inválido: §e" + args[3]); return;
                }
                if (value < 0) { sender.sendMessage(plugin.prefix() + "§cEl ELO no puede ser negativo."); return; }
                String name = getDisplayName(uuid, args[2]);
                plugin.getEloManager().setElo(uuid, name, value);
                plugin.getEloManager().saveElo();
                sender.sendMessage(plugin.prefix() + "§aELO de §e" + args[2] + " §aestablecido a §e" + value + "§a.");
                Player target = Bukkit.getPlayer(uuid);
                if (target != null) target.sendMessage(plugin.prefix() + "§7Un admin ha establecido tu ELO a §e" + value + "§7.");
            }
            case "get" -> {
                if (args.length < 3) { sender.sendMessage(plugin.prefix() + "§cUso: /admin elo get <jugador>"); return; }
                UUID uuid = resolveUUID(args[2]);
                if (uuid == null) { sender.sendMessage(plugin.prefix() + "§cJugador §e" + args[2] + " §cno encontrado."); return; }
                int elo  = plugin.getEloManager().getElo(uuid);
                int rank = plugin.getEloManager().getRank(uuid);
                sender.sendMessage(plugin.prefix() + "§eELO de §f" + args[2] + "§e: §6" + elo
                        + " §7| Rango: §e#" + (rank == -1 ? "N/A" : rank));
            }
            case "resetall" -> {
                plugin.getEloManager().resetAllElo();
                int def = plugin.getEloManager().getDefaultElo();
                sender.sendMessage(plugin.prefix() + "§c⚠ ELO de TODOS los jugadores restablecido a §e" + def + "§c.");
                plugin.getLogger().warning(sender.getName() + " ha restablecido el ELO de todos los jugadores.");
            }
            default -> sender.sendMessage(plugin.prefix() + "§cSubcomando ELO desconocido. Usa: reset, set, get, resetall");
        }
    }

    // ── Kick ─────────────────────────────────────────────────────────────

    private void handleKick(CommandSender sender, String[] args) {
        if (args.length < 2) { sender.sendMessage(plugin.prefix() + "§cUso: /admin kick <jugador>"); return; }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) { sender.sendMessage(plugin.prefix() + "§cJugador §e" + args[1] + " §cno está online."); return; }

        UUID uuid = target.getUniqueId();
        boolean acted = false;

        if (plugin.getQueueManager().isInQueue(uuid)) {
            plugin.getQueueManager().removeFromQueue(uuid);
            plugin.getScoreboardManager().restoreLobbyScoreboard(target);
            target.sendMessage(plugin.prefix() + "§cFuiste sacado de la cola por un administrador.");
            sender.sendMessage(plugin.prefix() + "§a" + target.getName() + " §asacado de la cola.");
            acted = true;
        }

        Duel duel = plugin.getDuelManager().getDuelByPlayer(uuid);
        if (duel != null && duel.getState() != Duel.State.ENDED) {
            UUID opponent = duel.getOpponent(uuid);
            plugin.getDuelManager().endDuel(duel, opponent, "admin kick");
            sender.sendMessage(plugin.prefix() + "§aDuelo de §e" + target.getName() + " §aterminado forzadamente.");
            acted = true;
        }

        if (!acted) sender.sendMessage(plugin.prefix() + "§e" + target.getName() + " §eno está en cola ni en duelo.");
    }

    // ── ForceEnd ──────────────────────────────────────────────────────────

    private void handleForceEnd(CommandSender sender, String[] args) {
        if (args.length < 2) { sender.sendMessage(plugin.prefix() + "§cUso: /admin forceend <jugador>"); return; }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) { sender.sendMessage(plugin.prefix() + "§cJugador §e" + args[1] + " §cno está online."); return; }

        Duel duel = plugin.getDuelManager().getDuelByPlayer(target.getUniqueId());
        if (duel == null || duel.getState() == Duel.State.ENDED) {
            sender.sendMessage(plugin.prefix() + "§e" + target.getName() + " §eno está en un duelo activo.");
            return;
        }
        plugin.getDuelManager().endDuel(duel, null, "admin forceend");
        sender.sendMessage(plugin.prefix() + "§aDuelo terminado en §e§lempate §apor orden de admin.");
    }

    // ── SetupWall ────────────────────────────────────────────────

    private void handleSetupWall(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.prefix() + "§cEste subcomando solo puede usarlo un jugador."); return;
        }
        String worldName = player.getWorld().getName();

        // /admin setupwall (no args) → show usage
        if (args.length == 1) {
            sender.sendMessage(plugin.prefix() + "§7Uso: §f/admin setupwall <wallId> §7para recibir la herramienta.");
            sender.sendMessage(plugin.prefix() + "§7   : §f/admin setupwall <wallId> <bloque> §7para guardar.");
            sender.sendMessage(plugin.prefix() + "§7   : §f/admin setupwall list §7para listar muros.");
            sender.sendMessage(plugin.prefix() + "§7   : §f/admin setupwall remove <wallId> §7para eliminar.");
            return;
        }

        // /admin setupwall list
        if (args[1].equalsIgnoreCase("list")) {
            if (plugin.getArenaManager().getArena(worldName) == null) {
                sender.sendMessage(plugin.prefix() + "§cEste mundo no es una arena registrada."); return;
            }
            java.util.Set<String> ids = plugin.getWallManager().getWallIds(worldName);
            if (ids.isEmpty()) {
                sender.sendMessage(plugin.prefix() + "§eNingún muro configurado para §f" + worldName + "§e."); return;
            }
            sender.sendMessage(plugin.prefix() + "§aMusros de §e" + worldName + "§a: §f" + String.join("§7, §f", ids));
            return;
        }

        // /admin setupwall remove <wallId>
        if (args[1].equalsIgnoreCase("remove")) {
            if (args.length < 3) { sender.sendMessage(plugin.prefix() + "§cUso: /admin setupwall remove <wallId>"); return; }
            boolean removed = plugin.getWallManager().removeWall(worldName, args[2]);
            sender.sendMessage(removed
                    ? plugin.prefix() + "§aMuro §e" + args[2] + "§a eliminado de §e" + worldName + "§a."
                    : plugin.prefix() + "§cNo existe el muro §e" + args[2] + "§c en §e" + worldName + "§c.");
            return;
        }

        String wallId = args[1];

        // /admin setupwall <wallId>  → give tool
        if (args.length == 2) {
            plugin.getWallManager().giveSetupTool(player, wallId);
            return;
        }

        // /admin setupwall <wallId> <blockType>  → finalize
        Material mat = Material.matchMaterial(args[2].toUpperCase());
        if (mat == null) {
            sender.sendMessage(plugin.prefix() + "§cBloque desconocido: §e" + args[2]); return;
        }
        if (!plugin.getWallManager().hasFullSelection(player.getUniqueId())) {
            sender.sendMessage(plugin.prefix() + "§cAún no has seleccionado los dos puntos.");
            sender.sendMessage(plugin.prefix() + "§f/admin setupwall " + wallId + "§7 para recibir la herramienta.");
            return;
        }
        if (plugin.getArenaManager().getArena(worldName) == null) {
            sender.sendMessage(plugin.prefix() + "§cEste mundo (§e" + worldName + "§c) no es una arena registrada.");
            return;
        }
        int count = plugin.getWallManager().setupWall(worldName, wallId, player.getUniqueId(), mat);
        if (count <= 0) {
            sender.sendMessage(plugin.prefix() + "§cNo se encontraron bloques de tipo §e" + mat.name() + "§c en la selección.");
            return;
        }
        sender.sendMessage(plugin.prefix() + "§a¡Muro §e" + wallId + "§a guardado! §f" + count
                + "§a bloques §f" + mat.name() + "§a en §e" + worldName + "§a.");
    }

    // ── Config map ─────────────────────────────────────────────

    private void handleConfig(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.prefix() + "§cEste subcomando solo puede usarlo un jugador."); return;
        }
        // args[1] == "map" (only sub-config for now)
        if (args.length < 2 || !args[1].equalsIgnoreCase("map")) {
            sender.sendMessage(plugin.prefix() + "§7Uso: §f/admin config map [arena]");
            return;
        }
        // Determine arena: optional args[2], otherwise infer from current world
        String arenaName;
        if (args.length >= 3) {
            arenaName = args[2];
        } else {
            arenaName = player.getWorld().getName();
        }
        ArenaTemplate template = plugin.getArenaManager().getArena(arenaName);
        if (template == null) {
            sender.sendMessage(plugin.prefix() + "§cArena §e" + arenaName + "§c no encontrada.");
            sender.sendMessage(plugin.prefix() + "§7Usa §f/admin travel <arena>§7 para ir al mundo de la arena primero.");
            return;
        }
        player.openInventory(plugin.getArenaConfigGUI().build(template));
    }

    // ── Travel ────────────────────────────────────────────────

    private void handleTravel(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.prefix() + "§cEste subcomando solo puede usarlo un jugador.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(plugin.prefix() + "§cUso: /admin travel <mundo>");
            return;
        }
        String worldName = args[1];
        org.bukkit.World world = org.bukkit.Bukkit.getWorld(worldName);
        if (world == null) {
            // Intentar cargar el mundo si existe en disco
            java.io.File worldFolder = new java.io.File(org.bukkit.Bukkit.getWorldContainer(), worldName);
            if (worldFolder.exists() && worldFolder.isDirectory()) {
                world = org.bukkit.Bukkit.createWorld(new org.bukkit.WorldCreator(worldName));
            }
        }
        if (world == null) {
            sender.sendMessage(plugin.prefix() + "§cNo existe ningún mundo llamado §e" + worldName + "§c.");
            return;
        }
        player.setGameMode(org.bukkit.GameMode.CREATIVE);
        player.teleport(world.getSpawnLocation());
        sender.sendMessage(plugin.prefix() + "§aTeleportado al mundo §e" + world.getName()
                + " §a. Modo creativo activado.");
    }

    // ── Spear ─────────────────────────────────────────────────────────────

    private void handleSpear(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.prefix() + "§cSolo un jugador puede usar este comando.");
            return;
        }
        if (!player.hasPermission("pvprooms.admin")) {
            player.sendMessage(plugin.prefix() + "§cSin permiso.");
            return;
        }

        double damage = 7.0;
        double speed  = -2.8;

        if (args.length >= 4) {
            try {
                damage = Double.parseDouble(args[2]);
                speed  = Double.parseDouble(args[3]);
            } catch (NumberFormatException e) {
                player.sendMessage(plugin.prefix() + "§cUso: /admin spear give [daño] [velocidad]");
                return;
            }
        }

        org.bukkit.inventory.ItemStack spear = SpearItem.create(plugin, damage, speed);
        player.getInventory().addItem(spear);
        player.sendMessage(plugin.prefix()
                + "§a¡Lanza entregada! §7(daño §f" + damage + "§7, vel §f" + speed + "§7)");
        player.sendMessage(plugin.prefix()
                + "§7Colócala en un kit con §f/kit edit§7. El attribute swap funcionará en Paper.");
    }

    // ── Reload ────────────────────────────────────────────────────────────

    private void handleReload(CommandSender sender) {
        plugin.reloadConfig();
        sender.sendMessage(plugin.prefix() + "§aconfig.yml recargado correctamente.");
    }

    // ── Info ──────────────────────────────────────────────────────────────

    private void handleInfo(CommandSender sender) {
        sender.sendMessage("§8§m══════════════════════════════");
        sender.sendMessage("§6§l  ⚙ PvPRoomsPro — Info");
        sender.sendMessage("§8§m══════════════════════════════");
        sender.sendMessage("§e Versión:    §f" + plugin.getDescription().getVersion());
        sender.sendMessage("§e Kits:       §f" + plugin.getKitManager().getAllKits().size());
        sender.sendMessage("§e Arenas:     §f" + plugin.getArenaManager().getAllArenas().size());
        sender.sendMessage("§e Jugadores:  §f" + plugin.getEloManager().getPlayerCount() + " registrados");
        sender.sendMessage("§e Duelos:     §f" + plugin.getDuelManager().getActiveDuelCount() + " activos");
        sender.sendMessage("§e En cola:    §f" + plugin.getQueueManager().getTotalQueued());
        sender.sendMessage("§e ELO base:   §f" + plugin.getEloManager().getDefaultElo());
        sender.sendMessage("§8§m══════════════════════════════");
    }

    // ── Help ──────────────────────────────────────────────────────────────

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§8§m══════════════════════════════════════");
        sender.sendMessage("§4§l  ⚙ Comandos de Administración");
        sender.sendMessage("§8§m══════════════════════════════════════");
        sender.sendMessage("§e/adminpanel                        §8» §7Panel gráfico");
        sender.sendMessage("§e/admin elo reset §f<jugador>        §8» §7Resetear ELO");
        sender.sendMessage("§e/admin elo set §f<jugador> <valor>  §8» §7Establecer ELO");
        sender.sendMessage("§e/admin elo get §f<jugador>          §8» §7Ver ELO");
        sender.sendMessage("§e/admin elo resetall                §8» §7⚠ Resetear TODOS");
        sender.sendMessage("§e/admin kick §f<jugador>             §8» §7Sacar de cola/duelo");
        sender.sendMessage("§e/admin forceend §f<jugador>         §8» §7Terminar duelo (empate)");
        sender.sendMessage("§e/admin setupwall §f<id>             §8» §7Herramienta de muro");
        sender.sendMessage("§e/admin setupwall §f<id> <bloque>    §8» §7Guardar muro");
        sender.sendMessage("§e/admin config map §f[arena]         §8» §7Config del mapa (explosiones, bloques)");
        sender.sendMessage("§e/admin travel §f<mundo>              §8» §7Teleportarse a un mundo");
        sender.sendMessage("§e/admin reload                      §8» §7Recargar config");
        sender.sendMessage("§e/admin info                        §8» §7Info del plugin");
        sender.sendMessage("§8§m══════════════════════════════════════");
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private UUID resolveUUID(String name) {
        Player online = Bukkit.getPlayer(name);
        if (online != null) return online.getUniqueId();
        return plugin.getEloManager().getUUIDByName(name);
    }

    private String getDisplayName(UUID uuid, String fallback) {
        Player online = Bukkit.getPlayer(uuid);
        return online != null ? online.getName() : fallback;
    }

    // ── Tab completion ────────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("pvprooms.admin")) return List.of();
        if (label.equalsIgnoreCase("adminpanel")) return List.of();

        if (args.length == 1) {
            return Arrays.asList("elo", "kick", "forceend", "setupwall", "config", "travel", "reload", "info").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("elo")) {
            return Arrays.asList("reset", "set", "get", "resetall").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        // World name suggestions for /admin travel
        if (args.length == 2 && args[0].equalsIgnoreCase("travel")) {
            String partial = args[1].toLowerCase();
            List<String> worlds = new ArrayList<>();
            org.bukkit.Bukkit.getWorlds().stream()
                    .map(org.bukkit.World::getName)
                    .filter(n -> n.toLowerCase().startsWith(partial))
                    .forEach(worlds::add);
            // Also suggest unloaded world folders
            java.io.File container = org.bukkit.Bukkit.getWorldContainer();
            java.io.File[] dirs = container.listFiles(java.io.File::isDirectory);
            if (dirs != null) {
                for (java.io.File d : dirs) {
                    if (d.getName().toLowerCase().startsWith(partial) && !worlds.contains(d.getName())) {
                        worlds.add(d.getName());
                    }
                }
            }
            return worlds;
        }

        // Player name suggestions for commands that need a player argument
        boolean needsPlayer = (args.length == 2 && (args[0].equalsIgnoreCase("kick")
                || args[0].equalsIgnoreCase("forceend")))
                || (args.length == 3 && args[0].equalsIgnoreCase("elo")
                && !args[1].equalsIgnoreCase("resetall"));
        if (needsPlayer) {
            String partial = args[args.length - 1].toLowerCase();
            List<String> names = new ArrayList<>();
            Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(partial))
                    .forEach(names::add);
            // Also suggest from ELO name cache (offline players)
            plugin.getEloManager().getNameMap().values().stream()
                    .filter(n -> n.toLowerCase().startsWith(partial) && !names.contains(n))
                    .forEach(names::add);
            return names;
        }
        return List.of();
    }
}

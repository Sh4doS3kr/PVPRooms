package com.pvprooms.commands;

import com.pvprooms.PvPRoomsPro;
import com.pvprooms.model.ArenaTemplate;
import com.pvprooms.model.Duel;
import com.pvprooms.weapons.SpearItem;
import com.pvprooms.util.PresetKits;
import com.pvprooms.model.Tier;
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
        if (!(sender instanceof Player p) || !p.isOp()) {
            sender.sendMessage(plugin.prefix() + "§cSolo OPs pueden usar este comando.");
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
        if (!(sender instanceof Player p) || !p.isOp()) {
            sender.sendMessage(plugin.prefix() + "§cSolo OPs pueden usar este comando.");
            return true;
        }
        return dispatchSubcommand(sender, args);
    }

    // ── /admin ────────────────────────────────────────────────────────────

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (label.equalsIgnoreCase("adminpanel")) return openPanel(sender);

        if (!(sender instanceof Player pl) || !pl.isOp()) {
            sender.sendMessage(plugin.prefix() + "§cSolo OPs pueden usar este comando.");
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
            case "resetall"  -> handleResetAll(sender, args);
            case "createkit" -> handleCreateKit(sender, args);
            case "settier"   -> handleSetTier(sender, args);
            case "mc", "multiaccount" -> handleMultiaccount(sender, args);
            default -> sendHelp(sender);
        }
        return true;
    }

    // ── Reset ALL data ────────────────────────────────────────────────────

    private void handleResetAll(CommandSender sender, String[] args) {
        if (args.length < 2 || !args[1].equalsIgnoreCase("confirm")) {
            sender.sendMessage(plugin.prefix() + "§4§l⚠ ADVERTENCIA ⚠");
            sender.sendMessage(plugin.prefix() + "§cEsto borrará TODOS los datos de TODOS los jugadores:");
            sender.sendMessage("§7  • ELO de todos los jugadores");
            sender.sendMessage("§7  • Tiers de todos los jugadores");
            sender.sendMessage("§7  • Puntos de todos los kits");
            sender.sendMessage("§7  • Rankings y estadísticas");
            sender.sendMessage("");
            sender.sendMessage(plugin.prefix() + "§cEscribe §e/admin resetall confirm §cpara confirmar.");
            return;
        }

        // Reset ELO
        plugin.getEloManager().resetAllElo();
        // Reset Tiers
        plugin.getTierManager().resetAllData();

        sender.sendMessage(plugin.prefix() + "§4§l☠ TODOS LOS DATOS HAN SIDO RESETEADOS");
        sender.sendMessage(plugin.prefix() + "§cELO y Tiers de todos los jugadores han sido eliminados.");
        plugin.getLogger().warning("§4" + sender.getName() + " ha reseteado TODOS los datos de jugadores.");

        // Notify online players
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(plugin.prefix() + "§c§lTus estadísticas han sido reseteadas por un administrador.");
            plugin.getScoreboardManager().restoreLobbyScoreboard(p);
        }
    }

    // ── Create preset kit ─────────────────────────────────────────────────

    private void handleCreateKit(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(plugin.prefix() + "§cUso: /admin createkit <nombre>");
            sender.sendMessage(plugin.prefix() + "§7Kits disponibles: §esword, axepvp, nethpot, uhc, smp, crystal, mace, spear, all");
            return;
        }

        String kitName = args[1].toLowerCase();

        if (kitName.equals("all")) {
            int count = PresetKits.installAllPresets(plugin);
            sender.sendMessage(plugin.prefix() + "§a✔ Se han instalado §e" + count + " §akits oficiales.");
            sender.sendMessage("§7  • §bSword §8- §7Classic 1.9+");
            sender.sendMessage("§7  • §6AxePvP §8- §7Vanilla Axe combat");
            sender.sendMessage("§7  • §dNethpot §8- §7Netherite Pot PvP");
            sender.sendMessage("§7  • §eUHC §8- §7Ultra Hardcore");
            sender.sendMessage("§7  • §2SMP §8- §7Survival full gear");
            sender.sendMessage("§7  • §5Crystal §8- §7Crystal PvP");
            sender.sendMessage("§7  • §8Mace §8- §71.21 Mace combat");
            sender.sendMessage("§7  • §3Spear §8- §7Trident + Attribute Swap");
            return;
        }

        if (PresetKits.installPreset(plugin, kitName)) {
            sender.sendMessage(plugin.prefix() + "§a✔ Kit §e" + kitName + " §ainstalado correctamente.");
            sender.sendMessage(plugin.prefix() + "§7Usa §e/queue §7para probarlo.");
        } else {
            sender.sendMessage(plugin.prefix() + "§cKit §e" + kitName + " §cno encontrado.");
            sender.sendMessage(plugin.prefix() + "§7Kits disponibles: §esword, axepvp, nethpot, uhc, smp, crystal, mace, spear, all");
        }
    }

    // ── Set Tier (Elite verification via Discord ticket) ──────────────────

    private void handleSetTier(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(plugin.prefix() + "§cUso: /admin settier <jugador> <tier> <kit>");
            sender.sendMessage(plugin.prefix() + "§7Tiers: §eLT5, HT5, LT4, HT4, LT3, HT3, LT2, HT2, LT1, HT1");
            sender.sendMessage("");
            sender.sendMessage(plugin.prefix() + "§d§l⚡ TIERS ÉLITE (LT2+)");
            sender.sendMessage("§7Los tiers §cLT2, HT2, LT1, HT1 §7son de élite.");
            sender.sendMessage("§7Requieren verificación via ticket en:");
            sender.sendMessage("§b§n discord.mlmc.lat");
            return;
        }

        String playerName = args[1];
        String tierName = args[2].toUpperCase();
        String kitName = args[3].toLowerCase();

        // Resolve player UUID
        UUID uuid = resolveUUID(playerName);
        if (uuid == null) {
            sender.sendMessage(plugin.prefix() + "§cJugador §e" + playerName + " §cno encontrado.");
            return;
        }

        // Parse tier
        Tier tier;
        try {
            tier = Tier.valueOf(tierName);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(plugin.prefix() + "§cTier §e" + tierName + " §cno válido.");
            sender.sendMessage(plugin.prefix() + "§7Tiers: §eLT5, HT5, LT4, HT4, LT3, HT3, LT2, HT2, LT1, HT1");
            return;
        }

        // Verify kit exists
        if (!plugin.getKitManager().kitExists(kitName)) {
            sender.sendMessage(plugin.prefix() + "§cKit §e" + kitName + " §cno existe.");
            return;
        }

        // Calculate points needed for this tier
        int points = tier.minPoints;
        if (points < 0) points = 0;

        // Set the tier points directly
        plugin.getTierManager().setPoints(uuid, kitName, points);

        // Notify
        String tierDisplay = tier.formatted();
        sender.sendMessage(plugin.prefix() + "§a✔ Tier de §e" + playerName + " §aen §e" + kitName + " §aestablecido a " + tierDisplay);
        
        // Check if elite tier
        if (tier.ordinal() >= Tier.LT2.ordinal()) {
            sender.sendMessage(plugin.prefix() + "§d⚡ §7Tier de élite asignado via verificación.");
        }

        // Notify player if online
        Player target = Bukkit.getPlayer(uuid);
        if (target != null) {
            target.sendMessage(plugin.prefix() + "§d§l⚡ ¡TIER ÉLITE VERIFICADO!");
            target.sendMessage(plugin.prefix() + "§7Tu tier en §e" + kitName + " §7ha sido establecido a " + tierDisplay);
            target.sendMessage(plugin.prefix() + "§7Verificado por: §e" + sender.getName());
            plugin.getScoreboardManager().restoreLobbyScoreboard(target);
        }
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

    // ── Anti-Multiaccount ─────────────────────────────────────────────────

    private void handleMultiaccount(CommandSender sender, String[] args) {
        var antiMc = plugin.getAntiMultiaccount();
        if (antiMc == null) {
            sender.sendMessage(plugin.prefix() + "§cSistema anti-multicuenta no disponible.");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(plugin.prefix() + "§e/admin mc whitelist <ip|player> <valor> §8- §7Añadir a whitelist");
            sender.sendMessage(plugin.prefix() + "§e/admin mc unwhitelist <ip|player> <valor> §8- §7Quitar de whitelist");
            sender.sendMessage(plugin.prefix() + "§e/admin mc check <jugador> §8- §7Ver IP y cuentas asociadas");
            sender.sendMessage(plugin.prefix() + "§e/admin mc list §8- §7Ver whitelist");
            return;
        }

        switch (args[1].toLowerCase()) {
            case "whitelist" -> {
                if (args.length < 4) {
                    sender.sendMessage(plugin.prefix() + "§cUso: /admin mc whitelist <ip|player> <valor>");
                    return;
                }
                String type = args[2].toLowerCase();
                String value = args[3];
                
                if (type.equals("ip")) {
                    antiMc.whitelistIp(value);
                    sender.sendMessage(plugin.prefix() + "§aIP §e" + value + " §aañadida a la whitelist.");
                } else if (type.equals("player")) {
                    Player target = Bukkit.getPlayer(value);
                    if (target == null) {
                        sender.sendMessage(plugin.prefix() + "§cJugador §e" + value + " §cno está online.");
                        return;
                    }
                    antiMc.whitelistPlayer(target.getUniqueId());
                    sender.sendMessage(plugin.prefix() + "§aJugador §e" + target.getName() + " §aañadido a la whitelist.");
                } else {
                    sender.sendMessage(plugin.prefix() + "§cTipo inválido. Usa: ip, player");
                }
            }
            case "unwhitelist" -> {
                if (args.length < 4) {
                    sender.sendMessage(plugin.prefix() + "§cUso: /admin mc unwhitelist <ip|player> <valor>");
                    return;
                }
                String type = args[2].toLowerCase();
                String value = args[3];
                
                if (type.equals("ip")) {
                    antiMc.unwhitelistIp(value);
                    sender.sendMessage(plugin.prefix() + "§cIP §e" + value + " §celiminada de la whitelist.");
                } else if (type.equals("player")) {
                    Player target = Bukkit.getPlayer(value);
                    if (target == null) {
                        sender.sendMessage(plugin.prefix() + "§cJugador §e" + value + " §cno está online.");
                        return;
                    }
                    antiMc.unwhitelistPlayer(target.getUniqueId());
                    sender.sendMessage(plugin.prefix() + "§cJugador §e" + target.getName() + " §celiminado de la whitelist.");
                } else {
                    sender.sendMessage(plugin.prefix() + "§cTipo inválido. Usa: ip, player");
                }
            }
            case "check" -> {
                if (args.length < 3) {
                    sender.sendMessage(plugin.prefix() + "§cUso: /admin mc check <jugador>");
                    return;
                }
                Player target = Bukkit.getPlayer(args[2]);
                if (target == null) {
                    sender.sendMessage(plugin.prefix() + "§cJugador §e" + args[2] + " §cno está online.");
                    return;
                }
                
                String ip = antiMc.getPlayerIp(target);
                sender.sendMessage(plugin.prefix() + "§eJugador: §f" + target.getName());
                sender.sendMessage(plugin.prefix() + "§eIP: §f" + ip);
                
                var accounts = antiMc.getAccountsForIp(ip);
                if (accounts.isEmpty()) {
                    sender.sendMessage(plugin.prefix() + "§7No hay otras cuentas conocidas.");
                } else {
                    sender.sendMessage(plugin.prefix() + "§c⚠ Cuentas asociadas a esta IP (" + accounts.size() + "):");
                    for (UUID uuid : accounts) {
                        String name = Bukkit.getOfflinePlayer(uuid).getName();
                        boolean online = Bukkit.getPlayer(uuid) != null;
                        sender.sendMessage("§7  • §f" + (name != null ? name : uuid.toString().substring(0, 8)) 
                                + (online ? " §a(online)" : " §8(offline)"));
                    }
                }
            }
            case "list" -> {
                sender.sendMessage(plugin.prefix() + "§e=== Whitelist Anti-Multicuenta ===");
                
                var ips = antiMc.getWhitelistedIps();
                if (ips.isEmpty()) {
                    sender.sendMessage(plugin.prefix() + "§7IPs: §8(ninguna)");
                } else {
                    sender.sendMessage(plugin.prefix() + "§7IPs: §f" + String.join(", ", ips));
                }
                
                var players = antiMc.getWhitelistedPlayers();
                if (players.isEmpty()) {
                    sender.sendMessage(plugin.prefix() + "§7Jugadores: §8(ninguno)");
                } else {
                    StringBuilder sb = new StringBuilder();
                    for (UUID uuid : players) {
                        String name = Bukkit.getOfflinePlayer(uuid).getName();
                        if (sb.length() > 0) sb.append(", ");
                        sb.append(name != null ? name : uuid.toString().substring(0, 8));
                    }
                    sender.sendMessage(plugin.prefix() + "§7Jugadores: §f" + sb);
                }
            }
            default -> sender.sendMessage(plugin.prefix() + "§cSubcomando desconocido. Usa: whitelist, unwhitelist, check, list");
        }
    }

    // ── Help ──────────────────────────────────────────────────────────────

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§8§m══════════════════════════════════════");
        sender.sendMessage("§4§l  ⚙ Comandos de Administración");
        sender.sendMessage("§8§m══════════════════════════════════════");
        sender.sendMessage("§e/adminpanel                        §8› §7Panel gráfico");
        sender.sendMessage("§e/admin elo reset §f<jugador>        §8› §7Resetear ELO");
        sender.sendMessage("§e/admin elo set §f<jugador> <valor>  §8› §7Establecer ELO");
        sender.sendMessage("§e/admin elo get §f<jugador>          §8› §7Ver ELO");
        sender.sendMessage("§e/admin elo resetall                §8› §7⚠ Resetear TODOS los ELO");
        sender.sendMessage("§4/admin resetall confirm            §8› §c☠ BORRAR TODO (ELO+Tiers)");
        sender.sendMessage("§e/admin kick §f<jugador>             §8› §7Sacar de cola/duelo");
        sender.sendMessage("§e/admin forceend §f<jugador>         §8› §7Terminar duelo (empate)");
        sender.sendMessage("§e/admin setupwall §f<id>             §8› §7Herramienta de muro");
        sender.sendMessage("§e/admin setupwall §f<id> <bloque>    §8› §7Guardar muro");
        sender.sendMessage("§e/admin config map §f[arena]         §8› §7Config del mapa (explosiones, bloques)");
        sender.sendMessage("§e/admin travel §f<mundo>              §8› §7Teleportarse a un mundo");
        sender.sendMessage("§e/admin createkit §f<nombre>          §8› §7Instalar kit oficial");
        sender.sendMessage("§d/admin settier §f<user> <tier> <kit> §8› §7Verificar tier élite");
        sender.sendMessage("§c/admin mc §f<whitelist|check|list>  §8› §7Anti-multicuenta");
        sender.sendMessage("§e/admin reload                      §8› §7Recargar config");
        sender.sendMessage("§e/admin info                        §8› §7Info del plugin");
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
            return Arrays.asList("elo", "kick", "forceend", "setupwall", "config", "travel", "createkit", "settier", "mc", "reload", "info").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        // mc (multiaccount) tab completion
        if (args[0].equalsIgnoreCase("mc")) {
            if (args.length == 2) {
                return Arrays.asList("whitelist", "unwhitelist", "check", "list").stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
            if (args.length == 3 && (args[1].equalsIgnoreCase("whitelist") || args[1].equalsIgnoreCase("unwhitelist"))) {
                return Arrays.asList("ip", "player").stream()
                        .filter(s -> s.startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("check")) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(n -> n.toLowerCase().startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
            }
            if (args.length == 4 && args[2].equalsIgnoreCase("player")) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(n -> n.toLowerCase().startsWith(args[3].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }
        // settier tab completion: /admin settier <player> <tier> <kit>
        if (args[0].equalsIgnoreCase("settier")) {
            if (args.length == 2) {
                // Player names
                String partial = args[1].toLowerCase();
                List<String> names = new ArrayList<>();
                Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(n -> n.toLowerCase().startsWith(partial))
                        .forEach(names::add);
                return names;
            }
            if (args.length == 3) {
                // Tier names
                return Arrays.asList("LT5", "HT5", "LT4", "HT4", "LT3", "HT3", "LT2", "HT2", "LT1", "HT1").stream()
                        .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
            }
            if (args.length == 4) {
                // Kit names
                String partial = args[3].toLowerCase();
                return plugin.getKitManager().getKitNames().stream()
                        .filter(k -> k.toLowerCase().startsWith(partial))
                        .collect(Collectors.toList());
            }
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("createkit")) {
            return Arrays.asList("sword", "axepvp", "nethpot", "uhc", "smp", "crystal", "mace", "spear", "all").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
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

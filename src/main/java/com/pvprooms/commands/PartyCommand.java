package com.pvprooms.commands;

import com.pvprooms.PvPRoomsPro;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Party management command.
 * 
 * Usage:
 *   /party create - Create a new party
 *   /party invite <player> - Invite a player
 *   /party accept - Accept pending invite
 *   /party deny - Deny pending invite
 *   /party leave - Leave current party
 *   /party kick <player> - Kick a member (leader only)
 *   /party disband - Disband the party (leader only)
 *   /party list - Show party members
 */
public class PartyCommand implements CommandExecutor, TabCompleter {

    private final PvPRoomsPro plugin;

    public PartyCommand(PvPRoomsPro plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, 
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cEste comando solo puede ser usado por jugadores.");
            return true;
        }

        if (args.length == 0) {
            showHelp(player);
            return true;
        }

        String sub = args[0].toLowerCase();
        var pm = plugin.getPartyManager();

        switch (sub) {
            case "create", "crear" -> {
                pm.createParty(player);
            }

            case "invite", "invitar", "inv" -> {
                if (args.length < 2) {
                    player.sendMessage(plugin.prefix() + "§cUso: /party invite <jugador>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    player.sendMessage(plugin.prefix() + "§cJugador no encontrado.");
                    return true;
                }
                if (target.equals(player)) {
                    player.sendMessage(plugin.prefix() + "§cNo puedes invitarte a ti mismo.");
                    return true;
                }
                pm.invitePlayer(player, target);
            }

            case "accept", "aceptar" -> {
                pm.acceptInvite(player);
            }

            case "deny", "rechazar", "decline" -> {
                pm.denyInvite(player);
            }

            case "leave", "salir" -> {
                pm.leaveParty(player);
            }

            case "kick", "expulsar" -> {
                if (args.length < 2) {
                    player.sendMessage(plugin.prefix() + "§cUso: /party kick <jugador>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    player.sendMessage(plugin.prefix() + "§cJugador no encontrado.");
                    return true;
                }
                pm.kickPlayer(player, target);
            }

            case "disband", "disolver" -> {
                pm.disbandParty(player);
            }

            case "list", "lista", "info" -> {
                showPartyInfo(player);
            }

            default -> showHelp(player);
        }

        return true;
    }

    private void showHelp(Player player) {
        player.sendMessage("");
        player.sendMessage("§d§l⚔ Comandos de Party §d§l⚔");
        player.sendMessage("");
        player.sendMessage("§e/party create §7- Crear una party");
        player.sendMessage("§e/party invite <jugador> §7- Invitar jugador");
        player.sendMessage("§e/party accept §7- Aceptar invitación");
        player.sendMessage("§e/party deny §7- Rechazar invitación");
        player.sendMessage("§e/party leave §7- Abandonar party");
        player.sendMessage("§e/party kick <jugador> §7- Expulsar jugador");
        player.sendMessage("§e/party disband §7- Disolver party");
        player.sendMessage("§e/party list §7- Ver miembros");
        player.sendMessage("");
        player.sendMessage("§7TIP: §eShift + Click derecho §7sobre un jugador para invitarlo.");
        player.sendMessage("");
    }

    private void showPartyInfo(Player player) {
        var pm = plugin.getPartyManager();
        UUID uuid = player.getUniqueId();

        if (!pm.isInParty(uuid)) {
            player.sendMessage(plugin.prefix() + "§cNo estás en ninguna party.");
            return;
        }

        UUID leaderUUID = pm.getPartyLeader(uuid);
        var members = pm.getPartyMembers(leaderUUID);
        boolean isLeader = pm.isPartyLeader(uuid);

        player.sendMessage("");
        player.sendMessage("§d§l♦ Tu Party §7(" + members.size() + " miembros)");
        player.sendMessage("");

        for (UUID memberUUID : members) {
            Player member = Bukkit.getPlayer(memberUUID);
            String name = member != null ? member.getName() : "???";
            String role = memberUUID.equals(leaderUUID) ? " §6★ Líder" : "";
            String status = member != null ? "§a●" : "§c●";
            player.sendMessage("§7  " + status + " §f" + name + role);
        }

        player.sendMessage("");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                       @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return List.of();

        if (args.length == 1) {
            List<String> subs = Arrays.asList("create", "invite", "accept", "deny", "leave", "kick", "disband", "list");
            return subs.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("invite") || sub.equals("kick")) {
                return Bukkit.getOnlinePlayers().stream()
                        .filter(p -> !p.equals(player))
                        .map(Player::getName)
                        .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        return List.of();
    }
}

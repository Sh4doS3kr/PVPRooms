package com.pvprooms.commands;

import com.pvprooms.PvPRoomsPro;
import com.pvprooms.model.Duel;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Handles the /spectate <player> command.
 * Allows a player to spectate an active duel.
 * Spectators are made invisible to combatants and receive SPECTATOR gamemode.
 */
public class SpectateCommand implements CommandExecutor, TabCompleter {

    private final PvPRoomsPro plugin;

    /** Simple cooldown map: spectator UUID → last use time */
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public SpectateCommand(PvPRoomsPro plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("pvprooms.spectate")) {
            sender.sendMessage(plugin.prefix() + "§cYou do not have permission to use this command.");
            return true;
        }
        if (!(sender instanceof Player spectator)) {
            sender.sendMessage(plugin.prefix() + "§cThis command must be run by a player.");
            return true;
        }

        if (args.length == 0) {
            spectator.sendMessage(plugin.prefix() + "§cUsage: /spectate <player>");
            return true;
        }

        // Cooldown check
        long cooldownMs = plugin.getConfig().getLong("cooldowns.spectate", 2) * 1000L;
        long lastUse = cooldowns.getOrDefault(spectator.getUniqueId(), 0L);
        if (System.currentTimeMillis() - lastUse < cooldownMs) {
            spectator.sendMessage(plugin.prefix() + "§cPlease wait before spectating again.");
            return true;
        }

        // Cannot spectate if in queue or duel
        if (plugin.getQueueManager().isInQueue(spectator.getUniqueId())) {
            spectator.sendMessage(plugin.prefix() + "§cYou cannot spectate while in a queue.");
            return true;
        }
        if (plugin.getDuelManager().isInDuel(spectator.getUniqueId())) {
            spectator.sendMessage(plugin.prefix() + "§cYou cannot spectate while in a duel.");
            return true;
        }

        // Find target player
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            spectator.sendMessage(plugin.prefix() + "§cPlayer §e" + args[0] + " §cis not online.");
            return true;
        }
        if (target.equals(spectator)) {
            spectator.sendMessage(plugin.prefix() + "§cYou cannot spectate yourself.");
            return true;
        }

        // Check target is in a duel
        Duel duel = plugin.getDuelManager().getDuelByPlayer(target.getUniqueId());
        if (duel == null || duel.getState() != Duel.State.FIGHTING) {
            spectator.sendMessage(plugin.prefix() + "§e" + target.getName() + " §cis not currently in an active duel.");
            return true;
        }

        // Add spectator
        boolean success = plugin.getDuelManager().addSpectator(spectator, target);
        if (success) {
            cooldowns.put(spectator.getUniqueId(), System.currentTimeMillis());
            spectator.sendMessage(plugin.prefix() + "§aYou are now spectating §e" + target.getName() + "'s §aduel. Use §f/pvpleave §ato stop spectating.");
            target.sendMessage(plugin.prefix() + "§7" + spectator.getName() + " §7is now spectating your match.");
        } else {
            spectator.sendMessage(plugin.prefix() + "§cCould not spectate §e" + target.getName() + "§c's match.");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("pvprooms.spectate")) return List.of();
        if (args.length == 1) {
            // Suggest players who are currently in a duel
            return Bukkit.getOnlinePlayers().stream()
                    .filter(p -> plugin.getDuelManager().isInDuel(p.getUniqueId()))
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}

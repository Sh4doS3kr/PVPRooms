package com.pvprooms.commands;

import com.pvprooms.PvPRoomsPro;
import com.pvprooms.gui.KitGUI;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Handles /duel <player> command to challenge another player to a duel.
 */
public class DuelCommand implements CommandExecutor, TabCompleter {

    private final PvPRoomsPro plugin;
    
    /** Pending duel requests: challenged UUID -> challenger UUID */
    private final Map<UUID, UUID> pendingRequests = new HashMap<>();
    /** Request timestamps for expiration */
    private final Map<UUID, Long> requestTimestamps = new HashMap<>();
    
    private static final long REQUEST_TIMEOUT_MS = 60_000; // 60 seconds

    public DuelCommand(PvPRoomsPro plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cSolo jugadores pueden usar este comando.");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(plugin.prefix() + "§eUso: §f/duel <jugador> §7- Retar a un jugador");
            player.sendMessage(plugin.prefix() + "§eUso: §f/duel accept §7- Aceptar un reto pendiente");
            player.sendMessage(plugin.prefix() + "§eUso: §f/duel deny §7- Rechazar un reto pendiente");
            return true;
        }

        String sub = args[0].toLowerCase();

        // Check if player is already in a duel or queue
        if (plugin.getDuelManager().isInDuel(player.getUniqueId())) {
            player.sendMessage(plugin.prefix() + "§cYa estás en un duelo.");
            return true;
        }
        if (plugin.getQueueManager().isInQueue(player.getUniqueId())) {
            player.sendMessage(plugin.prefix() + "§cYa estás en una cola. Usa §e/pvpleave §cprimero.");
            return true;
        }

        switch (sub) {
            case "accept", "aceptar" -> handleAccept(player);
            case "deny", "rechazar", "decline" -> handleDeny(player);
            default -> handleChallenge(player, sub);
        }

        return true;
    }

    private void handleChallenge(Player challenger, String targetName) {
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            // Try partial match
            target = Bukkit.getPlayer(targetName);
        }

        if (target == null) {
            challenger.sendMessage(plugin.prefix() + "§cJugador §e" + targetName + " §cno encontrado.");
            return;
        }

        if (target.equals(challenger)) {
            challenger.sendMessage(plugin.prefix() + "§cNo puedes retarte a ti mismo.");
            return;
        }

        if (plugin.getDuelManager().isInDuel(target.getUniqueId())) {
            challenger.sendMessage(plugin.prefix() + "§c" + target.getName() + " §cya está en un duelo.");
            return;
        }

        if (plugin.getQueueManager().isInQueue(target.getUniqueId())) {
            challenger.sendMessage(plugin.prefix() + "§c" + target.getName() + " §cestá en una cola.");
            return;
        }

        // Check if there's already a pending request from target to challenger
        UUID existingChallenger = pendingRequests.get(challenger.getUniqueId());
        if (existingChallenger != null && existingChallenger.equals(target.getUniqueId())) {
            // Target already challenged us - auto accept
            cleanupRequest(challenger.getUniqueId());
            startKitSelection(challenger, target);
            return;
        }

        // Clean expired requests
        cleanExpiredRequests();

        // Check if we already have a pending request to this player
        if (pendingRequests.containsKey(target.getUniqueId()) 
                && pendingRequests.get(target.getUniqueId()).equals(challenger.getUniqueId())) {
            challenger.sendMessage(plugin.prefix() + "§eYa has retado a §f" + target.getName() + "§e. Espera su respuesta.");
            return;
        }

        // Send challenge
        pendingRequests.put(target.getUniqueId(), challenger.getUniqueId());
        requestTimestamps.put(target.getUniqueId(), System.currentTimeMillis());

        challenger.sendMessage(plugin.prefix() + "§a¡Reto enviado a §f" + target.getName() + "§a!");
        challenger.sendMessage(plugin.prefix() + "§7Esperando respuesta... (60s)");

        target.sendMessage("");
        target.sendMessage(plugin.prefix() + "§e§l¡RETO DE DUELO!");
        target.sendMessage(plugin.prefix() + "§f" + challenger.getName() + " §ete ha retado a un duelo.");
        target.sendMessage(plugin.prefix() + "§aEscribe §f/duel accept §apara aceptar");
        target.sendMessage(plugin.prefix() + "§cEscribe §f/duel deny §cpara rechazar");
        target.sendMessage("");

        // Auto-expire after timeout
        final UUID targetUUID = target.getUniqueId();
        final UUID challengerUUID = challenger.getUniqueId();
        final String tgtName = target.getName();
        final String chName = challenger.getName();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (pendingRequests.containsKey(targetUUID) 
                    && pendingRequests.get(targetUUID).equals(challengerUUID)) {
                cleanupRequest(targetUUID);
                Player ch = Bukkit.getPlayer(challengerUUID);
                Player tg = Bukkit.getPlayer(targetUUID);
                if (ch != null) ch.sendMessage(plugin.prefix() + "§cTu reto a §e" + tgtName + " §cha expirado.");
                if (tg != null) tg.sendMessage(plugin.prefix() + "§cEl reto de §e" + chName + " §cha expirado.");
            }
        }, REQUEST_TIMEOUT_MS / 50); // Convert to ticks
    }

    private void handleAccept(Player player) {
        UUID challengerUUID = pendingRequests.get(player.getUniqueId());
        if (challengerUUID == null) {
            player.sendMessage(plugin.prefix() + "§cNo tienes ningún reto pendiente.");
            return;
        }

        Player challenger = Bukkit.getPlayer(challengerUUID);
        if (challenger == null) {
            cleanupRequest(player.getUniqueId());
            player.sendMessage(plugin.prefix() + "§cEl retador ya no está conectado.");
            return;
        }

        // Check if challenger is still available
        if (plugin.getDuelManager().isInDuel(challengerUUID)) {
            cleanupRequest(player.getUniqueId());
            player.sendMessage(plugin.prefix() + "§c" + challenger.getName() + " §cya está en otro duelo.");
            return;
        }

        cleanupRequest(player.getUniqueId());
        startKitSelection(challenger, player);
    }

    private void handleDeny(Player player) {
        UUID challengerUUID = pendingRequests.get(player.getUniqueId());
        if (challengerUUID == null) {
            player.sendMessage(plugin.prefix() + "§cNo tienes ningún reto pendiente.");
            return;
        }

        Player challenger = Bukkit.getPlayer(challengerUUID);
        cleanupRequest(player.getUniqueId());

        player.sendMessage(plugin.prefix() + "§cHas rechazado el reto.");
        if (challenger != null) {
            challenger.sendMessage(plugin.prefix() + "§c" + player.getName() + " §cha rechazado tu reto.");
        }
    }

    private void startKitSelection(Player challenger, Player target) {
        challenger.sendMessage(plugin.prefix() + "§a¡" + target.getName() + " §aha aceptado tu reto!");
        target.sendMessage(plugin.prefix() + "§a¡Has aceptado el reto de §f" + challenger.getName() + "§a!");

        // Store the duel pair temporarily
        plugin.getQueueManager().storeDuelPair(challenger.getUniqueId(), target.getUniqueId());

        // Open kit selection GUI for challenger (target will see it after challenger picks)
        challenger.sendMessage(plugin.prefix() + "§eElige un kit para el duelo:");
        plugin.getKitGUI().openDuelKitSelection(challenger, target.getUniqueId());
    }

    private void cleanupRequest(UUID targetUUID) {
        pendingRequests.remove(targetUUID);
        requestTimestamps.remove(targetUUID);
    }

    private void cleanExpiredRequests() {
        long now = System.currentTimeMillis();
        requestTimestamps.entrySet().removeIf(e -> {
            if (now - e.getValue() > REQUEST_TIMEOUT_MS) {
                pendingRequests.remove(e.getKey());
                return true;
            }
            return false;
        });
    }

    public boolean hasPendingRequest(UUID targetUUID) {
        cleanExpiredRequests();
        return pendingRequests.containsKey(targetUUID);
    }

    public UUID getChallenger(UUID targetUUID) {
        return pendingRequests.get(targetUUID);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            List<String> suggestions = new ArrayList<>();
            suggestions.add("accept");
            suggestions.add("deny");
            
            // Add online players
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.equals(sender) && p.getName().toLowerCase().startsWith(partial)) {
                    suggestions.add(p.getName());
                }
            }
            
            return suggestions.stream()
                    .filter(s -> s.toLowerCase().startsWith(partial))
                    .toList();
        }
        return Collections.emptyList();
    }
}

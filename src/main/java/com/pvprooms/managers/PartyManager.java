package com.pvprooms.managers;

import com.pvprooms.PvPRoomsPro;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages player parties for group queuing.
 */
public class PartyManager {

    private final PvPRoomsPro plugin;

    // Leader UUID -> Set of member UUIDs (includes leader)
    private final Map<UUID, Set<UUID>> parties = new ConcurrentHashMap<>();
    // Player UUID -> Leader UUID (for quick lookup)
    private final Map<UUID, UUID> playerParty = new ConcurrentHashMap<>();
    // Pending invites: Invitee UUID -> Inviter UUID
    private final Map<UUID, UUID> pendingInvites = new ConcurrentHashMap<>();
    // Invite expiry times
    private final Map<UUID, Long> inviteExpiry = new ConcurrentHashMap<>();

    private static final long INVITE_TIMEOUT_MS = 60_000; // 60 seconds

    public PartyManager(PvPRoomsPro plugin) {
        this.plugin = plugin;
        // Cleanup task for expired invites
        Bukkit.getScheduler().runTaskTimer(plugin, this::cleanupExpiredInvites, 20L * 30, 20L * 30);
    }

    // ── Party Creation ─────────────────────────────────────────────────────

    /**
     * Create a new party with the player as leader.
     */
    public boolean createParty(Player leader) {
        UUID leaderUUID = leader.getUniqueId();
        if (isInParty(leaderUUID)) {
            leader.sendMessage(plugin.prefix() + "§cYa estás en una party. Sal primero con §e/party leave§c.");
            return false;
        }
        Set<UUID> members = ConcurrentHashMap.newKeySet();
        members.add(leaderUUID);
        parties.put(leaderUUID, members);
        playerParty.put(leaderUUID, leaderUUID);
        leader.sendMessage(plugin.prefix() + "§a¡Party creada! Invita jugadores con §e/party invite <jugador>§a.");
        return true;
    }

    // ── Invitations ────────────────────────────────────────────────────────

    /**
     * Invite a player to the party.
     */
    public boolean invitePlayer(Player inviter, Player target) {
        UUID inviterUUID = inviter.getUniqueId();
        UUID targetUUID = target.getUniqueId();

        // Check if inviter is in a party
        if (!isInParty(inviterUUID)) {
            // Auto-create party
            createParty(inviter);
        }

        // Check if inviter is the leader
        UUID leaderUUID = getPartyLeader(inviterUUID);
        if (!leaderUUID.equals(inviterUUID)) {
            inviter.sendMessage(plugin.prefix() + "§cSolo el líder de la party puede invitar jugadores.");
            return false;
        }

        // Check if target is already in a party
        if (isInParty(targetUUID)) {
            inviter.sendMessage(plugin.prefix() + "§c" + target.getName() + " ya está en una party.");
            return false;
        }

        // Check if already invited
        if (pendingInvites.containsKey(targetUUID) && pendingInvites.get(targetUUID).equals(inviterUUID)) {
            inviter.sendMessage(plugin.prefix() + "§cYa has invitado a " + target.getName() + ".");
            return false;
        }

        // Send invitation
        pendingInvites.put(targetUUID, inviterUUID);
        inviteExpiry.put(targetUUID, System.currentTimeMillis() + INVITE_TIMEOUT_MS);

        inviter.sendMessage(plugin.prefix() + "§aInvitación enviada a §e" + target.getName() + "§a.");
        target.sendMessage("");
        target.sendMessage(plugin.prefix() + "§e" + inviter.getName() + " §ate ha invitado a su party.");
        target.sendMessage("§7  Escribe §a/party accept §7para aceptar o §c/party deny §7para rechazar.");
        target.sendMessage("§7  La invitación expira en 60 segundos.");
        target.sendMessage("");

        return true;
    }

    /**
     * Accept a pending invitation.
     */
    public boolean acceptInvite(Player player) {
        UUID playerUUID = player.getUniqueId();
        UUID inviterUUID = pendingInvites.remove(playerUUID);
        inviteExpiry.remove(playerUUID);

        if (inviterUUID == null) {
            player.sendMessage(plugin.prefix() + "§cNo tienes invitaciones pendientes.");
            return false;
        }

        // Check if party still exists
        if (!parties.containsKey(inviterUUID)) {
            player.sendMessage(plugin.prefix() + "§cLa party ya no existe.");
            return false;
        }

        // Add to party
        parties.get(inviterUUID).add(playerUUID);
        playerParty.put(playerUUID, inviterUUID);

        // Notify all members
        Player leader = Bukkit.getPlayer(inviterUUID);
        if (leader != null) {
            leader.sendMessage(plugin.prefix() + "§a" + player.getName() + " se ha unido a la party.");
        }
        for (UUID memberUUID : parties.get(inviterUUID)) {
            if (!memberUUID.equals(inviterUUID) && !memberUUID.equals(playerUUID)) {
                Player member = Bukkit.getPlayer(memberUUID);
                if (member != null) {
                    member.sendMessage(plugin.prefix() + "§a" + player.getName() + " se ha unido a la party.");
                }
            }
        }
        player.sendMessage(plugin.prefix() + "§a¡Te has unido a la party de §e" + 
                (leader != null ? leader.getName() : "???") + "§a!");

        return true;
    }

    /**
     * Deny a pending invitation.
     */
    public boolean denyInvite(Player player) {
        UUID playerUUID = player.getUniqueId();
        UUID inviterUUID = pendingInvites.remove(playerUUID);
        inviteExpiry.remove(playerUUID);

        if (inviterUUID == null) {
            player.sendMessage(plugin.prefix() + "§cNo tienes invitaciones pendientes.");
            return false;
        }

        player.sendMessage(plugin.prefix() + "§cInvitación rechazada.");
        Player inviter = Bukkit.getPlayer(inviterUUID);
        if (inviter != null) {
            inviter.sendMessage(plugin.prefix() + "§c" + player.getName() + " ha rechazado tu invitación.");
        }
        return true;
    }

    // ── Leave / Disband ────────────────────────────────────────────────────

    /**
     * Leave the current party.
     */
    public boolean leaveParty(Player player) {
        UUID playerUUID = player.getUniqueId();
        UUID leaderUUID = playerParty.remove(playerUUID);

        if (leaderUUID == null) {
            player.sendMessage(plugin.prefix() + "§cNo estás en ninguna party.");
            return false;
        }

        Set<UUID> members = parties.get(leaderUUID);
        if (members == null) return false;

        // If player is the leader, disband the party
        if (leaderUUID.equals(playerUUID)) {
            disbandParty(player);
            return true;
        }

        // Remove from party
        members.remove(playerUUID);
        player.sendMessage(plugin.prefix() + "§cHas abandonado la party.");

        // Notify leader
        Player leader = Bukkit.getPlayer(leaderUUID);
        if (leader != null) {
            leader.sendMessage(plugin.prefix() + "§c" + player.getName() + " ha abandonado la party.");
        }

        return true;
    }

    /**
     * Disband the party (leader only).
     */
    public boolean disbandParty(Player leader) {
        UUID leaderUUID = leader.getUniqueId();

        if (!parties.containsKey(leaderUUID)) {
            leader.sendMessage(plugin.prefix() + "§cNo tienes una party.");
            return false;
        }

        Set<UUID> members = parties.remove(leaderUUID);
        for (UUID memberUUID : members) {
            playerParty.remove(memberUUID);
            Player member = Bukkit.getPlayer(memberUUID);
            if (member != null && !memberUUID.equals(leaderUUID)) {
                member.sendMessage(plugin.prefix() + "§cLa party ha sido disuelta por el líder.");
            }
        }
        leader.sendMessage(plugin.prefix() + "§cHas disuelto la party.");
        return true;
    }

    /**
     * Kick a player from the party (leader only).
     */
    public boolean kickPlayer(Player leader, Player target) {
        UUID leaderUUID = leader.getUniqueId();
        UUID targetUUID = target.getUniqueId();

        if (!parties.containsKey(leaderUUID)) {
            leader.sendMessage(plugin.prefix() + "§cNo tienes una party.");
            return false;
        }

        Set<UUID> members = parties.get(leaderUUID);
        if (!members.contains(targetUUID)) {
            leader.sendMessage(plugin.prefix() + "§c" + target.getName() + " no está en tu party.");
            return false;
        }

        members.remove(targetUUID);
        playerParty.remove(targetUUID);

        leader.sendMessage(plugin.prefix() + "§cHas expulsado a " + target.getName() + " de la party.");
        target.sendMessage(plugin.prefix() + "§cHas sido expulsado de la party.");
        return true;
    }

    // ── Queries ────────────────────────────────────────────────────────────

    public boolean isInParty(UUID playerUUID) {
        return playerParty.containsKey(playerUUID);
    }

    public UUID getPartyLeader(UUID playerUUID) {
        return playerParty.get(playerUUID);
    }

    public Set<UUID> getPartyMembers(UUID leaderUUID) {
        return parties.getOrDefault(leaderUUID, Collections.emptySet());
    }

    public int getPartySize(UUID playerUUID) {
        UUID leader = getPartyLeader(playerUUID);
        if (leader == null) return 0;
        Set<UUID> members = parties.get(leader);
        return members != null ? members.size() : 0;
    }

    public boolean isPartyLeader(UUID playerUUID) {
        return parties.containsKey(playerUUID);
    }

    public boolean hasPendingInvite(UUID playerUUID) {
        return pendingInvites.containsKey(playerUUID);
    }

    // ── Cleanup ────────────────────────────────────────────────────────────

    private void cleanupExpiredInvites() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Long>> it = inviteExpiry.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
            if (entry.getValue() < now) {
                UUID playerUUID = entry.getKey();
                pendingInvites.remove(playerUUID);
                it.remove();
                Player player = Bukkit.getPlayer(playerUUID);
                if (player != null) {
                    player.sendMessage(plugin.prefix() + "§7La invitación a la party ha expirado.");
                }
            }
        }
    }

    /**
     * Clean up when player disconnects.
     */
    public void handleDisconnect(UUID playerUUID) {
        pendingInvites.remove(playerUUID);
        inviteExpiry.remove(playerUUID);

        UUID leaderUUID = playerParty.remove(playerUUID);
        if (leaderUUID == null) return;

        if (leaderUUID.equals(playerUUID)) {
            // Leader disconnected, disband party
            Set<UUID> members = parties.remove(leaderUUID);
            if (members != null) {
                for (UUID memberUUID : members) {
                    if (!memberUUID.equals(playerUUID)) {
                        playerParty.remove(memberUUID);
                        Player member = Bukkit.getPlayer(memberUUID);
                        if (member != null) {
                            member.sendMessage(plugin.prefix() + "§cEl líder de la party se ha desconectado. Party disuelta.");
                        }
                    }
                }
            }
        } else {
            // Member disconnected
            Set<UUID> members = parties.get(leaderUUID);
            if (members != null) {
                members.remove(playerUUID);
            }
        }
    }
}

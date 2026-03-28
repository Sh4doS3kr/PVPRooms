package com.pvprooms.listeners;

import com.pvprooms.PvPRoomsPro;
import com.pvprooms.weapons.SpearItem;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Implements the "attribute swap" mechanic for the Spear weapon on Paper servers.
 *
 * Problem: On Paper 1.9+ the server-side attack cooldown is NOT reset when
 * a player switches items, so the classic "swap to reset cooldown" technique
 * used in Minecraft PvP does not work out of the box.
 *
 * Solution: Detect the swap manually via PlayerItemHeldEvent.
 *   1. Player switches AWAY from spear → record timestamp.
 *   2. Player switches BACK to spear within SWAP_WINDOW_MS → mark as "swap-ready".
 *   3. On the very next EntityDamageByEntityEvent with the spear → override damage
 *      to the item's full attribute value (ignoring partial cooldown reduction).
 */
public class SpearListener implements Listener {

    /** Maximum milliseconds between switching away and back to count as an attribute swap. */
    private static final long SWAP_WINDOW_MS = 500L;

    private final PvPRoomsPro plugin;

    /** UUID → time (ms) when the player last switched AWAY from their spear. */
    private final Map<UUID, Long> leftSpearAt = new HashMap<>();

    /** Players whose next spear hit should deal full (un-penalised) damage. */
    private final Set<UUID> swapReady = new HashSet<>();

    public SpearListener(PvPRoomsPro plugin) {
        this.plugin = plugin;
    }

    // ── Item switch detection ─────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemSwitch(PlayerItemHeldEvent event) {
        Player player   = event.getPlayer();
        UUID   uuid     = player.getUniqueId();

        ItemStack prev  = player.getInventory().getItem(event.getPreviousSlot());
        ItemStack next  = player.getInventory().getItem(event.getNewSlot());

        boolean prevSpear = SpearItem.isSpear(prev);
        boolean nextSpear = SpearItem.isSpear(next);

        if (prevSpear && !nextSpear) {
            // Switched AWAY from spear — start the swap window
            leftSpearAt.put(uuid, System.currentTimeMillis());

        } else if (!prevSpear && nextSpear) {
            // Switched BACK to spear — check if it was within the swap window
            Long left = leftSpearAt.remove(uuid);
            if (left != null && System.currentTimeMillis() - left <= SWAP_WINDOW_MS) {
                swapReady.add(uuid);
            }
        }
    }

    // ── Combat: force full damage on swap hit ─────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpearHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;

        ItemStack held = attacker.getInventory().getItemInMainHand();
        if (!SpearItem.isSpear(held)) return;

        UUID uuid = attacker.getUniqueId();

        if (swapReady.remove(uuid)) {
            // Attribute-swap hit: bypass the cooldown damage penalty.
            // Paper multiplies damage by getAttackCooldown() (0.0–1.0), so we
            // set the raw damage here to the full spear value; vanilla will still
            // apply armour reduction and enchantment bonuses correctly.
            double fullDmg = SpearItem.getAttackDamage(held);
            event.setDamage(fullDmg);
        }
        // Non-swap hit: let Paper's vanilla cooldown formula handle partial damage.
    }
}

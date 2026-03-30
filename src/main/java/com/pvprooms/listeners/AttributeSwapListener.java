package com.pvprooms.listeners;

import com.pvprooms.PvPRoomsPro;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Generic Attribute Swapping support for ALL weapons.
 * 
 * Attribute swapping is a competitive PvP technique where players:
 * 1. Hit with one weapon (e.g., sword for base damage)
 * 2. Quickly swap to another weapon (e.g., mace with Breach)
 * 3. The game applies attributes from BOTH weapons due to tick timing
 * 
 * This listener ensures the mechanic works properly by:
 * - Tracking weapon swaps within a tight window (50ms = ~1 tick)
 * - Applying enchantment effects from the swapped-to weapon
 * - Running at LOWEST priority to execute FIRST and not be blocked
 * 
 * Common combos:
 * - Sword → Mace (Breach): Sword damage + armor penetration
 * - Axe → Mace (Breach): Shield break + armor penetration  
 * - Any → Mace (Wind Burst): Apply wind burst after hit
 */
public class AttributeSwapListener implements Listener {

    /** 
     * Maximum milliseconds for a valid attribute swap.
     * ~50ms = 1 game tick, allows for human reaction time variance.
     */
    private static final long SWAP_WINDOW_MS = 100L;

    /** Weapons that can be used for attribute swapping. */
    private static final Set<Material> SWAP_WEAPONS = EnumSet.of(
        // Swords
        Material.NETHERITE_SWORD, Material.DIAMOND_SWORD, Material.IRON_SWORD, Material.STONE_SWORD, Material.WOODEN_SWORD, Material.GOLDEN_SWORD,
        // Axes
        Material.NETHERITE_AXE, Material.DIAMOND_AXE, Material.IRON_AXE, Material.STONE_AXE, Material.WOODEN_AXE, Material.GOLDEN_AXE,
        // Spears (1.21.11)
        Material.NETHERITE_SPEAR, Material.DIAMOND_SPEAR, Material.IRON_SPEAR, Material.STONE_SPEAR, Material.WOODEN_SPEAR, Material.GOLDEN_SPEAR, Material.COPPER_SPEAR,
        // Other weapons
        Material.MACE, Material.TRIDENT
    );

    /**
     * Checks if an item is a valid swap weapon.
     */
    private boolean isSwapWeapon(ItemStack item) {
        if (item == null) return false;
        return SWAP_WEAPONS.contains(item.getType());
    }

    /** Enchantments that transfer on attribute swap (offensive enchants). */
    private static final Set<Enchantment> TRANSFERABLE_ENCHANTS = Set.of(
        Enchantment.BREACH,        // Armor penetration
        Enchantment.DENSITY,       // Bonus damage on fall
        Enchantment.SHARPNESS,     // Bonus damage
        Enchantment.SMITE,         // Bonus vs undead
        Enchantment.BANE_OF_ARTHROPODS, // Bonus vs arthropods
        Enchantment.FIRE_ASPECT,   // Set on fire
        Enchantment.KNOCKBACK,     // Extra knockback
        Enchantment.LUNGE          // Spear dash (1.21.11)
    );

    private final PvPRoomsPro plugin;

    /** UUID → SwapData tracking the last weapon switch. */
    private final Map<UUID, SwapData> swapTracking = new HashMap<>();

    /** Players who performed a valid attribute swap and should get bonus effects. */
    private final Map<UUID, ItemStack> pendingSwapEffects = new HashMap<>();

    public AttributeSwapListener(PvPRoomsPro plugin) {
        this.plugin = plugin;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // HOTBAR SWAP DETECTION - LOWEST priority = runs FIRST
    // ═══════════════════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.LOWEST)
    public void onHotbarSwitch(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        ItemStack prevItem = player.getInventory().getItem(event.getPreviousSlot());
        ItemStack newItem = player.getInventory().getItem(event.getNewSlot());

        // Track swap if both are valid weapons (including Spear)
        if (isSwapWeapon(prevItem) && isSwapWeapon(newItem)) {
            long now = System.currentTimeMillis();
            SwapData existing = swapTracking.get(uuid);

            if (existing != null && (now - existing.timestamp) <= SWAP_WINDOW_MS) {
                // Double swap within window - mark for attribute transfer
                pendingSwapEffects.put(uuid, newItem);
            }

            // Always track the swap
            swapTracking.put(uuid, new SwapData(prevItem, newItem, now));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onOffhandSwap(PlayerSwapHandItemsEvent event) {
        // Also track offhand swaps for totem swapping etc.
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        
        ItemStack mainHand = event.getMainHandItem();
        ItemStack offHand = event.getOffHandItem();

        if (isSwapWeapon(mainHand)) {
            swapTracking.put(uuid, new SwapData(offHand, mainHand, System.currentTimeMillis()));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // DAMAGE APPLICATION - LOWEST priority = runs FIRST, before other plugins
    // ═══════════════════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof LivingEntity victim)) return;

        UUID uuid = attacker.getUniqueId();
        SwapData swapData = swapTracking.get(uuid);

        if (swapData == null) return;

        long timeSinceSwap = System.currentTimeMillis() - swapData.timestamp;

        // Valid attribute swap: hit registered within 1 tick of swapping
        if (timeSinceSwap <= SWAP_WINDOW_MS) {
            ItemStack currentWeapon = attacker.getInventory().getItemInMainHand();
            ItemStack swappedFrom = swapData.fromItem;

            // Apply enchantment effects from the NEW weapon to the damage
            applySwapEnchantments(event, currentWeapon, victim, attacker);

            // Clear the swap tracking after use
            swapTracking.remove(uuid);
            pendingSwapEffects.remove(uuid);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ENCHANTMENT TRANSFER LOGIC
    // ═══════════════════════════════════════════════════════════════════════

    private void applySwapEnchantments(EntityDamageByEntityEvent event, ItemStack weapon, 
                                        LivingEntity victim, Player attacker) {
        if (weapon == null || !weapon.hasItemMeta()) return;

        ItemMeta meta = weapon.getItemMeta();
        double bonusDamage = 0;

        // Breach: Reduces armor effectiveness (we simulate by adding damage)
        int breach = meta.getEnchantLevel(Enchantment.BREACH);
        if (breach > 0) {
            // Breach 4 = ~60% armor penetration, simulate as bonus damage
            double armorPen = breach * 0.15; // 15% per level
            bonusDamage += event.getDamage() * armorPen;
        }

        // Density: Bonus damage (normally on fall, but allow on swap)
        int density = meta.getEnchantLevel(Enchantment.DENSITY);
        if (density > 0) {
            bonusDamage += density * 0.5; // 0.5 hearts per level
        }

        // Sharpness: Extra flat damage
        int sharp = meta.getEnchantLevel(Enchantment.SHARPNESS);
        if (sharp > 0) {
            bonusDamage += 0.5 + (sharp * 0.5); // 0.5 + 0.5 per level
        }

        // Fire Aspect: Set target on fire
        int fireAspect = meta.getEnchantLevel(Enchantment.FIRE_ASPECT);
        if (fireAspect > 0) {
            victim.setFireTicks(fireAspect * 80); // 4 seconds per level
        }

        // Lunge (Spear exclusive): Dash/boost toward target
        int lunge = meta.getEnchantLevel(Enchantment.LUNGE);
        if (lunge > 0) {
            // Calculate direction toward victim and apply velocity boost
            org.bukkit.util.Vector direction = victim.getLocation().toVector()
                    .subtract(attacker.getLocation().toVector()).normalize();
            double lungeStrength = 0.4 + (lunge * 0.2); // 0.6, 0.8, 1.0 for levels 1-3
            direction.setY(0.15); // Slight upward boost
            attacker.setVelocity(direction.multiply(lungeStrength));
            // Small bonus damage for landing the lunge
            bonusDamage += lunge * 0.5;
        }

        // Knockback: Extra knockback to victim
        int knockback = meta.getEnchantLevel(Enchantment.KNOCKBACK);
        if (knockback > 0) {
            org.bukkit.util.Vector kb = attacker.getLocation().getDirection().normalize();
            kb.setY(0.2);
            victim.setVelocity(victim.getVelocity().add(kb.multiply(knockback * 0.4)));
        }

        // Apply bonus damage
        if (bonusDamage > 0) {
            event.setDamage(event.getDamage() + bonusDamage);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // HELPER CLASS
    // ═══════════════════════════════════════════════════════════════════════

    private record SwapData(ItemStack fromItem, ItemStack toItem, long timestamp) {}
}

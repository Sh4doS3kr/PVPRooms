package com.pvprooms.weapons;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Collection;
import java.util.List;

/**
 * Utility class for the custom "Spear" weapon.
 *
 * Uses Paper 1.21-compatible attribute modifiers (NamespacedKey + EquipmentSlotGroup).
 * The spear is a slow but high-damage melee weapon. Its "attribute swap" mechanic
 * (full damage on a quick item-switch hit) is handled by SpearListener.
 *
 * Default stats:
 *   Attack damage: +7  (= 8 total, 4 hearts)
 *   Attack speed:  -2.8 (= 1.2 swings/s — very slow)
 */
public class SpearItem {

    /** PersistentDataContainer key used to tag spear items. */
    private static NamespacedKey SPEAR_TAG;

    /** Call once during plugin enable. */
    public static void init(Plugin plugin) {
        SPEAR_TAG = new NamespacedKey(plugin, "pvp_spear");
    }

    // ── Factory ───────────────────────────────────────────────────────────

    /**
     * Creates a ready-to-use Spear item with Paper-compatible attribute modifiers.
     *
     * @param plugin      owning plugin (for NamespacedKey)
     * @param damage      bonus attack damage (added on top of the 1.0 base = total hearts × 2 − 1)
     * @param speedBonus  attack speed bonus (negative = slower; vanilla base is 4.0)
     */
    public static ItemStack create(Plugin plugin, double damage, double speedBonus) {
        ItemStack item = new ItemStack(Material.IRON_HOE);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§c§lLanza");
        meta.setLore(List.of(
                "§7Arma pesada de largo alcance.",
                "",
                "§fDaño:    §c" + (int) (damage + 1) + " ❤",
                "§fVelocidad: §eLenta",
                "",
                "§7Haz un §fattribute swap §7para",
                "§7lanzar con daño completo."
        ));

        // Tag the item so SpearListener can identify it
        meta.getPersistentDataContainer().set(SPEAR_TAG, PersistentDataType.BYTE, (byte) 1);

        // ── Attack damage ── Paper 1.21: NamespacedKey + EquipmentSlotGroup
        meta.addAttributeModifier(
                Attribute.ATTACK_DAMAGE,
                new AttributeModifier(
                        new NamespacedKey(plugin, "spear_dmg"),
                        damage,
                        AttributeModifier.Operation.ADD_NUMBER,
                        EquipmentSlotGroup.MAINHAND
                )
        );

        // ── Attack speed ──
        meta.addAttributeModifier(
                Attribute.ATTACK_SPEED,
                new AttributeModifier(
                        new NamespacedKey(plugin, "spear_spd"),
                        speedBonus,
                        AttributeModifier.Operation.ADD_NUMBER,
                        EquipmentSlotGroup.MAINHAND
                )
        );

        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    /** Creates a spear with default stats (damage +7, speed −2.8). */
    public static ItemStack createDefault(Plugin plugin) {
        return create(plugin, 7.0, -2.8);
    }

    // ── Detection ─────────────────────────────────────────────────────────

    public static boolean isSpear(ItemStack item) {
        if (item == null || item.getType() == org.bukkit.Material.AIR || !item.hasItemMeta()) return false;
        if (SPEAR_TAG == null) return false;
        return item.getItemMeta().getPersistentDataContainer()
                .has(SPEAR_TAG, PersistentDataType.BYTE);
    }

    // ── Stat helpers ─────────────────────────────────────────────────────

    /**
     * Returns the total attack damage of a spear item
     * (sum of ADD_NUMBER modifiers on MAINHAND + the 1.0 base).
     */
    public static double getAttackDamage(ItemStack item) {
        if (!isSpear(item)) return 1.0;
        Collection<AttributeModifier> mods =
                item.getItemMeta().getAttributeModifiers(Attribute.ATTACK_DAMAGE);
        if (mods == null) return 1.0;
        double bonus = mods.stream().mapToDouble(AttributeModifier::getAmount).sum();
        return 1.0 + bonus;
    }
}

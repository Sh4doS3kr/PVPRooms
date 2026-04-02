package com.pvprooms.util;

import com.pvprooms.PvPRoomsPro;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.HashMap;
import java.util.Map;

/**
 * Official PvP Kit Presets based on standard competitive configurations.
 * Sources: MCTiers, Practice servers, competitive PvP standards.
 */
public class PresetKits {

    public record KitPreset(String name, String displayName, Material icon, ItemStack[] armor, ItemStack[] inventory) {}

    public static Map<String, KitPreset> getAllPresets() {
        Map<String, KitPreset> presets = new HashMap<>();
        presets.put("sword", createSwordKit());
        presets.put("axepvp", createAxeKit());
        presets.put("nethpot", createNethpotKit());
        presets.put("uhc", createUHCKit());
        presets.put("smp", createSMPKit());
        presets.put("crystal", createCrystalKit());
        presets.put("mace", createMaceKit());
        presets.put("spear", createSpearKit());
        presets.put("explosivo", createExplosivoKit());
        return presets;
    }

    // ══════════════════════════════════════════════════════════════════════
    // SWORD KIT - Classic 1.9+ PvP
    // Diamond armor (Prot 4/3, Unbreaking 3), Diamond sword (Unbreaking 3)
    // ══════════════════════════════════════════════════════════════════════
    private static KitPreset createSwordKit() {
        ItemStack[] armor = new ItemStack[4];
        
        // Boots - Protection 3, Unbreaking 3
        armor[0] = createItem(Material.DIAMOND_BOOTS, 
            Map.of(Enchantment.PROTECTION, 3, Enchantment.UNBREAKING, 3));
        // Leggings - Protection 3, Unbreaking 3
        armor[1] = createItem(Material.DIAMOND_LEGGINGS, 
            Map.of(Enchantment.PROTECTION, 3, Enchantment.UNBREAKING, 3));
        // Chestplate - Protection 4, Unbreaking 3
        armor[2] = createItem(Material.DIAMOND_CHESTPLATE, 
            Map.of(Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3));
        // Helmet - Protection 4, Unbreaking 3
        armor[3] = createItem(Material.DIAMOND_HELMET, 
            Map.of(Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3));

        ItemStack[] inventory = new ItemStack[36];
        // Slot 0: Diamond Sword - Unbreaking 3
        inventory[0] = createItem(Material.DIAMOND_SWORD, 
            Map.of(Enchantment.UNBREAKING, 3));

        return new KitPreset("sword", "§b⚔ Sword Kit", Material.DIAMOND_SWORD, armor, inventory);
    }

    // ══════════════════════════════════════════════════════════════════════
    // AXE KIT - 1.9+ Axe PvP (Vanilla, no enchants)
    // Diamond armor (no enchants), Sword, Axe, Bow, Crossbow, Shield
    // ══════════════════════════════════════════════════════════════════════
    private static KitPreset createAxeKit() {
        ItemStack[] armor = new ItemStack[4];
        armor[0] = new ItemStack(Material.DIAMOND_BOOTS);
        armor[1] = new ItemStack(Material.DIAMOND_LEGGINGS);
        armor[2] = new ItemStack(Material.DIAMOND_CHESTPLATE);
        armor[3] = new ItemStack(Material.DIAMOND_HELMET);

        ItemStack[] inventory = new ItemStack[36];
        inventory[0] = new ItemStack(Material.DIAMOND_SWORD);
        inventory[1] = new ItemStack(Material.DIAMOND_AXE);
        inventory[2] = new ItemStack(Material.BOW);
        inventory[3] = new ItemStack(Material.CROSSBOW);
        inventory[8] = new ItemStack(Material.ARROW, 6);

        // Offhand: Shield (slot 40 maps to offhand in applyKit)
        return new KitPreset("axepvp", "§6⚔ Axe Kit", Material.NETHERITE_AXE, armor, inventory);
    }

    // ══════════════════════════════════════════════════════════════════════
    // NETHPOT KIT - Netherite Pot PvP (Official PvP Legacy config)
    // Netherite armor (Prot 4, Unbreaking 3, Mending 1), Sharp 5 Sweeping 3 Sword
    // ══════════════════════════════════════════════════════════════════════
    private static KitPreset createNethpotKit() {
        ItemStack[] armor = new ItemStack[4];
        // Netherite armor - Protection 4, Unbreaking 3, Mending 1
        armor[0] = createItem(Material.NETHERITE_BOOTS, 
            Map.of(Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1));
        armor[1] = createItem(Material.NETHERITE_LEGGINGS, 
            Map.of(Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1));
        armor[2] = createItem(Material.NETHERITE_CHESTPLATE, 
            Map.of(Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1));
        armor[3] = createItem(Material.NETHERITE_HELMET, 
            Map.of(Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1));

        ItemStack[] inventory = new ItemStack[36];
        // Slot 0: Netherite Sword - Sharpness 5, Sweeping Edge 3, Unbreaking 3
        inventory[0] = createItem(Material.NETHERITE_SWORD, 
            Map.of(Enchantment.SHARPNESS, 5, Enchantment.SWEEPING_EDGE, 3, Enchantment.UNBREAKING, 3));
        
        // Slots 1-5: Splash Potions of Healing II
        for (int i = 1; i <= 5; i++) {
            inventory[i] = createSplashPotion(PotionType.STRONG_HEALING);
        }
        // Slot 6: Strength II
        inventory[6] = createSplashPotion(PotionType.STRONG_STRENGTH);
        // Slot 7: Speed II
        inventory[7] = createSplashPotion(PotionType.STRONG_SWIFTNESS);
        // Slot 8: Totem
        inventory[8] = new ItemStack(Material.TOTEM_OF_UNDYING);
        
        // Rest of inventory: More potions (healing, strength, speed)
        // Row 2: Healing x5, Strength x4
        for (int i = 9; i <= 13; i++) inventory[i] = createSplashPotion(PotionType.STRONG_HEALING);
        for (int i = 14; i <= 17; i++) inventory[i] = createSplashPotion(PotionType.STRONG_STRENGTH);
        
        // Row 3: Healing x5, Speed x4
        for (int i = 18; i <= 22; i++) inventory[i] = createSplashPotion(PotionType.STRONG_HEALING);
        for (int i = 23; i <= 26; i++) inventory[i] = createSplashPotion(PotionType.STRONG_SWIFTNESS);
        
        // Row 4: More healing + golden apples + totem
        for (int i = 27; i <= 31; i++) inventory[i] = createSplashPotion(PotionType.STRONG_HEALING);
        inventory[32] = new ItemStack(Material.GOLDEN_APPLE, 64);
        inventory[33] = new ItemStack(Material.GOLDEN_APPLE, 64);
        inventory[34] = new ItemStack(Material.EXPERIENCE_BOTTLE, 64);
        inventory[35] = new ItemStack(Material.TOTEM_OF_UNDYING);

        return new KitPreset("nethpot", "§d⚗ Nethpot Kit", Material.NETHERITE_SWORD, armor, inventory);
    }

    // ══════════════════════════════════════════════════════════════════════
    // UHC KIT - Ultra Hardcore
    // Diamond armor (Prot 2-3), Sharp 3 Sword, Eff 3 Axe, Golden Apples, Crossbow
    // ══════════════════════════════════════════════════════════════════════
    private static KitPreset createUHCKit() {
        ItemStack[] armor = new ItemStack[4];
        armor[0] = createItem(Material.DIAMOND_BOOTS, 
            Map.of(Enchantment.PROTECTION, 3));
        armor[1] = createItem(Material.DIAMOND_LEGGINGS, 
            Map.of(Enchantment.PROTECTION, 2));
        armor[2] = createItem(Material.DIAMOND_CHESTPLATE, 
            Map.of(Enchantment.PROTECTION, 2));
        armor[3] = createItem(Material.DIAMOND_HELMET, 
            Map.of(Enchantment.PROTECTION, 3));

        ItemStack[] inventory = new ItemStack[36];
        // Sword - Sharpness 3
        inventory[0] = createItem(Material.DIAMOND_SWORD, 
            Map.of(Enchantment.SHARPNESS, 3));
        // Axe - Efficiency 3
        inventory[1] = createItem(Material.DIAMOND_AXE, 
            Map.of(Enchantment.EFFICIENCY, 3));
        // Golden Apples
        inventory[2] = new ItemStack(Material.GOLDEN_APPLE, 8);
        // Golden Head placeholder (regular golden apple)
        inventory[3] = new ItemStack(Material.GOLDEN_APPLE, 2);
        // Blocks
        inventory[4] = new ItemStack(Material.OAK_PLANKS, 64);
        // Water bucket
        inventory[5] = new ItemStack(Material.WATER_BUCKET);
        // Lava bucket
        inventory[6] = new ItemStack(Material.LAVA_BUCKET);
        // Crossbow - Piercing 1
        inventory[7] = createItem(Material.CROSSBOW, 
            Map.of(Enchantment.PIERCING, 1));
        // Cobwebs
        inventory[8] = new ItemStack(Material.COBWEB, 8);
        // Arrows
        inventory[17] = new ItemStack(Material.ARROW, 10);
        // Bow - Power 1
        inventory[26] = createItem(Material.BOW, 
            Map.of(Enchantment.POWER, 1));
        // Pickaxe - Efficiency 3
        inventory[31] = createItem(Material.DIAMOND_PICKAXE, 
            Map.of(Enchantment.EFFICIENCY, 3));

        return new KitPreset("uhc", "§e⚔ UHC Kit", Material.GOLDEN_APPLE, armor, inventory);
    }

    // ══════════════════════════════════════════════════════════════════════
    // SMP KIT - Survival Multiplayer full gear
    // Netherite armor (full enchants), Netherite tools, Totems, Potions
    // ══════════════════════════════════════════════════════════════════════
    private static KitPreset createSMPKit() {
        ItemStack[] armor = new ItemStack[4];
        armor[0] = createItem(Material.NETHERITE_BOOTS, 
            Map.of(Enchantment.PROTECTION, 4, Enchantment.FEATHER_FALLING, 4, 
                   Enchantment.DEPTH_STRIDER, 3, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1));
        armor[1] = createItem(Material.NETHERITE_LEGGINGS, 
            Map.of(Enchantment.PROTECTION, 4, Enchantment.SWIFT_SNEAK, 3, 
                   Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1));
        armor[2] = createItem(Material.NETHERITE_CHESTPLATE, 
            Map.of(Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1));
        armor[3] = createItem(Material.NETHERITE_HELMET, 
            Map.of(Enchantment.PROTECTION, 4, Enchantment.RESPIRATION, 3, 
                   Enchantment.AQUA_AFFINITY, 1, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1));

        ItemStack[] inventory = new ItemStack[36];
        // Sword - Sharpness 5, Sweeping Edge 3, Fire Aspect 2, Knockback 1, Unbreaking 3
        inventory[0] = createItem(Material.NETHERITE_SWORD, 
            Map.of(Enchantment.SHARPNESS, 5, Enchantment.SWEEPING_EDGE, 3, 
                   Enchantment.FIRE_ASPECT, 2, Enchantment.KNOCKBACK, 1, Enchantment.UNBREAKING, 3));
        // Ender pearls
        inventory[1] = new ItemStack(Material.ENDER_PEARL, 16);
        // Golden apples
        inventory[2] = new ItemStack(Material.GOLDEN_APPLE, 64);
        // Axe - Sharpness 5, Efficiency 5, Unbreaking 3
        inventory[3] = createItem(Material.NETHERITE_AXE, 
            Map.of(Enchantment.SHARPNESS, 5, Enchantment.EFFICIENCY, 5, Enchantment.UNBREAKING, 3));
        // Experience bottles
        inventory[4] = new ItemStack(Material.EXPERIENCE_BOTTLE, 64);
        // Strength potion
        inventory[5] = createSplashPotion(PotionType.STRONG_STRENGTH);
        // Speed potion
        inventory[6] = createSplashPotion(PotionType.STRONG_SWIFTNESS);
        // Fire resistance
        inventory[7] = createSplashPotion(PotionType.LONG_FIRE_RESISTANCE);
        // Totem
        inventory[8] = new ItemStack(Material.TOTEM_OF_UNDYING);

        // More supplies in inventory
        for (int i = 9; i < 18; i++) {
            inventory[i] = createSplashPotion(i % 2 == 0 ? PotionType.STRONG_STRENGTH : PotionType.STRONG_SWIFTNESS);
        }

        return new KitPreset("smp", "§2⚔ SMP Kit", Material.CHEST, armor, inventory);
    }

    // ══════════════════════════════════════════════════════════════════════
    // CRYSTAL KIT - Crystal PvP
    // Netherite armor (Blast Prot legs/boots), Crystals, Obsidian, Anchors, Totems
    // ══════════════════════════════════════════════════════════════════════
    private static KitPreset createCrystalKit() {
        ItemStack[] armor = new ItemStack[4];
        // Boots - Blast Protection 4, Feather Falling 4, Mending, Unbreaking 3
        armor[0] = createItem(Material.NETHERITE_BOOTS, 
            Map.of(Enchantment.BLAST_PROTECTION, 4, Enchantment.FEATHER_FALLING, 4, 
                   Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1));
        // Leggings - Blast Protection 4, Mending, Unbreaking 3
        armor[1] = createItem(Material.NETHERITE_LEGGINGS, 
            Map.of(Enchantment.BLAST_PROTECTION, 4, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1));
        // Chestplate - Protection 4, Mending, Unbreaking 3
        armor[2] = createItem(Material.NETHERITE_CHESTPLATE, 
            Map.of(Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1));
        // Helmet - Protection 4, Mending, Unbreaking 3
        armor[3] = createItem(Material.NETHERITE_HELMET, 
            Map.of(Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1));

        ItemStack[] inventory = new ItemStack[36];
        // Sword - Sharpness 5, Knockback 1, Sweeping Edge 3, Mending, Unbreaking 3
        inventory[0] = createItem(Material.NETHERITE_SWORD, 
            Map.of(Enchantment.SHARPNESS, 5, Enchantment.KNOCKBACK, 1, 
                   Enchantment.SWEEPING_EDGE, 3, Enchantment.MENDING, 1, Enchantment.UNBREAKING, 3));
        // Respawn Anchors
        inventory[1] = new ItemStack(Material.RESPAWN_ANCHOR, 64);
        // Glowstone
        inventory[2] = new ItemStack(Material.GLOWSTONE, 64);
        // End Crystals
        inventory[3] = new ItemStack(Material.END_CRYSTAL, 64);
        // Obsidian
        inventory[4] = new ItemStack(Material.OBSIDIAN, 64);
        // Ender pearls
        inventory[5] = new ItemStack(Material.ENDER_PEARL, 16);
        // Golden apples
        inventory[6] = new ItemStack(Material.GOLDEN_APPLE, 64);
        // Totem
        inventory[7] = new ItemStack(Material.TOTEM_OF_UNDYING);
        // Shield - Mending, Unbreaking 3
        inventory[8] = createItem(Material.SHIELD, 
            Map.of(Enchantment.MENDING, 1, Enchantment.UNBREAKING, 3));

        // More totems and supplies
        for (int i = 9; i < 18; i++) {
            inventory[i] = new ItemStack(Material.TOTEM_OF_UNDYING);
        }
        inventory[18] = new ItemStack(Material.END_CRYSTAL, 64);
        inventory[19] = new ItemStack(Material.OBSIDIAN, 64);
        inventory[20] = new ItemStack(Material.ENDER_PEARL, 16);
        // Axe - Sharpness 5, Efficiency 5, Silk Touch, Mending, Unbreaking 3
        inventory[21] = createItem(Material.NETHERITE_AXE, 
            Map.of(Enchantment.SHARPNESS, 5, Enchantment.EFFICIENCY, 5, 
                   Enchantment.SILK_TOUCH, 1, Enchantment.MENDING, 1, Enchantment.UNBREAKING, 3));
        // Pickaxe - Efficiency 5, Silk Touch, Mending, Unbreaking 3
        inventory[22] = createItem(Material.NETHERITE_PICKAXE, 
            Map.of(Enchantment.EFFICIENCY, 5, Enchantment.SILK_TOUCH, 1, 
                   Enchantment.MENDING, 1, Enchantment.UNBREAKING, 3));

        return new KitPreset("crystal", "§5⚗ Crystal Kit", Material.END_CRYSTAL, armor, inventory);
    }

    // ══════════════════════════════════════════════════════════════════════
    // MACE KIT - 1.21+ Mace PvP
    // Netherite armor (Prot 4), Maces (Breach/Wind Burst), Elytra, Wind Charges
    // ══════════════════════════════════════════════════════════════════════
    private static KitPreset createMaceKit() {
        ItemStack[] armor = new ItemStack[4];
        armor[0] = createItem(Material.NETHERITE_BOOTS, 
            Map.of(Enchantment.PROTECTION, 4, Enchantment.FEATHER_FALLING, 4));
        armor[1] = createItem(Material.NETHERITE_LEGGINGS, 
            Map.of(Enchantment.PROTECTION, 4));
        armor[2] = createItem(Material.NETHERITE_CHESTPLATE, 
            Map.of(Enchantment.PROTECTION, 4));
        armor[3] = createItem(Material.NETHERITE_HELMET, 
            Map.of(Enchantment.PROTECTION, 4));

        ItemStack[] inventory = new ItemStack[36];
        // Sword - Sharpness 5, Unbreaking 3
        inventory[0] = createItem(Material.NETHERITE_SWORD, 
            Map.of(Enchantment.SHARPNESS, 5, Enchantment.UNBREAKING, 3));
        // Axe - Sharpness 5, Unbreaking 3
        inventory[1] = createItem(Material.NETHERITE_AXE, 
            Map.of(Enchantment.SHARPNESS, 5, Enchantment.UNBREAKING, 3));
        // Ender pearls
        inventory[2] = new ItemStack(Material.ENDER_PEARL, 16);
        // Golden apples
        inventory[3] = new ItemStack(Material.GOLDEN_APPLE, 64);
        // Wind charges
        inventory[4] = new ItemStack(Material.WIND_CHARGE, 64);
        // Elytra
        inventory[5] = new ItemStack(Material.ELYTRA);
        // Mace 1 - Breach 4, Unbreaking 3
        inventory[6] = createItem(Material.MACE, 
            Map.of(Enchantment.BREACH, 4, Enchantment.UNBREAKING, 3));
        // Mace 2 - Wind Burst 3, Density 5, Unbreaking 3
        inventory[7] = createItem(Material.MACE, 
            Map.of(Enchantment.WIND_BURST, 3, Enchantment.DENSITY, 5, Enchantment.UNBREAKING, 3));
        // Shield - Mending, Unbreaking 3
        inventory[8] = createItem(Material.SHIELD, 
            Map.of(Enchantment.MENDING, 1, Enchantment.UNBREAKING, 3));

        // Potions
        for (int i = 9; i < 18; i++) {
            inventory[i] = createSplashPotion(i % 2 == 0 ? PotionType.STRONG_STRENGTH : PotionType.STRONG_SWIFTNESS);
        }
        // Totem
        inventory[35] = new ItemStack(Material.TOTEM_OF_UNDYING);

        return new KitPreset("mace", "§8⚒ Mace Kit", Material.MACE, armor, inventory);
    }

    // ══════════════════════════════════════════════════════════════════════
    // SPEAR KIT - 1.21.11 Spear PvP with Attribute Swapping
    // Spear is used for LUNGE dash mobility + damage
    // Sword/Mace for attribute swapping combos
    // ══════════════════════════════════════════════════════════════════════
    private static KitPreset createSpearKit() {
        ItemStack[] armor = new ItemStack[4];
        armor[0] = createItem(Material.NETHERITE_BOOTS, 
            Map.of(Enchantment.PROTECTION, 4, Enchantment.FEATHER_FALLING, 4, Enchantment.UNBREAKING, 3));
        armor[1] = createItem(Material.NETHERITE_LEGGINGS, 
            Map.of(Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3));
        armor[2] = createItem(Material.NETHERITE_CHESTPLATE, 
            Map.of(Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3));
        armor[3] = createItem(Material.NETHERITE_HELMET, 
            Map.of(Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3));

        ItemStack[] inventory = new ItemStack[36];
        // Slot 0: Netherite Spear - Lunge for dash mobility + damage
        // Compatible: Sharpness, Fire Aspect, Lunge, Knockback, Looting, Unbreaking
        // Note: Lunge is INCOMPATIBLE with Mending
        inventory[0] = createItem(Material.NETHERITE_SPEAR, 
            Map.of(Enchantment.SHARPNESS, 5, Enchantment.LUNGE, 3, 
                   Enchantment.KNOCKBACK, 2, Enchantment.LOOTING, 3, Enchantment.UNBREAKING, 3));
        // Slot 1: Netherite Sword - for attribute swap (hit → swap to spear for lunge)
        inventory[1] = createItem(Material.NETHERITE_SWORD, 
            Map.of(Enchantment.SHARPNESS, 5, Enchantment.SWEEPING_EDGE, 3, Enchantment.FIRE_ASPECT, 2, Enchantment.UNBREAKING, 3));
        // Slot 2: Netherite Axe - shield break + attribute swap
        inventory[2] = createItem(Material.NETHERITE_AXE, 
            Map.of(Enchantment.SHARPNESS, 5, Enchantment.EFFICIENCY, 5, Enchantment.UNBREAKING, 3));
        // Slot 3: Mace - for Breach attribute swaps
        inventory[3] = createItem(Material.MACE, 
            Map.of(Enchantment.BREACH, 4, Enchantment.UNBREAKING, 3));
        // Slot 4: Ender pearls
        inventory[4] = new ItemStack(Material.ENDER_PEARL, 16);
        // Slot 5: Golden apples
        inventory[5] = new ItemStack(Material.GOLDEN_APPLE, 64);
        // Slot 6: Wind charges for mobility
        inventory[6] = new ItemStack(Material.WIND_CHARGE, 64);
        // Slot 7: Second Mace - Wind Burst + Density for combos
        inventory[7] = createItem(Material.MACE, 
            Map.of(Enchantment.WIND_BURST, 3, Enchantment.DENSITY, 5, Enchantment.UNBREAKING, 3));
        // Slot 8: Shield
        inventory[8] = createItem(Material.SHIELD, 
            Map.of(Enchantment.UNBREAKING, 3));

        // Potions row
        for (int i = 9; i <= 13; i++) inventory[i] = createSplashPotion(PotionType.STRONG_HEALING);
        inventory[14] = createSplashPotion(PotionType.STRONG_STRENGTH);
        inventory[15] = createSplashPotion(PotionType.STRONG_STRENGTH);
        inventory[16] = createSplashPotion(PotionType.STRONG_SWIFTNESS);
        inventory[17] = createSplashPotion(PotionType.STRONG_SWIFTNESS);

        // More supplies
        for (int i = 18; i <= 26; i++) inventory[i] = createSplashPotion(PotionType.STRONG_HEALING);
        
        // Totems and extras
        inventory[27] = new ItemStack(Material.TOTEM_OF_UNDYING);
        inventory[28] = new ItemStack(Material.TOTEM_OF_UNDYING);
        inventory[29] = new ItemStack(Material.GOLDEN_APPLE, 64);
        inventory[30] = new ItemStack(Material.EXPERIENCE_BOTTLE, 64);
        for (int i = 31; i <= 35; i++) inventory[i] = new ItemStack(Material.TOTEM_OF_UNDYING);

        return new KitPreset("spear", "§3🔱 Spear Kit", Material.NETHERITE_SPEAR, armor, inventory);
    }

    // ══════════════════════════════════════════════════════════════════════
    // EXPLOSIVO KIT - TNT/Explosion PvP
    // Chainmail Blast Prot IV | Sword Sharp II | TNT | Flame Bow | Spawn Eggs
    // ══════════════════════════════════════════════════════════════════════
    private static KitPreset createExplosivoKit() {
        ItemStack[] armor = new ItemStack[4];
        armor[0] = createItem(Material.CHAINMAIL_BOOTS,
            Map.of(Enchantment.BLAST_PROTECTION, 4, Enchantment.FEATHER_FALLING, 4, Enchantment.UNBREAKING, 3));
        armor[1] = createItem(Material.CHAINMAIL_LEGGINGS,
            Map.of(Enchantment.BLAST_PROTECTION, 4, Enchantment.UNBREAKING, 3));
        armor[2] = createItem(Material.CHAINMAIL_CHESTPLATE,
            Map.of(Enchantment.BLAST_PROTECTION, 4, Enchantment.UNBREAKING, 3));
        armor[3] = createItem(Material.CHAINMAIL_HELMET,
            Map.of(Enchantment.BLAST_PROTECTION, 4, Enchantment.UNBREAKING, 3));

        ItemStack[] inventory = new ItemStack[36];
        // Hotbar
        inventory[0] = createItem(Material.DIAMOND_SWORD,
            Map.of(Enchantment.SHARPNESS, 2, Enchantment.UNBREAKING, 3));
        inventory[1] = createItem(Material.STICK,
            Map.of(Enchantment.KNOCKBACK, 2, Enchantment.UNBREAKING, 3));
        inventory[2] = createItem(Material.FLINT_AND_STEEL,
            Map.of(Enchantment.UNBREAKING, 3));
        inventory[3] = createItem(Material.BOW,
            Map.of(Enchantment.FLAME, 1, Enchantment.POWER, 2, Enchantment.UNBREAKING, 3, Enchantment.INFINITY, 1));
        inventory[4] = new ItemStack(Material.TNT, 64);
        inventory[5] = new ItemStack(Material.TNT, 64);
        inventory[6] = new ItemStack(Material.ENDER_PEARL, 16);
        inventory[7] = new ItemStack(Material.CREEPER_SPAWN_EGG, 16);
        inventory[8] = new ItemStack(Material.GOLDEN_APPLE, 32);
        // Inventory
        inventory[9]  = new ItemStack(Material.ARROW, 1);
        inventory[10] = new ItemStack(Material.TNT, 64);
        inventory[11] = new ItemStack(Material.TNT, 64);
        inventory[12] = new ItemStack(Material.OBSIDIAN, 32);
        inventory[13] = new ItemStack(Material.CREEPER_SPAWN_EGG, 16);
        inventory[14] = new ItemStack(Material.GOLDEN_APPLE, 32);
        for (int i = 15; i < 18; i++) inventory[i] = new ItemStack(Material.TOTEM_OF_UNDYING);

        return new KitPreset("explosivo", "§c§l💥 Explosivo Kit", Material.TNT, armor, inventory);
    }

    // ══════════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ══════════════════════════════════════════════════════════════════════

    private static ItemStack createItem(Material mat, Map<Enchantment, Integer> enchants) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            for (var entry : enchants.entrySet()) {
                meta.addEnchant(entry.getKey(), entry.getValue(), true);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack createSplashPotion(PotionType type) {
        ItemStack potion = new ItemStack(Material.SPLASH_POTION);
        PotionMeta meta = (PotionMeta) potion.getItemMeta();
        if (meta != null) {
            meta.setBasePotionType(type);
            potion.setItemMeta(meta);
        }
        return potion;
    }

    /**
     * Installs only presets that don't already exist in KitManager.
     * Safe to call on startup — never overwrites customised kits.
     */
    public static void installMissingPresets(PvPRoomsPro plugin) {
        var kitManager = plugin.getKitManager();
        if (kitManager == null) return;
        boolean saved = false;
        for (var preset : getAllPresets().values()) {
            if (!kitManager.kitExists(preset.name())) {
                kitManager.createKit(preset.name(), preset.armor(), preset.inventory(), preset.icon());
                saved = true;
                plugin.getLogger().info("[PvPRooms] Preset kit auto-instalado: " + preset.name());
            }
        }
        if (saved) kitManager.saveKits();
    }

    public static int installAllPresets(PvPRoomsPro plugin) {
        int count = 0;
        var kitManager = plugin.getKitManager();
        if (kitManager == null) return 0;

        for (var preset : getAllPresets().values()) {
            kitManager.createKit(preset.name(), preset.armor(), preset.inventory(), preset.icon());
            count++;
        }
        kitManager.saveKits();
        return count;
    }

    /**
     * Installs a single preset kit.
     */
    public static boolean installPreset(PvPRoomsPro plugin, String kitName) {
        var preset = getAllPresets().get(kitName.toLowerCase());
        if (preset == null) return false;

        var kitManager = plugin.getKitManager();
        if (kitManager == null) return false;

        kitManager.createKit(preset.name(), preset.armor(), preset.inventory(), preset.icon());
        kitManager.saveKits();
        return true;
    }
}

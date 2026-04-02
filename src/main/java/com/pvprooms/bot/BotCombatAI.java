package com.pvprooms.bot;

import com.pvprooms.PvPRoomsPro;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * Advanced Combat AI for practice bots.
 * 
 * Features:
 * - Sword/Axe combat with proper timing
 * - Mace attacks with jump crits and smash damage
 * - Spear/Trident throwing and attribute swapping
 * - Elytra flight combat
 * - Golden apple eating with proper timing
 * - Potion throwing (splash & lingering)
 * - Block placing for bridging/towering
 * - W-tapping for knockback
 * - Strafing and movement prediction
 * - Critical hits with proper jump timing
 * - Shield blocking and disabling
 * - Bow/Crossbow usage
 * - Combo tracking and reset
 */
public class BotCombatAI {

    private final PvPRoomsPro plugin;
    private final NPC npc;
    private final Player target;
    private final BotDifficulty difficulty;
    private final String kitName;
    private final Random random = new Random();
    
    // Tasks
    private BukkitTask mainTask;
    private BukkitTask movementTask;
    private BukkitTask healTask;
    
    // Combat state
    private long lastAttackTime = 0;
    private long lastHealTime = 0;
    private long lastPotionTime = 0;
    private long lastBlockPlace = 0;
    private long lastBowShot = 0;
    private long lastMaceJump = 0;
    private long lastSpearThrow = 0;
    private long lastElytraUse = 0;
    
    // Combo tracking
    private int comboCount = 0;
    private long lastComboHit = 0;
    
    // Movement state
    private int strafeDirection = 1;
    private long lastStrafeChange = 0;
    private boolean isRetreating = false;
    private boolean isEating = false;
    private boolean isBlocking = false;
    private long lastShieldRaise = 0;
    private boolean isUsingElytra = false;
    private boolean isSprinting = false;
    private boolean isSneaking = false;
    private long lastJump = 0;
    private long lastSneak = 0;
    private long lastSprintToggle = 0;

    // Smooth head rotation (interpolated)
    private float currentBotYaw = 0;
    private float currentBotPitch = 0;
    private boolean rotationInitialized = false;
    
    // Weapon detection cache
    private WeaponType currentWeapon = WeaponType.SWORD;
    private long lastWeaponCheck = 0;
    
    // Constants based on difficulty
    private final int tickRate;
    private final double accuracy;
    private final int reactionMs;
    private final double critChance;
    private final double healThreshold;
    
    // Adaptive AI parameters (null if not adaptive)
    private final AdaptiveAI.BotParameters adaptiveParams;
    
    // Adaptive-specific rates
    private final double strafeChance;
    private final double wTapChance;
    private final double jumpChance;
    private final double sneakChance;

    public BotCombatAI(PvPRoomsPro plugin, NPC npc, Player target, 
                       BotDifficulty difficulty, String kitName,
                       AdaptiveAI.BotParameters adaptiveParams) {
        this.plugin = plugin;
        this.npc = npc;
        this.target = target;
        this.difficulty = difficulty;
        this.kitName = kitName;
        this.adaptiveParams = adaptiveParams;
        
        // Use adaptive parameters if available, otherwise use difficulty defaults
        if (adaptiveParams != null && difficulty == BotDifficulty.ADAPTIVE) {
            this.tickRate = adaptiveParams.ticksBetweenAttacks;
            this.accuracy = adaptiveParams.hitAccuracy;
            this.reactionMs = adaptiveParams.reactionTimeMs;
            this.critChance = adaptiveParams.critChance;
            this.healThreshold = adaptiveParams.healThreshold;
            this.strafeChance = adaptiveParams.strafeChance;
            this.wTapChance = adaptiveParams.wTapChance;
            this.jumpChance = adaptiveParams.jumpChance;
            this.sneakChance = adaptiveParams.sneakChance;
        } else {
            // Standard difficulty parameters
            this.tickRate = difficulty.ticksBetweenActions;
            this.accuracy = difficulty.hitAccuracy;
            this.reactionMs = (int) difficulty.reactionTimeMs;
            this.critChance = switch(difficulty) {
                case EASY -> 0.08;
                case MEDIUM -> 0.15;
                case HARD -> 0.25;
                case HACKER -> 0.50;
                case ADAPTIVE -> 0.20;
            };
            this.healThreshold = difficulty.healThreshold;
            this.strafeChance = switch(difficulty) {
                case EASY -> 0.1;
                case MEDIUM -> 0.2;
                case HARD -> 0.35;
                case HACKER -> 0.5;
                case ADAPTIVE -> 0.25;
            };
            this.wTapChance = switch(difficulty) {
                case EASY -> 0.05;
                case MEDIUM -> 0.15;
                case HARD -> 0.25;
                case HACKER -> 0.4;
                case ADAPTIVE -> 0.15;
            };
            this.jumpChance = switch(difficulty) {
                case EASY -> 0.05;
                case MEDIUM -> 0.1;
                case HARD -> 0.15;
                case HACKER -> 0.25;
                case ADAPTIVE -> 0.1;
            };
            this.sneakChance = switch(difficulty) {
                case EASY -> 0.02;
                case MEDIUM -> 0.05;
                case HARD -> 0.1;
                case HACKER -> 0.2;
                case ADAPTIVE -> 0.08;
            };
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ══════════════════════════════════════════════════════════════════════════

    public void start() {
        // DISABLE Citizens Navigator completely - we'll move manually
        // Navigator causes teleporting issues
        npc.getNavigator().cancelNavigation();
        
        startMainLoop();
        startMovementLoop();
        startHealLoop();
    }

    public void stop() {
        // Drop shield visual before stopping so it doesn't get stuck
        if (isBlocking) {
            Player bot = getBotPlayer();
            if (bot != null) setShieldBlockVisual(bot, false);
            isBlocking = false;
        }
        if (mainTask != null) { mainTask.cancel(); mainTask = null; }
        if (movementTask != null) { movementTask.cancel(); movementTask = null; }
        if (healTask != null) { healTask.cancel(); healTask = null; }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MAIN COMBAT LOOP
    // ══════════════════════════════════════════════════════════════════════════

    private void startMainLoop() {
        mainTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!isValid()) { stop(); return; }
                
                Player bot = getBotPlayer();
                if (bot == null) return;
                
                double distance = bot.getLocation().distance(target.getLocation());
                
                // Update weapon type periodically
                updateWeaponType(bot);
                
                // Decision making based on situation
                if (isEating) return; // Don't interrupt eating
                
                // Shield management (raise/lower reactively)
                handleShieldLogic(bot, distance);
                
                // Combat decisions based on distance and weapon
                if (distance <= 4.0) {
                    handleMeleeCombat(bot, distance);
                } else if (distance <= 8.0) {
                    handleMidRangeCombat(bot, distance);
                } else if (distance <= 30.0) {
                    handleLongRangeCombat(bot, distance);
                } else {
                    // Far away (>30 blocks) - use elytra if available, otherwise just sprint
                    // NO TELEPORTING - bot walks/runs like a human
                    if (hasElytra(bot) && hasFireworks(bot)) {
                        handleElytraApproach(bot);
                    }
                    // Movement is handled by startMovementLoop() with velocity
                }
            }
        }.runTaskTimer(plugin, 1L, tickRate);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MELEE COMBAT (distance <= 4)
    // ══════════════════════════════════════════════════════════════════════════

    private void handleMeleeCombat(Player bot, double distance) {
        long now = System.currentTimeMillis();
        
        // Attack cooldown (vanilla is ~500ms = 10 ticks)
        // reactionMs is minimum time between attacks, not a skip chance
        if (now - lastAttackTime < reactionMs) return;
        
        // Look at target
        lookAt(bot, target.getLocation());
        
        // Crystal kit special handling - prioritize crystals/anchors
        if (kitName != null && kitName.equalsIgnoreCase("Crystal")) {
            handleCrystalCombat(bot, distance);
            return;
        }
        
        // Select best weapon for situation
        selectBestWeapon(bot);
        
        // Attack based on weapon type
        switch (currentWeapon) {
            case MACE -> handleMaceAttack(bot, distance);
            case AXE -> handleAxeAttack(bot, distance);
            case TRIDENT, SPEAR -> handleSpearMelee(bot, distance);
            default -> handleSwordAttack(bot, distance);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // CRYSTAL KIT COMBAT
    // ══════════════════════════════════════════════════════════════════════════

    private long lastCrystalPlace = 0;
    private long lastAnchorUse = 0;
    private boolean isCrystalAction = false; // Track if we're in middle of crystal action

    private void handleCrystalCombat(Player bot, double distance) {
        long now = System.currentTimeMillis();
        
        // If in middle of crystal action, don't interrupt
        if (isCrystalAction) return;
        
        // ALWAYS ensure we have sword equipped when not placing crystals
        ensureSwordEquipped(bot);
        
        // Decision making based on distance and situation
        boolean hasCrystals = hasCrystals(bot);
        boolean hasAnchors = hasAnchors(bot);
        boolean hasGlowstone = hasGlowstone(bot);
        
        // Pro crystal PvP: very aggressive crystal/anchor usage
        // Cooldowns based on difficulty
        int crystalCooldown = switch(difficulty) {
            case EASY -> 800;
            case MEDIUM -> 500;
            case HARD -> 300;
            case HACKER -> 150;  // Insane crystal speed
            case ADAPTIVE -> 400;
        };
        int anchorCooldown = switch(difficulty) {
            case EASY -> 1200;
            case MEDIUM -> 800;
            case HARD -> 500;
            case HACKER -> 250;
            case ADAPTIVE -> 600;
        };
        
        // Priority: Crystals > Anchors > Sword (pro crystal players spam crystals)
        if (hasCrystals && now - lastCrystalPlace > crystalCooldown) {
            // 80% chance to use crystal if available and off cooldown
            if (random.nextDouble() < 0.80) {
                placeCrystalAndDetonate(bot, distance);
                return;
            }
        }
        
        if (hasAnchors && hasGlowstone && now - lastAnchorUse > anchorCooldown) {
            // 70% chance to use anchor if no crystal used
            if (random.nextDouble() < 0.70) {
                useRespawnAnchor(bot, distance);
                return;
            }
        }
        
        // Sword attack as fallback - but still attack!
        if (distance <= 3.5) {
            handleSwordAttack(bot, distance);
        }
    }
    
    private void ensureSwordEquipped(Player bot) {
        ItemStack held = bot.getInventory().getItemInMainHand();
        if (held != null && isSword(held.getType())) return;
        
        // Find and equip sword
        for (int i = 0; i < 9; i++) {
            ItemStack item = bot.getInventory().getItem(i);
            if (item != null && isSword(item.getType())) {
                bot.getInventory().setHeldItemSlot(i);
                return;
            }
        }
    }
    
    private boolean isSword(Material mat) {
        return mat == Material.DIAMOND_SWORD || mat == Material.NETHERITE_SWORD ||
               mat == Material.IRON_SWORD || mat == Material.GOLDEN_SWORD ||
               mat == Material.STONE_SWORD || mat == Material.WOODEN_SWORD;
    }

    private void placeCrystalAndDetonate(Player bot, double distance) {
        int crystalSlot = findItem(bot.getInventory(), Material.END_CRYSTAL);
        if (crystalSlot == -1) return;
        
        // Find suitable placement location (obsidian/bedrock near target)
        Location targetLoc = target.getLocation().clone();
        
        // Look for obsidian/bedrock nearby
        Location placeLoc = findCrystalPlacement(bot, targetLoc);
        if (placeLoc == null) {
            // No valid placement, try placing obsidian first
            placeObsidianForCrystal(bot, targetLoc);
            return;
        }
        
        final int finalCrystalSlot = hotbarSlot(bot.getInventory(), crystalSlot);
        if (finalCrystalSlot == -1) return;
        
        // Find sword slot to switch back to
        int swordSlot = findSwordSlot(bot);
        
        isCrystalAction = true;
        bot.getInventory().setHeldItemSlot(finalCrystalSlot);
        
        // Place crystal
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!isValid()) { isCrystalAction = false; return; }
            
            // Spawn end crystal
            Location crystalLoc = placeLoc.clone().add(0.5, 1, 0.5);
            EnderCrystal crystal = (EnderCrystal) bot.getWorld().spawnEntity(crystalLoc, EntityType.END_CRYSTAL);
            crystal.setShowingBottom(false);
            
            // Consume crystal item
            ItemStack crystalItem = bot.getInventory().getItem(finalCrystalSlot);
            if (crystalItem != null) crystalItem.setAmount(crystalItem.getAmount() - 1);
            
            // IMMEDIATELY switch back to sword
            if (swordSlot != -1) {
                bot.getInventory().setHeldItemSlot(swordSlot);
            }
            
            // Detonate crystal after tiny delay
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (crystal.isValid()) {
                    bot.swingMainHand();
                    crystal.remove();
                    
                    // Create explosion damage
                    bot.getWorld().createExplosion(crystalLoc, 6.0f, false, false);
                }
                isCrystalAction = false;
            }, 1L);
        }, 1L);
        
        lastCrystalPlace = System.currentTimeMillis();
    }
    
    private int findSwordSlot(Player bot) {
        for (int i = 0; i < 9; i++) {
            ItemStack item = bot.getInventory().getItem(i);
            if (item != null && isSword(item.getType())) {
                return i;
            }
        }
        return -1;
    }

    private Location findCrystalPlacement(Player bot, Location target) {
        // Search for obsidian/bedrock blocks near target
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    Location check = target.clone().add(dx, dy, dz);
                    Block block = check.getBlock();
                    if ((block.getType() == Material.OBSIDIAN || block.getType() == Material.BEDROCK) 
                            && check.clone().add(0, 1, 0).getBlock().getType() == Material.AIR) {
                        return check;
                    }
                }
            }
        }
        return null;
    }

    private void placeObsidianForCrystal(Player bot, Location target) {
        int obsidianSlot = findItem(bot.getInventory(), Material.OBSIDIAN);
        if (obsidianSlot == -1) return;
        
        // Place obsidian near target
        Location placeLoc = target.clone();
        placeLoc.setY(Math.floor(placeLoc.getY()));
        Block placeBlock = placeLoc.getBlock();
        
        if (placeBlock.getType() == Material.AIR) {
            final int finalObsidianSlot = hotbarSlot(bot.getInventory(), obsidianSlot);
            if (finalObsidianSlot == -1) return;
            int originalSlot = bot.getInventory().getHeldItemSlot();
            bot.getInventory().setHeldItemSlot(finalObsidianSlot);
            
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!isValid()) return;
                placeBlock.setType(Material.OBSIDIAN);
                ItemStack obsidian = bot.getInventory().getItem(finalObsidianSlot);
                if (obsidian != null) obsidian.setAmount(obsidian.getAmount() - 1);
                bot.swingMainHand();
                bot.getInventory().setHeldItemSlot(originalSlot);
            }, 1L);
        }
    }

    private void useRespawnAnchor(Player bot, double distance) {
        int anchorSlot = findItem(bot.getInventory(), Material.RESPAWN_ANCHOR);
        int glowstoneSlot = findItem(bot.getInventory(), Material.GLOWSTONE);
        if (anchorSlot == -1 || glowstoneSlot == -1) return;
        
        Location targetLoc = target.getLocation().clone();
        Location placeLoc = targetLoc.clone().subtract(0, 1, 0);
        Block placeBlock = placeLoc.getBlock();
        
        // Need air to place anchor
        if (placeBlock.getType() != Material.AIR && !placeBlock.getType().isSolid()) {
            placeLoc = targetLoc.clone();
            placeBlock = placeLoc.getBlock();
        }
        
        if (placeBlock.getType() != Material.AIR) return;
        
        final int finalAnchorSlot = hotbarSlot(bot.getInventory(), anchorSlot);
        final int finalGlowstoneSlot = hotbarSlot(bot.getInventory(), glowstoneSlot);
        if (finalAnchorSlot == -1 || finalGlowstoneSlot == -1) return;
        
        // Find sword slot to switch back to
        int swordSlot = findSwordSlot(bot);
        
        isCrystalAction = true;
        bot.getInventory().setHeldItemSlot(finalAnchorSlot);
        final Block finalBlock = placeBlock;
        
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!isValid()) { isCrystalAction = false; return; }
            
            finalBlock.setType(Material.RESPAWN_ANCHOR);
            ItemStack anchor = bot.getInventory().getItem(finalAnchorSlot);
            if (anchor != null) anchor.setAmount(anchor.getAmount() - 1);
            bot.swingMainHand();
            
            // Charge with glowstone
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!isValid()) { isCrystalAction = false; return; }
                bot.getInventory().setHeldItemSlot(finalGlowstoneSlot);
                
                // Simulate charging and exploding (in overworld it explodes)
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!isValid()) { isCrystalAction = false; return; }
                    ItemStack glowstone = bot.getInventory().getItem(finalGlowstoneSlot);
                    if (glowstone != null) glowstone.setAmount(glowstone.getAmount() - 1);
                    
                    // Explode the anchor
                    Location explosionLoc = finalBlock.getLocation().add(0.5, 0.5, 0.5);
                    finalBlock.setType(Material.AIR);
                    bot.getWorld().createExplosion(explosionLoc, 5.0f, true, false);
                    
                    // Switch back to sword
                    if (swordSlot != -1) {
                        bot.getInventory().setHeldItemSlot(swordSlot);
                    }
                    isCrystalAction = false;
                }, 2L);
            }, 2L);
        }, 1L);
        
        lastAnchorUse = System.currentTimeMillis();
    }

    private boolean hasCrystals(Player bot) {
        return findItem(bot.getInventory(), Material.END_CRYSTAL) != -1;
    }

    private boolean hasAnchors(Player bot) {
        return findItem(bot.getInventory(), Material.RESPAWN_ANCHOR) != -1;
    }

    private boolean hasGlowstone(Player bot) {
        return findItem(bot.getInventory(), Material.GLOWSTONE) != -1;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SHIELD BLOCKING
    // ══════════════════════════════════════════════════════════════════════════

    private void handleShieldLogic(Player bot, double distance) {
        ItemStack offhand = bot.getInventory().getItemInOffHand();
        if (offhand == null || offhand.getType() != Material.SHIELD) return;

        long now = System.currentTimeMillis();

        // Lower shield when attacking or target is far
        if (distance > 5.0 || now - lastAttackTime < 300) {
            if (isBlocking) {
                isBlocking = false;
                setShieldBlockVisual(bot, false);
            }
            return;
        }

        // Chance to raise shield (per combat tick)
        double blockChance = switch (difficulty) {
            case EASY     -> 0.04;
            case MEDIUM   -> 0.10;
            case HARD     -> 0.22;
            case HACKER   -> 0.40;
            case ADAPTIVE -> 0.18;
        };

        if (!isBlocking && random.nextDouble() < blockChance) {
            isBlocking = true;
            setShieldBlockVisual(bot, true);
            lastShieldRaise = now;
        } else if (isBlocking) {
            long holdTime = switch (difficulty) {
                case EASY     -> 600;
                case MEDIUM   -> 900;
                case HARD     -> 1400;
                case HACKER   -> 1800;
                case ADAPTIVE -> 1000;
            };
            if (now - lastShieldRaise > holdTime || random.nextDouble() < 0.04) {
                isBlocking = false;
                setShieldBlockVisual(bot, false);
            }
        }
    }

    /**
     * Makes the bot entity visually raise/lower its shield using NMS.
     * Paper 1.21 uses Mojang mappings at runtime, so we can find the
     * LivingEntity#startUsingItem / stopUsingItem methods by their proper names.
     */
    private void setShieldBlockVisual(Player bot, boolean blocking) {
        try {
            // ServerPlayer via CraftPlayer.getHandle()
            java.lang.reflect.Method getHandle =
                    bot.getClass().getDeclaredMethod("getHandle");
            getHandle.setAccessible(true);
            Object nmsPlayer = getHandle.invoke(bot);

            if (blocking) {
                Class<?> handClass =
                        Class.forName("net.minecraft.world.InteractionHand");
                Object offHand = handClass.getField("OFF_HAND").get(null);
                nmsPlayer.getClass()
                        .getMethod("startUsingItem", handClass)
                        .invoke(nmsPlayer, offHand);
            } else {
                // stopUsingItem() is defined on LivingEntity in 1.21 Mojang mappings
                try {
                    nmsPlayer.getClass().getMethod("stopUsingItem").invoke(nmsPlayer);
                } catch (NoSuchMethodException ignored) {
                    nmsPlayer.getClass().getMethod("releaseUsingItem").invoke(nmsPlayer);
                }
            }
        } catch (Exception ignored) {
            // NMS reflection unavailable — no visual change
        }
    }

    public boolean isShieldBlocking() { return isBlocking; }

    private void handleSwordAttack(Player bot, double distance) {
        if (distance > 3.5) return;
        
        // Check line of sight first
        if (!hasLineOfSight(bot, target)) return;
        
        long now = System.currentTimeMillis();
        
        // If target is blocking with shield, switch to axe to break it
        if (target.isBlocking() && hasAxe(bot)) {
            switchToAxe(bot);
            handleAxeShieldBreak(bot);
            lastAttackTime = now;
            return;
        }
        
        // W-tap for extra knockback (sprint reset) - only if target not blocking
        if (shouldWTap() && !target.isBlocking()) {
            performWTap(bot);
        }
        
        // Attempt critical hit sometimes (not every hit - that's not human-like)
        double critAttemptChance = switch(difficulty) {
            case EASY -> 0.15;
            case MEDIUM -> 0.30;
            case HARD -> 0.45;
            case HACKER -> 0.70;
            case ADAPTIVE -> critChance;
        };
        
        if (bot.isOnGround() && random.nextDouble() < critAttemptChance && System.currentTimeMillis() - lastJump > 800) {
            bot.setVelocity(bot.getVelocity().add(new Vector(0, 0.42, 0)));
            lastJump = System.currentTimeMillis();
            // Delay attack to land crit
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (isValid()) performAttack(bot, true);
            }, 3L);
        } else {
            // Normal attack or already in air
            boolean isFalling = !bot.isOnGround() && bot.getVelocity().getY() < -0.08;
            performAttack(bot, isFalling);
        }
        
        lastAttackTime = now;
    }

    private void handleAxeShieldBreak(Player bot) {
        // Jump for crit + shield break - but only if not recently jumped
        if (bot.isOnGround() && System.currentTimeMillis() - lastJump > 1000) {
            bot.setVelocity(bot.getVelocity().add(new Vector(0, 0.42, 0)));
            lastJump = System.currentTimeMillis();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (isValid()) {
                    performAttack(bot, true); // Crit to disable shield
                }
            }, 3L);
        } else {
            performAttack(bot, bot.getVelocity().getY() < -0.08);
        }
    }

    private boolean hasAxe(Player bot) {
        for (ItemStack item : bot.getInventory().getContents()) {
            if (item != null && isAxe(item.getType())) return true;
        }
        return false;
    }

    private void switchToAxe(Player bot) {
        for (int i = 0; i < 9; i++) {
            ItemStack item = bot.getInventory().getItem(i);
            if (item != null && isAxe(item.getType())) {
                bot.getInventory().setHeldItemSlot(i);
                return;
            }
        }
    }

    private boolean isAxe(Material mat) {
        return mat == Material.DIAMOND_AXE || mat == Material.NETHERITE_AXE ||
               mat == Material.IRON_AXE || mat == Material.GOLDEN_AXE ||
               mat == Material.STONE_AXE || mat == Material.WOODEN_AXE;
    }

    private void handleAxeAttack(Player bot, double distance) {
        if (distance > 3.5) return;
        if (!hasLineOfSight(bot, target)) return;
        
        long now = System.currentTimeMillis();
        
        // Axe attacks are slower but deal more damage and can disable shields
        // Check if target is blocking - ALWAYS crit with axe to break shield
        boolean targetBlocking = target.isBlocking();
        
        // Jump for crit with axe - but not constantly
        double axeCritChance = switch(difficulty) {
            case EASY -> 0.20;
            case MEDIUM -> 0.40;
            case HARD -> 0.55;
            case HACKER -> 0.80;
            case ADAPTIVE -> critChance;
        };
        
        if (bot.isOnGround() && random.nextDouble() < axeCritChance && System.currentTimeMillis() - lastJump > 1000) {
            bot.setVelocity(bot.getVelocity().add(new Vector(0, 0.42, 0)));
            lastJump = System.currentTimeMillis();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (isValid()) performAttack(bot, true);
            }, 3L);
        } else {
            boolean isFalling = !bot.isOnGround() && bot.getVelocity().getY() < -0.08;
            performAttack(bot, isFalling);
        }
        
        lastAttackTime = now;
    }

    private void handleMaceAttack(Player bot, double distance) {
        long now = System.currentTimeMillis();
        
        // Check if we have elytra - use it for maximum mace damage!
        if (hasElytra(bot) && hasFireworks(bot) && now - lastMaceJump > 4000) {
            handleElytraMaceCombo(bot);
            lastMaceJump = now;
            return;
        }
        
        // Mace is most effective with fall damage - use vanilla jump height
        if (bot.isOnGround() && now - lastMaceJump > 3000) {
            // Use vanilla jump height (0.42) - mace damage comes from fall, not jump height
            double jumpPower = 0.42;
            
            // Jump towards target with vanilla-like velocity
            Vector direction = target.getLocation().toVector()
                    .subtract(bot.getLocation().toVector()).normalize();
            direction.setY(jumpPower);
            direction.multiply(0.5); // Better horizontal speed
            bot.setVelocity(direction);
            
            lastMaceJump = now;
            
            // Attack when falling
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (isValid() && bot.getFallDistance() > 0.3) {
                    performMaceSmash(bot);
                }
            }, 8L);
        } else if (bot.getFallDistance() > 0.3) {
            // Already falling - smash!
            performMaceSmash(bot);
        } else if (distance <= 3.0) {
            // Ground attack - normal melee
            performAttack(bot, false);
            lastAttackTime = now;
        }
    }

    /**
     * Elytra + Mace combo - fly up high and smash down for massive damage!
     */
    private void handleElytraMaceCombo(Player bot) {
        // Equip elytra if not wearing
        ItemStack chestplate = bot.getInventory().getChestplate();
        if (chestplate == null || chestplate.getType() != Material.ELYTRA) {
            int elytraSlot = findItem(bot.getInventory(), Material.ELYTRA);
            if (elytraSlot != -1) {
                ItemStack elytra = bot.getInventory().getItem(elytraSlot);
                bot.getInventory().setItem(elytraSlot, chestplate);
                bot.getInventory().setChestplate(elytra);
            } else {
                return;
            }
        }
        
        // Make sure we have mace equipped
        int maceSlot = findItem(bot.getInventory(), Material.MACE);
        if (maceSlot != -1 && maceSlot < 9) {
            bot.getInventory().setHeldItemSlot(maceSlot);
        }
        
        // Launch upward
        Vector launchVel = new Vector(0, 1.5, 0);
        bot.setVelocity(launchVel);
        
        // Start gliding after jump
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!isValid()) return;
            Player b = getBotPlayer();
            if (b == null) return;
            
            b.setGliding(true);
            isUsingElytra = true;
            
            // Boost UP with firework for height
            int fwSlot = findItem(b.getInventory(), Material.FIREWORK_ROCKET);
            if (fwSlot != -1) {
                ItemStack fw = b.getInventory().getItem(fwSlot);
                if (fw != null) {
                    // Boost upward
                    b.setVelocity(new Vector(0, 2.5, 0));
                    fw.setAmount(fw.getAmount() - 1);
                    b.getWorld().playSound(b.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 1.0f);
                    b.getWorld().spawn(b.getLocation(), Firework.class, f -> f.detonate());
                }
            }
        }, 3L);
        
        // After reaching height, dive down towards target
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!isValid()) return;
            Player b = getBotPlayer();
            if (b == null) return;
            
            // Stop gliding and dive
            b.setGliding(false);
            isUsingElytra = false;
            
            // Calculate dive vector towards target
            Vector dive = target.getLocation().toVector()
                    .subtract(b.getLocation().toVector())
                    .normalize();
            dive.setY(-1.5); // Strong downward component
            dive.multiply(1.5);
            b.setVelocity(dive);
            
            // Smash when landing
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (isValid()) {
                    Player bb = getBotPlayer();
                    if (bb != null && bb.getFallDistance() > 1.0) {
                        performMaceSmash(bb);
                    }
                }
            }, 15L);
        }, 25L);
    }

    private void performMaceSmash(Player bot) {
        if (!isValid()) return;
        
        double distance = bot.getLocation().distance(target.getLocation());
        if (distance > 4.0) return;
        
        // Swing animation
        bot.swingMainHand();
        
        // Mace smash damage scales with fall distance (vanilla-like)
        double fallDistance = bot.getFallDistance();
        double baseDamage = 6.0;
        double bonusDamage = Math.min(fallDistance * 1.5, 10); // Reasonable cap
        double totalDamage = baseDamage + bonusDamage;
        
        // Deal damage
        target.damage(totalDamage, bot);
        
        // Mace knockback - vanilla-like, not excessive
        Vector direction = target.getLocation().toVector()
                .subtract(bot.getLocation().toVector());
        direction.setY(0);
        direction.normalize();
        
        // Small horizontal + normal vertical lift
        double kbHorizontal = 0.5 + Math.min(fallDistance * 0.05, 0.3);
        Vector knockback = direction.multiply(kbHorizontal);
        knockback.setY(0.4);
        target.setVelocity(knockback);
        
        // Sound and particles
        bot.getWorld().playSound(bot.getLocation(), Sound.ITEM_MACE_SMASH_GROUND, 1.0f, 1.0f);
        
        lastAttackTime = System.currentTimeMillis();
        comboCount = 0;
    }

    private void handleSpearMelee(Player bot, double distance) {
        // Spear can be used in melee or thrown
        if (distance <= 3.0) {
            // Melee attack
            performAttack(bot, random.nextDouble() < critChance);
            lastAttackTime = System.currentTimeMillis();
        } else if (distance <= 6.0) {
            // Consider throwing
            handleSpearThrow(bot, distance);
        }
    }

    private void performAttack(Player bot, boolean isCrit) {
        if (!isValid()) return;
        
        double distance = bot.getLocation().distance(target.getLocation());
        if (distance > 4.0) return;
        
        // Line of sight check - cannot hit through walls
        if (!hasLineOfSight(bot, target)) {
            return;
        }
        
        // Swing animation
        bot.swingMainHand();
        
        // Calculate damage
        double damage = getWeaponDamage(bot);
        if (isCrit) {
            damage *= 1.5;
            // Crit particles
            target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0), 10);
        }
        
        // Apply damage
        target.damage(damage, bot);
        
        // Apply knockback only if target is NOT blocking with shield
        if (!target.isBlocking()) {
            applyKnockback(bot, target);
        }
        
        // Update combo
        long now = System.currentTimeMillis();
        if (now - lastComboHit < 1500) {
            comboCount++;
        } else {
            comboCount = 1;
        }
        lastComboHit = now;
    }

    /**
     * Check if bot has clear line of sight to target (no walls between).
     */
    private boolean hasLineOfSight(Player bot, Player target) {
        Location eyeLocation = bot.getEyeLocation();
        Location targetEye = target.getEyeLocation();
        
        Vector direction = targetEye.toVector().subtract(eyeLocation.toVector());
        double distance = direction.length();
        direction.normalize();
        
        // Ray trace for blocks
        for (double d = 0.5; d < distance; d += 0.5) {
            Location check = eyeLocation.clone().add(direction.clone().multiply(d));
            Block block = check.getBlock();
            if (block.getType().isSolid() && block.getType().isOccluding()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Apply REDUCED knockback to victim.
     * Bot knockback is intentionally lower than vanilla to prevent
     * excessive combo knockback that feels unfair.
     * 
     * Vanilla values (for reference):
     * - Base horizontal: 0.4
     * - Base vertical: 0.4
     * - Sprint bonus: +0.4 horizontal
     * 
     * Bot uses ~60% of vanilla values.
     */
    private void applyKnockback(Player attacker, Player victim) {
        // Cooldown between knockback applications (prevent spam)
        long now = System.currentTimeMillis();
        if (now - lastKnockbackApplied < 400) return; // 400ms cooldown
        lastKnockbackApplied = now;
        
        // Direction from attacker to victim
        Vector direction = victim.getLocation().toVector()
                .subtract(attacker.getLocation().toVector());
        direction.setY(0);
        if (direction.lengthSquared() > 0) {
            direction.normalize();
        } else {
            return; // Same position, no knockback
        }
        
        // REDUCED values (60% of vanilla)
        double kbHorizontal = 0.25;  // Was 0.4
        double kbVertical = 0.28;    // Was 0.4
        
        // Sprint bonus (reduced)
        if (attacker.isSprinting()) {
            kbHorizontal += 0.2;  // Was 0.4
        }
        
        // Knockback enchantment (reduced)
        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        if (weapon != null && weapon.hasItemMeta()) {
            int kbLevel = weapon.getEnchantmentLevel(Enchantment.KNOCKBACK);
            kbHorizontal += kbLevel * 0.3;  // Was 0.5
        }
        
        // Build final knockback vector
        Vector knockback = direction.multiply(kbHorizontal);
        knockback.setY(kbVertical);
        
        // Apply knockback
        victim.setVelocity(knockback);
    }
    
    private long lastKnockbackApplied = 0;

    // ══════════════════════════════════════════════════════════════════════════
    // MID-RANGE COMBAT (4 < distance <= 8)
    // ══════════════════════════════════════════════════════════════════════════

    private void handleMidRangeCombat(Player bot, double distance) {
        // Options: chase, throw potions, throw spear, use bow
        
        // Check for throwables first
        if (hasSpear(bot) && shouldThrowSpear()) {
            handleSpearThrow(bot, distance);
            return;
        }
        
        if (hasSplashPotions(bot) && shouldThrowPotion()) {
            throwPotion(bot);
            return;
        }
        
        // Chase to melee range - movement handled by startMovementLoop()
        // NO Navigator - causes teleporting
        bot.setSprinting(true);
    }

    private void handleSpearThrow(Player bot, double distance) {
        long now = System.currentTimeMillis();
        if (now - lastSpearThrow < 1500) return;
        
        // Find trident/spear in inventory
        int slot = findTrident(bot.getInventory());
        if (slot == -1) return;
        
        // Accuracy check
        if (random.nextDouble() > accuracy) return;
        
        // Switch to trident
        slot = hotbarSlot(bot.getInventory(), slot);
        if (slot == -1) return;
        bot.getInventory().setHeldItemSlot(slot);
        
        // Look at target with prediction
        Location predictedLoc = predictTargetLocation(target, distance);
        lookAt(bot, predictedLoc);
        
        // Throw trident
        ItemStack trident = bot.getInventory().getItem(slot);
        if (trident != null && trident.getType() == Material.TRIDENT) {
            Trident thrown = bot.getWorld().spawn(bot.getEyeLocation(), Trident.class);
            
            Vector velocity = predictedLoc.toVector()
                    .subtract(bot.getEyeLocation().toVector())
                    .normalize()
                    .multiply(2.5);
            
            thrown.setVelocity(velocity);
            thrown.setShooter(bot);
            thrown.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
            
            // Loyalty - trident returns
            if (trident.getEnchantmentLevel(Enchantment.LOYALTY) > 0) {
                // Simulate return after delay
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (thrown.isValid()) thrown.remove();
                }, 60L);
            } else {
                // Consume if no loyalty
                trident.setAmount(trident.getAmount() - 1);
            }
            
            bot.swingMainHand();
            lastSpearThrow = now;
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // LONG-RANGE COMBAT (distance > 8)
    // ══════════════════════════════════════════════════════════════════════════

    private void handleLongRangeCombat(Player bot, double distance) {
        // Options: bow, crossbow, elytra approach, chase
        
        if (hasBow(bot) && shouldShootBow()) {
            handleBowShot(bot, distance);
            return;
        }
        
        if (hasElytra(bot) && shouldUseElytra(distance)) {
            handleElytraApproach(bot);
            return;
        }
        
        // Default: chase - movement handled by startMovementLoop()
        // NO Navigator - causes teleporting
        bot.setSprinting(true);
    }

    private void handleBowShot(Player bot, double distance) {
        long now = System.currentTimeMillis();
        if (now - lastBowShot < 1200) return;
        
        int bowSlot = findBow(bot.getInventory());
        if (bowSlot == -1) return;
        
        // Check for arrows
        if (!hasArrows(bot)) return;
        
        // Accuracy based on difficulty
        if (random.nextDouble() > accuracy * 0.8) return;
        
        // Switch to bow
        bowSlot = hotbarSlot(bot.getInventory(), bowSlot);
        if (bowSlot == -1) return;
        bot.getInventory().setHeldItemSlot(bowSlot);
        
        // Predict where target will be when arrow arrives
        Location predictedLoc = predictTargetLocation(target, distance);

        // Pre-calculate the gravity-compensated velocity so bot looks where it will actually shoot
        Vector aimVelocity = calculateArrowVelocity(bot.getEyeLocation(), predictedLoc, distance);

        // Make bot face the gravity-corrected aim direction (looks like a real player aiming up for range)
        Location aimLook = bot.getEyeLocation().clone();
        aimLook.setDirection(aimVelocity);
        bot.teleport(aimLook);

        // Charge time based on difficulty (full charge = 20 ticks)
        int chargeTime = switch(difficulty) {
            case EASY -> 25;
            case MEDIUM -> 20;
            case HARD -> 15;
            case HACKER -> 8;
            case ADAPTIVE -> 18;
        };
        
        // Simulate bow draw and release
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!isValid()) return;
            
            // Recalculate at release time in case target moved
            Location releasedPredicted = predictTargetLocation(target, bot.getLocation().distance(target.getLocation()));
            Vector velocity = calculateArrowVelocity(bot.getEyeLocation(), releasedPredicted, distance);

            Arrow arrow = bot.getWorld().spawn(bot.getEyeLocation(), Arrow.class);
            arrow.setVelocity(velocity);
            arrow.setShooter(bot);
            arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
            arrow.setDamage(9.0); // Full charge damage
            
            // Consume arrow
            consumeArrow(bot);
            
            bot.getWorld().playSound(bot.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1.0f, 1.0f);
            lastBowShot = System.currentTimeMillis();
        }, chargeTime);
    }

    private Vector calculateArrowVelocity(Location from, Location to, double distance) {
        // Arrow speed at full charge varies by difficulty
        double arrowSpeed = switch (difficulty) {
            case EASY     -> 2.2;
            case MEDIUM   -> 2.7;
            case HARD     -> 3.0;
            case HACKER   -> 3.0;
            case ADAPTIVE -> 2.8;
        };

        // Horizontal distance for flight-time estimation
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);

        // Estimated flight ticks (accounting for ~1% air resistance per tick)
        double flightTicks = horizontalDist / (arrowSpeed * 0.97);

        // Gravity compensation: Minecraft arrows fall ~0.05 blocks/tick²
        // Approximate drop = 0.025 * t² for the vertical component
        double gravityCompensation = 0.025 * flightTicks * flightTicks;

        Vector direction = to.toVector().subtract(from.toVector());
        direction.setY(direction.getY() + gravityCompensation);

        // Human-like aim error per difficulty
        double aimError = switch (difficulty) {
            case EASY     -> 0.25;
            case MEDIUM   -> 0.12;
            case HARD     -> 0.05;
            case HACKER   -> 0.01;
            case ADAPTIVE -> 0.08;
        };
        direction.add(new Vector(
            (random.nextDouble() - 0.5) * aimError,
            (random.nextDouble() - 0.5) * aimError * 0.4,
            (random.nextDouble() - 0.5) * aimError
        ));

        return direction.normalize().multiply(arrowSpeed);
    }

    private void handleElytraApproach(Player bot) {
        long now = System.currentTimeMillis();
        if (now - lastElytraUse < 2000) return;
        
        // Check if wearing elytra
        ItemStack chestplate = bot.getInventory().getChestplate();
        if (chestplate == null || chestplate.getType() != Material.ELYTRA) {
            // Try to equip elytra from inventory
            int elytraSlot = findItem(bot.getInventory(), Material.ELYTRA);
            if (elytraSlot != -1) {
                ItemStack elytra = bot.getInventory().getItem(elytraSlot);
                ItemStack currentChest = bot.getInventory().getChestplate();
                bot.getInventory().setChestplate(elytra);
                bot.getInventory().setItem(elytraSlot, currentChest);
            } else {
                return;
            }
        }
        
        // Jump and glide towards target
        if (bot.isOnGround()) {
            // Launch upward first
            Vector direction = target.getLocation().toVector()
                    .subtract(bot.getLocation().toVector())
                    .normalize();
            direction.setY(1.0);
            direction.multiply(1.2);
            bot.setVelocity(direction);
            
            // Start gliding after jump
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (isValid()) {
                    Player b = getBotPlayer();
                    if (b == null) return;
                    b.setGliding(true);
                    isUsingElytra = true;
                    
                    // Boost with firework
                    if (hasFireworks(b)) {
                        useFireworkBoost(b);
                    }
                }
            }, 4L);
            
            lastElytraUse = now;
            
            // Continue boosting with fireworks while gliding
            for (int i = 1; i <= 3; i++) {
                final int delay = i;
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (isValid() && isUsingElytra) {
                        Player b = getBotPlayer();
                        if (b != null && b.isGliding() && hasFireworks(b)) {
                            useFireworkBoost(b);
                        }
                    }
                }, 10L * delay);
            }
            
            // Stop gliding when close or after timeout
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (isValid()) {
                    Player b = getBotPlayer();
                    if (b != null) {
                        b.setGliding(false);
                        isUsingElytra = false;
                    }
                }
            }, 60L);
        }
    }

    private void useFireworkBoost(Player bot) {
        int slot = findItem(bot.getInventory(), Material.FIREWORK_ROCKET);
        if (slot == -1) return;
        
        ItemStack firework = bot.getInventory().getItem(slot);
        if (firework == null) return;
        
        // Switch to firework briefly to use it
        slot = hotbarSlot(bot.getInventory(), slot);
        if (slot == -1) return;
        int originalSlot = bot.getInventory().getHeldItemSlot();
        bot.getInventory().setHeldItemSlot(slot);
        
        // Calculate boost direction towards target
        Vector toTarget = target.getLocation().toVector()
                .subtract(bot.getLocation().toVector())
                .normalize();
        
        // Apply strong boost velocity
        Vector boost = toTarget.multiply(2.0);
        if (boost.getY() < 0.3) boost.setY(0.3); // Keep some lift
        bot.setVelocity(boost);
        
        // Spawn firework entity for visual effect
        bot.getWorld().spawn(bot.getLocation(), Firework.class, fw -> {
            fw.detonate();
        });
        
        // Consume firework
        firework.setAmount(firework.getAmount() - 1);
        
        // Sound
        bot.getWorld().playSound(bot.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 1.0f);
        
        // Switch back
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (isValid()) getBotPlayer().getInventory().setHeldItemSlot(originalSlot);
        }, 2L);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MOVEMENT LOOP
    // ══════════════════════════════════════════════════════════════════════════

    private void startMovementLoop() {
        movementTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!isValid()) return;
                
                Player bot = getBotPlayer();
                if (bot == null) return;
                
                double distance = bot.getLocation().distance(target.getLocation());
                double health = bot.getHealth();
                double maxHealth = bot.getMaxHealth();
                double healthPercent = (health / maxHealth) * 100;
                
                // ALWAYS move towards target when not eating or retreating
                if (!isEating && !isRetreating && !isUsingElytra) {
                    // Move manually with velocity - NO Navigator (causes teleporting)
                    moveTowardsTarget(bot, distance);
                }
                
                // Sneak/shift management (for combos, dodging)
                handleSneaking(bot, distance);
                
                // Random jumps while moving (PvP style)
                handleJumping(bot, distance);
                
                // Retreat if low health
                if (healthPercent < 30 && !isEating) {
                    if (!isRetreating && random.nextDouble() < 0.3) {
                        isRetreating = true;
                        retreat(bot);
                    }
                } else {
                    isRetreating = false;
                }
                
                // W-tap periodically during combat
                if (distance <= 4 && shouldWTap()) {
                    performWTap(bot);
                }
                
                // Block placement for positioning
                if (shouldPlaceBlock(bot, distance)) {
                    placeBlock(bot);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L); // Run EVERY tick for smooth movement
    }

    /**
     * Moves the bot towards the target using teleportation with small steps.
     * This prevents the "flying" behavior caused by velocity manipulation.
     * 
     * Movement is done by teleporting small distances while respecting gravity.
     */
    private void moveTowardsTarget(Player bot, double distance) {
        if (distance < 2.0) return;

        // Apply gravity manually when airborne (Player entities are client-driven normally)
        if (!bot.isOnGround()) {
            Vector vel = bot.getVelocity();
            vel.setY(Math.max(vel.getY() - 0.08, -3.92));
            bot.setVelocity(vel);
            return;
        }

        Location targetLoc = target.getLocation();

        // Look at target FIRST so the following bot.getLocation() returns the new yaw/pitch
        lookAt(bot, targetLoc);

        // Capture location AFTER lookAt so yaw is correct in the movement teleport
        Location botLoc = bot.getLocation();

        double dx = targetLoc.getX() - botLoc.getX();
        double dz = targetLoc.getZ() - botLoc.getZ();
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
        if (horizontalDist < 0.1) return;

        dx /= horizontalDist;
        dz /= horizontalDist;

        double speed;
        if (distance > 4) {
            bot.setSprinting(true);
            isSprinting = true;
            speed = 0.22;
        } else {
            bot.setSprinting(false);
            isSprinting = false;
            speed = 0.16;
        }

        // Integrate strafe into the movement vector (circle-strafing)
        double strafeSpeed = 0;
        if (distance <= 4.5 && !isRetreating) {
            long now = System.currentTimeMillis();
            if (now - lastStrafeChange > 700 + random.nextInt(800)) {
                if (random.nextDouble() < 0.35) {
                    strafeDirection *= -1;
                    lastStrafeChange = now;
                }
            }
            strafeSpeed = switch (difficulty) {
                case EASY     -> 0.06;
                case MEDIUM   -> 0.09;
                case HARD     -> 0.13;
                case HACKER   -> 0.20;
                case ADAPTIVE -> 0.11;
            } * strafeDirection;
        }

        // Forward + perpendicular strafe combined
        double finalDx = dx * speed + (-dz) * strafeSpeed;
        double finalDz = dz * speed + ( dx) * strafeSpeed;

        double newX = botLoc.getX() + finalDx;
        double newZ = botLoc.getZ() + finalDz;

        Location newLoc = new Location(botLoc.getWorld(), newX, botLoc.getY(), newZ,
                botLoc.getYaw(), botLoc.getPitch());

        Block blockAtFeet = newLoc.getBlock();
        if (blockAtFeet.getType().isSolid()) {
            Location stepUp = newLoc.clone().add(0, 1, 0);
            if (!stepUp.getBlock().getType().isSolid()) {
                newLoc = stepUp;
            } else {
                return;
            }
        }

        bot.teleport(newLoc);
    }

    private void handleSprinting(Player bot, double distance) {
        long now = System.currentTimeMillis();
        
        // Sprint chance based on difficulty
        double sprintChance = switch(difficulty) {
            case EASY -> 0.3;
            case MEDIUM -> 0.5;
            case HARD -> 0.7;
            case HACKER -> 0.95;
            case ADAPTIVE -> adaptiveParams != null ? adaptiveParams.sprintChance : 0.6;
        };
        
        // Always sprint when chasing from far away
        if (distance > 5 && !isEating && !isRetreating) {
            if (!isSprinting && random.nextDouble() < sprintChance) {
                bot.setSprinting(true);
                isSprinting = true;
                lastSprintToggle = now;
            }
        }
        
        // Sprint during combat approach
        if (distance > 2 && distance <= 5 && !isEating) {
            if (now - lastSprintToggle > 500 && random.nextDouble() < sprintChance * 0.6) {
                bot.setSprinting(true);
                isSprinting = true;
                lastSprintToggle = now;
            }
        }
        
        // Stop sprinting when very close or eating
        if ((distance < 2 || isEating) && isSprinting) {
            if (random.nextDouble() < 0.3) {
                bot.setSprinting(false);
                isSprinting = false;
            }
        }
    }

    private void handleSneaking(Player bot, double distance) {
        long now = System.currentTimeMillis();
        if (now - lastSneak < 300) return;
        
        // Sneak chance based on difficulty (for reduce knockback, mind games)
        double sneakChance = switch(difficulty) {
            case EASY -> 0.02;
            case MEDIUM -> 0.08;
            case HARD -> 0.15;
            case HACKER -> 0.25;
            case ADAPTIVE -> this.sneakChance;
        };
        
        // Sneak randomly during close combat (reduces knockback taken)
        if (distance <= 4 && !isEating && !isRetreating) {
            if (!isSneaking && random.nextDouble() < sneakChance) {
                bot.setSneaking(true);
                isSneaking = true;
                lastSneak = now;
                
                // Stop sneaking after short duration
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (isValid() && isSneaking) {
                        getBotPlayer().setSneaking(false);
                        isSneaking = false;
                    }
                }, 3L + random.nextInt(5));
            }
        }
        
        // Quick sneak when getting hit (reduce KB)
        if (target.getLastDamageCause() != null && distance <= 3) {
            if (random.nextDouble() < sneakChance * 2) {
                bot.setSneaking(true);
                isSneaking = true;
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (isValid()) {
                        getBotPlayer().setSneaking(false);
                        isSneaking = false;
                    }
                }, 2L);
            }
        }
    }

    private void handleJumping(Player bot, double distance) {
        long now = System.currentTimeMillis();
        // Much longer cooldown between jumps (was 400, now 1500+)
        long jumpCooldown = switch(difficulty) {
            case EASY -> 3000;
            case MEDIUM -> 2000;
            case HARD -> 1500;
            case HACKER -> 1000;
            case ADAPTIVE -> 1800;
        };
        if (now - lastJump < jumpCooldown) return;
        if (!bot.isOnGround()) return;
        
        // Much lower jump chance
        double jumpChance = switch(difficulty) {
            case EASY -> 0.02;
            case MEDIUM -> 0.05;
            case HARD -> 0.08;
            case HACKER -> 0.15;
            case ADAPTIVE -> this.jumpChance;
        };
        
        // Only jump over obstacles - this is necessary movement
        Block inFront = bot.getLocation().add(bot.getLocation().getDirection().multiply(1)).getBlock();
        if (inFront.getType().isSolid()) {
            bot.setVelocity(bot.getVelocity().add(new Vector(0, 0.42, 0)));
            lastJump = now;
            return;
        }
        
        // Occasional bhop while sprinting (very rare)
        if (isSprinting && distance > 5 && distance < 15 && random.nextDouble() < jumpChance) {
            bot.setVelocity(bot.getVelocity().add(new Vector(0, 0.42, 0)));
            lastJump = now;
        }
    }

    private void performStrafe(Player bot) {
        long now = System.currentTimeMillis();
        
        // Human-like: don't strafe constantly, only sometimes
        if (random.nextDouble() > 0.4) return; // 60% chance to NOT strafe
        
        // Change strafe direction periodically (human-like timing 800-1500ms)
        if (now - lastStrafeChange > 800 + random.nextInt(700) && random.nextDouble() < 0.25) {
            strafeDirection *= -1;
            lastStrafeChange = now;
        }
        
        // Calculate strafe vector - MUCH slower than before (human-like)
        double strafeSpeed = switch(difficulty) {
            case EASY -> 0.08;
            case MEDIUM -> 0.10;
            case HARD -> 0.12;    // Human-like
            case HACKER -> 0.18;
            case ADAPTIVE -> strafeChance;
        };
        
        Vector strafe = bot.getLocation().getDirection()
                .crossProduct(new Vector(0, 1, 0))
                .normalize()
                .multiply(strafeSpeed * strafeDirection);
        
        // Apply strafe
        Vector currentVel = bot.getVelocity();
        currentVel.add(strafe);
        bot.setVelocity(currentVel);
    }

    private boolean shouldWTap() {
        // Human-like: W-tap is hard to do consistently, reduce chance
        double chance = switch(difficulty) {
            case EASY -> 0.0;     // Beginners don't W-tap
            case MEDIUM -> 0.08;  // Rarely
            case HARD -> 0.15;    // Sometimes (human-like)
            case HACKER -> 0.35;  // Often
            case ADAPTIVE -> wTapChance;
        };
        return random.nextDouble() < chance;
    }

    private void performWTap(Player bot) {
        // Sprint reset for extra knockback
        bot.setSprinting(false);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (isValid()) bot.setSprinting(true);
        }, 1L);
    }

    private void retreat(Player bot) {
        // Move away from target (human-like speed)
        double retreatSpeed = switch(difficulty) {
            case EASY -> 0.2;
            case MEDIUM -> 0.25;
            case HARD -> 0.3;    // Human-like
            case HACKER -> 0.4;
            case ADAPTIVE -> 0.28;
        };
        
        Vector away = bot.getLocation().toVector()
                .subtract(target.getLocation().toVector())
                .normalize()
                .multiply(retreatSpeed);
        away.setY(0);
        
        bot.setVelocity(bot.getVelocity().add(away));
        
        // Try to heal while retreating
        tryHeal(bot);
    }

    private boolean shouldPlaceBlock(Player bot, double distance) {
        // Human-like: place blocks less frequently
        long cooldown = switch(difficulty) {
            case EASY -> 2000;    // Very slow
            case MEDIUM -> 1500;  // Slow  
            case HARD -> 1000;    // Human-like
            case HACKER -> 400;   // Fast
            case ADAPTIVE -> 1200;
        };
        if (System.currentTimeMillis() - lastBlockPlace < cooldown) return false;
        
        // Don't always place blocks (human hesitation)
        if (random.nextDouble() > 0.3) return false;
        
        // Bridge gaps or tower up
        Block below = bot.getLocation().subtract(0, 1, 0).getBlock();
        if (below.getType() == Material.AIR) {
            return hasBlocks(bot);
        }
        
        return false;
    }

    private void placeBlock(Player bot) {
        int slot = findBuildingBlock(bot.getInventory());
        if (slot == -1) return;
        
        Block below = bot.getLocation().subtract(0, 1, 0).getBlock();
        if (below.getType() != Material.AIR) return;
        
        ItemStack blocks = bot.getInventory().getItem(slot);
        if (blocks == null) return;
        
        // Store current held item
        ItemStack previousHeld = bot.getInventory().getItemInMainHand();
        slot = hotbarSlot(bot.getInventory(), slot);
        if (slot == -1) return;
        int previousSlot = bot.getInventory().getHeldItemSlot();
        
        // Switch to block slot (hold in hand)
        bot.getInventory().setHeldItemSlot(slot);
        
        // Small delay to simulate human switching, then place
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!isValid()) return;
            
            Block targetBlock = bot.getLocation().subtract(0, 1, 0).getBlock();
            if (targetBlock.getType() != Material.AIR) return;
            
            ItemStack blockItem = bot.getInventory().getItemInMainHand();
            if (blockItem == null || !blockItem.getType().isBlock()) return;
            
            // Place block and swing arm
            targetBlock.setType(blockItem.getType());
            bot.swingMainHand();
            blockItem.setAmount(blockItem.getAmount() - 1);
            
            // Switch back to weapon after placing (human-like delay)
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!isValid()) return;
                bot.getInventory().setHeldItemSlot(previousSlot);
            }, 4L); // 200ms to switch back
            
        }, 3L); // 150ms to switch to block
        
        lastBlockPlace = System.currentTimeMillis();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // HEALING LOOP
    // ══════════════════════════════════════════════════════════════════════════

    private void startHealLoop() {
        healTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!isValid()) return;
                
                Player bot = getBotPlayer();
                if (bot == null) return;
                
                double health = bot.getHealth();
                double maxHealth = bot.getMaxHealth();
                double healthPercent = (health / maxHealth) * 100;
                
                // Manage offhand items (totems, shields)
                manageOffhand(bot);
                
                // Heal when below threshold
                if (healthPercent <= healThreshold && !isEating) {
                    tryHeal(bot);
                }
                
                // Use potions at low health
                if (healthPercent <= 50) {
                    tryUsePotions(bot);
                }
            }
        }.runTaskTimer(plugin, 10L, 20L);
    }

    /**
     * Manage offhand items - equip totems, shields, etc.
     */
    private void manageOffhand(Player bot) {
        ItemStack offhand = bot.getInventory().getItemInOffHand();
        
        // If offhand is empty or not useful, try to equip something
        if (offhand == null || offhand.getType() == Material.AIR) {
            // Priority 1: Totem of Undying
            int totemSlot = findItem(bot.getInventory(), Material.TOTEM_OF_UNDYING);
            if (totemSlot != -1) {
                ItemStack totem = bot.getInventory().getItem(totemSlot);
                bot.getInventory().setItem(totemSlot, null);
                bot.getInventory().setItemInOffHand(totem);
                return;
            }
            
            // Priority 2: Shield (for sword kit)
            if (currentWeapon == WeaponType.SWORD || currentWeapon == WeaponType.AXE) {
                int shieldSlot = findItem(bot.getInventory(), Material.SHIELD);
                if (shieldSlot != -1) {
                    ItemStack shield = bot.getInventory().getItem(shieldSlot);
                    bot.getInventory().setItem(shieldSlot, null);
                    bot.getInventory().setItemInOffHand(shield);
                    return;
                }
            }
            
            // Priority 3: Arrows for bow (convenience)
            if (hasBow(bot)) {
                int arrowSlot = findItem(bot.getInventory(), Material.ARROW);
                if (arrowSlot == -1) arrowSlot = findItem(bot.getInventory(), Material.SPECTRAL_ARROW);
                if (arrowSlot == -1) arrowSlot = findItem(bot.getInventory(), Material.TIPPED_ARROW);
                if (arrowSlot != -1) {
                    ItemStack arrows = bot.getInventory().getItem(arrowSlot);
                    bot.getInventory().setItem(arrowSlot, null);
                    bot.getInventory().setItemInOffHand(arrows);
                    return;
                }
            }
        }
        
        // If totem was used (offhand became empty after having totem), re-equip new one
        if (offhand != null && offhand.getType() != Material.TOTEM_OF_UNDYING) {
            int totemSlot = findItem(bot.getInventory(), Material.TOTEM_OF_UNDYING);
            if (totemSlot != -1) {
                // Move current offhand to inventory
                ItemStack currentOffhand = bot.getInventory().getItemInOffHand();
                ItemStack totem = bot.getInventory().getItem(totemSlot);
                bot.getInventory().setItem(totemSlot, currentOffhand);
                bot.getInventory().setItemInOffHand(totem);
            }
        }
    }

    private void tryHeal(Player bot) {
        if (isEating) return;
        
        long now = System.currentTimeMillis();
        if (now - lastHealTime < 2000) return;
        
        // Find golden apples (search whole inventory)
        int gappleSlot = findItem(bot.getInventory(), Material.GOLDEN_APPLE);
        if (gappleSlot == -1) {
            gappleSlot = findItem(bot.getInventory(), Material.ENCHANTED_GOLDEN_APPLE);
        }
        
        if (gappleSlot == -1) return;
        
        gappleSlot = hotbarSlot(bot.getInventory(), gappleSlot);
        if (gappleSlot == -1) return;
        
        final int slot = gappleSlot;
        int originalSlot = bot.getInventory().getHeldItemSlot();
        
        // Switch to gapple
        bot.getInventory().setHeldItemSlot(slot);
        isEating = true;
        
        // Vanilla eating time is 32 ticks (1.6 seconds)
        int eatTime = 32;
        
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!isValid()) { isEating = false; return; }
            
            ItemStack gapple = bot.getInventory().getItem(slot);
            if (gapple == null) { isEating = false; return; }
            
            // Apply effects
            if (gapple.getType() == Material.ENCHANTED_GOLDEN_APPLE) {
                bot.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 400, 1));
                bot.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 2400, 3));
                bot.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 6000, 0));
                bot.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 6000, 0));
            } else {
                bot.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1));
                bot.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 2400, 0));
            }
            
            // Consume
            gapple.setAmount(gapple.getAmount() - 1);
            
            // Switch back
            bot.getInventory().setHeldItemSlot(originalSlot);
            isEating = false;
            lastHealTime = System.currentTimeMillis();
            
            // Eating sound
            bot.getWorld().playSound(bot.getLocation(), Sound.ENTITY_PLAYER_BURP, 1.0f, 1.0f);
        }, eatTime);
    }

    private void tryUsePotions(Player bot) {
        int potSlot = findHealingPotion(bot.getInventory());
        if (potSlot == -1) return;

        long now = System.currentTimeMillis();
        if (now - lastPotionTime < 1200) return;

        ItemStack potion = bot.getInventory().getItem(potSlot);
        if (potion == null) return;

        // Move to hotbar if needed for visual
        int hsSlot = potSlot <= 8 ? potSlot : hotbarSlot(bot.getInventory(), potSlot);
        if (hsSlot == -1) return;

        final int originalSlot = bot.getInventory().getHeldItemSlot();
        bot.getInventory().setHeldItemSlot(hsSlot);
        bot.swingMainHand(); // "throw" animation

        // Apply INSTANT_HEALTH directly — Citizens NPCs aren’t reliably hit by splash potions
        org.bukkit.inventory.meta.PotionMeta meta =
                (org.bukkit.inventory.meta.PotionMeta) potion.getItemMeta();
        if (meta != null && meta.getBasePotionType() != null) {
            for (PotionEffect eff : meta.getBasePotionType().getPotionEffects()) {
                if (eff.getType().isInstant()) {
                    // INSTANT_HEALTH: heal directly
                    double heal = (4 << eff.getAmplifier());
                    bot.setHealth(Math.min(bot.getHealth() + heal, bot.getMaxHealth()));
                } else {
                    bot.addPotionEffect(eff);
                }
            }
        }
        // Also apply any custom effects
        if (meta != null) {
            for (PotionEffect eff : meta.getCustomEffects()) {
                if (eff.getType().isInstant()) {
                    double heal = (4 << eff.getAmplifier());
                    bot.setHealth(Math.min(bot.getHealth() + heal, bot.getMaxHealth()));
                } else {
                    bot.addPotionEffect(eff);
                }
            }
        }

        // Consume potion
        ItemStack held = bot.getInventory().getItem(hsSlot);
        if (held != null) held.setAmount(held.getAmount() - 1);

        // Visual/audio feedback
        bot.getWorld().playSound(bot.getLocation(),
                org.bukkit.Sound.ENTITY_SPLASH_POTION_BREAK, 0.8f, 1.1f);
        bot.getWorld().spawnParticle(org.bukkit.Particle.INSTANT_EFFECT,
                bot.getLocation().add(0, 1, 0), 20, 0.4, 0.4, 0.4, 0.05,
                org.bukkit.Color.AQUA);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (isValid()) bot.getInventory().setHeldItemSlot(originalSlot);
        }, 3L);

        lastPotionTime = now;
    }

    private void throwPotion(Player bot) {
        int potSlot = findDamagePotion(bot.getInventory());
        if (potSlot == -1) return;
        
        long now = System.currentTimeMillis();
        if (now - lastPotionTime < 2000) return;
        
        ItemStack potion = bot.getInventory().getItem(potSlot);
        if (potion == null) return;
        
        // Predict target location
        double distance = bot.getLocation().distance(target.getLocation());
        Location predictedLoc = predictTargetLocation(target, distance);
        
        // Throw at target
        ThrownPotion thrown = bot.getWorld().spawn(bot.getEyeLocation(), ThrownPotion.class);
        thrown.setItem(potion.clone());
        
        Vector velocity = predictedLoc.toVector()
                .subtract(bot.getEyeLocation().toVector())
                .normalize()
                .multiply(1.2);
        velocity.setY(velocity.getY() + 0.3);
        
        thrown.setVelocity(velocity);
        thrown.setShooter(bot);
        
        potion.setAmount(potion.getAmount() - 1);
        lastPotionTime = now;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // UTILITY METHODS
    // ══════════════════════════════════════════════════════════════════════════

    private boolean isValid() {
        return npc != null && npc.isSpawned() 
                && target != null && target.isOnline() && !target.isDead()
                && npc.getEntity() != null && !npc.getEntity().isDead();
    }

    private Player getBotPlayer() {
        if (!npc.isSpawned()) return null;
        Entity entity = npc.getEntity();
        return entity instanceof Player ? (Player) entity : null;
    }

    private void lookAt(Player bot, Location target) {
        // Initialize rotation from bot's actual current rotation
        if (!rotationInitialized) {
            currentBotYaw   = bot.getLocation().getYaw();
            currentBotPitch = bot.getLocation().getPitch();
            rotationInitialized = true;
        }

        // Occasionally "miss" looking at target (human-like)
        if (random.nextDouble() > accuracy + 0.25) return;

        Vector direction = target.toVector().subtract(bot.getEyeLocation().toVector()).normalize();

        // Aim jitter per difficulty
        double jitter = switch (difficulty) {
            case EASY     -> 0.14;
            case MEDIUM   -> 0.07;
            case HARD     -> 0.03;
            case HACKER   -> 0.005;
            case ADAPTIVE -> 0.05;
        };
        direction.add(new Vector(
                (random.nextDouble() - 0.5) * jitter,
                (random.nextDouble() - 0.5) * jitter * 0.5,
                (random.nextDouble() - 0.5) * jitter
        )).normalize();

        // Compute target yaw/pitch from direction vector
        double dx = direction.getX(), dy = direction.getY(), dz = direction.getZ();
        float targetYaw   = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float targetPitch = (float) Math.toDegrees(Math.atan2(-dy, Math.sqrt(dx * dx + dz * dz)));

        // Max turn speed per tick — human-like smooth rotation
        float maxTurn = switch (difficulty) {
            case EASY     -> 7.0f;
            case MEDIUM   -> 14.0f;
            case HARD     -> 28.0f;
            case HACKER   -> 55.0f;
            case ADAPTIVE -> 20.0f;
        };

        // Wrap yaw difference to [-180, 180] so we always turn the short way
        float yawDiff = targetYaw - currentBotYaw;
        while (yawDiff >  180) yawDiff -= 360;
        while (yawDiff < -180) yawDiff += 360;
        float pitchDiff = targetPitch - currentBotPitch;

        currentBotYaw   += Math.clamp(yawDiff,   -maxTurn, maxTurn);
        currentBotPitch += Math.clamp(pitchDiff, -maxTurn * 0.6f, maxTurn * 0.6f);
        currentBotPitch  = Math.clamp(currentBotPitch, -90f, 90f);

        // Send rotation packet only — NO teleport, no position change
        bot.setRotation(currentBotYaw, currentBotPitch);
    }

    private Location predictTargetLocation(Player target, double distance) {
        // Predict where target will be based on velocity
        Vector velocity = target.getVelocity();
        double prediction = switch(difficulty) {
            case EASY -> 0.1;
            case MEDIUM -> 0.3;
            case HARD -> 0.5;
            case HACKER -> 0.8;
            case ADAPTIVE -> 0.4;
        };
        
        Location predicted = target.getLocation().clone();
        predicted.add(velocity.multiply(prediction * (distance / 5)));
        return predicted;
    }

    private void updateWeaponType(Player bot) {
        long now = System.currentTimeMillis();
        if (now - lastWeaponCheck < 500) return;
        
        ItemStack weapon = bot.getInventory().getItemInMainHand();
        if (weapon == null) {
            currentWeapon = WeaponType.FIST;
            return;
        }
        
        currentWeapon = switch(weapon.getType()) {
            case MACE -> WeaponType.MACE;
            case TRIDENT -> WeaponType.TRIDENT;
            case NETHERITE_AXE, DIAMOND_AXE, IRON_AXE, STONE_AXE, WOODEN_AXE, GOLDEN_AXE -> WeaponType.AXE;
            case BOW -> WeaponType.BOW;
            case CROSSBOW -> WeaponType.CROSSBOW;
            default -> WeaponType.SWORD;
        };
        
        lastWeaponCheck = now;
    }

    private void selectBestWeapon(Player bot) {
        // Select best weapon from hotbar for current situation
        PlayerInventory inv = bot.getInventory();
        
        // Priority: Mace (if can crit) > Axe (if target blocking) > Sword
        if (target.isBlocking()) {
            int axeSlot = findAxe(inv);
            if (axeSlot != -1) {
                inv.setHeldItemSlot(axeSlot);
                currentWeapon = WeaponType.AXE;
                return;
            }
        }
        
        // Default to first weapon found
        for (int i = 0; i < 9; i++) {
            ItemStack item = inv.getItem(i);
            if (item == null) continue;
            
            Material type = item.getType();
            if (type.name().contains("SWORD") || type.name().contains("AXE") 
                    || type == Material.MACE || type == Material.TRIDENT) {
                inv.setHeldItemSlot(i);
                return;
            }
        }
    }

    private double getWeaponDamage(Player bot) {
        ItemStack weapon = bot.getInventory().getItemInMainHand();
        if (weapon == null) return 1.0;
        
        double base = switch(weapon.getType()) {
            case NETHERITE_SWORD -> 8.0;
            case DIAMOND_SWORD -> 7.0;
            case IRON_SWORD -> 6.0;
            case STONE_SWORD -> 5.0;
            case WOODEN_SWORD, GOLDEN_SWORD -> 4.0;
            case NETHERITE_AXE -> 10.0;
            case DIAMOND_AXE -> 9.0;
            case IRON_AXE -> 9.0;
            case STONE_AXE -> 9.0;
            case WOODEN_AXE, GOLDEN_AXE -> 7.0;
            case MACE -> 7.0;
            case TRIDENT -> 9.0;
            default -> 1.0;
        };
        
        // Sharpness enchantment
        int sharpness = weapon.getEnchantmentLevel(Enchantment.SHARPNESS);
        base += sharpness * 0.5 + (sharpness > 0 ? 0.5 : 0);
        
        return base;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // INVENTORY SEARCH METHODS
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Ensures the item at `slot` is in the hotbar (0-8).
     * If it's outside the hotbar, swaps it into the first free hotbar slot
     * (or slot 8 as last resort). Returns the hotbar slot, or -1 if slot is -1.
     */
    private int hotbarSlot(PlayerInventory inv, int slot) {
        if (slot == -1) return -1;
        if (slot <= 8) return slot; // Already in hotbar
        
        ItemStack item = inv.getItem(slot);
        if (item == null) return -1;
        
        // Find a free hotbar slot first
        for (int i = 0; i <= 8; i++) {
            ItemStack existing = inv.getItem(i);
            if (existing == null || existing.getType() == Material.AIR) {
                inv.setItem(i, item);
                inv.setItem(slot, null);
                return i;
            }
        }
        
        // No free slot - swap with slot 8
        ItemStack displaced = inv.getItem(8);
        inv.setItem(8, item);
        inv.setItem(slot, displaced);
        return 8;
    }

    private int findItem(PlayerInventory inv, Material material) {
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() == material) return i;
        }
        return -1;
    }

    private int findAxe(PlayerInventory inv) {
        for (int i = 0; i < 9; i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType().name().contains("_AXE")) return i;
        }
        return -1;
    }

    private int findTrident(PlayerInventory inv) {
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() == Material.TRIDENT) return i;
        }
        return -1;
    }

    private int findBow(PlayerInventory inv) {
        for (int i = 0; i < 9; i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && (item.getType() == Material.BOW || item.getType() == Material.CROSSBOW)) return i;
        }
        return -1;
    }

    private int findBuildingBlock(PlayerInventory inv) {
        Set<Material> buildBlocks = Set.of(
                Material.COBBLESTONE, Material.DIRT, Material.OAK_PLANKS, 
                Material.STONE, Material.NETHERRACK, Material.END_STONE
        );
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && buildBlocks.contains(item.getType())) return i;
        }
        return -1;
    }

    private int findHealingPotion(PlayerInventory inv) {
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() == Material.SPLASH_POTION) {
                // Check if it's a healing potion
                if (item.hasItemMeta()) {
                    org.bukkit.inventory.meta.PotionMeta meta = (org.bukkit.inventory.meta.PotionMeta) item.getItemMeta();
                    if (meta.getBasePotionType() != null && 
                        meta.getBasePotionType().name().contains("HEAL")) {
                        return i;
                    }
                }
            }
        }
        return -1;
    }

    private int findDamagePotion(PlayerInventory inv) {
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() == Material.SPLASH_POTION) {
                if (item.hasItemMeta()) {
                    org.bukkit.inventory.meta.PotionMeta meta = (org.bukkit.inventory.meta.PotionMeta) item.getItemMeta();
                    if (meta.getBasePotionType() != null && 
                        (meta.getBasePotionType().name().contains("HARM") ||
                         meta.getBasePotionType().name().contains("POISON"))) {
                        return i;
                    }
                }
            }
        }
        return -1;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // HAS ITEM CHECKS
    // ══════════════════════════════════════════════════════════════════════════

    private boolean hasSpear(Player bot) { return findTrident(bot.getInventory()) != -1; }
    private boolean hasBow(Player bot) { return findBow(bot.getInventory()) != -1; }
    private boolean hasBlocks(Player bot) { return findBuildingBlock(bot.getInventory()) != -1; }
    private boolean hasSplashPotions(Player bot) {
        return findItem(bot.getInventory(), Material.SPLASH_POTION) != -1;
    }
    private boolean hasElytra(Player bot) {
        ItemStack chest = bot.getInventory().getChestplate();
        return chest != null && chest.getType() == Material.ELYTRA;
    }
    private boolean hasFireworks(Player bot) {
        return findItem(bot.getInventory(), Material.FIREWORK_ROCKET) != -1;
    }
    private boolean hasArrows(Player bot) {
        return findItem(bot.getInventory(), Material.ARROW) != -1 
                || findItem(bot.getInventory(), Material.SPECTRAL_ARROW) != -1
                || findItem(bot.getInventory(), Material.TIPPED_ARROW) != -1;
    }

    private void consumeArrow(Player bot) {
        int slot = findItem(bot.getInventory(), Material.ARROW);
        if (slot == -1) slot = findItem(bot.getInventory(), Material.SPECTRAL_ARROW);
        if (slot == -1) slot = findItem(bot.getInventory(), Material.TIPPED_ARROW);
        if (slot == -1) return;
        
        ItemStack arrows = bot.getInventory().getItem(slot);
        if (arrows != null) arrows.setAmount(arrows.getAmount() - 1);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DECISION METHODS
    // ══════════════════════════════════════════════════════════════════════════

    private boolean shouldThrowSpear() {
        return random.nextDouble() < accuracy * 0.6 
                && System.currentTimeMillis() - lastSpearThrow > 2000;
    }

    private boolean shouldThrowPotion() {
        return random.nextDouble() < accuracy * 0.5 
                && System.currentTimeMillis() - lastPotionTime > 3000;
    }

    private boolean shouldShootBow() {
        return random.nextDouble() < accuracy * 0.7 
                && System.currentTimeMillis() - lastBowShot > 1500;
    }

    private boolean shouldUseElytra(double distance) {
        return distance > 15 && random.nextDouble() < 0.3 
                && System.currentTimeMillis() - lastElytraUse > 5000;
    }

    public BotDifficulty getDifficulty() { return difficulty; }

    private enum WeaponType {
        FIST, SWORD, AXE, MACE, TRIDENT, SPEAR, BOW, CROSSBOW
    }
}

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
    private boolean isUsingElytra = false;
    
    // Weapon detection cache
    private WeaponType currentWeapon = WeaponType.SWORD;
    private long lastWeaponCheck = 0;
    
    // Constants based on difficulty
    private final int tickRate;
    private final double accuracy;
    private final int reactionMs;
    private final double critChance;
    private final double healThreshold;

    public BotCombatAI(PvPRoomsPro plugin, NPC npc, Player target, 
                       BotDifficulty difficulty, String kitName) {
        this.plugin = plugin;
        this.npc = npc;
        this.target = target;
        this.difficulty = difficulty;
        this.kitName = kitName;
        
        // Set difficulty parameters (human-like values)
        this.tickRate = difficulty.ticksBetweenActions;
        this.accuracy = difficulty.hitAccuracy;
        this.reactionMs = (int) difficulty.reactionTimeMs;
        // Human crit chance: requires timing jump perfectly, most players hit 20-40%
        this.critChance = switch(difficulty) {
            case EASY -> 0.08;   // Beginner rarely crits
            case MEDIUM -> 0.18; // Average player
            case HARD -> 0.30;   // Good player (human-like)
            case HACKER -> 0.75; // Inhuman timing
        };
        this.healThreshold = difficulty.healThreshold;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ══════════════════════════════════════════════════════════════════════════

    public void start() {
        startMainLoop();
        startMovementLoop();
        startHealLoop();
    }

    public void stop() {
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
                
                // Combat decisions based on distance and weapon
                if (distance <= 4.0) {
                    handleMeleeCombat(bot, distance);
                } else if (distance <= 8.0) {
                    handleMidRangeCombat(bot, distance);
                } else if (distance <= 30.0) {
                    handleLongRangeCombat(bot, distance);
                } else {
                    // Chase target
                    npc.getNavigator().setTarget(target, true);
                }
            }
        }.runTaskTimer(plugin, 1L, tickRate);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MELEE COMBAT (distance <= 4)
    // ══════════════════════════════════════════════════════════════════════════

    private void handleMeleeCombat(Player bot, double distance) {
        long now = System.currentTimeMillis();
        
        // Check reaction time
        if (now - lastAttackTime < reactionMs) return;
        
        // Accuracy check
        if (random.nextDouble() > accuracy) return;
        
        // Look at target
        lookAt(bot, target.getLocation());
        
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

    private void handleSwordAttack(Player bot, double distance) {
        if (distance > 3.5) return;
        
        long now = System.currentTimeMillis();
        
        // W-tap for extra knockback (sprint reset)
        if (shouldWTap()) {
            performWTap(bot);
        }
        
        // Critical hit - jump before hitting
        boolean doCrit = random.nextDouble() < critChance && bot.isOnGround();
        if (doCrit) {
            bot.setVelocity(bot.getVelocity().add(new Vector(0, 0.42, 0)));
            // Delay attack to land crit
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (isValid()) performAttack(bot, true);
            }, 3L);
        } else {
            performAttack(bot, false);
        }
        
        lastAttackTime = now;
    }

    private void handleAxeAttack(Player bot, double distance) {
        if (distance > 3.5) return;
        
        long now = System.currentTimeMillis();
        
        // Axe attacks are slower but deal more damage and can disable shields
        // Check if target is blocking
        boolean targetBlocking = target.isBlocking();
        
        if (targetBlocking) {
            // Prioritize axe to disable shield
            performAttack(bot, false);
            // Shield disabled for 5 seconds
        } else {
            // Normal axe crit
            boolean doCrit = random.nextDouble() < critChance && bot.isOnGround();
            if (doCrit) {
                bot.setVelocity(bot.getVelocity().add(new Vector(0, 0.42, 0)));
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (isValid()) performAttack(bot, true);
                }, 3L);
            } else {
                performAttack(bot, false);
            }
        }
        
        lastAttackTime = now;
    }

    private void handleMaceAttack(Player bot, double distance) {
        long now = System.currentTimeMillis();
        
        // Mace is most effective with fall damage - but human-like jumps
        if (bot.isOnGround() && now - lastMaceJump > 2500) {
            // Normal human jump power (vanilla jump is 0.42)
            double jumpPower = switch(difficulty) {
                case EASY -> 0.42;    // Normal jump
                case MEDIUM -> 0.45;  // Slightly higher
                case HARD -> 0.48;    // Human-like
                case HACKER -> 0.6;   // Only hacker jumps high
            };
            
            // Jump towards target with vanilla-like velocity
            Vector direction = target.getLocation().toVector()
                    .subtract(bot.getLocation().toVector()).normalize();
            direction.setY(jumpPower);
            direction.multiply(0.4); // Modest horizontal speed
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
        
        // Apply knockback
        applyKnockback(bot, target);
        
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
     * Apply vanilla-like knockback to victim.
     * Based on Minecraft Wiki knockback mechanics:
     * - Base horizontal: 0.4 blocks/tick
     * - Base vertical: 0.4 blocks/tick (lifts off ground)
     * - Sprint bonus: +0.4 horizontal (no stack with crit)
     * - Knockback enchant: +0.5 per level
     */
    private void applyKnockback(Player attacker, Player victim) {
        // Direction from attacker to victim
        Vector direction = victim.getLocation().toVector()
                .subtract(attacker.getLocation().toVector());
        direction.setY(0); // Only horizontal for direction
        direction.normalize();
        
        // Vanilla base knockback values
        double kbHorizontal = 0.4;
        double kbVertical = 0.4;
        
        // Sprint bonus (only if sprinting, doesn't stack with other bonuses excessively)
        if (attacker.isSprinting()) {
            kbHorizontal += 0.4;
        }
        
        // Knockback enchantment (+0.5 per level, vanilla value)
        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        if (weapon != null && weapon.hasItemMeta()) {
            int kbLevel = weapon.getEnchantmentLevel(Enchantment.KNOCKBACK);
            kbHorizontal += kbLevel * 0.5;
        }
        
        // Build final knockback vector
        Vector knockback = direction.multiply(kbHorizontal);
        knockback.setY(kbVertical);
        
        // Apply - don't add to existing velocity, replace it (vanilla behavior)
        victim.setVelocity(knockback);
    }

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
        
        // Chase to melee range
        npc.getNavigator().setTarget(target, true);
        
        // Sprint
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
        
        // Default: chase
        npc.getNavigator().setTarget(target, true);
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
        bot.getInventory().setHeldItemSlot(bowSlot);
        
        // Predict target location
        Location predictedLoc = predictTargetLocation(target, distance);
        lookAt(bot, predictedLoc);
        
        // Charge time based on difficulty (full charge = 20 ticks)
        int chargeTime = switch(difficulty) {
            case EASY -> 25;
            case MEDIUM -> 20;
            case HARD -> 15;
            case HACKER -> 8;
        };
        
        // Simulate bow draw and release
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!isValid()) return;
            
            // Spawn arrow
            Arrow arrow = bot.getWorld().spawn(bot.getEyeLocation(), Arrow.class);
            
            // Calculate velocity with arc
            Vector velocity = calculateArrowVelocity(bot.getEyeLocation(), predictedLoc, distance);
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
        Vector direction = to.toVector().subtract(from.toVector());
        
        // Add arc for distance
        double arc = Math.min(distance * 0.02, 0.3);
        direction.setY(direction.getY() + arc);
        
        // Normalize and scale
        direction.normalize().multiply(Math.min(3.0, 1.5 + distance * 0.05));
        
        return direction;
    }

    private void handleElytraApproach(Player bot) {
        long now = System.currentTimeMillis();
        if (now - lastElytraUse < 3000) return;
        
        // Check if wearing elytra
        ItemStack chestplate = bot.getInventory().getChestplate();
        if (chestplate == null || chestplate.getType() != Material.ELYTRA) return;
        
        // Jump and glide towards target
        if (bot.isOnGround()) {
            // Launch
            Vector direction = target.getLocation().toVector()
                    .subtract(bot.getLocation().toVector())
                    .normalize();
            direction.setY(0.8);
            direction.multiply(1.5);
            bot.setVelocity(direction);
            
            // Start gliding after jump
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (isValid()) {
                    bot.setGliding(true);
                    isUsingElytra = true;
                    
                    // Boost with firework if available
                    if (hasFireworks(bot)) {
                        useFireworkBoost(bot);
                    }
                }
            }, 5L);
            
            lastElytraUse = now;
            
            // Stop gliding when close
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (isValid()) {
                    bot.setGliding(false);
                    isUsingElytra = false;
                }
            }, 40L);
        }
    }

    private void useFireworkBoost(Player bot) {
        int slot = findItem(bot.getInventory(), Material.FIREWORK_ROCKET);
        if (slot == -1) return;
        
        ItemStack firework = bot.getInventory().getItem(slot);
        if (firework == null) return;
        
        // Boost velocity
        Vector boost = bot.getLocation().getDirection().multiply(1.5);
        bot.setVelocity(bot.getVelocity().add(boost));
        
        // Consume firework
        firework.setAmount(firework.getAmount() - 1);
        
        // Sound
        bot.getWorld().playSound(bot.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 1.0f);
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
                
                // Retreat if low health
                if (healthPercent < 30 && !isEating) {
                    if (!isRetreating && random.nextDouble() < 0.5) {
                        isRetreating = true;
                        retreat(bot);
                    }
                } else {
                    isRetreating = false;
                }
                
                // Strafe in combat
                if (distance <= 5 && !isRetreating) {
                    performStrafe(bot);
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
        }.runTaskTimer(plugin, 2L, 4L);
    }

    private void performStrafe(Player bot) {
        long now = System.currentTimeMillis();
        
        // Change strafe direction periodically
        if (now - lastStrafeChange > 500 && random.nextDouble() < 0.3) {
            strafeDirection *= -1;
            lastStrafeChange = now;
        }
        
        // Calculate strafe vector (perpendicular to facing)
        Vector strafe = bot.getLocation().getDirection()
                .crossProduct(new Vector(0, 1, 0))
                .normalize()
                .multiply(0.25 * strafeDirection);
        
        // Apply strafe
        Vector currentVel = bot.getVelocity();
        currentVel.add(strafe);
        bot.setVelocity(currentVel);
    }

    private boolean shouldWTap() {
        return (difficulty == BotDifficulty.HARD || difficulty == BotDifficulty.HACKER)
                && random.nextDouble() < 0.4;
    }

    private void performWTap(Player bot) {
        // Sprint reset for extra knockback
        bot.setSprinting(false);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (isValid()) bot.setSprinting(true);
        }, 1L);
    }

    private void retreat(Player bot) {
        // Move away from target
        Vector away = bot.getLocation().toVector()
                .subtract(target.getLocation().toVector())
                .normalize()
                .multiply(0.5);
        away.setY(0);
        
        bot.setVelocity(bot.getVelocity().add(away));
        
        // Try to heal while retreating
        tryHeal(bot);
    }

    private boolean shouldPlaceBlock(Player bot, double distance) {
        // Place blocks for tactical advantage
        if (System.currentTimeMillis() - lastBlockPlace < 500) return false;
        
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
        
        // Place block below
        below.setType(blocks.getType());
        blocks.setAmount(blocks.getAmount() - 1);
        
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

    private void tryHeal(Player bot) {
        if (isEating) return;
        
        long now = System.currentTimeMillis();
        if (now - lastHealTime < 2000) return;
        
        // Find golden apples
        int gappleSlot = findItem(bot.getInventory(), Material.GOLDEN_APPLE);
        if (gappleSlot == -1) {
            gappleSlot = findItem(bot.getInventory(), Material.ENCHANTED_GOLDEN_APPLE);
        }
        
        if (gappleSlot == -1) return;
        
        final int slot = gappleSlot;
        int originalSlot = bot.getInventory().getHeldItemSlot();
        
        // Switch to gapple
        bot.getInventory().setHeldItemSlot(slot);
        isEating = true;
        
        // Eating time based on difficulty
        int eatTime = switch(difficulty) {
            case EASY -> 32;    // Normal eating time
            case MEDIUM -> 28;
            case HARD -> 20;
            case HACKER -> 8;   // Speed eating
        };
        
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
        // Try instant health splash potions on self
        int potSlot = findHealingPotion(bot.getInventory());
        if (potSlot == -1) return;
        
        long now = System.currentTimeMillis();
        if (now - lastPotionTime < 1000) return;
        
        ItemStack potion = bot.getInventory().getItem(potSlot);
        if (potion == null) return;
        
        // Splash at feet for instant heal
        ThrownPotion thrown = bot.getWorld().spawn(bot.getEyeLocation(), ThrownPotion.class);
        thrown.setItem(potion.clone());
        thrown.setVelocity(new Vector(0, -0.5, 0)); // Down towards self
        thrown.setShooter(bot);
        
        potion.setAmount(potion.getAmount() - 1);
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
        Vector direction = target.toVector().subtract(bot.getEyeLocation().toVector()).normalize();
        Location loc = bot.getLocation().clone();
        loc.setDirection(direction);
        bot.teleport(loc);
    }

    private Location predictTargetLocation(Player target, double distance) {
        // Predict where target will be based on velocity
        Vector velocity = target.getVelocity();
        double prediction = switch(difficulty) {
            case EASY -> 0.1;
            case MEDIUM -> 0.3;
            case HARD -> 0.5;
            case HACKER -> 0.8;
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

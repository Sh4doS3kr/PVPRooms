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
    private long lastCrossbowShot = 0;
    private boolean isBowDrawing = false;
    private boolean isCrossbowLoading = false;
    private long lastMaceJump = 0;
    private long lastSpearThrow = 0;
    private long lastElytraUse = 0;
    private long lastWindChargeUse = 0;

    // MC 1.21 Attack Cooldown System
    // Vanilla attack speeds: Sword=1.6, Axe=1.0, Mace=0.6, Trident=1.1, Fist=4.0
    // Cooldown in ms = 1000 / attackSpeed
    private long getWeaponCooldownMs(Player bot) {
        ItemStack weapon = bot.getInventory().getItemInMainHand();
        if (weapon == null) return 250; // Fist: 4.0 speed
        return switch (weapon.getType()) {
            // Swords: attack speed 1.6 → 625ms
            case NETHERITE_SWORD, DIAMOND_SWORD, IRON_SWORD, STONE_SWORD,
                 WOODEN_SWORD, GOLDEN_SWORD -> 625;
            // Axes: attack speed 1.0 → 1000ms
            case NETHERITE_AXE, DIAMOND_AXE -> 1000;
            case IRON_AXE, STONE_AXE -> 1000; // iron/stone = 0.9 → ~1111ms, simplify
            case WOODEN_AXE, GOLDEN_AXE -> 1000;
            // Mace: attack speed 0.6 → 1667ms
            case MACE -> 1667;
            // Trident: attack speed 1.1 → 909ms
            case TRIDENT -> 909;
            default -> 250; // Fist
        };
    }

    /** Returns true if the weapon cooldown has elapsed and an attack will deal full damage. */
    private boolean isAttackCooldownReady(Player bot) {
        long elapsed = System.currentTimeMillis() - lastAttackTime;
        return elapsed >= getWeaponCooldownMs(bot);
    }

    // Manual fall distance tracking (NPCs may not accumulate getFallDistance correctly)
    private double manualFallStartY = -1;
    private boolean wasOnGroundLastTick = true;
    
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
    
    // Ender pearl tracking
    private long lastPearlThrow = 0;
    
    // Opponent state tracking (smarter AI)
    private double lastTargetHealth = 20.0;
    private int targetHitStreak = 0;
    private long lastTargetDamageTime = 0;
    private boolean targetIsAggressive = false;

    // Bot's own combo tracking — how many consecutive hits WE landed
    private int botComboCount = 0;
    private long lastBotHitLanded = 0;

    // Damage received tracking — detect when WE are being comboed
    private double lastBotHealth = 20.0;
    private int hitsReceivedStreak = 0;
    private long lastDamageReceivedTime = 0;
    private boolean isBeingComboed = false;

    // Post-hit chase: after landing a hit, chase harder for a short window
    private boolean postHitChase = false;
    private long postHitChaseStart = 0;

    // Reactive defense: cooldowns for emergency actions
    private long lastEmergencyBlock = 0;
    private long lastEmergencyPearl = 0;
    private long lastEmergencyRetreat = 0;

    // ── FLEE-TO-HEAL system ──
    // Triggered when totem pops or HP is critically low.
    // Bot will: pearl away → sprint retreat → eat gapple → return to fight.
    private boolean isFleeingToHeal = false;
    private long fleeStartTime = 0;
    private boolean totemJustPopped = false;
    private long totemPopTime = 0;
    private long lastEscapePearl = 0;
    
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
                case DUMMY -> 0.0;
            };
            this.healThreshold = difficulty.healThreshold;
            this.strafeChance = switch(difficulty) {
                case EASY -> 0.1;
                case MEDIUM -> 0.2;
                case HARD -> 0.45;   // Aggressive strafing
                case HACKER -> 0.65; // Constant strafing
                case ADAPTIVE -> 0.25;
                case DUMMY -> 0.0;
            };
            this.wTapChance = switch(difficulty) {
                case EASY -> 0.05;
                case MEDIUM -> 0.15;
                case HARD -> 0.35;   // Consistent W-tapping
                case HACKER -> 0.55; // Almost always W-taps
                case ADAPTIVE -> 0.15;
                case DUMMY -> 0.0;
            };
            this.jumpChance = switch(difficulty) {
                case EASY -> 0.05;
                case MEDIUM -> 0.1;
                case HARD -> 0.20;   // Sprint-jumps often
                case HACKER -> 0.35; // Sprint-jumps very often
                case ADAPTIVE -> 0.1;
                case DUMMY -> 0.0;
            };
            this.sneakChance = switch(difficulty) {
                case EASY -> 0.02;
                case MEDIUM -> 0.05;
                case HARD -> 0.15;   // KB reduction shift-tap
                case HACKER -> 0.30; // Almost always shift-taps after hit
                case ADAPTIVE -> 0.08;
                case DUMMY -> 0.0;
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

    /** Bot combat state — prevents conflicting actions */
    private enum CombatState {
        IDLE,           // Free to make decisions
        MELEE,          // In melee range, swinging
        ELYTRA_FLIGHT,  // Elytra dive/approach in progress — DO NOT INTERRUPT
        BOW_DRAW,       // Drawing bow
        CROSSBOW_LOAD,  // Loading crossbow
        EATING,         // Eating golden apple
        PEARL_THROW     // Pearl just thrown, brief cooldown
    }
    private CombatState combatState = CombatState.IDLE;

    private void startMainLoop() {
        mainTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!isValid()) { stop(); return; }

                Player bot = getBotPlayer();
                if (bot == null) return;

                double distance = bot.getLocation().distance(target.getLocation());

                // ── DUMMY MODE: do absolutely nothing — stand still for target practice ──
                if (difficulty == BotDifficulty.DUMMY) return;

                // ── NEVER interrupt these states ──
                if (combatState == CombatState.ELYTRA_FLIGHT) return; // Elytra dive in progress
                if (combatState == CombatState.BOW_DRAW)      return; // Drawing bow
                if (combatState == CombatState.CROSSBOW_LOAD)  return; // Loading crossbow
                if (combatState == CombatState.EATING)          return; // Eating
                if (isEating || isBowDrawing || isCrossbowLoading || isUsingElytra) return;

                // Update weapon type + track opponent
                updateWeaponType(bot);
                trackOpponentState(bot);

                // ── FLEE-TO-HEAL: highest priority — overrides all combat ──
                if (handleFleeToHeal(bot, distance)) return;

                // ── LOW HP FLEE TRIGGER: start fleeing if critically low ──
                if (!isFleeingToHeal && difficulty != BotDifficulty.DUMMY
                        && difficulty != BotDifficulty.EASY) {
                    double hp = (bot.getHealth() / bot.getMaxHealth()) * 100;
                    boolean hasHealing = findItem(bot.getInventory(), Material.GOLDEN_APPLE) != -1
                            || findItem(bot.getInventory(), Material.ENCHANTED_GOLDEN_APPLE) != -1
                            || findHealingPotion(bot.getInventory()) != -1;
                    // Flee at ≤15% HP if we have healing items
                    double fleeChance = switch (difficulty) {
                        case MEDIUM   -> 0.08;
                        case HARD     -> 0.20;
                        case HACKER   -> 0.40;
                        case ADAPTIVE -> 0.12;
                        default -> 0.0;
                    };
                    if (hp <= 15 && hasHealing && random.nextDouble() < fleeChance) {
                        startFleeToHeal();
                        return;
                    }
                }

                // ── REACTIVE DEFENSE: respond to being comboed ──
                if (handleReactiveDefense(bot, distance)) return;

                // Shield logic (reactive — only in close combat)
                handleShieldLogic(bot, distance);

                // ── Distance-based combat decisions ──
                if (distance <= 4.0) {
                    handleMeleeCombat(bot, distance);
                } else if (distance <= 8.0) {
                    handleMidRangeCombat(bot, distance);
                } else if (distance <= 50.0) {
                    // Long range: elytra approach or pearl to close gap, then ranged
                    // Pearl ONLY when not using elytra and with tight cooldowns
                    if (!isUsingElytra && distance > 15 && distance <= 40
                            && shouldThrowPearl(bot, distance)) {
                        throwEnderPearl(bot, distance);
                        return;
                    }
                    handleLongRangeCombat(bot, distance);
                } else {
                    // Very far (>50 blocks) — elytra approach or sprint
                    if (hasElytra(bot) && hasFireworks(bot) && !isUsingElytra) {
                        handleElytraApproach(bot);
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, tickRate);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MELEE COMBAT (distance <= 4)
    // ══════════════════════════════════════════════════════════════════════════

    private void handleMeleeCombat(Player bot, double distance) {
        long now = System.currentTimeMillis();

        // MC 1.21 attack cooldown — NEVER attack before cooldown is ready
        // This prevents spam-clicking which does almost no damage
        if (!isAttackCooldownReady(bot)) return;
        // Also respect reaction time (human delay on top of cooldown)
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
    // OPPONENT STATE TRACKING (Smarter AI)
    // ══════════════════════════════════════════════════════════════════════════

    private void trackOpponentState(Player bot) {
        double currentTargetHealth = target.getHealth();
        double currentBotHealth = bot.getHealth();
        long now = System.currentTimeMillis();

        // ── Track when WE land hits on the target ──
        if (currentTargetHealth < lastTargetHealth - 0.5) {
            lastTargetDamageTime = now;
            // We landed a hit — increment our combo
            if (now - lastBotHitLanded < 1200) {
                botComboCount++;
            } else {
                botComboCount = 1;
            }
            lastBotHitLanded = now;

            // Post-hit chase: after landing a hit, be aggressive for a window
            postHitChase = true;
            postHitChaseStart = now;
        }

        // Expire post-hit chase after 800ms
        if (postHitChase && now - postHitChaseStart > 800) {
            postHitChase = false;
        }

        // ── Track when WE receive damage (being comboed) ──
        if (currentBotHealth < lastBotHealth - 0.5) {
            if (now - lastDamageReceivedTime < 1000) {
                hitsReceivedStreak++;
            } else {
                hitsReceivedStreak = 1;
            }
            lastDamageReceivedTime = now;
        }

        // Being comboed = 3+ hits in quick succession
        isBeingComboed = hitsReceivedStreak >= 3 && (now - lastDamageReceivedTime < 1500);

        // Decay hit streak if no damage for a while
        if (now - lastDamageReceivedTime > 2000) {
            hitsReceivedStreak = 0;
            isBeingComboed = false;
        }

        // Reset our combo if we haven't landed a hit recently
        if (now - lastBotHitLanded > 1500) {
            botComboCount = 0;
        }

        // Detect if target is aggressive
        targetIsAggressive = (now - lastDamageReceivedTime < 2000) || target.isSprinting();

        // Track target hit streak (old behavior, refined)
        if (currentBotHealth < lastBotHealth - 0.5 && targetIsAggressive) {
            targetHitStreak++;
        } else if (now - lastDamageReceivedTime > 3000) {
            targetHitStreak = Math.max(0, targetHitStreak - 1);
        }

        lastTargetHealth = currentTargetHealth;
        lastBotHealth = currentBotHealth;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // REACTIVE DEFENSE — Combo escape, emergency actions
    //
    // When the bot detects it's being comboed (3+ hits in quick succession),
    // it reacts with pro-level techniques:
    //   1. Emergency shield raise (instant block to absorb next hit)
    //   2. Emergency retreat (sprint backwards + heal)
    //   3. Emergency pearl (pearl behind target to escape and counter-attack)
    //   4. Strafe burst (sudden direction change to break the combo)
    //
    // Higher difficulties react faster and more consistently.
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Check if the bot should perform a reactive defense action.
     * @return true if the bot performed a defense action this tick (skip combat)
     */
    private boolean handleReactiveDefense(Player bot, double distance) {
        long now = System.currentTimeMillis();
        double healthPercent = (bot.getHealth() / bot.getMaxHealth()) * 100;

        // Only react defensively if we've recently taken damage
        if (now - lastDamageReceivedTime > 1500) return false;
        if (difficulty == BotDifficulty.DUMMY || difficulty == BotDifficulty.EASY) return false;

        // ── CRITICAL HEALTH EMERGENCY: heal IMMEDIATELY ──
        if (healthPercent <= 15 && !isEating) {
            double emergencyHealChance = switch (difficulty) {
                case MEDIUM   -> 0.3;
                case HARD     -> 0.6;
                case HACKER   -> 0.9;
                case ADAPTIVE -> 0.4;
                default -> 0.0;
            };
            if (random.nextDouble() < emergencyHealChance) {
                tryHeal(bot);
                if (isEating) {
                    // Also retreat while eating
                    isRetreating = true;
                    return true;
                }
            }
        }

        // ── BEING COMBOED: react based on difficulty ──
        if (isBeingComboed) {
            // Option 1: Emergency shield raise (fastest reaction)
            if (now - lastEmergencyBlock > 3000 && distance <= 4.0) {
                double blockReactChance = switch (difficulty) {
                    case MEDIUM   -> 0.15;
                    case HARD     -> 0.35;
                    case HACKER   -> 0.60;
                    case ADAPTIVE -> 0.25;
                    default -> 0.0;
                };
                ItemStack offhand = bot.getInventory().getItemInOffHand();
                if (offhand != null && offhand.getType() == Material.SHIELD
                        && random.nextDouble() < blockReactChance && !isBlocking) {
                    isBlocking = true;
                    setShieldBlockVisual(bot, true);
                    lastShieldRaise = now;
                    lastEmergencyBlock = now;
                    // Quick block: release after 5-8 ticks (just enough to absorb one hit)
                    int holdTicks = 5 + random.nextInt(3);
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (isValid()) {
                            isBlocking = false;
                            Player b = getBotPlayer();
                            if (b != null) setShieldBlockVisual(b, false);
                        }
                    }, holdTicks);
                    return true;
                }
            }

            // Option 2: Emergency strafe burst (change direction suddenly)
            if (distance <= 5.0) {
                double strafeBurstChance = switch (difficulty) {
                    case MEDIUM   -> 0.10;
                    case HARD     -> 0.30;
                    case HACKER   -> 0.50;
                    case ADAPTIVE -> 0.20;
                    default -> 0.0;
                };
                if (random.nextDouble() < strafeBurstChance) {
                    // Sudden strafe direction change
                    strafeDirection *= -1;
                    lastStrafeChange = now;
                    // Apply lateral velocity burst
                    Vector look = bot.getLocation().getDirection();
                    look.setY(0).normalize();
                    Vector strafe = new Vector(-look.getZ(), 0, look.getX())
                            .multiply(0.25 * strafeDirection);
                    bot.setVelocity(bot.getVelocity().add(strafe));
                    return false; // Don't skip combat, just added movement
                }
            }

            // Option 3: Emergency retreat when low HP and being comboed
            if (healthPercent <= 40 && now - lastEmergencyRetreat > 4000) {
                double retreatChance = switch (difficulty) {
                    case MEDIUM   -> 0.10;
                    case HARD     -> 0.25;
                    case HACKER   -> 0.45;
                    case ADAPTIVE -> 0.15;
                    default -> 0.0;
                };
                if (random.nextDouble() < retreatChance) {
                    isRetreating = true;
                    lastEmergencyRetreat = now;
                    retreat(bot);
                    return true;
                }
            }
        }

        // ── TAKING HEAVY DAMAGE but not comboed: consider retreat at critical HP ──
        if (healthPercent <= 25 && hitsReceivedStreak >= 2) {
            double critRetreatChance = switch (difficulty) {
                case MEDIUM   -> 0.05;
                case HARD     -> 0.20;
                case HACKER   -> 0.40;
                case ADAPTIVE -> 0.10;
                default -> 0.0;
            };
            if (random.nextDouble() < critRetreatChance && now - lastEmergencyRetreat > 5000) {
                isRetreating = true;
                lastEmergencyRetreat = now;
                retreat(bot);
                tryHeal(bot);
                return true;
            }
        }

        return false;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ENDER PEARL COMBAT
    // ══════════════════════════════════════════════════════════════════════════

    private boolean shouldThrowPearl(Player bot, double distance) {
        // NEVER pearl during elytra flight or any special state
        if (isUsingElytra || elytraDiveActive) return false;
        if (combatState != CombatState.IDLE) return false;

        long now = System.currentTimeMillis();
        if (now - lastPearlThrow < 8000) return false; // 8s cooldown (pearls are precious)
        if (findItem(bot.getInventory(), Material.ENDER_PEARL) == -1) return false;

        // Low chance — pro players don't spam pearls
        double pearlChance = switch (difficulty) {
            case EASY     -> 0.005;
            case MEDIUM   -> 0.015;
            case HARD     -> 0.04;
            case HACKER   -> 0.08;
            case ADAPTIVE -> 0.03;
            case DUMMY    -> 0.0;
        };

        // Only pearl if target is running away AND far
        if (distance > 25 && target.isSprinting()) pearlChance *= 2.0;

        return random.nextDouble() < pearlChance;
    }

    private void throwEnderPearl(Player bot, double distance) {
        int pearlSlot = findItem(bot.getInventory(), Material.ENDER_PEARL);
        if (pearlSlot == -1) return;

        pearlSlot = hotbarSlot(bot.getInventory(), pearlSlot);
        if (pearlSlot == -1) return;

        int originalSlot = bot.getInventory().getHeldItemSlot();
        bot.getInventory().setHeldItemSlot(pearlSlot);

        // Aim at target with slight upward arc
        Location predicted = predictTargetLocation(target, distance);
        Vector direction = predicted.toVector().subtract(bot.getEyeLocation().toVector()).normalize();

        // Add arc for distance (pearls are affected by gravity)
        double arcCompensation = Math.min(distance * 0.02, 0.4);
        direction.setY(direction.getY() + arcCompensation);
        direction.normalize();

        // Accuracy jitter
        double jitter = switch (difficulty) {
            case EASY     -> 0.15;
            case MEDIUM   -> 0.08;
            case HARD     -> 0.03;
            case HACKER   -> 0.01;
            case ADAPTIVE -> 0.05;
            case DUMMY    -> 0.0;
        };
        direction.add(new Vector(
            (random.nextDouble() - 0.5) * jitter,
            (random.nextDouble() - 0.5) * jitter * 0.3,
            (random.nextDouble() - 0.5) * jitter
        )).normalize();

        // Look at throw direction
        float yaw   = (float) Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ()));
        float pitch = (float) Math.toDegrees(Math.atan2(-direction.getY(),
                Math.sqrt(direction.getX() * direction.getX() + direction.getZ() * direction.getZ())));
        bot.setRotation(yaw, pitch);
        currentBotYaw = yaw;
        currentBotPitch = pitch;

        // Throw the pearl
        org.bukkit.entity.EnderPearl pearl = bot.getWorld().spawn(
                bot.getEyeLocation(), org.bukkit.entity.EnderPearl.class);
        pearl.setVelocity(direction.multiply(2.0));
        pearl.setShooter(bot);

        // Consume pearl
        ItemStack pearlItem = bot.getInventory().getItem(bot.getInventory().getHeldItemSlot());
        if (pearlItem != null) pearlItem.setAmount(pearlItem.getAmount() - 1);

        bot.swingMainHand();
        bot.getWorld().playSound(bot.getLocation(), Sound.ENTITY_ENDER_PEARL_THROW, 1.0f, 1.0f);

        lastPearlThrow = System.currentTimeMillis();

        // Switch back to weapon
        final int fOriginalSlot = originalSlot;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (isValid()) getBotPlayer().getInventory().setHeldItemSlot(fOriginalSlot);
        }, 2L);
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
            case DUMMY -> 9999;
        };
        int anchorCooldown = switch(difficulty) {
            case EASY -> 1200;
            case MEDIUM -> 800;
            case HARD -> 500;
            case HACKER -> 250;
            case ADAPTIVE -> 600;
            case DUMMY -> 9999;
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
        // DISABLED — bots must NEVER place blocks in the arena
    }

    private void useRespawnAnchor(Player bot, double distance) {
        int anchorSlot = findItem(bot.getInventory(), Material.RESPAWN_ANCHOR);
        int glowstoneSlot = findItem(bot.getInventory(), Material.GLOWSTONE);
        if (anchorSlot == -1 || glowstoneSlot == -1) return;

        final int finalAnchorSlot = hotbarSlot(bot.getInventory(), anchorSlot);
        final int finalGlowstoneSlot = hotbarSlot(bot.getInventory(), glowstoneSlot);
        if (finalAnchorSlot == -1 || finalGlowstoneSlot == -1) return;

        int swordSlot = findSwordSlot(bot);

        isCrystalAction = true;
        bot.getInventory().setHeldItemSlot(finalAnchorSlot);

        // Simulate anchor placement + charge + explosion WITHOUT modifying blocks.
        // We consume items and create a non-destructive explosion near the target.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!isValid()) { isCrystalAction = false; return; }
            // Consume anchor item
            ItemStack anchor = bot.getInventory().getItem(finalAnchorSlot);
            if (anchor != null) anchor.setAmount(anchor.getAmount() - 1);
            bot.swingMainHand();

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!isValid()) { isCrystalAction = false; return; }
                bot.getInventory().setHeldItemSlot(finalGlowstoneSlot);

                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!isValid()) { isCrystalAction = false; return; }
                    // Consume glowstone
                    ItemStack glowstone = bot.getInventory().getItem(finalGlowstoneSlot);
                    if (glowstone != null) glowstone.setAmount(glowstone.getAmount() - 1);

                    // Explosion near target — NO fire, NO block breaking
                    Location explosionLoc = target.getLocation().clone().add(0, 0.5, 0);
                    bot.getWorld().createExplosion(explosionLoc, 5.0f, false, false);

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
            case HARD     -> 0.28;  // Blocks often
            case HACKER   -> 0.50;  // Very reactive blocking
            case ADAPTIVE -> 0.18;
            case DUMMY    -> 0.0;
        };

        if (!isBlocking && random.nextDouble() < blockChance) {
            isBlocking = true;
            setShieldBlockVisual(bot, true);
            lastShieldRaise = now;
        } else if (isBlocking) {
            long holdTime = switch (difficulty) {
                case EASY     -> 600;
                case MEDIUM   -> 900;
                case HARD     -> 1600;  // Holds shield longer
                case HACKER   -> 2000;  // Very patient blocking
                case ADAPTIVE -> 1000;
                case DUMMY    -> 100;
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

    // ══════════════════════════════════════════════════════════════════════════
    // FLEE-TO-HEAL SYSTEM
    //
    // Triggered when:
    //   1. Totem of Undying activates (bot was about to die)
    //   2. Health drops critically low (≤15%) and bot has healing items
    //
    // Behavior:
    //   - Throw ender pearl AWAY from the target (escape pearl)
    //   - Sprint retreat at max speed
    //   - Eat golden apple / use potions while running
    //   - After healing enough (or time expires), return to fight
    //
    // This mimics how real PvP players "run" after totem pop to regen.
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Called by BotListener when the bot's totem activates.
     * Triggers the flee-to-heal sequence.
     */
    public void onTotemPop() {
        totemJustPopped = true;
        totemPopTime = System.currentTimeMillis();

        // Immediately start fleeing (unless dummy/easy)
        if (difficulty != BotDifficulty.DUMMY && difficulty != BotDifficulty.EASY) {
            startFleeToHeal();
        }
    }

    /**
     * Begin the flee-to-heal sequence.
     * Bot will try to pearl away, then sprint retreat while healing.
     */
    private void startFleeToHeal() {
        if (isFleeingToHeal) return; // Already fleeing

        isFleeingToHeal = true;
        isRetreating = true;
        fleeStartTime = System.currentTimeMillis();

        Player bot = getBotPlayer();
        if (bot == null) return;

        // Drop shield if blocking — need to run, not block
        if (isBlocking) {
            isBlocking = false;
            setShieldBlockVisual(bot, false);
        }

        // ── Try escape pearl first (throw AWAY from target) ──
        long now = System.currentTimeMillis();
        if (now - lastEscapePearl > 5000 && now - lastPearlThrow > 3000) {
            boolean threwPearl = throwEscapePearl(bot);
            if (threwPearl) {
                lastEscapePearl = now;
                lastPearlThrow = now;
            }
        }

        // ── Start eating immediately while fleeing ──
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!isValid()) return;
            Player b = getBotPlayer();
            if (b == null) return;
            tryHeal(b);
        }, 3L); // Small delay to let pearl throw finish

        // ── Auto-end fleeing after a duration (so bot returns to fight) ──
        int fleeDurationTicks = switch (difficulty) {
            case MEDIUM   -> 60;  // 3 seconds
            case HARD     -> 80;  // 4 seconds
            case HACKER   -> 100; // 5 seconds (heals fully before returning)
            case ADAPTIVE -> 70;
            default -> 40;        // 2 seconds
        };

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            isFleeingToHeal = false;
            isRetreating = false;
            totemJustPopped = false;
        }, fleeDurationTicks);
    }

    /**
     * Throw an ender pearl AWAY from the target to escape.
     * Unlike offensive pearls, this aims BEHIND the bot (away from opponent).
     * @return true if pearl was successfully thrown
     */
    private boolean throwEscapePearl(Player bot) {
        int pearlSlot = findItem(bot.getInventory(), Material.ENDER_PEARL);
        if (pearlSlot == -1) return false;

        pearlSlot = hotbarSlot(bot.getInventory(), pearlSlot);
        if (pearlSlot == -1) return false;

        int originalSlot = bot.getInventory().getHeldItemSlot();
        bot.getInventory().setHeldItemSlot(pearlSlot);

        // Direction AWAY from target (opposite of target direction)
        Vector awayDir = bot.getLocation().toVector()
                .subtract(target.getLocation().toVector());
        awayDir.setY(0);
        if (awayDir.lengthSquared() < 0.001) {
            // Same position — pick random direction
            awayDir = new Vector(random.nextDouble() - 0.5, 0, random.nextDouble() - 0.5);
        }
        awayDir.normalize();

        // Add slight sideways offset so bot doesn't pearl into a wall directly behind
        Vector sideways = new Vector(-awayDir.getZ(), 0, awayDir.getX());
        awayDir.add(sideways.multiply(0.2 * (random.nextBoolean() ? 1 : -1)));
        awayDir.normalize();

        // Add upward arc for distance (aim ~15-25 blocks away)
        awayDir.setY(0.35);
        awayDir.normalize();

        // Look in escape direction
        float yaw = (float) Math.toDegrees(Math.atan2(-awayDir.getX(), awayDir.getZ()));
        float pitch = (float) Math.toDegrees(Math.atan2(-awayDir.getY(),
                Math.sqrt(awayDir.getX() * awayDir.getX() + awayDir.getZ() * awayDir.getZ())));
        bot.setRotation(yaw, pitch);
        currentBotYaw = yaw;
        currentBotPitch = pitch;

        // Spawn and throw the pearl
        org.bukkit.entity.EnderPearl pearl = bot.getWorld().spawn(
                bot.getEyeLocation(), org.bukkit.entity.EnderPearl.class);
        pearl.setVelocity(awayDir.multiply(2.0));
        pearl.setShooter(bot);

        // Consume pearl
        ItemStack pearlItem = bot.getInventory().getItem(bot.getInventory().getHeldItemSlot());
        if (pearlItem != null) pearlItem.setAmount(pearlItem.getAmount() - 1);

        bot.swingMainHand();
        bot.getWorld().playSound(bot.getLocation(), Sound.ENTITY_ENDER_PEARL_THROW, 1.0f, 1.0f);

        // Switch back to weapon
        final int fOriginalSlot = originalSlot;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (isValid()) getBotPlayer().getInventory().setHeldItemSlot(fOriginalSlot);
        }, 2L);

        return true;
    }

    /**
     * Handle the bot's behavior while fleeing to heal.
     * Called every tick from the main combat loop.
     * @return true if the bot is fleeing (skip normal combat)
     */
    private boolean handleFleeToHeal(Player bot, double distance) {
        if (!isFleeingToHeal) return false;

        long now = System.currentTimeMillis();
        double healthPercent = (bot.getHealth() / bot.getMaxHealth()) * 100;

        // ── If healed enough, stop fleeing early and return to fight ──
        if (healthPercent >= 70) {
            isFleeingToHeal = false;
            isRetreating = false;
            totemJustPopped = false;
            return false;
        }

        // ── Keep trying to heal while fleeing ──
        if (!isEating && now - fleeStartTime > 500) {
            tryHeal(bot);
            // Also try potions
            if (!isEating) {
                tryUsePotions(bot);
            }
        }

        // ── If pearl is on cooldown and we're still close, try another escape pearl ──
        if (distance <= 5.0 && now - lastEscapePearl > 5000 && now - lastPearlThrow > 3000) {
            boolean threwPearl = throwEscapePearl(bot);
            if (threwPearl) {
                lastEscapePearl = now;
                lastPearlThrow = now;
            }
        }

        // ── Sprint away from target ──
        retreat(bot);

        // ── Look behind occasionally (pro players check if being chased) ──
        if (random.nextDouble() < 0.15) {
            lookAt(bot, target.getLocation());
        }

        return true; // Skip normal combat while fleeing
    }

    private void handleSwordAttack(Player bot, double distance) {
        if (distance > 3.5) return;
        if (!hasLineOfSight(bot, target)) return;
        if (!isAttackCooldownReady(bot)) return; // MC 1.21 cooldown
        
        long now = System.currentTimeMillis();
        
        // If target is blocking with shield, switch to axe to break it
        if (target.isBlocking() && hasAxe(bot)) {
            switchToAxe(bot);
            handleAxeShieldBreak(bot);
            lastAttackTime = now;
            return;
        }
        
        // W-tap or D-tap for extra knockback (sprint reset) - only if target not blocking
        if (!target.isBlocking()) {
            if (shouldWTap()) {
                performWTap(bot);
            } else if (shouldDTap()) {
                performDTap(bot);
            }
        }
        
        // Attempt critical hit — higher difficulties crit much more consistently
        double critAttemptChance = switch(difficulty) {
            case EASY -> 0.15;
            case MEDIUM -> 0.30;
            case HARD -> 0.55;   // Crits often
            case HACKER -> 0.80; // Crits almost every hit
            case ADAPTIVE -> critChance;
            case DUMMY -> 0.0;
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
        if (!isAttackCooldownReady(bot)) return; // MC 1.21 cooldown (axe = 1000ms)
        
        long now = System.currentTimeMillis();
        
        // W-tap or D-tap for extra knockback — axe PvP relies on spacing
        if (!target.isBlocking()) {
            if (shouldWTap()) {
                performWTap(bot);
            } else if (shouldDTap()) {
                performDTap(bot);
            }
        }
        
        // Axe PvP is ALL about critical hits — much higher crit rates than sword
        // Pros crit almost every single axe hit because the slow cooldown gives time to jump
        double axeCritChance = switch(difficulty) {
            case EASY -> 0.40;
            case MEDIUM -> 0.65;
            case HARD -> 0.85;   // Crits almost every hit
            case HACKER -> 0.98; // Virtually always crits
            case ADAPTIVE -> Math.min(critChance + 0.25, 0.95);
            case DUMMY -> 0.05;
        };
        
        // If target is blocking with shield — ALWAYS crit to maximize shield disable chance
        if (target.isBlocking()) axeCritChance = Math.max(axeCritChance, 0.95);
        
        if (bot.isOnGround() && random.nextDouble() < axeCritChance && now - lastJump > 600) {
            bot.setVelocity(bot.getVelocity().add(new Vector(0, 0.42, 0)));
            lastJump = now;
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

        // ── Priority 1: Elytra+Mace dive (maximum damage, long range OK) ──
        if (hasElytra(bot) && hasFireworks(bot) && now - lastMaceJump > 5000 && distance > 4) {
            handleElytraMaceCombo(bot);
            lastMaceJump = now;
            return;
        }

        // ── Priority 2: Wind charge + mace combo (best close-range mace move) ──
        if (hasWindCharges(bot) && bot.isOnGround() && now - lastWindChargeUse > 4000
                && distance <= 8 && distance > 2) {
            performWindChargeMaceCombo(bot);
            return;
        }

        // ── Priority 3: Normal jump + mace smash ──
        if (bot.isOnGround() && now - lastMaceJump > 3000 && distance <= 5) {
            // Look at target before jumping
            lookAt(bot, target.getLocation());
            // Jump toward target — mace needs fall distance for damage
            Vector direction = target.getLocation().toVector()
                    .subtract(bot.getLocation().toVector()).normalize();
            direction.setY(0.42);
            direction.multiply(0.5);
            bot.setVelocity(direction);
            manualFallStartY = bot.getLocation().getY() + 1.25; // Approximate jump peak
            lastMaceJump = now;

            // Wait for falling phase, then smash
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!isValid()) return;
                Player b = getBotPlayer();
                if (b == null) return;
                double fallDist = getManualFallDistance(b);
                if (fallDist > 0.5 || b.getFallDistance() > 0.5) {
                    performMaceSmash(b);
                }
            }, 8L);
            return;
        }

        // ── Priority 4: Already falling — smash immediately! ──
        double fallDist = getManualFallDistance(bot);
        if ((fallDist > 0.5 || bot.getFallDistance() > 0.5) && distance <= 4.5) {
            performMaceSmash(bot);
            return;
        }

        // ── Priority 5: Ground attack (mace has slow cooldown, make it count) ──
        if (distance <= 3.0 && isAttackCooldownReady(bot)) {
            lookAt(bot, target.getLocation()); // Always face target with mace
            performAttack(bot, false);
            lastAttackTime = now;
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // WIND CHARGE + MACE COMBO
    // ══════════════════════════════════════════════════════════════════════════
    //
    // Wind charge launched at bot's own feet launches it ~7-8 blocks up.
    // Bot then free-falls with mace for massive smash damage.
    // This is the META mace combo in MC 1.21.
    // ══════════════════════════════════════════════════════════════════════════

    private void performWindChargeMaceCombo(Player bot) {
        int windSlot = findWindCharge(bot.getInventory());
        if (windSlot == -1) return;

        // Equip mace first
        int maceSlot = findMace(bot.getInventory());
        if (maceSlot != -1) {
            maceSlot = hotbarSlot(bot.getInventory(), maceSlot);
            if (maceSlot != -1) bot.getInventory().setHeldItemSlot(maceSlot);
        }

        // Lock state
        combatState = CombatState.ELYTRA_FLIGHT;
        lastWindChargeUse = System.currentTimeMillis();

        // Look at target before launching
        lookAt(bot, target.getLocation());

        // Consume wind charge from inventory
        ItemStack windItem = bot.getInventory().getItem(windSlot);
        if (windItem != null) windItem.setAmount(windItem.getAmount() - 1);

        // ── Spawn a REAL WindCharge entity at the bot's feet for visuals ──
        // BUT also apply manual velocity since explosions don't affect Citizens NPCs properly
        Location feetLoc = bot.getLocation().clone();
        try {
            org.bukkit.entity.WindCharge windCharge = bot.getWorld().spawn(
                    feetLoc, org.bukkit.entity.WindCharge.class, wc -> {
                        wc.setShooter(bot);
                        // Set downward velocity so it hits the ground immediately
                        wc.setVelocity(new Vector(0, -1.0, 0));
                    });
            // Detonate after 1 tick so it explodes at the bot's feet
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (windCharge.isValid()) {
                    // Explode at bot's feet — visual effect only
                    windCharge.explode();
                }
            }, 1L);
        } catch (Exception e) {
            // WindCharge entity not available - just use sound
            bot.getWorld().playSound(bot.getLocation(), Sound.ENTITY_WIND_CHARGE_THROW, 1.0f, 1.0f);
        }

        // ALWAYS apply manual launch since Citizens NPCs don't get launched by explosions
        // Vanilla wind charge at own feet: ~1.0 upward velocity (~5-6 blocks height)
        Vector launch = new Vector(0, 1.0, 0);
        // Slight horizontal toward target so we land near them
        Vector toTarget = target.getLocation().toVector()
                .subtract(bot.getLocation().toVector());
        toTarget.setY(0);
        if (toTarget.lengthSquared() > 0.01) {
            toTarget.normalize().multiply(0.2); // Vanilla: small horizontal push
            launch.add(toTarget);
        }
        bot.setVelocity(launch);
        
        // Wind charge visual + sound
        bot.getWorld().playSound(bot.getLocation(), Sound.ENTITY_WIND_CHARGE_THROW, 1.0f, 1.0f);
        bot.getWorld().spawnParticle(Particle.CLOUD, bot.getLocation(), 20, 0.3, 0.1, 0.3, 0.05);

        // Track the peak Y for manual fall distance
        manualFallStartY = bot.getLocation().getY();

        // Look at target during ascent (every few ticks)
        for (int tick = 3; tick <= 12; tick += 3) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!isValid()) { combatState = CombatState.IDLE; return; }
                Player b = getBotPlayer();
                if (b == null) return;
                lookAt(b, target.getLocation());
                // Update peak Y
                if (b.getLocation().getY() > manualFallStartY) {
                    manualFallStartY = b.getLocation().getY();
                }
            }, tick);
        }

        // At peak (~15 ticks), record actual peak and aim at target
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!isValid()) { combatState = CombatState.IDLE; return; }
            Player b = getBotPlayer();
            if (b == null) { combatState = CombatState.IDLE; return; }
            lookAt(b, target.getLocation());
            manualFallStartY = b.getLocation().getY();
        }, 15L);

        // During fall, track target and smash on contact (tick 17-55)
        for (int tick = 17; tick <= 55; tick += 2) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!isValid() || combatState != CombatState.ELYTRA_FLIGHT) return;
                Player b = getBotPlayer();
                if (b == null) return;

                // ALWAYS look at target during mace dive
                lookAt(b, target.getLocation());

                double dist = b.getLocation().distance(target.getLocation());
                double bestFall = Math.max(getManualFallDistance(b), b.getFallDistance());

                if (dist <= 4.5 && bestFall > 1.5) {
                    lookAt(b, target.getLocation()); // Face target for the smash
                    performMaceSmash(b);
                    combatState = CombatState.IDLE;
                    return;
                }

                if (b.isOnGround()) {
                    combatState = CombatState.IDLE;
                }
            }, tick);
        }

        // Safety unlock
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (combatState == CombatState.ELYTRA_FLIGHT) combatState = CombatState.IDLE;
        }, 60L);
    }

    /** Get manual fall distance (distance fallen from peak Y). */
    private double getManualFallDistance(Player bot) {
        if (manualFallStartY <= 0) return bot.getFallDistance();
        double currentY = bot.getLocation().getY();
        if (currentY >= manualFallStartY) return 0;
        return manualFallStartY - currentY;
    }

    private boolean hasWindCharges(Player bot) {
        return findWindCharge(bot.getInventory()) != -1;
    }

    private int findWindCharge(PlayerInventory inv) {
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() == Material.WIND_CHARGE) return i;
        }
        return -1;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ELYTRA + MACE DIVE SYSTEM
    // ══════════════════════════════════════════════════════════════════════════
    //
    // Pro-level elytra+mace dive in 5 phases:
    //   Phase 1 (tick 0):    Equip elytra, equip mace, jump
    //   Phase 2 (tick 5-6):  Start gliding (must be falling), firework boost UP
    //   Phase 3 (tick 15-20): Second firework boost (mostly upward, slight towards target)
    //   Phase 4 (tick 30):   Stop gliding, free-fall DIVE towards target
    //   Phase 5 (tick 32-80): Track target, smash on contact
    //   Cleanup:             Re-equip chestplate, switch to melee
    //
    // CRITICAL: combatState = ELYTRA_FLIGHT during entire sequence.
    //           Main loop will NOT run pearl/bow/sneak/anything.
    // ══════════════════════════════════════════════════════════════════════════

    private ItemStack savedChestplate = null;
    private boolean elytraDiveActive = false; // extra guard for scheduled tasks

    private void handleElytraMaceCombo(Player bot) {
        if (isUsingElytra || elytraDiveActive) return;
        if (!bot.isOnGround()) return;

        // ── Phase 1: Equip elytra + mace, normal jump ──
        ItemStack chest = bot.getInventory().getChestplate();
        if (chest == null || chest.getType() != Material.ELYTRA) {
            int elytraSlot = findItem(bot.getInventory(), Material.ELYTRA);
            if (elytraSlot == -1) return;
            ItemStack elytra = bot.getInventory().getItem(elytraSlot);
            savedChestplate = chest;
            bot.getInventory().setItem(elytraSlot, null);
            bot.getInventory().setChestplate(elytra);
        } else {
            savedChestplate = null;
        }

        int maceSlot = findMace(bot.getInventory());
        if (maceSlot != -1) {
            maceSlot = hotbarSlot(bot.getInventory(), maceSlot);
            if (maceSlot != -1) bot.getInventory().setHeldItemSlot(maceSlot);
        }

        combatState = CombatState.ELYTRA_FLIGHT;
        isUsingElytra = true;
        elytraDiveActive = true;

        // Normal jump (0.42) — exactly like a real player
        bot.setVelocity(new Vector(0, 0.42, 0));

        // Calculate direction TO target for angled boosts
        final Vector flatToTarget = target.getLocation().toVector()
                .subtract(bot.getLocation().toVector());
        flatToTarget.setY(0);
        if (flatToTarget.lengthSquared() > 0.01) flatToTarget.normalize();

        // ── Phase 2 (tick 6): Activate elytra while falling + first firework ──
        // Real players activate elytra mid-fall, then firework boosts in LOOK direction
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!elytraDiveActive || !isValid()) { finishElytraCombo(); return; }
            Player b = getBotPlayer();
            if (b == null) { finishElytraCombo(); return; }

            b.setGliding(true);

            if (!hasFireworks(b)) { finishElytraCombo(); return; }

            // First boost: ~55° upward angle toward target (natural elytra arc)
            // Vanilla firework boost: ~1.5 speed (not 2.2)
            Vector boost = flatToTarget.clone().multiply(0.8);
            boost.setY(1.2); // ~55° angle upward
            boost.normalize().multiply(1.5); // Vanilla firework boost speed
            boostElytra(b, boost);

            // Look in boost direction
            float yaw = (float) Math.toDegrees(Math.atan2(-boost.getX(), boost.getZ()));
            float pitch = (float) Math.toDegrees(Math.atan2(-boost.getY(),
                    Math.sqrt(boost.getX() * boost.getX() + boost.getZ() * boost.getZ())));
            b.setRotation(yaw, pitch);
            currentBotYaw = yaw;
            currentBotPitch = pitch;
        }, 6L);

        // ── Phase 3 (tick 18): Second firework — more forward, less upward ──
        // Bot is now arcing — boost more toward target to position above them
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!elytraDiveActive || !isValid()) { finishElytraCombo(); return; }
            Player b = getBotPlayer();
            if (b == null || !b.isGliding()) { finishElytraCombo(); return; }

            if (hasFireworks(b)) {
                // Recalculate direction to target (they may have moved)
                Vector toTarget = target.getLocation().toVector()
                        .subtract(b.getLocation().toVector());
                toTarget.normalize();
                // ~30° upward angle — transitioning from climb to arc
                Vector boost = new Vector(toTarget.getX() * 1.0, 0.6, toTarget.getZ() * 1.0);
                boost.normalize().multiply(1.5); // Vanilla firework speed
                boostElytra(b, boost);
            }
            lookAt(b, target.getLocation());
        }, 18L);

        // ── Phase 4 (tick 30): Stop gliding — begin free fall dive ──
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!elytraDiveActive || !isValid()) { finishElytraCombo(); return; }
            Player b = getBotPlayer();
            if (b == null) { finishElytraCombo(); return; }

            b.setGliding(false);
            b.setGravity(true);
            lookAt(b, target.getLocation());

            // Record peak Y for manual fall distance
            manualFallStartY = b.getLocation().getY();

            // Give a nudge toward target — natural dive momentum
            Vector dive = target.getLocation().toVector()
                    .subtract(b.getLocation().toVector());
            double horizDist = Math.sqrt(dive.getX() * dive.getX() + dive.getZ() * dive.getZ());
            dive.normalize();
            double speed = Math.max(0.8, Math.min(horizDist * 0.12, 1.8));
            dive.multiply(speed);
            // Gravity will pull Y down naturally — just ensure slight downward start
            if (dive.getY() > -0.3) dive.setY(-0.5);
            b.setVelocity(dive);
        }, 30L);

        // ── Phase 5 (tick 32-75): Track target, let gravity pull down, smash on contact ──
        for (int tick = 32; tick <= 75; tick += 2) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!elytraDiveActive || !isValid()) return;
                Player b = getBotPlayer();
                if (b == null) return;

                lookAt(b, target.getLocation());

                // Gentle steering — blend current velocity toward target
                if (!b.isOnGround()) {
                    Vector current = b.getVelocity();
                    Vector toTarget = target.getLocation().toVector()
                            .subtract(b.getLocation().toVector()).normalize();
                    // 90% physics + 10% correction (subtle, not robotic)
                    Vector corrected = current.multiply(0.9).add(toTarget.multiply(0.15));
                    // Don't fight gravity — let Y stay natural
                    b.setVelocity(corrected);
                }

                // Check smash distance
                double dist = b.getLocation().distance(target.getLocation());
                double fallDist = Math.max(getManualFallDistance(b), b.getFallDistance());
                if (dist <= 4.5 && fallDist > 1.5) {
                    performMaceSmash(b);
                    finishElytraCombo();
                    return;
                }

                if (b.isOnGround() && b.getFallDistance() < 0.1) {
                    finishElytraCombo();
                }
            }, tick);
        }

        // Safety timeout
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (elytraDiveActive) finishElytraCombo();
        }, 80L);
    }

    /** Boost elytra with firework: apply velocity, consume item, visual+sound. */
    private void boostElytra(Player bot, Vector direction) {
        int fwSlot = findItem(bot.getInventory(), Material.FIREWORK_ROCKET);
        if (fwSlot == -1) return;
        ItemStack fw = bot.getInventory().getItem(fwSlot);
        if (fw == null) return;

        bot.setVelocity(direction);
        fw.setAmount(fw.getAmount() - 1);
        bot.getWorld().playSound(bot.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 1.0f);
        bot.getWorld().spawn(bot.getLocation(), Firework.class, f -> f.detonate());
    }

    /** Clean up after elytra dive: stop gliding, re-equip armor, unlock state. */
    private void finishElytraCombo() {
        elytraDiveActive = false;
        isUsingElytra = false;
        combatState = CombatState.IDLE;

        Player b = getBotPlayer();
        if (b == null) return;

        if (b.isGliding()) b.setGliding(false);

        // Re-equip saved chestplate
        if (savedChestplate != null) {
            ItemStack elytra = b.getInventory().getChestplate();
            b.getInventory().setChestplate(savedChestplate);
            if (elytra != null && elytra.getType() == Material.ELYTRA) {
                b.getInventory().addItem(elytra);
            }
            savedChestplate = null;
        }

        // Switch back to melee
        selectBestWeapon(b);
    }

    private int findMace(PlayerInventory inv) {
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() == Material.MACE) return i;
        }
        return -1;
    }

    /**
     * MC 1.21 Mace Smash Attack.
     *
     * Damage formula (vanilla-like with diminishing returns):
     *   - Base mace damage: 7
     *   - First 3 blocks fallen: +4 damage per block
     *   - Blocks 3-8: +2 damage per block
     *   - Blocks 8+: +1 damage per block
     *   - Sharpness adds flat bonus
     *
     * On successful smash hit:
     *   - Mace NEGATES all fall damage for the attacker
     *   - Heavy knockback to target
     *   - Smash ground particles + sound
     */
    private void performMaceSmash(Player bot) {
        if (!isValid()) return;

        // ALWAYS look at target when smashing with mace
        lookAt(bot, target.getLocation());

        double distance = bot.getLocation().distance(target.getLocation());
        if (distance > 4.5) return;

        // Use manual tracking OR vanilla, whichever is higher
        double fallDistance = Math.max(getManualFallDistance(bot), bot.getFallDistance());

        // Factor in actual Y velocity for momentum bonus
        // Terminal velocity in MC is ~3.92 blocks/tick downward
        // More speed = more momentum = more damage
        double yVelocity = Math.abs(bot.getVelocity().getY());
        double momentumBonus = 0;
        if (yVelocity > 0.5) {
            // Scale: velocity 0.5-1.0 = small bonus, 1.0-2.0 = medium, 2.0+ = big
            momentumBonus = Math.min(yVelocity * 2.0, 6.0);
        }

        // Swing animation
        bot.swingMainHand();

        // ── MC 1.21 mace damage formula ──
        double baseDamage = 7.0;
        double bonusDamage = 0;

        if (fallDistance > 0) {
            // First 3 blocks: +4 per block
            double tier1 = Math.min(fallDistance, 3.0);
            bonusDamage += tier1 * 4.0;

            // Blocks 3-8: +2 per block
            if (fallDistance > 3) {
                double tier2 = Math.min(fallDistance - 3, 5.0);
                bonusDamage += tier2 * 2.0;
            }

            // Blocks 8+: +1 per block (up to reasonable cap)
            if (fallDistance > 8) {
                double tier3 = Math.min(fallDistance - 8, 20.0);
                bonusDamage += tier3 * 1.0;
            }
        }

        // Momentum bonus from velocity (rewards fast dives)
        bonusDamage += momentumBonus;

        // Sharpness from mace enchantment
        ItemStack mace = bot.getInventory().getItemInMainHand();
        if (mace != null && mace.getType() == Material.MACE) {
            int sharpness = mace.getEnchantmentLevel(Enchantment.SHARPNESS);
            baseDamage += sharpness * 0.5 + (sharpness > 0 ? 0.5 : 0);
        }

        double totalDamage = baseDamage + bonusDamage;

        // Deal damage
        target.damage(totalDamage, bot);

        // ── Mace NEGATES fall damage for the attacker on successful hit ──
        bot.setFallDistance(0);
        manualFallStartY = -1;

        // ── Knockback scales with fall distance + impact velocity ──
        Vector dir = target.getLocation().toVector()
                .subtract(bot.getLocation().toVector());
        dir.setY(0);
        if (dir.lengthSquared() > 0.001) dir.normalize();
        double impactForce = fallDistance + yVelocity;
        double kbH = 0.5 + Math.min(impactForce * 0.05, 0.6);
        double kbV = 0.35 + Math.min(impactForce * 0.03, 0.4);
        target.setVelocity(dir.multiply(kbH).setY(kbV));

        // ── Visual + sound ──
        if (fallDistance > 5 || yVelocity > 1.5) {
            // Heavy smash: ground crack + shockwave
            bot.getWorld().playSound(bot.getLocation(), Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.0f, 1.0f);
            bot.getWorld().spawnParticle(Particle.EXPLOSION, bot.getLocation(), 3);
            bot.getWorld().spawnParticle(Particle.CLOUD, bot.getLocation(), 15, 1.5, 0.2, 1.5, 0.02);
        } else if (fallDistance > 1) {
            bot.getWorld().playSound(bot.getLocation(), Sound.ITEM_MACE_SMASH_GROUND, 1.0f, 1.0f);
            bot.getWorld().spawnParticle(Particle.CLOUD, bot.getLocation(), 8, 0.8, 0.1, 0.8, 0.01);
        } else {
            bot.getWorld().playSound(bot.getLocation(), Sound.ITEM_MACE_SMASH_GROUND, 0.6f, 1.2f);
        }

        lastAttackTime = System.currentTimeMillis();
        comboCount = 0;
    }

    private void handleSpearMelee(Player bot, double distance) {
        // Spear can be used in melee or thrown
        if (distance <= 3.0) {
            // Melee attack with W-tap/D-tap like sword
            if (!target.isBlocking()) {
                if (shouldWTap()) performWTap(bot);
                else if (shouldDTap()) performDTap(bot);
            }
            performAttack(bot, random.nextDouble() < critChance);
            lastAttackTime = System.currentTimeMillis();
        } else if (distance <= 8.0) {
            // Throw at mid-range — pros throw spear aggressively
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
        
        // Vanilla shield disable: axe hit on blocking player → 5s (100 tick) shield cooldown
        boolean wasBlocking = target.isBlocking();
        ItemStack weapon = bot.getInventory().getItemInMainHand();
        boolean holdingAxe = weapon != null && isAxe(weapon.getType());
        if (wasBlocking && holdingAxe) {
            // Disable shield for 5 seconds (vanilla mechanic)
            target.setCooldown(Material.SHIELD, 100);
            // Force stop blocking by applying the cooldown — Bukkit handles the rest
            target.getWorld().playSound(target.getLocation(),
                    Sound.ITEM_SHIELD_BREAK, 1.0f, 0.8f + (float)(Math.random() * 0.4));
        }
        
        // Apply damage
        target.damage(damage, bot);
        
        // Apply knockback only if target is NOT blocking with shield
        if (!wasBlocking) {
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
     * Apply VANILLA knockback to victim using the exact MC source code formula.
     * 
     * From LivingEntity.knockback() in the Minecraft source:
     *   velX = currentVelX / 2.0 + direction.x * strength
     *   velY = onGround ? min(0.4, currentVelY / 2.0 + strength) : currentVelY
     *   velZ = currentVelZ / 2.0 + direction.z * strength
     * 
     * Base:   strength 0.4
     * Sprint: separate call with strength 0.5
     * KB enc: separate call with strength level * 0.5
     */
    private void applyKnockback(Player attacker, Player victim) {
        // Direction from attacker to victim (push direction)
        Vector dir = victim.getLocation().toVector()
                .subtract(attacker.getLocation().toVector());
        dir.setY(0);
        if (dir.lengthSquared() < 0.001) return;
        dir.normalize();
        
        boolean onGround = victim.isOnGround();
        Vector vel = victim.getVelocity().clone();
        
        // Step 1: Base knockback (strength 0.4)
        vel = vanillaKnockbackStep(vel, dir, 0.4, onGround);
        
        // Step 2: Sprint bonus (strength 0.5) — separate call
        if (attacker.isSprinting()) {
            vel = vanillaKnockbackStep(vel, dir, 0.5, onGround);
        }
        
        // Step 3: Knockback enchantment — separate call
        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        if (weapon != null) {
            int kbLevel = weapon.getEnchantmentLevel(Enchantment.KNOCKBACK);
            if (kbLevel > 0) {
                vel = vanillaKnockbackStep(vel, dir, kbLevel * 0.5, onGround);
            }
        }
        
        victim.setVelocity(vel);
    }

    /**
     * One round of the vanilla knockback formula.
     */
    private Vector vanillaKnockbackStep(Vector currentVel, Vector direction,
                                         double strength, boolean onGround) {
        double newX = currentVel.getX() / 2.0 + direction.getX() * strength;
        double newY = onGround
                ? Math.min(0.4, currentVel.getY() / 2.0 + strength)
                : currentVel.getY();
        double newZ = currentVel.getZ() / 2.0 + direction.getZ() * strength;
        return new Vector(newX, newY, newZ);
    }
    
    private long lastKnockbackApplied = 0;

    // ══════════════════════════════════════════════════════════════════════════
    // MID-RANGE COMBAT (4 < distance <= 8)
    // ══════════════════════════════════════════════════════════════════════════

    private void handleMidRangeCombat(Player bot, double distance) {
        // Priority: wind+mace > spear throw > damage potion > crossbow quick shot > chase

        // Wind charge + mace combo at mid-range (best engage with mace kit)
        if (hasWindCharges(bot) && findMace(bot.getInventory()) != -1
                && bot.isOnGround() && System.currentTimeMillis() - lastWindChargeUse > 4000) {
            performWindChargeMaceCombo(bot);
            return;
        }

        // Spear throw at mid-range — best mid-range weapon
        if (hasSpear(bot) && shouldThrowSpear()) {
            handleSpearThrow(bot, distance);
            return;
        }

        // Damage potions (splash)
        if (hasSplashPotions(bot) && shouldThrowPotion()) {
            throwPotion(bot);
            return;
        }

        // Crossbow quick shot at mid range if we have one loaded
        if (hasCrossbow(bot) && shouldShootCrossbow() && distance > 5) {
            handleCrossbowShot(bot, distance);
            return;
        }

        // Bow snap shot if target is retreating
        if (hasOnlyBow(bot) && shouldShootBow() && distance > 5 && !target.isSprinting()) {
            handleBowShot(bot, distance);
            return;
        }

        // Chase to melee range - movement handled by startMovementLoop()
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
        // NEVER do anything during elytra flight
        if (isUsingElytra || elytraDiveActive) return;

        // Priority 1: Elytra+Mace dive if we have both (best long-range engage)
        if (findMace(bot.getInventory()) != -1 && hasElytra(bot) && hasFireworks(bot)
                && distance > 8 && shouldUseElytra(distance)) {
            handleElytraMaceCombo(bot);
            return;
        }

        // Priority 2: Elytra approach (close gap fast, no mace needed)
        if (hasElytra(bot) && hasFireworks(bot) && distance > 20 && shouldUseElytra(distance)) {
            handleElytraApproach(bot);
            return;
        }

        // Priority 3: Crossbow (higher damage per shot)
        if (hasCrossbow(bot) && shouldShootCrossbow() && distance > 8) {
            handleCrossbowShot(bot, distance);
            return;
        }

        // Priority 4: Bow
        if (hasOnlyBow(bot) && shouldShootBow() && distance > 8) {
            handleBowShot(bot, distance);
            return;
        }

        // Default: sprint chase — movement loop handles the actual walking
        bot.setSprinting(true);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // CROSSBOW COMBAT
    // ══════════════════════════════════════════════════════════════════════════

    private void handleCrossbowShot(Player bot, double distance) {
        long now = System.currentTimeMillis();
        if (now - lastCrossbowShot < 2500) return; // Crossbow has longer cycle

        int cbSlot = findCrossbow(bot.getInventory());
        if (cbSlot == -1) return;
        if (!hasArrows(bot)) return;
        if (random.nextDouble() > accuracy * 0.85) return;

        cbSlot = hotbarSlot(bot.getInventory(), cbSlot);
        if (cbSlot == -1) return;
        bot.getInventory().setHeldItemSlot(cbSlot);

        isCrossbowLoading = true;
        combatState = CombatState.CROSSBOW_LOAD;

        // Crossbow load time (vanilla = 25 ticks, Quick Charge reduces)
        int loadTime = switch (difficulty) {
            case EASY -> 30;
            case MEDIUM -> 25;
            case HARD -> 20;
            case HACKER -> 12;
            case ADAPTIVE -> 22;
            case DUMMY -> 9999;
        };

        // Loading phase — bot aims at target while loading
        final int fCbSlot = cbSlot;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!isValid()) { isCrossbowLoading = false; return; }

            // Aim at predicted position
            double dist2 = bot.getLocation().distance(target.getLocation());
            Location predicted = predictTargetLocation(target, dist2);
            Vector velocity = calculateArrowVelocity(bot.getEyeLocation(), predicted, dist2);

            // Rotation only — NO teleport
            double dx = velocity.getX(), dy = velocity.getY(), dz = velocity.getZ();
            float yaw   = (float) Math.toDegrees(Math.atan2(-dx, dz));
            float pitch = (float) Math.toDegrees(Math.atan2(-dy, Math.sqrt(dx * dx + dz * dz)));
            bot.setRotation(yaw, pitch);
            currentBotYaw = yaw;
            currentBotPitch = pitch;

            // Fire!
            Arrow arrow = bot.getWorld().spawn(bot.getEyeLocation(), Arrow.class);
            arrow.setVelocity(velocity);
            arrow.setShooter(bot);
            arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
            arrow.setDamage(11.0); // Crossbow does more per-shot than bow
            arrow.setCritical(true);

            consumeArrow(bot);
            bot.getWorld().playSound(bot.getLocation(), Sound.ITEM_CROSSBOW_SHOOT, 1.0f, 1.0f);
            bot.swingMainHand();

            isCrossbowLoading = false;
            combatState = CombatState.IDLE;
            lastCrossbowShot = System.currentTimeMillis();

            // Switch back to melee weapon after shooting
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (isValid()) selectBestWeapon(getBotPlayer());
            }, 5L);
        }, loadTime);
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

        // Make bot face the gravity-corrected aim direction (rotation only — no teleport!)
        double aimDx = aimVelocity.getX(), aimDy = aimVelocity.getY(), aimDz = aimVelocity.getZ();
        float aimYaw   = (float) Math.toDegrees(Math.atan2(-aimDx, aimDz));
        float aimPitch = (float) Math.toDegrees(Math.atan2(-aimDy, Math.sqrt(aimDx * aimDx + aimDz * aimDz)));
        bot.setRotation(aimYaw, aimPitch);
        currentBotYaw = aimYaw;
        currentBotPitch = aimPitch;

        // Charge time based on difficulty (full charge = 20 ticks)
        int chargeTime = switch(difficulty) {
            case EASY -> 25;
            case MEDIUM -> 20;
            case HARD -> 15;
            case HACKER -> 8;
            case ADAPTIVE -> 18;
            case DUMMY -> 9999;
        };
        
        isBowDrawing = true;
        combatState = CombatState.BOW_DRAW;

        // Simulate bow draw and release
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!isValid()) { isBowDrawing = false; combatState = CombatState.IDLE; return; }
            
            // Recalculate at release time in case target moved
            double dist2 = bot.getLocation().distance(target.getLocation());
            Location releasedPredicted = predictTargetLocation(target, dist2);
            Vector velocity = calculateArrowVelocity(bot.getEyeLocation(), releasedPredicted, dist2);

            // Update aim at release (rotation only)
            double rdx = velocity.getX(), rdy = velocity.getY(), rdz = velocity.getZ();
            float rYaw   = (float) Math.toDegrees(Math.atan2(-rdx, rdz));
            float rPitch = (float) Math.toDegrees(Math.atan2(-rdy, Math.sqrt(rdx * rdx + rdz * rdz)));
            bot.setRotation(rYaw, rPitch);
            currentBotYaw = rYaw;
            currentBotPitch = rPitch;

            Arrow arrow = bot.getWorld().spawn(bot.getEyeLocation(), Arrow.class);
            arrow.setVelocity(velocity);
            arrow.setShooter(bot);
            arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
            arrow.setDamage(9.0); // Full charge damage
            
            // Consume arrow
            consumeArrow(bot);
            
            bot.getWorld().playSound(bot.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1.0f, 1.0f);
            isBowDrawing = false;
            combatState = CombatState.IDLE;
            lastBowShot = System.currentTimeMillis();

            // Switch back to melee weapon
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (isValid()) selectBestWeapon(getBotPlayer());
            }, 5L);
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
            case DUMMY    -> 0.0;
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
            case DUMMY    -> 0.0;
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
        if (now - lastElytraUse < 4000) return;
        if (!bot.isOnGround()) return;
        if (isUsingElytra || elytraDiveActive) return;

        // Equip elytra (save chestplate)
        ItemStack chest = bot.getInventory().getChestplate();
        if (chest == null || chest.getType() != Material.ELYTRA) {
            int elytraSlot = findItem(bot.getInventory(), Material.ELYTRA);
            if (elytraSlot == -1) return;
            ItemStack elytra = bot.getInventory().getItem(elytraSlot);
            savedChestplate = chest;
            bot.getInventory().setItem(elytraSlot, null);
            bot.getInventory().setChestplate(elytra);
        }

        combatState = CombatState.ELYTRA_FLIGHT;
        isUsingElytra = true;
        elytraDiveActive = true;
        lastElytraUse = now;

        // Normal jump — like a real player
        bot.setVelocity(new Vector(0, 0.42, 0));

        // Direction to target (flat)
        final Vector flatDir = target.getLocation().toVector()
                .subtract(bot.getLocation().toVector());
        flatDir.setY(0);
        if (flatDir.lengthSquared() > 0.01) flatDir.normalize();

        // Activate elytra mid-fall + first angled boost toward target
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!elytraDiveActive || !isValid()) { finishElytraCombo(); return; }
            Player b = getBotPlayer();
            if (b == null) { finishElytraCombo(); return; }

            b.setGliding(true);

            if (hasFireworks(b)) {
                // Boost at ~20° upward angle toward target (mostly forward, slight climb)
                // Vanilla firework boost: ~1.5 speed
                Vector boost = flatDir.clone().multiply(1.2);
                boost.setY(0.5);
                boost.normalize().multiply(1.5);
                boostElytra(b, boost);
            }
            lookAt(b, target.getLocation());
        }, 6L);

        // Additional boosts every 15 ticks — aim directly at target
        for (int i = 1; i <= 3; i++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!elytraDiveActive || !isValid()) return;
                Player b = getBotPlayer();
                if (b == null || !b.isGliding()) return;

                double dist = b.getLocation().distance(target.getLocation());
                if (dist < 6) { finishElytraCombo(); return; }

                if (hasFireworks(b)) {
                    Vector toTarget = target.getLocation().toVector()
                            .subtract(b.getLocation().toVector()).normalize();
                    // Slight upward bias when far, slight downward when close
                    if (dist > 15) {
                        if (toTarget.getY() < 0.1) toTarget.setY(0.15);
                    }
                    boostElytra(b, toTarget.multiply(1.5)); // Vanilla firework speed
                }
                lookAt(b, target.getLocation());
            }, 6L + 15L * i);
        }

        // Safety timeout
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (elytraDiveActive) finishElytraCombo();
        }, 75L);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MOVEMENT LOOP
    // ══════════════════════════════════════════════════════════════════════════

    private void startMovementLoop() {
        movementTask = new BukkitRunnable() {
            private int tickCounter = 0;

            @Override
            public void run() {
                if (!isValid()) return;
                tickCounter++;

                Player bot = getBotPlayer();
                if (bot == null) return;

                // ── Manual fall distance tracking + FALL DAMAGE ──
                // Citizens NPCs don't accumulate fall distance naturally,
                // so we track it manually and apply vanilla fall damage.
                if (bot.isOnGround()) {
                    if (!wasOnGroundLastTick && manualFallStartY > bot.getLocation().getY()) {
                        // Just landed — calculate and APPLY fall damage
                        double fallDist = manualFallStartY - bot.getLocation().getY();
                        // Vanilla: damage = fallDistance - 3 (no damage below 3 blocks)
                        if (fallDist > 3.0) {
                            double fallDamage = fallDist - 3.0;
                            // Check for Feather Falling enchantment (reduces by 12% per level)
                            ItemStack boots = bot.getInventory().getBoots();
                            if (boots != null && boots.containsEnchantment(org.bukkit.enchantments.Enchantment.FEATHER_FALLING)) {
                                int ffLevel = boots.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.FEATHER_FALLING);
                                fallDamage *= Math.max(0, 1.0 - (ffLevel * 0.12));
                            }
                            // Check for Protection enchantment on all armor
                            for (ItemStack armor : bot.getInventory().getArmorContents()) {
                                if (armor != null && armor.containsEnchantment(org.bukkit.enchantments.Enchantment.PROTECTION)) {
                                    int protLevel = armor.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.PROTECTION);
                                    fallDamage *= Math.max(0, 1.0 - (protLevel * 0.04));
                                }
                            }
                            if (fallDamage > 0.5) {
                                bot.damage(fallDamage);
                                bot.getWorld().playSound(bot.getLocation(),
                                        Sound.ENTITY_PLAYER_HURT, 1.0f, 1.0f);
                            }
                        }
                    }
                    manualFallStartY = -1;
                    wasOnGroundLastTick = true;
                } else {
                    if (wasOnGroundLastTick) {
                        // Just left ground — start tracking
                        manualFallStartY = bot.getLocation().getY();
                    } else if (manualFallStartY > 0 && bot.getLocation().getY() > manualFallStartY) {
                        // Going UP — reset start to peak
                        manualFallStartY = bot.getLocation().getY();
                    }
                    wasOnGroundLastTick = false;
                }

                // ── DUMMY MODE: no movement at all ──
                if (difficulty == BotDifficulty.DUMMY) return;

                // ── NEVER move during elytra flight or bow draw ──
                if (isUsingElytra || elytraDiveActive) return;
                if (combatState == CombatState.ELYTRA_FLIGHT) return;

                double distance = bot.getLocation().distance(target.getLocation());
                double healthPercent = (bot.getHealth() / bot.getMaxHealth()) * 100;

                // ── Movement: walk/sprint towards target ──
                // When eating: still move but slower (retreat direction)
                // When retreating: handled by retreat() method
                if (!isRetreating) {
                    if (isEating) {
                        // Walk backwards slowly while eating (pro technique)
                        if (distance <= 4.0) {
                            retreat(bot);
                        }
                    } else {
                        moveTowardsTarget(bot, distance);
                    }
                }

                // ── Jumping: only over obstacles or sprint-jumps ──
                if (tickCounter % 2 == 0) {
                    handleJumping(bot, distance);
                }

                // ── Sneaking: tap-shift to reduce KB after getting hit ──
                if (tickCounter % 4 == 0) {
                    handleSneaking(bot, distance);
                }

                // ── Smart retreat based on health and combat state ──
                if (!isEating && !isRetreating) {
                    // Retreat more aggressively when being comboed
                    double retreatThreshold = isBeingComboed ? 35 : 25;
                    double retreatChance = isBeingComboed ? 0.15 : 0.08;
                    
                    // Higher difficulties retreat smarter
                    retreatChance *= switch (difficulty) {
                        case EASY     -> 0.3;
                        case MEDIUM   -> 0.7;
                        case HARD     -> 1.2;
                        case HACKER   -> 1.5;
                        case ADAPTIVE -> 1.0;
                        case DUMMY    -> 0.0;
                    };
                    
                    if (healthPercent < retreatThreshold && random.nextDouble() < retreatChance) {
                        isRetreating = true;
                        retreat(bot);
                        Bukkit.getScheduler().runTaskLater(plugin, () -> isRetreating = false, 20L);
                    }
                }

                // ── W-tap for KB: only in melee, rare (every 3 ticks) ──
                if (tickCounter % 3 == 0 && distance <= 4 && distance > 1.5 && shouldWTap()) {
                    performWTap(bot);
                }

                // ── Block placement: very situational (every 5 ticks) ──
                if (tickCounter % 5 == 0 && shouldPlaceBlock(bot, distance)) {
                    placeBlock(bot);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /**
     * Velocity-based movement with natural gravity.
     *
     * Instead of teleporting (which bypasses physics), we set horizontal
     * velocity every tick. The server's physics engine handles gravity,
     * falling, landing, and collisions naturally — just like a real player.
     *
     * - Sprint when far, walk when close
     * - Circle-strafe in melee range
     * - Gravity is NATURAL (no teleport Y manipulation)
     */
    private void moveTowardsTarget(Player bot, double distance) {
        // Don't crowd the target — stop at comfortable melee range
        if (distance < 1.8) return;

        // Ensure gravity is always enabled
        bot.setGravity(true);

        Location botLoc = bot.getLocation();
        Location targetLoc = target.getLocation();

        // Look at target (smooth rotation)
        lookAt(bot, targetLoc);

        // ── Airborne: let vanilla physics handle everything ──
        // Only apply minimal air-strafe (like a real player can in MC)
        if (!bot.isOnGround()) {
            Vector vel = bot.getVelocity();
            double hdx = targetLoc.getX() - botLoc.getX();
            double hdz = targetLoc.getZ() - botLoc.getZ();
            double hlen = Math.sqrt(hdx * hdx + hdz * hdz);
            if (hlen > 0.5) {
                // Minecraft allows ~0.02 air-strafe per tick (very small)
                vel.setX(vel.getX() + (hdx / hlen) * 0.02);
                vel.setZ(vel.getZ() + (hdz / hlen) * 0.02);
                bot.setVelocity(vel);
            }
            return;
        }

        // ── On ground: set horizontal velocity ──
        double dx = targetLoc.getX() - botLoc.getX();
        double dz = targetLoc.getZ() - botLoc.getZ();
        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
        if (horizontalDist < 0.1) return;

        double ndx = dx / horizontalDist;
        double ndz = dz / horizontalDist;

        // Speed: sprint when far, walk when close
        double speed;
        if (distance > 3.5) {
            bot.setSprinting(true);
            isSprinting = true;
            // Vanilla sprint: 5.612 m/s ≈ 0.28 blocks/tick
            speed = switch (difficulty) {
                case EASY     -> 0.18;
                case MEDIUM   -> 0.22;
                case HARD     -> 0.27;  // Near vanilla sprint
                case HACKER   -> 0.28;  // Full vanilla sprint
                case ADAPTIVE -> 0.24;
                case DUMMY    -> 0.0;
            };
        } else {
            bot.setSprinting(false);
            isSprinting = false;
            // Vanilla walk: 4.317 m/s ≈ 0.216 blocks/tick
            speed = switch (difficulty) {
                case EASY     -> 0.12;
                case MEDIUM   -> 0.15;
                case HARD     -> 0.20;  // Fast walk
                case HACKER   -> 0.21;  // Full vanilla walk
                case ADAPTIVE -> 0.16;
                case DUMMY    -> 0.0;
            };
        }

        // ── POST-HIT CHASE: sprint harder after landing a hit to maintain combo ──
        if (postHitChase && distance > 2.0 && distance <= 6.0) {
            bot.setSprinting(true);
            isSprinting = true;
            // Boost speed by 15-25% to close the gap after knockback
            double chaseBoost = switch (difficulty) {
                case EASY     -> 1.05;
                case MEDIUM   -> 1.10;
                case HARD     -> 1.18;
                case HACKER   -> 1.25;
                case ADAPTIVE -> 1.12;
                case DUMMY    -> 1.0;
            };
            speed = Math.min(0.30, speed * chaseBoost); // Cap at slightly above vanilla sprint
        }

        // ── KILL CHASE: sprint at max speed when target is very low HP ──
        if (target.getHealth() <= 6.0 && distance > 2.0 && distance <= 8.0) {
            bot.setSprinting(true);
            isSprinting = true;
            speed = Math.max(speed, 0.27); // At least near-max sprint for the kill
        }

        // Circle-strafe in close combat
        double strafeSpeed = 0;
        if (distance <= 4.5 && distance > 1.8 && !isRetreating) {
            long now = System.currentTimeMillis();
            if (now - lastStrafeChange > 400 + random.nextInt(600)) {
                if (random.nextDouble() < strafeChance) {
                    strafeDirection *= -1;
                    lastStrafeChange = now;
                }
            }
            strafeSpeed = switch (difficulty) {
                case EASY     -> 0.04;
                case MEDIUM   -> 0.07;
                case HARD     -> 0.13;  // Aggressive strafe
                case HACKER   -> 0.17;  // Very aggressive strafe
                case ADAPTIVE -> 0.08;
                case DUMMY    -> 0.0;
            } * strafeDirection;
        }

        // Combine forward + perpendicular strafe
        double vx = ndx * speed + (-ndz) * strafeSpeed;
        double vz = ndz * speed + ( ndx) * strafeSpeed;

        // Check for wall ahead (don't walk into solid blocks)
        Location ahead = botLoc.clone().add(vx * 2, 0, vz * 2);
        Block blockAhead = ahead.getBlock();
        if (blockAhead.getType().isSolid()) {
            // Check if we can step up 1 block
            Block aboveAhead = ahead.clone().add(0, 1, 0).getBlock();
            Block headClear = ahead.clone().add(0, 2, 0).getBlock();
            if (!aboveAhead.getType().isSolid() && !headClear.getType().isSolid()) {
                // Step up: add jump velocity
                vx *= 0.6; // Reduce horizontal while stepping
                vz *= 0.6;
                bot.setVelocity(new Vector(vx, 0.42, vz));
                return;
            } else {
                return; // Wall — can't pass
            }
        }

        // Apply horizontal velocity, preserve vertical (gravity handles Y)
        Vector currentVel = bot.getVelocity();
        bot.setVelocity(new Vector(vx, currentVel.getY(), vz));
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
            case DUMMY -> 0.0;
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

    /**
     * Sneaking — PRO PLAYER STYLE:
     * - NEVER hold shift randomly. That looks like a noob.
     * - Only quick tap-shift (1-2 ticks) right after getting hit to reduce knockback.
     * - Higher difficulties tap more consistently.
     */
    private void handleSneaking(Player bot, double distance) {
        if (isSneaking || isEating || distance > 5) return;

        long now = System.currentTimeMillis();
        if (now - lastSneak < 800) return; // Min 800ms between shift-taps

        // Only shift-tap if we recently took damage (tracked by trackOpponentState)
        if (now - lastDamageReceivedTime < 500 && distance <= 4.5) {
            // Chance to shift-tap based on difficulty
            double tapChance = switch (difficulty) {
                case EASY     -> 0.0;   // Noobs don't know this trick
                case MEDIUM   -> 0.10;  // Rarely
                case HARD     -> 0.35;  // Often (pro KB reduction)
                case HACKER   -> 0.60;  // Almost always shift-taps
                case ADAPTIVE -> this.sneakChance * 0.5;
                case DUMMY    -> 0.0;
            };

            if (random.nextDouble() < tapChance) {
                bot.setSneaking(true);
                isSneaking = true;
                lastSneak = now;

                // Release after 1-2 ticks (instant tap, not held)
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (isValid()) {
                        getBotPlayer().setSneaking(false);
                        isSneaking = false;
                    }
                }, 1L + (random.nextInt(2))); // 1-2 ticks only
            }
        }
    }

    /**
     * Jumping — PRO PLAYER STYLE + BHOP:
     * - Jump to clear obstacles (block in front of bot).
     * - Sprint-jump when chasing from distance (moves ~30% faster than sprinting alone).
     * - BHop (Bunny Hop): continuous sprint-jump chains — jump immediately on landing
     *   to maintain maximum momentum. This is THE key PvP movement technique.
     *   In vanilla MC, sprint-jumping is ~30% faster than sprinting alone.
     *   BHop chains these jumps with no pause between landing and jumping.
     * - Higher difficulties BHop more consistently, like real pros.
     * - Crit jumps are handled in handleSwordAttack/handleAxeAttack, NOT here.
     */
    private boolean isBhopping = false;
    private long bhopStartTime = 0;

    private void handleJumping(Player bot, double distance) {
        if (!bot.isOnGround()) return;

        long now = System.currentTimeMillis();

        // Check if there's a solid block in front at knee level
        Vector facing = bot.getLocation().getDirection().clone();
        facing.setY(0);
        if (facing.lengthSquared() < 0.01) return;
        facing.normalize();

        Location kneeCheck = bot.getLocation().clone().add(facing.multiply(0.8));
        Block blockInFront = kneeCheck.getBlock();

        // ── OBSTACLE JUMP: always jump over blocks in front ──
        if (blockInFront.getType().isSolid()) {
            Block aboveObstacle = kneeCheck.clone().add(0, 1, 0).getBlock();
            Block headClearance = kneeCheck.clone().add(0, 2, 0).getBlock();
            if (!aboveObstacle.getType().isSolid() || !headClearance.getType().isSolid()) {
                bot.setVelocity(bot.getVelocity().add(new Vector(0, 0.42, 0)));
                lastJump = now;
                return;
            }
        }

        // ── BHOP: continuous sprint-jump chains ──
        // When sprinting and chasing, jump IMMEDIATELY on landing to chain jumps.
        // This gives ~30% speed boost over sprinting alone (vanilla mechanic).
        // BHop is triggered when chasing and maintained until bot stops or changes action.
        if (bot.isSprinting() && !isEating && !isRetreating && !isFleeingToHeal) {
            // Decide whether to start/continue BHop based on distance and difficulty
            if (distance > 4.0 && distance <= 30.0) {
                double bhopChance = switch (difficulty) {
                    case EASY     -> 0.0;    // Noobs don't BHop
                    case MEDIUM   -> 0.08;   // Rarely starts BHop
                    case HARD     -> 0.40;   // Often BHops (good players)
                    case HACKER   -> 0.70;   // Almost always BHops
                    case ADAPTIVE -> jumpChance * 2.0;
                    case DUMMY    -> 0.0;
                };

                // Start BHop chain
                if (!isBhopping && random.nextDouble() < bhopChance) {
                    isBhopping = true;
                    bhopStartTime = now;
                }

                // Continue BHop chain — jump immediately on landing (minimal delay)
                if (isBhopping) {
                    // BHop timing: jump within 1-2 ticks of landing for perfect chain
                    // Min delay between jumps: ~300ms (vanilla jump cooldown)
                    if (now - lastJump >= 300) {
                        Vector vel = bot.getVelocity();
                        vel.setY(0.42); // Vanilla jump velocity
                        bot.setVelocity(vel);
                        lastJump = now;
                        bot.setSprinting(true); // Maintain sprint through jump
                    }

                    // Stop BHop after a duration (human-like, not infinite)
                    long maxBhopDuration = switch (difficulty) {
                        case MEDIUM   -> 1500L;  // Short chains
                        case HARD     -> 3000L;  // Medium chains
                        case HACKER   -> 6000L;  // Long chains (very pro)
                        case ADAPTIVE -> 2000L;
                        default -> 0L;
                    };
                    if (now - bhopStartTime > maxBhopDuration) {
                        isBhopping = false;
                    }

                    return;
                }
            } else {
                // Out of BHop range — stop
                isBhopping = false;
            }

            // ── Regular sprint-jump (non-BHop, occasional) ──
            if (distance > 4.0 && now - lastJump >= 400) {
                double sprintJumpChance = switch (difficulty) {
                    case EASY     -> 0.0;
                    case MEDIUM   -> 0.10;
                    case HARD     -> 0.25;
                    case HACKER   -> 0.40;
                    case ADAPTIVE -> jumpChance * 1.5;
                    case DUMMY    -> 0.0;
                };

                if (random.nextDouble() < sprintJumpChance) {
                    Vector vel = bot.getVelocity();
                    vel.setY(0.42); // Vanilla jump height
                    bot.setVelocity(vel);
                    lastJump = now;
                }
            }
        } else {
            // Not sprinting or in special state — stop BHop
            isBhopping = false;
        }

        // ── BHOP while retreating (fleeing BHop — sprint-jump backwards) ──
        if (isFleeingToHeal && bot.isSprinting() && now - lastJump >= 350) {
            double fleeBhopChance = switch (difficulty) {
                case MEDIUM   -> 0.10;
                case HARD     -> 0.30;
                case HACKER   -> 0.55;
                case ADAPTIVE -> 0.20;
                default -> 0.0;
            };
            if (random.nextDouble() < fleeBhopChance) {
                Vector vel = bot.getVelocity();
                vel.setY(0.42);
                bot.setVelocity(vel);
                lastJump = now;
            }
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
            case DUMMY -> 0.0;
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
        double chance = switch(difficulty) {
            case EASY -> 0.0;     // Beginners don't W-tap
            case MEDIUM -> 0.12;  // Sometimes
            case HARD -> 0.30;    // Often (good players)
            case HACKER -> 0.55;  // Very consistently (pro)
            case ADAPTIVE -> wTapChance;
            case DUMMY -> 0.0;
        };
        return random.nextDouble() < chance;
    }

    /**
     * W-tap: sprint reset for extra knockback.
     * Pro technique: release sprint briefly then re-sprint before hitting
     * to reset the sprint-hit KB bonus, giving extra knockback per hit.
     */
    private void performWTap(Player bot) {
        bot.setSprinting(false);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (isValid()) {
                Player b = getBotPlayer();
                if (b != null) b.setSprinting(true);
            }
        }, 1L);
    }

    /**
     * D-tap: briefly strafe sideways then re-engage for sprint reset.
     * Alternative to W-tap used by pros to maintain forward pressure
     * while still resetting sprint knockback. Only Hard/Hacker use this.
     */
    private boolean shouldDTap() {
        double chance = switch(difficulty) {
            case EASY -> 0.0;
            case MEDIUM -> 0.0;
            case HARD -> 0.12;    // Sometimes (advanced technique)
            case HACKER -> 0.30;  // Often
            case ADAPTIVE -> wTapChance * 0.5;
            case DUMMY -> 0.0;
        };
        return random.nextDouble() < chance;
    }

    private void performDTap(Player bot) {
        // Brief sideways strafe to reset sprint
        bot.setSprinting(false);
        Vector strafe = bot.getLocation().getDirection()
                .crossProduct(new Vector(0, 1, 0))
                .normalize()
                .multiply(0.15 * (random.nextBoolean() ? 1 : -1));
        bot.setVelocity(bot.getVelocity().add(strafe));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (isValid()) {
                Player b = getBotPlayer();
                if (b != null) b.setSprinting(true);
            }
        }, 2L);
    }

    private void retreat(Player bot) {
        if (!bot.isOnGround()) return;

        Location botLoc = bot.getLocation();
        Location targetLoc = target.getLocation();

        double dx = botLoc.getX() - targetLoc.getX();
        double dz = botLoc.getZ() - targetLoc.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.1) return;

        double retreatSpeed = switch (difficulty) {
            case EASY     -> 0.18;
            case MEDIUM   -> 0.22;
            case HARD     -> 0.26;
            case HACKER   -> 0.28;
            case ADAPTIVE -> 0.24;
            case DUMMY    -> 0.0;
        };

        double vx = (dx / len) * retreatSpeed;
        double vz = (dz / len) * retreatSpeed;

        // Don't retreat into walls
        Location ahead = botLoc.clone().add(vx * 2, 0, vz * 2);
        if (ahead.getBlock().getType().isSolid()) return;

        // Velocity-based retreat — gravity handles Y naturally
        bot.setVelocity(new Vector(vx, bot.getVelocity().getY(), vz));
        bot.setSprinting(true);

        // Try to heal while retreating
        tryHeal(bot);
    }

    private boolean shouldPlaceBlock(Player bot, double distance) {
        // DISABLED — bots must NEVER modify the arena map
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
                bot.getLocation().add(0, 1, 0), 20, 0.4, 0.4, 0.4, 0.05);

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
            case HARD     -> 0.02;   // Very precise
            case HACKER   -> 0.003;  // Near-perfect aim
            case ADAPTIVE -> 0.05;
            case DUMMY    -> 0.0;
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
            case HARD     -> 40.0f;  // Fast tracking
            case HACKER   -> 90.0f;  // Instant snap-aim
            case ADAPTIVE -> 20.0f;
            case DUMMY    -> 0.0f;
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
            case HARD -> 0.65;  // Good prediction
            case HACKER -> 0.95; // Near-perfect prediction
            case ADAPTIVE -> 0.4;
            case DUMMY -> 0.0;
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
        PlayerInventory inv = bot.getInventory();
        double distance = bot.getLocation().distance(target.getLocation());

        // 1. Target blocking with shield → AXE to break it
        if (target.isBlocking()) {
            int axeSlot = findAxe(inv);
            if (axeSlot != -1) {
                inv.setHeldItemSlot(axeSlot);
                currentWeapon = WeaponType.AXE;
                return;
            }
        }

        // 2. Have mace + elytra + fireworks → MACE for dive attacks (far range only)
        if (distance > 6 && findMace(inv) != -1 && hasElytra(bot) && hasFireworks(bot)) {
            int maceSlot = hotbarSlot(inv, findMace(inv));
            if (maceSlot != -1) {
                inv.setHeldItemSlot(maceSlot);
                currentWeapon = WeaponType.MACE;
                return;
            }
        }

        // 3. Have mace + on ground + can jump → MACE for jump crits
        if (distance <= 4 && bot.isOnGround() && findMace(inv) != -1 
                && System.currentTimeMillis() - lastMaceJump > 3000) {
            int maceSlot = hotbarSlot(inv, findMace(inv));
            if (maceSlot != -1) {
                inv.setHeldItemSlot(maceSlot);
                currentWeapon = WeaponType.MACE;
                return;
            }
        }

        // 4. Mid-range with trident → SPEAR for throwing
        if (distance > 4 && distance <= 8) {
            int tridentSlot = findTrident(inv);
            if (tridentSlot != -1) {
                tridentSlot = hotbarSlot(inv, tridentSlot);
                if (tridentSlot != -1) {
                    inv.setHeldItemSlot(tridentSlot);
                    currentWeapon = WeaponType.TRIDENT;
                    return;
                }
            }
        }

        // 5. Axe kits: prefer AXE as primary weapon (axe PvP = crits + shield break)
        if (kitName != null && kitName.toLowerCase().contains("axe")) {
            int axeSlot = findAxe(inv);
            if (axeSlot != -1) {
                axeSlot = hotbarSlot(inv, axeSlot);
                if (axeSlot != -1) {
                    inv.setHeldItemSlot(axeSlot);
                    currentWeapon = WeaponType.AXE;
                    return;
                }
            }
        }

        // 6. Default: SWORD (best for combos and consistent DPS)
        for (int i = 0; i < 9; i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && isSword(item.getType())) {
                inv.setHeldItemSlot(i);
                currentWeapon = WeaponType.SWORD;
                return;
            }
        }

        // 6. Fallback: any melee weapon
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
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() == Material.BOW) return i;
        }
        return -1;
    }

    private int findCrossbow(PlayerInventory inv) {
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() == Material.CROSSBOW) return i;
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
    private boolean hasBow(Player bot) { return findBow(bot.getInventory()) != -1 || findCrossbow(bot.getInventory()) != -1; }
    private boolean hasOnlyBow(Player bot) { return findBow(bot.getInventory()) != -1; }
    private boolean hasCrossbow(Player bot) { return findCrossbow(bot.getInventory()) != -1; }
    private boolean hasBlocks(Player bot) { return findBuildingBlock(bot.getInventory()) != -1; }
    private boolean hasSplashPotions(Player bot) {
        return findItem(bot.getInventory(), Material.SPLASH_POTION) != -1;
    }
    private boolean hasElytra(Player bot) {
        ItemStack chest = bot.getInventory().getChestplate();
        if (chest != null && chest.getType() == Material.ELYTRA) return true;
        return findItem(bot.getInventory(), Material.ELYTRA) != -1;
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
        long cooldown = switch (difficulty) {
            case EASY     -> 3000;
            case MEDIUM   -> 2000;
            case HARD     -> 1200;  // Aggressive spear usage
            case HACKER   -> 800;   // Very aggressive
            case ADAPTIVE -> 1500;
            case DUMMY    -> 99999;
        };
        double chance = switch (difficulty) {
            case EASY     -> accuracy * 0.4;
            case MEDIUM   -> accuracy * 0.6;
            case HARD     -> accuracy * 0.8;   // High chance
            case HACKER   -> accuracy * 0.95;  // Almost always
            case ADAPTIVE -> accuracy * 0.6;
            case DUMMY    -> 0.0;
        };
        return random.nextDouble() < chance
                && System.currentTimeMillis() - lastSpearThrow > cooldown;
    }

    private boolean shouldThrowPotion() {
        return random.nextDouble() < accuracy * 0.5 
                && System.currentTimeMillis() - lastPotionTime > 3000;
    }

    private boolean shouldShootBow() {
        return random.nextDouble() < accuracy * 0.7 
                && System.currentTimeMillis() - lastBowShot > 1500;
    }

    private boolean shouldShootCrossbow() {
        return random.nextDouble() < accuracy * 0.8
                && System.currentTimeMillis() - lastCrossbowShot > 2500;
    }

    private boolean shouldUseElytra(double distance) {
        if (isUsingElytra || elytraDiveActive) return false;
        if (combatState != CombatState.IDLE) return false;
        long now = System.currentTimeMillis();
        // 6s cooldown between elytra uses
        if (now - lastElytraUse < 6000) return false;
        // Higher chance the farther the target is
        double chance = distance > 25 ? 0.15 : 0.06;
        return random.nextDouble() < chance;
    }

    public BotDifficulty getDifficulty() { return difficulty; }

    private enum WeaponType {
        FIST, SWORD, AXE, MACE, TRIDENT, SPEAR, BOW, CROSSBOW
    }
}

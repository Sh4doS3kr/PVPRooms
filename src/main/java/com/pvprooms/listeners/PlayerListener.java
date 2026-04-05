package com.pvprooms.listeners;

import com.pvprooms.PvPRoomsPro;
import com.pvprooms.model.Duel;
import com.pvprooms.managers.WallManager;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import com.pvprooms.model.ArenaTemplate;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Ghast;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Shulker;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.world.PortalCreateEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Listens for player events that affect duel and queue state.
 *
 * Handles:
 *  - Player disconnect while in queue or duel
 *  - Player death inside a duel world
 *  - Item drop prevention during duels
 *  - Food level management
 *  - Block break/place prevention for spectators
 */
public class PlayerListener implements Listener {

    private final PvPRoomsPro plugin;

    /** Last known safe (non-solid) location per dueling player for wall-clip rollback */
    private final Map<UUID, Location> lastSafeLoc = new HashMap<>();
    /** Death locations for spectator cam — player sees the lightning before being teleported */
    private final Map<UUID, Location> deathSpectatorLoc = new HashMap<>();
    /** Anti-clip scheduler task ID */
    private int antiClipTaskId = -1;

    public PlayerListener(PvPRoomsPro plugin) {
        this.plugin = plugin;
        startAntiClipTask();
    }

    /** Periodic validator (every 2 ticks) to catch elytra/packet-bypass wall clips */
    private void startAntiClipTask() {
        antiClipTaskId = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (org.bukkit.entity.Player p : plugin.getServer().getOnlinePlayers()) {
                UUID uid = p.getUniqueId();
                // Skip only if player is in creative mode (admins building)
                if (p.getGameMode() == org.bukkit.GameMode.CREATIVE) continue;
                // Skip death-cam spectators
                if (p.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;

                Location loc = p.getLocation();
                if (isInsideWall(loc)) {
                    Location safe = lastSafeLoc.get(uid);
                    Location target = safe != null ? safe : loc.clone().add(0, 1, 0);
                    if (!isInsideWall(target)) {
                        p.teleport(target);
                        // Stronger knockback: push away from the wall with upward boost
                        org.bukkit.util.Vector push = target.toVector().subtract(loc.toVector());
                        if (push.lengthSquared() > 0.001) {
                            push.normalize().multiply(0.8);
                        } else {
                            // Random upward ejection if no clear direction
                            push = new org.bukkit.util.Vector(
                                (Math.random() - 0.5) * 0.8,
                                0.6,
                                (Math.random() - 0.5) * 0.8
                            );
                        }
                        p.setVelocity(push);
                        p.sendMessage(plugin.prefix() + "§e¡Has sido expulsado de un bloque sólido!");
                    } else {
                        // No safe location - eject upward
                        Location ejectLoc = loc.clone().add(0, 2, 0);
                        p.teleport(ejectLoc);
                        p.setVelocity(new org.bukkit.util.Vector(0, 0.5, 0));
                        p.sendMessage(plugin.prefix() + "§e¡Expulsado hacia arriba para evitar quedar atrapado!");
                    }
                } else {
                    lastSafeLoc.put(uid, loc.clone());
                }

                // ── ANTI-FLOATING SAFETY: detect and fix stuck-in-air players ──
                // If player is in the lobby, not in a duel, and has broken movement state, fix it
                if (!plugin.getDuelManager().isInDuel(uid)
                        && !plugin.getDuelManager().isInFFA(uid)
                        && p.getGameMode() == GameMode.SURVIVAL) {
                    // Fix gravity being disabled
                    if (!p.hasGravity()) {
                        p.setGravity(true);
                    }
                    // Fix flight being enabled for non-staff
                    if (p.isFlying() && !p.hasPermission("pvprooms.staff")) {
                        p.setAllowFlight(false);
                        p.setFlying(false);
                    }
                    // Fix walk speed stuck at 0 or abnormal
                    if (p.getWalkSpeed() < 0.01f || p.getWalkSpeed() > 0.3f) {
                        p.setWalkSpeed(0.2f);
                    }
                    // Clear leftover freeze state
                    if (plugin.getDuelManager().isFrozen(uid)) {
                        plugin.getDuelManager().getPlayerDuelMap().remove(uid);
                        // Force unfreeze via the frozenPlayers set
                        plugin.getDuelManager().unfreezePlayer(uid);
                    }
                }
            }
        }, 2L, 2L).getTaskId();
    }

    // ── Join ───────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Ensure player is registered in EloManager (initializes if not exists)
        plugin.getEloManager().ensureRegistered(player.getUniqueId(), player.getName());

        // Safety: reset movement state on join (prevents stuck-in-air from previous session)
        player.setGravity(true);
        player.setWalkSpeed(0.2f);
        plugin.getDuelManager().unfreezePlayer(player.getUniqueId());
        if (!player.hasPermission("pvprooms.staff")) {
            player.setAllowFlight(false);
            player.setFlying(false);
        }

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            plugin.getScoreboardManager().showLobbyScoreboard(player);

            // Show pending Discord link code if any
            String pendingCode = plugin.getTierManager().getPendingCode(player.getUniqueId());
            if (pendingCode != null && plugin.getTierManager().getLinkedDiscord(player.getUniqueId()) == null) {
                player.sendMessage("§8§m──────────────────────────────");
                player.sendMessage(plugin.prefix() + "§b§lVinculación de Discord pendiente");
                player.sendMessage(plugin.prefix() + "§7Tu código es: §a§l" + pendingCode);
                player.sendMessage(plugin.prefix() + "§7Envíalo al bot de Discord para confirmar.");
                player.sendMessage("§8§m──────────────────────────────");
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1f);
            }

            // Fix stale spectator state from FFA disconnect
            if (player.getGameMode() == GameMode.SPECTATOR) {
                player.setGameMode(GameMode.SURVIVAL);
                player.teleport(plugin.getLobbySpawn());
                plugin.getLobbyManager().giveLobbyItems(player);
                return;
            }
            // Give lobby items if in lobby world
            if (plugin.getLobbyManager().isInLobby(player)) {
                plugin.getLobbyManager().giveLobbyItems(player);
            }
        }, 5L);
        
        // Detect player country from IP (async)
        detectCountry(player);
    }

    private void detectCountry(Player player) {
        String ip = player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : null;
        if (ip == null || ip.equals("127.0.0.1") || ip.startsWith("192.168.") || ip.startsWith("10.")) return;
        
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                java.net.URL url = new java.net.URL("https://ipapi.co/" + ip + "/country_code/");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestProperty("User-Agent", "PvPRoomsPro/1.0");
                
                if (conn.getResponseCode() == 200) {
                    try (java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(conn.getInputStream()))) {
                        String country = reader.readLine();
                        if (country != null && country.length() == 2) {
                            plugin.getEloManager().setCountry(player.getUniqueId(), country.toLowerCase());
                        }
                    }
                }
                conn.disconnect();
            } catch (Exception ignored) {}
        });
    }

    // ── World change → lobby ────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        // Only act when the player ARRIVES in the lobby world
        if (!plugin.getLobbyManager().isInLobby(player)) return;
        // Skip if still in an active duel (endDuel teleports before world change fires)
        if (plugin.getDuelManager().getDuelByPlayer(uuid) != null) return;
        if (plugin.getDuelManager().isInFFA(uuid)) return;

        if (player.getGameMode() != GameMode.CREATIVE) {
            player.setGameMode(GameMode.SURVIVAL);
        }
        plugin.getLobbyManager().giveLobbyItems(player);
        plugin.getScoreboardManager().restoreLobbyScoreboard(player);
    }

    // ── Disconnect ─────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        handlePlayerExit(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerKick(PlayerKickEvent event) {
        handlePlayerExit(event.getPlayer());
    }

    private void handlePlayerExit(Player player) {
        UUID uuid = player.getUniqueId();

        // Remove from queue
        if (plugin.getQueueManager().isInQueue(uuid)) {
            plugin.getQueueManager().removeFromQueue(uuid);
        }

        // End duel if in one (opponent wins)
        // IMPORTANT: check if they're a spectator first — spectators leaving should NOT end the match
        Duel duel = plugin.getDuelManager().getDuelByPlayer(uuid);
        if (duel != null && duel.getState() != Duel.State.ENDED) {
            if (duel.isSpectator(uuid)) {
                // Spectator leaving — just remove them, don't end the duel
                duel.removeSpectator(uuid);
                plugin.getDuelManager().getPlayerDuelMap().remove(uuid);
            } else {
                UUID opponentUUID = duel.getOpponent(uuid);
                if (opponentUUID != null) {
                    plugin.getDuelManager().endDuel(duel, opponentUUID, "disconnect");
                } else {
                    plugin.getDuelManager().endDuel(duel, null, "disconnect");
                }
            }
        }

        // Clean up FFA/2v2: dead spectators just get removed; active fighters trigger death logic
        if (plugin.getDuelManager().isFFASpectator(uuid)) {
            plugin.getDuelManager().removeFFASpectator(uuid);
        } else if (plugin.getDuelManager().isInFFA(uuid)) {
            UUID ffaMatchId = plugin.getDuelManager().getFFAMatchId(uuid);
            if (plugin.getDuelManager().is2v2(ffaMatchId)) {
                plugin.getDuelManager().handle2v2Death(player, null);
            } else {
                plugin.getDuelManager().handleFFADeath(player, null);
            }
        }

        // Handle party disconnect
        plugin.getPartyManager().handleDisconnect(uuid);

        // Remove scoreboard reference
        plugin.getScoreboardManager().clearScoreboard(player);
    }

    // ── Death ──────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player dead = event.getEntity();
        UUID uuid = dead.getUniqueId();

        // Check if in FFA match first
        if (plugin.getDuelManager().isInFFA(uuid)) {
            event.setDeathMessage(null);
            event.getDrops().clear();
            event.setDroppedExp(0);
            event.setKeepInventory(true);
            event.setKeepLevel(true);
            
            Player killer = dead.getKiller();
            UUID ffaMatchId = plugin.getDuelManager().getFFAMatchId(uuid);
            boolean is2v2 = plugin.getDuelManager().is2v2(ffaMatchId);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (dead.isDead()) {
                    dead.spigot().respawn();
                }
                if (is2v2) {
                    plugin.getDuelManager().handle2v2Death(dead, killer);
                } else {
                    plugin.getDuelManager().handleFFADeath(dead, killer);
                }
            }, 1L);
            return;
        }

        Duel duel = plugin.getDuelManager().getDuelByPlayer(uuid);
        if (duel == null || duel.getState() != Duel.State.FIGHTING) return;

        // Suppress default death behaviour
        event.setDeathMessage(null);
        event.getDrops().clear();
        event.setDroppedExp(0);
        event.setKeepInventory(true);
        event.setKeepLevel(true);

        // Determine winner
        UUID winnerUUID = duel.getOpponent(uuid);
        Location deathLoc = dead.getLocation().clone();

        // Calculate scores for title display
        int winnerScore, loserScore;
        boolean isMatchWin = false;
        if (duel.isMultiRound()) {
            winnerScore = duel.getWins(winnerUUID) + 1;
            loserScore = duel.getWins(uuid);
            isMatchWin = winnerScore >= duel.getWinsNeeded();
        } else {
            winnerScore = 1;
            loserScore = 0;
        }
        final int fWinnerScore = winnerScore;
        final int fLoserScore = loserScore;
        final boolean fIsMatchWin = isMatchWin;

        // Save death location — onPlayerRespawn will place them here
        deathSpectatorLoc.put(uuid, deathLoc);

        // Tick 20 (~1s): Force respawn — allows vanilla death animation to show briefly
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (dead.isDead()) dead.spigot().respawn();
        }, 20L);

        // Tick 22 (~1.1s): Spectator mode + Lightning + Titles
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (dead.isOnline() && deathSpectatorLoc.containsKey(uuid)) {
                dead.setGameMode(org.bukkit.GameMode.SPECTATOR);
                dead.teleport(deathLoc.clone().add(0, 1.5, 0));
            }

            // Cosmetic lightning at death location
            World w = deathLoc.getWorld();
            if (w != null) w.strikeLightningEffect(deathLoc);

            Player deadP = org.bukkit.Bukkit.getPlayer(uuid);
            Player winnerP = org.bukkit.Bukkit.getPlayer(winnerUUID);

            if (fIsMatchWin) {
                // Tier match winner — 6s title (120 ticks stay)
                if (deadP != null) {
                    deadP.sendTitle("§cHas perdido el match",
                            "§9" + fLoserScore + "§f-§c" + fWinnerScore, 0, 120, 20);
                }
                if (winnerP != null) {
                    winnerP.sendTitle("§a¡Ganaste el match!",
                            "§9" + fWinnerScore + "§f-§c" + fLoserScore, 0, 120, 20);
                }
            } else {
                // Regular kill — 3.5s title (70 ticks stay)
                if (deadP != null) {
                    deadP.sendTitle("§cHas muerto",
                            "§9" + fLoserScore + "§f-§c" + fWinnerScore, 0, 70, 0);
                }
                if (winnerP != null) {
                    winnerP.sendTitle("§aGanaste!",
                            "§9" + fWinnerScore + "§f-§c" + fLoserScore, 0, 70, 0);
                }
            }
        }, 22L);

        // End duel after title duration: 3.5s (70t) or 6s (120t) after tick 22
        long endDelay = fIsMatchWin ? 142L : 92L; // 22 + 120 or 22 + 70
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            deathSpectatorLoc.remove(uuid);
            plugin.getDuelManager().endDuel(duel, winnerUUID, "death");
        }, endDelay);
    }

    // ── Respawn ────────────────────────────────────────────────────────────

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        // FFA active fighters: let handleFFADeath teleport them as spectators — don't redirect to lobby
        if (plugin.getDuelManager().isInFFA(uuid) && !plugin.getDuelManager().isFFASpectator(uuid)) {
            return;
        }
        // If the player died in a duel, respawn at death location (spectator cam)
        Location deathLoc = deathSpectatorLoc.get(uuid);
        if (deathLoc != null) {
            event.setRespawnLocation(deathLoc);
            return;
        }

        // Otherwise, respawn at lobby
        Duel duel = plugin.getDuelManager().getDuelByPlayer(uuid);
        if (duel == null || duel.getState() == Duel.State.ENDED) {
            event.setRespawnLocation(plugin.getLobbySpawn());
        }
    }

    // ── Item drop prevention ───────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onItemDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        Duel duel = plugin.getDuelManager().getDuelByPlayer(player.getUniqueId());
        if (duel != null && duel.getState() != Duel.State.ENDED) {
            // Allow drops during fight; some kits involve throwing items
            // Change to event.setCancelled(true) if you want to prevent it
        }
    }

    // ── Movement freeze (countdown, no-walls arenas) ───────────────────────

    // ── Wall clip detection ────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerMoveWallClip(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Check for ALL players (not just duel participants) - anti-clip should work everywhere
        // Skip only if player is in creative mode (admins building)
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) return;
        // Skip death-cam spectators
        if (player.getGameMode() == org.bukkit.GameMode.SPECTATOR) return;

        Location to = event.getTo();
        if (to == null) return;

        // Skip pure head-rotation events
        Location from = event.getFrom();
        if (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ()) return;

        if (isInsideWall(to)) {
            event.setTo(from);
            Location safe = lastSafeLoc.get(uuid);
            Location target = safe != null ? safe : (!isInsideWall(from) ? from : null);
            if (target != null) {
                player.teleport(target);
                // Stronger knockback: push away from the wall with upward boost
                org.bukkit.util.Vector push = target.toVector().subtract(to.toVector());
                if (push.lengthSquared() > 0.001) {
                    push.normalize().multiply(0.8); // Stronger push
                } else {
                    // If no clear direction, push upward and random horizontal
                    push = new org.bukkit.util.Vector(
                        (Math.random() - 0.5) * 0.8,
                        0.6, // Upward boost
                        (Math.random() - 0.5) * 0.8
                    );
                }
                player.setVelocity(push);
                player.sendMessage(plugin.prefix() + "§e¡Has sido expulsado de un bloque sólido!");
            } else {
                // No safe location found - try to eject upward
                Location ejectLoc = to.clone().add(0, 2, 0);
                player.teleport(ejectLoc);
                player.setVelocity(new org.bukkit.util.Vector(0, 0.5, 0));
                player.sendMessage(plugin.prefix() + "§e¡Expulsado hacia arriba para evitar quedar atrapado!");
            }
        } else {
            lastSafeLoc.put(uuid, to.clone());
        }
    }

    /**
     * Returns true if the player's body occupies a solid full-cube block.
     * Excludes stairs, slabs, trapdoors, doors, fences, walls, and other
     * non-full-cube blocks that players can legitimately stand in/on.
     */
    private boolean isInsideWall(Location loc) {
        World w = loc.getWorld();
        int bx = loc.getBlockX(), bz = loc.getBlockZ();
        Block feet = w.getBlockAt(bx, (int) Math.floor(loc.getY()), bz);
        if (isWallBlock(feet)) return true;
        Block chest = w.getBlockAt(bx, (int) Math.floor(loc.getY() + 0.9), bz);
        if (isWallBlock(chest)) return true;
        Block eyes = w.getBlockAt(bx, (int) Math.floor(loc.getY() + 1.62), bz);
        return isWallBlock(eyes);
    }

    /**
     * Returns true only for full solid cubes that a player should never be inside.
     * Returns false for stairs, slabs, trapdoors, doors, fences, walls, signs,
     * beds, chests, and other non-full blocks.
     */
    private boolean isWallBlock(Block block) {
        if (block.isPassable()) return false;
        Material type = block.getType();
        if (!type.isSolid()) return false;
        // Must be occluding (full opaque cube) — stairs, slabs, etc. are NOT occluding
        if (!type.isOccluding()) return false;
        String name = type.name();
        // Extra safety: exclude any block with these keywords
        if (name.contains("STAIR") || name.contains("SLAB") || name.contains("STEP")
                || name.contains("FENCE") || name.contains("WALL") || name.contains("GATE")
                || name.contains("DOOR") || name.contains("TRAPDOOR") || name.contains("SIGN")
                || name.contains("BED") || name.contains("CHEST") || name.contains("ANVIL")
                || name.contains("BREWING") || name.contains("ENCHANT") || name.contains("HOPPER")
                || name.contains("LANTERN") || name.contains("CAMPFIRE") || name.contains("BELL")
                || name.contains("CANDLE") || name.contains("CHAIN") || name.contains("CARPET")
                || name.contains("PISTON") || name.contains("SKULL") || name.contains("HEAD")
                || name.contains("BANNER") || name.contains("POT")) {
            return false;
        }
        return true;
    }

    // ── Movement freeze (countdown, no-walls arenas) ───────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!plugin.getDuelManager().isFrozen(event.getPlayer().getUniqueId())) return;
        org.bukkit.Location from = event.getFrom();
        org.bukkit.Location to   = event.getTo();
        if (to == null) return;
        // Block horizontal movement (X/Z) but ALLOW gravity (Y decrease).
        // This prevents the bug where players get stuck floating in the air during countdown.
        if (from.getX() != to.getX() || from.getZ() != to.getZ()) {
            // Allow falling (Y decrease) and head rotation, block horizontal movement
            from.setY(to.getY());
            from.setYaw(to.getYaw());
            from.setPitch(to.getPitch());
            event.setTo(from);
        }
    }

    // ── Projectile / item block during countdown ──────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();

        // Block during regular duel countdown
        Duel duel = plugin.getDuelManager().getDuelByPlayer(uuid);
        if (duel != null && duel.getState() == Duel.State.COUNTDOWN) {
            event.setCancelled(true);
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    "\u00a7c\u00a7l¡Espera al inicio!"));
            return;
        }

        // Block during bot duel countdown
        if (plugin.getBotManager().isInBotCountdown(uuid)) {
            event.setCancelled(true);
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    "\u00a7c\u00a7l¡Espera al inicio!"));
        }
    }

    // ── Creeper spawn egg (Explosivo kit) ────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onCreeperEggUse(PlayerInteractEvent event) {
        if (event.getHand() == null) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR) return;
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.CREEPER_SPAWN_EGG) return;

        // Only active explosivo duels (regular or bot)
        boolean inExplosivo = false;
        Duel duel = plugin.getDuelManager().getDuelByPlayer(player.getUniqueId());
        if (duel != null && duel.getState() == Duel.State.FIGHTING
                && "explosivo".equalsIgnoreCase(duel.getKitName())) {
            inExplosivo = true;
        }
        if (!inExplosivo) {
            var botDuel = plugin.getBotManager().getBotDuel(player.getUniqueId());
            if (botDuel != null && "explosivo".equalsIgnoreCase(botDuel.kitName)) inExplosivo = true;
        }
        if (!inExplosivo) return;

        // Cancel vanilla handling (bypasses DO_MOB_SPAWNING gamerule)
        event.setCancelled(true);

        // Determine spawn location
        Location spawnLoc;
        if (event.getClickedBlock() != null) {
            spawnLoc = event.getClickedBlock().getRelative(event.getBlockFace()).getLocation().add(0.5, 0.0, 0.5);
        } else {
            spawnLoc = player.getEyeLocation().add(player.getLocation().getDirection().multiply(3));
        }

        // Spawn via CUSTOM reason — already whitelisted in onCreatureSpawn
        player.getWorld().spawnEntity(spawnLoc, EntityType.CREEPER);

        // Consume one egg from the correct hand
        if (event.getHand() == EquipmentSlot.OFF_HAND) {
            ItemStack offhand = player.getInventory().getItemInOffHand();
            if (offhand.getAmount() <= 1) player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
            else offhand.setAmount(offhand.getAmount() - 1);
        } else {
            if (item.getAmount() <= 1) player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            else item.setAmount(item.getAmount() - 1);
        }
    }

    // ── Golden Head consumption ─────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onGoldenHeadUse(org.bukkit.event.player.PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (!plugin.getLobbyManager().isGoldenHeadItem(item)) return;

        event.setCancelled(true);

        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 600, 0, false, true));

        if (item.getAmount() <= 1) {
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        } else {
            item.setAmount(item.getAmount() - 1);
        }

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_BURP, 1.0f, 1.0f);
        player.sendActionBar(net.kyori.adventure.text.Component.text("§6§lGolden Head §eactivado!"));
    }

    // ── Food level ─────────────────────────────────────────────────────────

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (plugin.getLobbyManager().isInLobby(player)) {
            event.setCancelled(true);
            return;
        }
        Duel duel = plugin.getDuelManager().getDuelByPlayer(player.getUniqueId());
        if (duel != null && duel.getState() == Duel.State.COUNTDOWN) {
            event.setCancelled(true);
        }
    }

    // ── Wall setup tool selection ──────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onWallToolInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!player.hasPermission("pvprooms.admin")) return;
        if (!plugin.getWallManager().isSetupTool(player.getInventory().getItemInMainHand())) return;

        org.bukkit.block.Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        String wallId = plugin.getWallManager().getToolWallId(player.getInventory().getItemInMainHand());
        if (wallId == null) return;

        org.bukkit.event.block.Action action = event.getAction();
        if (action == org.bukkit.event.block.Action.LEFT_CLICK_BLOCK) {
            event.setCancelled(true);
            plugin.getWallManager().setPos1(player.getUniqueId(), clicked.getLocation());
            player.sendMessage(plugin.prefix() + "§7[§e" + wallId + "§7] §ePunto §6A§e: §f"
                    + clicked.getX() + ", " + clicked.getY() + ", " + clicked.getZ());
        } else if (action == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            plugin.getWallManager().setPos2(player.getUniqueId(), clicked.getLocation());
            player.sendMessage(plugin.prefix() + "§7[§e" + wallId + "§7] §ePunto §6B§e: §f"
                    + clicked.getX() + ", " + clicked.getY() + ", " + clicked.getZ());
            if (plugin.getWallManager().hasFullSelection(player.getUniqueId())) {
                player.sendMessage(plugin.prefix()
                        + "§a¡Selección completa! Ahora: §f/admin setupwall " + wallId + " <tipo_bloque>");
            }
        }
    }

    // ── Fire protection in lobby ───────────────────────────────────────────

    @EventHandler(ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        String lobbyWorld = plugin.getConfig().getString("general.lobby-world", "world");
        if (event.getBlock().getWorld().getName().equals(lobbyWorld)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        String lobbyWorld = plugin.getConfig().getString("general.lobby-world", "world");
        if (event.getBlock().getWorld().getName().equals(lobbyWorld)) {
            event.setCancelled(true);
        }
    }

    // ── Advancement messages ────────────────────────────────────────────────

    @EventHandler
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        event.message(null);
    }

    // ── Block break / place by spectators ─────────────────────────────────

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        // Bloquear en el mundo lobby salvo OPs
        String lobbyWorld = plugin.getConfig().getString("general.lobby-world", "world");
        if (event.getBlock().getWorld().getName().equals(lobbyWorld) && !player.isOp()) {
            event.setCancelled(true);
            player.sendMessage(plugin.prefix() + "§cNo puedes romper bloques en el spawn.");
            return;
        }
        // Check any active duel world (covers pvp_match_* AND pvp_pool_* worlds)
        String blockWorld = event.getBlock().getWorld().getName();
        Duel duel = plugin.getDuelManager().getDuelByWorldName(blockWorld);
        if (duel != null) {
            if (duel.isSpectator(player.getUniqueId())) { event.setCancelled(true); return; }
            if (!duel.getArenaTemplate().isAllowBlockBreak()) event.setCancelled(true);
            return;
        }
        // FFA match world
        ArenaTemplate ffaTemplate = plugin.getDuelManager().getFFATemplateByWorldName(blockWorld);
        if (ffaTemplate != null) {
            if (!ffaTemplate.isAllowBlockBreak()) event.setCancelled(true);
            return;
        }
        // Block spectators breaking blocks in any other context
        Duel playerDuel = plugin.getDuelManager().getDuelByPlayer(player.getUniqueId());
        if (playerDuel != null && playerDuel.isSpectator(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        // Bloquear en el mundo lobby salvo OPs
        String lobbyWorld = plugin.getConfig().getString("general.lobby-world", "world");
        if (event.getBlock().getWorld().getName().equals(lobbyWorld) && !player.isOp()) {
            event.setCancelled(true);
            player.sendMessage(plugin.prefix() + "§cNo puedes colocar bloques en el spawn.");
            return;
        }
        // Check any active duel world (covers pvp_match_* AND pvp_pool_* worlds)
        String blockWorld = event.getBlock().getWorld().getName();
        Duel duel = plugin.getDuelManager().getDuelByWorldName(blockWorld);
        if (duel != null) {
            if (duel.isSpectator(player.getUniqueId())) { event.setCancelled(true); return; }
            if (!duel.getArenaTemplate().isAllowBlockPlace()) event.setCancelled(true);
            return;
        }
        // FFA match world
        ArenaTemplate ffaTemplate = plugin.getDuelManager().getFFATemplateByWorldName(blockWorld);
        if (ffaTemplate != null) {
            if (!ffaTemplate.isAllowBlockPlace()) event.setCancelled(true);
            return;
        }
        // Bot duel world
        var botDuel = plugin.getBotManager().getBotDuel(player.getUniqueId());
        if (botDuel != null && botDuel.instanceWorldName.equals(blockWorld)) {
            if (!botDuel.template.isAllowBlockPlace()) event.setCancelled(true);
            return;
        }
        // Block spectators placing blocks in any other context
        Duel playerDuel = plugin.getDuelManager().getDuelByPlayer(player.getUniqueId());
        if (playerDuel != null && playerDuel.isSpectator(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    // ── Mob spawn prevention (global) ───────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM) return;
        if (isMob(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        World w = event.getWorld();
        applyNoMobGamerules(w);
        // Disable auto-save on every world that loads — prevents the 5-min HDD spike
        w.setAutoSave(false);
    }

    private boolean isMob(org.bukkit.entity.Entity entity) {
        return entity instanceof Monster
                || entity instanceof Slime
                || entity instanceof Ghast
                || entity instanceof Phantom
                || entity instanceof Shulker;
    }

    public static void applyNoMobGamerules(org.bukkit.World world) {
        world.setGameRule(org.bukkit.GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(org.bukkit.GameRule.DO_PATROL_SPAWNING, false);
        world.setGameRule(org.bukkit.GameRule.DO_TRADER_SPAWNING, false);
    }

    // ── Explosion control in arenas ──────────────────────────────────────────

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        handleExplosion(event.getLocation().getWorld(), event.blockList());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        handleExplosion(event.getBlock().getWorld(), event.blockList());
    }

    private void handleExplosion(org.bukkit.World world, java.util.List<org.bukkit.block.Block> blockList) {
        if (world == null) return;
        String worldName = world.getName();
        String lobbyWorld = plugin.getConfig().getString("general.lobby-world", "world");
        // Always protect lobby
        if (worldName.equals(lobbyWorld)) { blockList.clear(); return; }
        String instancePrefix = plugin.getConfig().getString("arenas.instance-prefix", "pvp_match_");
        if (!worldName.startsWith(instancePrefix)) return;
        
        // Check duel first, then FFA
        Duel duel = plugin.getDuelManager().getDuelByWorldName(worldName);
        ArenaTemplate template = duel != null ? duel.getArenaTemplate() : null;
        if (template == null) {
            // Check FFA matches
            template = plugin.getDuelManager().getFFATemplateByWorldName(worldName);
        }
        
        // If explosions not allowed, prevent block damage (entity damage still applies)
        if (template == null || !template.isAllowExplosions()) {
            blockList.clear();
        }
    }

    // ── Teleport-out prevention ────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();
        if (to == null) return;
        
        // ═══ ENDERPEARL BLOCK GLITCH PREVENTION ═══
        // Only blocks pearls that would trap player inside solid blocks
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL) {
            // Check if destination would trap player inside solid blocks (both feet and head level)
            Block destBlock = to.getBlock();
            Block destBlockAbove = to.clone().add(0, 1, 0).getBlock();
            
            // Only block if BOTH feet and head would be inside solid blocks (truly stuck)
            if (isFullySolidBlock(destBlock) && isFullySolidBlock(destBlockAbove)) {
                // Try to find a safe location nearby
                Location safeLoc = findSafeLocation(to);
                if (safeLoc != null) {
                    event.setTo(safeLoc);
                } else {
                    event.setCancelled(true);
                    player.sendMessage(plugin.prefix() + "§c¡Enderpearl bloqueada! Destino inválido.");
                    return;
                }
            }
        }
        
        // Block /tp commands that bypass the arena during countdown
        Duel duel = plugin.getDuelManager().getDuelByPlayer(player.getUniqueId());
        if (duel != null && duel.getState() == Duel.State.COUNTDOWN) {
            // Spectators are allowed to teleport freely (they follow the world swap)
            if (duel.getSpectators().contains(player.getUniqueId())) return;

            PlayerTeleportEvent.TeleportCause cause = event.getCause();
            if (cause == PlayerTeleportEvent.TeleportCause.COMMAND
                    || cause == PlayerTeleportEvent.TeleportCause.PLUGIN) {
                // Allow only teleports within the same instance world (use currentWorldName to handle pool swaps)
                if (!to.getWorld().getName().equals(duel.getCurrentWorldName())) {
                    event.setCancelled(true);
                    player.sendMessage(plugin.prefix() + "§cNo puedes teletransportarte durante la cuenta atrás.");
                }
            }
        }
    }
    
    /**
     * Check if a block is fully solid and would trap a player
     * More strict than isSolid - excludes fences, glass, etc.
     */
    private boolean isFullySolidBlock(Block block) {
        if (block == null) return false;
        Material type = block.getType();
        // Must be solid AND occluding (full cube that blocks light/vision)
        // This excludes fences, glass panes, stairs, slabs, etc.
        return type.isSolid() && type.isOccluding() && !type.name().contains("FENCE") 
                && !type.name().contains("WALL") && !type.name().contains("GATE");
    }
    
    /**
     * Find a safe location near the destination (not inside blocks)
     */
    private Location findSafeLocation(Location loc) {
        World world = loc.getWorld();
        if (world == null) return null;
        
        // Check positions around the destination
        int[][] offsets = {{0,1,0}, {0,2,0}, {1,0,0}, {-1,0,0}, {0,0,1}, {0,0,-1}};
        for (int[] offset : offsets) {
            Location check = loc.clone().add(offset[0], offset[1], offset[2]);
            Block foot = check.getBlock();
            Block head = check.clone().add(0, 1, 0).getBlock();
            if (!isFullySolidBlock(foot) && !isFullySolidBlock(head)) {
                return check;
            }
        }
        return null;
    }

    // ── Block Nether and End travel ───────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerPortal(PlayerPortalEvent event) {
        World.Environment env = event.getTo() != null ? event.getTo().getWorld().getEnvironment() : null;
        if (env == World.Environment.NETHER || env == World.Environment.THE_END) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.prefix() + "§cNo puedes viajar al Nether ni al End.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPortalCreate(PortalCreateEvent event) {
        // Block portal creation entirely
        if (event.getReason() == PortalCreateEvent.CreateReason.FIRE || 
            event.getReason() == PortalCreateEvent.CreateReason.NETHER_PAIR ||
            event.getReason() == PortalCreateEvent.CreateReason.END_PLATFORM) {
            event.setCancelled(true);
        }
    }

}

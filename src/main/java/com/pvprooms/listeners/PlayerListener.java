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
import org.bukkit.entity.Monster;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Ghast;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Shulker;
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

    public PlayerListener(PvPRoomsPro plugin) {
        this.plugin = plugin;
    }

    // ── Join ───────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Ensure player is registered in EloManager (initializes if not exists)
        plugin.getEloManager().ensureRegistered(player.getUniqueId(), player.getName());
        
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            plugin.getScoreboardManager().showLobbyScoreboard(player);
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
        Duel duel = plugin.getDuelManager().getDuelByPlayer(uuid);
        if (duel != null && duel.getState() != Duel.State.ENDED) {
            UUID opponentUUID = duel.getOpponent(uuid);
            if (opponentUUID != null) {
                plugin.getDuelManager().endDuel(duel, opponentUUID, "disconnect");
            } else {
                plugin.getDuelManager().endDuel(duel, null, "disconnect");
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
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (dead.isDead()) {
                    dead.spigot().respawn();
                }
                plugin.getDuelManager().handleFFADeath(dead, killer);
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

        // Schedule duel end on next tick (death handling must complete first)
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (dead.isDead()) {
                dead.spigot().respawn();
            }
            plugin.getDuelManager().endDuel(duel, winnerUUID, "death");
        }, 1L);
    }

    // ── Respawn ────────────────────────────────────────────────────────────

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        // If they just died in a duel, respawn at lobby
        // The endDuel method will also teleport them, but just in case:
        Duel duel = plugin.getDuelManager().getDuelByPlayer(player.getUniqueId());
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

        // Only check active combatants — skip spectators, lobby, countdown
        Duel duel = plugin.getDuelManager().getDuelByPlayer(uuid);
        if (duel == null || duel.getState() != Duel.State.FIGHTING) return;
        if (duel.getSpectators().contains(uuid)) return;

        Location to = event.getTo();
        if (to == null) return;

        // Skip pure head-rotation events
        Location from = event.getFrom();
        if (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ()) return;

        if (isInsideWall(to)) {
            event.setCancelled(true);
            // If from is also inside a wall (teleport hack), push to last known safe spot
            if (isInsideWall(from)) {
                Location safe = lastSafeLoc.get(uuid);
                if (safe != null) player.teleport(safe);
            }
            player.sendActionBar(net.kyori.adventure.text.Component.text("§c§lMovimiento inválido"));
        } else {
            lastSafeLoc.put(uuid, to.clone());
        }
    }

    /** Returns true if both feet-level and eye-level blocks at loc are non-passable (solid wall). */
    private boolean isInsideWall(Location loc) {
        Block feet = loc.getBlock();
        Block eyes = loc.getWorld().getBlockAt(loc.getBlockX(),
                (int) Math.floor(loc.getY() + 1.62), loc.getBlockZ());
        return !feet.isPassable() && !eyes.isPassable();
    }

    // ── Movement freeze (countdown, no-walls arenas) ───────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!plugin.getDuelManager().isFrozen(event.getPlayer().getUniqueId())) return;
        org.bukkit.Location from = event.getFrom();
        org.bukkit.Location to   = event.getTo();
        if (to == null) return;
        // Only block actual position change; allow yaw/pitch (head rotation)
        if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
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

    // ── Creeper Launcher (explosivo kit only) ────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onCreeperLauncherUse(org.bukkit.event.player.PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (!plugin.getLobbyManager().isCreeperLauncherItem(item)) return;

        event.setCancelled(true);

        int used = plugin.getLobbyManager().getCreeperCount(player.getUniqueId());
        if (used >= com.pvprooms.managers.LobbyManager.MAX_CREEPERS_PER_DUEL) {
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    "§c¡Límite alcanzado! §7(" + com.pvprooms.managers.LobbyManager.MAX_CREEPERS_PER_DUEL + " creepers máx)"));
            return;
        }
        plugin.getLobbyManager().incrementCreeperCount(player.getUniqueId());

        Vector dir = player.getLocation().getDirection().normalize();
        Location spawnLoc = player.getEyeLocation().clone().add(dir.clone().multiply(1.5));

        org.bukkit.entity.Creeper creeper = (org.bukkit.entity.Creeper) player.getWorld().spawnEntity(
                spawnLoc, org.bukkit.entity.EntityType.CREEPER);
        creeper.setPowered(true);
        creeper.setMaxFuseTicks(10);
        creeper.setFuseTicks(10);
        creeper.setVelocity(dir.clone().multiply(1.5));

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_CREEPER_PRIMED, 1.0f, 1.2f);

        // Explode on ground contact
        new org.bukkit.scheduler.BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (!creeper.isValid() || creeper.isDead()) { cancel(); return; }
                if (++ticks > 100) {
                    creeper.getWorld().createExplosion(creeper.getLocation(), 4.0f, false, true);
                    creeper.remove();
                    cancel();
                    return;
                }
                if (creeper.isOnGround() || ticks > 2) {
                    // Only trigger on ground after initial launch arc
                    if (ticks > 2 && creeper.isOnGround()) {
                        creeper.getWorld().createExplosion(creeper.getLocation(), 4.0f, false, true);
                        creeper.remove();
                        cancel();
                    }
                }
            }
        }.runTaskTimer(plugin, 2L, 1L);
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

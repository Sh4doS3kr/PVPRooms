package com.pvprooms;

import com.pvprooms.commands.*;
import com.pvprooms.commands.SetSpawnCommand;
import com.pvprooms.commands.TrimCommand;
import com.pvprooms.commands.KitTrimCommand;
import com.pvprooms.gui.AdminPanelGUI;
import com.pvprooms.gui.ArenaConfigGUI;
import com.pvprooms.gui.KitTrimGUI;
import com.pvprooms.gui.QueueModeGUI;
import com.pvprooms.gui.TrimGUI;
import com.pvprooms.managers.HealthHologramManager;
import com.pvprooms.gui.KitGUI;
import com.pvprooms.managers.TrimManager;
import com.pvprooms.managers.WallManager;
import com.pvprooms.listeners.CombatListener;
import com.pvprooms.listeners.InventoryListener;
import com.pvprooms.listeners.KitTrimGUIListener;
import com.pvprooms.listeners.PlayerListener;
import com.pvprooms.listeners.TrimCrateListener;
import com.pvprooms.listeners.TrimGUIListener;
import com.pvprooms.api.TierApiServer;
import com.pvprooms.listeners.SpearListener;
import com.pvprooms.managers.TierManager;
import com.pvprooms.model.TrimCrate;
import com.pvprooms.util.RegionDetector;
import com.pvprooms.weapons.SpearItem;
import com.pvprooms.managers.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

/**
 * PvPRoomsPro — Professional PvP duels plugin for Paper 1.21.x
 *
 * Entry point. Initialises all managers, registers commands and listeners.
 *
 * Manager load order:
 *  1. KitManager        — loads kits.yml
 *  2. ArenaManager      — loads arenas.yml
 *  3. ArenaInstanceManager — world cloning utility
 *  4. EloManager        — loads elo.yml
 *  5. ScoreboardManager — scoreboard API wrapper
 *  6. DuelManager       — live duel state
 *  7. QueueManager      — matchmaking queue + runnable
 *  8. KitGUI            — GUI builder
 */
public class PvPRoomsPro extends JavaPlugin {

    // ── Managers ───────────────────────────────────────────────────────────
    private KitManager kitManager;
    private ArenaManager arenaManager;
    private ArenaInstanceManager arenaInstanceManager;
    private EloManager eloManager;
    private ScoreboardManager scoreboardManager;
    private DuelManager duelManager;
    private QueueManager queueManager;
    private KitGUI kitGUI;
    private AdminPanelGUI adminPanelGUI;
    private ArenaConfigGUI arenaConfigGUI;
    private QueueModeGUI queueModeGUI;
    private WallManager wallManager;
    private HealthHologramManager healthHologramManager;
    private TierManager tierManager;
    private TierApiServer tierApiServer;
    private PersonalKitManager personalKitManager;
    private TrimManager trimManager;
    private TrimGUI trimGUI;
    private KitTrimGUI kitTrimGUI;
    private NpcManager npcManager;
    private LeaderboardHologramManager hologramManager;
    private StatsManager statsManager;
    /** Detected or configured server region code (e.g. "eu", "na"). */
    private volatile String serverRegion = "eu";

    // ── Plugin lifecycle ───────────────────────────────────────────────────

    @Override
    public void onEnable() {
        // Create data folder and save default configs
        saveDefaultConfig();
        getDataFolder().mkdirs();
        new File(getDataFolder(), "maps").mkdirs();

        // Initialise managers
        personalKitManager   = new PersonalKitManager(this);
        trimManager          = new TrimManager(this);
        TrimCrate.init(this);
        kitManager           = new KitManager(this);
        arenaManager         = new ArenaManager(this);
        arenaInstanceManager = new ArenaInstanceManager(this);
        eloManager           = new EloManager(this);
        scoreboardManager    = new ScoreboardManager(this);
        duelManager          = new DuelManager(this);
        queueManager         = new QueueManager(this);
        kitGUI               = new KitGUI(this);
        adminPanelGUI        = new AdminPanelGUI(this);
        arenaConfigGUI          = new ArenaConfigGUI();
        queueModeGUI            = new QueueModeGUI();
        wallManager             = new WallManager(this);
        healthHologramManager   = new HealthHologramManager(this);
        tierManager             = new TierManager(this);
        statsManager            = new StatsManager(this);
        trimGUI                 = new TrimGUI(this);
        kitTrimGUI              = new KitTrimGUI(this);
        npcManager              = new NpcManager(this);
        hologramManager         = new LeaderboardHologramManager(this);
        SpearItem.init(this);

        // Detect server region asynchronously
        String fallback = getConfig().getString("server.region", "eu");
        serverRegion = fallback;
        RegionDetector.detectAsync(this, fallback, region -> {
            serverRegion = region;
            // Start API server after region is known
            int apiPort = getConfig().getInt("web-api.port", 27090);
            if (getConfig().getBoolean("web-api.enabled", true)) {
                tierApiServer = new TierApiServer(this, apiPort);
                tierApiServer.start();
            }
        });

        // Start matchmaking runnable
        queueManager.startMatchmaking();

        // Start lobby scoreboard task
        scoreboardManager.startLobbyTask();

        // Apply lobby world settings (always day, no weather) one tick after load
        Bukkit.getScheduler().runTaskLater(this, this::applyLobbyWorldSettings, 1L);

        // Disable mob spawning in all already-loaded worlds
        Bukkit.getScheduler().runTaskLater(this, () ->
                Bukkit.getWorlds().forEach(com.pvprooms.listeners.PlayerListener::applyNoMobGamerules), 1L);

        // Register commands
        registerCommands();

        // Register event listeners
        registerListeners();

        getLogger().info("§aPvPRoomsPro enabled successfully!");
        getLogger().info("§7Kits loaded: " + kitManager.getAllKits().size());
        getLogger().info("§7Arenas loaded: " + arenaManager.getAllArenas().size());
    }

    @Override
    public void onDisable() {
        // Stop matchmaking
        if (queueManager != null) {
            queueManager.stopMatchmaking();
        }

        // Stop lobby scoreboard task
        if (scoreboardManager != null) {
            scoreboardManager.stopLobbyTask();
        }

        // End all active duels gracefully (copy to list first to avoid ConcurrentModificationException)
        if (duelManager != null) {
            new java.util.ArrayList<>(duelManager.getActiveDuels())
                    .forEach(duel -> duelManager.endDuel(duel, null, "server shutdown"));
        }

        // Stop web API server
        if (tierApiServer != null) tierApiServer.stop();

        // Save tier data
        if (tierManager != null) tierManager.save();

        // Save trim data
        if (trimManager != null) trimManager.save();

        // Save stats data
        if (statsManager != null) statsManager.save();

        // Shutdown NPC and Hologram managers
        if (npcManager != null) npcManager.shutdown();
        if (hologramManager != null) hologramManager.shutdown();

        // Cancel all scheduled tasks owned by this plugin
        Bukkit.getScheduler().cancelTasks(this);

        getLogger().info("PvPRoomsPro disabled.");
    }

    // ── Registration helpers ───────────────────────────────────────────────

    private void registerCommands() {
        KitCommand kitCmd = new KitCommand(this);
        getCommand("kit").setExecutor(kitCmd);
        getCommand("kit").setTabCompleter(kitCmd);

        ArenaCommand arenaCmd = new ArenaCommand(this);
        getCommand("arena").setExecutor(arenaCmd);
        getCommand("arena").setTabCompleter(arenaCmd);

        getCommand("queue").setExecutor(new QueueCommand(this));

        DuelCommand duelCmd = new DuelCommand(this);
        getCommand("duel").setExecutor(duelCmd);
        getCommand("duel").setTabCompleter(duelCmd);

        SpectateCommand spectateCmd = new SpectateCommand(this);
        getCommand("spectate").setExecutor(spectateCmd);
        getCommand("spectate").setTabCompleter(spectateCmd);

        getCommand("pvpleave").setExecutor(new LeaveCommand(this));

        StatsCommand statsCmd = new StatsCommand(this);
        getCommand("stats").setExecutor(statsCmd);
        getCommand("stats").setTabCompleter(statsCmd);

        getCommand("top").setExecutor(new TopCommand(this));

        getCommand("setspawn").setExecutor(new SetSpawnCommand(this, false));
        getCommand("setspawnworld").setExecutor(new SetSpawnCommand(this, true));
        getCommand("spawn").setExecutor(new SpawnCommand(this));

        AdminCommand adminCmd = new AdminCommand(this);
        getCommand("admin").setExecutor(adminCmd);
        getCommand("admin").setTabCompleter(adminCmd);
        getCommand("adminpanel").setExecutor(adminCmd);

        TrimCommand trimCmd = new TrimCommand(this);
        getCommand("trim").setExecutor(trimCmd);
        getCommand("trim").setTabCompleter(trimCmd);

        KitTrimCommand kitTrimCmd = new KitTrimCommand(this);
        getCommand("kittrim").setExecutor(kitTrimCmd);
        getCommand("kittrim").setTabCompleter(kitTrimCmd);

        NpcCommand npcCmd = new NpcCommand(this);
        getCommand("npc").setExecutor(npcCmd);
        getCommand("npc").setTabCompleter(npcCmd);

        HoloCommand holoCmd = new HoloCommand(this);
        getCommand("holo").setExecutor(holoCmd);
        getCommand("holo").setTabCompleter(holoCmd);

        AdminNpcHoloCommand adminNpcHoloCmd = new AdminNpcHoloCommand(this);
        // Admin command already registered, just add NPC/Holo subcommands via the existing /admin
    }

    private void registerListeners() {
        var pm = getServer().getPluginManager();
        pm.registerEvents(new InventoryListener(this), this);
        pm.registerEvents(new PlayerListener(this), this);
        pm.registerEvents(new CombatListener(this), this);
        pm.registerEvents(new SpearListener(this), this);
        pm.registerEvents(new TrimGUIListener(this), this);
        pm.registerEvents(new KitTrimGUIListener(this), this);
        pm.registerEvents(new TrimCrateListener(this), this);
    }

    // ── Lobby world setup ──────────────────────────────────────────────────

    private void applyLobbyWorldSettings() {
        org.bukkit.World w = getLobbySpawn().getWorld();
        if (w == null) return;
        w.setGameRule(org.bukkit.GameRule.DO_DAYLIGHT_CYCLE, false);
        w.setGameRule(org.bukkit.GameRule.DO_WEATHER_CYCLE, false);
        w.setGameRule(org.bukkit.GameRule.DO_MOB_SPAWNING, false);
        w.setTime(6000L);
        w.setStorm(false);
        w.setThundering(false);
        w.setWeatherDuration(Integer.MAX_VALUE);
        getLogger().info("Lobby world '" + w.getName() + "': siempre día, sin lluvia.");
    }

    // ── Utility ────────────────────────────────────────────────────────────

    /**
     * Returns the colour-formatted plugin prefix defined in config.yml.
     */
    public String prefix() {
        return ChatColor.translateAlternateColorCodes('&',
                getConfig().getString("general.prefix", "&8[&cPvP&4Rooms&8] &r"));
    }

    /**
     * Builds the lobby spawn Location from config values.
     * Falls back to world spawn if the configured world is not found.
     */
    public Location getLobbySpawn() {
        String worldName = getConfig().getString("general.lobby-world", "world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            getLogger().warning("Lobby world '" + worldName + "' not found! Falling back to default world spawn.");
            world = Bukkit.getWorlds().get(0);
        }
        double x     = getConfig().getDouble("general.lobby-spawn.x", 0.5);
        double y     = getConfig().getDouble("general.lobby-spawn.y", 64.0);
        double z     = getConfig().getDouble("general.lobby-spawn.z", 0.5);
        float  yaw   = (float) getConfig().getDouble("general.lobby-spawn.yaw", 0.0);
        float  pitch = (float) getConfig().getDouble("general.lobby-spawn.pitch", 0.0);
        return new Location(world, x, y, z, yaw, pitch);
    }

    // ── Manager accessors ──────────────────────────────────────────────────

    public KitManager            getKitManager()           { return kitManager; }
    public ArenaManager          getArenaManager()         { return arenaManager; }
    public ArenaInstanceManager  getArenaInstanceManager() { return arenaInstanceManager; }
    public EloManager            getEloManager()           { return eloManager; }
    public ScoreboardManager     getScoreboardManager()    { return scoreboardManager; }
    public DuelManager           getDuelManager()          { return duelManager; }
    public QueueManager          getQueueManager()         { return queueManager; }
    public KitGUI                getKitGUI()               { return kitGUI; }
    public AdminPanelGUI         getAdminPanelGUI()        { return adminPanelGUI; }
    public ArenaConfigGUI        getArenaConfigGUI()        { return arenaConfigGUI; }
    public QueueModeGUI          getQueueModeGUI()          { return queueModeGUI; }
    public WallManager           getWallManager()          { return wallManager; }
    public HealthHologramManager getHealthHologramManager() { return healthHologramManager; }
    public TierManager           getTierManager()           { return tierManager; }
    public PersonalKitManager    getPersonalKitManager()    { return personalKitManager; }
    public TrimManager           getTrimManager()            { return trimManager; }
    public TrimGUI               getTrimGUI()                { return trimGUI; }
    public KitTrimGUI            getKitTrimGUI()             { return kitTrimGUI; }
    public NpcManager            getNpcManager()              { return npcManager; }
    public LeaderboardHologramManager getHologramManager()    { return hologramManager; }
    public StatsManager          getStatsManager()            { return statsManager; }
    public String                getServerRegion()           { return serverRegion; }
}

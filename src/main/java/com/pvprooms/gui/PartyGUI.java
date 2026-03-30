package com.pvprooms.gui;

import com.pvprooms.PvPRoomsPro;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

/**
 * GUI for managing parties.
 */
public class PartyGUI implements Listener {

    private final PvPRoomsPro plugin;
    private static final String GUI_TITLE = "Gestión de Party";
    private static final String INVITE_TITLE = "Invitar Jugador";
    private static final String MATCH_TITLE = "Configurar Partida";
    private static final String KIT_SELECT_TITLE = "Seleccionar Kit";
    private static final String ARENA_SELECT_TITLE = "Seleccionar Arena";

    public PartyGUI(PvPRoomsPro plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        UUID uuid = player.getUniqueId();
        var pm = plugin.getPartyManager();

        if (!pm.isInParty(uuid)) {
            openNoPartyMenu(player);
        } else {
            openPartyMenu(player);
        }
    }

    private void openNoPartyMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, Component.text(GUI_TITLE, NamedTextColor.LIGHT_PURPLE));

        // Fill background
        ItemStack glass = createItem(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, glass);
        }

        // Create party button (slot 11)
        inv.setItem(11, createItem(Material.EMERALD, "Crear Party", 
                List.of("", "§7Click para crear una nueva party", "§7y poder invitar jugadores")));

        // Invite player button (slot 13)
        inv.setItem(13, createItem(Material.PLAYER_HEAD, "Invitar Jugador",
                List.of("", "§7Click para ver la lista de", "§7jugadores online e invitar")));

        // Close button (slot 15)
        inv.setItem(15, createItem(Material.BARRIER, "Cerrar", List.of("", "§7Click para cerrar")));

        player.openInventory(inv);
    }

    private void openPartyMenu(Player player) {
        UUID uuid = player.getUniqueId();
        var pm = plugin.getPartyManager();
        UUID leaderUUID = pm.getPartyLeader(uuid);
        Set<UUID> members = pm.getPartyMembers(leaderUUID);
        boolean isLeader = pm.isPartyLeader(uuid);

        Inventory inv = Bukkit.createInventory(null, 45, Component.text(GUI_TITLE, NamedTextColor.LIGHT_PURPLE));

        // Fill background
        ItemStack glass = createItem(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < 45; i++) {
            inv.setItem(i, glass);
        }

        // Party info (slot 4)
        Player leader = Bukkit.getPlayer(leaderUUID);
        String leaderName = leader != null ? leader.getName() : "???";
        inv.setItem(4, createItem(Material.CAKE, "Tu Party", 
                List.of("", "§7Lider: §e" + leaderName, "§7Miembros: §e" + members.size())));

        // Show members (slots 19-25)
        int slot = 19;
        for (UUID memberUUID : members) {
            if (slot > 25) break;
            Player member = Bukkit.getPlayer(memberUUID);
            String memberName = member != null ? member.getName() : "???";
            boolean isThisLeader = memberUUID.equals(leaderUUID);
            
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (member != null) meta.setOwningPlayer(member);
            
            String title = (isThisLeader ? "§6" : "§f") + memberName + (isThisLeader ? " §e(Lider)" : "");
            meta.displayName(Component.text(title).decoration(TextDecoration.ITALIC, false));
            
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            if (member != null) {
                lore.add(Component.text("Online", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(Component.text("Offline", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
            }
            if (isLeader && !memberUUID.equals(uuid)) {
                lore.add(Component.empty());
                lore.add(Component.text("Click para expulsar", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
            head.setItemMeta(meta);
            inv.setItem(slot++, head);
        }

        // Action buttons
        if (isLeader) {
            // Start match button (slot 38)
            inv.setItem(38, createItem(Material.DIAMOND_SWORD, "Iniciar Partida",
                    List.of("", "§aClick para iniciar partida", "§7con todos los miembros")));

            // Invite button (slot 39)
            inv.setItem(39, createItem(Material.EMERALD, "Invitar Jugador",
                    List.of("", "§7Click para invitar", "§7a un jugador online")));

            // Close button (slot 40)
            inv.setItem(40, createItem(Material.BARRIER, "Cerrar", List.of("", "§7Click para cerrar")));

            // Disband button (slot 41)
            inv.setItem(41, createItem(Material.TNT, "Disolver Party",
                    List.of("", "§cClick para disolver", "§cla party completamente")));
        } else {
            // Leave button (slot 39)
            inv.setItem(39, createItem(Material.IRON_DOOR, "Abandonar Party",
                    List.of("", "§cClick para salir", "§cde la party")));

            // Close button (slot 40)
            inv.setItem(40, createItem(Material.BARRIER, "Cerrar", List.of("", "§7Click para cerrar")));
        }

        player.openInventory(inv);
    }

    public void openInviteMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, Component.text(INVITE_TITLE, NamedTextColor.GREEN));

        // Fill background
        ItemStack glass = createItem(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, glass);
        }

        // Show online players (excluding self and party members)
        UUID playerUUID = player.getUniqueId();
        var pm = plugin.getPartyManager();
        Set<UUID> partyMembers = pm.isInParty(playerUUID) 
                ? pm.getPartyMembers(pm.getPartyLeader(playerUUID)) 
                : Set.of(playerUUID);

        int slot = 10;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (slot > 43) break;
            if (partyMembers.contains(online.getUniqueId())) continue;
            if (pm.isInParty(online.getUniqueId())) continue; // Already in another party

            // Skip slots at edges
            if (slot % 9 == 0 || slot % 9 == 8) {
                slot++;
                continue;
            }

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(online);
            meta.displayName(Component.text(online.getName(), NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    Component.empty(),
                    Component.text("Click para invitar", NamedTextColor.GREEN)
                            .decoration(TextDecoration.ITALIC, false)
            ));
            head.setItemMeta(meta);
            inv.setItem(slot++, head);
        }

        // Back button (slot 49)
        inv.setItem(49, createItem(Material.ARROW, "Volver", List.of("", "§7Click para volver")));

        player.openInventory(inv);
    }

    private ItemStack createItem(Material material, String name, List<String> loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        if (loreLines != null && !loreLines.isEmpty()) {
            List<Component> lore = new ArrayList<>();
            for (String line : loreLines) {
                if (line.isEmpty()) {
                    lore.add(Component.empty());
                } else {
                    lore.add(Component.text(line).decoration(TextDecoration.ITALIC, false));
                }
            }
            meta.lore(lore);
        }
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().title().toString();
        
        if (!title.contains(GUI_TITLE) && !title.contains(INVITE_TITLE) 
                && !title.contains(MATCH_TITLE) && !title.contains(KIT_SELECT_TITLE) 
                && !title.contains(ARENA_SELECT_TITLE)) return;
        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        if (clicked.getType() == Material.GRAY_STAINED_GLASS_PANE) return;

        var pm = plugin.getPartyManager();
        UUID uuid = player.getUniqueId();

        // Handle invite menu
        if (title.contains(INVITE_TITLE)) {
            if (clicked.getType() == Material.ARROW) {
                open(player);
                return;
            }
            if (clicked.getType() == Material.PLAYER_HEAD) {
                SkullMeta meta = (SkullMeta) clicked.getItemMeta();
                if (meta.getOwningPlayer() != null) {
                    Player target = meta.getOwningPlayer().getPlayer();
                    if (target != null && target.isOnline()) {
                        // Auto-create party if not in one
                        if (!pm.isInParty(uuid)) {
                            pm.createParty(player);
                        }
                        pm.invitePlayer(player, target);
                        player.closeInventory();
                    }
                }
            }
            return;
        }

        // Handle main menu
        Material type = clicked.getType();

        switch (type) {
            case BARRIER -> player.closeInventory();
            
            case EMERALD -> {
                if (!pm.isInParty(uuid)) {
                    pm.createParty(player);
                }
                openInviteMenu(player);
            }
            
            case PLAYER_HEAD -> {
                if (!pm.isInParty(uuid)) {
                    // No party - open invite menu
                    openInviteMenu(player);
                } else if (pm.isPartyLeader(uuid)) {
                    // Leader clicking on a member - kick them
                    SkullMeta meta = (SkullMeta) clicked.getItemMeta();
                    if (meta.getOwningPlayer() != null) {
                        Player target = meta.getOwningPlayer().getPlayer();
                        if (target != null && !target.equals(player)) {
                            pm.kickPlayer(player, target);
                            open(player); // Refresh
                        }
                    }
                }
            }
            
            case TNT -> {
                pm.disbandParty(player);
                player.closeInventory();
            }
            
            case IRON_DOOR -> {
                pm.leaveParty(player);
                player.closeInventory();
            }
            
            case DIAMOND_SWORD -> {
                if (pm.isPartyLeader(uuid)) {
                    openMatchConfigMenu(player);
                }
            }
        }

        // Handle match config menu
        if (title.contains(MATCH_TITLE)) {
            handleMatchConfigClick(player, clicked);
        }

        // Handle kit select menu
        if (title.contains(KIT_SELECT_TITLE)) {
            handleKitSelectClick(player, clicked);
        }

        // Handle arena select menu  
        if (title.contains(ARENA_SELECT_TITLE)) {
            handleArenaSelectClick(player, clicked);
        }
    }

    // ── Match Configuration ─────────────────────────────────────────────────

    private final Map<UUID, String> selectedKit = new HashMap<>();
    private final Map<UUID, String> selectedArena = new HashMap<>();

    public void openMatchConfigMenu(Player player) {
        var pm = plugin.getPartyManager();
        if (!pm.isPartyLeader(player.getUniqueId())) return;

        Set<UUID> members = pm.getPartyMembers(player.getUniqueId());
        int memberCount = members.size();

        Inventory inv = Bukkit.createInventory(null, 27, Component.text(MATCH_TITLE, NamedTextColor.GOLD));

        ItemStack glass = createItem(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < 27; i++) inv.setItem(i, glass);

        // FFA option (slot 11)
        inv.setItem(11, createItem(Material.IRON_SWORD, "FFA (Todos contra todos)",
                List.of("", "§7Todos los miembros pelean", "§7entre si. Ultimo en pie gana.", "",
                        "§eJugadores: §f" + memberCount)));

        // Select Kit (slot 13)
        String kit = selectedKit.getOrDefault(player.getUniqueId(), "Ninguno");
        inv.setItem(13, createItem(Material.CHEST, "Seleccionar Kit",
                List.of("", "§7Kit actual: §e" + kit, "", "§aClick para cambiar")));

        // Select Arena (slot 15)
        String arena = selectedArena.getOrDefault(player.getUniqueId(), "Aleatoria");
        inv.setItem(15, createItem(Material.GRASS_BLOCK, "Seleccionar Arena",
                List.of("", "§7Arena actual: §e" + arena, "", "§aClick para cambiar")));

        // Start button (slot 22)
        boolean ready = selectedKit.containsKey(player.getUniqueId());
        if (ready) {
            inv.setItem(22, createItem(Material.LIME_WOOL, "INICIAR PARTIDA",
                    List.of("", "§aClick para iniciar FFA", "§7con " + memberCount + " jugadores")));
        } else {
            inv.setItem(22, createItem(Material.RED_WOOL, "Selecciona un kit",
                    List.of("", "§cDebes seleccionar un kit", "§cpara iniciar la partida")));
        }

        // Back button (slot 18)
        inv.setItem(18, createItem(Material.ARROW, "Volver", List.of("", "§7Volver al menu")));

        player.openInventory(inv);
    }

    public void openKitSelectMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 36, Component.text(KIT_SELECT_TITLE, NamedTextColor.YELLOW));

        ItemStack glass = createItem(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < 36; i++) inv.setItem(i, glass);

        int slot = 10;
        for (String kitName : plugin.getKitManager().getKitNames()) {
            if (slot > 25) break;
            if (slot % 9 == 0 || slot % 9 == 8) { slot++; continue; }

            var kit = plugin.getKitManager().getKit(kitName);
            Material icon = kit != null ? kit.getIconMaterial() : Material.DIAMOND_SWORD;
            inv.setItem(slot++, createItem(icon, kitName,
                    List.of("", "§aClick para seleccionar")));
        }

        inv.setItem(31, createItem(Material.ARROW, "Volver", List.of("", "§7Volver")));
        player.openInventory(inv);
    }

    public void openArenaSelectMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 36, Component.text(ARENA_SELECT_TITLE, NamedTextColor.GREEN));

        ItemStack glass = createItem(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < 36; i++) inv.setItem(i, glass);

        // Random option
        inv.setItem(10, createItem(Material.ENDER_PEARL, "Aleatoria",
                List.of("", "§7Selecciona una arena", "§7al azar")));

        int slot = 11;
        for (var arena : plugin.getArenaManager().getAllArenas()) {
            if (slot > 25) break;
            if (slot % 9 == 0 || slot % 9 == 8) { slot++; continue; }

            inv.setItem(slot++, createItem(Material.GRASS_BLOCK, arena.getName(),
                    List.of("", "§aClick para seleccionar")));
        }

        inv.setItem(31, createItem(Material.ARROW, "Volver", List.of("", "§7Volver")));
        player.openInventory(inv);
    }

    private void handleMatchConfigClick(Player player, ItemStack clicked) {
        Material type = clicked.getType();
        
        switch (type) {
            case ARROW -> openMatchConfigMenu(player);
            case CHEST -> openKitSelectMenu(player);
            case GRASS_BLOCK -> openArenaSelectMenu(player);
            case LIME_WOOL -> startPartyFFA(player);
            case IRON_SWORD -> {} // FFA info, no action
        }
    }

    private void handleKitSelectClick(Player player, ItemStack clicked) {
        if (clicked.getType() == Material.ARROW) {
            openMatchConfigMenu(player);
            return;
        }
        if (clicked.getType() == Material.GRAY_STAINED_GLASS_PANE) return;

        String kitName = clicked.getItemMeta().getDisplayName();
        if (kitName != null && !kitName.isEmpty()) {
            // Remove color codes
            kitName = kitName.replaceAll("§.", "");
            selectedKit.put(player.getUniqueId(), kitName);
            player.sendMessage(plugin.prefix() + "§aKit seleccionado: §e" + kitName);
            openMatchConfigMenu(player);
        }
    }

    private void handleArenaSelectClick(Player player, ItemStack clicked) {
        if (clicked.getType() == Material.ARROW) {
            openMatchConfigMenu(player);
            return;
        }
        if (clicked.getType() == Material.GRAY_STAINED_GLASS_PANE) return;

        String arenaName = clicked.getItemMeta().getDisplayName();
        if (arenaName != null) {
            arenaName = arenaName.replaceAll("§.", "");
            selectedArena.put(player.getUniqueId(), arenaName);
            player.sendMessage(plugin.prefix() + "§aArena seleccionada: §e" + arenaName);
            openMatchConfigMenu(player);
        }
    }

    private void startPartyFFA(Player player) {
        var pm = plugin.getPartyManager();
        UUID leaderUUID = player.getUniqueId();
        
        if (!pm.isPartyLeader(leaderUUID)) {
            player.sendMessage(plugin.prefix() + "§cSolo el lider puede iniciar partidas.");
            return;
        }

        String kitName = selectedKit.get(leaderUUID);
        if (kitName == null) {
            player.sendMessage(plugin.prefix() + "§cDebes seleccionar un kit primero.");
            return;
        }

        Set<UUID> members = pm.getPartyMembers(leaderUUID);
        if (members.size() < 2) {
            player.sendMessage(plugin.prefix() + "§cNecesitas al menos 2 jugadores para iniciar.");
            return;
        }

        // Check all members are online and not in duel/queue
        for (UUID memberUUID : members) {
            Player member = Bukkit.getPlayer(memberUUID);
            if (member == null || !member.isOnline()) {
                player.sendMessage(plugin.prefix() + "§cAlgunos miembros no están online.");
                return;
            }
            if (plugin.getDuelManager().isInDuel(memberUUID)) {
                player.sendMessage(plugin.prefix() + "§c" + member.getName() + " está en un duelo.");
                return;
            }
            if (plugin.getQueueManager().isInQueue(memberUUID)) {
                player.sendMessage(plugin.prefix() + "§c" + member.getName() + " está en cola.");
                return;
            }
        }

        player.closeInventory();

        // Announce to all members
        for (UUID memberUUID : members) {
            Player member = Bukkit.getPlayer(memberUUID);
            if (member != null) {
                member.sendMessage(plugin.prefix() + "§a¡Partida FFA iniciada! Kit: §e" + kitName);
                member.sendMessage(plugin.prefix() + "§7Jugadores: §f" + members.size());
            }
        }

        // TODO: Create actual FFA arena instance and teleport players
        // For now, just announce
        player.sendMessage(plugin.prefix() + "§e[FFA] Sistema en desarrollo. Próximamente...");
        
        // Clean up selections
        selectedKit.remove(leaderUUID);
        selectedArena.remove(leaderUUID);
    }
}

package com.pvprooms.commands;

import com.pvprooms.PvPRoomsPro;
import com.pvprooms.model.ArmorPiece;
import com.pvprooms.model.PhysicalTrimCrate;
import com.pvprooms.model.HelmetTrimBlock;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Command to place physical trim crates in the world.
 * /crate <type> <piece> [legendary]
 */
public class PhysicalCrateCommand implements CommandExecutor, TabCompleter {

    private final PvPRoomsPro plugin;
    private static NamespacedKey keyPieceTag;
    
    // Players in setup mode: UUID -> {piece, legendary}
    private static final Map<UUID, SetupData> setupMode = new HashMap<>();

    public PhysicalCrateCommand(PvPRoomsPro plugin) {
        this.plugin = plugin;
        keyPieceTag = new NamespacedKey(plugin, "crate_key_piece");
    }
    
    /** Data for players in setup mode */
    public record SetupData(ArmorPiece piece, boolean legendary) {}

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cSolo jugadores.");
            return true;
        }

        if (!player.isOp()) {
            player.sendMessage(plugin.prefix() + "§cSolo OPs pueden usar este comando.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String type = args[0].toLowerCase();
        
        // Handle "key" subcommand - /crate key <piece> OR /crate key give <user> <piece> OR /crate key giveall <piece>
        if (type.equals("key")) {
            if (args.length < 2) {
                player.sendMessage(plugin.prefix() + "§cUso: /crate key <pieza>");
                player.sendMessage(plugin.prefix() + "§cUso: /crate key give <usuario> <pieza>");
                player.sendMessage(plugin.prefix() + "§cUso: /crate key giveall <pieza>");
                return true;
            }
            
            String subCmd = args[1].toLowerCase();
            
            // /crate key give <user> <piece>
            if (subCmd.equals("give")) {
                if (args.length < 4) {
                    player.sendMessage(plugin.prefix() + "§cUso: /crate key give <usuario> <pieza>");
                    return true;
                }
                Player target = plugin.getServer().getPlayer(args[2]);
                if (target == null) {
                    player.sendMessage(plugin.prefix() + "§cJugador no encontrado.");
                    return true;
                }
                ArmorPiece keyPiece = ArmorPiece.fromName(args[3].toLowerCase());
                if (keyPiece == null) {
                    player.sendMessage(plugin.prefix() + "§cPieza inválida. Usa: helmet, chestplate, leggings, boots");
                    return true;
                }
                giveKeyTo(target, keyPiece);
                player.sendMessage(plugin.prefix() + "§aDiste una §6" + keyPiece.getDisplayName() + " Key §aa §e" + target.getName());
                return true;
            }
            
            // /crate key giveall <piece>
            if (subCmd.equals("giveall")) {
                if (args.length < 3) {
                    player.sendMessage(plugin.prefix() + "§cUso: /crate key giveall <pieza>");
                    return true;
                }
                ArmorPiece keyPiece = ArmorPiece.fromName(args[2].toLowerCase());
                if (keyPiece == null) {
                    player.sendMessage(plugin.prefix() + "§cPieza inválida. Usa: helmet, chestplate, leggings, boots");
                    return true;
                }
                int count = 0;
                for (Player online : plugin.getServer().getOnlinePlayers()) {
                    giveKeyTo(online, keyPiece);
                    count++;
                }
                // Broadcast to all players
                for (Player online : plugin.getServer().getOnlinePlayers()) {
                    online.sendMessage(plugin.prefix() + "§a§l¡KEYALL! §eTodos han recibido una §6" + keyPiece.getDisplayName() + " Key§e!");
                }
                player.sendMessage(plugin.prefix() + "§a§lKEYALL completado. §f" + count + " §ajugadores recibieron una §6" + keyPiece.getDisplayName() + " Key");
                return true;
            }
            
            // /crate key <piece> (give to self)
            ArmorPiece keyPiece = ArmorPiece.fromName(subCmd);
            if (keyPiece == null) {
                player.sendMessage(plugin.prefix() + "§cPieza inválida. Usa: helmet, chestplate, leggings, boots");
                player.sendMessage(plugin.prefix() + "§7O usa: /crate key give <usuario> <pieza>");
                player.sendMessage(plugin.prefix() + "§7O usa: /crate key giveall <pieza>");
                return true;
            }
            giveKey(player, keyPiece);
            return true;
        }

        // Handle "helmetblock" subcommand
        if (type.equals("helmetblock")) {
            giveHelmetBlock(player);
            return true;
        }
        
        // Handle "colocar" subcommand - enter setup mode
        if (type.equals("colocar")) {
            if (args.length < 2) {
                player.sendMessage(plugin.prefix() + "§cUso: /crate colocar <pieza> [legendary]");
                player.sendMessage(plugin.prefix() + "§7Piezas: helmet, chestplate, leggings, boots");
                return true;
            }
            ArmorPiece piece = ArmorPiece.fromName(args[1].toLowerCase());
            if (piece == null) {
                player.sendMessage(plugin.prefix() + "§cPieza inválida. Usa: helmet, chestplate, leggings, boots");
                return true;
            }
            boolean legendary = args.length >= 3 && args[2].equalsIgnoreCase("legendary");
            
            setupMode.put(player.getUniqueId(), new SetupData(piece, legendary));
            player.sendMessage(plugin.prefix() + "§a§l¡MODO COLOCACIÓN ACTIVADO!");
            player.sendMessage(plugin.prefix() + "§7Haz §eclick derecho §7en un bloque para convertirlo en una §dCrate de " + piece.getDisplayName() + 
                (legendary ? " §5§lLEGENDARIA" : ""));
            player.sendMessage(plugin.prefix() + "§7Escribe §c/crate cancelar §7para salir del modo colocación.");
            return true;
        }
        
        // Handle "cancelar" subcommand - exit setup mode
        if (type.equals("cancelar")) {
            if (setupMode.remove(player.getUniqueId()) != null) {
                player.sendMessage(plugin.prefix() + "§cModo colocación cancelado.");
            } else {
                player.sendMessage(plugin.prefix() + "§7No estabas en modo colocación.");
            }
            return true;
        }

        // Need at least 2 arguments: type and piece
        if (args.length < 2) {
            sendHelp(player);
            return true;
        }

        String pieceName = args[1].toLowerCase();
        boolean legendary = args.length >= 3 && args[2].equalsIgnoreCase("legendary");

        // Validate type
        if (!type.equals("normal") && !type.equals("themed")) {
            player.sendMessage(plugin.prefix() + "§cTipo inválido. Usa: normal, themed, helmetblock");
            return true;
        }

        // Validate armor piece
        ArmorPiece piece = ArmorPiece.fromName(pieceName);
        if (piece == null) {
            player.sendMessage(plugin.prefix() + "§cPieza inválida. Usa: helmet, chestplate, leggings, boots");
            return true;
        }

        // Create crate item
        ItemStack crateItem = PhysicalTrimCrate.createCrateItem(type, piece, legendary);
        
        // Give to player
        player.getInventory().addItem(crateItem);
        player.sendMessage(plugin.prefix() + "§aRecibiste una crate " + 
            (legendary ? "§5§lLEGENDARIA" : type.equals("themed") ? "§e§lTEMÁTICA" : "§b§lNORMAL") + 
            " §apara " + piece.getDisplayName() + "§a. Colócala en el mundo.");

        return true;
    }

    /** Gives a piece-specific crate key to the player (with message) */
    private void giveKey(Player player, ArmorPiece piece) {
        ItemStack key = createCrateKey(piece);
        player.getInventory().addItem(key);
        player.sendMessage(plugin.prefix() + "§aRecibiste una §6" + piece.getDisplayName() + " Key§a. Úsala para abrir crates de " + piece.getDisplayName() + ".");
    }
    
    /** Gives a piece-specific crate key to a target player (silent, for keyall) */
    private void giveKeyTo(Player target, ArmorPiece piece) {
        ItemStack key = createCrateKey(piece);
        target.getInventory().addItem(key);
    }

    /** Gives a helmet trim block to the player */
    private void giveHelmetBlock(Player player) {
        ItemStack block = HelmetTrimBlock.createBlockItem();
        player.getInventory().addItem(block);
        player.sendMessage(plugin.prefix() + "§aRecibiste un §6Bloque de Trims de Casco§a. Colócalo en el mundo y úsalo con una llave.");
    }

    /** Creates a piece-specific crate key item */
    private ItemStack createCrateKey(ArmorPiece piece) {
        ItemStack key = new ItemStack(Material.TRIPWIRE_HOOK);
        org.bukkit.inventory.meta.ItemMeta meta = key.getItemMeta();
        meta.setDisplayName("§6§l✦ §e" + piece.getDisplayName() + " Key §6§l✦");
        meta.setLore(List.of(
            "§7Usa esta llave para abrir crates de",
            "§d" + piece.getDisplayName() + " §7en el mundo.",
            "",
            "§8▸ Click derecho en una crate de " + piece.getDisplayName().toLowerCase()
        ));
        // Store piece type in persistent data
        meta.getPersistentDataContainer().set(keyPieceTag, PersistentDataType.STRING, piece.name());
        meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        key.setItemMeta(meta);
        return key;
    }
    
    /** Gets the piece type from a key item */
    public static ArmorPiece getKeyPiece(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        if (keyPieceTag == null) return null;
        String pieceName = item.getItemMeta().getPersistentDataContainer().get(keyPieceTag, PersistentDataType.STRING);
        if (pieceName == null) return null;
        try {
            return ArmorPiece.valueOf(pieceName);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
    
    public static NamespacedKey getKeyPieceTag() { return keyPieceTag; }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) {
            return List.of("normal", "themed", "key", "helmetblock", "colocar", "cancelar");
        }
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("key")) {
                return List.of("helmet", "chestplate", "leggings", "boots", "give", "giveall");
            }
            return List.of("helmet", "chestplate", "leggings", "boots");
        }
        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("key") && args[1].equalsIgnoreCase("give")) {
                return null; // Player names
            }
            if (args[0].equalsIgnoreCase("key") && args[1].equalsIgnoreCase("giveall")) {
                return List.of("helmet", "chestplate", "leggings", "boots");
            }
            return List.of("legendary");
        }
        if (args.length == 4) {
            if (args[0].equalsIgnoreCase("key") && args[1].equalsIgnoreCase("give")) {
                return List.of("helmet", "chestplate", "leggings", "boots");
            }
        }
        return List.of();
    }
    
    // ══════════════════════════════════════════════════════════════════════
    // Setup mode utilities
    // ══════════════════════════════════════════════════════════════════════
    
    public static boolean isInSetupMode(UUID playerId) {
        return setupMode.containsKey(playerId);
    }
    
    public static SetupData getSetupData(UUID playerId) {
        return setupMode.get(playerId);
    }
    
    public static void exitSetupMode(UUID playerId) {
        setupMode.remove(playerId);
    }

    private void sendHelp(Player p) {
        p.sendMessage("§5§m          §r §dCrates Físicas §5§m          ");
        p.sendMessage("§7/crate §f<tipo> <pieza> [legendary]");
        p.sendMessage("§7/crate §fkey <pieza> §8- Da llave a ti mismo");
        p.sendMessage("§7/crate §fkey give <usuario> <pieza> §8- Da llave a jugador");
        p.sendMessage("§7/crate §fkey giveall <pieza> §8- Da llave a TODOS (keyall)");
        p.sendMessage("§7/crate §fhelmetblock §8- Da bloque de trims de casco");
        p.sendMessage("§7/crate §fcolocar <pieza> [legendary] §8- Modo colocación");
        p.sendMessage("§7/crate §fcancelar §8- Cancela modo colocación");
        p.sendMessage("");
        p.sendMessage("§7Piezas: §fhelmet, chestplate, leggings, boots");
        p.sendMessage("");
        p.sendMessage("§eEjemplos:");
        p.sendMessage("§7/crate key chestplate");
        p.sendMessage("§7/crate key give Notch helmet");
        p.sendMessage("§7/crate key giveall boots §8(evento keyall)");
    }
}

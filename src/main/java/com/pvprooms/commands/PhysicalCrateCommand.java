package com.pvprooms.commands;

import com.pvprooms.PvPRoomsPro;
import com.pvprooms.model.ArmorPiece;
import com.pvprooms.model.PhysicalTrimCrate;
import com.pvprooms.model.HelmetTrimBlock;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Command to place physical trim crates in the world.
 * /crate <type> <piece> [legendary]
 */
public class PhysicalCrateCommand implements CommandExecutor, TabCompleter {

    private final PvPRoomsPro plugin;

    public PhysicalCrateCommand(PvPRoomsPro plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cSolo jugadores.");
            return true;
        }

        if (!player.hasPermission("pvprooms.admin")) {
            player.sendMessage(plugin.prefix() + "§cSin permiso.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String type = args[0].toLowerCase();
        
        // Handle "key" subcommand
        if (type.equals("key")) {
            if (!player.hasPermission("pvprooms.admin")) {
                player.sendMessage(plugin.prefix() + "§cSin permiso.");
                return true;
            }
            giveKey(player);
            return true;
        }

        // Handle "helmetblock" subcommand
        if (type.equals("helmetblock")) {
            giveHelmetBlock(player);
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

    /** Gives a crate key to the player */
    private void giveKey(Player player) {
        ItemStack key = createCrateKey();
        player.getInventory().addItem(key);
        player.sendMessage(plugin.prefix() + "§aRecibiste una §6Llave de Crate§a. Úsala para abrir crates.");
    }

    /** Gives a helmet trim block to the player */
    private void giveHelmetBlock(Player player) {
        ItemStack block = HelmetTrimBlock.createBlockItem();
        player.getInventory().addItem(block);
        player.sendMessage(plugin.prefix() + "§aRecibiste un §6Bloque de Trims de Casco§a. Colócalo en el mundo y úsalo con una llave.");
    }

    /** Creates a crate key item */
    private ItemStack createCrateKey() {
        ItemStack key = new ItemStack(Material.TRIPWIRE_HOOK);
        org.bukkit.inventory.meta.ItemMeta meta = key.getItemMeta();
        meta.setDisplayName("§6§l✦ §eLlave de Crate §6§l✦");
        meta.setLore(List.of(
            "§7Usa esta llave para abrir crates físicas",
            "§7de trims en el mundo.",
            "",
            "§8▸ Click derecho en una crate"
        ));
        meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        key.setItemMeta(meta);
        return key;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) {
            return List.of("normal", "themed", "key", "helmetblock");
        }
        if (args.length == 2) {
            return List.of("helmet", "chestplate", "leggings", "boots");
        }
        if (args.length == 3) {
            return List.of("legendary");
        }
        return List.of();
    }

    private void sendHelp(Player p) {
        p.sendMessage("§5§m          §r §dCrates Físicas §5§m          ");
        p.sendMessage("§7/crate §f<tipo> <pieza> [legendary]");
        p.sendMessage("§7/crate §fkey §8- Da llave de crate (admin)");
        p.sendMessage("§7/crate §fhelmetblock §8- Da bloque de trims de casco (admin)");
        p.sendMessage("");
        p.sendMessage("§7Tipos:");
        p.sendMessage("§f• §7normal §8- Crate normal (cyan)");
        p.sendMessage("§f• §7themed §8- Crate temática (amarilla)");
        p.sendMessage("§f• §7helmetblock §8- Bloque especial de casco (oro)");
        p.sendMessage("");
        p.sendMessage("§7Piezas:");
        p.sendMessage("§f• §7helmet §8- Casco");
        p.sendMessage("§f• §7chestplate §8- Pechera");
        p.sendMessage("§f• §7leggings §8- Pantalones");
        p.sendMessage("§f• §7boots §8- Botas");
        p.sendMessage("");
        p.sendMessage("§7Opcional:");
        p.sendMessage("§f• §7legendary §8- Hace la crate legendaria (púrpura)");
        p.sendMessage("");
        p.sendMessage("§eEjemplos:");
        p.sendMessage("§7/crate normal helmet");
        p.sendMessage("§7/crate themed chestplate");
        p.sendMessage("§7/crate normal boots legendary");
        p.sendMessage("§7/crate key");
        p.sendMessage("§7/crate helmetblock");
    }
}

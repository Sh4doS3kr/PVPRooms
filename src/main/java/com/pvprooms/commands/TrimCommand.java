package com.pvprooms.commands;

import com.pvprooms.PvPRoomsPro;
import com.pvprooms.gui.TrimGUI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Handles the /trim command.
 * Requires pvprooms.trim permission (true by default).
 *
 * Subcommands:
 *   /trim <material> <pattern>  — Applies trim to armor item in hand
 *   /trim gui                    — Opens trim selection GUI
 *   /trim remove                  — Removes trim from armor item in hand
 *   /trim list                    — Lists available materials and patterns
 */
@SuppressWarnings({"deprecation", "removal"})
public class TrimCommand implements CommandExecutor, TabCompleter {

    private final PvPRoomsPro plugin;

    public TrimCommand(PvPRoomsPro plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("pvprooms.trim")) {
            player.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            sendUsage(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("gui")) {
            openTrimGUI(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("remove")) {
            removeTrim(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("list")) {
            listTrims(player);
            return true;
        }

        if (args.length >= 2) {
            applyTrim(player, args[0], args[1]);
            return true;
        }

        sendUsage(player);
        return true;
    }

    private void openTrimGUI(Player player) {
        new TrimGUI(plugin).open(player);
    }

    private void removeTrim(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        
        if (item == null || !isArmor(item.getType())) {
            player.sendMessage("§cYou must hold an armor piece to remove trim.");
            return;
        }

        if (!(item.getItemMeta() instanceof ArmorMeta)) {
            player.sendMessage("§cThis item cannot have trims.");
            return;
        }

        ArmorMeta meta = (ArmorMeta) item.getItemMeta();
        if (!meta.hasTrim()) {
            player.sendMessage("§cThis armor piece doesn't have a trim.");
            return;
        }

        meta.setTrim(null);
        item.setItemMeta(meta);
        player.sendMessage("§aTrim removed successfully!");
    }

    private void applyTrim(Player player, String materialName, String patternName) {
        ItemStack item = player.getInventory().getItemInMainHand();
        
        if (item == null || !isArmor(item.getType())) {
            player.sendMessage("§cYou must hold an armor piece to apply trim.");
            return;
        }

        if (!(item.getItemMeta() instanceof ArmorMeta)) {
            player.sendMessage("§cThis item cannot have trims.");
            return;
        }

        TrimMaterial material = getTrimMaterial(materialName);
        if (material == null) {
            player.sendMessage("§cInvalid trim material. Use /trim list to see available materials.");
            return;
        }

        TrimPattern pattern = getTrimPattern(patternName);
        if (pattern == null) {
            player.sendMessage("§cInvalid trim pattern. Use /trim list to see available patterns.");
            return;
        }

        ArmorMeta meta = (ArmorMeta) item.getItemMeta();
        ArmorTrim trim = new ArmorTrim(material, pattern);
        meta.setTrim(trim);
        item.setItemMeta(meta);

        player.sendMessage("§aTrim applied successfully!");
        player.sendMessage("§7Material: " + formatEnum(material.getKey().getKey()));
        player.sendMessage("§7Pattern: " + formatEnum(pattern.getKey().getKey()));
    }

    private void listTrims(Player player) {
        player.sendMessage("§6=== Available Trim Materials ===");
        // Get available trim materials from the registry
        org.bukkit.Registry<TrimMaterial> materialRegistry = Bukkit.getRegistry(TrimMaterial.class);
        for (TrimMaterial material : materialRegistry) {
            player.sendMessage("§e- " + formatEnum(material.getKey().getKey()));
        }

        player.sendMessage("§6=== Available Trim Patterns ===");
        // Get available trim patterns from the registry
        org.bukkit.Registry<TrimPattern> patternRegistry = Bukkit.getRegistry(TrimPattern.class);
        for (TrimPattern pattern : patternRegistry) {
            player.sendMessage("§e- " + formatEnum(pattern.getKey().getKey()));
        }
    }

    private void sendUsage(Player player) {
        player.sendMessage("§6=== Trim Command Usage ===");
        player.sendMessage("§e/trim <material> <pattern> §7- Apply trim to armor in hand");
        player.sendMessage("§e/trim gui §7- Open trim selection GUI");
        player.sendMessage("§e/trim remove §7- Remove trim from armor in hand");
        player.sendMessage("§e/trim list §7- List available materials and patterns");
    }

    private boolean isArmor(Material material) {
        return material.name().endsWith("_HELMET") ||
               material.name().endsWith("_CHESTPLATE") ||
               material.name().endsWith("_LEGGINGS") ||
               material.name().endsWith("_BOOTS");
    }

    private TrimMaterial getTrimMaterial(String name) {
        org.bukkit.Registry<TrimMaterial> registry = Bukkit.getRegistry(TrimMaterial.class);
        for (TrimMaterial material : registry) {
            if (material.getKey().getKey().equalsIgnoreCase(name)) {
                return material;
            }
        }
        return null;
    }

    private TrimPattern getTrimPattern(String name) {
        org.bukkit.Registry<TrimPattern> registry = Bukkit.getRegistry(TrimPattern.class);
        for (TrimPattern pattern : registry) {
            if (pattern.getKey().getKey().equalsIgnoreCase(name)) {
                return pattern;
            }
        }
        return null;
    }

    private String formatEnum(String enumName) {
        return enumName.toLowerCase().replace("_", " ");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(Arrays.asList("gui", "remove", "list"));
            // Add trim materials
            org.bukkit.Registry<TrimMaterial> materialRegistry = Bukkit.getRegistry(TrimMaterial.class);
            for (TrimMaterial material : materialRegistry) {
                completions.add(material.getKey().getKey());
            }
        } else if (args.length == 2) {
            // Add trim patterns
            org.bukkit.Registry<TrimPattern> patternRegistry = Bukkit.getRegistry(TrimPattern.class);
            for (TrimPattern pattern : patternRegistry) {
                completions.add(pattern.getKey().getKey());
            }
        }

        return completions.stream()
                .filter(completion -> completion.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                .collect(Collectors.toList());
    }
}

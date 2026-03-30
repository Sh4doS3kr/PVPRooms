package com.pvprooms.commands;

import com.pvprooms.PvPRoomsPro;
import com.pvprooms.model.ArmorPiece;
import com.pvprooms.model.TrimCrate;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * /trimadmin - Admin commands for trim management
 */
public class TrimAdminCommand implements CommandExecutor, TabCompleter {

    private final PvPRoomsPro plugin;

    public TrimAdminCommand(PvPRoomsPro plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("pvprooms.admin")) {
            sender.sendMessage(plugin.prefix() + "§cSin permiso.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "give" -> {
                if (args.length < 3) {
                    sender.sendMessage(plugin.prefix() + "§cUso: /trimadmin give <jugador> <crate|key|legendary>");
                    return true;
                }
                Player target = plugin.getServer().getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage(plugin.prefix() + "§cJugador no encontrado.");
                    return true;
                }
                String type = args[2].toLowerCase();
                ItemStack item = switch (type) {
                    case "legendary" -> TrimCrate.createLegendaryCrate();
                    case "key" -> TrimCrate.createKey();
                    default -> TrimCrate.createNormalCrate();
                };
                target.getInventory().addItem(item);
                sender.sendMessage(plugin.prefix() + "§aDado §f" + type + " §aa §f" + target.getName());
            }

            case "giveall" -> {
                if (args.length < 2) {
                    sender.sendMessage(plugin.prefix() + "§cUso: /trimadmin giveall <crate|key|legendary>");
                    return true;
                }
                String type = args[1].toLowerCase();
                ItemStack item = switch (type) {
                    case "legendary" -> TrimCrate.createLegendaryCrate();
                    case "key" -> TrimCrate.createKey();
                    default -> TrimCrate.createNormalCrate();
                };
                for (Player p : plugin.getServer().getOnlinePlayers()) {
                    p.getInventory().addItem(item.clone());
                }
                sender.sendMessage(plugin.prefix() + "§aDado §f" + type + " §aa todos los jugadores.");
            }

            case "clear" -> {
                if (args.length < 2) {
                    sender.sendMessage(plugin.prefix() + "§cUso: /trimadmin clear <jugador>");
                    return true;
                }
                Player target = plugin.getServer().getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage(plugin.prefix() + "§cJugador no encontrado.");
                    return true;
                }
                plugin.getTrimManager().clearAllTrims(target.getUniqueId());
                sender.sendMessage(plugin.prefix() + "§aTrims de §f" + target.getName() + " §aeliminados.");
            }

            default -> sendHelp(sender);
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§5§m          §r §dTrim Admin §5§m          ");
        sender.sendMessage("§7/trimadmin §fgive <jugador> <crate|key|legendary>");
        sender.sendMessage("§7/trimadmin §fgiveall <crate|key|legendary>");
        sender.sendMessage("§7/trimadmin §fclear <jugador>");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("pvprooms.admin")) return List.of();
        
        if (args.length == 1) {
            return List.of("give", "giveall", "clear");
        }
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("giveall")) {
                return List.of("crate", "key", "legendary");
            }
            return null; // Player names
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return List.of("crate", "key", "legendary");
        }
        return List.of();
    }
}

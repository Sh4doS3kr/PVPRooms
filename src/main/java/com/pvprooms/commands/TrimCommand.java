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
 * /trim [gui | give <crate|key|legendary> [player] | clear [piece]]
 */
public class TrimCommand implements CommandExecutor, TabCompleter {

    private final PvPRoomsPro plugin;

    public TrimCommand(PvPRoomsPro plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cSolo jugadores.");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("gui")) {
            plugin.getTrimGUI().openMain(player);
            return true;
        }

        switch (args[0].toLowerCase()) {

            case "clear" -> {
                if (args.length >= 2) {
                    ArmorPiece piece = ArmorPiece.fromName(args[1]);
                    if (piece == null) {
                        player.sendMessage(plugin.prefix() + "§cPieza inválida. Usa: helmet, chestplate, leggings, boots");
                        return true;
                    }
                    plugin.getTrimManager().clearPlayerTrim(player.getUniqueId(), piece);
                    player.sendMessage(plugin.prefix() + "§7Trim de §f" + piece.getDisplayName() + " §7eliminado.");
                } else {
                    plugin.getTrimManager().clearAllTrims(player.getUniqueId());
                    player.sendMessage(plugin.prefix() + "§7Todos tus trims han sido eliminados.");
                }
            }

            case "give" -> {
                if (!player.hasPermission("pvprooms.admin")) {
                    player.sendMessage(plugin.prefix() + "§cSin permiso.");
                    return true;
                }
                String type = args.length >= 2 ? args[1].toLowerCase() : "crate";
                Player target = args.length >= 3
                        ? plugin.getServer().getPlayerExact(args[2]) : player;
                if (target == null) {
                    player.sendMessage(plugin.prefix() + "§cJugador no encontrado.");
                    return true;
                }
                ItemStack item = switch (type) {
                    case "legendary" -> TrimCrate.createLegendaryCrate();
                    case "key"       -> TrimCrate.createKey();
                    default          -> TrimCrate.createNormalCrate();
                };
                target.getInventory().addItem(item);
                player.sendMessage(plugin.prefix() + "§aDado §f" + type + " §aa §f" + target.getName());
            }

            default -> sendHelp(player);
        }

        return true;
    }

    private void sendHelp(Player p) {
        p.sendMessage("§5§m          §r §dTrims §5§m          ");
        p.sendMessage("§7/trim §fgui §8— Abre tu gestor de trims");
        p.sendMessage("§7/trim §fclear [pieza] §8— Elimina trim(s)");
        p.sendMessage("§7/trim §fgive <crate|key|legendary> [player] §8— (Admin) Da item");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) return List.of("gui", "clear", "give");
        if (args.length == 2) {
            return switch (args[0].toLowerCase()) {
                case "clear" -> List.of("helmet", "chestplate", "leggings", "boots");
                case "give"  -> List.of("crate", "key", "legendary");
                default      -> List.of();
            };
        }
        return List.of();
    }
}

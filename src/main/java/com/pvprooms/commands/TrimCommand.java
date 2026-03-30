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
 * /trim [gui | give <crate|key|legendary|themed <piece>> [player] | clear [piece]]
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

            case "apply" -> {
                // Apply all current trims instantly to equipped armor
                plugin.getTrimManager().applyAllTrimsInstantly(player);
                player.sendMessage(plugin.prefix() + "§aTrims aplicados a tu armadura actual.");
            }

            case "clear" -> {
                if (args.length >= 2) {
                    ArmorPiece piece = ArmorPiece.fromName(args[1]);
                    if (piece == null) {
                        player.sendMessage(plugin.prefix() + "§cPieza inválida. Usa: helmet, chestplate, leggings, boots");
                        return true;
                    }
                    plugin.getTrimManager().clearPlayerTrim(player.getUniqueId(), piece);
                    plugin.getTrimManager().applyAllTrimsInstantly(player);
                    player.sendMessage(plugin.prefix() + "§7Trim de §f" + piece.getDisplayName() + " §7eliminado.");
                } else {
                    plugin.getTrimManager().clearAllTrims(player.getUniqueId());
                    plugin.getTrimManager().applyAllTrimsInstantly(player);
                    player.sendMessage(plugin.prefix() + "§7Todos tus trims han sido eliminados.");
                }
            }

            case "crate", "crates", "abrir" -> {
                // Check if player has a key
                int keySlot = findKeySlot(player);
                if (keySlot == -1) {
                    player.sendMessage(plugin.prefix() + "§cNo tienes llaves de crate. Consíguelas jugando duelos.");
                    return true;
                }
                // Open crate selection GUI
                plugin.getTrimGUI().openCrateSelection(player);
            }

            case "give" -> {
                if (!player.isOp()) {
                    player.sendMessage(plugin.prefix() + "§cSolo OPs pueden usar este comando.");
                    return true;
                }
                String type = args.length >= 2 ? args[1].toLowerCase() : "crate";
                Player target = args.length >= 3
                        ? plugin.getServer().getPlayerExact(args[2]) : player;
                if (target == null) {
                    player.sendMessage(plugin.prefix() + "§cJugador no encontrado.");
                    return true;
                }
                
                ItemStack item;
                if (type.equals("themed")) {
                    if (args.length < 4) {
                        player.sendMessage(plugin.prefix() + "§cUso: /trim give themed <pieza> [jugador]");
                        player.sendMessage(plugin.prefix() + "§7Piezas: helmet, chestplate, leggings, boots");
                        return true;
                    }
                    ArmorPiece piece = ArmorPiece.fromName(args[3]);
                    if (piece == null) {
                        player.sendMessage(plugin.prefix() + "§cPieza inválida. Usa: helmet, chestplate, leggings, boots");
                        return true;
                    }
                    item = TrimCrate.createThemedCrate(piece);
                } else {
                    item = switch (type) {
                        case "legendary" -> TrimCrate.createLegendaryCrate();
                        case "key"       -> TrimCrate.createKey();
                        default          -> TrimCrate.createNormalCrate();
                    };
                }
                
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
        p.sendMessage("§7/trim §fcrates §8— Abre una crate (si tienes llave)");
        p.sendMessage("§7/trim §fapply §8— Aplica trims a tu armadura actual");
        p.sendMessage("§7/trim §fclear [pieza] §8— Elimina trim(s)");
        p.sendMessage("§7/trim §fgive <crate|key|legendary|themed> [player] §8— (Admin) Da item");
        p.sendMessage("§7/trim §fgive themed <pieza> [jugador] §8— (Admin) Da caja temática");
    }

    private int findKeySlot(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (TrimCrate.isKey(contents[i])) return i;
        }
        return -1;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) return List.of("gui", "crates", "apply", "clear", "give");
        if (args.length == 2) {
            return switch (args[0].toLowerCase()) {
                case "clear" -> List.of("helmet", "chestplate", "leggings", "boots");
                case "give"  -> List.of("crate", "key", "legendary", "themed");
                default      -> List.of();
            };
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give") && args[1].equalsIgnoreCase("themed")) {
            return List.of("helmet", "chestplate", "leggings", "boots");
        }
        return List.of();
    }
}

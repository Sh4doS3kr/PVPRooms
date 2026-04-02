package com.pvprooms.commands;

import com.pvprooms.PvPRoomsPro;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.stream.Collectors;

public class GoldenHeadCommand implements CommandExecutor, TabCompleter {

    private final PvPRoomsPro plugin;

    public GoldenHeadCommand(PvPRoomsPro plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp() && !sender.hasPermission("pvprooms.admin")) {
            sender.sendMessage(plugin.prefix() + "§cNo tienes permiso para usar este comando.");
            return true;
        }

        Player target;
        int amount = 1;

        if (args.length == 0) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage(plugin.prefix() + "§cUso: /gheads <jugador> [cantidad]");
                return true;
            }
            target = p;
        } else {
            target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(plugin.prefix() + "§cJugador no encontrado: §e" + args[0]);
                return true;
            }
            if (args.length >= 2) {
                try {
                    amount = Math.max(1, Math.min(64, Integer.parseInt(args[1])));
                } catch (NumberFormatException e) {
                    sender.sendMessage(plugin.prefix() + "§cCantidad inválida: §e" + args[1]);
                    return true;
                }
            }
        }

        ItemStack head = plugin.getLobbyManager().createGoldenHeadItem(amount);
        target.getInventory().addItem(head);

        sender.sendMessage(plugin.prefix() + "§aDiste §6" + amount + "x Golden Head §aa §f" + target.getName());
        if (!(sender instanceof Player sp) || !sp.getUniqueId().equals(target.getUniqueId())) {
            target.sendMessage(plugin.prefix() + "§aRecibiste §6" + amount + "x §aGolden Head.");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.isOp() && !sender.hasPermission("pvprooms.admin")) return List.of();
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(partial))
                    .collect(Collectors.toList());
        }
        if (args.length == 2) return List.of("1", "4", "8", "16", "32", "64");
        return List.of();
    }
}

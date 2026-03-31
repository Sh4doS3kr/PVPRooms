package com.pvprooms.commands;

import com.pvprooms.PvPRoomsPro;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Command for verifying web account registration.
 * Usage: /verify <9-digit-code>
 */
public class VerifyCommand implements CommandExecutor {

    private final PvPRoomsPro plugin;

    public VerifyCommand(PvPRoomsPro plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cEste comando solo puede ser usado por jugadores.");
            return true;
        }

        if (args.length != 1) {
            player.sendMessage(plugin.prefix() + "§cUso: /verify <código>");
            player.sendMessage(plugin.prefix() + "§7Obtén tu código en la página web al registrarte.");
            return true;
        }

        String code = args[0];
        
        // Validate code format (9 digits)
        if (!code.matches("\\d{9}")) {
            player.sendMessage(plugin.prefix() + "§cCódigo inválido. Debe ser de 9 dígitos.");
            return true;
        }

        boolean success = plugin.getTicketManager().verifyCode(player, code);
        
        if (!success) {
            player.sendMessage(plugin.prefix() + "§cCódigo incorrecto o ya usado.");
            player.sendMessage(plugin.prefix() + "§7Asegúrate de usar tu nombre exacto al registrarte.");
        }
        // Success message is sent by the TicketManager

        return true;
    }
}

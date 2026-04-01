package com.pvprooms.commands;

import com.pvprooms.PvPRoomsPro;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Command to verify web account registration.
 * Usage: /verificar <9-digit-code>
 */
public class VerifyCommand implements CommandExecutor {

    private final PvPRoomsPro plugin;

    public VerifyCommand(PvPRoomsPro plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cEste comando solo puede ser usado por jugadores.");
            return true;
        }

        if (args.length != 1) {
            player.sendMessage(plugin.prefix() + "§cUso: /verificar <código>");
            player.sendMessage(plugin.prefix() + "§7El código de 9 dígitos lo obtienes en la web al registrarte.");
            return true;
        }

        String code = args[0].trim();
        
        // Validate code format (9 digits)
        if (!code.matches("\\d{9}")) {
            player.sendMessage(plugin.prefix() + "§cEl código debe ser de 9 dígitos numéricos.");
            return true;
        }

        var ticketManager = plugin.getTicketManager();
        if (ticketManager == null) {
            player.sendMessage(plugin.prefix() + "§cSistema no disponible.");
            return true;
        }

        boolean verified = ticketManager.verifyCode(player, code);
        
        if (!verified) {
            player.sendMessage(plugin.prefix() + "§cCódigo inválido o expirado.");
            player.sendMessage(plugin.prefix() + "§7Los códigos expiran después de 10 minutos.");
            player.sendMessage(plugin.prefix() + "§7Asegúrate de usar el mismo nombre que registraste en la web.");
        }
        // Success message is sent by TicketManager

        return true;
    }
}

package com.pvprooms.commands;

import com.pvprooms.PvPRoomsPro;
import com.pvprooms.api.TicketManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Command for web account verification.
 * Usage: /verificar <codigo>
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
            player.sendMessage(plugin.prefix() + "§7Obtén tu código de verificación en la página web.");
            return true;
        }

        String code = args[0];
        
        // Validate code format (9 digits)
        if (!code.matches("\\d{9}")) {
            player.sendMessage(plugin.prefix() + "§cCódigo inválido. Debe ser un código de 9 dígitos.");
            return true;
        }

        // Check if there's a pending verification for this player
        TicketManager.PendingVerification pending = plugin.getTicketManager().getPendingVerification(player.getName());
        if (pending == null) {
            player.sendMessage(plugin.prefix() + "§cNo tienes ninguna verificación pendiente.");
            player.sendMessage(plugin.prefix() + "§7Inicia el registro en la página web primero.");
            return true;
        }

        // Verify the code
        if (plugin.getTicketManager().verifyCode(player.getUniqueId(), player.getName(), code)) {
            player.sendMessage("");
            player.sendMessage(plugin.prefix() + "§a§l¡VERIFICACIÓN EXITOSA!");
            player.sendMessage(plugin.prefix() + "§7Tu cuenta web ha sido vinculada a §e" + player.getName());
            player.sendMessage(plugin.prefix() + "§7Ya puedes iniciar sesión en la página web.");
            player.sendMessage("");
        } else {
            player.sendMessage(plugin.prefix() + "§cCódigo incorrecto o expirado.");
            player.sendMessage(plugin.prefix() + "§7Los códigos expiran después de 10 minutos.");
        }

        return true;
    }
}

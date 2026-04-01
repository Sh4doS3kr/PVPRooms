package com.pvprooms.discord;

import com.pvprooms.PvPRoomsPro;
import com.pvprooms.model.Tier;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;

import java.awt.Color;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Discord bot for PvPRoomsPro that provides tier lookup via /tiers command.
 */
public class DiscordBot extends ListenerAdapter {

    private final PvPRoomsPro plugin;
    private JDA jda;

    public DiscordBot(PvPRoomsPro plugin) {
        this.plugin = plugin;
    }

    public void start(String token) {
        if (token == null || token.isEmpty()) {
            plugin.getLogger().warning("[Discord] No token configured. Bot disabled.");
            return;
        }

        try {
            jda = JDABuilder.createDefault(token)
                    .setActivity(Activity.playing("PvP Tiers"))
                    .enableIntents(GatewayIntent.GUILD_MESSAGES)
                    .addEventListeners(this)
                    .build();

            jda.awaitReady();

            // Register slash commands to specific guild (instant) instead of global (takes 1 hour)
            String guildId = plugin.getConfig().getString("discord.guild-id", "1474155518942646394");
            var guild = jda.getGuildById(guildId);
            
            if (guild != null) {
                guild.updateCommands().addCommands(
                        Commands.slash("tiers", "Ver el tier de un jugador")
                                .addOption(OptionType.STRING, "usuario", "Nombre del jugador", true)
                ).queue(
                    success -> plugin.getLogger().info("[Discord] Comando /tiers registrado en guild " + guild.getName()),
                    error -> plugin.getLogger().warning("[Discord] Error registrando comando: " + error.getMessage())
                );
            } else {
                // Fallback to global commands if guild not found
                plugin.getLogger().warning("[Discord] Guild " + guildId + " no encontrado. Registrando comandos globales (puede tardar 1h)...");
                jda.updateCommands().addCommands(
                        Commands.slash("tiers", "Ver el tier de un jugador")
                                .addOption(OptionType.STRING, "usuario", "Nombre del jugador", true)
                ).queue();
            }

            plugin.getLogger().info("[Discord] Bot conectado como " + jda.getSelfUser().getName());

        } catch (Exception e) {
            plugin.getLogger().severe("[Discord] Error al iniciar bot: " + e.getMessage());
        }
    }

    public void stop() {
        if (jda != null) {
            jda.shutdown();
            plugin.getLogger().info("[Discord] Bot desconectado.");
        }
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("tiers")) return;

        String username = event.getOption("usuario").getAsString();
        
        // Find player UUID by name
        UUID uuid = resolveUUID(username);
        if (uuid == null) {
            event.reply("❌ Jugador **" + username + "** no encontrado.").setEphemeral(true).queue();
            return;
        }

        // Get player data
        var tierManager  = plugin.getTierManager();
        var eloManager   = plugin.getEloManager();
        var statsManager = plugin.getStatsManager();

        Tier bestTier              = tierManager.getBestTier(uuid);
        int  elo                   = eloManager.getElo(uuid);
        int  rank                  = eloManager.getRank(uuid);
        int  totalScore            = tierManager.getTotalScore(uuid);
        Map<String, Integer> kitPoints = tierManager.getKitPoints(uuid);
        String region              = plugin.getServerRegion().toUpperCase();

        var stats = statsManager.getStats(uuid);
        int wins    = stats.wins();
        int losses  = stats.losses();
        int total   = wins + losses;
        double winRate = total == 0 ? 0.0 : (wins * 100.0 / total);

        // ── Build embed (HispanBot field style) ──────────────────────────────
        EmbedBuilder embed = new EmbedBuilder();
        embed.setColor(getTierColor(bestTier));
        embed.setTitle(username + " — ESTADÍSTICAS");
        embed.setThumbnail("https://mc-heads.net/body/" + uuid + "/100");

        StringBuilder desc = new StringBuilder();

        // Rank & ELO
        desc.append("**RANK**\n")
            .append(rank <= 0 ? "Sin clasificar" : "#" + rank)
            .append("\n\n");

        desc.append("**ELO**\n")
            .append(elo)
            .append("\n\n");

        // Win / Loss
        desc.append("**VICTORIAS / DERROTAS**\n")
            .append(wins).append(" / ").append(losses)
            .append(" *(").append(String.format("%.1f", winRate)).append("% win rate)*")
            .append("\n\n");

        // Streaks
        if (stats.bestStreak() > 0) {
            desc.append("**MEJOR RACHA**\n")
                .append(stats.bestStreak()).append(" victorias consecutivas")
                .append("\n\n");
        }
        if (stats.currentStreak() > 1) {
            desc.append("**RACHA ACTUAL**\n")
                .append("🔥 ").append(stats.currentStreak()).append(" seguidas")
                .append("\n\n");
        }

        // K/D (only when meaningful)
        if (stats.hasKDR()) {
            desc.append("**K/D**\n")
                .append(String.format("%.2f", stats.getKDR()))
                .append(" (").append(stats.kills()).append("K / ").append(stats.deaths()).append("D)")
                .append("\n\n");
        }

        // Accuracy (only when swings tracked)
        if (stats.swings() >= 20) {
            desc.append("**PRECISIÓN**\n")
                .append(String.format("%.1f", stats.getAccuracy())).append("%")
                .append(" (").append(stats.hits()).append("/").append(stats.swings()).append(" golpes)")
                .append("\n\n");
        }

        // Tier points & best tier
        desc.append("**MEJOR TIER**\n")
            .append(bestTier != null ? getTierEmoji(bestTier) + " " + bestTier.displayName : "Sin tier")
            .append("\n\n");

        desc.append("**REGIÓN**\n")
            .append(region)
            .append("\n\n");

        // Per-kit tiers
        if (!kitPoints.isEmpty()) {
            desc.append("**KITS**\n");
            for (var entry : kitPoints.entrySet()) {
                Tier kitTier = Tier.fromPoints(entry.getValue());
                desc.append(getTierEmoji(kitTier))
                    .append(" **").append(capitalize(entry.getKey())).append("**")
                    .append(" — ").append(kitTier.displayName)
                    .append("\n");
            }
        } else {
            desc.append("**KITS**\n*Sin datos de kits*\n");
        }

        embed.setDescription(desc.toString());
        embed.setFooter("Estadísticas | PvPTiers", null);
        embed.setTimestamp(Instant.now());

        event.replyEmbeds(embed.build()).queue();
    }

    private UUID resolveUUID(String name) {
        // Check online players first
        var online = plugin.getServer().getPlayer(name);
        if (online != null) return online.getUniqueId();

        // Check offline players
        for (var p : plugin.getServer().getOfflinePlayers()) {
            if (name.equalsIgnoreCase(p.getName())) {
                return p.getUniqueId();
            }
        }

        // Check EloManager name map
        for (var entry : plugin.getEloManager().getNameMap().entrySet()) {
            if (name.equalsIgnoreCase(entry.getValue())) {
                try {
                    return UUID.fromString(entry.getKey());
                } catch (Exception ignored) {}
            }
        }

        return null;
    }

    private Color getTierColor(Tier tier) {
        if (tier == null) return Color.GRAY;
        return switch (tier) {
            case HT1, HT2, HT3, HT4, HT5 -> new Color(255, 85, 85);   // Red for HT
            case LT1, LT2, LT3, LT4, LT5 -> new Color(85, 255, 85);   // Green for LT
            default -> Color.GRAY;
        };
    }

    private String getTierEmoji(Tier tier) {
        if (tier == null) return "⚪";
        return switch (tier) {
            case HT1, HT2, HT3, HT4, HT5 -> "🔴";
            case LT1, LT2, LT3, LT4, LT5 -> "🟢";
            default -> "⚪";
        };
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
    }
}

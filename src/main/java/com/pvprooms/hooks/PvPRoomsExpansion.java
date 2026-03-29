package com.pvprooms.hooks;

import com.pvprooms.PvPRoomsPro;
import com.pvprooms.model.Tier;
import com.pvprooms.model.TierTitle;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * PlaceholderAPI expansion for PvPRoomsPro.
 * 
 * Placeholders:
 * - %pvprooms_tier% - Best tier name (LT5, HT3, etc.)
 * - %pvprooms_tier_formatted% - Tier with color (§bLT5)
 * - %pvprooms_tier_color% - Tier color code (§b)
 * - %pvprooms_tier_bracket% - Tier in brackets §8[§bLT5§8]
 * - %pvprooms_tier_<kit>% - Tier for specific kit
 * - %pvprooms_elo% - Player ELO
 * - %pvprooms_points% - Total points
 * - %pvprooms_rank% - ELO ranking position
 * - %pvprooms_wins% - Total wins
 * - %pvprooms_losses% - Total losses
 * - %pvprooms_kdr% - Kill/Death ratio
 * - %pvprooms_winrate% - Win percentage
 * - %pvprooms_title% - Player title
 * - %pvprooms_title_formatted% - Title with color and symbol
 */
public class PvPRoomsExpansion extends PlaceholderExpansion {

    private final PvPRoomsPro plugin;

    public PvPRoomsExpansion(PvPRoomsPro plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "pvprooms";
    }

    @Override
    public @NotNull String getAuthor() {
        return "PvPRoomsPro";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer offlinePlayer, @NotNull String params) {
        if (offlinePlayer == null) return "";
        UUID uuid = offlinePlayer.getUniqueId();

        // Tier placeholders
        if (params.equalsIgnoreCase("tier")) {
            Tier tier = plugin.getTierManager().getBestTier(uuid);
            return tier.displayName;
        }
        if (params.equalsIgnoreCase("tier_formatted")) {
            Tier tier = plugin.getTierManager().getBestTier(uuid);
            return tier.colour + tier.displayName;
        }
        if (params.equalsIgnoreCase("tier_color")) {
            Tier tier = plugin.getTierManager().getBestTier(uuid);
            return tier.colour;
        }
        if (params.equalsIgnoreCase("tier_bracket")) {
            Tier tier = plugin.getTierManager().getBestTier(uuid);
            if (tier == Tier.UNRANKED) {
                return "§8[§7?§8]";
            }
            return "§8[" + tier.colour + tier.displayName + "§8]";
        }
        
        // Kit-specific tier: tier_<kit>
        if (params.startsWith("tier_") && !params.equals("tier_formatted") && 
            !params.equals("tier_color") && !params.equals("tier_bracket")) {
            String kit = params.substring(5);
            Tier tier = plugin.getTierManager().getTier(uuid, kit);
            return tier.displayName;
        }

        // ELO
        if (params.equalsIgnoreCase("elo")) {
            return String.valueOf(plugin.getEloManager().getElo(uuid));
        }

        // Points
        if (params.startsWith("points_")) {
            String kit = params.substring(7);
            return String.valueOf(plugin.getTierManager().getPoints(uuid, kit));
        }

        // Rank
        if (params.equalsIgnoreCase("rank")) {
            int rank = plugin.getEloManager().getRank(uuid);
            return rank > 0 ? "#" + rank : "—";
        }
        if (params.equalsIgnoreCase("rank_num")) {
            int rank = plugin.getEloManager().getRank(uuid);
            return rank > 0 ? String.valueOf(rank) : "0";
        }

        // Stats (using StatsManager.getStats())
        if (params.equalsIgnoreCase("wins")) {
            return String.valueOf(plugin.getStatsManager().getStats(uuid).wins());
        }
        if (params.equalsIgnoreCase("losses")) {
            return String.valueOf(plugin.getStatsManager().getStats(uuid).losses());
        }
        if (params.equalsIgnoreCase("kills")) {
            return String.valueOf(plugin.getStatsManager().getStats(uuid).kills());
        }
        if (params.equalsIgnoreCase("deaths")) {
            return String.valueOf(plugin.getStatsManager().getStats(uuid).deaths());
        }
        if (params.equalsIgnoreCase("kdr") || params.equalsIgnoreCase("kd")) {
            double kdr = plugin.getStatsManager().getStats(uuid).getKDR();
            return String.format("%.2f", kdr);
        }
        if (params.equalsIgnoreCase("winrate")) {
            var stats = plugin.getStatsManager().getStats(uuid);
            int total = stats.wins() + stats.losses();
            double wr = total > 0 ? (stats.wins() * 100.0 / total) : 0;
            return String.format("%.1f", wr);
        }
        if (params.equalsIgnoreCase("streak")) {
            return String.valueOf(plugin.getStatsManager().getStats(uuid).currentStreak());
        }
        if (params.equalsIgnoreCase("best_streak")) {
            return String.valueOf(plugin.getStatsManager().getStats(uuid).bestStreak());
        }

        // Title
        if (params.equalsIgnoreCase("title")) {
            TierTitle title = plugin.getTierManager().getTitle(uuid);
            return title.name;
        }
        if (params.equalsIgnoreCase("title_formatted")) {
            TierTitle title = plugin.getTierManager().getTitle(uuid);
            return title.formatted();
        }
        if (params.equalsIgnoreCase("title_color")) {
            TierTitle title = plugin.getTierManager().getTitle(uuid);
            return title.colour;
        }
        if (params.equalsIgnoreCase("title_symbol")) {
            TierTitle title = plugin.getTierManager().getTitle(uuid);
            return title.symbol;
        }

        // Server stats
        if (params.equalsIgnoreCase("online")) {
            return String.valueOf(plugin.getServer().getOnlinePlayers().size());
        }
        if (params.equalsIgnoreCase("active_duels")) {
            return String.valueOf(plugin.getDuelManager().getActiveDuelCount());
        }
        if (params.equalsIgnoreCase("queue_total")) {
            return String.valueOf(plugin.getQueueManager().getTotalQueued());
        }
        if (params.equalsIgnoreCase("region")) {
            return plugin.getServerRegion().toUpperCase();
        }

        // In-game status
        if (params.equalsIgnoreCase("in_queue")) {
            return String.valueOf(plugin.getQueueManager().isInQueue(uuid));
        }
        if (params.equalsIgnoreCase("in_duel")) {
            return String.valueOf(plugin.getDuelManager().isInDuel(uuid));
        }

        return null;
    }
}

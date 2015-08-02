package us.talabrek.ultimateskyblock.command.island;

import org.bukkit.entity.Player;
import us.talabrek.ultimateskyblock.Settings;
import us.talabrek.ultimateskyblock.api.IslandRank;
import us.talabrek.ultimateskyblock.api.event.uSkyBlockEvent;
import us.talabrek.ultimateskyblock.async.Callback;
import us.talabrek.ultimateskyblock.island.IslandInfo;
import us.talabrek.ultimateskyblock.island.IslandScore;
import us.talabrek.ultimateskyblock.player.PlayerInfo;
import us.talabrek.ultimateskyblock.uSkyBlock;

import java.util.Map;
import java.util.logging.Level;

import static us.talabrek.ultimateskyblock.util.I18nUtil.tr;

public class LevelCommand extends RequireIslandCommand {
    public LevelCommand(uSkyBlock plugin) {
        super(plugin, "level", "usb.island.level", "?island", tr("check your or anothers island level"));
    }

    @Override
    protected boolean doExecute(String alias, Player player, PlayerInfo pi, IslandInfo island, Map<String, Object> data, String... args) {
        if (!Settings.island_useIslandLevel) {
            player.sendMessage(tr("\u00a74Island level has been disabled, contact an administrator."));
            return true;
        }
        if (args.length == 0) {
            if (!plugin.playerIsOnIsland(player)) {
                player.sendMessage(tr("\u00a7eYou must be on your island to use this command."));
                return true;
            }
            if (!island.isParty() && !pi.getHasIsland()) {
                player.sendMessage(tr("\u00a74You do not have an island!"));
            } else {
                getIslandLevel(player, player.getName(), alias);
            }
            return true;
        } else if (args.length == 1) {
            if (player.hasPermission("usb.island.level.other")) {
                getIslandLevel(player, args[0], alias);
            } else {
                player.sendMessage(tr("\u00a74You do not have access to that command!"));
            }
            return true;
        }
        return false;
    }

    public boolean getIslandLevel(final Player player, final String islandPlayer, final String cmd) {
        final PlayerInfo info = plugin.getPlayerInfo(islandPlayer);
        if (info == null || !info.getHasIsland() && !plugin.getIslandInfo(info).isParty()) {
            player.sendMessage(tr("\u00a74That player is invalid or does not have an island!"));
            return false;
        }
        final PlayerInfo playerInfo = islandPlayer.equals(player.getName()) ? plugin.getPlayerInfo(player) : plugin.getPlayerInfo(islandPlayer);
        final boolean shouldRecalculate = player.getName().equals(playerInfo.getPlayerName()) || player.hasPermission("usb.admin.island");
        final Runnable showInfo = new Runnable() {
            @Override
            public void run() {
                if (player != null && player.isOnline() && playerInfo != null) {
                    player.sendMessage(tr("\u00a7eInformation about " + islandPlayer + "'s Island:"));
                    if (cmd.equalsIgnoreCase("level")) {
                        IslandRank rank = plugin.getIslandLogic().getRank(playerInfo.locationForParty());
                        player.sendMessage(new String[]{
                                tr("\u00a7aIsland level is {0,number,###.##}", rank.getScore()),
                                tr("\u00a79Rank is {0}", rank.getRank())
                        });
                    }
                }
            }
        };
        if (shouldRecalculate) {
            plugin.getServer().getScheduler().runTaskLater(plugin, new Runnable() {
                @Override
                public void run() {
                    plugin.calculateScoreAsync(player, playerInfo.locationForParty(), new Callback<IslandScore>() {
                        @Override
                        public void run() {
                            plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, showInfo, 10L);
                        }
                    });
                }
            }, 1L);
        } else {
            showInfo.run();
        }
        return true;
    }
}

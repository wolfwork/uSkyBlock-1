package us.talabrek.ultimateskyblock.command.island;

import org.bukkit.entity.Player;
import us.talabrek.ultimateskyblock.island.IslandInfo;
import us.talabrek.ultimateskyblock.player.PlayerInfo;
import us.talabrek.ultimateskyblock.uSkyBlock;

import java.util.Map;

import static us.talabrek.ultimateskyblock.util.I18nUtil.tr;

public class HomeCommand extends RequireIslandCommand {
    public HomeCommand(uSkyBlock plugin) {
        super(plugin, "home|h", "usb.island.sethome", tr("teleport to the island home"));
    }

    @Override
    protected boolean doExecute(String alias, Player player, PlayerInfo pi, IslandInfo island, Map<String, Object> data, String... args) {
        if (pi.isIslandGenerating()) {
            player.sendMessage(tr("\u00a7cYour island is in the process of generating, you cannot teleport home right now."));
            return true;
        }
        if (pi.isIslandRestarting()) {
            player.sendMessage(tr("\u00a7cYour island is in the process of restarting, you cannot teleport home right now."));
            return true;
        }
        if (pi.getHomeLocation() == null) {
            pi.setHomeLocation(pi.getIslandLocation());
        }
        plugin.homeTeleport(player, false);
        return true;
    }
}

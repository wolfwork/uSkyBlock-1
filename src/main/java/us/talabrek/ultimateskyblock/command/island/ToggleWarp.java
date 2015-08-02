package us.talabrek.ultimateskyblock.command.island;

import org.bukkit.entity.Player;
import us.talabrek.ultimateskyblock.island.IslandInfo;
import us.talabrek.ultimateskyblock.player.PlayerInfo;
import us.talabrek.ultimateskyblock.uSkyBlock;

import java.util.Map;

import static us.talabrek.ultimateskyblock.util.I18nUtil.tr;

public class ToggleWarp extends RequireIslandCommand {
    public ToggleWarp(uSkyBlock plugin) {
        super(plugin, "togglewarp|tw", "usb.extra.addwarp", tr("enable/disable warping to your island."));
    }

    @Override
    protected boolean doExecute(String alias, Player player, PlayerInfo pi, IslandInfo island, Map<String, Object> data, String... args) {
        if (island.hasPerm(player, "canToggleWarp")) {
            if (!island.hasWarp()) {
                if (island.isLocked()) {
                    player.sendMessage(tr("\u00a74Your island is locked. You must unlock it before enabling your warp."));
                    return true;
                }
                island.sendMessageToIslandGroup("\u00a7b" +player.getName() + "\u00a7d activated the island warp.");
                island.setWarpActive(true);
            } else {
                island.sendMessageToIslandGroup("\u00a7b" +player.getName() + "\u00a7d deactivated the island warp.");
                island.setWarpActive(false);
            }
        } else {
            player.sendMessage(tr("\u00a7cYou do not have permission to enable/disable your island's warp!"));
        }
        return true;
    }
}

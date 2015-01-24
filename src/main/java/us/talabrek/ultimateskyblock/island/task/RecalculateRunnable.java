package us.talabrek.ultimateskyblock.island.task;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import us.talabrek.ultimateskyblock.api.event.uSkyBlockEvent;
import us.talabrek.ultimateskyblock.island.IslandInfo;
import us.talabrek.ultimateskyblock.uSkyBlock;

import java.util.HashSet;
import java.util.Set;

public class RecalculateRunnable extends BukkitRunnable {
    private final uSkyBlock plugin;

    public RecalculateRunnable(uSkyBlock plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        Set<String> recalcIslands = new HashSet<>();
        for (Player player : plugin.getWorld().getPlayers()) {
            if (player.isOnline() && plugin.playerIsOnIsland(player)) {
                recalcIslands.add(plugin.getPlayerInfo(player).locationForParty());
            }
        }
        if (!recalcIslands.isEmpty()) {
            plugin.getExecutor().execute(plugin, new RecalculateTask(plugin, recalcIslands), new Runnable() {
                @Override
                public void run() {
                    plugin.fireChangeEvent(new uSkyBlockEvent(null, plugin, uSkyBlockEvent.Cause.RANK_UPDATED));
                }
            }, 0.5f, 1);
        }
    }
}

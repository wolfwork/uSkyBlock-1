package us.talabrek.ultimateskyblock.command;

import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import us.talabrek.ultimateskyblock.handler.WorldGuardHandler;
import us.talabrek.ultimateskyblock.island.IslandInfo;
import us.talabrek.ultimateskyblock.player.PlayerInfo;
import us.talabrek.ultimateskyblock.uSkyBlock;

import java.util.*;

/**
 * Responsible for holding out-standing invites, and carrying out a transfer of invitation.
 */
@SuppressWarnings("deprecation")
public class InviteHandler {
    private final Map<UUID, Invite> inviteMap = new HashMap<>();
    private final Map<String, Set<UUID>> waitingInvites = new HashMap<>();
    private final uSkyBlock plugin;

    public InviteHandler(uSkyBlock plugin) {
        this.plugin = plugin;
    }

    public synchronized boolean invite(Player player, IslandInfo island, Player otherPlayer) {
        PlayerInfo oPi = plugin.getPlayerInfo(otherPlayer);
        Set<UUID> invites = waitingInvites.get(island.getName());
        if (invites == null) {
            invites = new HashSet<>();
        }
        if (island.getPartySize() + invites.size() >= island.getMaxPartySize()) {
            player.sendMessage("\u00a74Your island is full, or you have too many pending invites. You can't invite anyone else.");
            return false;
        }
        if (oPi.getHasIsland()) {
            IslandInfo oIsland = plugin.getIslandInfo(oPi);
            if (oIsland.isParty() && oIsland.isLeader(otherPlayer)) {
                player.sendMessage("\u00a74That player is already leader on another island.");
                otherPlayer.sendMessage("\u00a7e" + player.getDisplayName() + "\u00a7e tried to invite you, but you are already in a party.");
                return false;
            }
        }
        invites.add(otherPlayer.getUniqueId());
        inviteMap.put(otherPlayer.getUniqueId(), new Invite(island.getName()));
        waitingInvites.put(island.getName(), invites);
        player.sendMessage("\u00a7aInvite sent to " + otherPlayer.getDisplayName());
        otherPlayer.sendMessage(new String[]{
                player.getDisplayName() + "\u00a7e has invited you to join their island!",
                "\u00a7f/island [accept/reject]\u00a7e to accept or reject the invite.",
                "\u00a74WARNING: You will lose your current island if you accept!"
        });
        return true;
    }

    public synchronized boolean reject(Player player) {
        Invite invite = inviteMap.remove(player.getUniqueId());
        if (invite != null) {
            IslandInfo island = plugin.getIslandInfo(invite.getIslandName());
            if (island != null) {
                island.sendMessageToIslandGroup(player.getDisplayName() + "\u00a7e has rejected the invitation.");
            }
            if (waitingInvites.containsKey(invite.getIslandName())) {
                waitingInvites.get(invite.getIslandName()).remove(player.getUniqueId());
            }
            return true;
        }
        return false;
    }

    public synchronized boolean accept(final Player player) {
        UUID uuid = player.getUniqueId();
        IslandInfo oldIsland = plugin.getIslandInfo(player);
        if (oldIsland != null && oldIsland.isParty()) {
            player.sendMessage("\u00a74You can't use that command right now. Leave your current party first.");
            return false;
        }
        Invite invite = inviteMap.remove(uuid);
        if (invite != null) {
            PlayerInfo pi = plugin.getPlayerInfo(player);
            final IslandInfo island = plugin.getIslandInfo(invite.getIslandName());
            boolean deleteOldIsland = false;
            if (pi.getHasIsland() && pi.getIslandLocation() != null) {
                String islandName = WorldGuardHandler.getIslandNameAt(pi.getIslandLocation());
                deleteOldIsland = !island.getName().equals(islandName);
            }
            Set<UUID> uuids = waitingInvites.get(invite.getIslandName());
            uuids.remove(uuid);
            Runnable joinIsland = new Runnable() {
                @Override
                public void run() {
                    player.sendMessage(ChatColor.GREEN + "You have joined an island! Use /island party to see the other members.");
                    // TODO: 29/12/2014 - R4zorax: Perhaps these steps should belong somewhere else?
                    addPlayerToParty(player, island);
                    plugin.setRestartCooldown(player);
                    plugin.homeTeleport(player);
                    plugin.clearPlayerInventory(player);
                    WorldGuardHandler.addPlayerToOldRegion(island.getName(), player.getName());
                }
            };
            if (deleteOldIsland) {
                plugin.deletePlayerIsland(player.getName(), joinIsland);
            } else {
                joinIsland.run();
            }
            return true;
        }
        return false;
    }

    public synchronized Set<UUID> getPendingInvites(IslandInfo island) {
        return waitingInvites.get(island.getName());
    }

    public boolean addPlayerToParty(final Player player, final IslandInfo island) {
        PlayerInfo playerInfo = plugin.getPlayerInfo(player);
        PlayerInfo leaderInfo = plugin.getPlayerInfo(island.getLeader());
        playerInfo.setJoinParty(leaderInfo.getIslandLocation());
        if (playerInfo != leaderInfo) { // Caching is done in sky, this should be safe...
            if (leaderInfo.getHomeLocation() != null) {
                playerInfo.setHomeLocation(leaderInfo.getHomeLocation());
            } else {
                playerInfo.setHomeLocation(leaderInfo.getIslandLocation());
            }
            island.setupPartyMember(player.getName());
        }
        playerInfo.save();
        island.sendMessageToIslandGroup(player.getDisplayName() + " has joined your island group.");
        return true;
    }

    public synchronized boolean uninvite(IslandInfo islandInfo, String playerName) {
        OfflinePlayer offlinePlayer = plugin.getServer().getOfflinePlayer(playerName);
        if (offlinePlayer != null) {
            UUID uuid = offlinePlayer.getUniqueId();
            Set<UUID> invites = waitingInvites.get(islandInfo.getName());
            if (invites != null && invites.contains(uuid)) {
                inviteMap.remove(uuid);
                invites.remove(uuid);
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("UnusedDeclaration")
    private static class Invite {
        private final long time;
        private final String islandName;

        public Invite(String islandName) {
            this.islandName = islandName;
            time = System.currentTimeMillis();
        }

        public long getTime() {
            return time;
        }

        public String getIslandName() {
            return islandName;
        }
    }
}

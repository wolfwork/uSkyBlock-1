package us.talabrek.ultimateskyblock.player;

import com.google.common.base.Preconditions;
import java.nio.file.Files;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.*;

import java.util.*;

import org.bukkit.configuration.file.*;
import us.talabrek.ultimateskyblock.challenge.ChallengeCompletion;
import us.talabrek.ultimateskyblock.island.IslandInfo;
import us.talabrek.ultimateskyblock.uSkyBlock;
import us.talabrek.ultimateskyblock.util.LocationUtil;
import us.talabrek.ultimateskyblock.util.UUIDUtil;

import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.*;
import java.io.*;

import static us.talabrek.ultimateskyblock.util.I18nUtil.tr;

public class PlayerInfo implements Serializable {
    private static final String CN = PlayerInfo.class.getName();
    private static final Logger log = Logger.getLogger(CN);
    private static final long serialVersionUID = 1L;
    private static final int YML_VERSION = 1;
    private String playerName;
    private String displayName;
    private UUID uuid;
    private boolean hasIsland;

    private boolean islandGenerating = false;
    private boolean islandRestarting = false;

    private Location islandLocation;
    private Location homeLocation;

    private final Map<String, ChallengeCompletion> challenges = new ConcurrentHashMap<>();
    private FileConfiguration playerData;
    private File playerConfigFile;

    public PlayerInfo(String currentPlayerName, UUID playerUUID) {
        this.playerName = currentPlayerName;
        this.playerConfigFile = new File(uSkyBlock.getInstance().directoryPlayers, this.playerName + ".yml");
        if (this.playerConfigFile.exists() || !uSkyBlock.getInstance().getPlayerNameChangeManager().hasNameChanged(playerUUID, currentPlayerName)) {
            loadPlayer(true);
        }
    }
    
    public void startNewIsland(final Location l) {
        this.hasIsland = true;
        this.setIslandLocation(l);
        this.homeLocation = null;
    }

    public void removeFromIsland() {
        if (this.uuid != null) {
            Player onlinePlayer = Bukkit.getPlayer(this.uuid);
            if (onlinePlayer != null && onlinePlayer.isOnline()) {
                this.hasIsland = false;
                this.setIslandLocation(null);
                this.homeLocation = null;
                uSkyBlock.getInstance().clearPlayerInventory(onlinePlayer);
                uSkyBlock.getInstance().spawnTeleport(onlinePlayer);
            } 
            // If the player is not currently online, DO NOT set hasIsland to false.
            // They will be picked up when they login with checkIfKickedFromIsland().
        } else {
            this.hasIsland = false;
            this.setIslandLocation(null);
            this.homeLocation = null;
        }
    }

    public boolean getHasIsland() {
        return this.hasIsland;
    }

    public String locationForParty() {
        return LocationUtil.getIslandName(this.islandLocation);
    }

    public Player getPlayer() {
        return Bukkit.getPlayer(this.playerName);
    }

    public String getPlayerName() {
        return this.playerName;
    }

    public void setHasIsland(final boolean b) {
        this.hasIsland = b;
    }

    public void setIslandLocation(final Location l) {
        this.islandLocation = l != null ? l.clone() : null;
    }

    public Location getIslandLocation() {
        return islandLocation != null && hasIsland && islandLocation.getBlockY() != 0 ? islandLocation.clone() : null;
    }

    public void setHomeLocation(final Location l) {
        this.homeLocation = l != null ? l.clone() : null;
    }

    public Location getHomeLocation() {
        return homeLocation != null ? homeLocation.clone() : null;
    }

    public String getDisplayName() {
        return displayName != null ? displayName : playerName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void setJoinParty(final Location l) {
        this.islandLocation = l != null ? l.clone() : null;
        this.hasIsland = true;
    }

    public void completeChallenge(final String challenge) {
        if (challenges.containsKey(challenge)) {
            if (!onChallengeCooldown(challenge)) {
                long now = System.currentTimeMillis();
                challenges.get(challenge).setFirstCompleted(now + uSkyBlock.getInstance().getChallengeLogic().getResetInMillis(challenge));

            }
            challenges.get(challenge).addTimesCompleted();
            save();
        }
    }

    public boolean onChallengeCooldown(final String challenge) {
        return getChallenge(challenge).isOnCooldown();
    }

    public void resetChallenge(final String challenge) {
        if (challenges.containsKey(challenge)) {
            challenges.get(challenge).setTimesCompleted(0);
            challenges.get(challenge).setFirstCompleted(0L);
        }
    }

    public int checkChallenge(final String challenge) {
        try {
            if (challenges.containsKey(challenge.toLowerCase())) {
                return challenges.get(challenge.toLowerCase()).getTimesCompleted();
            }
        } catch (ClassCastException ex) {
        }
        return 0;
    }

    public ChallengeCompletion getChallenge(final String challenge) {
        return challenges.get(challenge.toLowerCase());
    }

    public boolean challengeExists(final String challenge) {
        return challenges.containsKey(challenge.toLowerCase());
    }

    public void resetAllChallenges() {
        challenges.clear();
        buildChallengeList();
    }

    private void buildChallengeList() {
        uSkyBlock.getInstance().getChallengeLogic().populateChallenges(challenges);
    }

    private void setupPlayer() {
        uSkyBlock.log(Level.INFO, "Creating player config Paths!");
        FileConfiguration playerConfig = getPlayerConfig();
        ConfigurationSection pSection = playerConfig.createSection("player");
        pSection.set("hasIsland", false);
        pSection.set("islandX", 0);
        pSection.set("islandY", 0);
        pSection.set("islandZ", 0);
        pSection.set("homeX", 0);
        pSection.set("homeY", 0);
        pSection.set("homeZ", 0);
        pSection.set("homeYaw", 0);
        pSection.set("homePitch", 0);
        final Iterator<String> ent = challenges.keySet().iterator();
        while (ent.hasNext()) {
            String currentChallenge = ent.next();
            ConfigurationSection cSection = playerConfig.createSection("player.challenges." + currentChallenge);
            cSection.set("firstCompleted", challenges.get(currentChallenge).getFirstCompleted());
            cSection.set("timesCompleted", challenges.get(currentChallenge).getTimesCompleted());
            cSection.set("timesCompletedSinceTimer", challenges.get(currentChallenge).getTimesCompletedSinceTimer());
        }
    }
    
    private PlayerInfo loadPlayer(boolean handleIslandRemoval) {
        Player onlinePlayer = getPlayer();
        try {
            log.entering(CN, "loadPlayer:" + this.playerName);
            FileConfiguration playerConfig = getPlayerConfig();
            if (!playerConfig.contains("player.hasIsland")) {
                this.hasIsland = false;
                this.islandLocation = null;
                this.homeLocation = null;
                buildChallengeList();
                createPlayerConfig();
                return this;
            }
            try {
                this.displayName = playerConfig.getString("player.displayName", playerName);
                this.uuid = UUIDUtil.fromString(playerConfig.getString("player.uuid", null));
                this.hasIsland = playerConfig.getBoolean("player.hasIsland");
                this.islandLocation = new Location(uSkyBlock.getSkyBlockWorld(),
                        playerConfig.getInt("player.islandX"), playerConfig.getInt("player.islandY"), playerConfig.getInt("player.islandZ"));
                this.homeLocation = new Location(uSkyBlock.getSkyBlockWorld(),
                        playerConfig.getInt("player.homeX") + 0.5, playerConfig.getInt("player.homeY") + 0.2, playerConfig.getInt("player.homeZ") + 0.5,
                        (float) playerConfig.getDouble("player.homeYaw", 0.0),
                        (float) playerConfig.getDouble("player.homePitch", 0.0));

                if (handleIslandRemoval) {
                    checkIfKickedFromIsland();
                }
                
                buildChallengeList();
                for (String currentChallenge : challenges.keySet()) {
                    this.challenges.put(currentChallenge, new ChallengeCompletion(currentChallenge, playerConfig.getLong("player.challenges." + currentChallenge + ".firstCompleted"), playerConfig.getInt("player.challenges." + currentChallenge + ".timesCompleted"), playerConfig.getInt("player.challenges." + currentChallenge + ".timesCompletedSinceTimer")));
                }
                log.exiting(CN, "loadPlayer");
                return this;
            } catch (Exception e) {
                e.printStackTrace();
                uSkyBlock.log(Level.INFO, "Returning null while loading, not good!");
                return null;
            }
        } finally {
            if (onlinePlayer != null && onlinePlayer.isOnline()) {
                updatePlayerInfo(onlinePlayer);
            }
        }
    }

    private void checkIfKickedFromIsland() {
        Player onlinePlayer = Bukkit.getPlayer(this.playerName);

        IslandInfo loadedIslandInfo = uSkyBlock.getInstance().getIslandInfo(LocationUtil.getIslandName(this.islandLocation));
        if (this.hasIsland && loadedIslandInfo != null && !loadedIslandInfo.getMembers().contains(this.playerName)) {
            if (onlinePlayer != null && onlinePlayer.isOnline()) {
                onlinePlayer.sendMessage(tr("\u00a7cYou were removed from your island since the last time you played!"));
            }

            removeFromIsland();
        }
    }
    
    // TODO: 09/12/2014 - R4zorax: All this should be made UUID
    private void reloadPlayerConfig() {
        playerConfigFile = new File(uSkyBlock.getInstance().directoryPlayers, playerName + ".yml");
        playerData = YamlConfiguration.loadConfiguration(playerConfigFile);
    }

    private void createPlayerConfig() {
        uSkyBlock.log(Level.INFO, "Creating new player config!");
        setupPlayer();
    }

    private FileConfiguration getPlayerConfig() {
        if (playerData == null) {
            reloadPlayerConfig();
        }
        return playerData;
    }

    public void save() {
        // TODO: 11/05/2015 - R4zorax: Instead of saving directly, schedule it for later...
        log.entering(CN, "save", playerName);
        if (playerData == null) {
            uSkyBlock.log(Level.INFO, "Can't save player data!");
            return;
        }
        FileConfiguration playerConfig = playerData;
        playerConfig.set("player.hasIsland", getHasIsland());
        playerConfig.set("player.displayName", displayName);
        playerConfig.set("player.uuid", UUIDUtil.asString(uuid));
        Location location = this.getIslandLocation();
        if (location != null) {
            playerConfig.set("player.islandX", location.getBlockX());
            playerConfig.set("player.islandY", location.getBlockY());
            playerConfig.set("player.islandZ", location.getBlockZ());
        } else {
            playerConfig.set("player.islandX", 0);
            playerConfig.set("player.islandY", 0);
            playerConfig.set("player.islandZ", 0);
        }
        Location home = this.getHomeLocation();
        if (home != null) {
            playerConfig.set("player.homeX", home.getBlockX());
            playerConfig.set("player.homeY", home.getBlockY());
            playerConfig.set("player.homeZ", home.getBlockZ());
            playerConfig.set("player.homeYaw", home.getYaw());
            playerConfig.set("player.homePitch", home.getPitch());
        } else {
            playerConfig.set("player.homeX", 0);
            playerConfig.set("player.homeY", 0);
            playerConfig.set("player.homeZ", 0);
            playerConfig.set("player.homeYaw", 0);
            playerConfig.set("player.homePitch", 0);
        }
        final Iterator<String> ent = challenges.keySet().iterator();
        String currentChallenge = "";
        while (ent.hasNext()) {
            currentChallenge = ent.next();
            playerConfig.set("player.challenges." + currentChallenge + ".firstCompleted", challenges.get(currentChallenge).getFirstCompleted());
            playerConfig.set("player.challenges." + currentChallenge + ".timesCompleted", challenges.get(currentChallenge).getTimesCompleted());
            playerConfig.set("player.challenges." + currentChallenge + ".timesCompletedSinceTimer", challenges.get(currentChallenge).getTimesCompletedSinceTimer());
        }
        playerConfigFile = new File(uSkyBlock.getInstance().directoryPlayers, playerName + ".yml");
        try {
            playerConfig.save(playerConfigFile);
            uSkyBlock.log(Level.FINEST, "Player data saved!");
        } catch (IOException ex) {
            uSkyBlock.getInstance().getLogger().log(Level.SEVERE, "Could not save config to " + playerConfigFile, ex);
        }
        log.exiting(CN, "save");
    }

    public Collection<ChallengeCompletion> getChallenges() {
        return challenges.values();
    }

    @Override
    public String toString() {
        // TODO: 01/06/2015 - R4zorax: use i18n.tr
        String str = "\u00a7bPlayer Info:\n";
        str += ChatColor.GRAY + "  - name: " + ChatColor.DARK_AQUA + getPlayerName() + "\n";
        str += ChatColor.GRAY + "  - nick: " + ChatColor.DARK_AQUA + getDisplayName() + "\n";
        str += ChatColor.GRAY + "  - hasIsland: " + ChatColor.DARK_AQUA +  getHasIsland() + "\n";
        str += ChatColor.GRAY + "  - home: " + ChatColor.DARK_AQUA +  LocationUtil.asString(getHomeLocation()) + "\n";
        str += ChatColor.GRAY + "  - island: " + ChatColor.DARK_AQUA + LocationUtil.asString(getIslandLocation()) + "\n";
        str += ChatColor.GRAY + "  - banned from: " + ChatColor.DARK_AQUA + getBannedFrom() + "\n";
        // TODO: 28/12/2014 - R4zorax: Some info about challenges?
        return str;
    }

    public void updatePlayerInfo(Player player) {
        if (!player.getDisplayName().equals(displayName) || !player.getUniqueId().equals(uuid)) {
            setDisplayName(player.getDisplayName());
            uuid = player.getUniqueId();
            save();
        }
    }

    public UUID getUniqueId() {
        return uuid;
    }

    public synchronized void renameFrom(String oldName) {
        Preconditions.checkState(!Bukkit.isPrimaryThread(), "This method cannot run in the main server thread!");
        
        // Delete the new file if for some reason it already exists.
        if (this.playerConfigFile.exists()) {
            this.playerConfigFile.delete();
        }
        File oldPlayerFile = new File(this.playerConfigFile.getParent(), oldName + ".yml");
        if (oldPlayerFile.exists()) {
            // Copy the old file to the new file.
            try {
                Files.move(oldPlayerFile.toPath(), this.playerConfigFile.toPath());
            } catch (Exception exception) {
                exception.printStackTrace();
            }
        }
        // Delete the old player file if it still exists.
        if (oldPlayerFile.exists()) {
            oldPlayerFile.delete();
        }

        // Reload the config.
        reloadPlayerConfig();
        
        // Load the player without checking for island removal.
        loadPlayer(false);
    }
    
    public synchronized void postRename() {
        Preconditions.checkState(!Bukkit.isPrimaryThread(), "This method cannot run in the main server thread!");
        
        checkIfKickedFromIsland();
    }

    public void banFromIsland(String name) {
        List<String> bannedFrom = playerData.getStringList("bannedFrom");
        if (bannedFrom != null && !bannedFrom.contains(name)) {
            bannedFrom.add(name);
            playerData.set("bannedFrom", bannedFrom);
            save();
        }
    }

    public void unbanFromIsland(String name) {
        List<String> bannedFrom = playerData.getStringList("bannedFrom");
        if (bannedFrom != null && bannedFrom.contains(name)) {
            bannedFrom.remove(name);
            playerData.set("bannedFrom", bannedFrom);
            save();
        }
    }

    public List<String> getBannedFrom() {
        return playerData.getStringList("bannedFrom");
    }

    public long getLastSaved() {
        return playerConfigFile.lastModified();
    }

    public void addTrust(String name) {
        List<String> trustedOn = playerData.getStringList("trustedOn");
        if (!trustedOn.contains(name)) {
            trustedOn.add(name);
            playerData.set("trustedOn", trustedOn);
        }
        save();
    }

    public void removeTrust(String name) {
        List<String> trustedOn = playerData.getStringList("trustedOn");
        trustedOn.remove(name);
        playerData.set("trustedOn", trustedOn);
        save();
    }

    public boolean isIslandGenerating() {
        return this.islandGenerating;
    }

    public void setIslandGenerating(boolean value) {
        this.islandGenerating = value;
    }

    public boolean isIslandRestarting() {
        return this.islandRestarting;
    }

    public void setIslandRestarting(boolean value) {
        this.islandRestarting = value;
    }
}

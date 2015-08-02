package us.talabrek.ultimateskyblock;

import com.google.common.base.Preconditions;
import com.sk89q.worldedit.Vector;
import com.sk89q.worldedit.regions.CuboidRegion;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Stack;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.MaterialData;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.mcstats.Metrics;
import us.talabrek.ultimateskyblock.api.IslandLevel;
import us.talabrek.ultimateskyblock.api.IslandRank;
import us.talabrek.ultimateskyblock.api.event.uSkyBlockEvent;
import us.talabrek.ultimateskyblock.api.uSkyBlockAPI;
import us.talabrek.ultimateskyblock.async.AsyncBalancedExecutor;
import us.talabrek.ultimateskyblock.async.BalancedExecutor;
import us.talabrek.ultimateskyblock.async.Callback;
import us.talabrek.ultimateskyblock.async.SyncBalancedExecutor;
import us.talabrek.ultimateskyblock.challenge.ChallengeLogic;
import us.talabrek.ultimateskyblock.challenge.ChallengesCommand;
import us.talabrek.ultimateskyblock.command.AdminCommand;
import us.talabrek.ultimateskyblock.command.IslandCommand;
import us.talabrek.ultimateskyblock.command.admin.DebugCommand;
import us.talabrek.ultimateskyblock.event.ExploitEvents;
import us.talabrek.ultimateskyblock.event.GriefEvents;
import us.talabrek.ultimateskyblock.event.ItemDropEvents;
import us.talabrek.ultimateskyblock.event.MenuEvents;
import us.talabrek.ultimateskyblock.event.PlayerEvents;
import us.talabrek.ultimateskyblock.handler.ConfirmHandler;
import us.talabrek.ultimateskyblock.handler.CooldownHandler;
import us.talabrek.ultimateskyblock.handler.MultiverseCoreHandler;
import us.talabrek.ultimateskyblock.handler.VaultHandler;
import us.talabrek.ultimateskyblock.handler.WorldEditHandler;
import us.talabrek.ultimateskyblock.handler.WorldGuardHandler;
import us.talabrek.ultimateskyblock.imports.impl.USBImporterExecutor;
import us.talabrek.ultimateskyblock.island.IslandGenerator;
import us.talabrek.ultimateskyblock.island.IslandInfo;
import us.talabrek.ultimateskyblock.island.IslandLogic;
import us.talabrek.ultimateskyblock.island.IslandScore;
import us.talabrek.ultimateskyblock.island.LevelLogic;
import us.talabrek.ultimateskyblock.island.task.RecalculateRunnable;
import us.talabrek.ultimateskyblock.player.PlayerInfo;
import us.talabrek.ultimateskyblock.player.PlayerLogic;
import us.talabrek.ultimateskyblock.player.PlayerNotifier;
import static us.talabrek.ultimateskyblock.util.BlockUtil.isBreathable;
import us.talabrek.ultimateskyblock.util.FileUtil;
import static us.talabrek.ultimateskyblock.util.FileUtil.getFileConfiguration;
import static us.talabrek.ultimateskyblock.util.I18nUtil.tr;
import us.talabrek.ultimateskyblock.util.LocationUtil;
import us.talabrek.ultimateskyblock.util.PlayerUtil;
import us.talabrek.ultimateskyblock.util.TimeUtil;
import us.talabrek.ultimateskyblock.uuid.FilePlayerDB;
import us.talabrek.ultimateskyblock.uuid.PlayerDB;
import us.talabrek.ultimateskyblock.uuid.PlayerNameChangeListener;
import us.talabrek.ultimateskyblock.uuid.PlayerNameChangeManager;

public class uSkyBlock extends JavaPlugin implements uSkyBlockAPI {


    private static final String CN = uSkyBlock.class.getName();
    private static final String[][] depends = new String[][]{
            new String[]{"Vault", "1.4"},
            new String[]{"WorldEdit", "5.5"},
            new String[]{"WorldGuard", "5.9"},
    };
    private static String missingRequirements = null;
    private static final Random RND = new Random(System.currentTimeMillis());

    private SkyBlockMenu menu;
    private ChallengeLogic challengeLogic;
    private LevelLogic levelLogic;
    private IslandLogic islandLogic;
    public IslandGenerator islandGenerator;
    private PlayerNotifier notifier;
    private USBImporterExecutor importer;
    private BalancedExecutor executor;
    private BalancedExecutor asyncExecutor;

    private static String pName = "";
    private FileConfiguration lastIslandConfig;
    private FileConfiguration orphans;
    private File orphanFile;
    private File lastIslandConfigFile;
    public static volatile World skyBlockWorld;
    private static uSkyBlock instance;
    private Location lastIsland;
    private Stack<Location> orphaned;
    private Stack<Location> tempOrphaned;
    private Stack<Location> reverseOrphaned;
    public File directoryPlayers;
    public File directoryIslands;

    private volatile boolean purgeActive;
    private volatile boolean protectAllActive;

    private BukkitTask autoRecalculateTask;

    static {
        uSkyBlock.skyBlockWorld = null;
    }

    private PlayerDB playerDB;
    private ConfirmHandler confirmHandler;

    private CooldownHandler cooldownHandler;
    private PlayerLogic playerLogic;

    private PlayerNameChangeManager playerNameChangeManager;

    private Map<String, Biome> validBiomes = new HashMap<String, Biome>() {
        {
            put("ocean", Biome.OCEAN);
            put("jungle", Biome.JUNGLE);
            put("hell", Biome.HELL);
            put("sky", Biome.SKY);
            put("mushroom", Biome.MUSHROOM_ISLAND);
            put("swampland", Biome.SWAMPLAND);
            put("taiga", Biome.TAIGA);
            put("desert", Biome.DESERT);
            put("forest", Biome.FOREST);
            put("plains", Biome.PLAINS);
            put("extreme_hills", Biome.EXTREME_HILLS);
            put("flower_forest", Biome.FLOWER_FOREST);
        }
    };

    public uSkyBlock() {
        this.lastIslandConfig = null;
        this.orphans = null;
        this.orphanFile = null;
        this.lastIslandConfigFile = null;
        this.orphaned = new Stack<>();
        this.tempOrphaned = new Stack<>();
        this.reverseOrphaned = new Stack<>();
        this.purgeActive = false;
    }

    @Override
    public void onDisable() {
        HandlerList.unregisterAll(this);
        Bukkit.getScheduler().cancelTasks(this);
        try {
            if (this.lastIsland != null) {
                setLastIsland(this.lastIsland);
            }
            uSkyBlock.skyBlockWorld = null; // Force a reload on config.
        } catch (Exception e) {
            log(Level.INFO, tr("Something went wrong saving the island and/or party data!"), e);
        }
        for (Player player : getServer().getOnlinePlayers()) {
            unloadPlayerData(player);
        }
        this.playerNameChangeManager.shutdown();
        DebugCommand.disableLogging(null);
    }

    @Override
    public FileConfiguration getConfig() {
        return getFileConfiguration("config.yml");
    }

    @Override
    public void onEnable() {
        skyBlockWorld = null; // Force a re-import or what-ever...
        missingRequirements = null;
        instance = this;
        FileUtil.init(getDataFolder());
        executor = new SyncBalancedExecutor(Bukkit.getScheduler());
        asyncExecutor = new AsyncBalancedExecutor(Bukkit.getScheduler());
        uSkyBlock.pName = "[" + getDescription().getName() + "] ";
        reloadConfigs();

        getServer().getScheduler().runTaskLater(getInstance(), new Runnable() {
            @Override
            public void run() {
                if (Bukkit.getServer().getPluginManager().isPluginEnabled("Vault")) {
                    log(Level.INFO, "Using vault for permissions");
                    VaultHandler.setupPermissions();
                    try {
                        FileConfiguration config = getLastIslandConfig();
                        if (!config.contains("options.general.lastIslandX") && getConfig().contains("options.general.lastIslandX")) {
                            FileConfiguration.createPath(config.getConfigurationSection("options.general"), "lastIslandX");
                            FileConfiguration.createPath(config.getConfigurationSection("options.general"), "lastIslandZ");
                            config.set("options.general.lastIslandX", getConfig().getInt("options.general.lastIslandX"));
                            config.set("options.general.lastIslandZ", getConfig().getInt("options.general.lastIslandZ"));
                            saveLastIslandConfig();
                        }
                        setLastIsland(new Location(uSkyBlock.getSkyBlockWorld(), (double) config.getInt("options.general.lastIslandX"), (double) Settings.island_height, (double) config.getInt("options.general.lastIslandZ")));
                    } catch (Exception e) {
                        setLastIsland(new Location(uSkyBlock.getSkyBlockWorld(), (double) uSkyBlock.this.getConfig().getInt("options.general.lastIslandX"), (double) Settings.island_height, (double) uSkyBlock.this.getConfig().getInt("options.general.lastIslandZ")));
                    }
                    if (uSkyBlock.this.lastIsland == null) {
                        setLastIsland(new Location(uSkyBlock.getSkyBlockWorld(), 0.0, (double) Settings.island_height, 0.0));
                    }
                    setupOrphans();
                }
                WorldGuardHandler.setupGlobal(getSkyBlockWorld());
                getServer().getScheduler().runTaskLater(instance, new Runnable() {
                    @Override
                    public void run() {
                        for (Player player : getServer().getOnlinePlayers()) {
                            playerLogic.loadPlayerDataAsync(player);
                        }
                    }
                }, 50L);
            }
        }, 50L);
        try {
            Metrics metrics = new Metrics(this);
            metrics.start();
        } catch (Exception e) {
            log(Level.WARNING, "Failed to submit metrics data", e);
        }
        log(Level.INFO, getVersionInfo());
    }

    public synchronized boolean isRequirementsMet(CommandSender sender) {
        if (missingRequirements == null) {
            PluginManager pluginManager = getServer().getPluginManager();
            missingRequirements = "";
            for (String[] pluginReq : depends) {
                if (pluginManager.isPluginEnabled(pluginReq[0])) {
                    PluginDescriptionFile desc = pluginManager.getPlugin(pluginReq[0]).getDescription();
                    if (pluginReq[1].compareTo(desc.getVersion()) > 0) {
                        missingRequirements += "\u00a7buSkyBlock\u00a7e depends on \u00a79" + pluginReq[0] + "\u00a7e >= \u00a7av" + pluginReq[1] + "\u00a7e but only \u00a7cv" + desc.getVersion() + "\u00a7e was found!\n";
                    }
                } else {
                    missingRequirements += "\u00a7buSkyBlock\u00a7e depends on \u00a79" + pluginReq[0] + "\u00a7e >= \u00a7av" + pluginReq[1];
                }
            }
        }
        if (missingRequirements.isEmpty()) {
            return true;
        } else {
            sender.sendMessage(missingRequirements.split("\n"));
            return false;
        }
    }

    private void createFolders() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        this.directoryPlayers = new File(this.getDataFolder() + File.separator + "players");
        if (!this.directoryPlayers.exists()) {
            this.directoryPlayers.mkdirs();
        }
        directoryIslands = new File(this.getDataFolder() + File.separator + "islands");
        if (!directoryIslands.exists()) {
            directoryIslands.mkdirs();
        }
        IslandInfo.setDirectory(directoryIslands);
    }

    public static uSkyBlock getInstance() {
        return uSkyBlock.instance;
    }

    public void registerEvents(PlayerDB playerDB) {
        final PluginManager manager = this.getServer().getPluginManager();
        manager.registerEvents(new PlayerNameChangeListener(this), this);
        manager.registerEvents(this.playerNameChangeManager, this);
        manager.registerEvents(new PlayerEvents(this), this);
        manager.registerEvents(new MenuEvents(this), this);
        manager.registerEvents(new ExploitEvents(this), this);
        if (getConfig().getBoolean("options.protection.enabled", true)) {
            manager.registerEvents(new GriefEvents(this), this);
            if (getConfig().getBoolean("options.protection.item-drops", true)) {
                manager.registerEvents(new ItemDropEvents(this), this);
            }
        }
    }

    public World getWorld() {
        if (uSkyBlock.skyBlockWorld == null) {
            skyBlockWorld = Bukkit.getWorld(Settings.general_worldName);
            if (skyBlockWorld == null || skyBlockWorld.canGenerateStructures() || !(skyBlockWorld.getGenerator() instanceof SkyBlockChunkGenerator)) {
                uSkyBlock.skyBlockWorld = WorldCreator
                        .name(Settings.general_worldName)
                        .type(WorldType.NORMAL)
                        .generateStructures(false)
                        .environment(World.Environment.NORMAL)
                        .generator(new SkyBlockChunkGenerator())
                        .createWorld();
                uSkyBlock.skyBlockWorld.save();
            }
            MultiverseCoreHandler.importWorld(skyBlockWorld);
            setupWorld(skyBlockWorld);

        }
        return uSkyBlock.skyBlockWorld;
    }

    private void setupWorld(World skyWorld) {
        if (Settings.general_spawnSize > 0) {
            if (LocationUtil.isEmptyLocation(skyWorld.getSpawnLocation())) {
                skyWorld.setSpawnLocation(0, Settings.island_height, 0);
            }
            Location worldSpawn = skyWorld.getSpawnLocation();
            if (!isSafeLocation(worldSpawn)) {
                Block spawnBlock = skyWorld.getBlockAt(worldSpawn).getRelative(BlockFace.DOWN);
                spawnBlock.setType(Material.BEDROCK);
                Block air1 = spawnBlock.getRelative(BlockFace.UP);
                air1.setType(Material.AIR);
                air1.getRelative(BlockFace.UP).setType(Material.AIR);
            }
        }
    }

    public static World getSkyBlockWorld() {
        return getInstance().getWorld();
    }

    public void clearOrphanedIsland() {
        while (this.hasOrphanedIsland()) {
            orphaned.pop();
        }
    }

    public Location getSafeHomeLocation(final PlayerInfo p) {
        Location home = null;
        if (p.getHomeLocation() != null) {
            home = p.getHomeLocation();
        } else if (p.getIslandLocation() != null) {
            home = p.getIslandLocation();
        }
        if (this.isSafeLocation(home)) {
            return home;
        }
        if (home == null) {
            return null;
        }
        for (int y = home.getBlockY() + 25; y > 0; --y) {
            final Location n = new Location(home.getWorld(), (double) home.getBlockX(), (double) y, (double) home.getBlockZ());
            if (this.isSafeLocation(n)) {
                return n;
            }
        }
        for (int y = home.getBlockY(); y < 255; ++y) {
            final Location n = new Location(home.getWorld(), (double) home.getBlockX(), (double) y, (double) home.getBlockZ());
            if (this.isSafeLocation(n)) {
                return n;
            }
        }
        final Location island = p.getIslandLocation();
        if (this.isSafeLocation(island)) {
            return island;
        }
        if (island == null) {
            return null;
        }
        for (int y2 = island.getBlockY() + 25; y2 > 0; --y2) {
            final Location n2 = new Location(island.getWorld(), (double) island.getBlockX(), (double) y2, (double) island.getBlockZ());
            if (this.isSafeLocation(n2)) {
                return n2;
            }
        }
        for (int y2 = island.getBlockY(); y2 < 255; ++y2) {
            final Location n2 = new Location(island.getWorld(), (double) island.getBlockX(), (double) y2, (double) island.getBlockZ());
            if (this.isSafeLocation(n2)) {
                return n2;
            }
        }
        return p.getHomeLocation();
    }

    public Location getSafeWarpLocation(final PlayerInfo p) {
        Location warp = null;
        FileConfiguration island = getTempIslandConfig(p.locationForParty());
        if (island.getInt("general.warpLocationX") == 0) {
            if (p.getHomeLocation() == null) {
                if (p.getIslandLocation() != null) {
                    warp = p.getIslandLocation();
                }
            } else {
                warp = p.getHomeLocation();
            }
        } else {
            warp = new Location(uSkyBlock.skyBlockWorld, (double) island.getInt("general.warpLocationX"), (double) island.getInt("general.warpLocationY"), (double) island.getInt("general.warpLocationZ"));
        }
        if (warp == null) {
            System.out.print("Error warping player to " + p.getPlayerName() + "'s island.");
            return null;
        }
        if (this.isSafeLocation(warp)) {
            return warp;
        }
        for (int y = warp.getBlockY() + 25; y > 0; --y) {
            final Location n = new Location(warp.getWorld(), (double) warp.getBlockX(), (double) y, (double) warp.getBlockZ());
            if (this.isSafeLocation(n)) {
                return n;
            }
        }
        for (int y = warp.getBlockY(); y < 255; ++y) {
            final Location n = new Location(warp.getWorld(), (double) warp.getBlockX(), (double) y, (double) warp.getBlockZ());
            if (this.isSafeLocation(n)) {
                return n;
            }
        }
        return null;
    }

    public boolean isSafeLocation(final Location l) {
        if (l == null) {
            return false;
        }
        final Block ground = l.getBlock().getRelative(BlockFace.DOWN);
        final Block air1 = l.getBlock();
        final Block air2 = l.getBlock().getRelative(BlockFace.UP);
        return ground.getType().isSolid() && isBreathable(air1) && isBreathable(air2);
    }

    public void removeCreatures(final Location l) {
        if (!Settings.island_removeCreaturesByTeleport || l == null) {
            return;
        }
        final int px = l.getBlockX();
        final int py = l.getBlockY();
        final int pz = l.getBlockZ();
        for (int x = -1; x <= 1; ++x) {
            for (int z = -1; z <= 1; ++z) {
                final Chunk c = l.getWorld().getChunkAt(new Location(l.getWorld(), (double) (px + x * 16), (double) py, (double) (pz + z * 16)));
                Entity[] entities;
                for (int length = (entities = c.getEntities()).length, i = 0; i < length; ++i) {
                    final Entity e = entities[i];
                    if (e.getType() == EntityType.SPIDER || e.getType() == EntityType.CREEPER || e.getType() == EntityType.ENDERMAN || e.getType() == EntityType.SKELETON || e.getType() == EntityType.ZOMBIE) {
                        e.remove();
                    }
                }
            }
        }
    }

    private void postDelete(final PlayerInfo pi) {
        IslandInfo islandInfo = getIslandInfo(pi);
        if (islandInfo != null) {
            postDelete(islandInfo);
        }
        pi.removeFromIsland();
        pi.save();
        playerLogic.removeActivePlayer(pi);
    }

    private void postDelete(final IslandInfo islandInfo) {
        addOrphan(islandInfo.getIslandLocation());
        WorldGuardHandler.removeIslandRegion(islandInfo.getName());
        islandLogic.deleteIslandConfig(islandInfo.getName());
        saveOrphans();
    }

    public boolean deleteIsland(String islandName, final Runnable runner) {
        final IslandInfo islandInfo = getIslandInfo(islandName);
        if (islandInfo != null) {
            for (String member : new ArrayList<>(islandInfo.getMembers())) {
                islandInfo.removeMember(member);
            }
            islandLogic.clearIsland(islandInfo.getIslandLocation(), new Runnable() {
                @Override
                public void run() {
                    postDelete(islandInfo);
                    if (runner != null) {
                        runner.run();
                    }
                }
            });
            return true;
        } else {
            return false;
        }
    }

    public boolean deleteEmptyIsland(String islandName, final Runnable runner) {
        final IslandInfo islandInfo = getIslandInfo(islandName);
        if (islandInfo != null && islandInfo.getMembers().isEmpty()) {
            islandLogic.clearIsland(islandInfo.getIslandLocation(), new Runnable() {
                @Override
                public void run() {
                    postDelete(islandInfo);
                    if (runner != null) {
                        runner.run();
                    }
                }
            });
            return true;
        } else {
            return false;
        }
    }

    public void deletePlayerIsland(final String player, final Runnable runner) {
        PlayerInfo pi = playerLogic.getPlayerInfo(player);
        
        if (pi == null) {
            if (Bukkit.isPrimaryThread()) {
                return;
            }
            pi = playerLogic.loadPlayerData(Bukkit.getOfflinePlayer(player).getUniqueId(), player);
        }
        final PlayerInfo finalPI = pi;
        islandLogic.clearIsland(pi.getIslandLocation(), new Runnable() {
            @Override
            public void run() {
                postDelete(finalPI);
                if (runner != null) runner.run();
            }
        });
    }

    private void postRestart(final Player player, final IslandGenerator.PlayerIslandCreationData playerIslandCreationData, final Location next) {
        getLogger().log(Level.FINE, "executing postRestart for " + playerIslandCreationData.getPlayerInfo().getPlayerName() + " on " + next);
        islandGenerator.createIsland(this, playerIslandCreationData, next);
        if (!changePlayerBiome(playerIslandCreationData, "OCEAN")) throw new UnsupportedOperationException();
        WorldEditHandler.unloadRegion(next);
        next.setY((double) Settings.island_height);
        setNewPlayerIsland(playerIslandCreationData.getPlayerInfo(), next);
        if (player != null && player.isOnline()) {
            getCooldownHandler().resetCooldown(player, "restart", Settings.general_cooldownRestart);
            PlayerInfo playerInfo = getPlayerInfo(player);
            if (playerInfo != null) {
                playerInfo.setIslandRestarting(false);
            }
            getServer().getScheduler().runTaskLater(uSkyBlock.getInstance(), new Runnable() {
                @Override
                public void run() {
                    getLogger().log(Level.FINE, "porting player back to the island");
                    homeTeleport(player, true);
                    WorldEditHandler.loadRegion(next);
                }
            }, getConfig().getInt("options.restart.teleportDelay", 20));
        }
    }

    public boolean restartPlayerIsland(final Player player, final Location next) {
        if (next.getBlockX() == 0 && next.getBlockZ() == 0) {
            return false;
        }
        PlayerInfo playerInfo = getPlayerInfo(player);
        if (playerInfo != null) {
            playerInfo.setIslandRestarting(true);
        }

        // Clear first, since the player could log out and we NEED to make sure their inventory gets cleared.
        clearPlayerInventory(player);
        clearEntitiesNearPlayer(player);

        final IslandGenerator.PlayerIslandCreationData playerIslandCreationData = this.islandGenerator.preCreateData(player, getPlayerInfo(player));
        this.islandLogic.clearIsland(next, new Runnable() {
            @Override
            public void run() {
                postRestart(player, playerIslandCreationData, next);
            }
        });
        return true;
    }

    public void clearPlayerInventory(Player player) {
        getLogger().entering(CN, "clearPlayerInventory", player);
        if (getConfig().getBoolean("options.restart.clearInventory", true)) {
            player.getInventory().clear();
        }
        if (getConfig().getBoolean("options.restart.clearArmor", true)) {
            ItemStack[] armor = player.getEquipment().getArmorContents();
            player.getEquipment().setArmorContents(new ItemStack[armor.length]);
        }
        if (getConfig().getBoolean("options.restart.clearEnderChest", true)) {
            player.getEnderChest().clear();
        }

        getLogger().exiting(CN, "clearPlayerInventory");
    }

    private void clearEntitiesNearPlayer(Player player) {
        getLogger().entering(CN, "clearEntitiesNearPlayer", player);
        for (final Entity entity : player.getNearbyEntities((double) (Settings.island_radius), 255.0, (double) (Settings.island_radius))) {
            if (!validEntity(entity)) {
                entity.remove();
            }
        }
        getLogger().exiting(CN, "clearEntitiesNearPlayer");
    }

    private boolean validEntity(Entity entity) {
        return (entity instanceof Player) ||
                (entity.getFallDistance() == 0 && !(entity instanceof Monster));
    }

    public Location findBedrockLocation(final Location l) {
        final int px = l.getBlockX();
        final int py = l.getBlockY();
        final int pz = l.getBlockZ();
        World world = l.getWorld();
        for (int x = -10; x <= 10; ++x) {
            for (int y = -30; y <= 30; ++y) {
                for (int z = -10; z <= 10; ++z) {
                    final Block b = world.getBlockAt(px + x, py + y, pz + z);
                    if (b.getType() == Material.BEDROCK) {
                        return new Location(world, px + x, py + y, pz + z);
                    }
                }
            }
        }
        return null;
    }
    
    public synchronized boolean devSetPlayerIsland(final Player sender, final Location l, final String player) {
        Preconditions.checkState(!Bukkit.isPrimaryThread(), "This method cannot run in the main thread!");
        
        PlayerInfo pi = playerLogic.getPlayerInfo(player);
        if (pi == null) {
            pi = this.playerLogic.loadPlayerData(Bukkit.getOfflinePlayer(player).getUniqueId(), player);
        }
        final PlayerInfo finalPI = pi;
        
        final Location newLoc = findBedrockLocation(l);
        boolean deleteOldIsland = false;
        if (pi.getHasIsland()) {
            Location oldLoc = pi.getIslandLocation();
            if (newLoc != null && oldLoc != null
                    && !(newLoc.getBlockX() == oldLoc.getBlockX() && newLoc.getBlockZ() == oldLoc.getBlockZ())) {
                deleteOldIsland = true;
            }
        }
        if (newLoc != null) {
            if (newLoc.equals(pi.getIslandLocation())) {
                sender.sendMessage(tr("\u00a74Player is already assigned to this island!"));
                deleteOldIsland = false;
            }
            Runnable resetIsland = new Runnable() {
                @Override
                public void run() {
                    finalPI.setHomeLocation(null);
                    finalPI.setHasIsland(true);
                    finalPI.setIslandLocation(newLoc);
                    finalPI.setHomeLocation(getSafeHomeLocation(finalPI));
                    IslandInfo island = islandLogic.createIsland(finalPI.locationForParty(), player);
                    WorldGuardHandler.updateRegion(sender, island);
                    finalPI.save();
                }
            };
            if (deleteOldIsland) {
                deletePlayerIsland(pi.getPlayerName(), resetIsland);
            } else {
                resetIsland.run();
            }
            return true;
        }
        return false;
    }

    public int orphanCount() {
        return orphaned.size();
    }

    public Location getLastIsland() {
        if (lastIsland != null && isSkyWorld(lastIsland.getWorld())) {
            return lastIsland;
        }
        this.setLastIsland(new Location(getSkyBlockWorld(), 0.0, (double) Settings.island_height, 0.0));
        return new Location(getSkyBlockWorld(), 0.0, (double) Settings.island_height, 0.0);
    }

    public void setLastIsland(final Location island) {
        this.getLastIslandConfig().set("options.general.lastIslandX", island.getBlockX());
        this.getLastIslandConfig().set("options.general.lastIslandZ", island.getBlockZ());
        this.saveLastIslandConfig();
        this.lastIsland = island;
    }

    public boolean hasOrphanedIsland() {
        return !orphaned.empty();
    }

    public Location checkOrphan() {
        return orphaned.peek();
    }

    public Location getOrphanedIsland() {
        if (this.hasOrphanedIsland()) {
            return orphaned.pop();
        }
        return null;
    }

    public void addOrphan(final Location island) {
        if (!orphaned.contains(island)) {
            orphaned.push(island);
        }
    }

    public void removeNextOrphan() {
        orphaned.pop();
    }

    public void saveOrphans() {
        String fullOrphan = "";
        this.tempOrphaned = (Stack<Location>) orphaned.clone();
        while (!this.tempOrphaned.isEmpty()) {
            reverseOrphaned.push(this.tempOrphaned.pop());
        }
        while (!reverseOrphaned.isEmpty()) {
            final Location tempLoc = reverseOrphaned.pop();
            if (tempLoc != null) {
                fullOrphan += tempLoc.getBlockX() + "," + tempLoc.getBlockZ() + ";";
            }
        }
        this.getOrphans().set("orphans.list", fullOrphan);
        this.saveOrphansFile();
    }

    public void setupOrphans() {
        if (this.getOrphans().contains("orphans.list")) {
            final String fullOrphan = this.getOrphans().getString("orphans.list");
            if (!fullOrphan.isEmpty()) {
                final String[] orphanArray = fullOrphan.split(";");
                orphaned = new Stack<>();
                for (int i = 0; i < orphanArray.length; ++i) {
                    final String[] orphanXY = orphanArray[i].split(",");
                    final Location tempLoc = new Location(
                            getSkyBlockWorld(), Integer.parseInt(orphanXY[0], 10),
                            Settings.island_height,
                            Integer.parseInt(orphanXY[1], 10));
                    orphaned.push(tempLoc);
                }
            }
        }
    }

    public boolean homeTeleport(final Player player, boolean force) {
        getLogger().entering(CN, "homeTeleport", player);
        try {
            Location homeSweetHome = null;
            PlayerInfo playerInfo = playerLogic.getPlayerInfo(player);
            if (playerInfo != null) {
                homeSweetHome = getSafeHomeLocation(playerInfo);
            }
            if (homeSweetHome == null) {
                player.sendMessage(tr("\u00a74You are not part of an island. Returning you the spawn area!"));
                spawnTeleport(player);
                return true;
            }
            removeCreatures(homeSweetHome);
            player.sendMessage(tr("\u00a7aTeleporting you to your island."));
            safeTeleport(player, homeSweetHome, force);
            return true;
        } finally {
            getLogger().exiting(CN, "homeTeleport");
        }
    }

    private void safeTeleport(final Player player, final Location homeSweetHome, boolean force) {
        int delay = getConfig().getInt("options.island.islandTeleportDelay", 5);
        if (player.hasPermission("usb.mod.bypassteleport") || (delay == 0) || force) {
            player.setVelocity(new org.bukkit.util.Vector());
            if (!homeSweetHome.getWorld().isChunkLoaded(homeSweetHome.getBlockX() >> 4, homeSweetHome.getBlockZ() >> 4)) {
                homeSweetHome.getWorld().loadChunk(homeSweetHome.getBlockX() >> 4, homeSweetHome.getBlockZ() >> 4);
            }
            player.teleport(homeSweetHome);
            player.setVelocity(new org.bukkit.util.Vector());
        } else {
            player.sendMessage(tr("\u00a7aYou will be teleported in {0} seconds.", delay));
            getServer().getScheduler().runTaskLater(getInstance(), new Runnable() {
                @Override
                public void run() {
                    player.setVelocity(new org.bukkit.util.Vector());
                    if (!homeSweetHome.getWorld().isChunkLoaded(homeSweetHome.getBlockX() >> 4, homeSweetHome.getBlockZ() >> 4)) {
                        homeSweetHome.getWorld().loadChunk(homeSweetHome.getBlockX() >> 4, homeSweetHome.getBlockZ() >> 4);
                    }
                    player.teleport(homeSweetHome);
                    player.setVelocity(new org.bukkit.util.Vector());
                }
            }, TimeUtil.secondsAsTicks(delay));
        }
    }

    public boolean warpTeleport(final Player player, final PlayerInfo pi, boolean force) {
        Location warpSweetWarp = null;
        if (pi == null) {
            player.sendMessage(tr("\u00a74That player does not exist!"));
            return true;
        }
        warpSweetWarp = getSafeWarpLocation(pi);
        if (warpSweetWarp == null) {
            player.sendMessage(tr("\u00a74Unable to warp you to that player's island!"));
            return true;
        }
        safeTeleport(player, warpSweetWarp, force);
        player.sendMessage(tr("\u00a7aTeleporting you to " + pi.getPlayerName() + "'s island."));
        return true;
    }

    public void spawnTeleport(final Player player) {
        spawnTeleport(player, false);
    }

    public void spawnTeleport(final Player player, boolean force) {
        getLogger().entering(CN, "spawnTeleport", new Object[]{player});

        int delay = getConfig().getInt("options.island.islandTeleportDelay", 5);
        if (player.hasPermission("usb.mod.bypassteleport") || (delay == 0) || force) {
            if (Settings.extras_sendToSpawn) {
                execCommand(player, "op:spawn");
            } else {
                if (!getWorld().isChunkLoaded(getWorld().getSpawnLocation().getBlockX() >> 4, getWorld().getSpawnLocation().getBlockZ() >> 4)) {
                    getWorld().loadChunk(getWorld().getSpawnLocation().getBlockX() >> 4, getWorld().getSpawnLocation().getBlockZ() >> 4);
                }
                player.teleport(getWorld().getSpawnLocation());
            }
        } else {
            player.sendMessage(tr("\u00a7aYou will be teleported in {0} seconds.", delay));
            getServer().getScheduler().runTaskLater(getInstance(), new Runnable() {
                @Override
                public void run() {
                    if (Settings.extras_sendToSpawn) {
                        execCommand(player, "op:spawn");
                    } else {
                        if (!getWorld().isChunkLoaded(getWorld().getSpawnLocation().getBlockX() >> 4, getWorld().getSpawnLocation().getBlockZ() >> 4)) {
                            getWorld().loadChunk(getWorld().getSpawnLocation().getBlockX() >> 4, getWorld().getSpawnLocation().getBlockZ() >> 4);
                        }
                        player.teleport(getWorld().getSpawnLocation());
                    }
                }
            }, TimeUtil.secondsAsTicks(delay));
        }
        getLogger().exiting(CN, "spawnTeleport");
    }

    public boolean homeSet(final Player player) {
        if (!player.getWorld().getName().equalsIgnoreCase(getSkyBlockWorld().getName())) {
            player.sendMessage(tr("\u00a74You must be closer to your island to set your skyblock home!"));
            return true;
        }
        if (this.playerIsOnIsland(player)) {
            PlayerInfo playerInfo = playerLogic.getPlayerInfo(player);
            if (playerInfo != null) {
                playerInfo.setHomeLocation(player.getLocation());
                playerInfo.save();
            }
            player.sendMessage(tr("\u00a7aYour skyblock home has been set to your current location."));
            return true;
        }
        player.sendMessage(tr("\u00a74You must be closer to your island to set your skyblock home!"));
        return true;
    }

    public boolean playerIsOnIsland(final Player player) {
        return locationIsOnIsland(player, player.getLocation()) || playerIsTrusted(player);
    }

    private boolean playerIsTrusted(Player player) {
        String islandName = WorldGuardHandler.getIslandNameAt(player.getLocation());
        if (islandName != null) {
            IslandInfo islandInfo = islandLogic.getIslandInfo(islandName);
            if (islandInfo != null && islandInfo.getTrustees().contains(player.getName())) {
                return true;
            }
        }
        return false;
    }

    public boolean locationIsOnIsland(final Player player, final Location loc) {
        if (!isSkyWorld(player.getWorld())) {
            return false;
        }
        PlayerInfo playerInfo = playerLogic.getPlayerInfo(player);
        if (playerInfo != null) {
            Location p = playerInfo.getIslandLocation();
            if (p == null) {
                return false;
            }
            int r = Settings.island_radius;
            CuboidRegion region = new CuboidRegion(
                    new Vector(p.getBlockX() - r, 0, p.getBlockZ() - r),
                    new Vector(p.getBlockX() + r, 255, p.getBlockZ() + r)
            );
            return region.contains(new Vector(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
        }
        return false;
    }

    public boolean hasIsland(final Player player) {
        PlayerInfo playerInfo = getPlayerInfo(player);
        return playerInfo != null && playerInfo.getHasIsland();
    }

    public boolean islandAtLocation(final Location loc) {
        return ((WorldGuardHandler.getIntersectingRegions(loc).size() > 0)
                || (findBedrockLocation(loc) != null)
                || islandLogic.hasIsland(loc)
        );

    }

    public boolean islandInSpawn(final Location loc) {
        if (loc == null) {
            return true;
        }
        return WorldGuardHandler.isIslandIntersectingSpawn(loc);
    }

    public ChunkGenerator getDefaultWorldGenerator(final String worldName, final String id) {
        return new SkyBlockChunkGenerator();
    }

    public boolean isPurgeActive() {
        return this.purgeActive;
    }

    public void activatePurge() {
        this.purgeActive = true;
    }

    public void deactivatePurge() {
        this.purgeActive = false;
    }

    public PlayerInfo getPlayerInfo(Player player) {
        return playerLogic.getPlayerInfo(player);
    }

    public PlayerInfo getPlayerInfo(String playerName) {
        return playerLogic.getPlayerInfo(playerName);
    }

    public void unloadPlayerData(Player player) {
        if (hasIsland(player) && !hasOtherIslandMembersOnline(player)) {
            islandLogic.removeIslandFromMemory(getPlayerInfo(player).locationForParty());
        }
        playerLogic.removeActivePlayer(player);
        notifier.unloadPlayer(player);
    }

    public FileConfiguration getTempIslandConfig(final String location) {
        File islandFile = new File(this.directoryIslands, location + ".yml");
        return YamlConfiguration.loadConfiguration(islandFile);
    }

    public void reloadLastIslandConfig() {
        if (this.lastIslandConfigFile == null) {
            this.lastIslandConfigFile = new File(this.getDataFolder(), "lastIslandConfig.yml");
        }
        this.lastIslandConfig = YamlConfiguration.loadConfiguration(this.lastIslandConfigFile);
        final InputStream defConfigStream = this.getResource("lastIslandConfig.yml");
        if (defConfigStream != null) {
            final YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(defConfigStream);
            this.lastIslandConfig.setDefaults(defConfig);
        }
    }

    public FileConfiguration getLastIslandConfig() {
        if (this.lastIslandConfig == null) {
            this.reloadLastIslandConfig();
        }
        return this.lastIslandConfig;
    }

    public void saveLastIslandConfig() {
        if (this.lastIslandConfig == null || this.lastIslandConfigFile == null) {
            return;
        }
        try {
            this.getLastIslandConfig().save(this.lastIslandConfigFile);
        } catch (IOException ex) {
            this.getLogger().log(Level.SEVERE, "Could not save config to " + this.lastIslandConfigFile, ex);
        }
    }

    public void reloadOrphans() {
        if (this.orphanFile == null) {
            this.orphanFile = new File(this.getDataFolder(), "orphans.yml");
        }
        this.orphans = YamlConfiguration.loadConfiguration(this.orphanFile);
        final InputStream defConfigStream = this.getResource("orphans.yml");
        if (defConfigStream != null) {
            final YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(defConfigStream);
            this.orphans.setDefaults(defConfig);
        }
    }

    public FileConfiguration getOrphans() {
        if (this.orphans == null) {
            this.reloadOrphans();
        }
        return this.orphans;
    }

    public void saveOrphansFile() {
        if (this.orphans == null || this.orphanFile == null) {
            return;
        }
        try {
            this.getOrphans().save(this.orphanFile);
        } catch (IOException ex) {
            this.getLogger().log(Level.SEVERE, "Could not save config to " + this.orphanFile, ex);
        }
    }

    public boolean setBiome(final Location loc, final String bName) {
        Biome biome = getBiome(bName);
        if (biome == null) return false;
        setBiome(loc, biome);
        return true;
    }

    public Biome getBiome(String bName) {
        if (bName == null) return null;
        return this.validBiomes.get(bName.toLowerCase());
    }

    private void setBiome(Location loc, Biome biome) {
        int r = Settings.island_radius;
        final int px = loc.getBlockX();
        final int pz = loc.getBlockZ();
        for (int x = px - r; x <= px + r; x++) {
            for (int z = pz - r; z <= pz + r; z++) {
                // Set the biome in the world.
                skyBlockWorld.setBiome(x, z, biome);
                // Refresh the chunks so players can see it without relogging!
                skyBlockWorld.refreshChunk(x, z);
            }
        }
    }

    public boolean biomeExists(String biomeName) {
        if (biomeName == null) return false;
        return this.validBiomes.containsKey(biomeName.toLowerCase());
    }

    // This method does not check for permissions, since it is only used in the generator.
    public boolean changePlayerBiome(IslandGenerator.PlayerIslandCreationData playerIslandCreationData, String bName) {
        if (!biomeExists(bName)) throw new UnsupportedOperationException();

        PlayerInfo playerInfo = playerIslandCreationData.getPlayerInfo();
        IslandInfo islandInfo = islandLogic.getIslandInfo(playerInfo);

        if (!setBiome(playerInfo.getIslandLocation(), bName)) {
            return false;
        }
        islandInfo.setBiome(bName);
        return true;
    }

    public boolean changePlayerBiome(Player player, String bName) {
        if (!biomeExists(bName)) throw new UnsupportedOperationException();

        if (!VaultHandler.checkPerk(player.getName(), "usb.biome." + bName, skyBlockWorld)) return false;

        PlayerInfo playerInfo = getPlayerInfo(player);
        IslandInfo islandInfo = islandLogic.getIslandInfo(playerInfo);
        if (islandInfo.hasPerm(player.getName(), "canChangeBiome")) {
            if (!setBiome(playerInfo.getIslandLocation(), bName)) {
                return false;
            }
            islandInfo.setBiome(bName);
            return true;
        }
        return false;
    }

    public boolean createIsland(final Player player, final PlayerInfo pi) {
        getLogger().entering(CN, "createIsland", new Object[]{player, pi});
        try {
            if (isSkyWorld(player.getWorld())) {
                spawnTeleport(player, true);
            }
            PlayerInfo playerInfo = getPlayerInfo(player);
            if (playerInfo != null) {
                playerInfo.setIslandGenerating(true);
            }

            final Location last = getLastIsland();
            last.setY((double) Settings.island_height);
            try {
                Location next = getNextIslandLocation(last);
                islandGenerator.createIsland(this, islandGenerator.preCreateData(player, pi), next);
                setNewPlayerIsland(player, next);
                changePlayerBiome(player, "OCEAN");
                protectWithWorldGuard(player, player, pi);
                getCooldownHandler().resetCooldown(player, "restart", Settings.general_cooldownRestart);
                if (playerInfo != null) {
                    playerInfo.setIslandGenerating(false);
                }

                // Clear first, since the player could log out and we NEED to make sure their inventory gets cleared.
                clearPlayerInventory(player);
                clearEntitiesNearPlayer(player);

                getServer().getScheduler().runTaskLater(uSkyBlock.getInstance(), new Runnable() {
                    @Override
                    public void run() {
                        if (player != null && player.isOnline()) {
                            getLogger().log(Level.FINE, "porting player back to the island");
                            homeTeleport(player, true);
                        }
                    }
                }, getConfig().getInt("options.restart.teleportDelay", 20));
            } catch (Exception ex) {
                player.sendMessage(tr("Could not create your Island. Please contact a server moderator."));
                log(Level.SEVERE, "Error creating island", ex);
                return false;
            }
            log(Level.INFO, "Finished creating player island.");
            return true;
        } finally {
            getLogger().exiting(CN, "createIsland");
        }
    }

    private void protectWithWorldGuard(CommandSender sender, Player player, PlayerInfo pi) {
        if (!WorldGuardHandler.protectIsland(player, pi)) {
            sender.sendMessage(tr("Player doesn't have an island or it's already protected!"));
        }
    }

    private synchronized Location getNextIslandLocation(Location last) {
        // Cleanup orphans
        while (hasOrphanedIsland() && !isSkyWorld(checkOrphan().getWorld())) {
            removeNextOrphan();
        }
        while (hasOrphanedIsland()) {
            if (!islandAtLocation(checkOrphan())) {
                break;
            }
            removeNextOrphan();
        }
        // Use last island-location (if available)
        Location next = last;
        // Scan orphans for a valid "re-use spot"
        if (hasOrphanedIsland() && !islandAtLocation(checkOrphan())) {
            next = getOrphanedIsland();
            saveOrphans();
        }
        // Ensure the found location is valid (or find one that is).
        while (islandInSpawn(next) || islandAtLocation(next)) {
            next = nextIslandLocation(next);
        }
        setLastIsland(next);
        return next;
    }

    private Location nextIslandLocation(final Location lastIsland) {
        final int x = (int) lastIsland.getX();
        final int z = (int) lastIsland.getZ();
        if (x < z) {
            if (-1 * x < z) {
                lastIsland.setX(lastIsland.getX() + Settings.island_distance);
                return lastIsland;
            }
            lastIsland.setZ(lastIsland.getZ() + Settings.island_distance);
            return lastIsland;
        } else if (x > z) {
            if (-1 * x >= z) {
                lastIsland.setX(lastIsland.getX() - Settings.island_distance);
                return lastIsland;
            }
            lastIsland.setZ(lastIsland.getZ() - Settings.island_distance);
            return lastIsland;
        } else {
            if (x <= 0) {
                lastIsland.setZ(lastIsland.getZ() + Settings.island_distance);
                return lastIsland;
            }
            lastIsland.setZ(lastIsland.getZ() - Settings.island_distance);
            return lastIsland;
        }
    }

    /**
     * Finds the nearest block to loc that is a chest.
     *
     * @param loc The location to scan for a chest.
     * @return The location of the chest
     */
    public Location findChestLocation(final Location loc) {
        World world = loc.getWorld();
        int px = loc.getBlockX();
        int pz = loc.getBlockZ();
        int py = loc.getBlockY();
        for (int dy = 1; dy <= 30; dy++) {
            for (int dx = 1; dx <= 30; dx++) {
                for (int dz = 1; dz <= 30; dz++) {
                    // Scans from the center and out
                    int x = px + (dx % 2 == 0 ? dx / 2 : -dx / 2);
                    int z = pz + (dz % 2 == 0 ? dz / 2 : -dz / 2);
                    int y = py + (dy % 2 == 0 ? dy / 2 : -dy / 2);
                    if (world.getBlockAt(x, y, z).getType() == Material.CHEST) {
                        return new Location(world, x, y, z);
                    }
                }
            }
        }
        return loc;
    }

    private Location findNearestSpawnLocation(Location loc) {
        World world = loc.getWorld();
        int px = loc.getBlockX();
        int pz = loc.getBlockZ();
        int py = loc.getBlockY();
        Block chestBlock = world.getBlockAt(loc);
        if (chestBlock.getType() == Material.CHEST) {
            BlockFace primaryDirection = null;
            // Start by checking in front of the chest.
            MaterialData data = chestBlock.getState().getData();
            if (data instanceof org.bukkit.material.Chest) {
                primaryDirection = ((org.bukkit.material.Chest) data).getFacing();
            }
            if (primaryDirection == BlockFace.NORTH) {
                // Neg Z
                pz -= 1; // start one block in the north dir.
            } else if (primaryDirection == BlockFace.SOUTH) {
                // Pos Z
                pz += 1; // start one block in the south dir
            } else if (primaryDirection == BlockFace.WEST) {
                // Neg X
                px -= 1; // start one block in the west dir
            } else if (primaryDirection == BlockFace.EAST) {
                // Pos X
                px += 1; // start one block in the east dir
            }
        }
        for (int dy = 1; dy <= 30; dy++) {
            for (int dx = 1; dx <= 30; dx++) {
                for (int dz = 1; dz <= 30; dz++) {
                    // Scans from the center and out
                    int x = px + (dx % 2 == 0 ? dx / 2 : -dx / 2);
                    int z = pz + (dz % 2 == 0 ? dz / 2 : -dz / 2);
                    int y = py + (dy % 2 == 0 ? dy / 2 : -dy / 2);
                    Location spawnLocation = new Location(world, x, y, z);
                    if (isSafeLocation(spawnLocation)) {
                        // look at the old location
                        Location d = loc.clone().subtract(spawnLocation);
                        spawnLocation.setDirection(d.toVector());
                        return spawnLocation;
                    }
                }
            }
        }
        return null;
    }

    public Location getChestSpawnLoc(final Location loc) {
        return findNearestSpawnLocation(findChestLocation(loc));
    }

    private void setNewPlayerIsland(final Player player, final Location loc) {
        setNewPlayerIsland(getPlayerInfo(player), loc);
    }

    private void setNewPlayerIsland(final PlayerInfo playerInfo, final Location loc) {
        playerInfo.startNewIsland(loc);
        
        Location chestSpawnLocation = getChestSpawnLoc(loc);
        if (chestSpawnLocation != null) {
            playerInfo.setHomeLocation(chestSpawnLocation.add(0.5, 0.1, 0.5));
        } else {
            log(Level.SEVERE, "Could not find a safe chest within 30 blocks of the island spawn. Bad schematic!");
        }
        IslandInfo info = islandLogic.createIsland(playerInfo.locationForParty(), playerInfo.getPlayerName());
        Player onlinePlayer = playerInfo.getPlayer();
        if (onlinePlayer != null && onlinePlayer.isOnline()) {
            info.handleMemberLoggedIn(onlinePlayer);
        }
        if (challengeLogic.isResetOnCreate()) {
            playerInfo.resetAllChallenges();
        }
        playerInfo.save();
    }

    public boolean hasOtherIslandMembersOnline(final Player player) {
        PlayerInfo playerInfo = getPlayerInfo(player);
        if (playerInfo == null) return false;
        IslandInfo islandInfo = islandLogic.getIslandInfo(getPlayerInfo(player));
        if (islandInfo == null) return false;
        for (final String member : islandInfo.getMembers()) {
            if (member.equalsIgnoreCase(player.getName())) {
                continue;
            }
            Player islandMember = Bukkit.getPlayer(member);
            if (islandMember != null && islandMember.isOnline()) {
                return true;
            }
        }
        return false;
    }

    public String getCurrentBiome(Player p) {
        return getIslandInfo(p).getBiome();
    }

    public IslandInfo getIslandInfo(Player player) {
        PlayerInfo playerInfo = getPlayerInfo(player);
        return islandLogic.getIslandInfo(playerInfo);
    }

    public boolean isPartyLeader(final Player player) {
        return getIslandInfo(player).getLeader().equalsIgnoreCase(player.getName());
    }

    public IslandInfo getIslandInfo(String location) {
        return islandLogic.getIslandInfo(location);
    }

    public IslandInfo getIslandInfo(PlayerInfo pi) {
        return islandLogic.getIslandInfo(pi);
    }

    public SkyBlockMenu getMenu() {
        return menu;
    }

    public static String stripFormatting(String format) {
        if (format == null || format.trim().isEmpty()) {
            return "";
        }
        return format.replaceAll("(\u00a7|&)[0-9a-fklmor]", "");
    }

    public static void log(Level level, String message) {
        log(level, message, null);
    }

    public static void log(Level level, String message, Throwable t) {
        getInstance().getLogger().log(level, message, t);
    }

    public ChallengeLogic getChallengeLogic() {
        return challengeLogic;
    }

    public LevelLogic getLevelLogic() {
        return levelLogic;
    }

    @Override
    public void reloadConfig() {
        reloadConfigs();
    }

    private void reloadConfigs() {
        createFolders();
        HandlerList.unregisterAll(this);
        VaultHandler.setupEconomy();
        if (Settings.loadPluginConfig(getConfig())) {
            saveConfig();
        }
        // Update all of the loaded configs.
        FileUtil.reload();

        this.playerDB = new FilePlayerDB(new File(getDataFolder(), "uuid2name.yml"));
        PlayerUtil.loadConfig(this.playerDB, getConfig());
        this.islandGenerator = new IslandGenerator(getDataFolder(), getConfig());
        this.challengeLogic = new ChallengeLogic(getFileConfiguration("challenges.yml"), this);
        this.menu = new SkyBlockMenu(this, this.challengeLogic);
        this.levelLogic = new LevelLogic(this, getFileConfiguration("levelConfig.yml"));
        this.islandLogic = new IslandLogic(this, this.directoryIslands);
        this.notifier = new PlayerNotifier(getConfig());
        this.playerLogic = new PlayerLogic(this);
        this.playerNameChangeManager = new PlayerNameChangeManager(this, this.playerDB);
        registerEvents(this.playerDB);
        if (this.autoRecalculateTask != null) {
            this.autoRecalculateTask.cancel();
        }
        int refreshEveryMinute = getConfig().getInt("options.island.autoRefreshScore", 0);
        if (refreshEveryMinute > 0) {
            int refreshTicks = refreshEveryMinute * 1200; // Ticks per minute
            this.autoRecalculateTask = new RecalculateRunnable(this).runTaskTimer(this, refreshTicks, refreshTicks);
        } else {
            this.autoRecalculateTask = null;
        }
        this.confirmHandler = new ConfirmHandler(this, getConfig().getInt("options.advanced.confirmTimeout", 10));
        this.cooldownHandler = new CooldownHandler(this);
        getCommand("island").setExecutor(new IslandCommand(this, this.menu));
        getCommand("challenges").setExecutor(new ChallengesCommand(this));
        getCommand("usb").setExecutor(new AdminCommand(this.instance));
    }

    public boolean isSkyWorld(World world) {
        if (world == null) {
            return false;
        }
        return getSkyBlockWorld().getName().equalsIgnoreCase(world.getName());
    }

    public boolean isSkyAssociatedWorld(World world) {
        return world.getName().startsWith(skyBlockWorld.getName());
    }

    public IslandLogic getIslandLogic() {
        return islandLogic;
    }

    public void execCommand(Player player, String command) {
        if (command == null || player == null) {
            return;
        }
        if (!isSkyAssociatedWorld(player.getWorld())) {
            return;
        }
        command = command
                .replaceAll("\\{player\\}", player.getName())
                .replaceAll("\\{playerName\\}", player.getDisplayName())
                .replaceAll("\\{position\\}", player.getLocation().toString()); // Figure out what this should be
        Matcher m = Pattern.compile("^\\{p=(?<prob>0?\\.[0-9]+)\\}(.*)$").matcher(command);
        if (m.matches()) {
            double p = Double.parseDouble(m.group("prob"));
            command = m.group(2);
            if (RND.nextDouble() > p) {
                return; // Skip the command
            }
        }
        m = Pattern.compile("^\\{d=(?<delay>[0-9]+)\\}(.*)$").matcher(command);
        int delay = 0;
        if (m.matches()) {
            delay = Integer.parseInt(m.group("delay"));
            command = m.group(2);
        }
        if (command.contains("{party}")) {
            PlayerInfo playerInfo = getPlayerInfo(player);
            for (String member : getIslandInfo(playerInfo).getMembers()) {
                doExecCommand(player, command.replaceAll("\\{party\\}", member), delay);
            }
        } else {
            doExecCommand(player, command, delay);
        }
    }

    private void doExecCommand(final Player player, final String command, int delay) {
        if (delay == 0) {
            Bukkit.getScheduler().runTask(this, new Runnable() {
                @Override
                public void run() {
                    doExecCommand(player, command);
                }
            });
        } else if (delay > 0) {
            Bukkit.getScheduler().runTaskLater(this, new Runnable() {
                @Override
                public void run() {
                    doExecCommand(player, command);
                }
            }, delay);
        } else {
            log(Level.INFO, "WARN: Misconfigured command found, with negative delay! " + command);
        }
    }

    private void doExecCommand(Player player, String command) {
        if (command.startsWith("op:")) {
            if (player.isOp()) {
                player.performCommand(command.substring(3).trim());
            } else {
                player.setOp(true);
                // Prevent privilege escalation if called command throws unhandled exception
                try {
                    player.performCommand(command.substring(3).trim());
                } finally {
                    player.setOp(false);
                }
            }
        } else if (command.startsWith("console:")) {
            getServer().dispatchCommand(getServer().getConsoleSender(), command.substring(8).trim());
        } else {
            player.performCommand(command);
        }
    }

    public USBImporterExecutor getPlayerImporter() {
        if (importer == null) {
            importer = new USBImporterExecutor(this);
        }
        return importer;
    }

    public boolean playerIsInSpawn(Player player) {
        Location pLoc = player.getLocation();
        if (!isSkyWorld(pLoc.getWorld())) {
            return false;
        }
        Location spawnCenter = new Location(skyBlockWorld, 0, pLoc.getBlockY(), 0);
        return spawnCenter.distance(pLoc) <= Settings.general_spawnSize;
    }

    /**
     * Notify the player, but max. every X seconds.
     */
    public void notifyPlayer(Player player, String msg) {
        notifier.notifyPlayer(player, msg);
    }

    public BalancedExecutor getExecutor() {
        return executor;
    }

    public BalancedExecutor getAsyncExecutor() {
        return asyncExecutor;
    }

    public static uSkyBlockAPI getAPI() {
        return getInstance();
    }

    // API

    @Override
    public List<IslandLevel> getTopTen() {
        return getRanks(0, 10);
    }

    @Override
    public List<IslandLevel> getRanks(int offset, int length) {
        return islandLogic != null ? islandLogic.getRanks(offset, length) : Collections.<IslandLevel>emptyList();
    }

    @Override
    public double getIslandLevel(Player player) {
        PlayerInfo info = getPlayerInfo(player);
        if (info != null) {
            IslandInfo islandInfo = getIslandInfo(info);
            if (islandInfo != null) {
                return islandInfo.getLevel();
            }
        }
        return 0;
    }

    @Override
    public IslandRank getIslandRank(Player player) {
        PlayerInfo playerInfo = getPlayerInfo(player);
        return islandLogic != null && playerInfo != null && playerInfo.getHasIsland() ?
                islandLogic.getRank(playerInfo.locationForParty())
                : null;
    }

    public void fireChangeEvent(CommandSender sender, uSkyBlockEvent.Cause cause) {
        Player player = (sender instanceof Player) ? (Player) sender : null;
        final uSkyBlockEvent event = new uSkyBlockEvent(player, this, cause);
        fireChangeEvent(event);
    }

    public void fireChangeEvent(final uSkyBlockEvent event) {
        getServer().getScheduler().runTaskAsynchronously(this, new Runnable() {
                    @Override
                    public void run() {
                        getServer().getPluginManager().callEvent(event);
                    }
                }
        );
    }

    public synchronized boolean isProtectAllActive() {
        return protectAllActive;
    }

    public synchronized void setProtectAllActive(boolean protectAllActive) {
        this.protectAllActive = protectAllActive;
    }

    public String getVersionInfo() {
        PluginDescriptionFile description = getDescription();
        String msg = tr("\u00a77Name: \u00a7b{0}\n" + description.getName());
        msg += tr("\u00a77Version: \u00a7b{0}\n", description.getVersion());
        msg += tr("\u00a77Description: \u00a7b{0}\n", description.getDescription());
        msg += tr("\u00a77------------------------------\n");
        msg += tr("\u00a77Server: \u00a7e{0} {1}\n", getServer().getName(), getServer().getVersion());
        for (String[] dep : depends) {
            Plugin dependency = getServer().getPluginManager().getPlugin(dep[0]);
            if (dependency != null) {
                msg += tr("\u00a77------------------------------\n");
                msg += tr("\u00a77Name: \u00a7d{0} ({1})\n", dependency.getName(), dependency.isEnabled() ? tr("\u00a72ENABLED") : tr("\u00a74DISABLED"));
                msg += tr("\u00a77Version: \u00a7d{0}\n", dependency.getDescription().getVersion());
            }
        }
        msg += tr("\u00a77------------------------------\n");
        return msg;
    }

    public PlayerDB getPlayerDB() {
        return playerDB;
    }

    public void calculateScoreAsync(final Player player, String islandName, final Callback<IslandScore> callback) {
        final IslandInfo islandInfo = getIslandInfo(islandName);
        getLevelLogic().calculateScoreAsync(islandInfo.getIslandLocation(), new Callback<IslandScore>() {
            @Override
            public void run() {
                IslandScore score = getState();
                callback.setState(score);
                callback.run();
                islandInfo.setLevel(score.getScore());
                getIslandLogic().updateRank(islandInfo, score);
                fireChangeEvent(new uSkyBlockEvent(player, getInstance(), uSkyBlockEvent.Cause.SCORE_CHANGED));
            }
        });
    }

    public ConfirmHandler getConfirmHandler() {
        return confirmHandler;
    }

    public CooldownHandler getCooldownHandler() {
        return cooldownHandler;
    }

    public PlayerLogic getPlayerLogic() {
        return playerLogic;
    }

    public PlayerNameChangeManager getPlayerNameChangeManager() {
        return playerNameChangeManager;
    }

    public Map<String, Biome> getValidBiomes() {
        return this.validBiomes;
    }
}

package us.talabrek.ultimateskyblock;

import java.util.Iterator;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.sk89q.worldedit.BlockVector;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.domains.DefaultDomain;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.databases.ProtectionDatabaseException;
import com.sk89q.worldguard.protection.flags.DefaultFlag;
import com.sk89q.worldguard.protection.flags.StateFlag.State;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;

public class WorldGuardHandler {
	public static void addPlayerToOldRegion(final String owner, final String player) {
		if (getWorldGuard().getRegionManager(uSkyBlock.getSkyBlockWorld()).hasRegion(owner + "Island")) {
			final DefaultDomain owners = getWorldGuard().getRegionManager(uSkyBlock.getSkyBlockWorld()).getRegion(owner + "Island")
					.getOwners();
			owners.addPlayer(player);
			getWorldGuard().getRegionManager(uSkyBlock.getSkyBlockWorld()).getRegion(owner + "Island").setOwners(owners);
		}
	}

	public static BlockVector getProtectionVectorLeft(final Location island) {
		return new BlockVector(island.getX() + Settings.island_protectionRange / 2, 255.0D, island.getZ() + Settings.island_protectionRange
				/ 2);
	}

	public static BlockVector getProtectionVectorRight(final Location island) {
		return new BlockVector(island.getX() - Settings.island_protectionRange / 2, 0.0D, island.getZ() - Settings.island_protectionRange
				/ 2);
	}

	public static WorldGuardPlugin getWorldGuard() {
		final Plugin plugin = uSkyBlock.getInstance().getServer().getPluginManager().getPlugin("WorldGuard");

		if (plugin == null || !(plugin instanceof WorldGuardPlugin)) { return null; }

		return (WorldGuardPlugin) plugin;
	}

	public static void islandLock(final CommandSender sender, final String player) {
		try {
			if (getWorldGuard().getRegionManager(uSkyBlock.getSkyBlockWorld()).hasRegion(player + "Island")) {
				getWorldGuard().getRegionManager(uSkyBlock.getSkyBlockWorld()).getRegion(player + "Island")
						.setFlag(DefaultFlag.ENTRY, DefaultFlag.ENTRY.parseInput(getWorldGuard(), sender, "deny"));
				sender.sendMessage(ChatColor.YELLOW + "Your island is now locked. Only your party members may enter.");
				getWorldGuard().getRegionManager(uSkyBlock.getSkyBlockWorld()).save();
			} else {
				sender.sendMessage(ChatColor.RED + "You must be the party leader to lock your island!");
			}
		} catch (final Exception ex) {
			System.out.println("uSkyblock " + "ERROR: Failed to lock " + player + "'s Island (" + sender.getName() + ")");
			ex.printStackTrace();
		}
	}

	public static void islandUnlock(final CommandSender sender, final String player) {
		try {
			if (getWorldGuard().getRegionManager(uSkyBlock.getSkyBlockWorld()).hasRegion(player + "Island")) {
				getWorldGuard().getRegionManager(uSkyBlock.getSkyBlockWorld()).getRegion(player + "Island")
						.setFlag(DefaultFlag.ENTRY, DefaultFlag.ENTRY.parseInput(getWorldGuard(), sender, "allow"));
				sender.sendMessage(ChatColor.YELLOW
						+ "Your island is unlocked and anyone may enter, however only you and your party members may build or remove blocks.");
				getWorldGuard().getRegionManager(uSkyBlock.getSkyBlockWorld()).save();
			} else {
				sender.sendMessage(ChatColor.RED + "You must be the party leader to unlock your island!");
			}
		} catch (final Exception ex) {
			System.out.println("uSkyblock " + "ERROR: Failed to unlock " + player + "'s Island (" + sender.getName() + ")");
			ex.printStackTrace();
		}
	}
	
	public static void protectIsland(String playerName) throws IllegalArgumentException, IllegalStateException
	{
		protectIsland(uSkyBlock.getInstance().getPlayer(playerName));
	}
	
	public static void protectIsland(PlayerInfo pi) throws IllegalArgumentException, IllegalStateException
	{
		if(!Settings.island_protectWithWorldGuard || pi == null)
			return;
		
		try
		{
			String regionName = pi.getPlayerName() + "Island";

			if(!pi.getHasIsland())
				throw new IllegalArgumentException(pi.getPlayerName() + " does not have an island.");
				
			RegionManager manager = getWorldGuard().getRegionManager(uSkyBlock.getSkyBlockWorld());
			
			if(manager.hasRegion(regionName))
				return;
			
			ProtectedRegion region = null;
			region = new ProtectedCuboidRegion(regionName, getProtectionVectorLeft(pi.getIslandLocation()), getProtectionVectorRight(pi.getIslandLocation()));
			region.getOwners().addPlayer(pi.getPlayerName());
			region.setPriority(100);
			region.setFlag( DefaultFlag.GREET_MESSAGE, "You are entering a protected island area. (" + pi.getPlayerName() + ")");
			region.setFlag( DefaultFlag.FAREWELL_MESSAGE, "You are leaving a protected island area. (" + pi.getPlayerName() + ")");
			
			if(Settings.island_allowPvP.equalsIgnoreCase("allow"))
				region.setFlag(DefaultFlag.PVP, State.ALLOW);
			else
				region.setFlag(DefaultFlag.PVP, State.DENY);
			
			region.setFlag(DefaultFlag.CHEST_ACCESS, State.DENY);
			region.setFlag(DefaultFlag.USE, State.DENY);
			
			ApplicableRegionSet set = manager.getApplicableRegions(pi.getIslandLocation());
			if (set.size() > 0) 
			{
				for (final ProtectedRegion regions : set) 
				{
					if (!regions.getId().equalsIgnoreCase("__global__"))
						manager.removeRegion(regions.getId());
				}
			}
			
			manager.addRegion(region);
			uSkyBlock.getLog().info("New protected region created for " + pi.getPlayerName() + "'s Island");
			manager.save();
		} 
		catch (ProtectionDatabaseException e) 
		{
			throw new IllegalStateException("Unable to save regions", e);
		}
	}
	
	public static void removePlayerFromRegion(final String owner, final String player) {
		if (getWorldGuard().getRegionManager(uSkyBlock.getSkyBlockWorld()).hasRegion(owner + "Island")) {
			final DefaultDomain owners = getWorldGuard().getRegionManager(uSkyBlock.getSkyBlockWorld()).getRegion(owner + "Island")
					.getOwners();
			owners.removePlayer(player);
			getWorldGuard().getRegionManager(uSkyBlock.getSkyBlockWorld()).getRegion(owner + "Island").setOwners(owners);
		}
	}

	public static void resetPlayerRegion(final String owner) {
		if (getWorldGuard().getRegionManager(uSkyBlock.getSkyBlockWorld()).hasRegion(owner + "Island")) {
			final DefaultDomain owners = new DefaultDomain();
			owners.addPlayer(owner);

			getWorldGuard().getRegionManager(uSkyBlock.getSkyBlockWorld()).getRegion(owner + "Island").setOwners(owners);
		}
	}

	public static void transferRegion(final String owner, final String player, final CommandSender sender) {
		try {
			ProtectedRegion region2 = null;
			region2 = new ProtectedCuboidRegion(player + "Island", getWorldGuard().getRegionManager(Bukkit.getWorld("skyworld"))
					.getRegion(owner + "Island").getMinimumPoint(), getWorldGuard()
					.getRegionManager(Bukkit.getWorld(Settings.general_worldName)).getRegion(owner + "Island").getMaximumPoint());
			region2.setOwners(getWorldGuard().getRegionManager(uSkyBlock.getSkyBlockWorld()).getRegion(owner + "Island").getOwners());
			region2.setParent(getWorldGuard().getRegionManager(uSkyBlock.getSkyBlockWorld()).getRegion("__Global__"));
			region2.setFlag(
					DefaultFlag.GREET_MESSAGE,
					DefaultFlag.GREET_MESSAGE.parseInput(getWorldGuard(), sender, "You are entering a protected island area. (" + player
							+ ")"));
			region2.setFlag(
					DefaultFlag.FAREWELL_MESSAGE,
					DefaultFlag.FAREWELL_MESSAGE.parseInput(getWorldGuard(), sender, "You are leaving a protected island area. (" + player
							+ ")"));
			region2.setFlag(DefaultFlag.PVP, DefaultFlag.PVP.parseInput(getWorldGuard(), sender, "deny"));
			getWorldGuard().getRegionManager(uSkyBlock.getSkyBlockWorld()).removeRegion(owner + "Island");
			getWorldGuard().getRegionManager(uSkyBlock.getSkyBlockWorld()).addRegion(region2);
		} catch (final Exception e) {
			System.out.println("Error transferring WorldGuard Protected Region from (" + owner + ") to (" + player + ")");
		}
	}
}
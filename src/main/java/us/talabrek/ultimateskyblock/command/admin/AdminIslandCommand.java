package us.talabrek.ultimateskyblock.command.admin;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import us.talabrek.ultimateskyblock.command.common.CompositeUSBCommand;
import us.talabrek.ultimateskyblock.handler.WorldGuardHandler;
import us.talabrek.ultimateskyblock.command.common.AbstractUSBCommand;
import us.talabrek.ultimateskyblock.island.IslandInfo;
import us.talabrek.ultimateskyblock.player.PlayerInfo;
import us.talabrek.ultimateskyblock.uSkyBlock;

import java.util.Map;

/**
 * The island moderator command.
 */
public class AdminIslandCommand extends CompositeUSBCommand {
    public AdminIslandCommand(final uSkyBlock plugin) {
        super("island", "usb.admin.island", "manage islands");
        add(new AbstractIslandInfoCommand("protect", "usb.mod.protect", "protects the island") {
            @Override
            protected void doExecute(CommandSender sender, PlayerInfo playerInfo, IslandInfo islandInfo, String... args) {
                protectIsland(sender, playerInfo, islandInfo);
            }
        });
        add(new AbstractUSBCommand("delete", "usb.admin.delete", "?island", "delete the island (removes the blocks)") {
            @Override
            public boolean execute(final CommandSender sender, String alias, Map<String, Object> data, String... args) {
                if (args.length == 1) {
                    PlayerInfo playerInfo = plugin.getPlayerInfo(args[0]);
                    if (playerInfo == null) {
                        sender.sendMessage(String.format("\u00a74Could not locate an island for player %s!", args[0]));
                        return false;
                    }
                    deleteIsland(sender, playerInfo);
                    return true;
                } else if (args.length == 0 && sender instanceof Player) {
                    String islandName = WorldGuardHandler.getIslandNameAt(((Player) sender).getLocation());
                    if (islandName != null) {
                        if (plugin.deleteEmptyIsland(islandName, new Runnable() {
                            @Override
                            public void run() {
                                sender.sendMessage("\u00a79Deleted abandoned island at your current location.");
                            }
                        })) {
                            return true;
                        } else {
                            sender.sendMessage("\u00a74Island at this location has members!\n\u00a7eUse \u00a79/usb island delete <name>\u00a7e to delete it.");
                        }
                    }
                }
                return false;
            }
        });
        add(new AbstractIslandInfoCommand("remove", "usb.admin.remove", "removes the player from the island") {
            @Override
            protected void doExecute(CommandSender sender, PlayerInfo playerInfo, IslandInfo islandInfo, String... args) {
                removePlayerFromIsland(sender, playerInfo, islandInfo);
            }
        });
        add(new AbstractIslandInfoCommand("info", null, "print out info about the island") {
            @Override
            protected void doExecute(CommandSender sender, PlayerInfo playerInfo, IslandInfo islandInfo, String... args) {
                sender.sendMessage(islandInfo.toString());
            }
        });
        add(new AbstractUSBCommand("protectall", "usb.mod.protectall", "protects all islands (time consuming)") {
            @Override
            public boolean execute(CommandSender sender, String alias, Map<String, Object> data, String... args) {
                protectAll(sender);
                return true;
            }
        });
        add(new AbstractIslandInfoCommand("setbiome", "usb.mod.biome", "sets the biome of the island") {
            private final String[] params = new String[]{"player", "biome"};
            @Override
            public String[] getParams() {
                return params;
            }

            @Override
            protected void doExecute(CommandSender sender, PlayerInfo playerInfo, IslandInfo islandInfo, String... args) {
                if (args.length > 0) {
                    setBiome(sender, playerInfo, islandInfo, args[0]);
                } else {
                    sender.sendMessage("\u00a74No biome supplied!");
                }
            }
        });
    }

    private void removePlayerFromIsland(CommandSender sender, PlayerInfo playerInfo, IslandInfo islandInfo) {
        sender.sendMessage("Removing " + playerInfo.getPlayerName() + " from island");
        playerInfo.removeFromIsland();
        islandInfo.removeMember(playerInfo);
        playerInfo.save();
    }

    private void setBiome(CommandSender sender, PlayerInfo playerInfo, IslandInfo islandInfo, String biome) {
        if (uSkyBlock.getInstance().setBiome(playerInfo.getIslandLocation(), biome)) {
            islandInfo.setBiome(biome);
            sender.sendMessage("\u00a7e" + playerInfo.getPlayerName() + " has had their biome changed to " + biome + ".");
        } else {
            sender.sendMessage("\u00a7e" + playerInfo.getPlayerName() + " has had their biome changed to OCEAN.");
        }
        sender.sendMessage(ChatColor.GREEN + "You may need to go to spawn, or relog, to see the changes.");
    }

    private void protectAll(CommandSender sender) {
        sender.sendMessage("\u00a7eOups, that was embarrassing - protectall is currently out of order");
    }

    private void deleteIsland(CommandSender sender, PlayerInfo playerInfo) {
        if (playerInfo.getIslandLocation() != null) {
            sender.sendMessage("\u00a7eRemoving " + playerInfo.getPlayerName() + "'s island.");
            uSkyBlock.getInstance().deletePlayerIsland(playerInfo.getPlayerName(), null);
        } else {
            sender.sendMessage("Error: That player does not have an island!");
        }
    }

    private void protectIsland(CommandSender sender, PlayerInfo playerInfo, IslandInfo islandInfo) {
        if (WorldGuardHandler.protectIsland(sender, playerInfo)) {
            sender.sendMessage("\u00a7e" + playerInfo.getPlayerName() + "'s island at " + playerInfo.locationForParty() + " has been protected");
        } else {
            sender.sendMessage("\u00a74" + playerInfo.getPlayerName() + "'s island at " + playerInfo.locationForParty() + " was already protected");
        }
    }
}

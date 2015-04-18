package us.talabrek.ultimateskyblock.handler.worldedit;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.Vector;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitPlayer;
import com.sk89q.worldedit.bukkit.BukkitWorld;
import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.registry.WorldData;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import us.talabrek.ultimateskyblock.Settings;
import us.talabrek.ultimateskyblock.uSkyBlock;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The World Edit 6.0 specific adaptations
 */
public class WorldEdit6Adaptor implements WorldEditAdaptor {
    private static final Logger log = Logger.getLogger(WorldEdit6Adaptor.class.getName());
    private WorldEditPlugin worldEditPlugin;

    public WorldEdit6Adaptor() {
    }

    @Override
    public void init(WorldEditPlugin worldEditPlugin) {
        this.worldEditPlugin = worldEditPlugin;
    }

    @Override
    public boolean loadIslandSchematic(Player player, World world, File file, Location origin) {
        log.finer("Trying to load schematic " + file + " for " + player);
        WorldEdit worldEdit = worldEditPlugin.getWorldEdit();
        BukkitPlayer wePlayer = worldEditPlugin.wrapPlayer(player);
        LocalSession session = worldEdit.getSession(wePlayer);
        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
            BukkitWorld bukkitWorld = new BukkitWorld(world);
            ClipboardReader reader = ClipboardFormat.SCHEMATIC.getReader(in);

            WorldData worldData = bukkitWorld.getWorldData();
            Clipboard clipboard = reader.read(worldData);
            ClipboardHolder holder = new ClipboardHolder(clipboard, worldData);
            session.setClipboard(holder);

            EditSession editSession = new EditSession(bukkitWorld, 255 * Settings.island_protectionRange * Settings.island_protectionRange);
            editSession.enableQueue();
            Vector to = new Vector(origin.getBlockX(), origin.getBlockY(), origin.getBlockZ());
            Operation operation = holder
                    .createPaste(editSession, worldData)
                    .to(to)
                    .ignoreAirBlocks(false)
                    .build();
            Operations.completeLegacy(operation);
            editSession.flushQueue();
            editSession.commit();
            return true;
        } catch (IOException |WorldEditException e) {
            uSkyBlock.log(Level.WARNING, "Unable to load schematic " + file, e);
        }
        return false;
    }
}

package us.talabrek.ultimateskyblock.uuid;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import us.talabrek.ultimateskyblock.util.UUIDUtil;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

import static us.talabrek.ultimateskyblock.util.FileUtil.readConfig;

/**
 * PlayerDB backed by a simple yml-file.
 */
public class FilePlayerDB implements PlayerDB {
    private final YamlConfiguration config;
    private final File file;

    public FilePlayerDB(File file) {
        this.file = file;
        config = new YamlConfiguration();
        if (file.exists()) {
            readConfig(config, file);
        }
    }

    @Override
    public String getName(UUID uuid) {
        String uuidStr = UUIDUtil.asString(uuid);
        return config.getString(uuidStr + ".name", config.getString(uuidStr, null));
    }

    @Override
    public String getDisplayName(UUID uuid) {
        String uuidStr = UUIDUtil.asString(uuid);
        return config.getString(uuidStr + ".displayName", null);
    }

    @Override
    public void updatePlayer(Player player) throws IOException {
        String uuid = UUIDUtil.asString(player.getUniqueId());
        config.set(uuid + ".name", player.getName());
        config.set(uuid + ".displayName", player.getDisplayName());
        config.save(file);
    }
}

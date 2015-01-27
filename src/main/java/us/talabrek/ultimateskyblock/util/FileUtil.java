package us.talabrek.ultimateskyblock.util;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import us.talabrek.ultimateskyblock.uSkyBlock;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Common file-utilities.
 */
public enum FileUtil {;
    private static final Logger log = Logger.getLogger(FileUtil.class.getName());
    private static final Map<String, FileConfiguration> configFiles = new ConcurrentHashMap<>();
    private static File dataFolder;

    public static void readConfig(FileConfiguration config, File configFile) {
        if (configFile == null || !configFile.exists()) {
            log.log(Level.INFO, "No "  + configFile + " found, it will be created");
            return;
        }
        try (Reader rdr = new InputStreamReader(new FileInputStream(configFile), "UTF-8")) {
            config.load(rdr);
        } catch (InvalidConfigurationException e) {
            log.log(Level.SEVERE, "Unable to read config file " + configFile, e);
            if (configFile.exists()) {
                try {
                    Files.copy(Paths.get(configFile.toURI()), Paths.get(configFile.getParent(), configFile.getName() + ".err"), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e1) {
                    // Ignore - we tried...
                }
            }
        } catch (IOException e) {
            log.log(Level.SEVERE, "Unable to read config file " + configFile, e);
        }
    }

    public static void readConfig(FileConfiguration config, InputStream inputStream) {
        if (inputStream == null) {
            return;
        }
        try (Reader rdr = new InputStreamReader(inputStream, "UTF-8")) {
            config.load(rdr);
        } catch (InvalidConfigurationException | IOException e) {
            log.log(Level.SEVERE, "Unable to read configuration", e);
        }
    }

    public static FilenameFilter createYmlFilenameFilter() {
        return new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name != null && name.endsWith(".yml");
            }
        };
    }

    public static FilenameFilter createIslandFilenameFilter() {
        return new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name != null && name.matches("-?[0-9]+,-?[0-9]+.yml");
            }
        };
    }

    public static String getBasename(String file) {
        if (file != null && file.lastIndexOf('.') != -1) {
            return file.substring(0, file.lastIndexOf('.'));
        }
        return file;
    }
    private static File getDataFolder() {
        return dataFolder != null ? dataFolder : uSkyBlock.getInstance().getDataFolder();
    }
    /**
     * System-encoding agnostic config-reader
     */
    public static FileConfiguration getFileConfiguration(String configName) {
        // Caching, for your convenience! (and a bigger memory print!)

        if (!configFiles.containsKey(configName)) {
            YamlConfiguration config = new YamlConfiguration();
            try {
                // read from datafolder!
                File configFile = new File(getDataFolder(), configName);
                // TODO: 09/12/2014 - R4zorax: Also replace + backup if jar-version is newer than local version
                YamlConfiguration configJar = new YamlConfiguration();
                readConfig(config, configFile);
                File orgFile = new File(dataFolder, configName + ".org");
                FileUtil.copy(FileUtil.class.getClassLoader().getResourceAsStream(configName), orgFile);
                readConfig(configJar, orgFile);
                if (!configFile.exists() || config.getInt("version", 0) < configJar.getInt("version", 0)) {
                    if (configFile.exists()) {
                        File backupFolder = new File(getDataFolder(), "backup");
                        backupFolder.mkdirs();
                        String bakFile = String.format("%1$s-%2$tY%2$tm%2$td-%2$tH%2$tM.yml", getBasename(configName), new Date());
                        log.log(Level.INFO, "Moving existing config " + configName + " to backup/" + bakFile);
                        Files.move(Paths.get(configFile.toURI()),
                                Paths.get(new File(backupFolder, bakFile).toURI()),
                                StandardCopyOption.REPLACE_EXISTING);
                        config = mergeConfig(configJar, config);
                        config.options().header("Merge from between jar-file and existing config");
                        config.save(configFile);
                    } else {
                        FileUtil.copy(FileUtil.class.getClassLoader().getResourceAsStream(configName), configFile);
                        config = configJar;
                    }
                }
            } catch (Exception e) {
                log.log(Level.SEVERE, "Unable to handle config-file " + configName, e);
            }
            configFiles.put(configName, config);
        }
        return configFiles.get(configName);
    }

    private static void copy(InputStream stream, File file) throws IOException {
        Files.copy(stream, Paths.get(file.toURI()), StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Merges the important keys from src to destination.
     * @param src The source (containing the new values).
     * @param dest The destination (containgin old-values).
     */
    private static YamlConfiguration mergeConfig(YamlConfiguration src, YamlConfiguration dest) {
        int version = src.getInt("version", dest.getInt("version"));
        dest.setDefaults(src); // Overwrite the "new-values" with existing ones.
        dest.options().copyDefaults(true);
        dest.set("version", version);
        return dest;
    }

    public static void init(File dataFolder) {
        FileUtil.dataFolder = dataFolder;
        configFiles.clear();
    }

    public static void reload() {
        for (Map.Entry<String, FileConfiguration> e : configFiles.entrySet()) {
            File configFile = new File(getDataFolder(), e.getKey());
            readConfig(e.getValue(), configFile);
        }
    }
}

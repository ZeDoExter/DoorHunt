package org.ZeDoExter.doorHunt.util;

import org.ZeDoExter.doorHunt.DoorHunt;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class LanguageManager {
    private final DoorHunt plugin;
    private FileConfiguration config;
    private String prefix;

    public LanguageManager(DoorHunt plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "language.yml");
        config = YamlConfiguration.loadConfiguration(file);
        prefix = config.getString("prefix", "&8[&aDoor Hunt&8] &7");
    }

    public String random(String path, Map<String, String> placeholders, String fallback) {
        ensureLoaded();
        List<String> options = config.getStringList(path);
        String value;
        if (options == null || options.isEmpty()) {
            value = fallback;
        } else {
            value = options.get(ThreadLocalRandom.current().nextInt(options.size()));
        }
        return plugin.color(getPrefix() + apply(value != null ? value : fallback, placeholders));
    }

    public String format(String path, Map<String, String> placeholders, String fallback) {
        ensureLoaded();
        String value = config.getString(path, fallback);
        return plugin.color(getPrefix() + apply(value != null ? value : fallback, placeholders));
    }

    public String getPrefix() {
        ensureLoaded();
        return prefix != null ? prefix : "";
    }

    private String apply(String message, Map<String, String> placeholders) {
        if (message == null) {
            return "";
        }
        String output = message;
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                output = output.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        return output;
    }

    private void ensureLoaded() {
        if (config == null) {
            reload();
        }
    }
}

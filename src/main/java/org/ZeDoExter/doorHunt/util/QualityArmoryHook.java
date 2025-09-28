package org.ZeDoExter.doorHunt.util;

import org.ZeDoExter.doorHunt.DoorHunt;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class QualityArmoryHook {
    private final DoorHunt plugin;
    private boolean qualityArmoryPresent;
    private List<String> loadoutCommands = defaultCommands();

    public QualityArmoryHook(DoorHunt plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        qualityArmoryPresent = Bukkit.getPluginManager().isPluginEnabled("QualityArmory");
        if (!qualityArmoryPresent) {
            plugin.getLogger().warning("QualityArmory not detected, seeker loadout commands will still run if the plugin loads later.");
        }

        ConfigurationSection section = plugin.getConfig().getConfigurationSection("settings.seeker-loadout");
        List<String> configured = section != null ? section.getStringList("commands") : null;
        if (configured == null || configured.isEmpty()) {
            loadoutCommands = defaultCommands();
        } else {
            loadoutCommands = configured.stream()
                    .filter(command -> command != null && !command.trim().isEmpty())
                    .map(String::trim)
                    .collect(Collectors.toCollection(ArrayList::new));
        }
    }

    public void giveSeekerLoadout(Player player) {
        if (loadoutCommands.isEmpty()) {
            return;
        }
        CommandSender console = Bukkit.getConsoleSender();
        for (String raw : loadoutCommands) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String command = raw.trim();
            if (command.startsWith("/")) {
                command = command.substring(1);
            }
            String resolved = applyPlaceholders(command, player);
            try {
                Bukkit.dispatchCommand(console, resolved);
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Failed to dispatch seeker loadout command '" + command + "' for " + player.getName(), ex);
            }
        }
        if (!qualityArmoryPresent) {
            player.getInventory().addItem(new ItemStack(Material.IRON_SWORD));
        }
    }

    private String applyPlaceholders(String command, Player player) {
        String name = player.getName();
        return command
                .replace("%player%", name)
                .replace("%player_name%", name)
                .replace("<player>", name)
                .replace("<seeker>", name)
                .replace("{player}", name);
    }

    private List<String> defaultCommands() {
        List<String> defaults = new ArrayList<>();
        defaults.add("/qa give m16 %player%");
        defaults.add("/qa give 556 %player% 30");
        defaults.add("/qa give grenade %player%");
        return defaults;
    }
}

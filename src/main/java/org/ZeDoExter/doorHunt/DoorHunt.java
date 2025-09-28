package org.ZeDoExter.doorHunt;

import org.ZeDoExter.doorHunt.command.DoorHuntCommand;
import org.ZeDoExter.doorHunt.game.GameManager;
import org.ZeDoExter.doorHunt.listener.GameListener;
import org.ZeDoExter.doorHunt.listener.SettingsListener;
import org.ZeDoExter.doorHunt.scoreboard.ScoreboardService;
import org.ZeDoExter.doorHunt.util.LocationUtil;
import org.ZeDoExter.doorHunt.util.LanguageManager;
import org.ZeDoExter.doorHunt.util.QualityArmoryHook;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;

import java.io.File;

public class DoorHunt extends JavaPlugin {
    private static final String RETURN_ITEM_NAME = "&cกลับ Lobby";

    private GameManager gameManager;
    private ScoreboardService scoreboardService;
    private FileConfiguration scoreboardConfig;
    private Location lobbyLocation;
    private LanguageManager languageManager;
    private QualityArmoryHook qualityArmoryHook;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadLobbyLocation();
        saveResourceIfNotExists("scoreboard.yml");
        saveResourceIfNotExists("language.yml");
        loadScoreboardConfig();
        scoreboardService = new ScoreboardService(this);
        scoreboardService.reload();
        languageManager = new LanguageManager(this);
        languageManager.reload();
        qualityArmoryHook = new QualityArmoryHook(this);
        qualityArmoryHook.reload();
        gameManager = new GameManager(this, scoreboardService);
        gameManager.loadArenas();

        DoorHuntCommand command = new DoorHuntCommand(this, gameManager);
        if (getCommand("dh") != null) {
            getCommand("dh").setExecutor(command);
            getCommand("dh").setTabCompleter(command);
        }
        Bukkit.getPluginManager().registerEvents(new GameListener(this, gameManager), this);
        Bukkit.getPluginManager().registerEvents(new SettingsListener(this, gameManager), this);

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (gameManager.getGame(online) == null) {
                gameManager.showLobbyBoard(online);
            }
        }
        gameManager.updateLobbyBoards();
    }

    @Override
    public void onDisable() {
        if (gameManager != null) {
            gameManager.shutdown();
        }
    }

    public String color(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public void resetPlayer(Player player) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.setFireTicks(0);
        player.setFallDistance(0);
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setExhaustion(0f);
        player.setLevel(0);
        player.setExp(0f);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setHealth(player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getDefaultValue());
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
    }

    public void loadLobbyLocation() {
        ConfigurationSection section = getConfig().getConfigurationSection("lobby");
        lobbyLocation = LocationUtil.deserialize(section);
    }

    public Location getLobbyLocation() {
        return lobbyLocation;
    }

    public void setLobbyLocation(Location location) {
        lobbyLocation = location != null ? location.clone() : null;
        if (lobbyLocation == null) {
            getConfig().set("lobby", null);
        } else {
            ConfigurationSection section = getConfig().createSection("lobby");
            LocationUtil.serialize(lobbyLocation, section);
        }
        saveConfig();
        if (gameManager != null) {
            gameManager.updateLobbyBoards();
        }
    }

    private void loadScoreboardConfig() {
        File file = new File(getDataFolder(), "scoreboard.yml");
        scoreboardConfig = YamlConfiguration.loadConfiguration(file);
    }

    public void reloadScoreboard() {
        loadScoreboardConfig();
        if (scoreboardService != null) {
            scoreboardService.reload();
            scoreboardService.updateLobbyBoards();
        }
    }

    public FileConfiguration getScoreboardConfig() {
        return scoreboardConfig;
    }

    public GameManager getGameManager() {
        return gameManager;
    }

    public ScoreboardService getScoreboardService() {
        return scoreboardService;
    }

    public LanguageManager getLanguageManager() {
        return languageManager;
    }

    public QualityArmoryHook getQualityArmoryHook() {
        return qualityArmoryHook;
    }

    private void saveResourceIfNotExists(String resource) {
        File file = new File(getDataFolder(), resource);
        if (!file.exists()) {
            saveResource(resource, false);
        }
    }

    public boolean isReturnItem(ItemStack item) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) {
            return false;
        }
        return item.getItemMeta().getDisplayName().equals(color(RETURN_ITEM_NAME));
    }

    public String getReturnItemName() {
        return color(RETURN_ITEM_NAME);
    }
}

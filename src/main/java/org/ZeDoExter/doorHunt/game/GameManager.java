package org.ZeDoExter.doorHunt.game;

import org.ZeDoExter.doorHunt.DoorHunt;
import org.ZeDoExter.doorHunt.scoreboard.ScoreboardService;
import org.ZeDoExter.doorHunt.util.LocationUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import org.bukkit.inventory.Inventory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GameManager {
    private final DoorHunt plugin;
    private final Map<String, GameArena> arenas = new LinkedHashMap<>();
    private final Map<String, GameInstance> instances = new ConcurrentHashMap<>();
    private final Map<UUID, GameInstance> playerGames = new ConcurrentHashMap<>();
    private final ScoreboardService scoreboardService;
    private final Map<UUID, GameArena> settingsViewers = new ConcurrentHashMap<>();
    private final Map<UUID, SettingsPrompt> pendingPrompts = new ConcurrentHashMap<>();

    public GameManager(DoorHunt plugin, ScoreboardService scoreboardService) {
        this.plugin = plugin;
        this.scoreboardService = scoreboardService;
    }

    public void loadArenas() {
        for (GameInstance instance : new ArrayList<>(instances.values())) {
            instance.shutdown();
        }
        instances.clear();
        arenas.clear();
        FileConfiguration config = plugin.getConfig();
        ConfigurationSection section = config.getConfigurationSection("arenas");
        if (section == null) {
            return;
        }

        for (String id : section.getKeys(false)) {
            ConfigurationSection arenaSection = section.getConfigurationSection(id);
            if (arenaSection == null) {
                continue;
            }

            GameArena arena = new GameArena(id);
            arena.setDisplayName(arenaSection.getString("name", id));
            arena.setMinPlayers(arenaSection.getInt("min-players", arena.getMinPlayers()));
            arena.setMaxPlayers(arenaSection.getInt("max-players", arena.getMaxPlayers()));
            arena.setRecruitingCountdown(arenaSection.getInt("recruiting-countdown", arena.getRecruitingCountdown()));
            arena.setPrepareDuration(arenaSection.getInt("prepare-duration", arena.getPrepareDuration()));
            arena.setHideDuration(arenaSection.getInt("hide-duration", arena.getHideDuration()));
            arena.setLiveDuration(arenaSection.getInt("live-duration", arena.getLiveDuration()));

            ConfigurationSection spawnSection = arenaSection.getConfigurationSection("spawns");
            if (spawnSection != null) {
                arena.setLobbyLocation(readLocation(spawnSection, "lobby"));
                arena.setHiderSpawn(readLocation(spawnSection, "hider"));
                arena.setSeekerWaitSpawn(readLocation(spawnSection, "seeker-wait"));
            }

            arenas.put(id.toLowerCase(Locale.ROOT), arena);
        }
        updateLobbyBoards();
    }

    private Location readLocation(ConfigurationSection section, String path) {
        ConfigurationSection node = section.getConfigurationSection(path);
        if (node == null) {
            return null;
        }
        Location location = LocationUtil.deserialize(node);
        if (location == null && node.getString("world") != null) {
            plugin.getLogger().warning("Failed to load spawn '" + path + "' for arena '" + section.getCurrentPath() + "'. Please verify the world exists.");
        }
        return location;
    }

    public void saveArena(GameArena arena) {
        FileConfiguration config = plugin.getConfig();
        String base = "arenas." + arena.getId() + ".";
        config.set(base + "name", arena.getDisplayName());
        config.set(base + "min-players", arena.getMinPlayers());
        config.set(base + "max-players", arena.getMaxPlayers());
        config.set(base + "recruiting-countdown", arena.getRecruitingCountdown());
        config.set(base + "prepare-duration", arena.getPrepareDuration());
        config.set(base + "hide-duration", arena.getHideDuration());
        config.set(base + "live-duration", arena.getLiveDuration());

        String spawnBase = base + "spawns.";
        config.set(spawnBase + "lobby", null);
        config.set(spawnBase + "hider", null);
        config.set(spawnBase + "seeker-wait", null);
        if (arena.getLobbyLocation() != null) {
            LocationUtil.serialize(arena.getLobbyLocation(), config.createSection(spawnBase + "lobby"));
        }
        if (arena.getHiderSpawn() != null) {
            LocationUtil.serialize(arena.getHiderSpawn(), config.createSection(spawnBase + "hider"));
        }
        if (arena.getSeekerWaitSpawn() != null) {
            LocationUtil.serialize(arena.getSeekerWaitSpawn(), config.createSection(spawnBase + "seeker-wait"));
        }
        plugin.saveConfig();
    }

    public GameArena createArena(String id, String name) {
        GameArena arena = new GameArena(id);
        arena.setDisplayName(name);
        FileConfiguration cfg = plugin.getConfig();
        arena.setMinPlayers(cfg.getInt("settings.min-players", arena.getMinPlayers()));
        arena.setMaxPlayers(cfg.getInt("settings.max-players", arena.getMaxPlayers()));
        arena.setRecruitingCountdown(cfg.getInt("settings.recruiting-countdown", arena.getRecruitingCountdown()));
        arena.setPrepareDuration(cfg.getInt("settings.prepare-duration", arena.getPrepareDuration()));
        arena.setHideDuration(cfg.getInt("settings.hide-duration", arena.getHideDuration()));
        arena.setLiveDuration(cfg.getInt("settings.live-duration", arena.getLiveDuration()));
        arenas.put(id.toLowerCase(Locale.ROOT), arena);
        saveArena(arena);
        updateLobbyBoards();
        return arena;
    }

    public boolean deleteArena(String id) {
        GameInstance instance = instances.remove(id.toLowerCase(Locale.ROOT));
        if (instance != null) {
            instance.shutdown();
        }
        GameArena arena = arenas.remove(id.toLowerCase(Locale.ROOT));
        if (arena == null) {
            return false;
        }
        plugin.getConfig().set("arenas." + id, null);
        plugin.saveConfig();
        updateLobbyBoards();
        return true;
    }

    public Collection<GameArena> getArenas() {
        return Collections.unmodifiableCollection(arenas.values());
    }

    public GameArena getArena(String id) {
        return arenas.get(id.toLowerCase(Locale.ROOT));
    }

    public GameInstance getInstance(String id) {
        String key = id.toLowerCase(Locale.ROOT);
        GameInstance instance = instances.get(key);
        if (instance != null) {
            return instance;
        }
        GameArena arena = arenas.get(key);
        if (arena == null) {
            return null;
        }
        instance = new GameInstance(plugin, arena, this, scoreboardService);
        instances.put(key, instance);
        return instance;
    }

    public GameInstance getLoadedInstance(String id) {
        return instances.get(id.toLowerCase(Locale.ROOT));
    }

    public GameInstance getInstance(GameArena arena) {
        return getInstance(arena.getId());
    }

    public GameInstance getGame(Player player) {
        return playerGames.get(player.getUniqueId());
    }

    public void showLobbyBoard(Player player) {
        scoreboardService.showLobby(player);
    }

    public void removeLobbyBoard(Player player) {
        scoreboardService.removeLobby(player);
    }

    public void updateLobbyBoards() {
        scoreboardService.updateLobbyBoards();
    }

    public int getRunningGameCount() {
        return (int) instances.values().stream()
                .filter(instance -> {
                    GameState state = instance.getState();
                    return state != GameState.WAITING && state != GameState.COUNTDOWN;
                })
                .count();
    }

    public int getPlayersInGamesCount() {
        return playerGames.size();
    }

    public void setPlayerGame(Player player, GameInstance instance) {
        if (instance == null) {
            playerGames.remove(player.getUniqueId());
        } else {
            playerGames.put(player.getUniqueId(), instance);
        }
    }
    public void clearPlayer(UUID uuid) {
        playerGames.remove(uuid);
    }

    public void openSettingsMenu(Player player, GameArena arena) {
        settingsViewers.put(player.getUniqueId(), arena);
        pendingPrompts.remove(player.getUniqueId());
        Inventory menu = org.ZeDoExter.doorHunt.gui.ArenaSettingsMenu.create(plugin, arena);
        player.openInventory(menu);
    }

    public GameArena getSettingsArena(Player player) {
        return settingsViewers.get(player.getUniqueId());
    }

    public void closeSettingsMenu(Player player) {
        settingsViewers.remove(player.getUniqueId());
        pendingPrompts.remove(player.getUniqueId());
    }

    public void reopenSettingsMenu(Player player) {
        GameArena arena = settingsViewers.get(player.getUniqueId());
        if (arena != null) {
            Inventory menu = org.ZeDoExter.doorHunt.gui.ArenaSettingsMenu.create(plugin, arena);
            player.openInventory(menu);
        }
    }

    public void beginPrompt(Player player, GameArena arena, ArenaSetting setting) {
        pendingPrompts.put(player.getUniqueId(), new SettingsPrompt(arena, setting));
        player.closeInventory();
        player.sendMessage(plugin.color("&e" + setting.getPrompt() + " &7(type 'cancel' to abort)"));
    }

    public boolean isAwaitingInput(Player player) {
        return pendingPrompts.containsKey(player.getUniqueId());
    }

    public void handleChatInput(Player player, String message) {
        SettingsPrompt prompt = pendingPrompts.get(player.getUniqueId());
        if (prompt == null) {
            return;
        }
        if (message.equalsIgnoreCase("cancel")) {
            pendingPrompts.remove(player.getUniqueId());
            player.sendMessage(plugin.color("&cยกเลิกการตั้งค่า"));
            reopenLater(player);
            return;
        }
        int value;
        try {
            value = Integer.parseInt(message.trim());
        } catch (NumberFormatException ex) {
            player.sendMessage(plugin.color("&cกรุณากรอกเป็นตัวเลข"));
            return;
        }
        if (!prompt.setting.isValid(prompt.arena, value)) {
            player.sendMessage(plugin.color("&cค่านี้ไม่ถูกต้องสำหรับ " + prompt.setting.getDisplayName()));
            return;
        }
        prompt.setting.set(prompt.arena, value);
        saveArena(prompt.arena);
        pendingPrompts.remove(player.getUniqueId());
        player.sendMessage(plugin.color("&aตั้งค่า " + prompt.setting.getDisplayName() + " เป็น &e" + value));
        reopenLater(player);
    }

    private void reopenLater(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> reopenSettingsMenu(player));
    }


    public void removePlayer(Player player) {
        GameInstance instance = playerGames.remove(player.getUniqueId());
        if (instance != null) {
            instance.leave(player, true);
        }
        removeLobbyBoard(player);
        updateLobbyBoards();
        closeSettingsMenu(player);
    }

    public void shutdown() {
        for (GameInstance instance : new ArrayList<>(instances.values())) {
            instance.shutdown();
        }
        instances.clear();
        arenas.clear();
        playerGames.clear();
    }

    private static class SettingsPrompt {
        private final GameArena arena;
        private final ArenaSetting setting;

        private SettingsPrompt(GameArena arena, ArenaSetting setting) {
            this.arena = arena;
            this.setting = setting;
        }
    }
}

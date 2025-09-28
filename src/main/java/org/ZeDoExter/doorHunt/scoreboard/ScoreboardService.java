package org.ZeDoExter.doorHunt.scoreboard;

import me.clip.placeholderapi.PlaceholderAPI;
import org.ZeDoExter.doorHunt.DoorHunt;
import org.ZeDoExter.doorHunt.game.GameInstance;
import org.ZeDoExter.doorHunt.game.GameManager;
import org.ZeDoExter.doorHunt.game.GameState;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ScoreboardService {
    private final DoorHunt plugin;
    private ScoreboardLayout defaultLayout;
    private final Map<GameState, ScoreboardLayout> layouts = new EnumMap<>(GameState.class);
    private ScoreboardLayout lobbyLayout;
    private final Set<UUID> lobbyPlayers = ConcurrentHashMap.newKeySet();
    private final boolean placeholderApiHooked;

    public ScoreboardService(DoorHunt plugin) {
        this.plugin = plugin;
        this.placeholderApiHooked = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
        if (placeholderApiHooked) {
            plugin.getLogger().info("Hooked into PlaceholderAPI for scoreboards.");
        }
    }

    public void reload() {
        layouts.clear();
        lobbyLayout = null;
        FileConfiguration config = plugin.getScoreboardConfig();
        String title = config.getString("title", "&aDoor Hunt");
        List<String> defaultLines = config.getStringList("states.DEFAULT");
        if (defaultLines.isEmpty()) {
            List<String> hidingLines = config.getStringList("states.HIDING");
            if (hidingLines.isEmpty()) {
                defaultLines = List.of("&fDoor Hunt");
            } else {
                defaultLines = hidingLines;
            }
        }
        defaultLayout = new ScoreboardLayout(title, defaultLines);
        ConfigurationSection states = config.getConfigurationSection("states");
        if (states != null) {
            for (String key : states.getKeys(false)) {
                if (key.equalsIgnoreCase("DEFAULT")) {
                    continue;
                }
                if (key.equalsIgnoreCase("LOBBY")) {
                    List<String> lines = states.getStringList(key);
                    if (lines.isEmpty()) {
                        lines = defaultLayout.getLines();
                    }
                    lobbyLayout = new ScoreboardLayout(title, lines);
                    continue;
                }
                GameState state = parseState(key);
                List<String> lines = states.getStringList(key);
                if (lines.isEmpty()) {
                    lines = defaultLayout.getLines();
                }
                layouts.put(state, new ScoreboardLayout(title, lines));
            }
        }
    }

    private GameState parseState(String key) {
        try {
            return GameState.valueOf(key.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return GameState.WAITING;
        }
    }

    public void update(GameInstance instance, Map<String, String> placeholders) {
        ScoreboardLayout layout = resolveLayout(instance.getState());
        if (layout == null) {
            return;
        }
        for (UUID uuid : instance.getPlayers()) {
            lobbyPlayers.remove(uuid);
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                apply(player, layout, placeholders, instance);
            }
        }
    }

    private ScoreboardLayout resolveLayout(GameState state) {
        GameState effective = remapState(state);
        return layouts.getOrDefault(effective, defaultLayout);
    }
    private GameState remapState(GameState state) {
        return switch (state) {
            case PREPARING, HIDING, LIVE, ENDING, COOLDOWN -> GameState.HIDING;
            default -> state;
        };
    }


    public void clear(Player player) {
        lobbyPlayers.remove(player.getUniqueId());
        if (Bukkit.getScoreboardManager() != null) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    public void showLobby(Player player) {
        lobbyPlayers.add(player.getUniqueId());
        applyLobby(player);
    }

    public void removeLobby(Player player) {
        lobbyPlayers.remove(player.getUniqueId());
    }

    public void updateLobbyBoards() {
        ScoreboardLayout layout = lobbyLayout != null ? lobbyLayout : defaultLayout;
        if (layout == null) {
            return;
        }
        Map<String, String> placeholders = buildLobbyPlaceholders();
        for (UUID uuid : new HashSet<>(lobbyPlayers)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                lobbyPlayers.remove(uuid);
                continue;
            }
            apply(player, layout, placeholders, null);
        }
    }

    private void applyLobby(Player player) {
        ScoreboardLayout layout = lobbyLayout != null ? lobbyLayout : defaultLayout;
        if (layout == null) {
            clear(player);
            return;
        }
        Map<String, String> placeholders = buildLobbyPlaceholders();
        apply(player, layout, placeholders, null);
    }

    private Map<String, String> buildLobbyPlaceholders() {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("online", String.valueOf(Bukkit.getOnlinePlayers().size()));
        GameManager manager = plugin.getGameManager();
        if (manager != null) {
            placeholders.put("arenas", String.valueOf(manager.getArenas().size()));
            placeholders.put("games", String.valueOf(manager.getRunningGameCount()));
            placeholders.put("ingame", String.valueOf(manager.getPlayersInGamesCount()));
        } else {
            placeholders.put("arenas", "0");
            placeholders.put("games", "0");
            placeholders.put("ingame", "0");
        }
        return placeholders;
    }

    private void apply(Player player, ScoreboardLayout layout, Map<String, String> placeholders, GameInstance context) {
        if (Bukkit.getScoreboardManager() == null) {
            return;
        }
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective("doorhunt", "dummy", colorize(resolvePlaceholders(layout.getTitle(), player, placeholders)));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        List<String> lines = layout.getLines();
        int score = lines.size();
        Set<String> used = new HashSet<>();
        for (String raw : lines) {
            String line = colorize(resolvePlaceholders(raw, player, placeholders));
            line = ensureUnique(line, used);
            objective.getScore(line).setScore(score--);
        }
        configureTeams(scoreboard, context);
        player.setScoreboard(scoreboard);
    }

    private void configureTeams(Scoreboard scoreboard, GameInstance context) {
        if (context == null) {
            return;
        }
        Team seekers = scoreboard.getTeam("seekers");
        if (seekers == null) {
            seekers = scoreboard.registerNewTeam("seekers");
        }
        seekers.setColor(ChatColor.RED);
        seekers.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        seekers.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);

        Team hiders = scoreboard.getTeam("hiders");
        if (hiders == null) {
            hiders = scoreboard.registerNewTeam("hiders");
        }
        hiders.setColor(ChatColor.GREEN);
        hiders.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        hiders.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);

        for (UUID uuid : context.getSeekers()) {
            String name = getPlayerName(uuid);
            if (name != null) {
                seekers.addEntry(name);
            }
        }
        for (UUID uuid : context.getHiders()) {
            String name = getPlayerName(uuid);
            if (name != null) {
                hiders.addEntry(name);
            }
        }
    }

    private String getPlayerName(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            return player.getName();
        }
        return Bukkit.getOfflinePlayer(uuid).getName();
    }

    private String ensureUnique(String line, Set<String> used) {
        if (line.length() > 32) {
            line = line.substring(0, 32);
        }
        String result = line;
        while (used.contains(result)) {
            result = result + ChatColor.RESET;
        }
        used.add(result);
        return result;
    }

    private String resolvePlaceholders(String input, Player player, Map<String, String> placeholders) {
        if (input == null) {
            return "";
        }
        String output = input;
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                output = output.replace("%" + entry.getKey() + "%", entry.getValue());
            }
        }
        if (placeholderApiHooked && player != null) {
            output = PlaceholderAPI.setPlaceholders(player, output);
        }
        return output;
    }

    private String colorize(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}

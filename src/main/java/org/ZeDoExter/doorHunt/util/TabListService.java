package org.ZeDoExter.doorHunt.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.ZeDoExter.doorHunt.DoorHunt;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Team;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * TabListService – ปลอดภัยกับทั้ง Paper (Adventure API) และ Spigot (legacy setPlayerListName)
 * - ใช้ Scoreboard ทีมสำหรับสีชื่อ (fallback)
 * - ถ้ามี TAB จะใช้ TAB ปรับสี/prefix ให้
 * - ถ้าไม่มี TAB และไม่มี Paper API → ตกมาที่ setPlayerListName(String)
 */
public class TabListService {

    public enum Role {
        SEEKER,
        HIDER
    }

    private static final String SEEKER_TEAM = "doorhunt_seeker";
    private static final String HIDER_TEAM  = "doorhunt_hider";

    private final DoorHunt plugin;

    // === Scoreboard ===
    private final Scoreboard mainScoreboard;
    private final Team seekerTeam;
    private final Team hiderTeam;

    // === State ===
    private final Map<UUID, Role> activeRoles = new ConcurrentHashMap<>();
    private final Map<UUID, Component> originalListNames = new ConcurrentHashMap<>();
    private final Map<UUID, String> originalLegacyNames = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastKnownNames = new ConcurrentHashMap<>();

    // === TAB reflection handles ===
    private final boolean tabHooked;
    private final Object tabApiInstance;
    private final Method getPlayerByUuidMethod;
    private final Method getPlayerByPlayerMethod;
    private final Object teamManagerInstance;
    private final Method teamSetPrefixMethod;
    private final Method teamResetPrefixMethod;
    private final Method teamSetNameColorMethod;
    private final Method teamResetNameColorMethod;
    private final Method tabPlayerSetTemporaryPrefixMethod;
    private final Method tabPlayerResetPrefixMethod;
    private final Method tabPlayerSetTemporaryColorMethod;
    private final Method tabPlayerResetColorMethod;
    private final Class<?> tabPlayerClass;
    private final Object tabColorRed;
    private final Object tabColorGreen;
    private final Object tabColorReset;

    // === Paper detection (playerListName(Component)) ===
    private final boolean hasPaperListName;

    public TabListService(DoorHunt plugin) {
        this.plugin = plugin;

        // --- Detect Paper Adventure API method availability safely ---
        boolean paperListName;
        try {
            // ถ้าเมธอดนี้มี แปลว่าเป็น Paper (1.19+) ที่มี Adventure API ฝัง
            Player.class.getMethod("playerListName", Component.class);
            paperListName = true;
        } catch (NoSuchMethodException e) {
            paperListName = false;
        }
        this.hasPaperListName = paperListName;

        // --- Setup Scoreboard teams (with null-safe manager) ---
        Scoreboard sb = null;
        Team seeker = null;
        Team hider = null;

        try {
            ScoreboardManager manager = Bukkit.getScoreboardManager();
            if (manager != null) {
                sb = manager.getMainScoreboard();
                seeker = ensureTeam(sb, SEEKER_TEAM, ChatColor.RED);
                hider = ensureTeam(sb, HIDER_TEAM, ChatColor.GREEN);
            } else {
                plugin.getLogger().warning("[TabListService] ScoreboardManager is null – scoreboard features disabled until server fully enables.");
            }
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "[TabListService] Failed to initialize scoreboard teams", t);
        }

        this.mainScoreboard = sb;
        this.seekerTeam = seeker;
        this.hiderTeam = hider;

        // --- Try hook TAB API via reflection (covering multiple TAB versions) ---
        Object apiInstance = null;
        Method playerByUuid = null;
        Method playerByPlayer = null;
        Object teamManager = null;
        Method setPrefix = null;
        Method resetPrefix = null;
        Method setNameColor = null;
        Method resetNameColor = null;
        Method temporaryPrefix = null;
        Method resetTemporaryPrefix = null;
        Method temporaryColor = null;
        Method resetTemporaryColor = null;
        Class<?> tabPlayerType = null;
        Object redColor = null, greenColor = null, resetColor = null;
        boolean hooked = false;

        try {
            Class<?> apiClass = Class.forName("me.neznamy.tab.api.TabAPI");
            Method instanceMethod = apiClass.getMethod("getInstance");
            apiInstance = instanceMethod.invoke(null);

            if (apiInstance != null) {
                playerByUuid   = findMethod(apiClass, "getPlayer", UUID.class);
                playerByPlayer = findMethod(apiClass, "getPlayer", Player.class);
                tabPlayerType  = Class.forName("me.neznamy.tab.api.TabPlayer");

                Class<?> tabColorClass = tryLoadClass(
                        "me.neznamy.tab.api.TabColor",
                        "me.neznamy.tab.api.chat.EnumChatFormat",
                        "me.neznamy.tab.api.util.EnumChatFormat"
                );
                redColor   = resolveColorConstant(tabColorClass, "RED");
                greenColor = resolveColorConstant(tabColorClass, "GREEN");
                resetColor = resolveColorConstant(tabColorClass, "RESET");
                if (resetColor == null) resetColor = resolveColorConstant(tabColorClass, "WHITE");

                Method teamManagerGetter = findNoArgMethod(apiClass, "getTeamManager", "getNameTagManager");
                if (teamManagerGetter != null) {
                    teamManager = teamManagerGetter.invoke(apiInstance);
                }

                if (teamManager != null) {
                    Class<?> teamManagerClass = teamManager.getClass();
                    setPrefix       = findTeamMethod(teamManagerClass, tabPlayerType, String.class, "set", "prefix");
                    resetPrefix     = findTeamMethod(teamManagerClass, tabPlayerType, null, "reset", "prefix");

                    if (tabColorClass != null) {
                        setNameColor   = findTeamMethod(teamManagerClass, tabPlayerType, tabColorClass, "set", "color");
                        resetNameColor = findTeamMethod(teamManagerClass, tabPlayerType, null, "reset", "color");
                    } else {
                        setNameColor   = findTeamMethod(teamManagerClass, tabPlayerType, String.class, "set", "color");
                        resetNameColor = findTeamMethod(teamManagerClass, tabPlayerType, null, "reset", "color");
                    }
                }

                if (tabPlayerType != null) {
                    temporaryPrefix        = findTabPlayerMethod(tabPlayerType, String.class, "temporary", "prefix");
                    resetTemporaryPrefix   = findTabPlayerMethod(tabPlayerType, null, "reset", "prefix");

                    if (redColor != null && greenColor != null) {
                        temporaryColor = findTabPlayerMethod(tabPlayerType, redColor.getClass(), "temporary", "color");
                    }
                    if (temporaryColor == null) {
                        temporaryColor = findTabPlayerMethod(tabPlayerType, String.class, "temporary", "color");
                    }
                    resetTemporaryColor    = findTabPlayerMethod(tabPlayerType, null, "reset", "color");
                }

                if ((playerByUuid != null || playerByPlayer != null) &&
                    (setPrefix != null || setNameColor != null || temporaryPrefix != null || temporaryColor != null)) {
                    hooked = true;
                }
            }
        } catch (Throwable ex) {
            plugin.getLogger().log(Level.FINE, "TAB API not available or changed – fallback to scoreboard/list name.", ex);
            apiInstance = null;
            playerByUuid = playerByPlayer = null;
            teamManager = null;
            setPrefix = resetPrefix = setNameColor = resetNameColor = null;
            temporaryPrefix = resetTemporaryPrefix = temporaryColor = resetTemporaryColor = null;
            tabPlayerType = null;
            redColor = greenColor = resetColor = null;
            hooked = false;
        }

        this.tabApiInstance = apiInstance;
        this.getPlayerByUuidMethod = playerByUuid;
        this.getPlayerByPlayerMethod = playerByPlayer;
        this.teamManagerInstance = teamManager;
        this.teamSetPrefixMethod = setPrefix;
        this.teamResetPrefixMethod = resetPrefix;
        this.teamSetNameColorMethod = setNameColor;
        this.teamResetNameColorMethod = resetNameColor;
        this.tabPlayerSetTemporaryPrefixMethod = temporaryPrefix;
        this.tabPlayerResetPrefixMethod = resetTemporaryPrefix;
        this.tabPlayerSetTemporaryColorMethod = temporaryColor;
        this.tabPlayerResetColorMethod = resetTemporaryColor;
        this.tabPlayerClass = tabPlayerType;
        this.tabColorRed = redColor;
        this.tabColorGreen = greenColor;
        this.tabColorReset = resetColor;
        this.tabHooked = hooked;

        if (hooked) {
            plugin.getLogger().info("[TabListService] Hooked into TAB for tablist role colors.");
        } else {
            plugin.getLogger().info("[TabListService] TAB not hooked – using scoreboard/list-name fallback.");
        }
    }

    // ===== Public API =====

    public void setRole(Player player, Role role) {
        if (player == null || role == null) return;

        UUID uuid = player.getUniqueId();
        activeRoles.put(uuid, role);
        lastKnownNames.put(uuid, player.getName());

        boolean appliedTab = applyTabRole(player, role);
        updateScoreboardTeams(player, role);
        applyListNameFallback(player, role, appliedTab);
    }

    public void clear(Player player) {
        if (player == null) return;

        UUID uuid = player.getUniqueId();
        activeRoles.remove(uuid);
        lastKnownNames.remove(uuid);

        boolean appliedTab = clearTabRole(player);
        removeFromTeams(player.getName());
        restoreListName(player, appliedTab);
    }

    public void clear(UUID uuid) {
        if (uuid == null) return;

        activeRoles.remove(uuid);
        originalListNames.remove(uuid);
        originalLegacyNames.remove(uuid);
        String name = lastKnownNames.remove(uuid);
        if (name != null) {
            removeFromTeams(name);
        }
    }

    public void clearAll() {
        for (UUID uuid : new java.util.ArrayList<>(activeRoles.keySet())) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                clear(p);
            } else {
                clear(uuid);
            }
        }
        activeRoles.clear();
        originalListNames.clear();
        originalLegacyNames.clear();
        lastKnownNames.clear();
    }

    // ===== Internals =====

    private boolean applyTabRole(Player player, Role role) {
        if (!tabHooked) return false;

        Object tabPlayer = resolveTabPlayer(player);
        if (tabPlayer == null) return false;

        try {
            String prefix = (role == Role.SEEKER) ? "&c" : "&a";
            Object color  = (role == Role.SEEKER) ? tabColorRed : tabColorGreen;
            boolean changed = false;

            if (teamSetNameColorMethod != null && color != null) {
                teamSetNameColorMethod.invoke(teamManagerInstance, tabPlayer, color);
                changed = true;
            } else if (tabPlayerSetTemporaryColorMethod != null && color != null) {
                tabPlayerSetTemporaryColorMethod.invoke(tabPlayer, color);
                changed = true;
            }

            if (teamSetPrefixMethod != null) {
                teamSetPrefixMethod.invoke(teamManagerInstance, tabPlayer, prefix);
                changed = true;
            } else if (tabPlayerSetTemporaryPrefixMethod != null) {
                tabPlayerSetTemporaryPrefixMethod.invoke(tabPlayer, prefix);
                changed = true;
            }

            return changed;
        } catch (Throwable ex) {
            plugin.getLogger().log(Level.FINE, "Failed to apply TAB role color to " + player.getName(), ex);
            return false;
        }
    }

    private boolean clearTabRole(Player player) {
        if (!tabHooked) return false;

        Object tabPlayer = resolveTabPlayer(player);
        if (tabPlayer == null) return false;

        try {
            boolean changed = false;

            if (teamResetNameColorMethod != null) {
                teamResetNameColorMethod.invoke(teamManagerInstance, tabPlayer);
                changed = true;
            } else if (tabPlayerResetColorMethod != null) {
                tabPlayerResetColorMethod.invoke(tabPlayer);
                changed = true;
            } else if (teamSetNameColorMethod != null && tabColorReset != null) {
                teamSetNameColorMethod.invoke(teamManagerInstance, tabPlayer, tabColorReset);
                changed = true;
            }

            if (teamResetPrefixMethod != null) {
                teamResetPrefixMethod.invoke(teamManagerInstance, tabPlayer);
                changed = true;
            } else if (tabPlayerResetPrefixMethod != null) {
                tabPlayerResetPrefixMethod.invoke(tabPlayer);
                changed = true;
            } else if (teamSetPrefixMethod != null) {
                teamSetPrefixMethod.invoke(teamManagerInstance, tabPlayer, "");
                changed = true;
            }

            return changed;
        } catch (Throwable ex) {
            plugin.getLogger().log(Level.FINE, "Failed to clear TAB role color for " + player.getName(), ex);
            return false;
        }
    }

    private Object resolveTabPlayer(Player player) {
        if (tabApiInstance == null) return null;
        try {
            if (getPlayerByPlayerMethod != null) {
                Object tp = getPlayerByPlayerMethod.invoke(tabApiInstance, player);
                if (tp != null) return tp;
            }
            if (getPlayerByUuidMethod != null) {
                return getPlayerByUuidMethod.invoke(tabApiInstance, player.getUniqueId());
            }
        } catch (Throwable ex) {
            plugin.getLogger().log(Level.FINE, "Failed to resolve TAB player for " + player.getName(), ex);
        }
        return null;
    }

    private void updateScoreboardTeams(Player player, Role role) {
        if (mainScoreboard == null || seekerTeam == null || hiderTeam == null) return;

        removeFromTeams(player.getName());
        if (role == Role.SEEKER) {
            safeAddEntry(seekerTeam, player.getName());
        } else {
            safeAddEntry(hiderTeam, player.getName());
        }
    }

    private void removeFromTeams(String name) {
        if (name == null) return;
        safeRemoveEntry(seekerTeam, name);
        safeRemoveEntry(hiderTeam, name);
    }

    private void applyListNameFallback(Player player, Role role, boolean appliedTab) {
        // ถ้า TAB จัดการแล้ว → คืนค่าเดิม/ไม่ทับชื่อ
        if (appliedTab) {
            restoreListName(player, true);
            return;
        }

        UUID uuid = player.getUniqueId();

        if (hasPaperListName) {
            // เก็บของเดิมเป็น Component
            originalListNames.computeIfAbsent(uuid, key -> {
                Component cur = player.playerListName();
                return (cur != null) ? cur : Component.text(player.getName());
            });
            NamedTextColor color = (role == Role.SEEKER) ? NamedTextColor.RED : NamedTextColor.GREEN;
            player.playerListName(Component.text(player.getName(), color));
        } else {
            // Legacy Spigot
            originalLegacyNames.computeIfAbsent(uuid, key -> {
                String cur = player.getPlayerListName();
                return (cur != null) ? cur : player.getName();
            });
            String color = (role == Role.SEEKER) ? ChatColor.RED.toString() : ChatColor.GREEN.toString();
            player.setPlayerListName(color + player.getName());
        }
    }

    private void restoreListName(Player player, boolean fromTab) {
        UUID uuid = player.getUniqueId();

        if (hasPaperListName) {
            Component original = originalListNames.remove(uuid);
            if (original != null) {
                player.playerListName(original);
            } else if (!fromTab) {
                player.playerListName(Component.text(player.getName()));
            }
        } else {
            String orig = originalLegacyNames.remove(uuid);
            if (orig != null) {
                player.setPlayerListName(orig);
            } else if (!fromTab) {
                player.setPlayerListName(player.getName());
            }
        }
    }

    private Team ensureTeam(Scoreboard scoreboard, String name, ChatColor color) {
        if (scoreboard == null) return null;

        Team team = scoreboard.getTeam(name);
        if (team == null) {
            try {
                team = scoreboard.registerNewTeam(name);
            } catch (IllegalArgumentException already) {
                // ถูกสร้างไปแล้วพร้อมกันที่อื่น
                team = scoreboard.getTeam(name);
            }
        }
        if (team == null) return null;

        try {
            team.setColor(color);
        } catch (Throwable ignored) {
            // บางบิลด์เก่าของ Bukkit อาจไม่รองรับ setColor – ข้ามได้
        }

        try {
            team.setPrefix(color.toString());
        } catch (Throwable ignored) {
        }

        try {
            team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        } catch (Throwable ignored) {
        }

        return team;
    }

    private void safeAddEntry(Team team, String name) {
        if (team == null || name == null) return;
        try {
            if (!team.hasEntry(name)) team.addEntry(name);
        } catch (Throwable ignored) {
        }
    }

    private void safeRemoveEntry(Team team, String name) {
        if (team == null || name == null) return;
        try {
            if (team.hasEntry(name)) team.removeEntry(name);
        } catch (Throwable ignored) {
        }
    }

    // === Reflection helpers ===

    private Method findMethod(Class<?> type, String name, Class<?> parameter) {
        try {
            if (parameter == null) return type.getMethod(name);
            return type.getMethod(name, parameter);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private Method findNoArgMethod(Class<?> type, String... candidates) {
        for (String candidate : candidates) {
            try {
                return type.getMethod(candidate);
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    private Method findTeamMethod(Class<?> type, Class<?> tabPlayerType, Class<?> secondParameter,
                                  String actionKeyword, String subjectKeyword) {
        for (Method method : type.getMethods()) {
            if (method.getParameterCount() == 0) continue;

            String lower = method.getName().toLowerCase(Locale.ROOT);
            if (!lower.contains(actionKeyword) || !lower.contains(subjectKeyword)) continue;

            Class<?>[] params = method.getParameterTypes();
            if (tabPlayerType != null) {
                if (!params[0].isAssignableFrom(tabPlayerType) && !tabPlayerType.isAssignableFrom(params[0])) {
                    continue;
                }
            }
            if (secondParameter == null) {
                if (params.length == 1) return method;
                continue;
            }
            if (params.length >= 2 && (params[1].isAssignableFrom(secondParameter) || secondParameter.isAssignableFrom(params[1]))) {
                return method;
            }
        }
        return null;
    }

    private Method findTabPlayerMethod(Class<?> type, Class<?> parameter, String actionKeyword, String subjectKeyword) {
        for (Method method : type.getMethods()) {
            String lower = method.getName().toLowerCase(Locale.ROOT);
            if (!lower.contains(actionKeyword) || !lower.contains(subjectKeyword)) continue;

            if (parameter == null) {
                if (method.getParameterCount() == 0) return method;
                continue;
            }
            if (method.getParameterCount() == 1) {
                Class<?> param = method.getParameterTypes()[0];
                if (param.isAssignableFrom(parameter) || parameter.isAssignableFrom(param)) {
                    return method;
                }
            }
        }
        return null;
    }

    private Class<?> tryLoadClass(String... candidates) {
        for (String name : candidates) {
            try {
                return Class.forName(name);
            } catch (ClassNotFoundException ignored) { }
        }
        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object resolveColorConstant(Class<?> colorClass, String name) {
        if (colorClass == null || !colorClass.isEnum()) return null;
        try {
            return Enum.valueOf((Class<? extends Enum>) colorClass.asSubclass(Enum.class), name);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}

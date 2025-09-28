package org.ZeDoExter.doorHunt.game;

import org.bukkit.Material;

import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Function;

public enum ArenaSetting {
    MIN_PLAYERS("min-players", "Min Players", "Enter the minimum players required to start", Material.PLAYER_HEAD,
            GameArena::getMinPlayers,
            GameArena::setMinPlayers,
            (arena, value) -> value >= 1 && value <= arena.getMaxPlayers()),
    MAX_PLAYERS("max-players", "Max Players", "Enter the maximum players allowed", Material.CHEST,
            GameArena::getMaxPlayers,
            GameArena::setMaxPlayers,
            (arena, value) -> value >= arena.getMinPlayers()),
    RECRUIT_TIME("recruit", "Lobby Countdown", "Enter the recruiting countdown in seconds", Material.CLOCK,
            GameArena::getRecruitingCountdown,
            GameArena::setRecruitingCountdown,
            (arena, value) -> value >= 5),
    PREPARE_TIME("prepare", "Prepare Duration", "Enter the prepare duration in seconds", Material.BEACON,
            GameArena::getPrepareDuration,
            GameArena::setPrepareDuration,
            (arena, value) -> value >= 0),
    HIDE_TIME("hide", "Hiding Time", "Enter the hiding time in seconds", Material.ENDER_PEARL,
            GameArena::getHideDuration,
            GameArena::setHideDuration,
            (arena, value) -> value >= 0),
    LIVE_TIME("live", "Hunt Duration", "Enter the hunt duration in seconds", Material.DIAMOND_SWORD,
            GameArena::getLiveDuration,
            GameArena::setLiveDuration,
            (arena, value) -> value >= 30);

    private final String key;
    private final String displayName;
    private final String prompt;
    private final Material icon;
    private final Function<GameArena, Integer> getter;
    private final BiConsumer<GameArena, Integer> setter;
    private final BiPredicate<GameArena, Integer> validator;

    ArenaSetting(String key, String displayName, String prompt, Material icon,
                 Function<GameArena, Integer> getter,
                 BiConsumer<GameArena, Integer> setter,
                 BiPredicate<GameArena, Integer> validator) {
        this.key = key;
        this.displayName = displayName;
        this.prompt = prompt;
        this.icon = icon;
        this.getter = getter;
        this.setter = setter;
        this.validator = validator;
    }

    public String getKey() {
        return key;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPrompt() {
        return prompt;
    }

    public Material getIcon() {
        return icon;
    }

    public int get(GameArena arena) {
        return getter.apply(arena);
    }

    public void set(GameArena arena, int value) {
        setter.accept(arena, value);
    }

    public boolean isValid(GameArena arena, int value) {
        return validator == null || validator.test(arena, value);
    }
}

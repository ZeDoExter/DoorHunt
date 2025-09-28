package org.ZeDoExter.doorHunt.game;

public enum GameState {
    WAITING,
    COUNTDOWN,
    PREPARING,
    HIDING,
    LIVE,
    ENDING,
    COOLDOWN;

    public boolean isActivePlay() {
        return this == HIDING || this == LIVE;
    }
}

package org.ZeDoExter.doorHunt.game;

import org.bukkit.Location;

public class GameArena {
    private final String id;
    private String displayName;
    private int minPlayers;
    private int maxPlayers;
    private int recruitingCountdown;
    private int prepareDuration;
    private int hideDuration;
    private int liveDuration;
    private Location lobbyLocation;
    private Location hiderSpawn;
    private Location seekerWaitSpawn;

    public GameArena(String id) {
        this.id = id;
        this.displayName = id;
        this.minPlayers = 2;
        this.maxPlayers = 16;
        this.recruitingCountdown = 60;
        this.prepareDuration = 10;
        this.hideDuration = 30;
        this.liveDuration = 3600;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public int getMinPlayers() {
        return minPlayers;
    }

    public void setMinPlayers(int minPlayers) {
        this.minPlayers = minPlayers;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public void setMaxPlayers(int maxPlayers) {
        this.maxPlayers = maxPlayers;
    }

    public int getRecruitingCountdown() {
        return recruitingCountdown;
    }

    public void setRecruitingCountdown(int recruitingCountdown) {
        this.recruitingCountdown = recruitingCountdown;
    }

    public int getHideDuration() {
        return hideDuration;
    }

    public void setHideDuration(int hideDuration) {
        this.hideDuration = hideDuration;
    }

    public int getPrepareDuration() {
        return prepareDuration;
    }

    public void setPrepareDuration(int prepareDuration) {
        this.prepareDuration = Math.max(0, prepareDuration);
    }

    public int getLiveDuration() {
        return liveDuration;
    }

    public void setLiveDuration(int liveDuration) {
        this.liveDuration = liveDuration;
    }

    public Location getLobbyLocation() {
        return lobbyLocation;
    }

    public void setLobbyLocation(Location lobbyLocation) {
        this.lobbyLocation = lobbyLocation;
    }

    public Location getHiderSpawn() {
        return hiderSpawn;
    }

    public void setHiderSpawn(Location hiderSpawn) {
        this.hiderSpawn = hiderSpawn;
    }

    public Location getSeekerWaitSpawn() {
        return seekerWaitSpawn;
    }

    public void setSeekerWaitSpawn(Location seekerWaitSpawn) {
        this.seekerWaitSpawn = seekerWaitSpawn;
    }

    public boolean isConfigured() {
        return lobbyLocation != null && hiderSpawn != null && seekerWaitSpawn != null;
    }
}

package org.ZeDoExter.doorHunt.game;

import org.ZeDoExter.doorHunt.DoorHunt;
import org.ZeDoExter.doorHunt.scoreboard.ScoreboardService;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class GameInstance {

    private final DoorHunt plugin;
    private final GameArena arena;
    private final GameManager gameManager;
    private final ScoreboardService scoreboardService;
    private final Set<UUID> players = new LinkedHashSet<>();
    private final Set<UUID> seekers = new HashSet<>();
    private final Set<UUID> hiders = new HashSet<>();
    private final Map<UUID, Integer> seekerKills = new HashMap<>();
    private final Map<UUID, UUID> lastAttackers = new HashMap<>();
    private final int endCooldownSeconds;
    private GameState state = GameState.WAITING;
    private BukkitTask countdownTask;
    private BukkitTask prepareTask;
    private BukkitTask hideTask;
    private BukkitTask liveTask;
    private BukkitTask scoreboardTask;
    private BukkitTask fireworksTask;
    private BukkitTask cooldownTask;
    private int countdownRemaining;
    private int prepareRemaining;
    private int hideRemaining;
    private int liveRemaining;
    private int cooldownRemaining;
    private boolean shuttingDown;

    public GameInstance(DoorHunt plugin, GameArena arena, GameManager gameManager, ScoreboardService scoreboardService) {
        this.plugin = plugin;
        this.arena = arena;
        this.gameManager = gameManager;
        this.scoreboardService = scoreboardService;
        this.endCooldownSeconds = plugin.getConfig().getInt("settings.end-cooldown", 10);
    }

    public GameArena getArena() {
        return arena;
    }

    public GameState getState() {
        return state;
    }

    public Set<UUID> getPlayers() {
        return Collections.unmodifiableSet(players);
    }

    public Set<UUID> getSeekers() {
        return Collections.unmodifiableSet(seekers);
    }

    public Set<UUID> getHiders() {
        return Collections.unmodifiableSet(hiders);
    }

    public int getCountdownRemaining() {
        return countdownRemaining;
    }

    public int getHideRemaining() {
        return hideRemaining;
    }

    public int getLiveRemaining() {
        return liveRemaining;
    }

    private void changeState(GameState newState) {
        if (state != newState) {
            state = newState;
            gameManager.updateLobbyBoards();
        } else {
            state = newState;
        }
    }

    private Location resolveReturnLobby() {
        Location lobby = plugin.getLobbyLocation();
        if (lobby != null) {
            return lobby;
        }
        return arena.getLobbyLocation();
    }

    private void sendToLobby(Player player, String message) {
        plugin.resetPlayer(player);
        Location lobby = resolveReturnLobby();
        if (lobby != null) {
            player.teleport(lobby.clone());
        } else {
            player.teleport(player.getWorld().getSpawnLocation());
        }
        if (message != null && !message.isBlank()) {
            player.sendMessage(plugin.color(message));
        }
        gameManager.showLobbyBoard(player);
    }

    public void join(Player player) {
        if (!arena.isConfigured()) {
            player.sendMessage(plugin.color("&cแมพนี้ยังตั้งค่าไม่ครบ!"));
            return;
        }
        if (players.contains(player.getUniqueId())) {
            player.sendMessage(plugin.color("&eคุณอยู่ในเกมนี้อยู่แล้ว"));
            return;
        }
        if (state != GameState.WAITING && state != GameState.COUNTDOWN) {
            player.sendMessage(plugin.color("&cเกมกำลังดำเนินอยู่ รอรอบต่อไปนะ"));
            return;
        }
        if (players.size() >= arena.getMaxPlayers()) {
            player.sendMessage(plugin.color("&cเกมเต็มแล้ว"));
            return;
        }

        players.add(player.getUniqueId());
        hiders.add(player.getUniqueId());
        gameManager.removeLobbyBoard(player);
        gameManager.setPlayerGame(player, this);
        gameManager.updateLobbyBoards();

        preparePlayerForLobby(player);
        player.teleport(arena.getLobbyLocation());
        broadcast(plugin.color("&a" + player.getName() + " &eเข้าร่วมเกม &7(" + players.size() + "/" + arena.getMaxPlayers() + ")"));

        if (players.size() >= arena.getMinPlayers() && state == GameState.WAITING) {
            startCountdown();
        }
        ensureScoreboardTask();
        updateScoreboards();
    }

    public void leave(Player player, boolean silent) {
        UUID uuid = player.getUniqueId();
        boolean removed = players.remove(uuid);
        seekers.remove(uuid);
        hiders.remove(uuid);
        seekerKills.remove(uuid);
        lastAttackers.remove(uuid);
        gameManager.setPlayerGame(player, null);
        sendToLobby(player, silent ? null : "&aกลับสู่ Lobby แล้ว!" );
        if (removed && !silent) {
            broadcast(plugin.color("&c" + player.getName() + " &eออกจากเกม"));
        }
        checkCountdownCancel();
        checkWinConditions();
        updateScoreboards();
        gameManager.updateLobbyBoards();
    }

    private void preparePlayerForLobby(Player player) {
        plugin.resetPlayer(player);
        player.setGameMode(GameMode.ADVENTURE);
        player.getInventory().setHeldItemSlot(0);
        player.getInventory().setItem(8, createReturnItem());
    }

    private ItemStack createReturnItem() {
        ItemStack item = new ItemStack(Material.RED_BED);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.color("&cกลับ Lobby"));
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return item;
    }

    private void startCountdown() {
        changeState(GameState.COUNTDOWN);
        countdownRemaining = arena.getRecruitingCountdown();
        broadcast(plugin.color("&eผู้เล่นครบแล้ว! เริ่มใน &c" + countdownRemaining + " &eวินาที"));
        if (countdownTask != null) {
            countdownTask.cancel();
        }
        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (players.size() < arena.getMinPlayers()) {
                broadcast(plugin.color("&cผู้เล่นไม่พอ ยกเลิกการนับถอยหลัง"));
                changeState(GameState.WAITING);
                cancelCountdown();
                updateScoreboards();
                return;
            }

            countdownRemaining--;
            if (countdownRemaining <= 0) {
                cancelCountdown();
                beginGame();
            } else {
                if (countdownRemaining <= 5 || countdownRemaining % 10 == 0) {
                    broadcast(plugin.color("&eเริ่มใน &c" + countdownRemaining + " &eวินาที"));
                    playSound(Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
                }
                updateScoreboards();
            }
        }, 20L, 20L);
    }

    private void cancelCountdown() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
        cancelFireworksTask();
        cancelCooldownTask();
        countdownRemaining = 0;
    }

    private void cancelPrepareTask() {
        if (prepareTask != null) {
            prepareTask.cancel();
            prepareTask = null;
        }
        prepareRemaining = 0;
    }

    private void beginGame() {
        if (players.isEmpty()) {
            resetToLobby();
            return;
        }
        changeState(GameState.PREPARING);
        selectSeekers();
        prepareRemaining = arena.getPrepareDuration();
        hideRemaining = arena.getHideDuration();
        liveRemaining = arena.getLiveDuration();

        if (prepareRemaining > 0) {
            broadcast(plugin.color("&eเลือกผู้เล่นเรียบร้อย! เตรียมเริ่มใน &c" + prepareRemaining + " &eวินาที"));
        } else {
            broadcast(plugin.color("&eเลือกผู้เล่นเรียบร้อย!"));
        }
        playSound(Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

        cancelPrepareTask();
        if (prepareRemaining <= 0) {
            startHidingPhase();
            return;
        }

        prepareTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            prepareRemaining--;
            if (prepareRemaining <= 0) {
                cancelPrepareTask();
                startHidingPhase();
                return;
            }
            if (prepareRemaining <= 5 || prepareRemaining % 10 == 0) {
                broadcast(plugin.color("&eเริ่มใน &c" + prepareRemaining + " &eวินาที"));
                playSound(Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
            }
            updateScoreboards();
        }, 20L, 20L);
        updateScoreboards();
    }

    private void startHidingPhase() {
        cancelPrepareTask();
        changeState(GameState.HIDING);

        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;
            plugin.resetPlayer(player);
            player.setGameMode(GameMode.SURVIVAL);
            if (seekers.contains(uuid)) {
                player.teleport(arena.getSeekerWaitSpawn());
                player.sendMessage(plugin.color("&cคุณเป็นคนหา! รอให้คนแอบแอบก่อน"));
            } else {
                player.teleport(arena.getHiderSpawn());
                player.sendMessage(plugin.color("&aคุณเป็นคนแอบ! มีเวลา " + Math.max(0, hideRemaining) + " วิในการหนี"));
            }
        }

        broadcast(plugin.color("&eเริ่มรอบใหม่! &c" + seekers.size() + " &eคนหา, &a" + hiders.size() + " &eคนแอบ"));
        playSound(Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

        if (hideTask != null) {
            hideTask.cancel();
        }
        if (hideRemaining <= 0) {
            startLivePhase();
            return;
        }
        hideTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            hideRemaining--;
            if (hideRemaining <= 0) {
                hideTask.cancel();
                hideTask = null;
                startLivePhase();
            }
            updateScoreboards();
        }, 20L, 20L);
        updateScoreboards();
    }

    private void selectSeekers() {
        seekers.clear();
        hiders.clear();

        List<UUID> shuffled = new ArrayList<>(players);
        Collections.shuffle(shuffled, ThreadLocalRandom.current());
        int seekerCount = Math.max(1, Math.round(players.size() * 0.2f));
        for (int i = 0; i < shuffled.size(); i++) {
            UUID uuid = shuffled.get(i);
            if (i < seekerCount) {
                seekers.add(uuid);
            } else {
                hiders.add(uuid);
            }
        }
    }

    private void startLivePhase() {
        changeState(GameState.LIVE);
        broadcast(plugin.color("&cคนหาออกล่าแล้ว!"));
        Location release = arena.getHiderSpawn();
        for (UUID uuid : seekers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.teleport(release);
                player.sendMessage(plugin.color("&cออกล่าได้แล้ว!"));
                plugin.getQualityArmoryHook().giveSeekerLoadout(player);
            }
        }
        if (liveTask != null) {
            liveTask.cancel();
        }
        liveTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            liveRemaining--;
            if (liveRemaining <= 0) {
                liveTask.cancel();
                liveTask = null;
                endGame(false);
            }
            updateScoreboards();
        }, 20L, 20L);
        updateScoreboards();
    }

    public void handleKill(Player killer, Player victim) {
        if (state != GameState.LIVE && state != GameState.HIDING) {
            return;
        }
        if (!hiders.contains(victim.getUniqueId())) {
            return;
        }
        hiders.remove(victim.getUniqueId());
        seekers.add(victim.getUniqueId());
        lastAttackers.remove(victim.getUniqueId());
        seekerKills.merge(killer.getUniqueId(), 1, Integer::sum);
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("killer", killer.getName());
        placeholders.put("victim", victim.getName());
        placeholders.put("time", formatTimeRemaining());
        placeholders.put("time_label", getTimeLabel());
        String message = plugin.getLanguageManager().random("kill-messages", placeholders, "&c{killer} eliminated &a{victim}");
        broadcast(message);
        preparePlayerForSeeker(victim);
        victim.teleport(arena.getHiderSpawn());
        checkWinConditions();
        updateScoreboards();
    }

    public void recordAttack(Player attacker, Player victim) {
        if (!isSeeker(attacker) || !isHider(victim)) {
            return;
        }
        lastAttackers.put(victim.getUniqueId(), attacker.getUniqueId());
    }
    public void handleExplosionKill(Player victim) {
        if (state != GameState.LIVE && state != GameState.HIDING) {
            return;
        }
        if (!hiders.contains(victim.getUniqueId())) {
            return;
        }
        UUID last = lastAttackers.remove(victim.getUniqueId());
        if (last != null) {
            Player killer = Bukkit.getPlayer(last);
            if (killer != null && isSeeker(killer)) {
                handleKill(killer, victim);
                return;
            }
        }
        hiders.remove(victim.getUniqueId());
        seekers.add(victim.getUniqueId());
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("victim", victim.getName());
        placeholders.put("time", formatTimeRemaining());
        placeholders.put("time_label", getTimeLabel());
        String message = plugin.getLanguageManager().random("death-messages", placeholders, "&c{victim} ถูกระเบิดกระเด็น! &7({time} left)");
        broadcast(message);
        preparePlayerForSeeker(victim);
        victim.teleport(arena.getHiderSpawn());
        checkWinConditions();
        updateScoreboards();
    }

    private void preparePlayerForSeeker(Player player) {
        plugin.resetPlayer(player);
        player.setGameMode(GameMode.SURVIVAL);
        player.sendMessage(plugin.color("&cตอนนี้คุณเป็นคนหาแล้ว!"));
        plugin.getQualityArmoryHook().giveSeekerLoadout(player);
    }

    public void checkWinConditions() {
        if (state != GameState.LIVE && state != GameState.HIDING && state != GameState.COUNTDOWN && state != GameState.WAITING) {
            return;
        }
        if (state == GameState.WAITING || state == GameState.COUNTDOWN) {
            if (players.size() < arena.getMinPlayers()) {
                changeState(GameState.WAITING);
                cancelCountdown();
            }
            return;
        }
        if (hiders.isEmpty()) {
            endGame(true);
        }
    }

    private void endGame(boolean seekersWin) {
        if (state == GameState.ENDING || state == GameState.COOLDOWN) {
            return;
        }
        cancelCooldownTask();
        cooldownRemaining = endCooldownSeconds;
        changeState(GameState.ENDING);
        cancelPrepareTask();
        if (hideTask != null) {
            hideTask.cancel();
            hideTask = null;
        }
        if (liveTask != null) {
            liveTask.cancel();
            liveTask = null;
        }
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }

        if (seekersWin) {
            List<String> topSeekers = seekerKills.entrySet().stream()
                    .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                    .map(entry -> {
                        Player player = Bukkit.getPlayer(entry.getKey());
                        String name = player != null ? player.getName() : Bukkit.getOfflinePlayer(entry.getKey()).getName();
                        return plugin.color("&c" + name + " &7- &e" + entry.getValue() + " kill");
                    })
                    .collect(Collectors.toList());
            broadcast(plugin.color("&cคนหาชนะ!"));
            if (!topSeekers.isEmpty()) {
                broadcast(plugin.color("&7อันดับคนหา:"));
                topSeekers.forEach(this::broadcast);
            }
        } else {
            List<String> survivors = hiders.stream()
                    .map(uuid -> {
                        Player player = Bukkit.getPlayer(uuid);
                        return player != null ? player.getName() : Bukkit.getOfflinePlayer(uuid).getName();
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            broadcast(plugin.color("&aคนแอบชนะ!"));
            if (!survivors.isEmpty()) {
                broadcast(plugin.color("&7ผู้รอดชีวิต: &a" + String.join(" &7, &a", survivors)));
            }
        }

        launchCelebrationFireworks();
        updateScoreboards();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            changeState(GameState.COOLDOWN);
            updateScoreboards();
            if (players.isEmpty()) {
                resetToLobby();
                return;
            }
            for (UUID uuid : new ArrayList<>(players)) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    plugin.resetPlayer(player);
                    scoreboardService.clear(player);
                    player.sendMessage(plugin.color("&eรอ &c" + cooldownRemaining + " &eวินาทีแล้วจะกลับ Lobby"));
                }
            }
            if (cooldownRemaining <= 0) {
                resetToLobby();
                return;
            }
            cancelCooldownTask();
            cooldownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                cooldownRemaining--;
                if (cooldownRemaining <= 0) {
                    cancelCooldownTask();
                    updateScoreboards();
                    resetToLobby();
                } else {
                    updateScoreboards();
                }
            }, 20L, 20L);
        }, 20L);
    }

    private void launchCelebrationFireworks() {
        cancelFireworksTask();
        fireworksTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (players.isEmpty()) {
                cancelFireworksTask();
                return;
            }
            List<Player> online = players.stream()
                    .map(Bukkit::getPlayer)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            if (online.isEmpty()) {
                cancelFireworksTask();
                return;
            }
            Player target = online.get(ThreadLocalRandom.current().nextInt(online.size()));
            spawnRandomFirework(target);
        }, 0L, 20L);
        Bukkit.getScheduler().runTaskLater(plugin, this::cancelFireworksTask, Math.max(40L, endCooldownSeconds * 20L));
    }

    private void spawnRandomFirework(Player player) {
        Location base = player.getLocation();
        if (base.getWorld() == null) {
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 10; attempt++) {
            double offsetX = random.nextDouble(-20.0, 20.0);
            double offsetZ = random.nextDouble(-20.0, 20.0);
            double targetX = base.getX() + offsetX;
            double targetZ = base.getZ() + offsetZ;
            int blockX = (int) Math.floor(targetX);
            int blockZ = (int) Math.floor(targetZ);
            int highest = base.getWorld().getHighestBlockYAt(blockX, blockZ);
            double desiredY = Math.max(highest + 1.0, base.getY() + 1.0);
            int blockY = (int) Math.floor(desiredY);
            if (blockY >= base.getWorld().getMaxHeight()) {
                continue;
            }
            if (!base.getWorld().getBlockAt(blockX, blockY, blockZ).getType().isAir()) {
                continue;
            }
            Location spawn = new Location(base.getWorld(), blockX + 0.5, blockY + 0.5, blockZ + 0.5);
            spawnFireworkAt(spawn, random);
            return;
        }
    }

    private void spawnFireworkAt(Location location, ThreadLocalRandom random) {
        location.getWorld().spawn(location, org.bukkit.entity.Firework.class, firework -> {
            var meta = firework.getFireworkMeta();
            meta.clearEffects();
            meta.addEffect(buildRandomEffect(random));
            meta.setPower(random.nextInt(1, 3));
            firework.setFireworkMeta(meta);
        });
    }

    private FireworkEffect buildRandomEffect(ThreadLocalRandom random) {
        FireworkEffect.Type[] types = FireworkEffect.Type.values();
        FireworkEffect.Builder builder = FireworkEffect.builder()
                .with(types[random.nextInt(types.length)])
                .withColor(randomColors(random));
        if (random.nextBoolean()) {
            builder.withFade(randomColors(random));
        }
        if (random.nextBoolean()) {
            builder.withFlicker();
        }
        if (random.nextBoolean()) {
            builder.withTrail();
        }
        return builder.build();
    }
    private List<Color> randomColors(ThreadLocalRandom random) {
        int amount = random.nextInt(1, 4);
        List<Color> colors = new ArrayList<>(amount);
        for (int i = 0; i < amount; i++) {
            colors.add(Color.fromRGB(random.nextInt(0x1000000)));
        }
        return colors;
    }
    private void cancelFireworksTask() {
        if (fireworksTask != null) {
            fireworksTask.cancel();
            fireworksTask = null;
        }
    }


    private void resetToLobby() {
        changeState(GameState.WAITING);
        cancelPrepareTask();
        if (scoreboardTask != null) {
            scoreboardTask.cancel();
            scoreboardTask = null;
        }
        cancelFireworksTask();
        cancelCooldownTask();
        for (UUID uuid : new ArrayList<>(players)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                gameManager.setPlayerGame(player, null);
                sendToLobby(player, "&aกลับสู่ Lobby แล้ว!");
            } else {
                gameManager.clearPlayer(uuid);
            }
        }
        players.clear();
        seekers.clear();
        hiders.clear();
        seekerKills.clear();
        lastAttackers.clear();
        countdownRemaining = 0;
        hideRemaining = 0;
        liveRemaining = 0;
        cooldownRemaining = 0;
        gameManager.updateLobbyBoards();
    }

    public void forceEnd() {
        if (players.isEmpty()) {
            return;
        }
        broadcast(plugin.color("&cรอบนี้ถูกผู้ดูแลยุติ"));
        cancelPrepareTask();
        if (hideTask != null) {
            hideTask.cancel();
            hideTask = null;
        }
        if (liveTask != null) {
            liveTask.cancel();
            liveTask = null;
        }
        cancelCountdown();
        cancelFireworksTask();
        cancelCooldownTask();
        changeState(GameState.COOLDOWN);
        Bukkit.getScheduler().runTask(plugin, this::resetToLobby);
    }

    public boolean isPlaying(Player player) {
        return players.contains(player.getUniqueId());
    }

    public boolean isSeeker(Player player) {
        return seekers.contains(player.getUniqueId());
    }

    public boolean isHider(Player player) {
        return hiders.contains(player.getUniqueId());
    }

    public void shutdown() {
        shuttingDown = true;
        if (countdownTask != null) {
            countdownTask.cancel();
        }
        cancelPrepareTask();
        if (hideTask != null) {
            hideTask.cancel();
        }
        if (liveTask != null) {
            liveTask.cancel();
        }
        if (scoreboardTask != null) {
            scoreboardTask.cancel();
        }
        cancelFireworksTask();
        cancelCooldownTask();
        for (UUID uuid : new ArrayList<>(players)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                gameManager.setPlayerGame(player, null);
                sendToLobby(player, null);
            } else {
                gameManager.clearPlayer(uuid);
            }
        }
        players.clear();
        seekers.clear();
        hiders.clear();
        seekerKills.clear();
        lastAttackers.clear();
        gameManager.updateLobbyBoards();
    }

    private void ensureScoreboardTask() {
        if (scoreboardTask == null) {
            scoreboardTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateScoreboards, 0L, 20L);
        }
    }

    public void updateScoreboards() {
        if (players.isEmpty()) {
            if (scoreboardTask != null) {
                scoreboardTask.cancel();
                scoreboardTask = null;
            }
            return;
        }
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("arena", arena.getDisplayName());
        placeholders.put("players", String.valueOf(players.size()));
        placeholders.put("max", String.valueOf(arena.getMaxPlayers()));
        placeholders.put("min", String.valueOf(arena.getMinPlayers()));
        placeholders.put("seekers", String.valueOf(seekers.size()));
        placeholders.put("hiders", String.valueOf(hiders.size()));
        placeholders.put("time", formatTimeRemaining());
        placeholders.put("time_label", getTimeLabel());
        String stateLabel = getStateDisplayName();
        placeholders.put("state", stateLabel);
        placeholders.put("state_name", stateLabel);
        placeholders.put("state_code", state.name());
        scoreboardService.update(this, placeholders);
    }

    private String formatTimeRemaining() {
        return formatTimeRemaining(state);
    }

    private String formatTimeRemaining(GameState state) {
        int seconds = switch (state) {
            case COUNTDOWN -> countdownRemaining;
            case PREPARING -> prepareRemaining;
            case HIDING -> hideRemaining;
            case LIVE -> liveRemaining;
            case ENDING, COOLDOWN -> Math.max(0, cooldownRemaining);
            default -> 0;
        };
        int minutes = seconds / 60;
        int sec = seconds % 60;
        return String.format("%02d:%02d", minutes, sec);
    }

    private String getTimeLabel() {
        return getTimeLabel(state);
    }

    private String getTimeLabel(GameState state) {
        return switch (state) {
            case WAITING -> "Waiting";
            case COUNTDOWN -> "Starting in";
            case PREPARING -> "Seekers in";
            case HIDING -> "Hide time";
            case LIVE -> "Time remaining";
            case ENDING -> "Ended";
            case COOLDOWN -> "Returning in";
        };
    }

    private void checkCountdownCancel() {
        if (state == GameState.COUNTDOWN && players.size() < arena.getMinPlayers()) {
            changeState(GameState.WAITING);
            cancelCountdown();
            broadcast(plugin.color("&cผู้เล่นไม่พอ ยกเลิกการนับถอยหลัง"));
        }
    }

    private void broadcast(String message) {
        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.sendMessage(message);
            }
        }
    }

    private void playSound(Sound sound, float volume, float pitch) {
        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.playSound(player.getLocation(), sound, volume, pitch);
            }
        }
    }

    public boolean isShuttingDown() {
        return shuttingDown;
    }

    private String getStateDisplayName() {
        return switch (state) {
            case WAITING -> "Waiting";
            case COUNTDOWN -> "Countdown";
            case PREPARING -> "Preparing";
            case HIDING -> "Hiding";
            case LIVE -> "Hunting";
            case ENDING -> "Ending";
            case COOLDOWN -> "Cooldown";
        };
    }

    private void cancelCooldownTask() {
        if (cooldownTask != null) {
            cooldownTask.cancel();
            cooldownTask = null;
        }
    }
}

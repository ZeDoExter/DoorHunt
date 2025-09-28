package org.ZeDoExter.doorHunt.command;

import org.ZeDoExter.doorHunt.DoorHunt;
import org.ZeDoExter.doorHunt.game.GameArena;
import org.ZeDoExter.doorHunt.game.GameInstance;
import org.ZeDoExter.doorHunt.game.GameManager;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class DoorHuntCommand implements CommandExecutor, TabCompleter {
    private final DoorHunt plugin;
    private final GameManager gameManager;

    private enum LocationArgument {
        LOBBY("lobby", GameArena::getLobbyLocation, GameArena::setLobbyLocation),
        HIDER("hider", GameArena::getHiderSpawn, GameArena::setHiderSpawn),
        SEEKER_WAIT("seeker-wait", GameArena::getSeekerWaitSpawn, GameArena::setSeekerWaitSpawn);

        private final String key;
        private final java.util.function.Function<GameArena, Location> getter;
        private final java.util.function.BiConsumer<GameArena, Location> setter;
        private final java.util.function.Predicate<GameArena> configured;

        LocationArgument(String key, java.util.function.Function<GameArena, Location> getter, java.util.function.BiConsumer<GameArena, Location> setter) {
            this(key, getter, setter, arena -> getter.apply(arena) != null);
        }

        LocationArgument(String key, java.util.function.Function<GameArena, Location> getter, java.util.function.BiConsumer<GameArena, Location> setter, java.util.function.Predicate<GameArena> configured) {
            this.key = key;
            this.getter = getter;
            this.setter = setter;
            this.configured = configured;
        }

        public String key() {
            return key;
        }

        public Location get(GameArena arena) {
            return getter.apply(arena);
        }

        public void set(GameArena arena, Location location) {
            setter.accept(arena, location);
        }

        public boolean isSet(GameArena arena) {
            return configured.test(arena);
        }

        public static Optional<LocationArgument> from(String input) {
            return Arrays.stream(values())
                    .filter(arg -> arg.key.equalsIgnoreCase(input))
                    .findFirst();
        }

        public static List<String> keys() {
            return Arrays.stream(values())
                    .map(LocationArgument::key)
                    .toList();
        }
    }

    public DoorHuntCommand(DoorHunt plugin, GameManager gameManager) {
        this.plugin = plugin;
        this.gameManager = gameManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "join" -> handleJoin(sender, args);
            case "leave" -> handleLeave(sender);
            case "list" -> handleList(sender);
            case "lobby" -> handleLobby(sender);
            case "create" -> handleCreate(sender, args);
            case "delete" -> handleDelete(sender, args);
            case "setloc" -> handleSetLocation(sender, args);
            case "tp" -> handleTeleport(sender, args);
            case "settings" -> handleSettings(sender, args);
            case "end" -> handleEnd(sender, args);
            case "setlobby" -> handleSetLobby(sender);
            case "reload" -> handleReload(sender);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void handleJoin(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.prefixed("&cOnly players can use this command."));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(plugin.prefixed("&eUsage: /dh join <id>"));
            return;
        }
        GameArena arena = gameManager.getArena(args[1]);
        if (arena == null) {
            sender.sendMessage(plugin.prefixed("&cNo arena with that id exists."));
            return;
        }
        GameInstance instance = gameManager.getInstance(arena);
        if (instance == null) {
            sender.sendMessage(plugin.prefixed("&cThat arena cannot be joined right now."));
            return;
        }
        instance.join(player);
    }

    private void handleLeave(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.prefixed("&cOnly players can use this command."));
            return;
        }
        GameInstance instance = gameManager.getGame(player);
        if (instance == null) {
            sender.sendMessage(plugin.prefixed("&cYou are not currently in a game."));
            return;
        }
        instance.leave(player, false);
        player.sendMessage(plugin.prefixed("&aYou left the game."));
    }

    private void handleList(CommandSender sender) {
        Collection<GameArena> arenas = gameManager.getArenas();
        if (arenas.isEmpty()) {
            sender.sendMessage(plugin.prefixed("&cThere are no arenas yet."));
            return;
        }
        sender.sendMessage(plugin.prefixed("&6Arenas:"));
        for (GameArena arena : arenas) {
            sender.sendMessage(plugin.prefixed("&e- &f" + arena.getId() + " &7(|" + arena.getDisplayName() + "|) &7" + formatLocationState(arena)));
        }
    }

    private String formatLocationState(GameArena arena) {
        List<String> missing = new ArrayList<>();
        for (LocationArgument argument : LocationArgument.values()) {
            if (!argument.isSet(arena)) {
                missing.add(argument.key());
            }
        }
        return missing.isEmpty() ? "&aReady" : "&cMissing: " + String.join(", ", missing);
    }

    private void handleCreate(CommandSender sender, String[] args) {
        if (!sender.hasPermission("doorhunt.admin")) {
            sender.sendMessage(plugin.prefixed("&cYou don't have permission."));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(plugin.prefixed("&eUsage: /dh create <id> [display name]"));
            return;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        if (gameManager.getArena(id) != null) {
            sender.sendMessage(plugin.prefixed("&cAn arena with that id already exists."));
            return;
        }
        String displayName = args.length >= 3 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : id;
        GameArena arena = gameManager.createArena(id, displayName);
        sender.sendMessage(plugin.prefixed("&aCreated arena &e" + arena.getId() + " &a."));
    }

    private void handleDelete(CommandSender sender, String[] args) {
        if (!sender.hasPermission("doorhunt.admin")) {
            sender.sendMessage(plugin.prefixed("&cYou don't have permission."));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(plugin.prefixed("&eUsage: /dh delete <id>"));
            return;
        }
        if (gameManager.deleteArena(args[1])) {
            sender.sendMessage(plugin.prefixed("&aArena deleted."));
        } else {
            sender.sendMessage(plugin.prefixed("&cNo arena with that id exists."));
        }
    }

    private void handleSetLocation(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.prefixed("&cOnly players can use this command."));
            return;
        }
        if (!sender.hasPermission("doorhunt.admin")) {
            sender.sendMessage(plugin.prefixed("&cYou don't have permission."));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(plugin.prefixed("&eUsage: /dh setloc <id> <" + String.join("|", LocationArgument.keys()) + ">"));
            return;
        }
        GameArena arena = gameManager.getArena(args[1]);
        if (arena == null) {
            sender.sendMessage(plugin.prefixed("&cNo arena with that id exists."));
            return;
        }
        Location location = player.getLocation();
        Optional<LocationArgument> argument = LocationArgument.from(args[2]);
        if (argument.isEmpty()) {
            sender.sendMessage(plugin.prefixed("&cValid locations: " + String.join(", ", LocationArgument.keys())));
            return;
        }
        argument.get().set(arena, location);
        gameManager.saveArena(arena);
        sender.sendMessage(plugin.prefixed("&aSet location &e" + argument.get().key() + " &a."));
    }

    private void handleTeleport(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.prefixed("&cOnly players can use this command."));
            return;
        }
        if (!sender.hasPermission("doorhunt.admin")) {
            sender.sendMessage(plugin.prefixed("&cYou don't have permission."));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(plugin.prefixed("&eUsage: /dh tp <id> <" + String.join("|", LocationArgument.keys()) + ">"));
            return;
        }
        GameArena arena = gameManager.getArena(args[1]);
        if (arena == null) {
            sender.sendMessage(plugin.prefixed("&cNo arena with that id exists."));
            return;
        }
        Optional<LocationArgument> argument = LocationArgument.from(args[2]);
        if (argument.isEmpty()) {
            sender.sendMessage(plugin.prefixed("&cAvailable locations: " + String.join(", ", LocationArgument.keys())));
            return;
        }
        if (!argument.get().isSet(arena)) {
            sender.sendMessage(plugin.prefixed("&cThat location has not been set."));
            return;
        }
        Location location = argument.get().get(arena);
        player.teleport(location);
        sender.sendMessage(plugin.prefixed("&aTeleported."));
    }

    private void handleSetLobby(CommandSender sender) {
        if (!sender.hasPermission("doorhunt.admin")) {
            sender.sendMessage(plugin.prefixed("&cYou don't have permission."));
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.prefixed("&cOnly players can use this command."));
            return;
        }
        plugin.setLobbyLocation(player.getLocation());
        player.sendMessage(plugin.prefixed("&aMain lobby location saved!"));
        gameManager.showLobbyBoard(player);
    }

    private void handleLobby(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.prefixed("&cOnly players can use this command."));
            return;
        }
        GameInstance instance = gameManager.getGame(player);
        if (instance != null) {
            instance.leave(player, false);
            return;
        }
        if (plugin.getLobbyLocation() == null) {
            sender.sendMessage(plugin.prefixed("&cThe main lobby hasn't been set yet."));
            return;
        }
        player.teleport(plugin.getLobbyLocation().clone());
        gameManager.showLobbyBoard(player);
        gameManager.updateLobbyBoards();
        player.sendMessage(plugin.prefixed("&aMoved to the lobby."));
    }

    private void handleSettings(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.prefixed("&cOnly players can use this command."));
            return;
        }
        if (!sender.hasPermission("doorhunt.admin")) {
            sender.sendMessage(plugin.prefixed("&cYou don't have permission."));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(plugin.prefixed("&eUsage: /dh settings <id>"));
            return;
        }
        GameArena arena = gameManager.getArena(args[1]);
        if (arena == null) {
            sender.sendMessage(plugin.prefixed("&cNo arena with that id exists."));
            return;
        }
        gameManager.openSettingsMenu(player, arena);
    }

    private void handleEnd(CommandSender sender, String[] args) {
        if (!sender.hasPermission("doorhunt.admin")) {
            sender.sendMessage(plugin.prefixed("&cYou don't have permission."));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(plugin.prefixed("&eUsage: /dh end <id>"));
            return;
        }
        GameInstance instance = gameManager.getLoadedInstance(args[1]);
        if (instance == null || instance.getPlayers().isEmpty()) {
            sender.sendMessage(plugin.prefixed("&cThere is no active game in that arena."));
            return;
        }
        instance.forceEnd();
        sender.sendMessage(plugin.prefixed("&aGame ended."));
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("doorhunt.admin")) {
            sender.sendMessage(plugin.prefixed("&cYou don't have permission."));
            return;
        }
        plugin.reloadConfig();
        plugin.loadLobbyLocation();
        plugin.reloadScoreboard();
        plugin.getLanguageManager().reload();
        plugin.getQualityArmoryHook().reload();
        gameManager.loadArenas();
        sender.sendMessage(plugin.prefixed("&aReloaded configuration."));
    }

    private int parseInt(String input, int def) {
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException ex) {
            return def;
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(plugin.prefixed("&6Door Hunt Commands:"));
        sender.sendMessage(plugin.prefixed("&e/dh join <id> &7- Join a game"));
        sender.sendMessage(plugin.prefixed("&e/dh leave &7- Leave your game"));
        sender.sendMessage(plugin.prefixed("&e/dh lobby &7- Return to the main lobby"));
        if (sender.hasPermission("doorhunt.admin")) {
            sender.sendMessage(plugin.prefixed("&e/dh create <id> [name]"));
            sender.sendMessage(plugin.prefixed("&e/dh delete <id>"));
            sender.sendMessage(plugin.prefixed("&e/dh list"));
            sender.sendMessage(plugin.prefixed("&e/dh setloc <id> <" + String.join("|", LocationArgument.keys()) + ">"));
            sender.sendMessage(plugin.prefixed("&e/dh tp <id> <" + String.join("|", LocationArgument.keys()) + ">"));
            sender.sendMessage(plugin.prefixed("&e/dh settings <id> &7- Configure via GUI"));
            sender.sendMessage(plugin.prefixed("&e/dh end <id> &7- End an active game"));
            sender.sendMessage(plugin.prefixed("&e/dh setlobby &7- Set the main lobby"));
            sender.sendMessage(plugin.prefixed("&e/dh reload"));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> base = new ArrayList<>(List.of("join", "leave", "list", "lobby"));
            if (sender.hasPermission("doorhunt.admin")) {
                base.addAll(Arrays.asList("create", "delete", "setloc", "tp", "settings", "end", "setlobby", "reload"));
            }
            return filter(base, args[0]);
        }
        if (args.length == 2) {
            if (Set.of("join", "delete", "setloc", "tp", "settings", "end").contains(args[0].toLowerCase(Locale.ROOT))) {
                return filter(gameManager.getArenas().stream().map(GameArena::getId).toList(), args[1]);
            }
        }
        if (args.length == 3) {
            if (Set.of("setloc", "tp").contains(args[0].toLowerCase(Locale.ROOT))) {
                return filter(LocationArgument.keys(), args[2]);
            }
        }
        return Collections.emptyList();
    }

    private List<String> filter(List<String> options, String token) {
        return options.stream()
                .filter(opt -> opt.toLowerCase(Locale.ROOT).startsWith(token.toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList());
    }
}

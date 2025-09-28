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
            sender.sendMessage(plugin.color("&cเฉพาะผู้เล่นเท่านั้น"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(plugin.color("&eใช้คำสั่ง: /dh join <id>"));
            return;
        }
        GameArena arena = gameManager.getArena(args[1]);
        if (arena == null) {
            sender.sendMessage(plugin.color("&cไม่พบบแมพนั้น"));
            return;
        }
        GameInstance instance = gameManager.getInstance(arena);
        if (instance == null) {
            sender.sendMessage(plugin.color("&cไม่สามารถเริ่มแมพนี้ได้"));
            return;
        }
        instance.join(player);
    }

    private void handleLeave(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.color("&cเฉพาะผู้เล่นเท่านั้น"));
            return;
        }
        GameInstance instance = gameManager.getGame(player);
        if (instance == null) {
            sender.sendMessage(plugin.color("&cคุณไม่ได้อยู่ในเกม"));
            return;
        }
        instance.leave(player, false);
        player.sendMessage(plugin.color("&aออกจากเกมแล้ว"));
    }

    private void handleList(CommandSender sender) {
        Collection<GameArena> arenas = gameManager.getArenas();
        if (arenas.isEmpty()) {
            sender.sendMessage(plugin.color("&cยังไม่มีแมพ"));
            return;
        }
        sender.sendMessage(plugin.color("&6รายการแมพ:"));
        for (GameArena arena : arenas) {
            sender.sendMessage(plugin.color("&e- &f" + arena.getId() + " &7(|" + arena.getDisplayName() + "|) &7" + formatLocationState(arena)));
        }
    }

    private String formatLocationState(GameArena arena) {
        List<String> missing = new ArrayList<>();
        for (LocationArgument argument : LocationArgument.values()) {
            if (!argument.isSet(arena)) {
                missing.add(argument.key());
            }
        }
        return missing.isEmpty() ? "&aพร้อม" : "&cขาด: " + String.join(", ", missing);
    }

    private void handleCreate(CommandSender sender, String[] args) {
        if (!sender.hasPermission("doorhunt.admin")) {
            sender.sendMessage(plugin.color("&cคุณไม่มีสิทธิ์"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(plugin.color("&eใช้คำสั่ง: /dh create <id> [ชื่อแสดงผล]"));
            return;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        if (gameManager.getArena(id) != null) {
            sender.sendMessage(plugin.color("&cมี id นี้แล้ว"));
            return;
        }
        String displayName = args.length >= 3 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : id;
        GameArena arena = gameManager.createArena(id, displayName);
        sender.sendMessage(plugin.color("&aสร้างแมพ &e" + arena.getId() + " &aเรียบร้อย"));
    }

    private void handleDelete(CommandSender sender, String[] args) {
        if (!sender.hasPermission("doorhunt.admin")) {
            sender.sendMessage(plugin.color("&cคุณไม่มีสิทธิ์"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(plugin.color("&eใช้คำสั่ง: /dh delete <id>"));
            return;
        }
        if (gameManager.deleteArena(args[1])) {
            sender.sendMessage(plugin.color("&aลบแมพเรียบร้อย"));
        } else {
            sender.sendMessage(plugin.color("&cไม่พบแมพ"));
        }
    }

    private void handleSetLocation(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.color("&cเฉพาะผู้เล่นเท่านั้น"));
            return;
        }
        if (!sender.hasPermission("doorhunt.admin")) {
            sender.sendMessage(plugin.color("&cคุณไม่มีสิทธิ์"));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(plugin.color("&eใช้คำสั่ง: /dh setloc <id> <" + String.join("|", LocationArgument.keys()) + ">"));
            return;
        }
        GameArena arena = gameManager.getArena(args[1]);
        if (arena == null) {
            sender.sendMessage(plugin.color("&cไม่พบแมพ"));
            return;
        }
        Location location = player.getLocation();
        Optional<LocationArgument> argument = LocationArgument.from(args[2]);
        if (argument.isEmpty()) {
            sender.sendMessage(plugin.color("&cจุดที่ตั้งได้: " + String.join(", ", LocationArgument.keys())));
            return;
        }
        argument.get().set(arena, location);
        gameManager.saveArena(arena);
        sender.sendMessage(plugin.color("&aตั้งตำแหน่ง &e" + argument.get().key() + " &aแล้ว"));
    }

    private void handleTeleport(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.color("&cเฉพาะผู้เล่นเท่านั้น"));
            return;
        }
        if (!sender.hasPermission("doorhunt.admin")) {
            sender.sendMessage(plugin.color("&cคุณไม่มีสิทธิ์"));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(plugin.color("&eใช้คำสั่ง: /dh tp <id> <" + String.join("|", LocationArgument.keys()) + ">"));
            return;
        }
        GameArena arena = gameManager.getArena(args[1]);
        if (arena == null) {
            sender.sendMessage(plugin.color("&cไม่พบแมพ"));
            return;
        }
        Optional<LocationArgument> argument = LocationArgument.from(args[2]);
        if (argument.isEmpty()) {
            sender.sendMessage(plugin.color("&cจุดที่มี: " + String.join(", ", LocationArgument.keys())));
            return;
        }
        if (!argument.get().isSet(arena)) {
            sender.sendMessage(plugin.color("&cยังไม่ได้ตั้งตำแหน่งนี้"));
            return;
        }
        Location location = argument.get().get(arena);
        player.teleport(location);
        sender.sendMessage(plugin.color("&aเทเลพอร์ตเรียบร้อย"));
    }

    private void handleSetLobby(CommandSender sender) {
        if (!sender.hasPermission("doorhunt.admin")) {
            sender.sendMessage(plugin.color("&cคุณไม่มีสิทธิ์"));
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.color("&cต้องเป็นผู้เล่นเท่านั้น"));
            return;
        }
        plugin.setLobbyLocation(player.getLocation());
        player.sendMessage(plugin.color("&aตั้ง Lobby หลักเรียบร้อย!"));
        gameManager.showLobbyBoard(player);
    }

    private void handleLobby(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.color("&cเฉพาะผู้เล่นเท่านั้น"));
            return;
        }
        GameInstance instance = gameManager.getGame(player);
        if (instance != null) {
            instance.leave(player, false);
            return;
        }
        if (plugin.getLobbyLocation() == null) {
            sender.sendMessage(plugin.color("&cยังไม่ได้ตั้ง Lobby หลัก"));
            return;
        }
        player.teleport(plugin.getLobbyLocation().clone());
        gameManager.showLobbyBoard(player);
        gameManager.updateLobbyBoards();
        player.sendMessage(plugin.color("&aย้ายไป Lobby แล้ว"));
    }

    private void handleSettings(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.color("&cต้องเป็นผู้เล่นเท่านั้น"));
            return;
        }
        if (!sender.hasPermission("doorhunt.admin")) {
            sender.sendMessage(plugin.color("&cคุณไม่มีสิทธิ์"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(plugin.color("&eใช้คำสั่ง: /dh settings <id>"));
            return;
        }
        GameArena arena = gameManager.getArena(args[1]);
        if (arena == null) {
            sender.sendMessage(plugin.color("&cไม่พบแมพ"));
            return;
        }
        gameManager.openSettingsMenu(player, arena);
    }

    private void handleEnd(CommandSender sender, String[] args) {
        if (!sender.hasPermission("doorhunt.admin")) {
            sender.sendMessage(plugin.color("&cคุณไม่มีสิทธิ์"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(plugin.color("&eใช้คำสั่ง: /dh end <id>"));
            return;
        }
        GameInstance instance = gameManager.getLoadedInstance(args[1]);
        if (instance == null || instance.getPlayers().isEmpty()) {
            sender.sendMessage(plugin.color("&cไม่มีเกมที่กำลังเล่นในแมพนี้"));
            return;
        }
        instance.forceEnd();
        sender.sendMessage(plugin.color("&aจบเกมแล้ว"));
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("doorhunt.admin")) {
            sender.sendMessage(plugin.color("&cคุณไม่มีสิทธิ์"));
            return;
        }
        plugin.reloadConfig();
        plugin.loadLobbyLocation();
        plugin.reloadScoreboard();
        plugin.getLanguageManager().reload();
        plugin.getQualityArmoryHook().reload();
        gameManager.loadArenas();
        sender.sendMessage(plugin.color("&aรีโหลดการตั้งค่าเรียบร้อย"));
    }

    private int parseInt(String input, int def) {
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException ex) {
            return def;
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(plugin.color("&6Door Hunt Commands:"));
        sender.sendMessage(plugin.color("&e/dh join <id> &7- เข้าร่วมเกม"));
        sender.sendMessage(plugin.color("&e/dh leave &7- ออกจากเกม"));
        sender.sendMessage(plugin.color("&e/dh lobby &7- กลับ Lobby หลัก"));
        if (sender.hasPermission("doorhunt.admin")) {
            sender.sendMessage(plugin.color("&e/dh create <id> [ชื่อ]"));
            sender.sendMessage(plugin.color("&e/dh delete <id>"));
            sender.sendMessage(plugin.color("&e/dh list"));
            sender.sendMessage(plugin.color("&e/dh setloc <id> <" + String.join("|", LocationArgument.keys()) + ">"));
            sender.sendMessage(plugin.color("&e/dh tp <id> <" + String.join("|", LocationArgument.keys()) + ">"));
            sender.sendMessage(plugin.color("&e/dh settings <id> &7- ตั้งค่าผ่าน GUI"));
            sender.sendMessage(plugin.color("&e/dh end <id> &7- จบเกมที่ค้างอยู่"));
            sender.sendMessage(plugin.color("&e/dh setlobby &7- ตั้ง Lobby หลัก"));
            sender.sendMessage(plugin.color("&e/dh reload"));
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

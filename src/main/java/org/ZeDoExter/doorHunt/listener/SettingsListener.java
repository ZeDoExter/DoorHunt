package org.ZeDoExter.doorHunt.listener;

import org.ZeDoExter.doorHunt.DoorHunt;
import org.ZeDoExter.doorHunt.game.GameArena;
import org.ZeDoExter.doorHunt.game.GameManager;
import org.ZeDoExter.doorHunt.gui.ArenaSettingsMenu;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class SettingsListener implements Listener {
    private final DoorHunt plugin;
    private final GameManager gameManager;

    public SettingsListener(DoorHunt plugin, GameManager gameManager) {
        this.plugin = plugin;
        this.gameManager = gameManager;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof ArenaSettingsMenu menu)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() == null || event.getClickedInventory().getHolder() != menu) {
            return;
        }
        if (event.getCurrentItem() == null) {
            return;
        }
        var setting = menu.getSetting(event.getSlot());
        if (setting == null) {
            return;
        }
        GameArena arena = menu.getArena();
        gameManager.beginPrompt(player, arena, setting);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof ArenaSettingsMenu)) {
            return;
        }
        if (!gameManager.isAwaitingInput(player)) {
            gameManager.closeSettingsMenu(player);
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (!gameManager.isAwaitingInput(player)) {
            return;
        }
        event.setCancelled(true);
        String message = event.getMessage();
        Bukkit.getScheduler().runTask(plugin, () -> gameManager.handleChatInput(player, message));
    }
}

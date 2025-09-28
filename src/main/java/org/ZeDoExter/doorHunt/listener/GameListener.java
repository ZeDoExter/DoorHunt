package org.ZeDoExter.doorHunt.listener;

import org.ZeDoExter.doorHunt.DoorHunt;
import org.ZeDoExter.doorHunt.game.GameInstance;
import org.ZeDoExter.doorHunt.game.GameManager;
import org.ZeDoExter.doorHunt.game.GameState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class GameListener implements Listener {
    private final DoorHunt plugin;
    private final GameManager gameManager;

    public GameListener(DoorHunt plugin, GameManager gameManager) {
        this.plugin = plugin;
        this.gameManager = gameManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (gameManager.getGame(event.getPlayer()) == null) {
            gameManager.showLobbyBoard(event.getPlayer());
            gameManager.updateLobbyBoards();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        gameManager.removePlayer(event.getPlayer());
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        GameInstance instance = gameManager.getGame(event.getPlayer());
        if (instance != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        GameInstance instance = gameManager.getGame(event.getPlayer());
        if (instance != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        GameInstance instance = gameManager.getGame(player);
        if (instance == null) {
            return;
        }
        ItemStack current = event.getCurrentItem();
        if (plugin.isReturnItem(current)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (plugin.isReturnItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        if (!plugin.isReturnItem(player.getInventory().getItemInMainHand())) {
            return;
        }
        GameInstance instance = gameManager.getGame(player);
        if (instance != null) {
            instance.leave(player, false);
            player.sendMessage(plugin.color("&aกลับสู่ Lobby"));
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        GameInstance instance = gameManager.getGame(player);
        if (instance == null) {
            return;
        }
        if (instance.getState() == GameState.WAITING || instance.getState() == GameState.COUNTDOWN || instance.getState() == GameState.ENDING || instance.getState() == GameState.COOLDOWN) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        GameInstance instance = gameManager.getGame(victim);
        if (instance == null) {
            return;
        }
        Player attacker = resolveAttacker(event.getDamager());
        if (attacker == null) {
            event.setCancelled(true);
            return;
        }
        GameInstance attackerGame = gameManager.getGame(attacker);
        if (attackerGame == null || attackerGame != instance) {
            event.setCancelled(true);
            return;
        }
        if (instance.getState() != GameState.LIVE) {
            event.setCancelled(true);
            return;
        }
        if (!instance.isSeeker(attacker) || !instance.isHider(victim)) {
            event.setCancelled(true);
            return;
        }
        if (event.getFinalDamage() >= victim.getHealth()) {
            event.setCancelled(true);
            instance.handleKill(attacker, victim);
        }
    }

    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile) {
            if (projectile.getShooter() instanceof Player player) {
                return player;
            }
        }
        return null;
    }
}

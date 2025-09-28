package org.ZeDoExter.doorHunt.gui;

import org.ZeDoExter.doorHunt.DoorHunt;
import org.ZeDoExter.doorHunt.game.ArenaSetting;
import org.ZeDoExter.doorHunt.game.GameArena;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ArenaSettingsMenu implements InventoryHolder {
    private static final List<Integer> SETTING_SLOTS = List.of(10, 11, 12, 14, 15, 16);

    private final DoorHunt plugin;
    private final GameArena arena;
    private final Map<Integer, ArenaSetting> slotMap = new HashMap<>();
    private final Inventory inventory;

    private ArenaSettingsMenu(DoorHunt plugin, GameArena arena) {
        this.plugin = plugin;
        this.arena = arena;
        this.inventory = Bukkit.createInventory(this, 27, plugin.color("&8Arena Settings » " + arena.getDisplayName()));
        draw();
    }

    public static Inventory create(DoorHunt plugin, GameArena arena) {
        return new ArenaSettingsMenu(plugin, arena).getInventory();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public GameArena getArena() {
        return arena;
    }

    public ArenaSetting getSetting(int slot) {
        return slotMap.get(slot);
    }

    private void draw() {
        fillBackground();
        int index = 0;
        for (ArenaSetting setting : ArenaSetting.values()) {
            if (index >= SETTING_SLOTS.size()) {
                break;
            }
            int slot = SETTING_SLOTS.get(index++);
            inventory.setItem(slot, createSettingItem(setting));
            slotMap.put(slot, setting);
        }
        inventory.setItem(22, createInfoItem());
    }

    private ItemStack createSettingItem(ArenaSetting setting) {
        ItemStack item = new ItemStack(setting.getIcon());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.color("&a" + setting.getDisplayName()));
            meta.setLore(List.of(
                    plugin.color("&7Current: &e" + setting.get(arena)),
                    plugin.color("&7Click to change")
            ));
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createInfoItem() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.color("&bHow to edit"));
            meta.setLore(Arrays.asList(
                    plugin.color("&7Click a setting to enter"),
                    plugin.color("&7a new value in chat."),
                    plugin.color("&7Type &c'cancel' &7to abort.")));
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return item;
    }

    private void fillBackground() {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            meta.addItemFlags(ItemFlag.values());
            filler.setItemMeta(meta);
        }
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler.clone());
        }
    }
}

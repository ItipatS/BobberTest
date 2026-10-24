package gg.fishon.bobber;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;

/**
 * Keeps the menu item locked in the last hotbar slot and opens the menu from it. The item can't be moved, dropped,
 * swapped, stored or lost on death; a once-a-second check puts it back if anything else (e.g. /clear) removes it.
 */
public class MenuListener implements Listener {

    public static final int MENU_SLOT = 8;

    private final Plugin plugin;
    private final SkinRegistry skins;
    private final BobberDisplays displays;

    public MenuListener(Plugin plugin, SkinRegistry skins, BobberDisplays displays) {
        this.plugin = plugin;
        this.skins = skins;
        this.displays = displays;
    }

    public void ensureMenuItem(Player player) {
        if (!skins.menuHotbarItem()) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        ItemStack current = inventory.getItem(MENU_SLOT);
        if (BobberMenu.isMenuItem(current)) {
            return;
        }
        // Remove strays first (e.g. one left on the cursor), then put the item back and re-home whatever was there.
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (BobberMenu.isMenuItem(inventory.getItem(slot))) {
                inventory.setItem(slot, null);
            }
        }
        inventory.setItem(MENU_SLOT, BobberMenu.menuItem(skins, player));
        if (current != null && !current.isEmpty()) {
            inventory.addItem(current).values()
                    .forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
        }
    }

    private void openMenu(Player player) {
        // A tick later: opening an inventory from inside a click event on another inventory is unreliable.
        player.getScheduler().run(plugin, task -> new BobberMenu(skins, displays, player).open(), null);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        ensureMenuItem(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        player.getScheduler().run(plugin, task -> ensureMenuItem(player), null);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(BobberMenu::isMenuItem);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !BobberMenu.isMenuItem(event.getItem())) {
            return;
        }
        if (event.getAction() != Action.PHYSICAL) {
            event.setCancelled(true);
            openMenu(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getView().getTopInventory().getHolder(false) instanceof BobberMenu menu) {
            event.setCancelled(true);
            if (event.getRawSlot() >= 0 && event.getRawSlot() < menu.getInventory().getSize()) {
                menu.click(event.getRawSlot());
            }
            return;
        }
        boolean hotbarSwap = event.getClick() == ClickType.NUMBER_KEY
                && BobberMenu.isMenuItem(player.getInventory().getItem(event.getHotbarButton()));
        if (BobberMenu.isMenuItem(event.getCurrentItem()) || BobberMenu.isMenuItem(event.getCursor()) || hotbarSwap) {
            event.setCancelled(true);
            if (BobberMenu.isMenuItem(event.getCurrentItem())) {
                player.closeInventory();
                openMenu(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof BobberMenu
                || BobberMenu.isMenuItem(event.getOldCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrop(PlayerDropItemEvent event) {
        if (BobberMenu.isMenuItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (BobberMenu.isMenuItem(event.getMainHandItem()) || BobberMenu.isMenuItem(event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }
}

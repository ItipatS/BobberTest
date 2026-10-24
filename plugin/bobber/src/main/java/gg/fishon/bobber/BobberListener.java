package gg.fishon.bobber;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import java.util.List;
import org.bukkit.Color;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public class BobberListener implements Listener {

    private final Plugin plugin;
    private final SkinRegistry skins;
    private final BobberDisplays displays;

    public BobberListener(Plugin plugin, SkinRegistry skins, BobberDisplays displays) {
        this.plugin = plugin;
        this.skins = skins;
        this.displays = displays;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        FishHook hook = event.getHook();
        switch (event.getState()) {
            case FISHING -> mountNextTick(event.getPlayer(), hook, event);
            case LURED -> displays.onLured(hook);
            case BITE -> displays.onBite(hook);
            case CAUGHT_FISH -> displays.onCatch(hook);
            case CAUGHT_ENTITY, IN_GROUND, REEL_IN, FAILED_ATTEMPT -> displays.remove(hook.getUniqueId());
        }
    }

    // The hook is not in the world yet when FISHING fires. Waiting one tick on the hook's own scheduler
    // is simpler than EntityAddToWorldEvent: no pending-skin map to keep, and spawning another entity from
    // inside the add-to-world callback is avoided. If the hook dies first the task is retired and never runs.
    private void mountNextTick(Player player, FishHook hook, PlayerFishEvent event) {
        ItemStack rod = event.getHand() == null ? null : player.getInventory().getItem(event.getHand());
        SkinProfile skin = skins.skinFor(rod, player);
        List<Color> colors = skins.colorsFor(rod, skin, player);
        hook.getScheduler().run(plugin, task -> {
            if (player.isOnline()) {
                displays.mount(hook, player, skin, colors);
            }
        }, null);
    }

    @EventHandler
    public void onEntityRemoved(EntityRemoveFromWorldEvent event) {
        displays.onEntityRemoved(event.getEntity());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // The server pack is sent during the configuration phase, before this player object exists,
        // so the status event may already have happened. Read the status the server recorded instead.
        displays.setPackLoaded(player,
                player.getResourcePackStatus() == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED);
    }

    @EventHandler
    public void onPackStatus(PlayerResourcePackStatusEvent event) {
        switch (event.getStatus()) {
            case SUCCESSFULLY_LOADED -> displays.setPackLoaded(event.getPlayer(), true);
            case ACCEPTED, DOWNLOADED -> { }
            default -> displays.setPackLoaded(event.getPlayer(), false);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        displays.removeAllOwnedBy(event.getPlayer().getUniqueId());
        displays.forget(event.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        displays.removeAllOwnedBy(event.getPlayer().getUniqueId());
    }
}

package gg.fishon.bobber;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public class BobberPlugin extends JavaPlugin {

    private SkinRegistry skins;
    private BobberDisplays displays;
    private BukkitTask sweepTask;
    private MenuListener menuListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        skins = new SkinRegistry(getLogger());
        skins.load(getConfig());
        displays = new BobberDisplays(this, skins);

        getServer().getPluginManager().registerEvents(new BobberListener(this, skins, displays), this);
        menuListener = new MenuListener(this, skins, displays);
        getServer().getPluginManager().registerEvents(menuListener, this);
        getServer().getScheduler().runTaskTimer(this,
                () -> getServer().getOnlinePlayers().forEach(menuListener::ensureMenuItem), 20, 20);
        BobberCommand command = new BobberCommand(this, skins, displays);
        getCommand("bobber").setExecutor(command);
        getCommand("bobber").setTabCompleter(command);
        scheduleSweep();
    }

    @Override
    public void onDisable() {
        if (displays != null) {
            displays.removeAll();
        }
    }

    public void reload() {
        reloadConfig();
        skins.load(getConfig());
        scheduleSweep();
        if (skins.menuHotbarItem()) {
            getServer().getOnlinePlayers().forEach(player ->
                    player.getInventory().setItem(MenuListener.MENU_SLOT, BobberMenu.menuItem(skins, player)));
        }
    }

    public void openMenu(Player player) {
        new BobberMenu(skins, displays, player).open();
    }

    private void scheduleSweep() {
        if (sweepTask != null) {
            sweepTask.cancel();
        }
        long period = skins.sweepIntervalSeconds() * 20L;
        sweepTask = getServer().getScheduler().runTaskTimer(this, displays::sweep, period, period);
    }
}

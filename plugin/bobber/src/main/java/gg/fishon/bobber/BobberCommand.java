package gg.fishon.bobber;

import io.papermc.paper.datacomponent.DataComponentTypes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public class BobberCommand implements TabExecutor {

    private static final String ADMIN = "fishon.bobber.admin";

    private final BobberPlugin plugin;
    private final SkinRegistry skins;
    private final BobberDisplays displays;

    public BobberCommand(BobberPlugin plugin, SkinRegistry skins, BobberDisplays displays) {
        this.plugin = plugin;
        this.skins = skins;
        this.displays = displays;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            return false;
        }
        // /bobber menu is for everyone; everything else is a tuning/admin tool.
        if (!args[0].equals("menu") && !sender.hasPermission(ADMIN)) {
            sender.sendMessage("Use /bobber menu to customise your bobber.");
            return true;
        }
        switch (args[0]) {
            case "stats" -> stats(sender);
            case "reload" -> {
                plugin.reload();
                sender.sendMessage("Bobber config reloaded.");
            }
            case "give" -> give(sender, args);
            case "color" -> color(sender, args);
            case "menu" -> {
                if (sender instanceof Player player) {
                    plugin.openMenu(player);
                }
            }
            case "anim" -> anim(sender, args);
            default -> {
                return false;
            }
        }
        return true;
    }

    private void stats(CommandSender sender) {
        // Logged as well as sent so console/RCON runs leave a record in the server log.
        String line = "[bobber-stats] hooks=" + displays.hooksInWorlds()
                + " tracked=" + displays.trackedCount()
                + " displays=" + displays.displaysInWorlds()
                + " sweepRemovals=" + displays.sweepRemovals();
        plugin.getLogger().info(line);
        sender.sendMessage(line);
    }

    // /bobber give <skin> [#rrggbb ...]: the rod gets the skin's rod item_model if one is mapped, otherwise the
    // fishon:skin PDC tag. Colours, if given, go into fishon:colors and override the skin's own colours.
    private void give(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player) || args.length < 2) {
            sender.sendMessage("Usage (in game): /bobber give <skin> [#rrggbb ...]");
            return;
        }
        String skin = args[1];
        if (skins.skin(skin) == null) {
            sender.sendMessage("Unknown skin: " + skin);
            return;
        }
        List<String> colorArgs = Arrays.asList(args).subList(2, args.length);
        if (!colorArgs.isEmpty() && SkinRegistry.parseColors(colorArgs).isEmpty()) {
            sender.sendMessage("Colours must look like #ff8800");
            return;
        }
        ItemStack rod = ItemStack.of(Material.FISHING_ROD);
        Key rodModel = skins.rodModelFor(skin);
        if (rodModel != null) {
            rod.setData(DataComponentTypes.ITEM_MODEL, rodModel);
        }
        rod.editPersistentDataContainer(pdc -> {
            if (rodModel == null) {
                pdc.set(SkinRegistry.SKIN_PDC_KEY, PersistentDataType.STRING, skin);
            }
            if (!colorArgs.isEmpty()) {
                pdc.set(SkinRegistry.COLORS_PDC_KEY, PersistentDataType.STRING, String.join(",", colorArgs));
            }
        });
        String name = "Test rod: " + skin + (colorArgs.isEmpty() ? "" : " " + String.join(" ", colorArgs));
        rod.setData(DataComponentTypes.CUSTOM_NAME, Component.text(name));
        player.getInventory().addItem(rod);
    }

    // /bobber color <#rrggbb ...>: recolours your bobber in the water right now, for trying palettes.
    private void color(CommandSender sender, String[] args) {
        List<Color> colors = SkinRegistry.parseColors(Arrays.asList(args).subList(1, args.length));
        if (!(sender instanceof Player player) || colors.isEmpty()) {
            sender.sendMessage("Usage (in game): /bobber color <#rrggbb> [#rrggbb ...]");
        } else if (!displays.recolor(player, colors)) {
            sender.sendMessage("Cast a rod first.");
        }
    }

    private void anim(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player) || args.length < 2) {
            sender.sendMessage("Usage (in game): /bobber anim <preset> [idle|lured|bite]");
            return;
        }
        String state = args.length > 2 ? args[2] : "bite";
        if (skins.animation(args[1], state).isEmpty()) {
            sender.sendMessage("No keyframes for " + args[1] + "." + state);
        } else if (!displays.replay(player, args[1], state)) {
            sender.sendMessage("Cast a rod first.");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.add("menu");
            if (sender.hasPermission(ADMIN)) {
                options.addAll(List.of("stats", "reload", "give", "color", "anim"));
            }
        } else if (args.length == 2 && args[0].equals("give")) {
            skins.skinNames().forEach(options::add);
        } else if (args.length == 2 && args[0].equals("anim")) {
            skins.animationNames().forEach(options::add);
        } else if (args.length >= 3 && args[0].equals("give") || args.length >= 2 && args[0].equals("color")) {
            options.addAll(List.of("#ff4040", "#ffd23f", "#3fa7ff", "#7cff6b", "#c77dff", "#ffffff"));
        } else if (args.length == 3 && args[0].equals("anim")) {
            options.addAll(List.of("idle", "lured", "bite"));
        }
        String prefix = args[args.length - 1];
        options.removeIf(option -> !option.startsWith(prefix));
        return options;
    }
}

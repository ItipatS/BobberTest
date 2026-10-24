package gg.fishon.bobber;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.DyedItemColor;
import io.papermc.paper.datacomponent.item.ItemLore;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Chest menu for picking a bobber skin and its colours.
 *
 * Row 1: skins. Row 2: preview of your bobber. Rows 3-4: primary colour (indicator, 16 colours, reset).
 * Rows 5-6: secondary colour, same layout. Colour rows are greyed out when the skin has fewer tints.
 */
public class BobberMenu implements InventoryHolder {

    public static final NamespacedKey MENU_ITEM_KEY = new NamespacedKey("fishon", "menu_item");
    private static final int PREVIEW_SLOT = 13;
    private static final int COLOR_ROWS_START = 18;
    private static final int SLOTS_PER_TINT = 18;
    private static final String[] TINT_NAMES = {"Primary", "Secondary"};

    private final SkinRegistry skins;
    private final BobberDisplays displays;
    private final Player player;
    private final Inventory inventory;

    public BobberMenu(SkinRegistry skins, BobberDisplays displays, Player player) {
        this.skins = skins;
        this.displays = displays;
        this.player = player;
        this.inventory = Bukkit.createInventory(this, 54, Component.text("Bobber Customisation"));
        render();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void open() {
        player.openInventory(inventory);
    }

    public void click(int slot) {
        List<SkinProfile> all = skins.skinsInOrder();
        if (slot < 9) {
            if (slot < all.size()) {
                Cosmetics.setSkin(player, all.get(slot).name());
                changed();
            }
            return;
        }
        if (slot < COLOR_ROWS_START) {
            return;
        }
        int tint = (slot - COLOR_ROWS_START) / SLOTS_PER_TINT;
        int index = (slot - COLOR_ROWS_START) % SLOTS_PER_TINT;
        SkinProfile skin = selectedSkin();
        if (tint >= skin.colors().size()) {
            return;
        }
        List<PaletteColor> palette = palette();
        if (index >= 1 && index <= palette.size()) {
            Cosmetics.setColor(player, tint, palette.get(index - 1).color(), skin.colors());
            changed();
        } else if (index == SLOTS_PER_TINT - 1) {
            Cosmetics.setColor(player, tint, skin.colors().get(tint), skin.colors());
            changed();
        }
    }

    private void changed() {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.4f);
        ItemStack rod = heldRod();
        SkinProfile skin = skins.skinFor(rod, player);
        displays.update(player, skin, skins.colorsFor(rod, skin, player));
        if (skins.menuHotbarItem()) {
            player.getInventory().setItem(MenuListener.MENU_SLOT, menuItem(skins, player));
        }
        render();
    }

    private void render() {
        inventory.clear();
        SkinProfile selected = selectedSkin();
        List<SkinProfile> all = skins.skinsInOrder();
        for (int i = 0; i < Math.min(9, all.size()); i++) {
            SkinProfile skin = all.get(i);
            boolean isSelected = skin.name().equals(selected.name());
            ItemStack icon = named(BobberDisplays.bobberItem(skin.model(), skin.colors()),
                    Component.text(displayName(skin), isSelected ? NamedTextColor.GREEN : NamedTextColor.WHITE),
                    isSelected ? "Selected" : "Click to use",
                    skin.colors().isEmpty() ? "Fixed colours" : skin.colors().size() + " recolourable part(s)");
            if (isSelected) {
                icon.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
            }
            inventory.setItem(i, icon);
        }

        ItemStack filler = named(ItemStack.of(Material.GRAY_STAINED_GLASS_PANE), Component.text(" "));
        for (int slot = 9; slot < COLOR_ROWS_START; slot++) {
            inventory.setItem(slot, filler);
        }
        List<Color> colors = skins.colorsFor(null, selected, player);
        List<String> preview = new ArrayList<>(List.of("Skin: " + displayName(selected)));
        for (int t = 0; t < colors.size() && t < TINT_NAMES.length; t++) {
            preview.add(TINT_NAMES[t] + ": " + Cosmetics.hex(colors.get(t)));
        }
        inventory.setItem(PREVIEW_SLOT, named(BobberDisplays.bobberItem(selected.model(), colors),
                Component.text("Your bobber", NamedTextColor.AQUA), preview.toArray(String[]::new)));

        for (int tint = 0; tint < TINT_NAMES.length; tint++) {
            int base = COLOR_ROWS_START + tint * SLOTS_PER_TINT;
            if (tint < colors.size()) {
                renderColorRows(base, tint, colors.get(tint), selected.colors().get(tint));
            } else {
                ItemStack none = named(ItemStack.of(Material.BLACK_STAINED_GLASS_PANE),
                        Component.text(displayName(selected) + " has no " + TINT_NAMES[tint].toLowerCase(Locale.ROOT)
                                + " colour", NamedTextColor.DARK_GRAY));
                for (int i = 0; i < SLOTS_PER_TINT; i++) {
                    inventory.setItem(base + i, none);
                }
            }
        }
    }

    private void renderColorRows(int base, int tint, Color current, Color skinDefault) {
        ItemStack indicator = ItemStack.of(Material.LEATHER_HORSE_ARMOR);
        indicator.setData(DataComponentTypes.DYED_COLOR, DyedItemColor.dyedItemColor(current));
        indicator.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay()
                .addHiddenComponents(DataComponentTypes.DYED_COLOR).build());
        inventory.setItem(base, named(indicator,
                Component.text(TINT_NAMES[tint] + " colour", TextColor.color(current.asRGB())),
                "Now: " + Cosmetics.hex(current), "Pick a colour on the right"));

        List<PaletteColor> palette = palette();
        for (int i = 0; i < palette.size(); i++) {
            PaletteColor entry = palette.get(i);
            boolean chosen = entry.color().asRGB() == current.asRGB();
            ItemStack button = named(ItemStack.of(entry.icon()),
                    Component.text(entry.name(), TextColor.color(entry.color().asRGB())),
                    chosen ? "Selected" : "Click to set " + TINT_NAMES[tint].toLowerCase(Locale.ROOT) + " colour");
            if (chosen) {
                button.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
            }
            inventory.setItem(base + 1 + i, button);
        }
        inventory.setItem(base + SLOTS_PER_TINT - 1, named(ItemStack.of(Material.BRUSH),
                Component.text("Reset " + TINT_NAMES[tint].toLowerCase(Locale.ROOT), NamedTextColor.YELLOW),
                "Back to the skin's own " + Cosmetics.hex(skinDefault)));
    }

    /** The always-there hotbar item: shows your current bobber, opens this menu. */
    public static ItemStack menuItem(SkinRegistry skins, Player player) {
        SkinProfile skin = skins.skinFor(null, player);
        ItemStack item = named(BobberDisplays.bobberItem(skin.model(), skins.colorsFor(null, skin, player)),
                Component.text("Bobber Customisation", NamedTextColor.AQUA), "Click to change your bobber");
        item.setData(DataComponentTypes.MAX_STACK_SIZE, 1);
        item.editPersistentDataContainer(pdc -> pdc.set(MENU_ITEM_KEY, PersistentDataType.BOOLEAN, true));
        return item;
    }

    public static boolean isMenuItem(ItemStack item) {
        return item != null && !item.isEmpty() && item.getPersistentDataContainer().has(MENU_ITEM_KEY);
    }

    private SkinProfile selectedSkin() {
        return skins.skinFor(null, player);
    }

    // Only 16 colour buttons fit between the indicator and the reset button.
    private List<PaletteColor> palette() {
        List<PaletteColor> palette = skins.palette();
        return palette.size() > 16 ? palette.subList(0, 16) : palette;
    }

    private ItemStack heldRod() {
        for (EquipmentSlot hand : List.of(EquipmentSlot.HAND, EquipmentSlot.OFF_HAND)) {
            ItemStack item = player.getInventory().getItem(hand);
            if (item.getType() == Material.FISHING_ROD) {
                return item;
            }
        }
        return null;
    }

    private static String displayName(SkinProfile skin) {
        return Arrays.stream(skin.name().split("_"))
                .map(word -> word.isEmpty() ? word : Character.toUpperCase(word.charAt(0)) + word.substring(1))
                .reduce((a, b) -> a + " " + b).orElse(skin.name());
    }

    private static ItemStack named(ItemStack item, Component name, String... lore) {
        item.setData(DataComponentTypes.CUSTOM_NAME, name.decoration(TextDecoration.ITALIC, false));
        if (lore.length > 0) {
            item.setData(DataComponentTypes.LORE, ItemLore.lore(Arrays.stream(lore)
                    .map(line -> Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))
                    .toList()));
        }
        return item;
    }
}

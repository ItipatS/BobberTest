package gg.fishon.bobber;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Color;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

/** A player's own bobber choice, stored on the player so it survives relogs and restarts. */
public final class Cosmetics {

    private static final NamespacedKey SKIN_KEY = new NamespacedKey("fishon", "selected_skin");
    private static final NamespacedKey COLORS_KEY = new NamespacedKey("fishon", "selected_colors");

    private Cosmetics() {
    }

    public static String skin(Player player) {
        return player.getPersistentDataContainer().get(SKIN_KEY, PersistentDataType.STRING);
    }

    public static void setSkin(Player player, String skin) {
        player.getPersistentDataContainer().set(SKIN_KEY, PersistentDataType.STRING, skin);
    }

    /** Chosen colour per tint index; may be shorter than a skin's tint count. */
    public static List<Color> colors(Player player) {
        String stored = player.getPersistentDataContainer().get(COLORS_KEY, PersistentDataType.STRING);
        return stored == null || stored.isEmpty() ? List.of() : SkinRegistry.parseColors(List.of(stored.split(",")));
    }

    public static void setColor(Player player, int tint, Color color, List<Color> fallback) {
        List<Color> colors = new ArrayList<>(colors(player));
        while (colors.size() <= tint) {
            colors.add(colors.size() < fallback.size() ? fallback.get(colors.size()) : Color.WHITE);
        }
        colors.set(tint, color);
        List<String> hex = colors.stream().map(Cosmetics::hex).toList();
        player.getPersistentDataContainer().set(COLORS_KEY, PersistentDataType.STRING, String.join(",", hex));
    }

    public static String hex(Color color) {
        return String.format(Locale.ROOT, "#%06x", color.asRGB());
    }
}

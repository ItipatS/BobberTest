package gg.fishon.bobber;

import java.util.List;
import java.util.Map;
import net.kyori.adventure.key.Key;
import org.bukkit.Color;
import org.bukkit.entity.Display;

public record SkinProfile(
        String name,
        Key model,
        Key biteModel,
        int biteModelTicks,
        List<Color> colors,
        List<Color> biteColors,
        boolean glow,
        Display.Billboard billboard,
        float scale,
        float yOffset,
        Map<String, String> sounds,
        String particlePreset,
        String animationPreset) {

    public String sound(String state) {
        return sounds.get(state);
    }
}

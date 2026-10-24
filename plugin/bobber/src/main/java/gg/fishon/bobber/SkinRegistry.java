package gg.fishon.bobber;

import io.papermc.paper.datacomponent.DataComponentTypes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import net.kyori.adventure.key.Key;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public class SkinRegistry {

    public static final String DEFAULT_SKIN = "default";
    public static final NamespacedKey SKIN_PDC_KEY = new NamespacedKey("fishon", "skin");
    // Per-rod colours, e.g. "#ffcc00,#ffffff". Overrides the skin's colours; one entry per tint index.
    public static final NamespacedKey COLORS_PDC_KEY = new NamespacedKey("fishon", "colors");

    private final Logger logger;
    private final Map<String, String> rodToSkin = new HashMap<>();
    private final Map<String, SkinProfile> skins = new LinkedHashMap<>();
    private final List<PaletteColor> palette = new ArrayList<>();
    private final Map<String, Map<String, ParticleSpec>> particlePresets = new HashMap<>();
    private final Map<String, Map<String, List<Keyframe>>> animationPresets = new HashMap<>();
    private int sweepIntervalSeconds;
    private boolean menuHotbarItem;

    public SkinRegistry(Logger logger) {
        this.logger = logger;
    }

    public void load(FileConfiguration config) {
        rodToSkin.clear();
        skins.clear();
        palette.clear();
        particlePresets.clear();
        animationPresets.clear();
        sweepIntervalSeconds = config.getInt("sweep-interval-seconds", 30);
        menuHotbarItem = config.getBoolean("menu.hotbar-item", true);

        ConfigurationSection rods = config.getConfigurationSection("rods");
        if (rods != null) {
            for (String rod : rods.getKeys(false)) {
                rodToSkin.put(rod, rods.getString(rod));
            }
        }
        loadSkins(config.getConfigurationSection("skins"));
        loadParticles(config.getConfigurationSection("particles"));
        loadAnimations(config.getConfigurationSection("animations"));
        loadPalette(config.getMapList("menu.palette"));

        if (!skins.containsKey(DEFAULT_SKIN)) {
            throw new IllegalStateException("config.yml must define skins.default");
        }
        logger.info("Loaded " + skins.size() + " skins, " + rodToSkin.size() + " rod mappings");
    }

    /**
     * The bobber is a cosmetic of its own, independent of the rod: the player's equipped bobber wins, then a skin
     * the rod itself carries (test and bot rods), then default.
     */
    public SkinProfile skinFor(ItemStack rod, Player player) {
        String chosen = player == null ? null : Cosmetics.skin(player);
        if (chosen != null && skins.containsKey(chosen)) {
            return skins.get(chosen);
        }
        SkinProfile fromRod = rodSkin(rod);
        return fromRod != null ? fromRod : skin(DEFAULT_SKIN);
    }

    public SkinProfile skinFor(ItemStack rod) {
        return skinFor(rod, null);
    }

    private SkinProfile rodSkin(ItemStack rod) {
        if (rod == null) {
            return null;
        }
        Key itemModel = rod.getData(DataComponentTypes.ITEM_MODEL);
        if (itemModel != null) {
            String mapped = rodToSkin.get(itemModel.asString());
            if (mapped != null && skins.containsKey(mapped)) {
                return skins.get(mapped);
            }
        }
        String pdcSkin = rod.getPersistentDataContainer().get(SKIN_PDC_KEY, PersistentDataType.STRING);
        return pdcSkin != null && skins.containsKey(pdcSkin) ? skins.get(pdcSkin) : null;
    }

    /** The player's chosen colours, else the rod's own, laid over the skin's defaults (one per tint). */
    public List<Color> colorsFor(ItemStack rod, SkinProfile skin, Player player) {
        List<Color> chosen = player == null ? List.of() : Cosmetics.colors(player);
        if (chosen.isEmpty() && rod != null) {
            String fromRod = rod.getPersistentDataContainer().get(COLORS_PDC_KEY, PersistentDataType.STRING);
            chosen = fromRod == null ? List.of() : parseColors(List.of(fromRod.split(",")));
        }
        List<Color> resolved = new ArrayList<>(skin.colors());
        for (int i = 0; i < Math.min(resolved.size(), chosen.size()); i++) {
            resolved.set(i, chosen.get(i));
        }
        return resolved;
    }

    public List<Color> colorsFor(ItemStack rod, SkinProfile skin) {
        return colorsFor(rod, skin, null);
    }

    public List<SkinProfile> skinsInOrder() {
        return List.copyOf(skins.values());
    }

    public List<PaletteColor> palette() {
        return palette;
    }

    /** Parses "#rrggbb" / "rrggbb" entries; returns an empty list if any entry is invalid. */
    public static List<Color> parseColors(List<String> values) {
        List<Color> colors = new ArrayList<>();
        for (String value : values) {
            String hex = value.trim().replace("#", "");
            if (!hex.matches("[0-9a-fA-F]{6}")) {
                return List.of();
            }
            colors.add(Color.fromRGB(Integer.parseInt(hex, 16)));
        }
        return colors;
    }

    public SkinProfile skin(String name) {
        return skins.get(name);
    }

    public Iterable<String> skinNames() {
        return skins.keySet();
    }

    public Iterable<String> animationNames() {
        return animationPresets.keySet();
    }

    public Key rodModelFor(String skin) {
        for (Map.Entry<String, String> entry : rodToSkin.entrySet()) {
            if (entry.getValue().equals(skin)) {
                return Key.key(entry.getKey());
            }
        }
        return null;
    }

    public ParticleSpec particles(SkinProfile skin, String state) {
        Map<String, ParticleSpec> preset = particlePresets.get(skin.particlePreset());
        return preset == null ? null : preset.get(state);
    }

    public List<Keyframe> animation(String preset, String state) {
        Map<String, List<Keyframe>> byState = animationPresets.get(preset);
        return byState == null ? List.of() : byState.getOrDefault(state, List.of());
    }

    public boolean menuHotbarItem() {
        return menuHotbarItem;
    }

    public int sweepIntervalSeconds() {
        return sweepIntervalSeconds;
    }

    private void loadSkins(ConfigurationSection section) {
        if (section == null) {
            return;
        }
        for (String name : section.getKeys(false)) {
            ConfigurationSection skin = section.getConfigurationSection(name);
            Map<String, String> sounds = new HashMap<>();
            ConfigurationSection soundSection = skin.getConfigurationSection("sounds");
            if (soundSection != null) {
                for (String state : soundSection.getKeys(false)) {
                    sounds.put(state, soundSection.getString(state));
                }
            }
            String biteModel = skin.getString("bite-model");
            skins.put(name, new SkinProfile(
                    name,
                    Key.key(skin.getString("model", "fishon:bobber/" + name)),
                    biteModel == null || biteModel.isEmpty() ? null : Key.key(biteModel),
                    skin.getInt("bite-model-ticks", 30),
                    colorList(name, skin, "colors"),
                    colorList(name, skin, "bite-colors"),
                    skin.getBoolean("glow", false),
                    parseBillboard(name, skin.getString("billboard", "fixed")),
                    (float) skin.getDouble("scale", 1.0),
                    (float) skin.getDouble("y-offset", 0.0),
                    sounds,
                    skin.getString("particles", ""),
                    skin.getString("animation", "")));
        }
    }

    private void loadPalette(List<Map<?, ?>> entries) {
        for (Map<?, ?> entry : entries) {
            Material icon = Material.matchMaterial(String.valueOf(entry.get("item")));
            List<Color> color = parseColors(List.of(String.valueOf(entry.get("color"))));
            if (icon == null || color.isEmpty()) {
                logger.warning("Skipping palette entry " + entry);
                continue;
            }
            palette.add(new PaletteColor(String.valueOf(entry.get("name")), icon, color.getFirst()));
        }
    }

    private List<Color> colorList(String skin, ConfigurationSection section, String key) {
        List<String> raw = section.getStringList(key);
        List<Color> colors = parseColors(raw);
        if (colors.isEmpty() && !raw.isEmpty()) {
            logger.warning("Invalid " + key + " for skin " + skin + ": " + raw + " (use \"#rrggbb\")");
        }
        return colors;
    }

    private Display.Billboard parseBillboard(String skin, String value) {
        try {
            return Display.Billboard.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            logger.warning("Unknown billboard " + value + " for skin " + skin + "; using fixed");
            return Display.Billboard.FIXED;
        }
    }

    private void loadParticles(ConfigurationSection section) {
        if (section == null) {
            return;
        }
        for (String preset : section.getKeys(false)) {
            Map<String, ParticleSpec> byState = new HashMap<>();
            ConfigurationSection states = section.getConfigurationSection(preset);
            for (String state : states.getKeys(false)) {
                ConfigurationSection spec = states.getConfigurationSection(state);
                String particleName = spec.getString("particle", "");
                Particle particle;
                try {
                    particle = Particle.valueOf(particleName);
                } catch (IllegalArgumentException e) {
                    logger.warning("Unknown particle " + particleName + " in particles." + preset + "." + state);
                    continue;
                }
                byState.put(state, new ParticleSpec(
                        particle, spec.getInt("count", 1), spec.getDouble("spread", 0), spec.getDouble("speed", 0)));
            }
            particlePresets.put(preset, byState);
        }
    }

    private void loadAnimations(ConfigurationSection section) {
        if (section == null) {
            return;
        }
        for (String preset : section.getKeys(false)) {
            Map<String, List<Keyframe>> byState = new HashMap<>();
            ConfigurationSection states = section.getConfigurationSection(preset);
            for (String state : states.getKeys(false)) {
                List<Keyframe> frames = new ArrayList<>();
                for (Map<?, ?> raw : states.getMapList(state)) {
                    frames.add(parseKeyframe(raw));
                }
                byState.put(state, frames);
            }
            animationPresets.put(preset, byState);
        }
    }

    private static Keyframe parseKeyframe(Map<?, ?> raw) {
        float scaleX = 1;
        float scaleY = 1;
        float scaleZ = 1;
        Object scale = raw.get("scale");
        if (scale instanceof List<?> list && list.size() == 3) {
            scaleX = toFloat(list.get(0), 1);
            scaleY = toFloat(list.get(1), 1);
            scaleZ = toFloat(list.get(2), 1);
        } else if (scale != null) {
            scaleX = scaleY = scaleZ = toFloat(scale, 1);
        }
        return new Keyframe(
                Math.max(1, (int) toFloat(raw.get("ticks"), 1)),
                toFloat(raw.get("y"), 0),
                scaleX, scaleY, scaleZ,
                toFloat(raw.get("rotate-x"), 0),
                toFloat(raw.get("rotate-y"), 0),
                toFloat(raw.get("rotate-z"), 0));
    }

    private static float toFloat(Object value, float fallback) {
        return value instanceof Number number ? number.floatValue() : fallback;
    }
}

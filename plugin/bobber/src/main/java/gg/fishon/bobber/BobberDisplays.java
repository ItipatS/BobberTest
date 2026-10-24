package gg.fishon.bobber;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class BobberDisplays {

    public static final NamespacedKey DISPLAY_TAG = new NamespacedKey("fishon", "bobber_display");
    // Written on the hook by a fishing minigame just before it removes the hook: "caught" or "escaped".
    public static final NamespacedKey RESULT_KEY = new NamespacedKey("fishon", "result");
    private static final double EFFECT_RANGE = 16;
    private static final int TRAIL_MAX_TICKS = 60;

    private final Plugin plugin;
    private final SkinRegistry skins;
    private final Map<UUID, TrackedBobber> byHook = new HashMap<>();
    private final Set<UUID> playersWithPack = new HashSet<>();
    private long sweepRemovals;

    public BobberDisplays(Plugin plugin, SkinRegistry skins) {
        this.plugin = plugin;
        this.skins = skins;
    }

    public void mount(FishHook hook, Player owner, SkinProfile skin, List<Color> colors) {
        if (!hook.isValid() || byHook.containsKey(hook.getUniqueId())) {
            return;
        }
        // Level, not the hook's own rotation: a hook still carries the pitch of the cast, which would tilt the model.
        Location level = hook.getLocation();
        level.setYaw(0);
        level.setPitch(0);
        ItemDisplay display = hook.getWorld().spawn(level, ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.setItemStack(bobberItem(skin.model(), colors));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setBillboard(skin.billboard());
            if (skin.glow()) {
                d.setBrightness(new Display.Brightness(15, 15));
            }
            d.setTransformation(restTransformation(skin));
            d.getPersistentDataContainer().set(DISPLAY_TAG, PersistentDataType.BOOLEAN, true);
        });
        hideFromPlayersWithoutPack(display);
        hook.addPassenger(display);
        TrackedBobber tracked = new TrackedBobber(hook, display, skin, owner.getUniqueId(), colors);
        byHook.put(hook.getUniqueId(), tracked);

        playSound(tracked, "cast");
        startCastTrail(tracked);
        playIdle(tracked);
    }

    public void onLured(FishHook hook) {
        TrackedBobber tracked = byHook.get(hook.getUniqueId());
        if (tracked == null) {
            return;
        }
        spawnParticles(tracked, "lured");
        play(tracked, skins.animation(tracked.skin.animationPreset(), "lured"), () -> playIdle(tracked));
    }

    public void onBite(FishHook hook) {
        TrackedBobber tracked = byHook.get(hook.getUniqueId());
        if (tracked == null) {
            return;
        }
        playSound(tracked, "bite");
        spawnParticles(tracked, "bite");
        tracked.hooked = true;
        showBiteLook(tracked);
        play(tracked, skins.animation(tracked.skin.animationPreset(), "bite"), () -> playIdle(tracked));
    }

    // The bite look is the bite model and/or bite colours. bite-model-ticks > 0 swaps back on a timer: plain vanilla
    // fires no event when a fish escapes unreeled. 0 holds it until the hook is gone, for minigame servers.
    private void showBiteLook(TrackedBobber tracked) {
        SkinProfile skin = tracked.skin;
        if (skin.biteModel() == null && skin.biteColors().isEmpty()) {
            return;
        }
        int bite = ++tracked.biteRun;
        Key model = skin.biteModel() != null ? skin.biteModel() : skin.model();
        List<Color> colors = skin.biteColors().isEmpty() ? tracked.colors : skin.biteColors();
        tracked.display.setItemStack(bobberItem(model, colors));
        if (skin.biteModelTicks() <= 0) {
            return;
        }
        tracked.display.getScheduler().runDelayed(plugin, task -> {
            if (bite == tracked.biteRun) {
                tracked.display.setItemStack(bobberItem(skin.model(), tracked.colors));
            }
        }, null, Math.max(1, skin.biteModelTicks()));
    }

    /** Applies a new look to the owner's bobber in the water, if any: re-mounts for a new skin, recolours otherwise. */
    public void update(Player owner, SkinProfile skin, List<Color> colors) {
        for (TrackedBobber tracked : new ArrayList<>(byHook.values())) {
            if (!tracked.owner.equals(owner.getUniqueId())) {
                continue;
            }
            if (tracked.skin.name().equals(skin.name())) {
                recolor(owner, colors);
            } else {
                FishHook hook = tracked.hook;
                remove(hook.getUniqueId());
                mount(hook, owner, skin, colors);
            }
            return;
        }
    }

    public boolean recolor(Player owner, List<Color> colors) {
        for (TrackedBobber tracked : byHook.values()) {
            if (tracked.owner.equals(owner.getUniqueId())) {
                tracked.colors = colors;
                tracked.biteRun++;
                tracked.display.setItemStack(bobberItem(tracked.skin.model(), colors));
                return true;
            }
        }
        return false;
    }

    public void onCatch(FishHook hook) {
        TrackedBobber tracked = byHook.get(hook.getUniqueId());
        if (tracked == null) {
            return;
        }
        playSound(tracked, "catch");
        spawnParticles(tracked, "catch");
        remove(hook.getUniqueId());
    }

    public boolean replay(Player owner, String preset, String state) {
        for (TrackedBobber tracked : byHook.values()) {
            if (tracked.owner.equals(owner.getUniqueId())) {
                if (state.equals("bite")) {
                    showBiteLook(tracked);
                }
                play(tracked, skins.animation(preset, state), () -> playIdle(tracked));
                return true;
            }
        }
        return false;
    }

    public void remove(UUID hookId) {
        TrackedBobber tracked = byHook.remove(hookId);
        if (tracked != null && tracked.display.isValid()) {
            tracked.display.remove();
        }
    }

    public void onEntityRemoved(Entity entity) {
        if (entity instanceof FishHook hook) {
            String result = hook.getPersistentDataContainer().get(RESULT_KEY, PersistentDataType.STRING);
            if ("caught".equals(result) || "escaped".equals(result)) {
                finish(hook.getUniqueId(), result);
            } else {
                remove(hook.getUniqueId());
            }
        } else if (entity instanceof ItemDisplay) {
            byHook.values().removeIf(tracked -> tracked.display.getUniqueId().equals(entity.getUniqueId()));
        }
    }

    // The hook is already gone, so the display is unmounted and plays its ending in place before it's removed.
    private void finish(UUID hookId, String result) {
        TrackedBobber tracked = byHook.remove(hookId);
        if (tracked == null || !tracked.display.isValid()) {
            return;
        }
        tracked.display.leaveVehicle();
        playSound(tracked, result.equals("caught") ? "catch" : "escape");
        spawnParticles(tracked, result.equals("caught") ? "catch" : "escape");
        List<Keyframe> ending = skins.animation(tracked.skin.animationPreset(), result);
        if (ending.isEmpty()) {
            tracked.display.remove();
        } else {
            play(tracked, ending, tracked.display::remove);
        }
    }

    public void removeAllOwnedBy(UUID playerId) {
        for (TrackedBobber tracked : new ArrayList<>(byHook.values())) {
            if (tracked.owner.equals(playerId)) {
                remove(tracked.hook.getUniqueId());
            }
        }
    }

    public void removeAll() {
        for (UUID hookId : new ArrayList<>(byHook.keySet())) {
            remove(hookId);
        }
    }

    public int sweep() {
        int removed = 0;
        for (TrackedBobber tracked : new ArrayList<>(byHook.values())) {
            if (!tracked.hook.isValid()) {
                plugin.getLogger().warning("Sweep removed display " + tracked.display.getUniqueId()
                        + " for missing hook " + tracked.hook.getUniqueId() + " (owner " + tracked.owner + ")");
                remove(tracked.hook.getUniqueId());
                removed++;
            }
        }
        sweepRemovals += removed;
        return removed;
    }

    public void setPackLoaded(Player player, boolean loaded) {
        if (loaded) {
            playersWithPack.add(player.getUniqueId());
        } else {
            playersWithPack.remove(player.getUniqueId());
        }
        for (TrackedBobber tracked : byHook.values()) {
            if (loaded) {
                player.showEntity(plugin, tracked.display);
            } else {
                player.hideEntity(plugin, tracked.display);
            }
        }
    }

    public void forget(Player player) {
        playersWithPack.remove(player.getUniqueId());
    }

    public boolean hasPack(Player player) {
        return playersWithPack.contains(player.getUniqueId());
    }

    public int trackedCount() {
        return byHook.size();
    }

    public long sweepRemovals() {
        return sweepRemovals;
    }

    public int displaysInWorlds() {
        int count = 0;
        for (World world : Bukkit.getWorlds()) {
            for (ItemDisplay display : world.getEntitiesByClass(ItemDisplay.class)) {
                if (display.getPersistentDataContainer().has(DISPLAY_TAG)) {
                    count++;
                }
            }
        }
        return count;
    }

    public int hooksInWorlds() {
        int count = 0;
        for (World world : Bukkit.getWorlds()) {
            count += world.getEntitiesByClass(FishHook.class).size();
        }
        return count;
    }

    private void hideFromPlayersWithoutPack(ItemDisplay display) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!playersWithPack.contains(player.getUniqueId())) {
                player.hideEntity(plugin, display);
            }
        }
    }

    private void startCastTrail(TrackedBobber tracked) {
        int[] ticks = {0};
        tracked.hook.getScheduler().runAtFixedRate(plugin, task -> {
            ticks[0] += 2;
            FishHook hook = tracked.hook;
            if (ticks[0] > TRAIL_MAX_TICKS || hook.isOnGround() || hook.isInWater()
                    || hook.getState() != FishHook.HookState.UNHOOKED) {
                task.cancel();
                return;
            }
            spawnParticles(tracked, "cast");
        }, null, 1, 2);
    }

    private void playIdle(TrackedBobber tracked) {
        String state = tracked.hooked && !skins.animation(tracked.skin.animationPreset(), "hooked").isEmpty()
                ? "hooked" : "idle";
        List<Keyframe> idle = skins.animation(tracked.skin.animationPreset(), state);
        if (!idle.isEmpty()) {
            play(tracked, idle, () -> playIdle(tracked));
        }
    }

    private void play(TrackedBobber tracked, List<Keyframe> frames, Runnable onDone) {
        int run = ++tracked.animationRun;
        playFrame(tracked, frames, 0, run, onDone);
    }

    private void playFrame(TrackedBobber tracked, List<Keyframe> frames, int index, int run, Runnable onDone) {
        if (run != tracked.animationRun || !tracked.display.isValid()) {
            return;
        }
        if (index >= frames.size()) {
            if (onDone != null) {
                onDone.run();
            }
            return;
        }
        Keyframe frame = frames.get(index);
        tracked.display.setInterpolationDelay(0);
        tracked.display.setInterpolationDuration(frame.ticks());
        tracked.display.setTransformation(keyframeTransformation(tracked.skin, frame));
        tracked.display.getScheduler().runDelayed(plugin,
                task -> playFrame(tracked, frames, index + 1, run, onDone), null, frame.ticks());
    }

    private void playSound(TrackedBobber tracked, String state) {
        String sound = tracked.skin.sound(state);
        if (sound == null) {
            return;
        }
        Location at = tracked.display.getLocation();
        for (Player player : at.getNearbyPlayers(EFFECT_RANGE)) {
            if (hasPack(player)) {
                player.playSound(at, sound, SoundCategory.NEUTRAL, 1f, 1f);
            }
        }
    }

    private void spawnParticles(TrackedBobber tracked, String state) {
        ParticleSpec spec = skins.particles(tracked.skin, state);
        if (spec == null) {
            return;
        }
        Location at = tracked.display.getLocation();
        for (Player player : at.getNearbyPlayers(EFFECT_RANGE)) {
            if (hasPack(player)) {
                player.spawnParticle(spec.particle(), at, spec.count(),
                        spec.spread(), spec.spread(), spec.spread(), spec.speed());
            }
        }
    }

    // Colours go into custom_model_data; the item definition's tints read them by index (tintindex in the model).
    public static ItemStack bobberItem(Key model, List<Color> colors) {
        ItemStack item = ItemStack.of(Material.PAPER);
        item.setData(DataComponentTypes.ITEM_MODEL, model);
        if (!colors.isEmpty()) {
            item.setData(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelData.customModelData().addColors(colors).build());
        }
        return item;
    }

    private static Transformation restTransformation(SkinProfile skin) {
        return keyframeTransformation(skin, new Keyframe(1, 0, 1, 1, 1, 0, 0, 0));
    }

    private static Transformation keyframeTransformation(SkinProfile skin, Keyframe frame) {
        Quaternionf rotation = new Quaternionf()
                .rotateY((float) Math.toRadians(frame.rotateY()))
                .rotateX((float) Math.toRadians(frame.rotateX()))
                .rotateZ((float) Math.toRadians(frame.rotateZ()));
        return new Transformation(
                new Vector3f(0, skin.yOffset() + frame.y(), 0),
                rotation,
                new Vector3f(skin.scale() * frame.scaleX(), skin.scale() * frame.scaleY(), skin.scale() * frame.scaleZ()),
                new Quaternionf(new AxisAngle4f()));
    }

    private static final class TrackedBobber {
        final FishHook hook;
        final ItemDisplay display;
        final SkinProfile skin;
        final UUID owner;
        List<Color> colors;
        int animationRun;
        int biteRun;
        boolean hooked;

        TrackedBobber(FishHook hook, ItemDisplay display, SkinProfile skin, UUID owner, List<Color> colors) {
            this.hook = hook;
            this.display = display;
            this.skin = skin;
            this.owner = owner;
            this.colors = colors;
        }
    }
}

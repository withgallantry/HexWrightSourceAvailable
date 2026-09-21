package com.bluup.hexwright.server.talisman;

import com.bluup.hexwright.server.item.HexwrightItems;
import com.bluup.hexwright.server.pocketcaster.PocketCasterData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;
import java.util.Optional;

public final class TalismanData {

    public static final String ROOT_TAG = "hexwright_talisman";
    private static final String TAG_QUALITY = "Quality";
    private static final String TAG_TRIGGER = "Trigger";
    private static final String TAG_CONTEXT = "Context";
    private static final String TAG_NEXT_FIRE = "NextFire";

    public static final String TAG_HEX_DATA = "data";

    public enum Trigger {
        STRUCK(100L, CROWD_FLOOR),
        FELLED(40L, CROWD_FLOOR),
        MISHAP(120L, INPUT_FLOOR),
        USE(20L, INPUT_FLOOR),
        HURT(40L, CROWD_FLOOR),
        WOUND(100L, CROWD_FLOOR),
        DEATH(20L, INPUT_FLOOR),
        BREAK(20L, INPUT_FLOOR),
        MINE(40L, INPUT_FLOOR),
        TRAVERSE(20L, INPUT_FLOOR),
        INTERACT(20L, INPUT_FLOOR),
        BRINK(6000L, 6000L, false);

        public final long baseCooldownTicks;

        public final long floorTicks;

        public final boolean bindable;

        Trigger(long baseCooldownTicks, long floorTicks) {
            this(baseCooldownTicks, floorTicks, true);
        }

        Trigger(long baseCooldownTicks, long floorTicks, boolean bindable) {
            this.baseCooldownTicks = baseCooldownTicks;
            this.floorTicks = floorTicks;
            this.bindable = bindable;
        }

        public String translationKey() {
            return "trigger.hexwright." + name().toLowerCase(Locale.ROOT);
        }
    }

    public enum Context {
        SELF,
        OTHER,
        MAGNITUDE,
        GAZE,
        POSITION;

        public String translationKey() {
            return "context.hexwright." + name().toLowerCase(Locale.ROOT);
        }
    }

    private TalismanData() {
    }


    public static ItemStack create(PocketCasterData.Quality quality) {
        ItemStack stack = new ItemStack(HexwrightItems.TALISMAN);
        CompoundTag root = stack.getOrCreateTagElement(ROOT_TAG);
        root.putString(TAG_QUALITY, quality.name());
        return stack;
    }

    public static PocketCasterData.Quality getQuality(ItemStack stack) {
        CompoundTag root = stack.getTagElement(ROOT_TAG);
        if (root == null) {
            return PocketCasterData.Quality.CRUDE;
        }
        try {
            return PocketCasterData.Quality.valueOf(root.getString(TAG_QUALITY));
        } catch (IllegalArgumentException e) {
            return PocketCasterData.Quality.CRUDE;
        }
    }


    public static Optional<Trigger> getTrigger(ItemStack stack) {
        if (stack.getItem() instanceof TalismanItem talisman && talisman.fixedTrigger() != null) {
            return Optional.of(talisman.fixedTrigger());
        }
        CompoundTag root = stack.getTagElement(ROOT_TAG);
        if (root == null || !root.contains(TAG_TRIGGER)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Trigger.valueOf(root.getString(TAG_TRIGGER)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public static void setTrigger(ItemStack stack, Trigger trigger) {
        stack.getOrCreateTagElement(ROOT_TAG).putString(TAG_TRIGGER, trigger.name());
    }

    public static Optional<Context> getContext(ItemStack stack) {
        if (stack.getItem() instanceof TalismanItem talisman && talisman.fixedContext() != null) {
            return Optional.of(talisman.fixedContext());
        }
        CompoundTag root = stack.getTagElement(ROOT_TAG);
        if (root == null || !root.contains(TAG_CONTEXT)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Context.valueOf(root.getString(TAG_CONTEXT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public static void setContext(ItemStack stack, Context context) {
        stack.getOrCreateTagElement(ROOT_TAG).putString(TAG_CONTEXT, context.name());
    }

    private static final long INPUT_FLOOR = 2L;

    private static final long CROWD_FLOOR = 10L;

    public static long cooldownTicks(ItemStack stack, Trigger trigger) {
        if (stack.getItem() instanceof TalismanItem talisman && talisman.fixedTrigger() != null) {
            return trigger.baseCooldownTicks;
        }
        PocketCasterData.Quality quality = getQuality(stack);
        long base = trigger.baseCooldownTicks;
        long floor = Math.min(trigger.floorTicks, base);
        int steps = Math.max(1, PocketCasterData.Quality.values().length - 1);
        long reduced = base - (base - floor) * quality.ordinal() / steps;
        return Math.max(floor, reduced);
    }

    public static long getNextFire(ItemStack stack) {
        CompoundTag root = stack.getTagElement(ROOT_TAG);
        return root == null ? 0L : root.getLong(TAG_NEXT_FIRE);
    }

    public static void setNextFire(ItemStack stack, long gameTime) {
        stack.getOrCreateTagElement(ROOT_TAG).putLong(TAG_NEXT_FIRE, gameTime);
    }

    public static boolean isArmed(ItemStack stack) {
        return getTrigger(stack).isPresent()
            && getContext(stack).isPresent()
            && stack.getTag() != null && stack.getTag().contains(TAG_HEX_DATA);
    }
}

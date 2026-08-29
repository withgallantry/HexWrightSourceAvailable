package com.bluup.hexwright.server.remnant;

import com.bluup.hexwright.common.remnant.Remnant;
import com.bluup.hexwright.common.remnant.RemnantType;
import com.bluup.hexwright.server.item.HexwrightItems;
import com.bluup.hexwright.server.pocketcaster.PocketCasterData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public final class BottleData {

    public static final String ROOT_TAG = "hexwright_bottle";
    private static final String TAG_QUALITY = "Quality";
    private static final String TAG_REMNANT = "Remnant";
    private static final String TAG_DRAMS = "Drams";

    private BottleData() {
    }

    public static int capacity(PocketCasterData.Quality quality) {
        return switch (quality) {
            case CRUDE -> 100;
            case SOUND -> 175;
            case FINE -> 275;
            case EXQUISITE -> 400;
            case MASTERWORK -> 600;
        };
    }

    public static ItemStack create(PocketCasterData.Quality quality) {
        ItemStack stack = new ItemStack(HexwrightItems.HEX_ENGRAVED_BOTTLE);
        stack.getOrCreateTagElement(ROOT_TAG).putString(TAG_QUALITY, quality.name());
        return stack;
    }

    public static PocketCasterData.Quality getQuality(ItemStack stack) {
        CompoundTag root = stack.getTagElement(ROOT_TAG);
        if (root == null) {
            return PocketCasterData.Quality.CRUDE;
        }
        return PocketCasterData.Quality.byName(root.getString(TAG_QUALITY));
    }

    public static int capacity(ItemStack stack) {
        return capacity(getQuality(stack));
    }

    public static @Nullable Remnant getContents(ItemStack stack) {
        CompoundTag root = stack.getTagElement(ROOT_TAG);
        if (root == null || !root.contains(TAG_REMNANT)) {
            return null;
        }
        RemnantType type = RemnantType.byName(root.getString(TAG_REMNANT));
        if (type == null) {
            return null;
        }
        double drams = root.getDouble(TAG_DRAMS);
        return drams <= 0.0 ? null : new Remnant(type, drams);
    }

    public static boolean isEmpty(ItemStack stack) {
        return getContents(stack) == null;
    }

    public static boolean isFull(ItemStack stack) {
        Remnant contents = getContents(stack);
        return contents != null && contents.drams() >= capacity(stack) - 0.001;
    }

    public static double fillFraction(ItemStack stack) {
        Remnant contents = getContents(stack);
        if (contents == null) {
            return 0.0;
        }
        int capacity = capacity(stack);
        return capacity <= 0 ? 0.0 : Math.min(1.0, contents.drams() / capacity);
    }

    public static boolean canAccept(ItemStack stack, @Nullable Remnant remnant) {
        if (remnant == null || remnant.isEmpty()) {
            return false;
        }
        Remnant contents = getContents(stack);
        if (contents == null) {
            return true;
        }
        return contents.type() == remnant.type() && !isFull(stack);
    }

    public static double pour(ItemStack stack, Remnant remnant) {
        if (!canAccept(stack, remnant)) {
            return 0.0;
        }
        Remnant contents = getContents(stack);
        double held = contents == null ? 0.0 : contents.drams();
        double room = capacity(stack) - held;
        double poured = Math.min(room, remnant.drams());
        if (poured <= 0.0) {
            return 0.0;
        }
        CompoundTag root = stack.getOrCreateTagElement(ROOT_TAG);
        root.putString(TAG_REMNANT, remnant.type().name());
        root.putDouble(TAG_DRAMS, held + poured);
        return poured;
    }

    public static void empty(ItemStack stack) {
        CompoundTag root = stack.getTagElement(ROOT_TAG);
        if (root != null) {
            root.remove(TAG_REMNANT);
            root.remove(TAG_DRAMS);
        }
    }

    public static final int FILL_STATES = 3;

    public static int fillState(ItemStack stack) {
        if (isEmpty(stack)) {
            return 0;
        }
        return isFull(stack) ? 2 : 1;
    }

    public static int modelIndex(ItemStack stack) {
        return getQuality(stack).ordinal() * FILL_STATES + fillState(stack);
    }

    public static final int MODEL_VARIANTS = PocketCasterData.Quality.values().length * FILL_STATES;
}

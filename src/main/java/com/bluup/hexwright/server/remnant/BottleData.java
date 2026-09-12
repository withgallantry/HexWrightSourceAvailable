package com.bluup.hexwright.server.remnant;

import com.bluup.hexwright.common.remnant.Remnant;
import com.bluup.hexwright.common.remnant.RemnantType;
import com.bluup.hexwright.server.fluid.TankRemnants;
import com.bluup.hexwright.server.item.HexwrightItems;
import com.bluup.hexwright.server.pocketcaster.PocketCasterData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public final class BottleData {

    public static final String ROOT_TAG = "hexwright_bottle";
    private static final String TAG_QUALITY = "Quality";
    private static final String TAG_MIX = "Mix";

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

    public static TankRemnants getMixture(ItemStack stack) {
        CompoundTag root = stack.getTagElement(ROOT_TAG);
        return root == null ? TankRemnants.EMPTY : read(root);
    }

    private static TankRemnants read(CompoundTag root) {
        if (root.contains(TAG_MIX)) {
            return TankRemnants.load(root.get(TAG_MIX));
        }
        if (!root.contains(TAG_REMNANT)) {
            return TankRemnants.EMPTY;
        }
        RemnantType type = RemnantType.byName(root.getString(TAG_REMNANT));
        double drams = root.getDouble(TAG_DRAMS);
        return type == null || drams <= 0.0
            ? TankRemnants.EMPTY
            : TankRemnants.EMPTY.plus(new Remnant(type, drams));
    }

    private static void write(ItemStack stack, TankRemnants mix) {
        CompoundTag root = stack.getOrCreateTagElement(ROOT_TAG);
        root.remove(TAG_REMNANT);
        root.remove(TAG_DRAMS);
        if (mix.isEmpty()) {
            root.remove(TAG_MIX);
        } else {
            root.put(TAG_MIX, mix.save());
        }
    }

    public static @Nullable Remnant getContents(ItemStack stack) {
        TankRemnants mix = getMixture(stack);
        return mix.kinds() == 1 ? mix.contents().get(0) : null;
    }

    public static double heldDrams(ItemStack stack) {
        return getMixture(stack).total();
    }

    public static boolean isEmpty(ItemStack stack) {
        return getMixture(stack).isEmpty();
    }

    public static boolean isFull(ItemStack stack) {
        double held = heldDrams(stack);
        return held > 0.0 && held >= capacity(stack) - 0.001;
    }

    public static double headroom(ItemStack stack) {
        return Math.max(0.0, capacity(stack) - heldDrams(stack));
    }

    public static double fillFraction(ItemStack stack) {
        int capacity = capacity(stack);
        return capacity <= 0 ? 0.0 : Math.min(1.0, heldDrams(stack) / capacity);
    }

    public static boolean canAccept(ItemStack stack, @Nullable Remnant remnant) {
        if (remnant == null || remnant.isEmpty()) {
            return false;
        }
        TankRemnants mix = getMixture(stack);
        if (mix.isEmpty()) {
            return true;
        }
        return mix.has(remnant.type()) && !isFull(stack);
    }

    public static double pour(ItemStack stack, Remnant remnant) {
        if (!canAccept(stack, remnant)) {
            return 0.0;
        }
        double poured = Math.min(headroom(stack), remnant.drams());
        if (poured < TankRemnants.MIN_DRAMS) {
            return 0.0;
        }
        write(stack, getMixture(stack).plus(remnant.withDrams(poured)));
        return poured;
    }

    public static double pourMixture(ItemStack stack, TankRemnants blend) {
        TankRemnants fits = blend.cappedTo(headroom(stack));
        if (fits.isEmpty()) {
            return 0.0;
        }
        write(stack, getMixture(stack).plusAll(fits));
        return fits.total();
    }

    public static TankRemnants acceptableFrom(ItemStack stack, TankRemnants available) {
        TankRemnants mix = getMixture(stack);
        if (mix.isEmpty()) {
            return available;
        }
        TankRemnants shared = TankRemnants.EMPTY;
        for (Remnant part : available.contents()) {
            if (mix.has(part.type())) {
                shared = shared.plus(part);
            }
        }
        return shared;
    }

    public static void empty(ItemStack stack) {
        if (stack.getTagElement(ROOT_TAG) != null) {
            write(stack, TankRemnants.EMPTY);
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

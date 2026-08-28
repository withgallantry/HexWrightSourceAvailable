package com.bluup.hexwright.server.vehicle;

import com.bluup.hexwright.server.item.HexwrightItems;
import com.bluup.hexwright.server.pocketcaster.PocketCasterData;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public enum BroomVariant {

    ETHEREAL("ethereal", null),
    STARRY("starry", PocketCasterData.Quality.SOUND),
    NOCTURNE("nocturne", PocketCasterData.Quality.FINE),
    SWEET_ENCHANTRESS("sweet_enchantress", PocketCasterData.Quality.EXQUISITE);

    private final String id;
    private final @Nullable PocketCasterData.Quality requiredMastery;

    BroomVariant(String id, @Nullable PocketCasterData.Quality requiredMastery) {
        this.id = id;
        this.requiredMastery = requiredMastery;
    }

    public String id() {
        return id;
    }

    public @Nullable PocketCasterData.Quality requiredMastery() {
        return requiredMastery;
    }

    public String nameKey() {
        return "item.hexwright.broom." + id;
    }

    public float modelFraction() {
        return ordinal() / (float) (values().length - 1);
    }

    public static double gradeFraction(PocketCasterData.Quality quality) {
        return VehicleData.gradeFraction(quality);
    }


    public static double maxHorizontalSpeed(PocketCasterData.Quality quality) {
        return VehicleConfig.BROOM_MAX_HORIZONTAL_SPEED
            + VehicleConfig.BROOM_HORIZONTAL_SPEED_GRADE_BONUS * gradeFraction(quality);
    }

    public static double maxVerticalSpeed(PocketCasterData.Quality quality) {
        return VehicleConfig.BROOM_MAX_VERTICAL_SPEED
            + VehicleConfig.BROOM_VERTICAL_SPEED_GRADE_BONUS * gradeFraction(quality);
    }

    public static double maxAcceleration(PocketCasterData.Quality quality) {
        return VehicleConfig.BROOM_MAX_ACCELERATION
            + VehicleConfig.BROOM_ACCELERATION_GRADE_BONUS * gradeFraction(quality);
    }

    public static long mediaCapacity(PocketCasterData.Quality quality) {
        return VehicleConfig.BROOM_MEDIA_CAPACITY
            + Math.round(VehicleConfig.BROOM_MEDIA_CAPACITY_GRADE_BONUS * gradeFraction(quality));
    }

    public static BroomVariant byId(String id) {
        for (BroomVariant variant : values()) {
            if (variant.id.equalsIgnoreCase(id)) {
                return variant;
            }
        }
        return ETHEREAL;
    }

    public static BroomVariant of(ItemStack stack) {
        return byId(VehicleData.getVariant(stack.getOrCreateTagElement(VehicleData.ROOT_TAG)));
    }

    public ItemStack createStack(PocketCasterData.Quality quality) {
        ItemStack stack = new ItemStack(HexwrightItems.BROOM);
        var data = stack.getOrCreateTagElement(VehicleData.ROOT_TAG);
        VehicleData.setVariant(data, id);
        VehicleData.setQuality(data, quality);
        return stack;
    }

    @Override
    public String toString() {
        return id.toLowerCase(Locale.ROOT);
    }
}

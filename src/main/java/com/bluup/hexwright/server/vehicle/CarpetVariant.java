package com.bluup.hexwright.server.vehicle;

import com.bluup.hexwright.server.item.HexwrightItems;
import com.bluup.hexwright.server.pocketcaster.PocketCasterData;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public enum CarpetVariant {

    PURPLE("purple", null),
    RED("red", PocketCasterData.Quality.FINE),
    TEAL("teal", PocketCasterData.Quality.EXQUISITE);

    private final String id;
    private final @Nullable PocketCasterData.Quality requiredMastery;

    CarpetVariant(String id, @Nullable PocketCasterData.Quality requiredMastery) {
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
        return "item.hexwright.carpet." + id;
    }

    public float modelFraction() {
        return ordinal() / (float) (values().length - 1);
    }


    public static double maxHorizontalSpeed(PocketCasterData.Quality quality) {
        return VehicleConfig.CARPET_MAX_HORIZONTAL_SPEED
            + VehicleConfig.CARPET_HORIZONTAL_SPEED_GRADE_BONUS * VehicleData.gradeFraction(quality);
    }

    public static double maxVerticalSpeed(PocketCasterData.Quality quality) {
        return VehicleConfig.CARPET_MAX_VERTICAL_SPEED
            + VehicleConfig.CARPET_VERTICAL_SPEED_GRADE_BONUS * VehicleData.gradeFraction(quality);
    }

    public static double maxAcceleration(PocketCasterData.Quality quality) {
        return VehicleConfig.CARPET_MAX_ACCELERATION
            + VehicleConfig.CARPET_ACCELERATION_GRADE_BONUS * VehicleData.gradeFraction(quality);
    }

    public static CarpetVariant byId(String id) {
        for (CarpetVariant variant : values()) {
            if (variant.id.equalsIgnoreCase(id)) {
                return variant;
            }
        }
        return PURPLE;
    }

    public static CarpetVariant of(ItemStack stack) {
        return byId(VehicleData.getVariant(stack.getOrCreateTagElement(VehicleData.ROOT_TAG)));
    }

    public ItemStack createStack(PocketCasterData.Quality quality) {
        ItemStack stack = new ItemStack(HexwrightItems.CARPET);
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

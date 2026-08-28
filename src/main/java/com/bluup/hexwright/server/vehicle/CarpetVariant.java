package com.bluup.hexwright.server.vehicle;

import com.bluup.hexwright.server.item.HexwrightItems;
import com.bluup.hexwright.server.pocketcaster.PocketCasterData;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public enum CarpetVariant {

    PURPLE("purple", null, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
    RED("red", PocketCasterData.Quality.FINE, 0.07, 0.07, 0.03, 0.03, 0.006, 0.006),
    TEAL("teal", PocketCasterData.Quality.EXQUISITE, 0.14, 0.10, 0.06, 0.04, 0.012, 0.009);

    private final String id;
    private final @Nullable PocketCasterData.Quality requiredMastery;
    private final double horizontalBonus;
    private final double horizontalGradeBonus;
    private final double verticalBonus;
    private final double verticalGradeBonus;
    private final double accelerationBonus;
    private final double accelerationGradeBonus;

    CarpetVariant(
        String id,
        @Nullable PocketCasterData.Quality requiredMastery,
        double horizontalBonus,
        double horizontalGradeBonus,
        double verticalBonus,
        double verticalGradeBonus,
        double accelerationBonus,
        double accelerationGradeBonus
    ) {
        this.id = id;
        this.requiredMastery = requiredMastery;
        this.horizontalBonus = horizontalBonus;
        this.horizontalGradeBonus = horizontalGradeBonus;
        this.verticalBonus = verticalBonus;
        this.verticalGradeBonus = verticalGradeBonus;
        this.accelerationBonus = accelerationBonus;
        this.accelerationGradeBonus = accelerationGradeBonus;
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

    public double maxHorizontalSpeed(PocketCasterData.Quality quality) {
        return VehicleConfig.CARPET_MAX_HORIZONTAL_SPEED
            + horizontalBonus + horizontalGradeBonus * VehicleData.gradeFraction(quality);
    }

    public double maxVerticalSpeed(PocketCasterData.Quality quality) {
        return VehicleConfig.CARPET_MAX_VERTICAL_SPEED
            + verticalBonus + verticalGradeBonus * VehicleData.gradeFraction(quality);
    }

    public double maxAcceleration(PocketCasterData.Quality quality) {
        return VehicleConfig.CARPET_MAX_ACCELERATION
            + accelerationBonus + accelerationGradeBonus * VehicleData.gradeFraction(quality);
    }

    public double horizontalSpeedBonus(PocketCasterData.Quality quality) {
        return maxHorizontalSpeed(quality) - VehicleConfig.CARPET_MAX_HORIZONTAL_SPEED;
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

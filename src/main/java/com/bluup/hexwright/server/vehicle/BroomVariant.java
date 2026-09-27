package com.bluup.hexwright.server.vehicle;

import com.bluup.hexwright.server.item.HexwrightItems;
import com.bluup.hexwright.server.pocketcaster.PocketCasterData;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

public enum BroomVariant {


    ETHEREAL("ethereal", Render.ITEM_MODEL, false),
    STARRY("starry", Render.ITEM_MODEL, false),
    NOCTURNE("nocturne", Render.ITEM_MODEL, false),
    SWEET_ENCHANTRESS("sweet_enchantress", Render.ITEM_MODEL, false),


    SPLINTERED_SWEEPER("splintered_sweeper", Render.GECKOLIB, false),
    BRISTLEBACK("bristleback", Render.GECKOLIB, false),
    CROOKED_COMBER("crooked_comber", Render.GECKOLIB, false),
    WHISPERWIND("whisperwind", Render.GECKOLIB, false),
    GLEAMGLIDE("gleamglide", Render.GECKOLIB, false),

    STARBEAM("starbeam", Render.GECKOLIB, true),
    NIGHTSWEEP("nightsweep", Render.GECKOLIB, true),
    TWIN_WHISK("twin_whisk", Render.GECKOLIB, true);

    public enum Render { ITEM_MODEL, GECKOLIB }

    private final String id;
    private final Render render;
    private final boolean artifact;

    BroomVariant(String id, Render render, boolean artifact) {
        this.id = id;
        this.render = render;
        this.artifact = artifact;
    }

    public String id() {
        return id;
    }

    public boolean isArtifact() {
        return artifact;
    }

    public boolean geo() {
        return render == Render.GECKOLIB;
    }

    public int passengerCapacity() {
        return BroomSeats.capacity(this);
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


    private double envelopeFraction(PocketCasterData.Quality quality) {
        return artifact ? 1.0 : gradeFraction(quality);
    }

    public double maxHorizontalSpeed(PocketCasterData.Quality quality) {
        return VehicleConfig.BROOM_MAX_HORIZONTAL_SPEED
            + VehicleConfig.BROOM_HORIZONTAL_SPEED_GRADE_BONUS * envelopeFraction(quality)
            + (artifact ? VehicleConfig.BROOM_ARTIFACT_HORIZONTAL_SPEED_BONUS : 0.0);
    }

    public double maxVerticalSpeed(PocketCasterData.Quality quality) {
        return VehicleConfig.BROOM_MAX_VERTICAL_SPEED
            + VehicleConfig.BROOM_VERTICAL_SPEED_GRADE_BONUS * envelopeFraction(quality)
            + (artifact ? VehicleConfig.BROOM_ARTIFACT_VERTICAL_SPEED_BONUS : 0.0);
    }

    public double maxAcceleration(PocketCasterData.Quality quality) {
        return VehicleConfig.BROOM_MAX_ACCELERATION
            + VehicleConfig.BROOM_ACCELERATION_GRADE_BONUS * envelopeFraction(quality)
            + (artifact ? VehicleConfig.BROOM_ARTIFACT_ACCELERATION_BONUS : 0.0);
    }

    public long mediaCapacity(PocketCasterData.Quality quality) {
        return VehicleConfig.BROOM_MEDIA_CAPACITY
            + Math.round(VehicleConfig.BROOM_MEDIA_CAPACITY_GRADE_BONUS * envelopeFraction(quality))
            + (artifact ? VehicleConfig.BROOM_ARTIFACT_MEDIA_CAPACITY_BONUS : 0L);
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
        if (!artifact) {
            VehicleData.setQuality(data, quality);
        }
        return stack;
    }

    public ItemStack createStack() {
        return createStack(PocketCasterData.Quality.CRUDE);
    }

    @Override
    public String toString() {
        return id.toLowerCase(Locale.ROOT);
    }
}

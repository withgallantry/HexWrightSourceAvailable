package com.bluup.hexwright.server.vehicle;

import com.bluup.hexwright.server.item.HexwrightItems;
import com.bluup.hexwright.server.pocketcaster.PocketCasterData;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public enum BroomVariant {


    ETHEREAL("ethereal", null, Render.ITEM_MODEL),
    STARRY("starry", PocketCasterData.Quality.SOUND, Render.ITEM_MODEL),
    NOCTURNE("nocturne", PocketCasterData.Quality.FINE, Render.ITEM_MODEL),
    SWEET_ENCHANTRESS("sweet_enchantress", PocketCasterData.Quality.EXQUISITE, Render.ITEM_MODEL),


    SPLINTERED_SWEEPER("splintered_sweeper", null, Render.GECKOLIB),
    BRISTLEBACK("bristleback", PocketCasterData.Quality.SOUND, Render.GECKOLIB),
    CROOKED_COMBER("crooked_comber", PocketCasterData.Quality.SOUND, Render.GECKOLIB),
    WHISPERWIND("whisperwind", PocketCasterData.Quality.FINE, Render.GECKOLIB),
    GLEAMGLIDE("gleamglide", PocketCasterData.Quality.EXQUISITE, Render.GECKOLIB),

    STARBEAM("starbeam", Render.GECKOLIB),
    NIGHTSWEEP("nightsweep", Render.GECKOLIB),
    TWIN_WHISK("twin_whisk", Render.GECKOLIB);

    public enum Render { ITEM_MODEL, GECKOLIB }

    private final String id;
    private final @Nullable PocketCasterData.Quality requiredMastery;
    private final Render render;
    private final boolean artifact;

    BroomVariant(String id, @Nullable PocketCasterData.Quality requiredMastery, Render render) {
        this.id = id;
        this.requiredMastery = requiredMastery;
        this.render = render;
        this.artifact = false;
    }

    BroomVariant(String id, Render render) {
        this.id = id;
        this.requiredMastery = null;
        this.render = render;
        this.artifact = true;
    }

    public String id() {
        return id;
    }

    public @Nullable PocketCasterData.Quality requiredMastery() {
        return requiredMastery;
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

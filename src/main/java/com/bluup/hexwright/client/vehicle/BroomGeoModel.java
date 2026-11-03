package com.bluup.hexwright.client.vehicle;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.vehicle.BroomItem;
import com.bluup.hexwright.server.vehicle.BroomVariant;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.GeoRenderer;

public class BroomGeoModel extends GeoModel<BroomItem> {

    private static final ResourceLocation ANIMATIONS = Hexwright.id("animations/armour.animation.json");

    private static ResourceLocation geo(BroomVariant variant) {
        return Hexwright.id("geo/item/broom_" + variant.id() + ".geo.json");
    }

    private static ResourceLocation texture(BroomVariant variant) {
        return Hexwright.id("textures/item/broom_" + variant.id() + ".png");
    }

    private static final BroomVariant FALLBACK = BroomVariant.SPLINTERED_SWEEPER;

    private static BroomVariant variantOf(@Nullable GeoRenderer<BroomItem> renderer) {
        if (renderer instanceof GeoItemRenderer<BroomItem> itemRenderer) {
            ItemStack stack = itemRenderer.getCurrentItemStack();
            if (stack != null) {
                BroomVariant variant = BroomVariant.of(stack);
                return variant.geo() ? variant : FALLBACK;
            }
        }
        return FALLBACK;
    }

    @Override
    public ResourceLocation getModelResource(BroomItem item, @Nullable GeoRenderer<BroomItem> renderer) {
        return geo(variantOf(renderer));
    }

    @Override
    public ResourceLocation getTextureResource(BroomItem item, @Nullable GeoRenderer<BroomItem> renderer) {
        return texture(variantOf(renderer));
    }

    @Override
    public ResourceLocation getModelResource(BroomItem item) {
        return geo(FALLBACK);
    }

    @Override
    public ResourceLocation getTextureResource(BroomItem item) {
        return texture(FALLBACK);
    }

    @Override
    public ResourceLocation getAnimationResource(BroomItem item) {
        return ANIMATIONS;
    }
}

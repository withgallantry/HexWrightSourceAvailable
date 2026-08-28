package com.bluup.hexwright.client.armour;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.armour.HexwrightArmourItem;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class HexwrightArmourModel extends GeoModel<HexwrightArmourItem> {

    private static final ResourceLocation ANIMATIONS = Hexwright.id("animations/armour.animation.json");

    @Override
    public ResourceLocation getModelResource(HexwrightArmourItem item) {
        return item.set().geo();
    }

    @Override
    public ResourceLocation getTextureResource(HexwrightArmourItem item) {
        return item.set().texture(item.tier());
    }

    @Override
    public ResourceLocation getAnimationResource(HexwrightArmourItem item) {
        return ANIMATIONS;
    }
}

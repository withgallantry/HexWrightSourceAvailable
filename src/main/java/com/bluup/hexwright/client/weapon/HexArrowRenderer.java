package com.bluup.hexwright.client.weapon;

import com.bluup.hexwright.server.weapon.HexArrowEntity;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public class HexArrowRenderer extends ArrowRenderer<HexArrowEntity> {

    private static final ResourceLocation ARROW_TEXTURE =
        new ResourceLocation("textures/entity/projectiles/arrow.png");

    public HexArrowRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(HexArrowEntity arrow) {
        return ARROW_TEXTURE;
    }
}

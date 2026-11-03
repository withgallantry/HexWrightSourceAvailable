package com.bluup.hexwright.client.powerorb;

import com.bluup.hexwright.client.weapon.SlashBloom;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

final class PowerOrbBloom {

    private static final float MAX_STRENGTH = 2.0f;

    enum Spirit {
        GOLEM(0.7f),
        CAT(0.5f),
        GUARDIAN(0.5f),
        SANCTUARY_HANDS(0.6f);

        final float authored;
        private float strength;

        Spirit(float authored) {
            this.authored = authored;
            this.strength = authored;
        }

        float strength() {
            return strength;
        }

        void setStrength(float value) {
            strength = Mth.clamp(value, 0.0f, MAX_STRENGTH);
        }
    }

    private PowerOrbBloom() {
    }

    static <T extends Entity & GeoAnimatable> void capture(GeoEntityRenderer<T> renderer, T entity,
                                                           PoseStack poseStack, float partialTick,
                                                           Spirit spirit) {
        float strength = spirit.strength();
        if (strength <= 0.0f) {
            return;
        }
        GeoModel<T> model = renderer.getGeoModel();
        BakedGeoModel baked = model.getBakedModel(model.getModelResource(entity));
        RenderType layer = RenderType.entityTranslucentEmissive(renderer.getTextureLocation(entity));
        SlashBloom.capture(layer, raw -> {
            VertexConsumer buffer = SlashBloom.dimmed(raw, strength);
            renderer.reRender(baked, poseStack, type -> buffer, entity, layer, buffer, partialTick,
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, 1.0f);
        });
    }
}

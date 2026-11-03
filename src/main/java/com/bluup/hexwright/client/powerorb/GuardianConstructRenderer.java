package com.bluup.hexwright.client.powerorb;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.powerorb.GuardianConstructEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class GuardianConstructRenderer extends GeoEntityRenderer<GuardianConstructEntity> {

    public GuardianConstructRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(Hexwright.id("guardian_construct")));
        this.shadowRadius = 0.0f;
    }

    private static final double DROP = 1.5D;

    @Override
    public Vec3 getRenderOffset(GuardianConstructEntity entity, float partialTick) {
        Entity wearer = entity.wearer();
        if (wearer == null) {
            return new Vec3(0.0D, -DROP, 0.0D);
        }
        return new Vec3(
            Mth.lerp(partialTick, wearer.xOld, wearer.getX()) - Mth.lerp(partialTick, entity.xOld, entity.getX()),
            Mth.lerp(partialTick, wearer.yOld, wearer.getY()) - Mth.lerp(partialTick, entity.yOld, entity.getY()) - DROP,
            Mth.lerp(partialTick, wearer.zOld, wearer.getZ()) - Mth.lerp(partialTick, entity.zOld, entity.getZ()));
    }

    @Override
    public void render(GuardianConstructEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, LightTexture.FULL_BRIGHT);
        PowerOrbBloom.capture(this, entity, poseStack, partialTick, PowerOrbBloom.Spirit.GUARDIAN);
    }

    @Override
    protected float getDeathMaxRotation(GuardianConstructEntity animatable) {
        return 0.0f;
    }
}

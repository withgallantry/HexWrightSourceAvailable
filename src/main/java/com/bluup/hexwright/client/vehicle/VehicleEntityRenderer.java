package com.bluup.hexwright.client.vehicle;

import com.bluup.hexwright.server.vehicle.CarpetEntity;
import com.bluup.hexwright.server.vehicle.VehicleConfig;
import com.bluup.hexwright.server.vehicle.VehicleData;
import com.bluup.hexwright.server.vehicle.VehicleEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class VehicleEntityRenderer<T extends VehicleEntity> extends EntityRenderer<T> {

    private static final ResourceLocation BLANK_TEXTURE = new ResourceLocation("minecraft", "textures/misc/white.png");

    private static final ItemStack CHEST_STACK = new ItemStack(Items.CHEST);

    private final ItemRenderer itemRenderer;

    public VehicleEntityRenderer(EntityRendererProvider.Context context, float shadowRadius) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
        this.shadowRadius = shadowRadius;
    }

    @Override
    public void render(T entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        double scale = entity.getRenderScale();
        poseStack.pushPose();
        poseStack.translate(0.0, VehicleConfig.VEHICLE_MODEL_HEIGHT_OFFSET * scale + idleBobOffset(entity, partialTicks), 0.0);
        poseStack.scale((float) scale, (float) scale, (float) scale);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - entityYaw));
        poseStack.mulPose(Axis.XP.rotationDegrees((float) entity.getVisualTiltDegrees()));
        applyBank(entity, partialTicks, poseStack);

        ItemStack displayStack = new ItemStack(entity.getItemForm());
        VehicleData.setVariant(displayStack.getOrCreateTagElement(VehicleData.ROOT_TAG), entity.getVariant());
        boolean drawn = entity instanceof CarpetEntity carpet
            && CarpetTailModels.render(carpet, displayStack, partialTicks, itemRenderer, poseStack, buffer, packedLight);
        if (!drawn) {
            itemRenderer.renderStatic(
                displayStack, ItemDisplayContext.NONE, packedLight, OverlayTexture.NO_OVERLAY,
                poseStack, buffer, entity.level(), entity.getId()
            );
        }

        if (entity instanceof CarpetEntity carpet && carpet.isChestAttached()) {
            renderAttachedChest(carpet, poseStack, buffer, packedLight);
        }

        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    private void renderAttachedChest(CarpetEntity carpet, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        poseStack.translate(0.0f, VehicleConfig.CARPET_CHEST_OFFSET_Y, VehicleConfig.CARPET_CHEST_OFFSET_Z);
        poseStack.scale(VehicleConfig.CARPET_CHEST_SCALE, VehicleConfig.CARPET_CHEST_SCALE, VehicleConfig.CARPET_CHEST_SCALE);
        itemRenderer.renderStatic(
            CHEST_STACK, ItemDisplayContext.NONE, packedLight, OverlayTexture.NO_OVERLAY,
            poseStack, buffer, carpet.level(), carpet.getId()
        );
        poseStack.popPose();
    }

    private static void applyBank(VehicleEntity entity, float partialTicks, PoseStack poseStack) {
        float bank = entity.getBankDegrees(partialTicks);
        if (bank == 0.0f) {
            return;
        }
        poseStack.translate(0.0, VehicleConfig.VEHICLE_MODEL_CENTRE_OFFSET, 0.0);
        poseStack.mulPose(Axis.ZP.rotationDegrees(bank));
        poseStack.translate(0.0, -VehicleConfig.VEHICLE_MODEL_CENTRE_OFFSET, 0.0);
    }

    private static double idleBobOffset(VehicleEntity entity, float partialTicks) {
        if (entity.getControllingPassenger() != null) {
            return 0.0;
        }
        double phase = (entity.getId() % 100) * 0.1;
        double t = entity.tickCount + partialTicks + phase;
        return Math.sin(t / VehicleConfig.PARK_BOB_PERIOD_TICKS * (Math.PI * 2.0)) * VehicleConfig.PARK_BOB_AMPLITUDE;
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return BLANK_TEXTURE;
    }
}

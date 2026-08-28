package com.bluup.hexwright.mixin;

import com.bluup.hexwright.client.staff_assembly.StaffTravellerWarpVisualClient;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class PlayerWarpRenderMixin {
    @Inject(
        method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At("HEAD")
    )
    private void hexwright$warpScalePush(LivingEntity entity, float entityYaw, float partialTicks, PoseStack poseStack,
                                          MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        if (!(entity instanceof Player)) {
            return;
        }

        float scale = hexwright$warpScale(entity.getId(), partialTicks);
        if (scale >= 0.999f) {
            return;
        }

        float pivotHeight = entity.getBbHeight() * 0.5f;
        poseStack.pushPose();
        poseStack.translate(0.0, pivotHeight, 0.0);
        poseStack.scale(scale, scale, scale);
        poseStack.translate(0.0, -pivotHeight, 0.0);
    }

    @Inject(
        method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At("RETURN")
    )
    private void hexwright$warpScalePop(LivingEntity entity, float entityYaw, float partialTicks, PoseStack poseStack,
                                         MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        if (!(entity instanceof Player)) {
            return;
        }

        if (hexwright$warpScale(entity.getId(), partialTicks) >= 0.999f) {
            return;
        }

        poseStack.popPose();
    }

    private static float hexwright$warpScale(int entityId, float partialTicks) {
        float t = StaffTravellerWarpVisualClient.getArrivalPopProgress(entityId, partialTicks);
        return t >= 1.0f ? 1.0f : hexwright$easeOutBack(t);
    }

    private static float hexwright$easeOutBack(float t) {
        float c1 = 1.70158f;
        float c3 = c1 + 1.0f;
        float x = t - 1.0f;
        return 1.0f + (c3 * x * x * x) + (c1 * x * x);
    }
}

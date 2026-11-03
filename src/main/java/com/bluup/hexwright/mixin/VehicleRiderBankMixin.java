package com.bluup.hexwright.mixin;

import com.bluup.hexwright.server.vehicle.VehicleEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class VehicleRiderBankMixin {

    @Inject(method = "setupRotations", at = @At("TAIL"))
    private void hexwright$bankWithVehicle(
        LivingEntity entity, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTicks, CallbackInfo ci
    ) {
        if (!(entity.getVehicle() instanceof VehicleEntity vehicle)) {
            return;
        }
        float bank = vehicle.getBankDegrees(partialTicks);
        if (bank == 0.0f) {
            return;
        }
        double pivot = vehicle.getBankPivotY() - entity.getY();
        poseStack.translate(0.0, pivot, 0.0);
        poseStack.mulPose(Axis.ZP.rotationDegrees(bank));
        poseStack.translate(0.0, -pivot, 0.0);
    }
}

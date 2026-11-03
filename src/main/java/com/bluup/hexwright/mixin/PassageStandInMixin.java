package com.bluup.hexwright.mixin;

import com.bluup.hexwright.client.armour.PassageStandInPose;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidModel.class)
public abstract class PassageStandInMixin {

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void hexwright$poseAsStandIn(LivingEntity entity, float limbSwing, float limbSwingAmount,
                                         float ageInTicks, float netHeadYaw, float headPitch,
                                         CallbackInfo ci) {
        PassageStandInPose pose = PassageStandInPose.active;
        if (pose != null) {
            pose.apply((HumanoidModel<?>) (Object) this);
        }
    }
}

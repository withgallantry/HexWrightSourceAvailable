package com.bluup.hexwright.mixin;

import com.bluup.hexwright.client.armour.PassageStandInPose;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class PassageStandInNameMixin {

    @Inject(method = "shouldShowName", at = @At("HEAD"), cancellable = true)
    private void hexwright$noStandInName(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (PassageStandInPose.active != null) {
            cir.setReturnValue(false);
        }
    }
}

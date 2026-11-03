package com.bluup.hexwright.mixin;

import com.bluup.hexwright.client.armour.PassagePortalVisualClient;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public abstract class PassageHidePlayerMixin {

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void hexwright$hidePassageTraveller(Entity entity, Frustum frustum, double camX, double camY,
                                                double camZ, CallbackInfoReturnable<Boolean> cir) {
        if (PassagePortalVisualClient.hidesPlayer(entity)) {
            cir.setReturnValue(false);
        }
    }
}

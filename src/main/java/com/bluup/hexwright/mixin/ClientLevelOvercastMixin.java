package com.bluup.hexwright.mixin;

import com.bluup.hexwright.client.sky.Overcast;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientLevel.class)
public abstract class ClientLevelOvercastMixin {

    @Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true)
    private void hexwright$overcastSky(Vec3 pos, float partialTick, CallbackInfoReturnable<Vec3> cir) {
        if (Overcast.any(partialTick)) {
            cir.setReturnValue(Overcast.sky(cir.getReturnValue(), partialTick));
        }
    }

    @Inject(method = "getCloudColor", at = @At("RETURN"), cancellable = true)
    private void hexwright$overcastClouds(float partialTick, CallbackInfoReturnable<Vec3> cir) {
        if (Overcast.any(partialTick)) {
            cir.setReturnValue(Overcast.clouds(cir.getReturnValue(), partialTick));
        }
    }

    @Inject(method = "getSkyDarken", at = @At("RETURN"), cancellable = true)
    private void hexwright$overcastLight(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (Overcast.any(partialTick)) {
            cir.setReturnValue(Overcast.skyDarken(cir.getReturnValueF(), partialTick));
        }
    }
}

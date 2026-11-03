package com.bluup.hexwright.mixin;

import com.bluup.hexwright.client.weapon.BloodTreeSky;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientLevel.class)
public abstract class ClientLevelBloodSkyMixin {

    @Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true)
    private void hexwright$bloodSky(Vec3 pos, float partialTick, CallbackInfoReturnable<Vec3> cir) {
        float darkness = BloodTreeSky.darkness(partialTick);
        if (darkness > 0.0F) {
            cir.setReturnValue(BloodTreeSky.sky(cir.getReturnValue(), darkness));
        }
    }

    @Inject(method = "getCloudColor", at = @At("RETURN"), cancellable = true)
    private void hexwright$bloodClouds(float partialTick, CallbackInfoReturnable<Vec3> cir) {
        float darkness = BloodTreeSky.darkness(partialTick);
        if (darkness > 0.0F) {
            cir.setReturnValue(BloodTreeSky.clouds(cir.getReturnValue(), darkness));
        }
    }

    @Inject(method = "getSkyDarken", at = @At("RETURN"), cancellable = true)
    private void hexwright$bloodNight(float partialTick, CallbackInfoReturnable<Float> cir) {
        float darkness = BloodTreeSky.darkness(partialTick);
        if (darkness > 0.0F) {
            cir.setReturnValue(BloodTreeSky.skyDarken(cir.getReturnValueF(), darkness));
        }
    }
}

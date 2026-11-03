package com.bluup.hexwright.mixin;

import com.bluup.hexwright.client.weapon.BloodTreeSky;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererBloodSkyMixin {

    @WrapOperation(
        method = "renderSky",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getRainLevel(F)F")
    )
    private float hexwright$bloodEclipse(ClientLevel level, float partialTick, Operation<Float> original) {
        return Math.max(original.call(level, partialTick), BloodTreeSky.darkness(partialTick));
    }
}

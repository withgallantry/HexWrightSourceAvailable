package com.bluup.hexwright.mixin;

import com.bluup.hexwright.client.sky.Overcast;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererOvercastMixin {

    @WrapOperation(
        method = "renderSky",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getRainLevel(F)F")
    )
    private float hexwright$overcastVeil(ClientLevel level, float partialTick, Operation<Float> original) {
        return Math.max(original.call(level, partialTick), Overcast.veil(partialTick));
    }
}

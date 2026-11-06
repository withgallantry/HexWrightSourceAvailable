package com.bluup.hexwright.mixin;

import com.bluup.hexwright.client.weapon.DivineStrainFog;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererDivineStrainMixin {

    @WrapOperation(
        method = "renderSky",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getRainLevel(F)F")
    )
    private float hexwright$divineStrainEclipse(ClientLevel level, float partialTick, Operation<Float> original,
                                                @Local(argsOnly = true) Camera camera) {
        return Math.max(original.call(level, partialTick), DivineStrainFog.darkness(DivineStrainFog.severity(camera)));
    }
}

package com.bluup.hexwright.mixin;

import com.bluup.hexwright.client.staff_assembly.StaffCoreSphereVisualClient;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererSolidDepthMixin {

    @Inject(
        method = "renderLevel",
        at = @At(
            value = "INVOKE",
            ordinal = 0,
            shift = At.Shift.AFTER,
            target = "Lnet/minecraft/client/renderer/LevelRenderer;renderChunkLayer("
                + "Lnet/minecraft/client/renderer/RenderType;"
                + "Lcom/mojang/blaze3d/vertex/PoseStack;DDDLorg/joml/Matrix4f;)V"))
    private void hexwright$captureSolidDepth(CallbackInfo ci) {
        StaffCoreSphereVisualClient.captureSolidDepth();
    }
}

package com.bluup.hexwright.mixin;

import com.bluup.hexwright.client.portal.PortalViewRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererQueueMixin {

    @Redirect(
        method = "initializeQueueForFullUpdate",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getPosition()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 hexwright$unfoldedQueuePosition(Camera camera) {
        Vec3 recorded = PortalViewRenderer.unfoldedCameraPosition();
        return recorded == null ? camera.getPosition() : recorded;
    }

    @Redirect(
        method = "initializeQueueForFullUpdate",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getBlockPosition()Lnet/minecraft/core/BlockPos;"))
    private BlockPos hexwright$unfoldedQueueBlock(Camera camera) {
        BlockPos recorded = PortalViewRenderer.unfoldedCameraBlock();
        return recorded == null ? camera.getBlockPosition() : recorded;
    }
}

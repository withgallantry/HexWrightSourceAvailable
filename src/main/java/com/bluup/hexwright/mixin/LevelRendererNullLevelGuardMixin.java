package com.bluup.hexwright.mixin;

import com.bluup.hexwright.Hexwright;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererNullLevelGuardMixin {

    @Shadow
    private @Nullable ClientLevel level;

    private static boolean hexwright$reportedClear;
    private static boolean hexwright$reportedRefusal;
    private static int hexwright$renderDepth;

    @Inject(method = "setLevel", at = @At("HEAD"))
    private void hexwright$noteLevelCleared(@Nullable ClientLevel incoming, CallbackInfo ci) {
        if (incoming != null || hexwright$reportedClear) {
            return;
        }
        if (hexwright$renderDepth == 0) {
            return;
        }
        if ((Object) this != Minecraft.getInstance().levelRenderer) {
            return;
        }
        hexwright$reportedClear = true;
        Hexwright.LOGGER.warn("[portal] the installed level renderer just had its level cleared -"
            + " any world pass before it is replaced would throw inside vanilla. Caller:",
            new Throwable("level cleared here"));
    }

    @Inject(method = "renderLevel", at = @At("HEAD"), cancellable = true)
    private void hexwright$refuseLevellessRender(PoseStack poseStack, float partialTick,
                                                 long finishNanos, boolean renderBlockOutline,
                                                 Camera camera, GameRenderer gameRenderer,
                                                 LightTexture lightTexture, Matrix4f projection,
                                                 CallbackInfo ci) {
        if (level != null) {
            hexwright$renderDepth++;
            return;
        }
        ci.cancel();
        if (hexwright$reportedRefusal) {
            return;
        }
        hexwright$reportedRefusal = true;
        Hexwright.LOGGER.warn("[portal] refused a world pass on a level renderer with no level;"
            + " drawing nothing rather than throwing. Called from:",
            new Throwable("render refused here"));
    }

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void hexwright$leaveRender(PoseStack poseStack, float partialTick, long finishNanos,
                                       boolean renderBlockOutline, Camera camera,
                                       GameRenderer gameRenderer, LightTexture lightTexture,
                                       Matrix4f projection, CallbackInfo ci) {
        if (hexwright$renderDepth > 0) {
            hexwright$renderDepth--;
        }
    }
}

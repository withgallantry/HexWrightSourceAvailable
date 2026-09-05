package com.bluup.hexwright.mixin;

import com.bluup.hexwright.Hexwright;
import com.mojang.blaze3d.platform.GlDebug;
import org.lwjgl.opengl.GL43;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GlDebug.class)
public class IrisGlDebugMixin {
    @Unique private static int hexwright$errors;

    @Inject(method = "printDebugLog", at = @At("HEAD"))
    private static void hexwright$firstGlError(int source, int type, int id, int severity,
                                              int length, long message, long user, CallbackInfo ci) {
        if (type == GL43.GL_DEBUG_TYPE_ERROR && hexwright$errors++ < 3) {
            Hexwright.LOGGER.error("[iris-audit] GL error {}: {}", id,
                MemoryUtil.memUTF8(message, length), new IllegalStateException("GL callback stack"));
        }
    }
}

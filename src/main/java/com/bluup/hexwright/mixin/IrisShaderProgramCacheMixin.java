package com.bluup.hexwright.mixin;

import com.bluup.hexwright.client.render.IrisCompat;
import com.mojang.blaze3d.shaders.Program;
import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.HashMap;
import java.util.Map;

@Mixin(ShaderInstance.class)
public class IrisShaderProgramCacheMixin {
    @Redirect(method = "getOrCreate", at = @At(value = "INVOKE",
        target = "Lcom/mojang/blaze3d/shaders/Program$Type;getPrograms()Ljava/util/Map;"))
    private static Map<String, Program> hexwright$dimensionPrograms(Program.Type type) {
        return IrisCompat.isShaderPackActive() ? new HashMap<>() : type.getPrograms();
    }
}

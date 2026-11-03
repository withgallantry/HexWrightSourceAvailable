package com.bluup.hexwright.mixin;

import com.bluup.hexwright.client.render.IrisCompat;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.shaders.Program;
import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.HashMap;
import java.util.Map;

@Mixin(ShaderInstance.class)
public class IrisShaderProgramCacheMixin {
    @WrapOperation(method = "getOrCreate", at = @At(value = "INVOKE",
        target = "Lcom/mojang/blaze3d/shaders/Program$Type;getPrograms()Ljava/util/Map;"))
    private static Map<String, Program> hexwright$dimensionPrograms(Program.Type type, Operation<Map<String, Program>> original) {
        return IrisCompat.isShaderPackActive() ? new HashMap<>() : original.call(type);
    }
}

package com.bluup.hexwright.mixin;

import at.petrak.hexcasting.api.client.ClientRenderHelper;
import com.bluup.hexwright.client.spellcasting.PatternDrawBatch;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec2;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = ClientRenderHelper.class, remap = false)
public abstract class CastingStackBatchMixin {
    @Redirect(
        method = "renderCastingStack",
        at = @At(
            value = "INVOKE",
            target = "Lat/petrak/hexcasting/client/render/RenderLib;drawLineSeq(Lorg/joml/Matrix4f;Ljava/util/List;FFII)V"))
    private static void hexwright$batchStroke(Matrix4f mat, List<Vec2> points, float width, float z,
                                              int tail, int head) {
        PatternDrawBatch.drawLineSeq(mat, points, width, z, tail, head);
    }

    @Inject(method = "renderCastingStack", at = @At("RETURN"))
    private static void hexwright$drawStackBatch(PoseStack ps, Player player, float pticks, CallbackInfo ci) {
        ShaderInstance shader = RenderSystem.getShader();
        if (PatternDrawBatch.flush()) {
            RenderSystem.setShader(() -> shader);
            RenderSystem.enableCull();
        }
    }
}

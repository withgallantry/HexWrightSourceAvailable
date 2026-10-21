package com.bluup.hexwright.mixin;

import com.bluup.hexwright.client.hexweave.WovenOrbPatterns;
import com.bluup.hexwright.client.render.IrisCompat;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemRenderer.class)
public abstract class ItemRendererWovenOrbMixin {

    @Inject(
        method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ItemBlockRenderTypes;getRenderType(Lnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/client/renderer/RenderType;"
        )
    )
    private void hexwright$renderWovenOrbPatterns(ItemStack stack, ItemDisplayContext displayContext,
                                                  boolean leftHand, PoseStack poseStack,
                                                  MultiBufferSource bufferSource, int light, int overlay,
                                                  BakedModel model, CallbackInfo ci) {
        if (!IrisCompat.isRenderingShadowPass()) {
            WovenOrbPatterns.onOrbRendered(stack, poseStack, bufferSource);
        }
    }
}

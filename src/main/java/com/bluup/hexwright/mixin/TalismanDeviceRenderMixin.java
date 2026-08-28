package com.bluup.hexwright.mixin;

import com.bluup.hexwright.client.talisman.TalismanItemRenderer;
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
public abstract class TalismanDeviceRenderMixin {

    @Inject(
        method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V", ordinal = 1)
    )
    private void hexwright$renderTalismanDevice(ItemStack stack, ItemDisplayContext displayContext,
                                                boolean leftHand, PoseStack poseStack,
                                                MultiBufferSource bufferSource, int light, int overlay,
                                                BakedModel model, CallbackInfo ci) {
        TalismanItemRenderer.renderDevice(stack, poseStack, bufferSource, light, overlay);
    }
}

package com.bluup.hexwright.mixin;

import com.bluup.hexwright.client.render.IrisCompat;
import com.bluup.hexwright.client.render.emissive.EmissiveItemModels;
import com.bluup.hexwright.client.render.emissive.EmissiveItemTrace;
import com.bluup.hexwright.client.staff_assembly.StaffTipFlash;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemRenderer.class)
public abstract class ItemRendererEmissiveMixin {

    @Inject(
        method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/ItemRenderer;renderModelLists(Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/item/ItemStack;IILcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;)V",
            shift = At.Shift.AFTER
        )
    )
    private void hexwright$renderEmissiveOverlay(ItemStack stack, ItemDisplayContext displayContext,
                                                 boolean leftHand, PoseStack poseStack,
                                                 MultiBufferSource bufferSource, int light, int overlay,
                                                 BakedModel model, CallbackInfo ci,
                                                 @Local RenderType itemLayer) {
        if (IrisCompat.isRenderingShadowPass()) {
            return;
        }
        boolean handPose = displayContext == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
            || displayContext == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        EmissiveItemTrace.note(stack, displayContext, model, handPose);
        EmissiveItemModels.renderGlow(model, poseStack, bufferSource, overlay, itemLayer, handPose);
        StaffTipFlash.onStaffRendered(stack, displayContext, poseStack, bufferSource, itemLayer);
    }
}

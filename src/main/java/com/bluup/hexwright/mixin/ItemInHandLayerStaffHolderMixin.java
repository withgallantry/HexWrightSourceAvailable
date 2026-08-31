package com.bluup.hexwright.mixin;

import com.bluup.hexwright.client.staff_assembly.StaffTipFlash;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerStaffHolderMixin {

    @Inject(method = "renderArmWithItem", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;"
            + "Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Z"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    private void hexwright$beginHolder(LivingEntity entity, ItemStack stack, ItemDisplayContext context,
                                       HumanoidArm arm, PoseStack poseStack, MultiBufferSource buffer,
                                       int light, CallbackInfo ci) {
        StaffTipFlash.beginHolder(entity);
    }

    @Inject(method = "renderArmWithItem", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;"
            + "Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Z"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        shift = At.Shift.AFTER))
    private void hexwright$endHolder(LivingEntity entity, ItemStack stack, ItemDisplayContext context,
                                     HumanoidArm arm, PoseStack poseStack, MultiBufferSource buffer,
                                     int light, CallbackInfo ci) {
        StaffTipFlash.endHolder();
    }
}

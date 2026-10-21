package com.bluup.hexwright.mixin;

import com.bluup.hexwright.client.soulforge.SoulforgedMaterialisation;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMaterialiseMixin {

    @ModifyExpressionValue(
        method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;"
            + "ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;getMainHandItem()Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack hexwright$showMaterialisingMainHand(ItemStack actual,
                                                          @Local(argsOnly = true) LivingEntity entity) {
        ItemStack shown = SoulforgedMaterialisation.shownIn(entity, InteractionHand.MAIN_HAND, actual);
        return shown != null ? shown : actual;
    }

    @ModifyExpressionValue(
        method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;"
            + "ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;getOffhandItem()Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack hexwright$showMaterialisingOffHand(ItemStack actual,
                                                         @Local(argsOnly = true) LivingEntity entity) {
        ItemStack shown = SoulforgedMaterialisation.shownIn(entity, InteractionHand.OFF_HAND, actual);
        return shown != null ? shown : actual;
    }
}

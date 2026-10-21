package com.bluup.hexwright.mixin;

import com.bluup.hexwright.client.soulforge.SoulforgedMaterialisation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMaterialiseMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    private ItemStack mainHandItem;

    @Shadow
    private ItemStack offHandItem;

    @Shadow
    private float mainHandHeight;

    @Shadow
    private float oMainHandHeight;

    @Shadow
    private float offHandHeight;

    @Shadow
    private float oOffHandHeight;

    @Inject(method = "renderItem", at = @At("HEAD"), cancellable = true)
    private void hexwright$drawMaterialising(LivingEntity entity, ItemStack stack, ItemDisplayContext context,
                                             boolean leftHand, PoseStack poseStack, MultiBufferSource buffers,
                                             int light, CallbackInfo ci) {
        if (SoulforgedMaterialisation.render(entity, stack, context, leftHand, poseStack, buffers, light)) {
            ci.cancel();
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void hexwright$holdMaterialising(CallbackInfo ci) {
        LocalPlayer player = this.minecraft.player;
        if (player == null) {
            return;
        }
        ItemStack main = SoulforgedMaterialisation.shownIn(player, InteractionHand.MAIN_HAND, player.getMainHandItem());
        if (main != null) {
            this.mainHandItem = main;
            this.mainHandHeight = 1.0F;
            this.oMainHandHeight = 1.0F;
        }
        ItemStack off = SoulforgedMaterialisation.shownIn(player, InteractionHand.OFF_HAND, player.getOffhandItem());
        if (off != null) {
            this.offHandItem = off;
            this.offHandHeight = 1.0F;
            this.oOffHandHeight = 1.0F;
        }
    }
}

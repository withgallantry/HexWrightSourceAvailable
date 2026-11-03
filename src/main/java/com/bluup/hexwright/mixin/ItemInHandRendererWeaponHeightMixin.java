package com.bluup.hexwright.mixin;

import com.bluup.hexwright.server.weapon.AnimatedWeapon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererWeaponHeightMixin {

    @Unique
    private static final float HEXWRIGHT$RAISE_PER_TICK = 0.4F;

    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    private ItemStack mainHandItem;

    @Shadow
    private float mainHandHeight;

    @Shadow
    private float oMainHandHeight;

    @Inject(method = "tick", at = @At("TAIL"))
    private void hexwright$keepWeaponRaised(CallbackInfo ci) {
        LocalPlayer player = this.minecraft.player;
        if (player == null || player.isHandsBusy()) {
            return;
        }
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof AnimatedWeapon)) {
            return;
        }
        if (this.mainHandItem != held) {
            return;
        }
        this.mainHandHeight = Math.min(1.0F, this.oMainHandHeight + HEXWRIGHT$RAISE_PER_TICK);
    }
}

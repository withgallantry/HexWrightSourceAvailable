package com.bluup.hexwright.mixin;

import com.bluup.hexwright.server.item.PentaboxItem;
import com.bluup.hexwright.server.pentabox.PentaboxData;
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
public abstract class PentaboxEquipAnimationMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    private ItemStack mainHandItem;

    @Shadow
    private ItemStack offHandItem;

    @Unique
    private int hexwright$lastHotbarSlot = -1;

    @Inject(method = "tick", at = @At("HEAD"))
    private void hexwright$skipSelectionReequip(CallbackInfo ci) {
        LocalPlayer player = this.minecraft.player;
        if (player == null) {
            this.hexwright$lastHotbarSlot = -1;
            return;
        }

        int hotbarSlot = player.getInventory().selected;
        boolean sameSlot = hotbarSlot == this.hexwright$lastHotbarSlot;
        this.hexwright$lastHotbarSlot = hotbarSlot;

        if (sameSlot && hexwright$isSelectionSwap(this.mainHandItem, player.getMainHandItem())) {
            this.mainHandItem = player.getMainHandItem();
        }
        if (hexwright$isSelectionSwap(this.offHandItem, player.getOffhandItem())) {
            this.offHandItem = player.getOffhandItem();
        }
    }

    @Unique
    private static boolean hexwright$isSelectionSwap(ItemStack from, ItemStack to) {
        return hexwright$isPentaboxContext(from) && hexwright$isPentaboxContext(to);
    }

    @Unique
    private static boolean hexwright$isPentaboxContext(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return stack.getItem() instanceof PentaboxItem || PentaboxData.isLinkedStack(stack);
    }
}

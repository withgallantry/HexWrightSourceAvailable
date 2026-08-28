package com.bluup.hexwright.mixin;

import com.bluup.hexwright.server.weapon.GreatBowItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiGraphics.class)
public abstract class GuiGraphicsGreatBowRechargeMixin {

    private static final int OVERLAY_COLOUR = Integer.MAX_VALUE;

    private static final int ICON_SIZE = 16;
    private static final float DECORATION_DEPTH = 200.0F;

    @Inject(
        method = "renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V",
        at = @At("TAIL")
    )
    private void hexwright$drawGreatBowRecharge(Font font, ItemStack stack, int x, int y,
                                                String text, CallbackInfo ci) {
        if (!(stack.getItem() instanceof GreatBowItem)) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            return;
        }
        float remaining = GreatBowItem.rechargeFraction(stack, client.level, client.getFrameTime());
        if (remaining <= 0.0F) {
            return;
        }

        GuiGraphics graphics = (GuiGraphics) (Object) this;
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, DECORATION_DEPTH);
        int top = y + Mth.floor(ICON_SIZE * (1.0F - remaining));
        int bottom = top + Mth.ceil(ICON_SIZE * remaining);
        graphics.fill(RenderType.guiOverlay(), x, top, x + ICON_SIZE, bottom, OVERLAY_COLOUR);
        graphics.pose().popPose();
    }
}

package com.bluup.hexwright.mixin;

import com.bluup.hexwright.client.portal.ClientPortalManager;
import com.bluup.hexwright.inits.HexwrightNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.jetbrains.annotations.Nullable;

@Mixin(Minecraft.class)
public abstract class PortalStartUseItemMixin {

    @Shadow
    @Nullable
    public LocalPlayer player;

    @Shadow
    @Nullable
    public MultiPlayerGameMode gameMode;

    @Shadow
    @Nullable
    public HitResult hitResult;

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void hexwright$useThroughPortal(CallbackInfo ci) {
        if (player == null || gameMode == null || gameMode.isDestroying()
            || player.isHandsBusy() || player.isSpectator()) {
            return;
        }
        ClientPortalManager.PortalPick pick =
            ClientPortalManager.pickPortal(player, gameMode.getPickRange(), hitResult);
        if (pick == null) {
            return;
        }
        ((MinecraftPortalAccessor) this).hexwright$setRightClickDelay(4);
        HexwrightNetworking.sendPortalUse(pick.pairId(), pick.side());
        player.swing(InteractionHand.MAIN_HAND);
        ci.cancel();
    }
}

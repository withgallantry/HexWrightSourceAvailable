package com.bluup.hexwright.mixin;

import com.bluup.hexwright.client.hexweave.LoomClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class LoomOrbStartUseItemMixin {

    @Shadow
    @Nullable
    public LocalPlayer player;

    @Shadow
    @Nullable
    public MultiPlayerGameMode gameMode;

    @Shadow
    @Nullable
    public HitResult hitResult;

    @Inject(method = "startUseItem", at = @At("HEAD"))
    private void hexwright$useDockedOrb(CallbackInfo ci) {
        if (player == null || gameMode == null) {
            return;
        }
        BlockHitResult orb = LoomClient.pickDockedOrb(player, gameMode.getPickRange(), hitResult);
        if (orb != null) {
            hitResult = orb;
        }
    }
}

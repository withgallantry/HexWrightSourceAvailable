package com.bluup.hexwright.mixin;

import com.bluup.hexwright.server.region.BreakFxProfiling;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class ServerLevelBreakFxProfilingMixin {

    @Inject(method = "levelEvent(Lnet/minecraft/world/entity/player/Player;ILnet/minecraft/core/BlockPos;I)V",
        at = @At("HEAD"), cancellable = true)
    private void hexwright$profileLevelEvent(Player player, int id, BlockPos pos, int data, CallbackInfo ci) {
        if (BreakFxProfiling.onLevelEvent(id)) {
            ci.cancel();
        }
    }
}

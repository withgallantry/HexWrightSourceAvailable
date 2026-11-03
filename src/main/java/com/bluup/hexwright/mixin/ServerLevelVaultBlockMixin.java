package com.bluup.hexwright.mixin;

import com.bluup.hexwright.server.vault.VaultChunkStreamer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class ServerLevelVaultBlockMixin {

    @Inject(method = "sendBlockUpdated", at = @At("TAIL"))
    private void hexwright$forwardToVaultWatchers(BlockPos pos, BlockState oldState, BlockState newState,
                                                  int flags, CallbackInfo ci) {
        VaultChunkStreamer.onBlockChanged((ServerLevel) (Object) this, pos);
    }
}

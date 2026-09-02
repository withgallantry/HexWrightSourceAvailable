package com.bluup.hexwright.mixin;

import com.bluup.hexwright.client.render.emissive.BlockGlow;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public abstract class ClientLevelGlowIndexMixin {

    @Inject(method = "sendBlockUpdated", at = @At("HEAD"))
    private void hexwright$invalidateGlowIndex(BlockPos pos, BlockState oldState, BlockState newState,
                                               int flags, CallbackInfo ci) {
        BlockGlow.onBlockChanged(pos);
    }
}

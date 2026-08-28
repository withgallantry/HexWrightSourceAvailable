package com.bluup.hexwright.mixin;

import com.bluup.hexwright.server.region.RegionBatch;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class ServerLevelRegionBatchMixin {

    @Inject(method = "blockUpdated", at = @At("HEAD"), cancellable = true)
    private void hexwright$deferNeighbourUpdate(BlockPos pos, Block block, CallbackInfo ci) {
        if (RegionBatch.deferBlockUpdate((ServerLevel) (Object) this, pos, block)) {
            ci.cancel();
        }
    }
}

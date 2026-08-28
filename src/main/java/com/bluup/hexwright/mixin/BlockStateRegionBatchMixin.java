package com.bluup.hexwright.mixin;

import com.bluup.hexwright.server.region.RegionBatch;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateRegionBatchMixin {

    @Inject(method = "updateNeighbourShapes(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;II)V",
        at = @At("HEAD"), cancellable = true)
    private void hexwright$deferShapeUpdate(LevelAccessor level, BlockPos pos, int flags, int recursion,
                                            CallbackInfo ci) {
        if (RegionBatch.deferShapeUpdate(level, pos)) {
            ci.cancel();
        }
    }

    @Inject(method = "updateIndirectNeighbourShapes(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;II)V",
        at = @At("HEAD"), cancellable = true)
    private void hexwright$deferIndirectShapeUpdate(LevelAccessor level, BlockPos pos, int flags, int recursion,
                                                    CallbackInfo ci) {
        if (RegionBatch.deferShapeUpdate(level, pos)) {
            ci.cancel();
        }
    }
}

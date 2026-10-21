package com.bluup.hexwright.mixin;

import com.bluup.hexwright.server.armour.DomitorPowers;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LiquidBlock.class)
public abstract class DomitorLavaWalkMixin {

    @Inject(method = "getCollisionShape", at = @At("HEAD"), cancellable = true)
    private void hexwright$domitorLavaWalk(BlockState state, BlockGetter level, BlockPos pos,
                                          CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        if (state.getFluidState().is(FluidTags.LAVA)
            && context instanceof EntityCollisionContext entityContext
            && entityContext.getEntity() instanceof LivingEntity wearer
            && context.isAbove(Shapes.block(), pos, true)
            && !level.getFluidState(pos.above()).is(FluidTags.LAVA)
            && DomitorPowers.walksOnLava(wearer)) {
            cir.setReturnValue(Shapes.block());
        }
    }
}

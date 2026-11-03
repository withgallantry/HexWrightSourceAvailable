package com.bluup.hexwright.mixin;

import com.bluup.hexwright.server.region.RegionSpills;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerLevel.class)
public abstract class ServerLevelRegionSpillMixin {

    @Inject(method = "addFreshEntity", at = @At("HEAD"), cancellable = true)
    private void hexwright$catchSpill(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (RegionSpills.hold((ServerLevel) (Object) this, entity)) {
            cir.setReturnValue(true);
        }
    }
}

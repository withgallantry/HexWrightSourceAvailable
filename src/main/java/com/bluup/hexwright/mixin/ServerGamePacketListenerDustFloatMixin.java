package com.bluup.hexwright.mixin;

import com.bluup.hexwright.server.dust.DustSupport;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerGamePacketListenerImpl.class)
abstract class ServerGamePacketListenerDustFloatMixin {

    @Inject(method = "noBlocksAround", at = @At("HEAD"), cancellable = true)
    private void hexwright$standingOnDust(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (DustSupport.holdsUp(entity)) {
            cir.setReturnValue(false);
        }
    }
}

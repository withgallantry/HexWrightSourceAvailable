package com.bluup.hexwright.mixin;

import com.bluup.hexwright.server.worldgen.TeleportWards;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityRandomTeleportWardMixin {

    @Inject(method = "randomTeleport", at = @At("HEAD"), cancellable = true)
    private void hexwright$refuseWardedTeleport(
        double x, double y, double z, boolean broadcast, CallbackInfoReturnable<Boolean> cir
    ) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        TeleportWards.Check refusing = TeleportWards.firstRefusing(serverLevel, self.position(), new Vec3(x, y, z));
        if (refusing == null) {
            return;
        }
        if (self instanceof ServerPlayer player) {
            refusing.notifyRefused(player);
        }
        cir.setReturnValue(false);
    }
}

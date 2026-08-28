package com.bluup.hexwright.mixin;

import com.bluup.hexwright.server.worldgen.TeleportWards;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ThrownEnderpearl.class)
public abstract class ThrownEnderpearlWardMixin {

    @Inject(method = "onHit", at = @At("HEAD"), cancellable = true)
    private void hexwright$dudInsideWard(HitResult result, CallbackInfo ci) {
        ThrownEnderpearl self = (ThrownEnderpearl) (Object) this;
        if (!(self.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        Entity owner = self.getOwner();
        Vec3 to = result.getLocation();
        Vec3 from = owner != null ? owner.position() : to;
        TeleportWards.Check refusing = TeleportWards.firstRefusing(serverLevel, from, to);
        if (refusing == null) {
            return;
        }
        if (owner instanceof ServerPlayer player) {
            refusing.notifyRefused(player);
        }
        self.discard();
        ci.cancel();
    }
}

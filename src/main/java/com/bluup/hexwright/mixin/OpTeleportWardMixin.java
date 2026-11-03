package com.bluup.hexwright.mixin;

import at.petrak.hexcasting.common.casting.actions.spells.great.OpTeleport;
import com.bluup.hexwright.server.worldgen.TeleportWards;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OpTeleport.class)
public abstract class OpTeleportWardMixin {

    @Inject(method = "teleportRespectSticky", at = @At("HEAD"), cancellable = true, remap = false)
    private void hexwright$refuseWardedTeleport(Entity teleportee, Vec3 delta, ServerLevel world, CallbackInfo ci) {
        Vec3 from = teleportee.position();
        TeleportWards.Check refusing = TeleportWards.firstRefusing(world, from, from.add(delta));
        if (refusing == null) {
            return;
        }
        if (teleportee instanceof ServerPlayer player) {
            refusing.notifyRefused(player);
        }
        ci.cancel();
    }
}

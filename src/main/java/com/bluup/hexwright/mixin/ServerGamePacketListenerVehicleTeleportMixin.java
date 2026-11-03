package com.bluup.hexwright.mixin;

import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
abstract class ServerGamePacketListenerVehicleTeleportMixin {

    @Shadow
    private @Nullable Vec3 awaitingPositionFromClient;

    @Inject(method = "handleMoveVehicle", cancellable = true, at = @At(value = "INVOKE", shift = At.Shift.AFTER,
        target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread("
            + "Lnet/minecraft/network/protocol/Packet;"
            + "Lnet/minecraft/network/PacketListener;"
            + "Lnet/minecraft/server/level/ServerLevel;)V"))
    private void hexwright$awaitTeleportAcknowledgement(ServerboundMoveVehiclePacket packet, CallbackInfo ci) {
        if (this.awaitingPositionFromClient != null) {
            ci.cancel();
        }
    }
}

package com.bluup.hexwright.mixin;

import com.bluup.hexwright.server.vehicle.VehicleEntity;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class VehicleMoveVehiclePacketMixin {

    @Shadow
    @Final
    private ServerPlayer player;

    @Inject(method = "handleMoveVehicle", at = @At("HEAD"), cancellable = true)
    private void hexwright$ignoreClientReportedPositionForServerAuthoritativeVehicles(
        ServerboundMoveVehiclePacket packet, CallbackInfo ci
    ) {
        Entity rootVehicle = this.player.getRootVehicle();
        if (rootVehicle instanceof VehicleEntity) {
            ci.cancel();
        }
    }
}

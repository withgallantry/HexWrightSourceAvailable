package com.bluup.hexwright.mixin;

import com.bluup.hexwright.client.portal.DimensionLeakFixCompat;
import com.bluup.hexwright.client.portal.RemoteLevelManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerVaultRetainMixin {

    @Inject(method = "handleRespawn", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
        target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread("
            + "Lnet/minecraft/network/protocol/Packet;"
            + "Lnet/minecraft/network/PacketListener;"
            + "Lnet/minecraft/util/thread/BlockableEventLoop;)V"))
    private void hexwright$retainLevelForVaultView(ClientboundRespawnPacket packet, CallbackInfo ci) {
        hexwright$respawnStartNanos = System.nanoTime();
        DimensionLeakFixCompat.noteTransition(Minecraft.getInstance().level, packet.getDimension());
        RemoteLevelManager.retainOutgoingLevel(packet.getDimension());
        hexwright$retainDoneNanos = System.nanoTime();
    }

    @Unique
    private long hexwright$respawnStartNanos;

    @Unique
    private long hexwright$retainDoneNanos;

    @Inject(method = "handleRespawn", at = @At("RETURN"))
    private void hexwright$reportRespawnCost(ClientboundRespawnPacket packet, CallbackInfo ci) {
        if (hexwright$respawnStartNanos == 0L) {
            return;
        }
        long now = System.nanoTime();
        long retainMs = (hexwright$retainDoneNanos - hexwright$respawnStartNanos) / 1_000_000L;
        long vanillaMs = (now - hexwright$retainDoneNanos) / 1_000_000L;
        hexwright$respawnStartNanos = 0L;
        if (retainMs + vanillaMs >= 200L) {
            com.bluup.hexwright.Hexwright.LOGGER.warn(
                "[vault] slow dimension change into {}: retain {} ms, vanilla level+renderer rebuild {} ms",
                packet.getDimension().location(), retainMs, vanillaMs);
        }
    }

    @Inject(method = "handleRespawn", at = @At("TAIL"))
    private void hexwright$dismissTerrainScreenForVault(ClientboundRespawnPacket packet, CallbackInfo ci) {
        com.bluup.hexwright.client.portal.ArrivalTrace.arm(packet.getDimension().location().toString());
        if (!RemoteLevelManager.shouldSkipLoadingScreen()) {
            return;
        }
        if (Minecraft.getInstance().screen instanceof ReceivingLevelScreen terrain) {
            terrain.loadingPacketsReceived();
        }
    }
}

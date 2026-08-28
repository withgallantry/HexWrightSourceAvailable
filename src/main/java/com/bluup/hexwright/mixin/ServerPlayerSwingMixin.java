package com.bluup.hexwright.mixin;

import com.bluup.hexwright.server.weapon.AnimatedWeapon;
import com.bluup.hexwright.server.weapon.MeleeSwingState;
import com.bluup.hexwright.server.weapon.SwingArc;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerPlayerSwingMixin {

    @Shadow
    public ServerPlayer player;

    @Unique
    private final MeleeSwingState hexwright$swingState = new MeleeSwingState();

    @Inject(method = "handleAnimate", at = @At("TAIL"))
    private void hexwright$weaponAttackAnimation(ServerboundSwingPacket packet, CallbackInfo ci) {
        AnimatedWeapon.onSwing(this.player, packet.getHand(), this.hexwright$swingState);
    }

    @Inject(
        method = "handleInteract",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/game/ServerboundInteractPacket;"
                + "dispatch(Lnet/minecraft/network/protocol/game/ServerboundInteractPacket$Handler;)V"
        )
    )
    private void hexwright$noteAimedAttack(ServerboundInteractPacket packet, CallbackInfo ci) {
        MeleeSwingState state = this.hexwright$swingState;
        int now = this.player.server.getTickCount();
        packet.dispatch(new ServerboundInteractPacket.Handler() {
            @Override
            public void onInteraction(InteractionHand hand) {
                note(hand);
            }

            @Override
            public void onInteraction(InteractionHand hand, Vec3 pos) {
                note(hand);
            }

            @Override
            public void onAttack() {
                state.noteAimedAttack(now);
            }

            private void note(InteractionHand hand) {
                if (hand == InteractionHand.MAIN_HAND) {
                    state.noteInteract(now);
                }
            }
        });
    }

    @Inject(
        method = "handleUseItemOn",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread"
                + "(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;"
                + "Lnet/minecraft/server/level/ServerLevel;)V",
            shift = At.Shift.AFTER
        )
    )
    private void hexwright$noteBlockInteract(ServerboundUseItemOnPacket packet, CallbackInfo ci) {
        hexwright$noteInteract(packet.getHand());
    }

    @Unique
    private void hexwright$noteInteract(InteractionHand hand) {
        if (hand == InteractionHand.MAIN_HAND) {
            this.hexwright$swingState.noteInteract(this.player.server.getTickCount());
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void hexwright$playHeldSwing(CallbackInfo ci) {
        AnimatedWeapon.onConnectionTick(this.player, this.hexwright$swingState);
    }
}

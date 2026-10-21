package com.bluup.hexwright.mixin;

import com.bluup.hexwright.server.weapon.AnimatedWeapon;
import com.bluup.hexwright.server.weapon.MeleeSwingState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerMeleeMixin implements MeleeSwingState.Holder {

    @Shadow
    public ServerPlayer player;

    @Unique
    private final MeleeSwingState hexwright$swingState = new MeleeSwingState();

    @Override
    public MeleeSwingState hexwright$meleeSwingState() {
        return this.hexwright$swingState;
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void hexwright$playHeldSwing(CallbackInfo ci) {
        AnimatedWeapon.onConnectionTick(this.player, this.hexwright$swingState);
    }
}

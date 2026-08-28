package com.bluup.hexwright.mixin;

import com.bluup.hexwright.common.weapon.MiningClickState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerGameMode.class)
public abstract class ServerPlayerGameModeMiningMixin implements MiningClickState {

    @Shadow
    @Final
    protected ServerPlayer player;

    @Shadow
    private boolean isDestroyingBlock;

    @Unique
    private int hexwright$lastBlockActionTick = Integer.MIN_VALUE;

    @Inject(method = "handleBlockBreakAction", at = @At("HEAD"))
    private void hexwright$noteBlockAction(BlockPos pos, ServerboundPlayerActionPacket.Action action,
                                           Direction face, int maxBuildHeight, int sequence,
                                           CallbackInfo ci) {
        this.hexwright$lastBlockActionTick = this.player.server.getTickCount();
    }

    @Override
    public boolean hexwright$isMiningClick() {
        return this.isDestroyingBlock
            || this.hexwright$lastBlockActionTick == this.player.server.getTickCount();
    }
}

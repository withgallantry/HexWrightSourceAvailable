package com.bluup.hexwright.server.hexweave;

import com.bluup.hexwright.server.pocketcaster.PocketCasterCastEnv;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;

public final class WovenOrbCastEnv extends PocketCasterCastEnv {

    public WovenOrbCastEnv(ServerPlayer holder, InteractionHand hand) {
        super(holder, hand);
    }
}

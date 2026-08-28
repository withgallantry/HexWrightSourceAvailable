package com.bluup.hexwright.server.network;

import com.bluup.hexwright.server.pocketcaster.PocketCasterCastEnv;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;

public final class ResonantRingCastEnv extends PocketCasterCastEnv {

    public ResonantRingCastEnv(ServerPlayer wearer, InteractionHand castingHand) {
        super(wearer, castingHand);
    }
}

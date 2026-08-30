package com.bluup.hexwright.server.talisman;

import com.bluup.hexwright.server.pocketcaster.PocketCasterCastEnv;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;

public final class TalismanCastEnv extends PocketCasterCastEnv {

    public TalismanCastEnv(ServerPlayer wearer, InteractionHand hand) {
        super(wearer, hand);
    }
}

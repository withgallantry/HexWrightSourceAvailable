package com.bluup.hexwright.server.talisman;

import com.bluup.hexwright.server.hexpatterns.CastSounds;
import at.petrak.hexcasting.api.casting.eval.CastResult;
import at.petrak.hexcasting.api.casting.eval.env.StaffCastEnv;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;

public final class TalismanCastEnv extends StaffCastEnv {

    public TalismanCastEnv(ServerPlayer wearer, InteractionHand hand) {
        super(wearer, hand);
    }

    @Override
    public void postExecution(CastResult result) {
        super.postExecution(CastSounds.muted(result));
    }
}

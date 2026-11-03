package com.bluup.hexwright.server.staff_assembly;

import com.bluup.hexwright.server.hexpatterns.CastSounds;
import at.petrak.hexcasting.api.casting.eval.CastResult;
import at.petrak.hexcasting.api.casting.eval.env.StaffCastEnv;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;

public final class StaffPowerCastEnv extends StaffCastEnv {
    public StaffPowerCastEnv(ServerPlayer caster, InteractionHand castingHand) {
        super(caster, castingHand);
    }

    @Override
    public void postExecution(CastResult result) {
        super.postExecution(CastSounds.muted(result));
    }

    @Override
    public void postCast(CastingImage image) {
    }
}

package com.bluup.hexwright.server.pocketcaster;

import com.bluup.hexwright.server.hexpatterns.CastSounds;
import at.petrak.hexcasting.api.casting.eval.CastResult;
import at.petrak.hexcasting.api.casting.eval.env.StaffCastEnv;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;

public class PocketCasterCastEnv extends StaffCastEnv {

    private final boolean deliberate;

    public PocketCasterCastEnv(ServerPlayer caster, InteractionHand castingHand) {
        this(caster, castingHand, true);
    }

    public PocketCasterCastEnv(ServerPlayer caster, InteractionHand castingHand, boolean deliberate) {
        super(caster, castingHand);
        this.deliberate = deliberate;
    }

    public boolean deliberate() {
        return deliberate;
    }

    @Override
    public void postExecution(CastResult result) {
        super.postExecution(CastSounds.muted(result));
    }

    @Override
    public void postCast(CastingImage image) {
    }
}

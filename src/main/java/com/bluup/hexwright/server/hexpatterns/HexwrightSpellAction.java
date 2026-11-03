package com.bluup.hexwright.server.hexpatterns;

import at.petrak.hexcasting.api.casting.castables.SpellAction;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.eval.OperationResult;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.eval.vm.SpellContinuation;
import at.petrak.hexcasting.api.casting.iota.Iota;
import net.minecraft.nbt.CompoundTag;

import java.util.List;

public abstract class HexwrightSpellAction implements SpellAction {

    @Override
    public Result executeWithUserdata(List<? extends Iota> args, CastingEnvironment env, CompoundTag userData) {
        return SpellAction.DefaultImpls.executeWithUserdata(this, args, env, userData);
    }

    @Override
    public boolean hasCastingSound(CastingEnvironment env) {
        return SpellAction.DefaultImpls.hasCastingSound(this, env);
    }

    @Override
    public boolean awardsCastingStat(CastingEnvironment env) {
        return SpellAction.DefaultImpls.awardsCastingStat(this, env);
    }

    @Override
    public OperationResult operate(CastingEnvironment env, CastingImage image, SpellContinuation continuation) {
        return SpellAction.DefaultImpls.operate(this, env, image, continuation);
    }
}

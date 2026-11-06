package com.bluup.hexwright.server.hexahedron;

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.mishaps.Mishap;
import at.petrak.hexcasting.api.pigment.FrozenPigment;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;

import java.util.List;

public final class MishapHexahedron extends Mishap {

    @Override
    public FrozenPigment accentColor(CastingEnvironment env, Mishap.Context errorCtx) {
        return dyeColor(DyeColor.LIGHT_BLUE);
    }

    @Override
    public void execute(CastingEnvironment env, Mishap.Context errorCtx, List<Iota> stack) {
    }

    @Override
    protected Component errorMessage(CastingEnvironment env, Mishap.Context errorCtx) {
        return error("hexwright_hexahedron");
    }
}

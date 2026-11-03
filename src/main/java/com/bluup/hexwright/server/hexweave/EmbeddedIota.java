package com.bluup.hexwright.server.hexweave;

import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.api.casting.math.HexCoord;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

import java.util.List;

public record EmbeddedIota(CompoundTag iota, HexCoord origin) implements ThreadEntry {

    public EmbeddedIota {
        iota = iota.copy();
    }

    @Override
    public List<HexCoord> points() {
        return List.of(origin);
    }

    @Override
    public EmbeddedIota at(HexCoord origin) {
        return new EmbeddedIota(iota, origin);
    }

    @Override
    public Iota iota(ServerLevel level) {
        return IotaType.deserialize(iota.copy(), level);
    }
}

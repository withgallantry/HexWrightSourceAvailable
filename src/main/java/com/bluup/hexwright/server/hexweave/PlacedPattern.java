package com.bluup.hexwright.server.hexweave;

import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.PatternIota;
import at.petrak.hexcasting.api.casting.math.HexCoord;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import net.minecraft.server.level.ServerLevel;

import java.util.List;

public record PlacedPattern(HexPattern pattern, HexCoord origin) implements ThreadEntry {

    @Override
    public List<HexCoord> points() {
        return pattern.positions(origin);
    }

    @Override
    public PlacedPattern at(HexCoord origin) {
        return new PlacedPattern(pattern, origin);
    }

    @Override
    public Iota iota(ServerLevel level) {
        return new PatternIota(pattern);
    }
}

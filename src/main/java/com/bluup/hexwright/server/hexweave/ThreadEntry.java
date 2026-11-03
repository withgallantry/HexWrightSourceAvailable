package com.bluup.hexwright.server.hexweave;

import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.api.casting.math.HexCoord;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.common.lib.hex.HexIotaTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;

public sealed interface ThreadEntry permits PlacedPattern, EmbeddedIota {

    HexCoord origin();

    List<HexCoord> points();

    ThreadEntry at(HexCoord origin);

    Iota iota(ServerLevel level);

    static List<ThreadEntry> copiedFrom(CompoundTag stored) {
        IotaType<?> type = IotaType.getTypeFromTag(stored);
        if (type == null || type == HexIotaTypes.NULL) {
            return List.of();
        }
        if (type != HexIotaTypes.LIST) {
            return List.of(entry(stored));
        }
        List<ThreadEntry> entries = new ArrayList<>();
        if (stored.get(HexIotaTypes.KEY_DATA) instanceof ListTag list) {
            for (Tag element : list) {
                if (element instanceof CompoundTag iota) {
                    entries.add(entry(iota));
                }
            }
        }
        return entries;
    }

    private static ThreadEntry entry(CompoundTag iota) {
        if (IotaType.getTypeFromTag(iota) == HexIotaTypes.PATTERN
            && iota.get(HexIotaTypes.KEY_DATA) instanceof CompoundTag pattern) {
            return new PlacedPattern(HexPattern.fromNBT(pattern), HexCoord.getOrigin());
        }
        return new EmbeddedIota(iota, HexCoord.getOrigin());
    }
}

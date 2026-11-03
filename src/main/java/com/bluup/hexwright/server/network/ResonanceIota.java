package com.bluup.hexwright.server.network;

import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.bluup.hexwright.Hexwright;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.NotNull;

public class ResonanceIota extends Iota {

    public ResonanceIota(String networkKey) {
        super(TYPE, networkKey);
    }

    public String getNetworkKey() {
        return (String) this.payload;
    }

    @Override
    public boolean isTruthy() {
        return true;
    }

    @Override
    public boolean toleratesOther(Iota that) {
        return typesMatch(this, that)
            && that instanceof ResonanceIota other
            && this.getNetworkKey().equals(other.getNetworkKey());
    }

    @Override
    public @NotNull Tag serialize() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Network", getNetworkKey());
        return tag;
    }

    public static final IotaType<ResonanceIota> TYPE = new IotaType<>() {
        @Override
        public ResonanceIota deserialize(Tag tag, ServerLevel world) throws IllegalArgumentException {
            if (!(tag instanceof CompoundTag compound) || !compound.contains("Network")) {
                throw new IllegalArgumentException("Expected a compound tag for a resonance iota");
            }
            return new ResonanceIota(compound.getString("Network"));
        }

        @Override
        public Component display(Tag tag) {
            if (tag instanceof CompoundTag compound) {
                BlockPos pos = ResonantAttunement.keyPos(compound.getString("Network"));
                if (pos != null) {
                    return Component.translatable("hexwright.iota.resonance.display",
                        pos.getX(), pos.getY(), pos.getZ());
                }
            }
            return Component.translatable("hexwright.iota.resonance");
        }

        @Override
        public int color() {
            return 0xFF_5FD3C8;
        }
    };

    public static void register() {
        Registry.register(IXplatAbstractions.INSTANCE.getIotaTypeRegistry(), Hexwright.id("resonance"), TYPE);
    }
}
